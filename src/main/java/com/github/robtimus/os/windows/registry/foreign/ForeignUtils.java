/*
 * ForeignUtils.java
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

import java.lang.System.Logger.Level;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.Optional;

@SuppressWarnings("javadoc")
public final class ForeignUtils {

    static final Arena ARENA = Arena.ofAuto();

    private ForeignUtils() {
    }

    static Optional<SymbolLookup> optionalSymbolLookup(String name, Arena arena) {
        try {
            return Optional.of(SymbolLookup.libraryLookup(name, arena));
        } catch (IllegalArgumentException e) {
            System.getLogger("windows-registry").log(Level.WARNING, e.getMessage()); //$NON-NLS-1$
            return Optional.empty();
        }
    }

    static MethodHandle functionMethodHandle(Linker linker, SymbolLookup symbolLookup, String name,
            FunctionDescriptor function, Linker.Option... options) {

        return optionalFunctionMethodHandle(linker, symbolLookup, name, function, options)
                .orElseThrow(() -> new IllegalStateException(Messages.ApiImpl.functionNotFound(name)));
    }

    static Optional<MethodHandle> optionalFunctionMethodHandle(Linker linker, SymbolLookup symbolLookup, String name,
            FunctionDescriptor function, Linker.Option... options) {

        return symbolLookup.find(name)
                .map(address -> linker.downcallHandle(address, function, options));
    }

    static Optional<MethodHandle> optionalFunctionMethodHandle(Linker linker, Optional<SymbolLookup> symbolLookup, String name,
            FunctionDescriptor function, Linker.Option... options) {

        return symbolLookup.flatMap(lookup -> optionalFunctionMethodHandle(linker, lookup, name, function, options));
    }

    public static MemorySegment allocateBytes(SegmentAllocator allocator, byte[] bytes) {
        return allocator.allocateFrom(ValueLayout.JAVA_BYTE, bytes);
    }

    public static MemorySegment allocateBytes(SegmentAllocator allocator, long byteCount) {
        return allocator.allocate(ValueLayout.JAVA_BYTE, byteCount);
    }

    public static MemorySegment allocateInt(SegmentAllocator allocator) {
        return allocator.allocate(ValueLayout.JAVA_INT);
    }

    public static MemorySegment allocateInt(SegmentAllocator allocator, int value) {
        return allocateInt(allocator, ValueLayout.JAVA_INT, value);
    }

    public static MemorySegment allocateInt(SegmentAllocator allocator, long value) {
        return allocateInt(allocator, Math.toIntExact(value));
    }

    public static MemorySegment allocateInt(SegmentAllocator allocator, ValueLayout.OfInt layout, int value) {
        return allocator.allocateFrom(layout, value);
    }

    public static MemorySegment allocateLong(SegmentAllocator allocator, ValueLayout.OfLong layout, long value) {
        return allocator.allocateFrom(layout, value);
    }

    public static byte[] toByteArray(MemorySegment segment) {
        return segment.toArray(ValueLayout.JAVA_BYTE);
    }

    public static int getInt(MemorySegment segment) {
        return getInt(segment, ValueLayout.JAVA_INT);
    }

    public static int getInt(MemorySegment segment, ValueLayout.OfInt layout) {
        return segment.get(layout, 0);
    }

    public static void setInt(MemorySegment segment, int value) {
        segment.set(ValueLayout.JAVA_INT, 0, value);
    }

    public static void setInt(MemorySegment segment, long value) {
        setInt(segment, Math.toIntExact(value));
    }

    public static long getLong(MemorySegment segment, ValueLayout.OfLong layout) {
        return segment.get(layout, 0);
    }

    public static void clear(MemorySegment segment) {
        segment.fill((byte) 0);
    }
}
