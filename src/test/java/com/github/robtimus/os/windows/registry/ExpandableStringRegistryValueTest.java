/*
 * ExpandableStringRegistryValueTest.java
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
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@SuppressWarnings("nls")
class ExpandableStringRegistryValueTest {

    @Nested
    @DisplayName("value")
    class Value {

        @Test
        @DisplayName("from String")
        void testFromString() {
            ExpandableStringRegistryValue value = new ExpandableStringRegistryValue("test", TEXT);

            assertEquals(TEXT, value.value());
        }

        @Test
        @DisplayName("from bytes")
        void testFromBytes() {
            byte[] bytes = textAsBytes();
            ExpandableStringRegistryValue value = new ExpandableStringRegistryValue("test", bytes, bytes.length);

            assertEquals(TEXT, value.value());
        }
    }

    @Test
    @DisplayName("expandedValue")
    // No need to mock anything, just make sure we run on Windows
    @EnabledOnOs(OS.WINDOWS)
    void testExpandedValue() {
        Map<String, String> getenv = System.getenv();

        String text = getenv.keySet().stream()
                .limit(3)
                .collect(Collectors.joining("%, %", "Three values: %", "%"));
        String expectedValue = getenv.values().stream()
                .limit(3)
                .collect(Collectors.joining(", ", "Three values: ", ""));

        ExpandableStringRegistryValue value = new ExpandableStringRegistryValue("test", text);
        assertEquals(expectedValue, value.expandedValue());
    }

    @Nested
    @DisplayName("rawData")
    class RawData {

        @Test
        @DisplayName("from String")
        void testFromString() {
            ExpandableStringRegistryValue value = new ExpandableStringRegistryValue("test", TEXT);

            assertArrayEquals(textAsBytes(), value.rawData());
        }

        @Test
        @DisplayName("from bytes")
        void testFromBytes() {
            byte[] bytes = textAsBytes();
            ExpandableStringRegistryValue value = new ExpandableStringRegistryValue("test", bytes, bytes.length);

            assertArrayEquals(bytes, value.rawData());
        }
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("equalsArguments")
    @DisplayName("equals")
    void testEquals(ExpandableStringRegistryValue value, Object other, boolean expected) {
        assertEquals(expected, value.equals(other));
    }

    static Arguments[] equalsArguments() {
        byte[] data = textAsBytes();
        ExpandableStringRegistryValue value = new ExpandableStringRegistryValue("test", TEXT);

        return new Arguments[] {
                arguments(value, value, true),
                arguments(value, new ExpandableStringRegistryValue("test", TEXT), true),
                arguments(value, new ExpandableStringRegistryValue("test", data, data.length), true),
                arguments(value, new ExpandableStringRegistryValue("test", Arrays.copyOf(data, data.length + 10), data.length), true),
                arguments(value, new ExpandableStringRegistryValue("test2", TEXT), false),
                arguments(value, new ExpandableStringRegistryValue("test", TEXT.substring(0, TEXT.length() - 1)), false),
                arguments(value, new ExpandableStringRegistryValue("test", data, data.length - 4), false),
                arguments(value, "foo", false),
                arguments(value, null, false),
        };
    }

    @Test
    @DisplayName("hashCode")
    void testHashCode() {
        ExpandableStringRegistryValue value = new ExpandableStringRegistryValue("test", TEXT);

        assertEquals(value.hashCode(), value.hashCode());
        assertEquals(value.hashCode(), new ExpandableStringRegistryValue("test", TEXT).hashCode());
    }
}
