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

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.openrewrite.java.JavaParser

interface HasImportTest {
    
    @Test
    fun hasImport(jp: JavaParser) {
        val a = jp.parse("""
            import java.util.List;
            class A {}
        """)[0]
        
        assertTrue(a.hasImport("java.util.List"))
        assertFalse(a.hasImport("java.util.Set"))
    }

    @Test
    fun hasStarImport(jp: JavaParser) {
        val a = jp.parse("""
            import java.util.*;
            class A {}
        """)[0]

        assertTrue(a.hasImport("java.util.List"))
    }

    @Test
    fun hasStarImportOnInnerClass(jp: JavaParser) {
        val a = """
            package a;
            public class A {
               public static class B { }
            }
        """
        
        val c = """
            import a.*;
            public class C {
                A.B b = new A.B();
            }
        """

        assertTrue(jp.parse(c, a)[0].hasImport("a.A.B"))
        assertTrue(jp.parse(c, a)[0].hasImport("a.A"))
    }
}
