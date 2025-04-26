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

import java.lang.foreign.MemorySegment;

@SuppressWarnings("javadoc")
public interface Advapi32 {

    Advapi32 INSTANCE = new Advapi32Impl();

    // The following functions all return any error and do not require GetLastError() to be called; CaptureState is therefore not needed

    /* LSTATUS */ int RegCloseKey/* NOSONAR */(
            /* HKEY */ MemorySegment hKey);

    /* LSTATUS */ int RegConnectRegistry/* NOSONAR */(
            /* LPCWSTR */ MemorySegment lpMachineName,
            /* HKEY */ MemorySegment hKey,
            /* PHKEY */ MemorySegment phkResult);

    /* LSTATUS */ int RegCreateKeyEx/* NOSONAR */(
            /* HKEY */ MemorySegment hKey,
            /* LPCWSTR */ MemorySegment lpSubKey,
            /* DWORD */ int Reserved, // NOSONAR
            /* LPWSTR */ MemorySegment lpClass,
            /* DWORD */ int dwOptions,
            /* REGSAM */ int samDesired,
            /* const LPSECURITY_ATTRIBUTES */ MemorySegment lpSecurityAttributes,
            /* PHKEY */ MemorySegment phkResult,
            /* LPDWORD */ MemorySegment lpdwDisposition);

    /* LSTATUS */ int RegDeleteKey/* NOSONAR */(
            /* HKEY */ MemorySegment hKey,
            /* LPCWSTR */ MemorySegment lpSubKey);

    /* LSTATUS */ int RegDeleteValue/* NOSONAR */(
            /* HKEY */ MemorySegment hKey,
            /* LPCWSTR */ MemorySegment lpValueName);

    /* LSTATUS */ int RegEnumKeyEx/* NOSONAR */(
            /* HKEY */ MemorySegment hKey,
            /* DWORD */ int dwIndex,
            /* LPWSTR  */ MemorySegment lpName,
            /* LPDWORD */ MemorySegment lpcchName,
            /* LPDWORD */ MemorySegment lpReserved,
            /* LPWSTR */ MemorySegment lpClass,
            /* LPDWORD */ MemorySegment lpcchClass,
            /* PFILETIME */ MemorySegment lpftLastWriteTime);

    /* LSTATUS */ int RegEnumValue/* NOSONAR */(
            /* HKEY */ MemorySegment hKey,
            /* DWORD */ int dwIndex,
            /* LPWSTR */ MemorySegment lpValueName,
            /* LPDWORD */ MemorySegment lpcchValueName,
            /* LPDWORD */ MemorySegment lpReserved,
            /* LPDWORD */ MemorySegment lpType,
            /* LPBYTE */ MemorySegment lpData,
            /* LPDWORD */ MemorySegment lpcbData);

    /* LSTATUS */ int RegOpenKeyEx/* NOSONAR */(
            /* HKEY */ MemorySegment hKey,
            /* LPCWSTR */ MemorySegment lpSubKey,
            /* DWORD */ int ulOptions,
            /* REGSAM */ int samDesired,
            /* PHKEY */ MemorySegment phkResult);

    /* LSTATUS */ int RegQueryInfoKey/* NOSONAR */(
            /* HKEY */ MemorySegment hKey,
            /* LPWSTR */ MemorySegment lpClass,
            /* LPDWORD */ MemorySegment lpcchClass,
            /* LPDWORD */ MemorySegment lpReserved,
            /* LPDWORD */ MemorySegment lpcSubKeys,
            /* LPDWORD */ MemorySegment lpcbMaxSubKeyLen,
            /* LPDWORD */ MemorySegment lpcbMaxClassLen,
            /* LPDWORD */ MemorySegment lpcValues,
            /* LPDWORD */ MemorySegment lpcbMaxValueNameLen,
            /* LPDWORD */ MemorySegment lpcbMaxValueLen,
            /* LPDWORD */ MemorySegment lpcbSecurityDescriptor,
            /* PFILETIME */ MemorySegment lpftLastWriteTime);

    /* LSTATUS */ int RegQueryValueEx(// NOSONAR
            /* HKEY */ MemorySegment hKey,
            /* LPCWSTR */ MemorySegment lpValueName,
            /* LPDWORD */ MemorySegment lpReserved,
            /* LPDWORD */ MemorySegment lpType,
            /* LPBYTE */ MemorySegment lpData,
            /* LPDWORD */ MemorySegment lpcbData);

    /* LSTATUS */ int RegRenameKey/* NOSONAR */(
            /* HKEY */ MemorySegment hKey,
            /* LPCWSTR */ MemorySegment lpSubKeyName,
            /* LPCWSTR */ MemorySegment lpNewKeyName);

    boolean isRegRenameKeyEnabled();

    /* LSTATUS */ int RegSetValueEx/* NOSONAR */(
            /* HKEY */ MemorySegment hKey,
            /* LPCWSTR */ MemorySegment lpValueName,
            /* DWORD */ int Reserved, // NOSONAR
            /* DWORD */ int dwType,
            /* const BYTE * */ MemorySegment lpData,
            /* DWORD */ int cbData);
}
