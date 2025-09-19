/*
 * WinDef.java
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

import static java.lang.foreign.MemoryLayout.PathElement.groupElement;
import static java.lang.invoke.MethodHandles.insertCoordinates;
import java.lang.foreign.AddressLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.lang.foreign.StructLayout;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.VarHandle;

@SuppressWarnings("javadoc")
public final class WinDef {

    private WinDef() {
    }

    public static final class HKEY {

        private static final AddressLayout LAYOUT = ValueLayout.ADDRESS;
        private static final AddressLayout REFERENCE_LAYOUT = ValueLayout.ADDRESS.withTargetLayout(LAYOUT);

        private HKEY() {
        }

        public static MemorySegment allocateRef(SegmentAllocator allocator) {
            return allocator.allocate(REFERENCE_LAYOUT);
        }

        public static MemorySegment target(MemorySegment ref) {
            return ref.get(LAYOUT, 0);
        }
    }

    public static final class FILETIME {

        private static final String DW_LOW_DATE_TIME_NAME = "dwLowDateTime"; //$NON-NLS-1$
        private static final String DW_HIGH_DATE_TIME_NAME = "dwHighDateTime"; //$NON-NLS-1$

        private static final StructLayout LAYOUT = MemoryLayout.structLayout(
                ValueLayout.JAVA_INT.withName(DW_LOW_DATE_TIME_NAME),
                ValueLayout.JAVA_INT.withName(DW_HIGH_DATE_TIME_NAME));

        private static final VarHandle DW_LOW_DATE_TIME = insertCoordinates(LAYOUT.varHandle(groupElement(DW_LOW_DATE_TIME_NAME)), 1, 0L);
        private static final VarHandle DW_HIGH_DATE_TIME = insertCoordinates(LAYOUT.varHandle(groupElement(DW_HIGH_DATE_TIME_NAME)), 1, 0L);

        private FILETIME() {
        }

        public static MemorySegment allocate(SegmentAllocator allocator) {
            return allocator.allocate(LAYOUT);
        }

        public static int dwLowDateTime(MemorySegment segment) {
            return (int) DW_LOW_DATE_TIME.get(segment);
        }

        public static void dwLowDateTime(MemorySegment segment, int value) {
            DW_LOW_DATE_TIME.set(segment, value);
        }

        public static int dwHighDateTime(MemorySegment segment) {
            return (int) DW_HIGH_DATE_TIME.get(segment);
        }

        public static void dwHighDateTime(MemorySegment segment, int value) {
            DW_HIGH_DATE_TIME.set(segment, value);
        }
    }
}
