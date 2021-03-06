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
import org.openrewrite.java.tree.J
import org.openrewrite.whenParsedBy

interface RenameVariableTest {
    @Test
    fun renameVariable(jp: JavaParser) {
        """
            public class B {
               int n;
            
               {
                   int n;
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
                .whenVisitedByMapped { a ->
                    val blockN = (a.classes[0].body.statements[1] as J.Block<*>).statements[0] as J.VariableDecls
                    RenameVariable(blockN.vars[0], "n1")
                }
                .whenVisitedByMapped { a ->
                    val paramN = (a.classes[0].methods[0]).params.params[0] as J.VariableDecls
                    RenameVariable(paramN.vars[0], "n2")
                }
                .isRefactoredTo("""
                    public class B {
                       int n;
                    
                       {
                           int n1;
                           n1 = 1;
                           n1 /= 2;
                           if(n1 + 1 == 2) {}
                           n1++;
                       }
                       
                       public int foo(int n2) {
                           return n2 + this.n;
                       }
                    }
                """)
    }
}
