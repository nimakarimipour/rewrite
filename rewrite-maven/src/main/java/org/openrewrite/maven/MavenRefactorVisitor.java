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

import org.openrewrite.AbstractRefactorVisitor;
import org.openrewrite.maven.tree.Maven;
import org.openrewrite.refactor.Formatter;
import org.openrewrite.xml.XmlRefactorVisitor;
import org.openrewrite.xml.tree.Xml;

public class MavenRefactorVisitor extends AbstractRefactorVisitor<Maven> implements MavenSourceVisitor<Maven> {
    protected Formatter formatter;

    XmlRefactorVisitor xmlRefactorVisitor = new XmlRefactorVisitor() {
    };

    @Override
    public Maven visitPom(Maven.Pom pom) {
        formatter = new Formatter(pom.getDocument());
        Maven.Pom p = pom;
        p = p.withParent(refactor(p.getParent()));
        p = p.withDependencyManagement(refactor(p.getDependencyManagement()));
        p = p.withDependencies(refactor(p.getDependencies()));
        p = p.withProperties(refactor(p.getProperties()));
        return p;
    }

    @Override
    public Maven visitDependencyManagement(Maven.DependencyManagement dependencyManagement) {
        return dependencyManagement.withDependencies(refactor(dependencyManagement.getDependencies()));
    }

    @Override
    public Maven visitDependency(Maven.Dependency dependency) {
        Xml.Tag t = (Xml.Tag) xmlRefactorVisitor.visitTag(dependency.getTag());
        if(t != dependency.getTag()) {
            return new Maven.Dependency(dependency.isManaged(), dependency.getModel(), t);
        }
        return dependency;
    }

    @Override
    public Maven visitProperty(Maven.Property property) {
        Xml.Tag t = (Xml.Tag) xmlRefactorVisitor.visitTag(property.getTag());
        if(t != property.getTag()) {
            return new Maven.Property(t);
        }
        return property;
    }

    @Override
    public Maven visitParent(Maven.Parent parent) {
        return parent;
    }
}
