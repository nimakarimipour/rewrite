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
package org.openrewrite.java.search

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.openrewrite.java.JavaParser
import org.openrewrite.java.tree.J

interface FindReferencesToVariableTest {
    @Test
    fun findReferences(jp: JavaParser) {
        val a = jp.parse("""
            public class A {
                int n;
                public void foo() {
                    int n;
                    n = 1;
                    (n) = 2;
                    n++;
                    if((n = 4) > 1) {}
                    this.n = 1;
                }
            }
        """.trimIndent())[0]

        val n = (a.classes[0]!!.methods[0]!!.body!!.statements[0] as J.VariableDecls).vars[0]
        val refs = FindReferencesToVariable(n.name).visit(a.classes[0])

        assertEquals(4, refs.size)
    }
}
