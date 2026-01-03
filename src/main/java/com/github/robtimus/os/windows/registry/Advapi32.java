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

package com.github.robtimus.os.windows.registry;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.Optional;

@SuppressWarnings("nls")
final class Advapi32 extends WindowsApi {

    private static final MethodHandle REG_CLOSE_KEY;
    private static final MethodHandle REG_CONNECT_REGISTRY;
    private static final MethodHandle REG_CREATE_KEY_EX;
    private static final Optional<MethodHandle> REG_CREATE_KEY_TRANSACTED;
    private static final MethodHandle REG_DELETE_KEY_EX;
    private static final Optional<MethodHandle> REG_DELETE_KEY_TRANSACTED;
    private static final MethodHandle REG_DELETE_VALUE;
    private static final MethodHandle REG_ENUM_KEY_EX;
    private static final MethodHandle REG_ENUM_VALUE;
    private static final MethodHandle REG_OPEN_KEY_EX;
    private static final Optional<MethodHandle> REG_OPEN_KEY_TRANSACTED;
    private static final MethodHandle REG_QUERY_INFO_KEY;
    private static final MethodHandle REG_QUERY_VALUE_EX;
    private static final Optional<MethodHandle> REG_RENAME_KEY;
    private static final MethodHandle REG_SET_VALUE_EX;

    static {
        Linker linker = Linker.nativeLinker();
        SymbolLookup advapi32 = SymbolLookup.libraryLookup("Advapi32", ARENA);

        REG_CLOSE_KEY = linker.downcallHandle(advapi32.findOrThrow("RegCloseKey"), FunctionDescriptor.of(ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS)); // hKey

        REG_CONNECT_REGISTRY = linker.downcallHandle(advapi32.findOrThrow("RegConnectRegistryW"), FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS, // lpMachineName
                ValueLayout.ADDRESS, // hKey
                ValueLayout.ADDRESS)); // phkResult

        REG_CREATE_KEY_EX = linker.downcallHandle(advapi32.findOrThrow("RegCreateKeyExW"), FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS, // hKey
                ValueLayout.ADDRESS, // lpSubKey
                ValueLayout.JAVA_INT, // Reserved
                ValueLayout.ADDRESS, // lpClass
                ValueLayout.JAVA_INT, // dwOptions
                ValueLayout.JAVA_INT, // samDesired
                ValueLayout.ADDRESS, // lpSecurityAttributes
                ValueLayout.ADDRESS, // phkResult
                ValueLayout.ADDRESS)); // lpdwDisposition

        // RegCreateKeyTransactedW does not work before Windows Vista / Windows Server 2008
        REG_CREATE_KEY_TRANSACTED = advapi32.find("RegCreateKeyTransactedW")
                .map(address -> linker.downcallHandle(address, FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS, // hKey
                        ValueLayout.ADDRESS, // lpSubKey
                        ValueLayout.JAVA_INT, // Reserved
                        ValueLayout.ADDRESS, // lpClass
                        ValueLayout.JAVA_INT, // dwOptions
                        ValueLayout.JAVA_INT, // samDesired
                        ValueLayout.ADDRESS, // lpSecurityAttributes
                        ValueLayout.ADDRESS, // phkResult
                        ValueLayout.ADDRESS, // lpdwDisposition
                        ValueLayout.ADDRESS, // hTransaction
                        ValueLayout.ADDRESS))); // pExtendedParemeter

        REG_DELETE_KEY_EX = linker.downcallHandle(advapi32.findOrThrow("RegDeleteKeyExW"), FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS, // hKey
                ValueLayout.ADDRESS, // lpSubKey
                ValueLayout.JAVA_INT, // samDesired
                ValueLayout.JAVA_INT)); // Reserved

        // RegDeleteKeyTransactedW does not work before Windows Vista / Windows Server 2008
        REG_DELETE_KEY_TRANSACTED = advapi32.find("RegDeleteKeyTransactedW")
                .map(address -> linker.downcallHandle(address, FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS, // hKey
                        ValueLayout.ADDRESS, // lpSubKey
                        ValueLayout.JAVA_INT, // samDesired
                        ValueLayout.JAVA_INT, // Reserved
                        ValueLayout.ADDRESS, // hTransaction
                        ValueLayout.ADDRESS))); // pExtendedParemeter

        REG_DELETE_VALUE = linker.downcallHandle(advapi32.findOrThrow("RegDeleteValueW"), FunctionDescriptor.of(ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS, // hKey
                ValueLayout.ADDRESS)); // lpValueName

        REG_ENUM_KEY_EX = linker.downcallHandle(advapi32.findOrThrow("RegEnumKeyExW"), FunctionDescriptor.of(ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS, // hKey
                ValueLayout.JAVA_INT, // dwIndex
                ValueLayout.ADDRESS, // lpName
                ValueLayout.ADDRESS, // lpcchName
                ValueLayout.ADDRESS, // lpReserved
                ValueLayout.ADDRESS, // lpClass
                ValueLayout.ADDRESS, // lpcchClass
                ValueLayout.ADDRESS)); // lpftLastWriteTime

        REG_ENUM_VALUE = linker.downcallHandle(advapi32.findOrThrow("RegEnumValueW"), FunctionDescriptor.of(ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS, // hKey
                ValueLayout.JAVA_INT, // dwIndex
                ValueLayout.ADDRESS, // lpValueName
                ValueLayout.ADDRESS, // lpcchValueName
                ValueLayout.ADDRESS, // lpReserved
                ValueLayout.ADDRESS, // lpType
                ValueLayout.ADDRESS, // lpData
                ValueLayout.ADDRESS)); // lpcbData

        REG_OPEN_KEY_EX = linker.downcallHandle(advapi32.findOrThrow("RegOpenKeyExW"), FunctionDescriptor.of(ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS, // hKey
                ValueLayout.ADDRESS, // lpSubKey
                ValueLayout.JAVA_INT, // ulOptions
                ValueLayout.JAVA_INT, // samDesired
                ValueLayout.ADDRESS)); // phkResult

        // RegOpenKeyTransactedW does not work before Windows Vista / Windows Server 2008
        REG_OPEN_KEY_TRANSACTED = advapi32.find("RegOpenKeyTransactedW")
                .map(address -> linker.downcallHandle(address, FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS, // hKey
                        ValueLayout.ADDRESS, // lpSubKey
                        ValueLayout.JAVA_INT, // ulOptions
                        ValueLayout.JAVA_INT, // samDesired
                        ValueLayout.ADDRESS, // phkResult
                        ValueLayout.ADDRESS, // hTransaction
                        ValueLayout.ADDRESS))); // pExtendedParemeter

        REG_QUERY_INFO_KEY = linker.downcallHandle(advapi32.findOrThrow("RegQueryInfoKeyW"), FunctionDescriptor.of(ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS, // hKey
                ValueLayout.ADDRESS, // lpClass
                ValueLayout.ADDRESS, // lpcchClass
                ValueLayout.ADDRESS, // lpReserved
                ValueLayout.ADDRESS, // lpcSubKeys
                ValueLayout.ADDRESS, // lpcbMaxSubKeyLen
                ValueLayout.ADDRESS, // lpcbMaxClassLen
                ValueLayout.ADDRESS, // lpcValues
                ValueLayout.ADDRESS, // lpcbMaxValueNameLen
                ValueLayout.ADDRESS, // lpcbMaxValueLen
                ValueLayout.ADDRESS, // lpcbSecurityDescriptor
                ValueLayout.ADDRESS)); // lpftLastWriteTime

        REG_QUERY_VALUE_EX = linker.downcallHandle(advapi32.findOrThrow("RegQueryValueExW"), FunctionDescriptor.of(ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS, // hKey
                ValueLayout.ADDRESS, // lpValueName
                ValueLayout.ADDRESS, // lpReserved
                ValueLayout.ADDRESS, // lpType
                ValueLayout.ADDRESS, // lpData
                ValueLayout.ADDRESS)); // lpcbData

        // RegRenameKey does not work before Windows Vista / Windows Server 2008
        REG_RENAME_KEY = advapi32.find("RegRenameKey")
                .map(address -> linker.downcallHandle(address, FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS, // hKey
                        ValueLayout.ADDRESS, // lpSubKeyName
                        ValueLayout.ADDRESS))); // lpNewKeyName

        REG_SET_VALUE_EX = linker.downcallHandle(advapi32.findOrThrow("RegSetValueExW"), FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS, // hKey
                ValueLayout.ADDRESS, // lpValueName
                ValueLayout.JAVA_INT, // Reserved
                ValueLayout.JAVA_INT, // dwType
                ValueLayout.ADDRESS, // lpData
                ValueLayout.JAVA_INT)); // cbData
    }

    private Advapi32() {
    }

    // The following functions all return any error and do not require GetLastError() to be called; CaptureState is therefore not needed

    /*
     * LSTATUS RegCloseKey(
     *   [in] HKEY hKey
     * )
     */
    @SuppressWarnings({ "checkstyle:MethodName", "squid:S100" })
    static int RegCloseKey(
            MemorySegment hKey) {

        try {
            return (int) REG_CLOSE_KEY.invokeExact(
                    hKey);
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        }
    }

    /*
     * LSTATUS RegConnectRegistryW(
     *   [in, optional] LPCWSTR lpMachineName,
     *   [in]           HKEY    hKey,
     *   [out]          PHKEY   phkResult
     * )
     */
    @SuppressWarnings({ "checkstyle:MethodName", "squid:S100" })
    static int RegConnectRegistry(
            MemorySegment lpMachineName,
            MemorySegment hKey,
            MemorySegment phkResult) {

        try {
            return (int) REG_CONNECT_REGISTRY.invokeExact(
                    lpMachineName,
                    hKey,
                    phkResult);
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        }
    }

    /*
     * LSTATUS RegCreateKeyExW(
     *   [in]            HKEY                        hKey,
     *   [in]            LPCWSTR                     lpSubKey,
     *                   DWORD                       Reserved,
     *   [in, optional]  LPWSTR                      lpClass,
     *   [in]            DWORD                       dwOptions,
     *   [in]            REGSAM                      samDesired,
     *   [in, optional]  const LPSECURITY_ATTRIBUTES lpSecurityAttributes,
     *   [out]           PHKEY                       phkResult,
     *   [out, optional] LPDWORD                     lpdwDisposition
     * )
     */
    @SuppressWarnings({ "checkstyle:MethodName", "squid:S100", "squid:S107" })
    static int RegCreateKeyEx(
            MemorySegment hKey,
            MemorySegment lpSubKey,
            @SuppressWarnings({ "checkstyle:ParameterName", "squid:S117" })
            int Reserved,
            MemorySegment lpClass,
            int dwOptions,
            int samDesired,
            MemorySegment lpSecurityAttributes,
            MemorySegment phkResult,
            MemorySegment lpdwDisposition) {

        try {
            return (int) REG_CREATE_KEY_EX.invokeExact(
                    hKey,
                    lpSubKey,
                    Reserved,
                    lpClass,
                    dwOptions,
                    samDesired,
                    lpSecurityAttributes,
                    phkResult,
                    lpdwDisposition);
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        }
    }

    /*
     * LSTATUS RegCreateKeyTransactedW(
     *   [in]            HKEY                        hKey,
     *   [in]            LPCWSTR                     lpSubKey,
     *                   DWORD                       Reserved,
     *   [in, optional]  LPWSTR                      lpClass,
     *   [in]            DWORD                       dwOptions,
     *   [in]            REGSAM                      samDesired,
     *   [in, optional]  const LPSECURITY_ATTRIBUTES lpSecurityAttributes,
     *   [out]           PHKEY                       phkResult,
     *   [out, optional] LPDWORD                     lpdwDisposition,
     *   [in]            HANDLE                      hTransaction,
     *                   PVOID                       pExtendedParemeter
     * )
     */
    @SuppressWarnings({ "checkstyle:MethodName", "squid:S100", "squid:S107" })
    static int RegCreateKeyTransacted(
            MemorySegment hKey,
            MemorySegment lpSubKey,
            @SuppressWarnings({ "checkstyle:ParameterName", "squid:S117" })
            int Reserved,
            MemorySegment lpClass,
            int dwOptions,
            int samDesired,
            MemorySegment lpSecurityAttributes,
            MemorySegment phkResult,
            MemorySegment lpdwDisposition,
            MemorySegment hTransaction,
            MemorySegment pExtendedParemeter) {

        MethodHandle regCreateKeyTransactedHandle = REG_CREATE_KEY_TRANSACTED.orElseThrow(UnsupportedOperationException::new);
        try {
            return (int) regCreateKeyTransactedHandle.invokeExact(
                    hKey,
                    lpSubKey,
                    Reserved,
                    lpClass,
                    dwOptions,
                    samDesired,
                    lpSecurityAttributes,
                    phkResult,
                    lpdwDisposition,
                    hTransaction,
                    pExtendedParemeter);
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        }
    }

    static boolean isRegCreateKeyTransactedEnabled() {
        return REG_CREATE_KEY_TRANSACTED.isPresent();
    }

    /*
     * LSTATUS RegDeleteKeyExW(
     *   [in] HKEY    hKey,
     *   [in] LPCWSTR lpSubKey,
     *   [in] REGSAM  samDesired,
     *        DWORD   Reserved
     * )
     */
    @SuppressWarnings({ "checkstyle:MethodName", "squid:S100" })
    static int RegDeleteKeyEx(
            MemorySegment hKey,
            MemorySegment lpSubKey,
            int samDesired,
            @SuppressWarnings({ "checkstyle:ParameterName", "squid:S117" })
            int Reserved) {

        try {
            return (int) REG_DELETE_KEY_EX.invokeExact(
                    hKey,
                    lpSubKey,
                    samDesired,
                    Reserved);
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        }
    }

    /*
     * LSTATUS RegDeleteKeyTransactedW(
     *   [in] HKEY    hKey,
     *   [in] LPCWSTR lpSubKey,
     *   [in] REGSAM  samDesired,
     *        DWORD   Reserved,
     *   [in] HANDLE  hTransaction,
     *        PVOID   pExtendedParameter
     * )
     */
    @SuppressWarnings({ "checkstyle:MethodName", "squid:S100" })
    static int RegDeleteKeyTransacted(
            MemorySegment hKey,
            MemorySegment lpSubKey,
            int samDesired,
            @SuppressWarnings({ "checkstyle:ParameterName", "squid:S117" })
            int Reserved,
            MemorySegment hTransaction,
            MemorySegment pExtendedParameter) {

        MethodHandle regDeleteKeyTransactedHandle = REG_DELETE_KEY_TRANSACTED.orElseThrow(UnsupportedOperationException::new);
        try {
            return (int) regDeleteKeyTransactedHandle.invokeExact(
                    hKey,
                    lpSubKey,
                    samDesired,
                    Reserved,
                    hTransaction,
                    pExtendedParameter);
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        }
    }

    static boolean isRegDeleteKeyTransactedEnabled() {
        return REG_DELETE_KEY_TRANSACTED.isPresent();
    }

    /*
     * LSTATUS RegDeleteValueW(
     *   [in]           HKEY    hKey,
     *   [in, optional] LPCWSTR lpValueName
     * )
     */
    @SuppressWarnings({ "checkstyle:MethodName", "squid:S100" })
    static int RegDeleteValue(
            MemorySegment hKey,
            MemorySegment lpValueName) {

        try {
            return (int) REG_DELETE_VALUE.invokeExact(
                    hKey,
                    lpValueName);
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        }
    }

    /*
     * LSTATUS RegEnumKeyExW(
     *   [in]                HKEY      hKey,
     *   [in]                DWORD     dwIndex,
     *   [out]               LPWSTR    lpName,
     *   [in, out]           LPDWORD   lpcchName,
     *                       LPDWORD   lpReserved,
     *   [in, out]           LPWSTR    lpClass,
     *   [in, out, optional] LPDWORD   lpcchClass,
     *   [out, optional]     PFILETIME lpftLastWriteTime
     * )
     */
    @SuppressWarnings({ "checkstyle:MethodName", "squid:S100", "squid:S107" })
    static int RegEnumKeyEx(
            MemorySegment hKey,
            int dwIndex,
            MemorySegment lpName,
            MemorySegment lpcchName,
            MemorySegment lpReserved,
            MemorySegment lpClass,
            MemorySegment lpcchClass,
            MemorySegment lpftLastWriteTime) {

        try {
            return (int) REG_ENUM_KEY_EX.invokeExact(
                    hKey,
                    dwIndex,
                    lpName,
                    lpcchName,
                    lpReserved,
                    lpClass,
                    lpcchClass,
                    lpftLastWriteTime);
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        }
    }

    /*
     * LSTATUS RegEnumValueW(
     *   [in]                HKEY    hKey,
     *   [in]                DWORD   dwIndex,
     *   [out]               LPWSTR  lpValueName,
     *   [in, out]           LPDWORD lpcchValueName,
     *                       LPDWORD lpReserved,
     *   [out, optional]     LPDWORD lpType,
     *   [out, optional]     LPBYTE  lpData,
     *   [in, out, optional] LPDWORD lpcbData
     * )
     */
    @SuppressWarnings({ "checkstyle:MethodName", "squid:S100", "squid:S107" })
    static int RegEnumValue(
            MemorySegment hKey,
            int dwIndex,
            MemorySegment lpValueName,
            MemorySegment lpcchValueName,
            MemorySegment lpReserved,
            MemorySegment lpType,
            MemorySegment lpData,
            MemorySegment lpcbData) {

        try {
            return (int) REG_ENUM_VALUE.invokeExact(
                    hKey,
                    dwIndex,
                    lpValueName,
                    lpcchValueName,
                    lpReserved,
                    lpType,
                    lpData,
                    lpcbData);
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        }
    }

    /*
     * LSTATUS RegOpenKeyExW(
     *   [in]           HKEY    hKey,
     *   [in, optional] LPCWSTR lpSubKey,
     *   [in]           DWORD   ulOptions,
     *   [in]           REGSAM  samDesired,
     *   [out]          PHKEY   phkResult
     * )
     */
    @SuppressWarnings({ "checkstyle:MethodName", "squid:S100" })
    static int RegOpenKeyEx(
            MemorySegment hKey,
            MemorySegment lpSubKey,
            int ulOptions,
            int samDesired,
            MemorySegment phkResult) {

        try {
            return (int) REG_OPEN_KEY_EX.invokeExact(
                    hKey,
                    lpSubKey,
                    ulOptions,
                    samDesired,
                    phkResult);
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        }
    }

    /*
     * LSTATUS RegOpenKeyTransactedW(
     *  [in]           HKEY    hKey,
     *  [in, optional] LPCWSTR lpSubKey,
     *  [in]           DWORD   ulOptions,
     *  [in]           REGSAM  samDesired,
     *  [out]          PHKEY   phkResult,
     *  [in]           HANDLE  hTransaction,
     *                 PVOID   pExtendedParemeter
     *)
     */
    @SuppressWarnings({ "checkstyle:MethodName", "squid:S100" })
    static int RegOpenKeyTransacted(
            MemorySegment hKey,
            MemorySegment lpSubKey,
            int ulOptions,
            int samDesired,
            MemorySegment phkResult,
            MemorySegment hTransaction,
            MemorySegment pExtendedParemeter) {

        MethodHandle regOpenKeyTransactedHandle = REG_OPEN_KEY_TRANSACTED.orElseThrow(UnsupportedOperationException::new);
        try {
            return (int) regOpenKeyTransactedHandle.invokeExact(
                    hKey,
                    lpSubKey,
                    ulOptions,
                    samDesired,
                    phkResult,
                    hTransaction,
                    pExtendedParemeter);
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        }
    }

    static boolean isRegOpenKeyTransactedEnabled() {
        return REG_OPEN_KEY_TRANSACTED.isPresent();
    }

    /*
     * LSTATUS RegQueryInfoKeyW(
     *  [in]                HKEY      hKey,
     *  [out, optional]     LPWSTR    lpClass,
     *  [in, out, optional] LPDWORD   lpcchClass,
     *                      LPDWORD   lpReserved,
     *  [out, optional]     LPDWORD   lpcSubKeys,
     *  [out, optional]     LPDWORD   lpcbMaxSubKeyLen,
     *  [out, optional]     LPDWORD   lpcbMaxClassLen,
     *  [out, optional]     LPDWORD   lpcValues,
     *  [out, optional]     LPDWORD   lpcbMaxValueNameLen,
     *  [out, optional]     LPDWORD   lpcbMaxValueLen,
     *  [out, optional]     LPDWORD   lpcbSecurityDescriptor,
     *  [out, optional]     PFILETIME lpftLastWriteTime
     *)
     */
    @SuppressWarnings({ "checkstyle:MethodName", "squid:S100", "squid:S107" })
    static int RegQueryInfoKey(
            MemorySegment hKey,
            MemorySegment lpClass,
            MemorySegment lpcchClass,
            MemorySegment lpReserved,
            MemorySegment lpcSubKeys,
            MemorySegment lpcbMaxSubKeyLen,
            MemorySegment lpcbMaxClassLen,
            MemorySegment lpcValues,
            MemorySegment lpcbMaxValueNameLen,
            MemorySegment lpcbMaxValueLen,
            MemorySegment lpcbSecurityDescriptor,
            MemorySegment lpftLastWriteTime) {

        try {
            return (int) REG_QUERY_INFO_KEY.invokeExact(
                    hKey,
                    lpClass,
                    lpcchClass,
                    lpReserved,
                    lpcSubKeys,
                    lpcbMaxSubKeyLen,
                    lpcbMaxClassLen,
                    lpcValues,
                    lpcbMaxValueNameLen,
                    lpcbMaxValueLen,
                    lpcbSecurityDescriptor,
                    lpftLastWriteTime);
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        }
    }

    /*
     * LSTATUS RegQueryValueExW(
     *   [in]                HKEY    hKey,
     *   [in, optional]      LPCWSTR lpValueName,
     *                       LPDWORD lpReserved,
     *   [out, optional]     LPDWORD lpType,
     *   [out, optional]     LPBYTE  lpData,
     *   [in, out, optional] LPDWORD lpcbData
     * )
     */
    @SuppressWarnings({ "checkstyle:MethodName", "squid:S100" })
    static int RegQueryValueEx(
            MemorySegment hKey,
            MemorySegment lpValueName,
            MemorySegment lpReserved,
            MemorySegment lpType,
            MemorySegment lpData,
            MemorySegment lpcbData) {

        try {
            return (int) REG_QUERY_VALUE_EX.invokeExact(
                    hKey,
                    lpValueName,
                    lpReserved,
                    lpType,
                    lpData,
                    lpcbData);
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        }
    }

    /*
     * LSTATUS RegRenameKey(
     *   HKEY    hKey,
     *   LPCWSTR lpSubKeyName,
     *   LPCWSTR lpNewKeyName
     * )
     */
    @SuppressWarnings({ "checkstyle:MethodName", "squid:S100" })
    static int RegRenameKey(
            MemorySegment hKey,
            MemorySegment lpSubKeyName,
            MemorySegment lpNewKeyName) {

        MethodHandle regRenameKeyHandle = REG_RENAME_KEY.orElseThrow(UnsupportedOperationException::new);
        try {
            return (int) regRenameKeyHandle.invokeExact(
                    hKey,
                    lpSubKeyName,
                    lpNewKeyName);
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        }
    }

    static boolean isRegRenameKeyEnabled() {
        return REG_RENAME_KEY.isPresent();
    }

    /*
     * LSTATUS RegSetValueExW(
     *   [in]           HKEY       hKey,
     *   [in, optional] LPCWSTR    lpValueName,
     *                  DWORD      Reserved,
     *   [in]           DWORD      dwType,
     *   [in]           const BYTE *lpData,
     *   [in]           DWORD      cbData
     * )
     */
    @SuppressWarnings({ "checkstyle:MethodName", "squid:S100" })
    static int RegSetValueEx(
            MemorySegment hKey,
            MemorySegment lpValueName,
            @SuppressWarnings({ "checkstyle:ParameterName", "squid:S117" })
            int Reserved,
            int dwType,
            MemorySegment lpData,
            int cbData) {

        try {
            return (int) REG_SET_VALUE_EX.invokeExact(
                    hKey,
                    lpValueName,
                    Reserved,
                    dwType,
                    lpData,
                    cbData);
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        }
    }
}
