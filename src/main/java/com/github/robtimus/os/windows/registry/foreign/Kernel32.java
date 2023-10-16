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

@SuppressWarnings("javadoc")
public interface Kernel32 {

    Kernel32 INSTANCE = new Kernel32Impl();

    int ExpandEnvironmentStrings/* NOSONAR */(
            StringPointer lpSrc,
            StringPointer lpDst,
            int nSize);

    int FormatMessage/* NOSONAR */(
            int dwFlags,
            Pointer lpSource,
            int dwMessageId,
            int dwLanguageId,
            StringPointer.Reference lpBuffer,
            int nSize,
            Pointer Arguments); // NOSONAR

    int GetLastError/* NOSONAR */();

    Pointer LocalFree/* NOSONAR */(
            Pointer hMem);
}
