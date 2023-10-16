/*
 * Advapi32.java
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

import com.github.robtimus.os.windows.registry.foreign.WinBase.SECURITY_ATTRIBUTES;
import com.github.robtimus.os.windows.registry.foreign.WinDef.FILETIME;
import com.github.robtimus.os.windows.registry.foreign.WinDef.HKEY;

@SuppressWarnings("javadoc")
public interface Advapi32 {

    Advapi32 INSTANCE = new Advapi32Impl();

    int RegCloseKey/* NOSONAR */(
            HKEY hKey);

    int RegConnectRegistry/* NOSONAR */(
            StringPointer lpMachineName,
            HKEY hKey,
            HKEY.Reference phkResult);

    int RegCreateKeyEx/* NOSONAR */(
            HKEY hKey,
            StringPointer lpSubKey,
            int Reserved, // NOSONAR
            StringPointer lpClass,
            int dwOptions,
            int samDesired,
            SECURITY_ATTRIBUTES lpSecurityAttributes,
            HKEY.Reference phkResult,
            IntPointer lpdwDisposition);

    int RegDeleteKey/* NOSONAR */(
            HKEY hKey,
            StringPointer lpSubKey);

    int RegDeleteValue/* NOSONAR */(
            HKEY hKey,
            StringPointer lpValueName);

    int RegEnumKeyEx/* NOSONAR */(
            HKEY hKey,
            int dwIndex,
            StringPointer lpName,
            IntPointer lpcchName,
            IntPointer lpReserved,
            StringPointer lpClass,
            IntPointer lpcchClass,
            FILETIME lpftLastWriteTime);

    int RegEnumValue/* NOSONAR */(
            HKEY hKey,
            int dwIndex,
            StringPointer lpValueName,
            IntPointer lpcchValueName,
            IntPointer lpReserved,
            IntPointer lpType,
            BytePointer lpData,
            IntPointer lpcbData);

    int RegOpenKeyEx/* NOSONAR */(
            HKEY hKey,
            StringPointer lpSubKey,
            int ulOptions,
            int samDesired,
            HKEY.Reference phkResult);

    int RegQueryInfoKey/* NOSONAR */(
            HKEY hKey,
            StringPointer lpClass,
            IntPointer lpcClass,
            IntPointer lpReserved,
            IntPointer lpcSubKeys,
            IntPointer lpcMaxSubKeyLen,
            IntPointer lpcMaxClassLen,
            IntPointer lpcValues,
            IntPointer lpcMaxValueNameLen,
            IntPointer lpcMaxValueLen,
            IntPointer lpcbSecurityDescriptor,
            FILETIME lpftLastWriteTime);

    int RegQueryValueEx(// NOSONAR
            HKEY hKey,
            StringPointer lpValueName,
            IntPointer lpReserved,
            IntPointer lpType,
            BytePointer lpData,
            IntPointer lpcbData);

    int RegRenameKey/* NOSONAR */(
            HKEY hKey,
            StringPointer lpSubKeyName,
            StringPointer lpNewKeyName);

    int RegSetValueEx/* NOSONAR */(
            HKEY hKey,
            StringPointer lpValueName,
            int Reserved, // NOSONAR
            int dwType,
            BytePointer lpData,
            int cbData);
}
