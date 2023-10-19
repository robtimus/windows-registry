/*
 * IntPointer.java
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

@SuppressWarnings("javadoc")
public final class IntPointer extends Pointer {

    static final ValueLayout.OfInt LAYOUT = ValueLayout.JAVA_INT;

    private IntPointer(MemorySegment segment) {
        super(segment);
    }

    public static IntPointer withValue(int value, SegmentAllocator allocator) {
        MemorySegment segment = allocator.allocate(LAYOUT, value);
        return new IntPointer(segment);
    }

    public static IntPointer uninitialized(SegmentAllocator allocator) {
        MemorySegment segment = allocator.allocate(LAYOUT, 0);
        return new IntPointer(segment);
    }

    public int value() {
        return segment().get(LAYOUT, 0);
    }

    public IntPointer value(int value) {
        segment().set(LAYOUT, 0, value);
        return this;
    }
}
