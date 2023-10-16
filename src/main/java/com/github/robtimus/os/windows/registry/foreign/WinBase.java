/*
 * WinBase.java
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
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.lang.foreign.StructLayout;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.VarHandle;

@SuppressWarnings("javadoc")
public final class WinBase {

    public static final int FORMAT_MESSAGE_ALLOCATE_BUFFER = 0x100;
    public static final int FORMAT_MESSAGE_IGNORE_INSERTS = 0x200;
    public static final int FORMAT_MESSAGE_FROM_SYSTEM = 0x1000;

    private WinBase() {
    }

    public static final class SECURITY_ATTRIBUTES /* NOSONAR */ extends Structure {

        static final StructLayout LAYOUT = MemoryLayout.structLayout(
                ValueLayout.JAVA_INT.withName("nLength"), //$NON-NLS-1$
                ValueLayout.ADDRESS.withName("lpSecurityDescriptor"), //$NON-NLS-1$
                ValueLayout.JAVA_BOOLEAN.withName("bInheritHandle")); //$NON-NLS-1$

        private static final VarHandle N_LENGTH = LAYOUT.varHandle(groupElement("nLength")); //$NON-NLS-1$
        private static final VarHandle LP_SECURITY_DESCRIPTOR = LAYOUT.varHandle(groupElement("lpSecurityDescriptor")); //$NON-NLS-1$
        private static final VarHandle B_INHERIT_HANDLE = LAYOUT.varHandle(groupElement("bInheritHandle")); //$NON-NLS-1$

        public SECURITY_ATTRIBUTES(SegmentAllocator allocator) {
            super(LAYOUT, allocator);
        }

        public int nLength() {
            return (int) N_LENGTH.get(segment());
        }

        public SECURITY_ATTRIBUTES nLength(int value) {
            N_LENGTH.set(segment(), value);
            return this;
        }

        public Pointer lpSecurityDescriptor() {
            MemorySegment segment = (MemorySegment) LP_SECURITY_DESCRIPTOR.get(segment());
            return segment == null || MemorySegment.NULL.equals(segment)
                    ? null
                    : new Pointer(segment);
        }

        public SECURITY_ATTRIBUTES lpSecurityDescriptor(Pointer value) {
            MemorySegment segment = value == null
                    ? null
                    : value.segment();
            LP_SECURITY_DESCRIPTOR.set(segment(), segment);
            return this;
        }

        public boolean bInheritHandle() {
            return (boolean) B_INHERIT_HANDLE.get(segment());
        }

        public SECURITY_ATTRIBUTES bInheritHandle(boolean value) {
            B_INHERIT_HANDLE.set(segment(), value);
            return this;
        }

        @Override
        @SuppressWarnings("nls")
        public String toString() {
            return "SECURITY_ATTRIBUTES@0x%x (nLength = %d, lpSecurityDescriptor = %s, bInheritHandle = %b)"
                    .formatted(segment().address(), nLength(), lpSecurityDescriptor(), bInheritHandle());
        }
    }
}
