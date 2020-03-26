package org.openrewrite.git;

import okhttp3.*;
import org.openrewrite.git.pack.PackfileReader;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.nio.file.Files;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;

public class GitFetchTreeOnly {
//    private static final String REMOTE = "https://bitbucket.org/jkschneider/sample-bitbucket.git";
    private static final String REMOTE = "https://gitlab.com/jkschneider/sample-gitlab.git";
//    private static final String REMOTE = "https://github.com/jkschneider/sample-github.git";

    // bitbucket access token: 6gWtB6jS2dqWytyNRXkP
    // gitlab access token: qTddsPf9zTmYXaKhwN-R
    // github access token: e685615f7fed851ec8ebb7170a20c74222750291

    private static final String FLUSH_PKT = "0000";
    private static final String DELIM_PKT = "0001";

    public static void main(String[] args) throws IOException, NoSuchAlgorithmException, KeyManagementException {
        // https://github.com/git/git/blob/master/Documentation/technical/protocol-v2.txt
        // https://github.com/git/git/blob/master/Documentation/technical/pack-protocol.txt

        SSLContext sslContext = SSLContext.getInstance("TLS");
        TrustEveryoneManager trustManager = new TrustEveryoneManager();
        TrustManager[] trustManagers = new TrustManager[]{trustManager};
        sslContext.init(null, trustManagers, null);

        var client = new OkHttpClient()
                .newBuilder()
                .proxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress("localhost", 8080)))
                .sslSocketFactory(sslContext.getSocketFactory(), trustManager)
                .authenticator((route, response) -> {
                    if (response.request().header("Authorization") != null) {
                        return null;
                    }
                    return response.request().newBuilder()
                            .header("Authorization", Credentials.basic(args[0], args[1]))
                            .build();
                })
                .build();

        capabilityAdvertisement(client);
        lsRefs(client);
        fetch(client);
    }

    /*
    BITBUCKET capabilities (i.e. does not support v2 protocol):
    001d# service=git-upload-pack000000d2e2b2f7edfb0490bef6faa72c10e7a2a8c1a5bccf HEAD multi_ack thin-pack side-band side-band-64k ofs-delta shallow no-progress include-tag multi_ack_detailed no-done symref=HEAD:refs/heads/master agent=git/2.10.5
    003fe2b2f7edfb0490bef6faa72c10e7a2a8c1a5bccf refs/heads/master
    0000
     */

    /*
    GITLAB capabilities:
    001e# service=git-upload-pack
    0000000eversion 2
    0015agent=git/2.24.1
    000cls-refs
    0012fetch=shallow
    0012server-option
    0000
     */

    /*
    GITHUB capabilities:
    001e# service=git-upload-pack
    0000000eversion 2
    0023agent=git/github-g70eaaeb1276f
    000cls-refs
    0019fetch=shallow filter
    0012server-option
    0000
     */
    private static void capabilityAdvertisement(OkHttpClient client) throws IOException {
        var request = new Request.Builder()
                .url(REMOTE + "/info/refs?service=git-upload-pack")
                .header("Git-Protocol", "version=2")
                .get()
                .build();

        printResponse(client, request);
    }

    /*
    0052581d6609738162f8f99ed2eade08440df14a06af HEAD symref-target:refs/heads/master
    003f581d6609738162f8f99ed2eade08440df14a06af refs/heads/master
    0000
     */
    private static void lsRefs(OkHttpClient client) throws IOException {
        var request = new Request.Builder()
                .url(REMOTE + "/git-upload-pack")
                .header("Git-Protocol", "version=2")
                .post(RequestBody.create((pktLine("command=ls-refs") + DELIM_PKT + pktLine("symrefs") + FLUSH_PKT).getBytes()))
                .build();

        printResponse(client, request);
    }

    private static void fetch(OkHttpClient client) throws IOException {
        var request = new Request.Builder()
                .url(REMOTE + "/git-upload-pack")
                .header("Git-Protocol", "version=2")
                .post(RequestBody.create((
                        pktLine("command=fetch")
                                + DELIM_PKT + pktLine("want 581d6609738162f8f99ed2eade08440df14a06af")
                                + pktLine("done")
                                + pktLine("no-progress") // output won't be multiplexed with progress packet lines
                                + FLUSH_PKT
                ).getBytes()))
                .build();

        printResponse(client, request);
    }

    private static String pktLine(String line) {
        String pktLine = line + "\n";
        BigInteger size = BigInteger.valueOf(pktLine.length() + 4);
        return String.format("%04x", size) + pktLine;
    }

    @SuppressWarnings("ConstantConditions")
    private static void printResponse(OkHttpClient client, Request request) throws IOException {
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected code " + response);
            }

            String body = response.body().string();
            if (body.contains("packfile")) {
                String[] beforeAndAfterPackfile = body.split("packfile\n");
                body = beforeAndAfterPackfile[0] + "packfile";
                String packfileLines = beforeAndAfterPackfile[1];

//                AtomicInteger absolutePos = new AtomicInteger(0);
//                Matcher matcher = Pattern.compile("[0-9a-fA-F]{4}").matcher(packfileLines);
//                matcher.results().forEach(result -> {
//                    int size = new BigInteger(result.group(0), 16).intValue();
//                    System.out.println("Found match at " + result.start() +
//                            " with content " + result.group(0) + " (" + size + ", absolute = " + absolutePos.addAndGet(size) + ")");
//                });

//                try(InputStream in = new ByteArrayInputStream(packfileLines.getBytes())) {
//                    while(in.available() > 5) {
//                        int length = new BigInteger(new String(in.readNBytes(4), StandardCharsets.UTF_8), 16).intValue();
//                        String line = "packfile-line length=" + length + ", " + "stream code=" + in.read();
//                        body = body + line;
//                        System.out.println(line);
//                        in.readNBytes(length - 4 - 1);
//                    }
//                }

                byte[] packfileBytes = packfileLines.substring(5).getBytes();

                Files.write(new File("git-upload-pack.pack").toPath(), packfileBytes);
                new PackfileReader().read(packfileBytes);
            }
            System.out.println(body + "\n\n");
        }
    }

    static class TrustEveryoneManager implements X509TrustManager {
        @Override
        public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) {
        }

        @Override
        public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) {
        }

        @Override
        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }
}
