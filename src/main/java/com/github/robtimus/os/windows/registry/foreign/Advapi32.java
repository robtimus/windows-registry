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

import static com.github.robtimus.os.windows.registry.foreign.ForeignUtils.ARENA;
import static com.github.robtimus.os.windows.registry.foreign.ForeignUtils.functionMethodHandle;
import static com.github.robtimus.os.windows.registry.foreign.ForeignUtils.optionalFunctionMethodHandle;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.Optional;

@SuppressWarnings({ "javadoc", "nls" })
public final class Advapi32 {

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

        REG_CLOSE_KEY = functionMethodHandle(linker, advapi32, "RegCloseKey", FunctionDescriptor.of(ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS)); // hKey

        REG_CONNECT_REGISTRY = functionMethodHandle(linker, advapi32, "RegConnectRegistryW", FunctionDescriptor.of(ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS, // lpMachineName
                ValueLayout.ADDRESS, // hKey
                ValueLayout.ADDRESS)); // phkResult

        REG_CREATE_KEY_EX = functionMethodHandle(linker, advapi32, "RegCreateKeyExW", FunctionDescriptor.of(ValueLayout.JAVA_INT,
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
        REG_CREATE_KEY_TRANSACTED = optionalFunctionMethodHandle(linker, advapi32, "RegCreateKeyTransactedW", FunctionDescriptor.of(
                ValueLayout.JAVA_INT, // return value
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
                ValueLayout.ADDRESS)); // pExtendedParemeter

        REG_DELETE_KEY_EX = functionMethodHandle(linker, advapi32, "RegDeleteKeyExW", FunctionDescriptor.of(ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS, // hKey
                ValueLayout.ADDRESS, // lpSubKey
                ValueLayout.JAVA_INT, // samDesired
                ValueLayout.JAVA_INT)); // Reserved

        // RegDeleteKeyTransactedW does not work before Windows Vista / Windows Server 2008
        REG_DELETE_KEY_TRANSACTED = optionalFunctionMethodHandle(linker, advapi32, "RegDeleteKeyTransactedW", FunctionDescriptor.of(
                ValueLayout.JAVA_INT, // return value
                ValueLayout.ADDRESS, // hKey
                ValueLayout.ADDRESS, // lpSubKey
                ValueLayout.JAVA_INT, // samDesired
                ValueLayout.JAVA_INT, // Reserved
                ValueLayout.ADDRESS, // hTransaction
                ValueLayout.ADDRESS)); // pExtendedParemeter

        REG_DELETE_VALUE = functionMethodHandle(linker, advapi32, "RegDeleteValueW", FunctionDescriptor.of(ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS, // hKey
                ValueLayout.ADDRESS)); // lpValueName

        REG_ENUM_KEY_EX = functionMethodHandle(linker, advapi32, "RegEnumKeyExW", FunctionDescriptor.of(ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS, // hKey
                ValueLayout.JAVA_INT, // dwIndex
                ValueLayout.ADDRESS, // lpName
                ValueLayout.ADDRESS, // lpcchName
                ValueLayout.ADDRESS, // lpReserved
                ValueLayout.ADDRESS, // lpClass
                ValueLayout.ADDRESS, // lpcchClass
                ValueLayout.ADDRESS)); // lpftLastWriteTime

        REG_ENUM_VALUE = functionMethodHandle(linker, advapi32, "RegEnumValueW", FunctionDescriptor.of(ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS, // hKey
                ValueLayout.JAVA_INT, // dwIndex
                ValueLayout.ADDRESS, // lpValueName
                ValueLayout.ADDRESS, // lpcchValueName
                ValueLayout.ADDRESS, // lpReserved
                ValueLayout.ADDRESS, // lpType
                ValueLayout.ADDRESS, // lpData
                ValueLayout.ADDRESS)); // lpcbData

        REG_OPEN_KEY_EX = functionMethodHandle(linker, advapi32, "RegOpenKeyExW", FunctionDescriptor.of(ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS, // hKey
                ValueLayout.ADDRESS, // lpSubKey
                ValueLayout.JAVA_INT, // ulOptions
                ValueLayout.JAVA_INT, // samDesired
                ValueLayout.ADDRESS)); // phkResult

        // RegOpenKeyTransactedW does not work before Windows Vista / Windows Server 2008
        REG_OPEN_KEY_TRANSACTED = optionalFunctionMethodHandle(linker, advapi32, "RegOpenKeyTransactedW", FunctionDescriptor.of(ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS, // hKey
                ValueLayout.ADDRESS, // lpSubKey
                ValueLayout.JAVA_INT, // ulOptions
                ValueLayout.JAVA_INT, // samDesired
                ValueLayout.ADDRESS, // phkResult
                ValueLayout.ADDRESS, // hTransaction
                ValueLayout.ADDRESS)); // pExtendedParemeter

        REG_QUERY_INFO_KEY = functionMethodHandle(linker, advapi32, "RegQueryInfoKeyW", FunctionDescriptor.of(ValueLayout.JAVA_INT,
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

        REG_QUERY_VALUE_EX = functionMethodHandle(linker, advapi32, "RegQueryValueExW", FunctionDescriptor.of(ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS, // hKey
                ValueLayout.ADDRESS, // lpValueName
                ValueLayout.ADDRESS, // lpReserved
                ValueLayout.ADDRESS, // lpType
                ValueLayout.ADDRESS, // lpData
                ValueLayout.ADDRESS)); // lpcbData

        // RegRenameKey does not work before Windows Vista / Windows Server 2008
        REG_RENAME_KEY = optionalFunctionMethodHandle(linker, advapi32, "RegRenameKey", FunctionDescriptor.of(ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS, // hKey
                ValueLayout.ADDRESS, // lpSubKeyName
                ValueLayout.ADDRESS)); // lpNewKeyName

        REG_SET_VALUE_EX = functionMethodHandle(linker, advapi32, "RegSetValueExW", FunctionDescriptor.of(ValueLayout.JAVA_INT,
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

    public static /* LSTATUS */ int RegCloseKey/* NOSONAR */(
            /* HKEY */ MemorySegment hKey) {

        try {
            return (int) REG_CLOSE_KEY.invokeExact(
                    hKey);
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        }
    }

    public static /* LSTATUS */ int RegConnectRegistry/* NOSONAR */(
            /* LPCWSTR */ MemorySegment lpMachineName,
            /* HKEY */ MemorySegment hKey,
            /* PHKEY */ MemorySegment phkResult) {

        try {
            return (int) REG_CONNECT_REGISTRY.invokeExact(
                    lpMachineName,
                    hKey,
                    phkResult);
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        }
    }

    public static /* LSTATUS */ int RegCreateKeyEx/* NOSONAR */(
            /* HKEY */ MemorySegment hKey,
            /* LPCWSTR */ MemorySegment lpSubKey,
            /* DWORD */ int Reserved, // NOSONAR
            /* LPWSTR */ MemorySegment lpClass,
            /* DWORD */ int dwOptions,
            /* REGSAM */ int samDesired,
            /* const LPSECURITY_ATTRIBUTES */ MemorySegment lpSecurityAttributes,
            /* PHKEY */ MemorySegment phkResult,
            /* LPDWORD */ MemorySegment lpdwDisposition) {

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

    public static /* LSTATUS */ int RegCreateKeyTransacted/* NOSONAR */(
            /* HKEY */ MemorySegment hKey,
            /* LPCWSTR */ MemorySegment lpSubKey,
            /* DWORD */ int Reserved, // NOSONAR
            /* LPWSTR */ MemorySegment lpClass,
            /* DWORD */ int dwOptions,
            /* REGSAM */ int samDesired,
            /* const LPSECURITY_ATTRIBUTES */ MemorySegment lpSecurityAttributes,
            /* PHKEY */ MemorySegment phkResult,
            /* LPDWORD */ MemorySegment lpdwDisposition,
            /* HANDLE */ MemorySegment hTransaction,
            /* PVOID */ MemorySegment pExtendedParemeter) {

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

    public static boolean isRegCreateKeyTransactedEnabled() {
        return REG_CREATE_KEY_TRANSACTED.isPresent();
    }

    public static /* LSTATUS */ int RegDeleteKeyEx/* NOSONAR */(
            /* HKEY */ MemorySegment hKey,
            /* LPCWSTR */ MemorySegment lpSubKey,
            /* REGSAM */ int samDesired,
            /* DWORD  */ int Reserved) { // NOSONAR

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

    public static /* LSTATUS */ int RegDeleteKeyTransacted/* NOSONAR */(
            /* HKEY */ MemorySegment hKey,
            /* LPCWSTR */ MemorySegment lpSubKey,
            /* REGSAM */ int samDesired,
            /* DWORD */ int Reserved, // NOSONAR
            /* HANDLE */ MemorySegment hTransaction,
            /* PVOID */ MemorySegment pExtendedParameter) {

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

    public static boolean isRegDeleteKeyTransactedEnabled() {
        return REG_DELETE_KEY_TRANSACTED.isPresent();
    }

    public static /* LSTATUS */ int RegDeleteValue/* NOSONAR */(
            /* HKEY */ MemorySegment hKey,
            /* LPCWSTR */ MemorySegment lpValueName) {

        try {
            return (int) REG_DELETE_VALUE.invokeExact(
                    hKey,
                    lpValueName);
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        }
    }

    public static /* LSTATUS */ int RegEnumKeyEx/* NOSONAR */(
            /* HKEY */ MemorySegment hKey,
            /* DWORD */ int dwIndex,
            /* LPWSTR  */ MemorySegment lpName,
            /* LPDWORD */ MemorySegment lpcchName,
            /* LPDWORD */ MemorySegment lpReserved,
            /* LPWSTR */ MemorySegment lpClass,
            /* LPDWORD */ MemorySegment lpcchClass,
            /* PFILETIME */ MemorySegment lpftLastWriteTime) {

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

    public static /* LSTATUS */ int RegEnumValue/* NOSONAR */(
            /* HKEY */ MemorySegment hKey,
            /* DWORD */ int dwIndex,
            /* LPWSTR */ MemorySegment lpValueName,
            /* LPDWORD */ MemorySegment lpcchValueName,
            /* LPDWORD */ MemorySegment lpReserved,
            /* LPDWORD */ MemorySegment lpType,
            /* LPBYTE */ MemorySegment lpData,
            /* LPDWORD */ MemorySegment lpcbData) {

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

    public static /* LSTATUS */ int RegOpenKeyEx/* NOSONAR */(
            /* HKEY */ MemorySegment hKey,
            /* LPCWSTR */ MemorySegment lpSubKey,
            /* DWORD */ int ulOptions,
            /* REGSAM */ int samDesired,
            /* PHKEY */ MemorySegment phkResult) {

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

    public static /* LSTATUS */ int RegOpenKeyTransacted/* NOSONAR */(
            /* HKEY */ MemorySegment hKey,
            /* LPCWSTR */ MemorySegment lpSubKey,
            /* DWORD */ int ulOptions,
            /* REGSAM */ int samDesired,
            /* PHKEY */ MemorySegment phkResult,
            /* HANDLE */ MemorySegment hTransaction,
            /* PVOID */ MemorySegment pExtendedParemeter) {

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

    public static boolean isRegOpenKeyTransactedEnabled() {
        return REG_OPEN_KEY_TRANSACTED.isPresent();
    }

    public static /* LSTATUS */ int RegQueryInfoKey/* NOSONAR */(
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
            /* PFILETIME */ MemorySegment lpftLastWriteTime) {

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

    public static /* LSTATUS */ int RegQueryValueEx(// NOSONAR
            /* HKEY */ MemorySegment hKey,
            /* LPCWSTR */ MemorySegment lpValueName,
            /* LPDWORD */ MemorySegment lpReserved,
            /* LPDWORD */ MemorySegment lpType,
            /* LPBYTE */ MemorySegment lpData,
            /* LPDWORD */ MemorySegment lpcbData) {

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

    public static /* LSTATUS */ int RegRenameKey/* NOSONAR */(
            /* HKEY */ MemorySegment hKey,
            /* LPCWSTR */ MemorySegment lpSubKeyName,
            /* LPCWSTR */ MemorySegment lpNewKeyName) {

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

    public static boolean isRegRenameKeyEnabled() {
        return REG_RENAME_KEY.isPresent();
    }

    public static /* LSTATUS */ int RegSetValueEx/* NOSONAR */(
            /* HKEY */ MemorySegment hKey,
            /* LPCWSTR */ MemorySegment lpValueName,
            /* DWORD */ int Reserved, // NOSONAR
            /* DWORD */ int dwType,
            /* const BYTE * */ MemorySegment lpData,
            /* DWORD */ int cbData) {

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
