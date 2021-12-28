/*
 * StringRegistryValueTest.java
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

import static com.github.robtimus.os.windows.registry.RegistryValueTest.TEXT;
import static com.github.robtimus.os.windows.registry.RegistryValueTest.textAsBytes;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@SuppressWarnings("nls")
class StringRegistryValueTest {

    @Nested
    @DisplayName("value")
    class Value {

        @Test
        @DisplayName("from String")
        void testFromString() {
            StringRegistryValue value = new StringRegistryValue("test", TEXT);

            assertEquals(TEXT, value.value());
        }

        @Test
        @DisplayName("from bytes")
        void testFromBytes() {
            byte[] bytes = textAsBytes();
            StringRegistryValue value = new StringRegistryValue("test", bytes, bytes.length);

            assertEquals(TEXT, value.value());
        }
    }

    @Nested
    @DisplayName("rawData")
    class RawData {

        @Test
        @DisplayName("from String")
        void testFromString() {
            StringRegistryValue value = new StringRegistryValue("test", TEXT);

            assertArrayEquals(textAsBytes(), value.rawData());
        }

        @Test
        @DisplayName("from bytes")
        void testFromBytes() {
            byte[] bytes = textAsBytes();
            StringRegistryValue value = new StringRegistryValue("test", bytes, bytes.length);

            assertArrayEquals(bytes, value.rawData());
        }
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("equalsArguments")
    @DisplayName("equals")
    void testEquals(StringRegistryValue value, Object other, boolean expected) {
        assertEquals(expected, value.equals(other));
    }

    static Arguments[] equalsArguments() {
        byte[] data = textAsBytes();
        StringRegistryValue value = new StringRegistryValue("test", TEXT);

        return new Arguments[] {
                arguments(value, value, true),
                arguments(value, new StringRegistryValue("test", TEXT), true),
                arguments(value, new StringRegistryValue("test", data, data.length), true),
                arguments(value, new StringRegistryValue("test", Arrays.copyOf(data, data.length + 10), data.length), true),
                arguments(value, new StringRegistryValue("test2", TEXT), false),
                arguments(value, new StringRegistryValue("test", TEXT.substring(0, TEXT.length() - 1)), false),
                arguments(value, new StringRegistryValue("test", data, data.length - 4), false),
                arguments(value, "foo", false),
                arguments(value, null, false),
        };
    }

    @Test
    @DisplayName("hashCode")
    void testHashCode() {
        StringRegistryValue value = new StringRegistryValue("test", TEXT);

        assertEquals(value.hashCode(), value.hashCode());
        assertEquals(value.hashCode(), new StringRegistryValue("test", TEXT).hashCode());
    }
}
