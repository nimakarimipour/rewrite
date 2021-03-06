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
package org.openrewrite.java

import org.junit.jupiter.api.Test
import org.openrewrite.java.tree.JavaType
import org.openrewrite.whenParsedBy

interface ChangeFieldNameTest {

    @Test
    fun changeFieldName(jp: JavaParser) {
        """
            import java.util.List;
            public class A {
               List collection = null;
            }
        """
                .whenParsedBy(jp)
                .whenVisitedByMapped { a -> ChangeFieldName.Scoped(a.classes[0].type.asClass(), "collection", "list") }
                .isRefactoredTo("""
                    import java.util.List;
                    public class A {
                       List list = null;
                    }
                """)
    }

    @Test
    fun changeFieldNameReferences(jp: JavaParser) {
        """
            public class B {
               int n;
               
               {
                   n = 1;
                   n /= 2;
                   if(n + 1 == 2) {}
                   n++;
               }
               
               public int foo(int n) {
                   return n + this.n;
               }
            }
        """
                .whenParsedBy(jp)
                .whenVisitedBy(ChangeFieldName.Scoped(JavaType.Class.build("B"), "n", "n1"))
                .isRefactoredTo("""
                    public class B {
                       int n1;
                       
                       {
                           n1 = 1;
                           n1 /= 2;
                           if(n1 + 1 == 2) {}
                           n1++;
                       }
                       
                       public int foo(int n) {
                           return n + this.n1;
                       }
                    }
                """)
    }

    @Test
    fun changeFieldNameReferencesInOtherClass(jp: JavaParser) {
        """
            public class A {
                B b = new B();
                {
                    b.n = 1;
                }
            }
        """
                .whenParsedBy(jp)
                .whichDependsOn("""
                    public class B {
                       int n;
                    }
                """)
                .whenVisitedBy(ChangeFieldName.Scoped(JavaType.Class.build("B"), "n", "n1"))
                .isRefactoredTo("""
                    public class A {
                        B b = new B();
                        {
                            b.n1 = 1;
                        }
                    }
                """)
    }
}
