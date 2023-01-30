/*
 * Advapi32Extended.java
 * Copyright 2021 Rob Spoor
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

package com.github.robtimus.os.windows.registry;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.Advapi32;
import com.sun.jna.platform.win32.WinReg.HKEY;
import com.sun.jna.win32.W32APIOptions;

interface Advapi32Extended extends Advapi32 {
    Advapi32Extended INSTANCE = Native.load("Advapi32", Advapi32Extended.class, W32APIOptions.DEFAULT_OPTIONS); //$NON-NLS-1$

    int RegRenameKey(HKEY hKey, String lpSubKeyName, String lpNewKeyName);
}
