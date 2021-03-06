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
package org.openrewrite.maven

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.openrewrite.whenParsedBy
import java.io.File
import java.nio.file.Path

class UpgradeVersionTest {
    @Test
    fun upgradeVersion(@TempDir tempDir: Path) {
        File(tempDir.toFile(), "pom.xml").apply {
            writeText("""
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  
                  <groupId>com.mycompany.app</groupId>
                  <artifactId>my-app</artifactId>
                  <version>1</version>
                  
                  <dependencies>
                    <dependency>
                      <groupId>org.springframework.boot</groupId>
                      <artifactId>spring-boot</artifactId>
                      <version>1.5.1.RELEASE</version>
                      <scope>test</scope>
                    </dependency>
                  </dependencies>
                </project>
            """.trimIndent().trim())
        }
                .toPath()
                .whenParsedBy(MavenParser.builder().build())
                .whenVisitedBy(UpgradeVersion().apply {
                    setGroupId("org.springframework.boot")
                    setToVersion("~1.5")
                })
                .isRefactoredTo("""
                    <project>
                      <modelVersion>4.0.0</modelVersion>
                      
                      <groupId>com.mycompany.app</groupId>
                      <artifactId>my-app</artifactId>
                      <version>1</version>
                      
                      <dependencies>
                        <dependency>
                          <groupId>org.springframework.boot</groupId>
                          <artifactId>spring-boot</artifactId>
                          <version>1.5.22.RELEASE</version>
                          <scope>test</scope>
                        </dependency>
                      </dependencies>
                    </project>
                """)
    }
}
