/*
 * Kernel32Impl.java
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

import static com.github.robtimus.os.windows.registry.foreign.ForeignUtils.ARENA;
import static com.github.robtimus.os.windows.registry.foreign.ForeignUtils.functionMethodHandle;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

final class Kernel32Impl implements Kernel32 {

    private final MethodHandle expandEnvironmentStrings;
    private final MethodHandle formatMessage;
    private final MethodHandle localFree;

    @SuppressWarnings("nls")
    Kernel32Impl() {
        Linker linker = Linker.nativeLinker();
        SymbolLookup symbolLookup = SymbolLookup.libraryLookup("Kernel32", ARENA);

        expandEnvironmentStrings = functionMethodHandle(linker, symbolLookup, "ExpandEnvironmentStringsW", FunctionDescriptor.of(ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS, // lpSrc
                ValueLayout.ADDRESS, // lpDst
                ValueLayout.JAVA_INT), // nSize
                CaptureState.LINKER_OPTION);

        formatMessage = functionMethodHandle(linker, symbolLookup, "FormatMessageW", FunctionDescriptor.of(ValueLayout.JAVA_INT,
                ValueLayout.JAVA_INT, // dwFlags
                ValueLayout.ADDRESS, // lpSource
                ValueLayout.JAVA_INT, // dwMessageId
                ValueLayout.JAVA_INT, // dwLanguageId
                ValueLayout.ADDRESS, // lpBuffer
                ValueLayout.JAVA_INT, // nSize
                ValueLayout.ADDRESS), // Arguments
                CaptureState.LINKER_OPTION);

        localFree = functionMethodHandle(linker, symbolLookup, "LocalFree", FunctionDescriptor.of(ValueLayout.ADDRESS,
                ValueLayout.ADDRESS), // hMem
                CaptureState.LINKER_OPTION);
    }

    @Override
    public int ExpandEnvironmentStrings(
            MemorySegment lpSrc,
            MemorySegment lpDst,
            int nSize,
            MemorySegment captureState) {

        try {
            return (int) expandEnvironmentStrings.invokeExact(
                    captureState,
                    lpSrc,
                    lpDst,
                    nSize);
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public int FormatMessage(
            int dwFlags,
            MemorySegment lpSource,
            int dwMessageId,
            int dwLanguageId,
            MemorySegment lpBuffer,
            int nSize,
            MemorySegment Arguments, // NOSONAR
            MemorySegment captureState) {

        try {
            return (int) formatMessage.invokeExact(
                    captureState,
                    dwFlags,
                    lpSource,
                    dwMessageId,
                    dwLanguageId,
                    lpBuffer,
                    nSize,
                    Arguments);
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public MemorySegment LocalFree(
            MemorySegment hMem,
            MemorySegment captureState) {

        MemorySegment result = invokeLocalFree(hMem, captureState);

        if (result == null || MemorySegment.NULL.equals(result)) {
            return null;
        }
        if (result.equals(hMem)) {
            return hMem;
        }
        throw new IllegalStateException(Messages.Kernel32.localFreeUnexpectedResult(hMem, result));
    }

    private MemorySegment invokeLocalFree(MemorySegment segment, MemorySegment captureState) {
        try {
            return (MemorySegment) localFree.invokeExact(
                    captureState,
                    segment);
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        }
    }
}
