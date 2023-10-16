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

@SuppressWarnings("javadoc")
public final class Kernel32Utils {

    private Kernel32Utils() {
    }

    public static String expandEnvironmentStrings(String input) {
        if (input == null) {
            return ""; //$NON-NLS-1$
        }

        try (Arena allocator = Arena.ofConfined()) {
            StringPointer lpSrc = StringPointer.withValue(input, allocator);

            int result = Kernel32.INSTANCE.ExpandEnvironmentStrings(lpSrc, null, 0);
            if (result == 0) {
                throw new IllegalStateException(formatMessage(result));
            }

            StringPointer lpDst = StringPointer.uninitialized(result, allocator);

            result = Kernel32.INSTANCE.ExpandEnvironmentStrings(lpSrc, lpDst, result);
            if (result == 0) {
                throw new IllegalStateException(formatMessage(result));
            }

            return lpDst.value();
        }
    }

    public static String formatMessage(int code) {
        try (Arena allocator = Arena.ofConfined()) {
            int dwFlags = WinBase.FORMAT_MESSAGE_ALLOCATE_BUFFER | WinBase.FORMAT_MESSAGE_FROM_SYSTEM | WinBase.FORMAT_MESSAGE_IGNORE_INSERTS;
            int dwLanguageId = 0;
            StringPointer.Reference lpBuffer = StringPointer.uninitializedReference(allocator);

            int result = Kernel32.INSTANCE.FormatMessage(dwFlags, null, code, dwLanguageId, lpBuffer, 0, null);
            if (result == 0) {
                throw new IllegalStateException(Messages.Kernel32.formatMessageError(code, result));
            }

            StringPointer pointer = lpBuffer.value(result);
            try {
                return pointer
                        .value()
                        .strip();
            } finally {
                free(pointer);
            }
        }
    }

    private static void free(StringPointer pointer) {
        if (Kernel32.INSTANCE.LocalFree(pointer) != null) {
            throw new IllegalStateException(Messages.Kernel32.localFreeError(Kernel32.INSTANCE.GetLastError()));
        }
    }
}
