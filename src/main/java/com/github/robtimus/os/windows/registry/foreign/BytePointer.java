/*
 * BytePointer.java
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
import java.util.List;

@SuppressWarnings("javadoc")
public final class BytePointer extends Pointer {

    private BytePointer(MemorySegment segment) {
        super(segment);
    }

    public static BytePointer unitialized(int size, SegmentAllocator allocator) {
        MemorySegment segment = allocator.allocateArray(ValueLayout.JAVA_BYTE, size);
        return new BytePointer(segment);
    }

    public static BytePointer withBytes(byte[] bytes, SegmentAllocator allocator) {
        MemorySegment segment = allocator.allocateArray(ValueLayout.JAVA_BYTE, bytes);
        return new BytePointer(segment);
    }

    public static BytePointer withInt(int value, ValueLayout.OfInt layout, SegmentAllocator allocator) {
        MemorySegment segment = allocator.allocate(layout, value);
        return new BytePointer(segment);
    }

    public static BytePointer withLong(long value, ValueLayout.OfLong layout, SegmentAllocator allocator) {
        MemorySegment segment = allocator.allocate(layout, value);
        return new BytePointer(segment);
    }

    public static BytePointer withString(String value, SegmentAllocator allocator) {
        MemorySegment segment = StringUtils.fromString(value, allocator);
        return new BytePointer(segment);
    }

    public static BytePointer withStringList(List<String> values, SegmentAllocator allocator) {
        MemorySegment segment = StringUtils.fromStringList(values, allocator);
        return new BytePointer(segment);
    }

    public int toInt(ValueLayout.OfInt layout) {
        return segment().get(layout, 0);
    }

    public long toLong(ValueLayout.OfLong layout) {
        return segment().get(layout, 0);
    }

    public String toString(int length) {
        // TODO: if the segment does not end with a '\0', increase length if possible
        MemorySegment segment = segment().asSlice(0, length);
        return StringUtils.toString(segment);
    }

    public List<String> toStringList(int length) {
        // TODO: if the segment does not end with a '\0\0', increase length if possible
        MemorySegment segment = segment().asSlice(0, length);
        return StringUtils.toStringList(segment);
    }

    public byte[] toByteArray() {
        return segment().toArray(ValueLayout.JAVA_BYTE);
    }

    public byte[] toByteArray(int length) {
        return segment().asSlice(0, length).toArray(ValueLayout.JAVA_BYTE);
    }
}
