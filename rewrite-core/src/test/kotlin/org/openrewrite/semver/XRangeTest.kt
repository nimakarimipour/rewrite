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
package org.openrewrite.semver

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class XRangeTest {
    @Test
    fun pattern() {
        assertThat(XRange.build("*").isValid).isTrue()
        assertThat(XRange.build("*.0.0").isValid).isFalse()
        assertThat(XRange.build("1.x").isValid).isTrue()
        assertThat(XRange.build("1.x.0").isValid).isFalse()
        assertThat(XRange.build("1.1.X").isValid).isTrue()
        assertThat(XRange.build("a").isValid).isFalse()
    }

    /**
     * X := >=0.0.0
     */
    @Test
    fun anyVersion() {
        val xRange: XRange = XRange.build("X").getValue()

        assertThat(xRange.isValid("0.0.0")).isTrue()
    }

    /**
     * 1.* := >=1.0.0 <2.0.0-0
     */
    @Test
    fun matchingMajorVersion() {
        val xRange: XRange = XRange.build("1.*").getValue()

        assertThat(xRange.isValid("1.0.0")).isTrue()
        assertThat(xRange.isValid("1.2.3.RELEASE")).isTrue()
        assertThat(xRange.isValid("1.9.9")).isTrue()
        assertThat(xRange.isValid("2.0.0")).isFalse()
    }

    /**
     * 1.2.X := >=1.2.0 <1.3.1
     */
    @Test
    fun matchingMajorAndMinorVersions() {
        val xRange: XRange = XRange.build("1.2.X").getValue()

        assertThat(xRange.isValid("1.2.0")).isTrue()
        assertThat(xRange.isValid("1.3.0")).isFalse()
    }
}
