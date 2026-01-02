/*
 * Kernel32.java
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

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

@SuppressWarnings({ "javadoc", "nls" })
public final class Kernel32 extends WindowsApi {

    private static final MethodHandle EXPAND_ENVIRONMENT_STRINGS;
    private static final MethodHandle FORMAT_MESSAGE;
    private static final MethodHandle LOCAL_FREE;
    private static final MethodHandle CLOSE_HANDLE;

    static {
        Linker linker = Linker.nativeLinker();
        SymbolLookup kernel32 = SymbolLookup.libraryLookup("Kernel32", ARENA);

        EXPAND_ENVIRONMENT_STRINGS = linker.downcallHandle(kernel32.findOrThrow("ExpandEnvironmentStringsW"), FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS, // lpSrc
                ValueLayout.ADDRESS, // lpDst
                ValueLayout.JAVA_INT), // nSize
                CaptureState.LINKER_OPTION);

        FORMAT_MESSAGE = linker.downcallHandle(kernel32.findOrThrow("FormatMessageW"), FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_INT, // dwFlags
                ValueLayout.ADDRESS, // lpSource
                ValueLayout.JAVA_INT, // dwMessageId
                ValueLayout.JAVA_INT, // dwLanguageId
                ValueLayout.ADDRESS, // lpBuffer
                ValueLayout.JAVA_INT, // nSize
                ValueLayout.ADDRESS), // Arguments
                CaptureState.LINKER_OPTION);

        LOCAL_FREE = linker.downcallHandle(kernel32.findOrThrow("LocalFree"), FunctionDescriptor.of(
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS), // hMem
                CaptureState.LINKER_OPTION);

        CLOSE_HANDLE = linker.downcallHandle(kernel32.findOrThrow("CloseHandle"), FunctionDescriptor.of(
                ValueLayout.JAVA_BOOLEAN,
                ValueLayout.ADDRESS), // hObject
                CaptureState.LINKER_OPTION);
    }

    private static final int FORMAT_MESSAGE_ALLOCATE_BUFFER = 0x100;
    private static final int FORMAT_MESSAGE_IGNORE_INSERTS = 0x200;
    private static final int FORMAT_MESSAGE_FROM_SYSTEM = 0x1000;

    private Kernel32() {
    }

    // The following functions all require GetLastError() to be called to retrieve any error that occurred

    public static String expandEnvironmentStrings(String input) {
        if (input == null) {
            return ""; //$NON-NLS-1$
        }

        try (Arena allocator = Arena.ofConfined()) {
            MemorySegment lpSrc = WString.allocate(allocator, input);
            MemorySegment captureState = CaptureState.allocate(allocator);

            int result = ExpandEnvironmentStrings(lpSrc,
                    MemorySegment.NULL,
                    0,
                    captureState);
            if (result == 0) {
                throw new IllegalStateException(formatMessage(CaptureState.getLastError(captureState)));
            }

            MemorySegment lpDst = WString.allocate(allocator, result);

            result = ExpandEnvironmentStrings(lpSrc,
                    lpDst,
                    result,
                    captureState);
            if (result == 0) {
                throw new IllegalStateException(formatMessage(CaptureState.getLastError(captureState)));
            }

            // result is the number of characters including the terminating character, but WString also includes it.
            // Subtract 1 to not include it in the result.
            return WString.getString(lpDst, result - 1);
        }
    }

    /*
     * DWORD ExpandEnvironmentStringsW(
     *   [in]            LPCWSTR lpSrc,
     *   [out, optional] LPWSTR  lpDst,
     *   [in]            DWORD   nSize
     * )
     */
    private static int ExpandEnvironmentStrings/* NOSONAR */(
            MemorySegment lpSrc,
            MemorySegment lpDst,
            int nSize,
            MemorySegment captureState) {

        try {
            return (int) EXPAND_ENVIRONMENT_STRINGS.invokeExact(
                    captureState,
                    lpSrc,
                    lpDst,
                    nSize);
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        }
    }

    public static String formatMessage(int code) {
        try (Arena allocator = Arena.ofConfined()) {
            int dwFlags = FORMAT_MESSAGE_ALLOCATE_BUFFER | FORMAT_MESSAGE_FROM_SYSTEM | FORMAT_MESSAGE_IGNORE_INSERTS;
            int dwLanguageId = 0;
            MemorySegment lpBuffer = WString.allocateRef(allocator);
            MemorySegment captureState = CaptureState.allocate(allocator);

            int result = FormatMessage(dwFlags,
                    MemorySegment.NULL,
                    code,
                    dwLanguageId,
                    lpBuffer,
                    0,
                    MemorySegment.NULL,
                    captureState);
            if (result == 0) {
                throw new IllegalStateException(Messages.Kernel32.formatMessageError(code, CaptureState.getLastError(captureState)));
            }

            MemorySegment pointer = WString.target(lpBuffer, result);
            try {
                // result is the number of characters excluding the terminating character
                return WString.getString(pointer, result)
                        .strip();
            } finally {
                free(pointer, captureState);
            }
        }
    }

    /*
     * DWORD FormatMessage(
     *   [in]           DWORD   dwFlags,
     *   [in, optional] LPCVOID lpSource,
     *   [in]           DWORD   dwMessageId,
     *   [in]           DWORD   dwLanguageId,
     *   [out]          LPTSTR  lpBuffer,
     *   [in]           DWORD   nSize,
     *   [in, optional] va_list *Arguments
     * )
     */
    private static int FormatMessage/* NOSONAR */(
            int dwFlags,
            MemorySegment lpSource,
            int dwMessageId,
            int dwLanguageId,
            MemorySegment lpBuffer,
            int nSize,
            MemorySegment Arguments, // NOSONAR
            MemorySegment captureState) {

        try {
            return (int) FORMAT_MESSAGE.invokeExact(
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

    private static void free(MemorySegment hMem, MemorySegment captureState) {
        MemorySegment result = LocalFree(hMem, captureState);
        if (result == null || MemorySegment.NULL.equals(result)) {
            return;
        }
        if (!result.equals(hMem)) {
            throw new IllegalStateException(Messages.Kernel32.localFreeUnexpectedResult(hMem, result));
        }
        throw new IllegalStateException(Messages.Kernel32.localFreeError(CaptureState.getLastError(captureState)));
    }

    /*
     * HLOCAL LocalFree(
     *   [in] _Frees_ptr_opt_ HLOCAL hMem
     * )
     */
    private static MemorySegment LocalFree/* NOSONAR */(
            MemorySegment hMem,
            MemorySegment captureState) {

        try {
            return (MemorySegment) LOCAL_FREE.invokeExact(
                    captureState,
                    hMem);
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        }
    }

    /*
     * BOOL CloseHandle(
     *   [in] HANDLE hObject
     * )
     */
    public static boolean CloseHandle/* NOSONAR */(
            MemorySegment hObject,
            MemorySegment captureState) {

        try {
            return (boolean) CLOSE_HANDLE.invokeExact(
                    captureState,
                    hObject);
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        }
    }
}
