/*
 * Kernel32Utils.java
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
import java.lang.foreign.MemorySegment;

@SuppressWarnings("javadoc")
public final class Kernel32Utils {

    private Kernel32Utils() {
    }

    public static String expandEnvironmentStrings(String input) {
        if (input == null) {
            return ""; //$NON-NLS-1$
        }

        try (Arena allocator = Arena.ofConfined()) {
            MemorySegment lpSrc = WString.allocate(allocator, input);
            MemorySegment captureState = CaptureState.allocate(allocator);

            int result = Kernel32.INSTANCE.ExpandEnvironmentStrings(lpSrc,
                    MemorySegment.NULL,
                    0,
                    captureState);
            if (result == 0) {
                throw new IllegalStateException(formatMessage(CaptureState.getLastError(captureState)));
            }

            MemorySegment lpDst = WString.allocate(allocator, result);

            result = Kernel32.INSTANCE.ExpandEnvironmentStrings(lpSrc,
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

    public static String formatMessage(int code) {
        try (Arena allocator = Arena.ofConfined()) {
            int dwFlags = WinBase.FORMAT_MESSAGE_ALLOCATE_BUFFER | WinBase.FORMAT_MESSAGE_FROM_SYSTEM | WinBase.FORMAT_MESSAGE_IGNORE_INSERTS;
            int dwLanguageId = 0;
            MemorySegment lpBuffer = WString.allocateRef(allocator);
            MemorySegment captureState = CaptureState.allocate(allocator);

            int result = Kernel32.INSTANCE.FormatMessage(dwFlags,
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

    private static void free(MemorySegment pointer, MemorySegment captureState) {
        if (Kernel32.INSTANCE.LocalFree(pointer, captureState) != null) {
            throw new IllegalStateException(Messages.Kernel32.localFreeError(CaptureState.getLastError(captureState)));
        }
    }
}
