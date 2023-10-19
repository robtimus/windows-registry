/*
 * StringUtilsTest.java
 * Copyright 2023 Rob Spoor
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

package com.github.robtimus.os.windows.registry.foreign;

import static com.github.robtimus.os.windows.registry.foreign.ForeignTestUtils.ALLOCATOR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import com.sun.jna.Memory;
import com.sun.jna.Native;

@SuppressWarnings("nls")
class StringUtilsTest {

    @Nested
    @DisplayName("toString")
    class ToString {

        @Test
        @DisplayName("empty segment")
        void testEmptySegment() {
            MemorySegment segment = MemorySegment.NULL;

            assertNull(StringUtils.toString(segment));
        }

        @ParameterizedTest
        @DisplayName("non-empty segment")
        @ValueSource(strings = { "foo", "bar" })
        @EmptySource
        void testNonEmptySegment(String value) {
            Memory memory = new Memory((value.length() + 1L) * Native.WCHAR_SIZE);
            memory.setWideString(0, value);
            byte[] bytes = memory.getByteArray(0, (int) memory.size());

            MemorySegment segment = ALLOCATOR.allocateArray(ValueLayout.JAVA_BYTE, bytes);

            String result = StringUtils.toString(segment);
            assertEquals(value, result);
        }

        @Test
        void testMissingTerminator() {
            String value = "foo";
            Memory memory = new Memory((value.length() + 1L) * Native.WCHAR_SIZE);
            memory.setWideString(0, value);
            byte[] bytes = memory.getByteArray(0, (int) memory.size() - Native.WCHAR_SIZE);

            MemorySegment segment = ALLOCATOR.allocateArray(ValueLayout.JAVA_BYTE, bytes);

            IllegalStateException exception = assertThrows(IllegalStateException.class, () -> StringUtils.toString(segment));
            assertEquals(Messages.StringUtils.stringEndNotFound(bytes.length), exception.getMessage());
        }
    }

    @Nested
    @DisplayName("toStringList")
    class ToStringList {

        @Test
        @DisplayName("empty segment")
        void testEmptySegment() {
            MemorySegment segment = MemorySegment.NULL;

            assertEquals(Collections.emptyList(), StringUtils.toStringList(segment));
        }

        @Test
        @DisplayName("non-empty segment")
        void testNonEmptySegment() {
            List<String> values = List.of("foo", "bar");

            int size = values.stream()
                    .mapToInt(String::length)
                    .sum()
                    + values.size()
                    + 1;

            Memory memory = new Memory(size * Native.WCHAR_SIZE);
            long offset = 0;
            for (String value : values) {
                memory.setWideString(offset, value);
                offset += (value.length() + 1) * Native.WCHAR_SIZE;
            }
            memory.setWideString(offset, "");

            byte[] bytes = memory.getByteArray(0, (int) memory.size());

            MemorySegment segment = ALLOCATOR.allocateArray(ValueLayout.JAVA_BYTE, bytes);

            List<String> result = StringUtils.toStringList(segment);
            assertEquals(values, result);
        }

        @Test
        @DisplayName("terminated early")
        void testTerminatedEarly() {
            List<String> values = List.of("foo", "bar", "", "hello", "world");

            int size = values.stream()
                    .mapToInt(String::length)
                    .sum()
                    + values.size()
                    + 1;

            Memory memory = new Memory(size * Native.WCHAR_SIZE);
            long offset = 0;
            for (String value : values) {
                memory.setWideString(offset, value);
                offset += (value.length() + 1) * Native.WCHAR_SIZE;
            }
            memory.setWideString(offset, "");

            byte[] bytes = memory.getByteArray(0, (int) memory.size());

            MemorySegment segment = ALLOCATOR.allocateArray(ValueLayout.JAVA_BYTE, bytes);

            List<String> result = StringUtils.toStringList(segment);
            assertEquals(values.subList(0, 2), result);
        }

        @Test
        void testMissingTerminator() {
            List<String> values = List.of("foo", "bar");

            int size = values.stream()
                    .mapToInt(String::length)
                    .sum()
                    + values.size();

            Memory memory = new Memory(size * Native.WCHAR_SIZE);
            long offset = 0;
            for (String value : values) {
                memory.setWideString(offset, value);
                offset += (value.length() + 1) * Native.WCHAR_SIZE;
            }

            byte[] bytes = memory.getByteArray(0, (int) memory.size());

            MemorySegment segment = ALLOCATOR.allocateArray(ValueLayout.JAVA_BYTE, bytes);

            List<String> result = StringUtils.toStringList(segment);
            assertEquals(values, result);
        }

        @Test
        void testMissingElementTerminator() {
            List<String> values = List.of("foo", "bar");

            int size = values.stream()
                    .mapToInt(String::length)
                    .sum()
                    + values.size();

            Memory memory = new Memory(size * Native.WCHAR_SIZE);
            long offset = 0;
            for (String value : values) {
                memory.setWideString(offset, value);
                offset += (value.length() + 1) * Native.WCHAR_SIZE;
            }

            byte[] bytes = memory.getByteArray(0, (int) memory.size() - Native.WCHAR_SIZE);

            MemorySegment segment = ALLOCATOR.allocateArray(ValueLayout.JAVA_BYTE, bytes);

            IllegalStateException exception = assertThrows(IllegalStateException.class, () -> StringUtils.toStringList(segment));
            assertEquals(Messages.StringUtils.stringEndNotFound(bytes.length), exception.getMessage());
        }
    }

    @ParameterizedTest
    @DisplayName("fromString")
    @ValueSource(strings = { "foo", "bar" })
    @EmptySource
    void testFromString(String value) {
        MemorySegment segment = StringUtils.fromString(value, ALLOCATOR);

        byte[] bytes = segment.toArray(ValueLayout.JAVA_BYTE);

        Memory memory = new Memory(bytes.length);
        memory.write(0, bytes, 0, bytes.length);

        String result = memory.getWideString(0);

        assertEquals(value, result);
    }

    @Nested
    @DisplayName("fromStringList")
    class FromStringList {

        @Test
        @DisplayName("without empty strings")
        void testWithoutEmptyStrings() {
            List<String> values = List.of("foo", "bar");
            List<String> expected = List.of("foo", "bar", "");

            MemorySegment segment = StringUtils.fromStringList(values, ALLOCATOR);

            byte[] bytes = segment.toArray(ValueLayout.JAVA_BYTE);

            Memory memory = new Memory(bytes.length);
            memory.write(0, bytes, 0, bytes.length);

            List<String> result = new ArrayList<>();
            long offset = 0;
            while (offset < memory.size()) {
                String value = memory.getWideString(offset);
                result.add(value);
                offset += (value.length() + 1L) * Native.WCHAR_SIZE;
            }

            assertEquals(expected, result);
        }

        @Test
        @DisplayName("with empty strings")
        void testWithEmptyStrings() {
            List<String> values = List.of("foo", "bar", "", "hello", "world");
            List<String> expected = List.of("foo", "bar", "", "hello", "world", "");

            MemorySegment segment = StringUtils.fromStringList(values, ALLOCATOR);

            byte[] bytes = segment.toArray(ValueLayout.JAVA_BYTE);

            Memory memory = new Memory(bytes.length);
            memory.write(0, bytes, 0, bytes.length);

            List<String> result = new ArrayList<>();
            long offset = 0;
            while (offset < memory.size()) {
                String value = memory.getWideString(offset);
                result.add(value);
                offset += (value.length() + 1L) * Native.WCHAR_SIZE;
            }

            assertEquals(expected, result);
        }
    }
}