/*
 * WString.java
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

import java.lang.foreign.AddressLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.lang.foreign.ValueLayout;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("javadoc")
public final class WString {

    // LPWSTR and LPCWSTR are explicitly 16-bit, see
    // https://learn.microsoft.com/en-us/openspecs/windows_protocols/ms-dtyp/50e9ef83-d6fd-4e22-a34a-2c6b4e3c24f3 and
    // https://learn.microsoft.com/en-us/openspecs/windows_protocols/ms-dtyp/76f10dd8-699d-45e6-a53c-5aefc586da20
    // That means there's no need to lookup the size for wchar_t

    private static final ValueLayout.OfChar CHAR_LAYOUT = ValueLayout.JAVA_CHAR;
    public static final long CHAR_SIZE = CHAR_LAYOUT.byteSize();

    private static final AddressLayout REFERENCE_LAYOUT = ValueLayout.ADDRESS.withTargetLayout(ValueLayout.ADDRESS);

    private WString() {
    }

    public static MemorySegment allocate(SegmentAllocator allocator, int size) {
        return allocator.allocate(WString.CHAR_LAYOUT, size + 1L);
    }

    public static MemorySegment allocateRef(SegmentAllocator allocator) {
        return allocator.allocate(REFERENCE_LAYOUT);
    }

    public static MemorySegment target(MemorySegment ref, int length) {
        AddressLayout layout = ValueLayout.ADDRESS.withTargetLayout(MemoryLayout.sequenceLayout(length + 1L, ValueLayout.JAVA_CHAR));
        return ref.get(layout, 0);
    }

    public static String getString(MemorySegment segment) {
        if (segment.byteSize() == 0) {
            return null;
        }

        int length = stringLength(segment, 0);
        return getString(segment, length);
    }

    public static String getString(MemorySegment segment, int length) {
        char[] chars = new char[length];
        MemorySegment.copy(segment, CHAR_LAYOUT, 0, chars, 0, length);
        return new String(chars);
    }

    public static List<String> getStringList(MemorySegment segment) {
        List<String> result = new ArrayList<>();
        long offset = 0;
        while (offset < segment.byteSize()) {
            int length = stringLength(segment, offset);
            if (length == 0) {
                // A sequence of null-terminated strings, terminated by an empty string (\0).
                // => The first empty string terminates the string list
                break;
            }
            char[] chars = new char[length];
            MemorySegment.copy(segment, CHAR_LAYOUT, offset, chars, 0, length);
            String value = new String(chars);

            result.add(value);

            offset += (length + 1L) * CHAR_SIZE;
        }
        return result;
    }

    private static int stringLength(MemorySegment segment, long start) {
        // add offset >= 0 check to guard against overflow
        int length = 0;
        for (long offset = start; offset < segment.byteSize() && offset >= 0; offset += CHAR_SIZE, length++) {
            char c = segment.get(CHAR_LAYOUT, offset);
            if (c == '\0') {
                return length;
            }
        }
        throw new IllegalStateException(Messages.StringUtils.stringEndNotFound(segment.byteSize()));
    }

    public static MemorySegment allocate(SegmentAllocator allocator, String value) {
        MemorySegment segment = allocator.allocate(CHAR_LAYOUT, value.length() + 1L);
        copy(value, segment, 0);
        return segment;
    }

    public static MemorySegment allocate(SegmentAllocator allocator, List<String> values) {
        long charCount = 1L + values.stream()
                .mapToLong(value -> value.length() + 1L)
                .sum();

        MemorySegment segment = allocator.allocate(CHAR_LAYOUT, charCount);

        long offset = 0;
        for (String value : values) {
            copy(value, segment, offset);
            offset += CHAR_SIZE * value.length();
            segment.set(CHAR_LAYOUT, offset, '\0');
            offset += CHAR_SIZE;
        }
        segment.set(CHAR_LAYOUT, offset, '\0');

        return segment;
    }

    public static void copy(String value, MemorySegment segment, long start) {
        char[] chars = value.toCharArray();
        MemorySegment.copy(chars, 0, segment, CHAR_LAYOUT, start, value.length());
    }
}
