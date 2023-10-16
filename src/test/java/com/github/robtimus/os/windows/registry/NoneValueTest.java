/*
 * NoneValueTest.java
 * Copyright 2021 Rob Spoor
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

package com.github.robtimus.os.windows.registry;

import static com.github.robtimus.os.windows.registry.RegistryValueTest.randomDataBytePointer;
import static com.github.robtimus.os.windows.registry.RegistryValueTest.resized;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import com.github.robtimus.os.windows.registry.foreign.BytePointer;

@SuppressWarnings("nls")
class NoneValueTest {

    @ParameterizedTest(name = "{1}")
    @MethodSource("equalsArguments")
    @DisplayName("equals")
    void testEquals(NoneValue value, Object other, boolean expected) {
        assertEquals(expected, value.equals(other));
    }

    static Arguments[] equalsArguments() {
        BytePointer data = randomDataBytePointer();
        NoneValue value = new NoneValue("test", data, data.size());

        return new Arguments[] {
                arguments(value, value, true),
                arguments(value, new NoneValue("test", data, data.size()), true),
                arguments(value, new NoneValue("test", resized(data, data.size() + 10), data.size()), true),
                arguments(value, new NoneValue("test2", data, data.size()), false),
                arguments(value, new NoneValue("test", data, data.size() - 1), false),
                arguments(value, "foo", false),
                arguments(value, null, false),
        };
    }

    @Test
    @DisplayName("hashCode")
    void testHashCode() {
        BytePointer data = randomDataBytePointer();
        NoneValue value = new NoneValue("test", data, data.size());

        assertEquals(value.hashCode(), value.hashCode());
        assertEquals(value.hashCode(), new NoneValue("test", data, data.size()).hashCode());
    }
}
