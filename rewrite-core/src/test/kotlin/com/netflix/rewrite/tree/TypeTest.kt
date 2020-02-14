/**
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.rewrite.tree

import com.netflix.rewrite.parse.OpenJdkParser
import com.netflix.rewrite.parse.Parser
import org.junit.Assert.*
import org.junit.Test

open class TypeTest: Parser by OpenJdkParser() {
    @Test
    fun isAssignableFrom() {
        val a = parse("""
            import java.util.List;
            import java.util.Collection;
            public class A {
                public List[] listArr;
                public Collection[] collArr;
            }
        """.trimIndent())

        val (list, collection) = a.imports.map { TypeUtils.asClass(it.qualid.type)!! }
        val (listArr, collectionArr) = a.classes[0].fields.map { TypeUtils.asClass(it.typeExpr!!.type)!! }

        assertTrue(collection.isAssignableFrom(list))
        assertFalse(list.isAssignableFrom(collection))
        assertTrue(Type.Class.OBJECT.isAssignableFrom(collection))

        assertTrue(collectionArr.isAssignableFrom(listArr))
        assertFalse(listArr.isAssignableFrom(collectionArr))
    }

    @Test
    fun innerClassType() {
        val t = Type.Class.build("com.foo.Foo.Bar")
        assertEquals("com.foo.Foo.Bar", t.fullyQualifiedName)
        assertEquals("com.foo", t.packageName)
    }

    @Test
    fun packageName() {
        val t = Type.Class.build("com.foo.Foo")
        assertEquals("com.foo", t.packageName)

        val a = Type.Class.build("a.A1")
        assertEquals("a", a.packageName)

        val b = Type.Class.build("a.A.B")
        assertEquals("a", b.packageName)

        val c = Type.Class.build("A.C")
        assertEquals("", c.packageName)
    }

    @Test
    fun selfReferentialTypeIsShared() {
        val a = OpenJdkParser().parse("public class A { A a; }")
        val outerType = a.classes[0].type
        val fieldType = a.classes[0].fields[0].typeExpr?.type
        assertTrue(outerType === fieldType)
    }

    @Test
    fun typeFlyweightsAreSharedBetweenParsers() {
        val a = OpenJdkParser().parse("public class A {}")
        val a2 = OpenJdkParser().parse("public class A {}")

        assertTrue(a.classes[0].type === a2.classes[0].type)
    }

    @Test
    fun sameFullyQualifiedNameWithDifferentMembers() {
        val a = OpenJdkParser().parse("public class A { String foo; }")
        val a2 = OpenJdkParser().parse("public class A { String bar; }")

        assertTrue(a.classes[0].type !== a2.classes[0].type)
    }

    @Test
    fun sameFullyQualifiedNameWithDifferentTypeHierarchy() {
        val a = OpenJdkParser().parse("""
            public class A extends B {}
            class B {}
        """)

        val a2 = OpenJdkParser().parse("""
            public class A {}
            class B {}
        """)

        assertTrue(a.classes[0].type !== a2.classes[0].type)
    }
}