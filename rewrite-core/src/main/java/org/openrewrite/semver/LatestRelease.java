/*
 * Copyright 2020 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.semver;

import org.openrewrite.Validated;

import java.util.regex.Matcher;

import static java.lang.Integer.parseInt;

public class LatestRelease implements VersionComparator {
    public static final LatestRelease INSTANCE = build("latest.release").getValue();

    @Override
    public boolean isValid(String version) {
        return VersionComparator.RELEASE_PATTERN.matcher(normalizeVersion(version)).matches();
    }

    protected static String normalizeVersion(String version) {
        if (version.endsWith(".RELEASE")) {
            return version.substring(0, version.length() - ".RELEASE".length());
        }
        return version;
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    public int compare(String v1, String v2) {
        Matcher v1Gav = VersionComparator.RELEASE_PATTERN.matcher(normalizeVersion(v1));
        Matcher v2Gav = VersionComparator.RELEASE_PATTERN.matcher(normalizeVersion(v2));

        v1Gav.matches();
        v2Gav.matches();

        for (int i = 1; i <= 3; i++) {
            String v1Part = v1Gav.group(i);
            String v2Part = v2Gav.group(i);
            if (v1Part == null) {
                return v2Part == null ? 0 : -11;
            } else if (v2Part == null) {
                return 1;
            }

            int diff = parseInt(v1Part) - parseInt(v2Part);
            if (diff != 0) {
                return diff;
            }
        }

        return v1.compareTo(v2);
    }

    public static Validated build(String pattern) {
        return pattern.equals("latest.release") ?
                Validated.valid("latestRelease", new LatestRelease()) :
                Validated.invalid("latestRelease", pattern, "not a hyphen range");
    }
}
