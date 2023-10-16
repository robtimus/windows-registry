/*
 * StringUtils.java
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

import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.lang.foreign.ValueLayout;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("javadoc")
public final class StringUtils {

    static final ValueLayout.OfChar CHAR_LAYOUT = ValueLayout.JAVA_CHAR;
    static final long CHAR_SIZE = CHAR_LAYOUT.byteSize();

    private StringUtils() {
    }

    static String toString(MemorySegment segment) {
        if (segment.byteSize() == 0) {
            return null;
        }

        int length = stringLength(segment, 0);
        char[] chars = new char[length];
        MemorySegment.copy(segment, CHAR_LAYOUT, 0, chars, 0, length);
        return new String(chars);
    }

    static List<String> toStringList(MemorySegment segment) {
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
        for (long offset = start; offset < segment.byteSize() && offset >= 0; offset += 2, length++) {
            char c = segment.get(CHAR_LAYOUT, offset);
            if (c == 0) {
                return length;
            }
        }
        throw new IllegalStateException(Messages.StringUtils.stringEndNotFound(segment.byteSize()));
    }

    static MemorySegment fromString(String value, SegmentAllocator allocator) {
        MemorySegment segment = allocator.allocate(CHAR_SIZE * (value.length() + 1L));
        copy(value, segment, 0);
        return segment;
    }

    static MemorySegment fromStringList(List<String> values, SegmentAllocator allocator) {
        long charCount = 1L + values.stream()
                .mapToLong(value -> value.length() + 1L)
                .sum();

        MemorySegment segment = allocator.allocate(CHAR_SIZE * charCount);

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

    static void copy(String value, MemorySegment segment, long start) {
        for (int i = 0; i < value.length(); i++) {
            segment.set(CHAR_LAYOUT, start + i * CHAR_SIZE, value.charAt(i));
        }
    }
}
