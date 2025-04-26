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

import static com.github.robtimus.os.windows.registry.RegistryValueTest.assertContentEquals;
import static com.github.robtimus.os.windows.registry.RegistryValueTest.randomData;
import static com.github.robtimus.os.windows.registry.RegistryValueTest.randomDataBytePointer;
import static com.github.robtimus.os.windows.registry.RegistryValueTest.resized;
import static com.github.robtimus.os.windows.registry.foreign.ForeignTestUtils.ALLOCATOR;
import static com.github.robtimus.os.windows.registry.foreign.ForeignUtils.toByteArray;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.foreign.MemorySegment;
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
        @DisplayName("from memory segment")
        void testFromBytesWithLength() {
            MemorySegment data = randomDataBytePointer();
            BinaryValue value = new BinaryValue("test", data, data.byteSize() - 10);

            assertArrayEquals(toByteArray(data.asSlice(0, data.byteSize() - 10)), value.data());
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

            MemorySegment rawData = value.rawData(ALLOCATOR);
            assertArrayEquals(data, toByteArray(rawData));
        }

        @Test
        @DisplayName("from input stream")
        void testFromInputStream() {
            byte[] data = randomData();
            BinaryValue value = assertDoesNotThrow(() -> BinaryValue.of("test", new ByteArrayInputStream(data)));

            MemorySegment rawData = value.rawData(ALLOCATOR);
            assertArrayEquals(data, toByteArray(rawData));
        }

        @Test
        @DisplayName("from memory segment")
        void testFromBytesWithLength() {
            MemorySegment data = randomDataBytePointer();
            BinaryValue value = new BinaryValue("test", data, data.byteSize() - 10);

            MemorySegment rawData = value.rawData(ALLOCATOR);
            assertContentEquals(data, rawData, data.byteSize() - 10);
            assertEquals(data.byteSize() - 10, rawData.byteSize());
        }
    }

    @Test
    @DisplayName("inputStream")
    void testInputStream() throws IOException {
        MemorySegment data = randomDataBytePointer();
        BinaryValue value = new BinaryValue("test", data, data.byteSize() - 10);

        byte[] content = new byte[Math.toIntExact(data.byteSize()) - 10];
        try (InputStream inputStream = value.inputStream()) {
            int offset = 0;
            int remaining = content.length;
            while (inputStream.read(content, offset, remaining) != -1) {
                // Nothing to do
            }
        }
        assertArrayEquals(toByteArray(data.asSlice(0, data.byteSize() - 10)), content);
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

            assertEquals(value, otherValue);
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

    @Nested
    @DisplayName("withData")
    class WithData {

        @Test
        @DisplayName("same data")
        void testSameData() {
            byte[] data = randomData();
            BinaryValue value = BinaryValue.of("test", data);

            BinaryValue otherValue = value.withData(data);

            assertEquals(value, otherValue);
        }

        @Test
        @DisplayName("different data array")
        void testDifferentDataArray() {
            byte[] data = randomData();
            BinaryValue value = BinaryValue.of("test", data);

            BinaryValue otherValue = value.withData(Arrays.copyOf(data, data.length - 1));

            assertNotEquals(value, otherValue);
            assertEquals(value.name(), otherValue.name());
            assertArrayEquals(Arrays.copyOf(data, data.length - 1), otherValue.data());
        }

        @Test
        @DisplayName("different data stream")
        void testDifferentDataStream() {
            byte[] data = randomData();
            BinaryValue value = BinaryValue.of("test", data);

            BinaryValue otherValue = assertDoesNotThrow(() -> value.withData(new ByteArrayInputStream(data, 0, data.length - 1)));

            assertNotEquals(value, otherValue);
            assertEquals(value.name(), otherValue.name());
            assertArrayEquals(Arrays.copyOf(data, data.length - 1), otherValue.data());
        }
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("equalsArguments")
    @DisplayName("equals")
    void testEquals(BinaryValue value, Object other, boolean expected) {
        assertEquals(expected, value.equals(other));
    }

    static Arguments[] equalsArguments() {
        MemorySegment data = randomDataBytePointer();
        byte[] dataBytes = toByteArray(data);
        BinaryValue value = BinaryValue.of("test", dataBytes);

        return new Arguments[] {
                arguments(value, value, true),
                arguments(value, BinaryValue.of("test", dataBytes), true),
                arguments(value, new BinaryValue("test", data, data.byteSize()), true),
                arguments(value, new BinaryValue("test", resized(data, data.byteSize() + 10), data.byteSize()), true),
                arguments(value, BinaryValue.of("test2", dataBytes), false),
                arguments(value, new BinaryValue("test", data, data.byteSize() - 1), false),
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
