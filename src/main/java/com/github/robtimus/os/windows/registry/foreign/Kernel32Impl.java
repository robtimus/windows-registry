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
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

final class Kernel32Impl extends ApiImpl implements Kernel32 {

    private final MethodHandle expandEnvironmentStrings;
    private final MethodHandle formatMessage;
    private final MethodHandle getLastError;
    private final MethodHandle localFree;

    @SuppressWarnings("nls")
    Kernel32Impl() {
        Linker linker = Linker.nativeLinker();
        SymbolLookup symbolLookup = SymbolLookup.libraryLookup("Kernel32", ARENA);

        expandEnvironmentStrings = functionMethodHandle(linker, symbolLookup, "ExpandEnvironmentStringsW", ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS, // lpSrc
                ValueLayout.ADDRESS, // lpDst
                ValueLayout.JAVA_INT); // nSize

        formatMessage = functionMethodHandle(linker, symbolLookup, "FormatMessageW", ValueLayout.JAVA_INT,
                ValueLayout.JAVA_INT, // dwFlags
                ValueLayout.ADDRESS, // lpSource
                ValueLayout.JAVA_INT, // dwMessageId
                ValueLayout.JAVA_INT, // dwLanguageId
                ValueLayout.ADDRESS, // lpBuffer
                ValueLayout.JAVA_INT, // nSize
                ValueLayout.ADDRESS); // Arguments

        getLastError = functionMethodHandle(linker, symbolLookup, "GetLastError", ValueLayout.JAVA_INT);

        localFree = functionMethodHandle(linker, symbolLookup, "LocalFree", ValueLayout.ADDRESS,
                ValueLayout.ADDRESS); // hMem
    }

    @Override
    public int ExpandEnvironmentStrings(
            StringPointer lpSrc,
            StringPointer lpDst,
            int nSize) {

        try {
            return (int) expandEnvironmentStrings.invokeExact(
                    lpSrc.segment(),
                    segment(lpDst),
                    nSize);
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public int FormatMessage(
            int dwFlags,
            Pointer lpSource,
            int dwMessageId,
            int dwLanguageId,
            StringPointer.Reference lpBuffer,
            int nSize,
            Pointer Arguments) { // NOSONAR

        try {
            return (int) formatMessage.invokeExact(
                    dwFlags,
                    segment(lpSource),
                    dwMessageId,
                    dwLanguageId,
                    lpBuffer.segment(),
                    nSize,
                    segment(Arguments));
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public int GetLastError() {
        try {
            return (int) getLastError.invokeExact();
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public Pointer LocalFree(
            Pointer hMem) {

        MemorySegment result = invokeLocalFree(hMem);

        return result != null && !MemorySegment.NULL.equals(result)
                ? new Pointer(result)
                : null;
    }

    private MemorySegment invokeLocalFree(Pointer hMem) {
        try {
            return (MemorySegment) localFree.invokeExact(
                    segment(hMem));
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        }
    }
}
