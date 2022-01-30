/*
 * BinaryValueTest.java
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

import static com.github.robtimus.os.windows.registry.RegistryValueTest.randomData;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@SuppressWarnings("nls")
class BinaryValueTest {

    @Nested
    @DisplayName("data")
    class Data {

        @Test
        @DisplayName("from bytes")
        void testFromBytes() {
            byte[] data = randomData();
            BinaryValue value = BinaryValue.of("test", data);

            assertArrayEquals(data, value.data());
        }

        @Test
        @DisplayName("from input stream")
        void testFromInputStream() {
            byte[] data = randomData();
            BinaryValue value = assertDoesNotThrow(() -> BinaryValue.of("test", new ByteArrayInputStream(data)));

            assertArrayEquals(data, value.data());
        }

        @Test
        @DisplayName("from bytes with length")
        void testFromBytesWithLength() {
            byte[] data = randomData();
            BinaryValue value = new BinaryValue("test", data, data.length - 10);

            assertArrayEquals(Arrays.copyOf(data, data.length - 10), value.data());
        }
    }

    @Nested
    @DisplayName("raw data")
    class RawData {

        @Test
        @DisplayName("from bytes")
        void testFromBytes() {
            byte[] data = randomData();
            BinaryValue value = BinaryValue.of("test", data);

            assertArrayEquals(data, value.rawData());
        }

        @Test
        @DisplayName("from input stream")
        void testFromInputStream() {
            byte[] data = randomData();
            BinaryValue value = assertDoesNotThrow(() -> BinaryValue.of("test", new ByteArrayInputStream(data)));

            assertArrayEquals(data, value.rawData());
        }

        @Test
        @DisplayName("from bytes with length")
        void testFromBytesWithLength() {
            byte[] data = randomData();
            BinaryValue value = new BinaryValue("test", data, data.length - 10);

            assertArrayEquals(Arrays.copyOf(data, data.length - 10), value.rawData());
        }
    }

    @Test
    @DisplayName("inputStream")
    void testInputStream() throws IOException {
        byte[] data = randomData();
        BinaryValue value = new BinaryValue("test", data, data.length - 10);

        byte[] content = new byte[data.length - 10];
        try (InputStream inputStream = value.inputStream()) {
            int offset = 0;
            int remaining = content.length;
            while (inputStream.read(content, offset, remaining) != -1) {
                // Nothing to do
            }
        }
        assertArrayEquals(Arrays.copyOf(data, data.length - 10), content);
    }

    @Nested
    @DisplayName("withName")
    class WithName {

        @Test
        @DisplayName("same name")
        void testSameName() {
            byte[] data = randomData();
            BinaryValue value = BinaryValue.of("test", data);

            BinaryValue otherValue = value.withName("test");

            assertSame(value, otherValue);
        }

        @Test
        @DisplayName("different name")
        void testDifferentName() {
            byte[] data = randomData();
            BinaryValue value = BinaryValue.of("test", data);

            BinaryValue otherValue = value.withName("test2");

            assertNotEquals(value, otherValue);
            assertEquals("test2", otherValue.name());
            assertArrayEquals(data, otherValue.data());
        }
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("equalsArguments")
    @DisplayName("equals")
    void testEquals(BinaryValue value, Object other, boolean expected) {
        assertEquals(expected, value.equals(other));
    }

    static Arguments[] equalsArguments() {
        byte[] data = randomData();
        BinaryValue value = BinaryValue.of("test", data);

        return new Arguments[] {
                arguments(value, value, true),
                arguments(value, BinaryValue.of("test", data), true),
                arguments(value, new BinaryValue("test", data, data.length), true),
                arguments(value, new BinaryValue("test", Arrays.copyOf(data, data.length + 10), data.length), true),
                arguments(value, BinaryValue.of("test2", data), false),
                arguments(value, new BinaryValue("test", data, data.length - 1), false),
                arguments(value, "foo", false),
                arguments(value, null, false),
        };
    }

    @Test
    @DisplayName("hashCode")
    void testHashCode() {
        byte[] data = randomData();
        BinaryValue value = BinaryValue.of("test", data);

        assertEquals(value.hashCode(), value.hashCode());
        assertEquals(value.hashCode(), BinaryValue.of("test", data).hashCode());
    }
}
