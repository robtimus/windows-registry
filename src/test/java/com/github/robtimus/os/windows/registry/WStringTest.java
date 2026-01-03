/*
 * WStringTest.java
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

package com.github.robtimus.os.windows.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.lang.foreign.Arena;
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
class WStringTest {

    @Nested
    @DisplayName("getString")
    class GetString {

        @Test
        @DisplayName("empty segment")
        void testEmptySegment() {
            MemorySegment segment = MemorySegment.NULL;

            assertNull(WString.getString(segment));
        }

        @ParameterizedTest
        @DisplayName("non-empty segment")
        @ValueSource(strings = { "foo", "bar" })
        @EmptySource
        void testNonEmptySegment(String value) {
            try (Memory memory = new Memory((value.length() + 1L) * Native.WCHAR_SIZE);
                    Arena arena = Arena.ofConfined()) {

                memory.setWideString(0, value);
                byte[] bytes = memory.getByteArray(0, (int) memory.size());

                MemorySegment segment = arena.allocateFrom(ValueLayout.JAVA_BYTE, bytes);

                String result = WString.getString(segment);
                assertEquals(value, result);
            }
        }

        @Test
        void testMissingTerminator() {
            String value = "foo";
            try (Memory memory = new Memory((value.length() + 1L) * Native.WCHAR_SIZE);
                    Arena arena = Arena.ofConfined()) {

                memory.setWideString(0, value);
                byte[] bytes = memory.getByteArray(0, (int) memory.size() - Native.WCHAR_SIZE);

                MemorySegment segment = arena.allocateFrom(ValueLayout.JAVA_BYTE, bytes);

                IllegalStateException exception = assertThrows(IllegalStateException.class, () -> WString.getString(segment));
                assertEquals(Messages.StringUtils.stringEndNotFound(bytes.length), exception.getMessage());
            }
        }
    }

    @Nested
    @DisplayName("getStringList")
    class GetStringList {

        @Test
        @DisplayName("empty segment")
        void testEmptySegment() {
            MemorySegment segment = MemorySegment.NULL;

            assertEquals(Collections.emptyList(), WString.getStringList(segment));
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

            try (Memory memory = new Memory(size * Native.WCHAR_SIZE);
                    Arena arena = Arena.ofConfined()) {

                long offset = 0;
                for (String value : values) {
                    memory.setWideString(offset, value);
                    offset += (value.length() + 1) * Native.WCHAR_SIZE;
                }
                memory.setWideString(offset, "");

                byte[] bytes = memory.getByteArray(0, (int) memory.size());

                MemorySegment segment = arena.allocateFrom(ValueLayout.JAVA_BYTE, bytes);

                List<String> result = WString.getStringList(segment);
                assertEquals(values, result);
            }
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

            try (Memory memory = new Memory(size * Native.WCHAR_SIZE);
                    Arena arena = Arena.ofConfined()) {

                long offset = 0;
                for (String value : values) {
                    memory.setWideString(offset, value);
                    offset += (value.length() + 1) * Native.WCHAR_SIZE;
                }
                memory.setWideString(offset, "");

                byte[] bytes = memory.getByteArray(0, (int) memory.size());

                MemorySegment segment = arena.allocateFrom(ValueLayout.JAVA_BYTE, bytes);

                List<String> result = WString.getStringList(segment);
                assertEquals(values.subList(0, 2), result);
            }
        }

        @Test
        void testMissingTerminator() {
            List<String> values = List.of("foo", "bar");

            int size = values.stream()
                    .mapToInt(String::length)
                    .sum()
                    + values.size();

            try (Memory memory = new Memory(size * Native.WCHAR_SIZE);
                    Arena arena = Arena.ofConfined()) {

                long offset = 0;
                for (String value : values) {
                    memory.setWideString(offset, value);
                    offset += (value.length() + 1) * Native.WCHAR_SIZE;
                }

                byte[] bytes = memory.getByteArray(0, (int) memory.size());

                MemorySegment segment = arena.allocateFrom(ValueLayout.JAVA_BYTE, bytes);

                List<String> result = WString.getStringList(segment);
                assertEquals(values, result);
            }
        }

        @Test
        void testMissingElementTerminator() {
            List<String> values = List.of("foo", "bar");

            int size = values.stream()
                    .mapToInt(String::length)
                    .sum()
                    + values.size();

            try (Memory memory = new Memory(size * Native.WCHAR_SIZE);
                    Arena arena = Arena.ofConfined()) {

                long offset = 0;
                for (String value : values) {
                    memory.setWideString(offset, value);
                    offset += (value.length() + 1) * Native.WCHAR_SIZE;
                }

                byte[] bytes = memory.getByteArray(0, (int) memory.size() - Native.WCHAR_SIZE);

                MemorySegment segment = arena.allocateFrom(ValueLayout.JAVA_BYTE, bytes);

                IllegalStateException exception = assertThrows(IllegalStateException.class, () -> WString.getStringList(segment));
                assertEquals(Messages.StringUtils.stringEndNotFound(bytes.length), exception.getMessage());
            }
        }
    }

    @ParameterizedTest
    @DisplayName("allocate(SegmentAllocator, String)")
    @ValueSource(strings = { "foo", "bar" })
    @EmptySource
    void testAllocateFromString(String value) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = WString.allocate(arena, value);

            byte[] bytes = segment.toArray(ValueLayout.JAVA_BYTE);

            try (Memory memory = new Memory(bytes.length)) {
                memory.write(0, bytes, 0, bytes.length);

                String result = memory.getWideString(0);

                assertEquals(value, result);
            }
        }
    }

    @Nested
    @DisplayName("allocate(SegmentAllocator, List<String>)")
    class AllocateFromStringList {

        @Test
        @DisplayName("without empty strings")
        void testWithoutEmptyStrings() {
            try (Arena arena = Arena.ofConfined()) {
                List<String> values = List.of("foo", "bar");
                List<String> expected = List.of("foo", "bar", "");

                MemorySegment segment = WString.allocate(arena, values);

                byte[] bytes = segment.toArray(ValueLayout.JAVA_BYTE);

                try (Memory memory = new Memory(bytes.length)) {
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

        @Test
        @DisplayName("with empty strings")
        void testWithEmptyStrings() {
            try (Arena arena = Arena.ofConfined()) {
                List<String> values = List.of("foo", "bar", "", "hello", "world");
                List<String> expected = List.of("foo", "bar", "", "hello", "world", "");

                MemorySegment segment = WString.allocate(arena, values);

                byte[] bytes = segment.toArray(ValueLayout.JAVA_BYTE);

                try (Memory memory = new Memory(bytes.length)) {
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
    }
}
