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

import java.lang.foreign.MemorySegment;

@SuppressWarnings("javadoc")
public interface Kernel32 {

    Kernel32 INSTANCE = new Kernel32Impl();

    // The following functions all require GetLastError() to be called to retrieve any error that occurred

    /* DWORD */ int ExpandEnvironmentStrings/* NOSONAR */(
            /* LPCWSTR */ MemorySegment lpSrc,
            /* LPWSTR */ MemorySegment lpDst,
            /* DWORD */ int nSize,
            MemorySegment captureState);

    /* DWORD */ int FormatMessage/* NOSONAR */(
            /* DWORD */ int dwFlags,
            /* LPCVOID */ MemorySegment lpSource,
            /* DWORD */ int dwMessageId,
            /* DWORD */ int dwLanguageId,
            /* LPTSTR */ MemorySegment lpBuffer,
            /* DWORD */ int nSize,
            /* va_list * */ MemorySegment Arguments, // NOSONAR
            MemorySegment captureState);

    /* HLOCAL */ MemorySegment LocalFree/* NOSONAR */(
            /* HLOCAL */ MemorySegment hMem,
            MemorySegment captureState);
}
