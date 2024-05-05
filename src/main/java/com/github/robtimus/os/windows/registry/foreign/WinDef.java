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

    public static final class HKEY extends Pointer {

        static final AddressLayout LAYOUT = ValueLayout.ADDRESS;

        HKEY(MemorySegment segment) {
            super(segment.asReadOnly());
        }

        public static Reference uninitializedReference(SegmentAllocator allocator) {
            MemorySegment segment = allocator.allocate(Reference.LAYOUT);
            return new Reference(segment);
        }

        public static final class Reference extends Pointer {

            static final AddressLayout LAYOUT = ValueLayout.ADDRESS.withTargetLayout(HKEY.LAYOUT);

            private Reference(MemorySegment segment) {
                super(segment);
            }

            public HKEY value() {
                MemorySegment segment = segment().get(HKEY.LAYOUT, 0);
                return new HKEY(segment);
            }

            void value(HKEY value) {
                segment().set(HKEY.LAYOUT, 0, value.segment());
            }
        }
    }

    public static final class FILETIME extends Structure {

        private static final String DW_LOW_DATE_TIME_NAME = "dwLowDateTime"; //$NON-NLS-1$
        private static final String DW_HIGH_DATE_TIME_NAME = "dwHighDateTime"; //$NON-NLS-1$

        static final StructLayout LAYOUT = MemoryLayout.structLayout(
                ValueLayout.JAVA_INT.withName(DW_LOW_DATE_TIME_NAME),
                ValueLayout.JAVA_INT.withName(DW_HIGH_DATE_TIME_NAME));

        private static final VarHandle DW_LOW_DATE_TIME = insertCoordinates(LAYOUT.varHandle(groupElement(DW_LOW_DATE_TIME_NAME)), 1, 0L);
        private static final VarHandle DW_HIGH_DATE_TIME = insertCoordinates(LAYOUT.varHandle(groupElement(DW_HIGH_DATE_TIME_NAME)), 1, 0L);

        public FILETIME(SegmentAllocator allocator) {
            super(LAYOUT, allocator);
        }

        public int dwLowDateTime() {
            return (int) DW_LOW_DATE_TIME.get(segment());
        }

        public FILETIME dwLowDateTime(int value) {
            DW_LOW_DATE_TIME.set(segment(), value);
            return this;
        }

        public int dwHighDateTime() {
            return (int) DW_HIGH_DATE_TIME.get(segment());
        }

        public FILETIME dwHighDateTime(int value) {
            DW_HIGH_DATE_TIME.set(segment(), value);
            return this;
        }
    }
}
