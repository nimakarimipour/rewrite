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
package org.openrewrite.maven;

import org.openrewrite.Validated;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.semver.LatestRelease;
import org.openrewrite.semver.Semver;
import org.openrewrite.maven.tree.Maven;
import org.openrewrite.semver.VersionComparator;

import java.util.Optional;

import static org.openrewrite.Validated.required;

/**
 * Upgrade the version a group or group and artifact using Node Semver
 * <a href="https://github.com/npm/node-semver#advanced-range-syntax">advanced range selectors</a>, allowing
 * more precise control over version updates to patch or minor releases.
 */
public class UpgradeVersion extends MavenRefactorVisitor {
    private String groupId;

    @Nullable
    private String artifactId;

    /**
     * Node Semver range syntax.
     */
    private String toVersion;

    private VersionComparator versionComparator;

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public void setArtifactId(@Nullable String artifactId) {
        this.artifactId = artifactId;
    }

    public void setToVersion(String toVersion) {
        this.toVersion = toVersion;
    }

    @Override
    public Validated validate() {
        return required("groupId", groupId)
                .and(required("toVersion", toVersion))
                .and(Semver.validate(toVersion));
    }

    @Override
    public boolean isIdempotent() {
        return false;
    }

    @Override
    public Maven visitPom(Maven.Pom pom) {
        versionComparator = Semver.validate(toVersion).getValue();
        return super.visitPom(pom);
    }

    @Override
    public Maven visitDependency(Maven.Dependency dependency) {
        Maven.Dependency d = refactor(dependency, super::visitDependency);

        if (groupId.equals(d.getGroupId()) && (artifactId == null || artifactId.equals(d.getArtifactId()))) {
            Optional<String> newerVersion = d.getModel().getModuleVersion().getNewerVersions().stream()
                    .filter(v -> versionComparator.isValid(v))
                    .filter(v -> LatestRelease.INSTANCE.compare(dependency.getModel().getModuleVersion().getVersion(), v) < 0)
                    .max(versionComparator);

            if (newerVersion.isPresent()) {
                ChangeDependencyVersion changeDependencyVersion = new ChangeDependencyVersion();
                changeDependencyVersion.setGroupId(groupId);
                changeDependencyVersion.setArtifactId(artifactId);
                changeDependencyVersion.setToVersion(newerVersion.get());
                andThen(changeDependencyVersion);
            }
        }

        return d;
    }
}
