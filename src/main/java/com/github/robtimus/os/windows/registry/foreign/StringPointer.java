/*
 * StringPointer.java
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

@SuppressWarnings("javadoc")
public final class StringPointer extends Pointer {

    public static final int CHAR_SIZE = Math.toIntExact(StringUtils.CHAR_SIZE);

    private StringPointer(MemorySegment segment) {
        super(segment);
    }

    public static StringPointer withValue(String value, SegmentAllocator allocator) {
        MemorySegment segment = StringUtils.fromString(value, allocator);
        return new StringPointer(segment);
    }

    public static StringPointer uninitialized(int size, SegmentAllocator allocator) {
        MemorySegment segment = allocator.allocate(StringUtils.CHAR_LAYOUT, size + 1L);
        return new StringPointer(segment);
    }

    public static Reference uninitializedReference(SegmentAllocator allocator) {
        MemorySegment segment = allocator.allocate(Reference.LAYOUT);
        return new Reference(segment);
    }

    public String value() {
        return StringUtils.toString(segment());
    }

    public StringPointer value(String value) {
        StringUtils.copy(value, segment(), 0);
        return this;
    }

    public static final class Reference extends Pointer {

        static final AddressLayout LAYOUT = ValueLayout.ADDRESS.withTargetLayout(ValueLayout.ADDRESS);

        private Reference(MemorySegment segment) {
            super(segment);
        }

        public StringPointer value(int length) {
            AddressLayout layout = ValueLayout.ADDRESS.withTargetLayout(MemoryLayout.sequenceLayout(length + 1L, ValueLayout.JAVA_CHAR));
            MemorySegment segment = segment().get(layout, 0);
            return new StringPointer(segment);
        }
    }
}
