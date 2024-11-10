/*
 * Advapi32Impl.java
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

final class Advapi32Impl implements Advapi32 {

    private final MethodHandle regCloseKey;
    private final MethodHandle regConnectRegistry;
    private final MethodHandle regCreateKeyEx;
    private final Optional<MethodHandle> regCreateKeyTransacted;
    private final MethodHandle regDeleteKey;
    private final Optional<MethodHandle> regDeleteKeyTransacted;
    private final MethodHandle regDeleteValue;
    private final MethodHandle regEnumKeyEx;
    private final MethodHandle regEnumValue;
    private final MethodHandle regOpenKeyEx;
    private final Optional<MethodHandle> regOpenKeyTransacted;
    private final MethodHandle regQueryInfoKey;
    private final MethodHandle regQueryValueEx;
    private final Optional<MethodHandle> regRenameKey;
    private final MethodHandle regSetValueEx;

    @SuppressWarnings("nls")
    Advapi32Impl() {
        Linker linker = Linker.nativeLinker();
        SymbolLookup symbolLookup = SymbolLookup.libraryLookup("Advapi32", ARENA);

        regCloseKey = functionMethodHandle(linker, symbolLookup, "RegCloseKey", FunctionDescriptor.of(ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS)); // hKey

        regConnectRegistry = functionMethodHandle(linker, symbolLookup, "RegConnectRegistryW", FunctionDescriptor.of(ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS, // lpMachineName
                ValueLayout.ADDRESS, // hKey
                ValueLayout.ADDRESS)); // phkResult

        regCreateKeyEx = functionMethodHandle(linker, symbolLookup, "RegCreateKeyExW", FunctionDescriptor.of(ValueLayout.JAVA_INT,
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
        regCreateKeyTransacted = optionalFunctionMethodHandle(linker, symbolLookup, "RegCreateKeyTransactedW", FunctionDescriptor.of(
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

        regDeleteKey = functionMethodHandle(linker, symbolLookup, "RegDeleteKeyW", FunctionDescriptor.of(ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS, // hKey
                ValueLayout.ADDRESS)); // lpSubKey

        // RegDeleteKeyTransactedW does not work before Windows Vista / Windows Server 2008
        regDeleteKeyTransacted = optionalFunctionMethodHandle(linker, symbolLookup, "RegDeleteKeyTransactedW", FunctionDescriptor.of(
                ValueLayout.JAVA_INT, // return value
                ValueLayout.ADDRESS, // hKey
                ValueLayout.ADDRESS, // lpSubKey
                ValueLayout.JAVA_INT, // samDesired
                ValueLayout.JAVA_INT, // Reserved
                ValueLayout.ADDRESS, // hTransaction
                ValueLayout.ADDRESS)); // pExtendedParemeter

        regDeleteValue = functionMethodHandle(linker, symbolLookup, "RegDeleteValueW", FunctionDescriptor.of(ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS, // hKey
                ValueLayout.ADDRESS)); // lpValueName

        regEnumKeyEx = functionMethodHandle(linker, symbolLookup, "RegEnumKeyExW", FunctionDescriptor.of(ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS, // hKey
                ValueLayout.JAVA_INT, // dwIndex
                ValueLayout.ADDRESS, // lpName
                ValueLayout.ADDRESS, // lpcchName
                ValueLayout.ADDRESS, // lpReserved
                ValueLayout.ADDRESS, // lpClass
                ValueLayout.ADDRESS, // lpcchClass
                ValueLayout.ADDRESS)); // lpftLastWriteTime

        regEnumValue = functionMethodHandle(linker, symbolLookup, "RegEnumValueW", FunctionDescriptor.of(ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS, // hKey
                ValueLayout.JAVA_INT, // dwIndex
                ValueLayout.ADDRESS, // lpValueName
                ValueLayout.ADDRESS, // lpcchValueName
                ValueLayout.ADDRESS, // lpReserved
                ValueLayout.ADDRESS, // lpType
                ValueLayout.ADDRESS, // lpData
                ValueLayout.ADDRESS)); // lpcbData

        regOpenKeyEx = functionMethodHandle(linker, symbolLookup, "RegOpenKeyExW", FunctionDescriptor.of(ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS, // hKey
                ValueLayout.ADDRESS, // lpSubKey
                ValueLayout.JAVA_INT, // ulOptions
                ValueLayout.JAVA_INT, // samDesired
                ValueLayout.ADDRESS)); // phkResult

        // RegOpenKeyTransactedW does not work before Windows Vista / Windows Server 2008
        regOpenKeyTransacted = optionalFunctionMethodHandle(linker, symbolLookup, "RegOpenKeyTransactedW", FunctionDescriptor.of(ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS, // hKey
                ValueLayout.ADDRESS, // lpSubKey
                ValueLayout.JAVA_INT, // ulOptions
                ValueLayout.JAVA_INT, // samDesired
                ValueLayout.ADDRESS, // phkResult
                ValueLayout.ADDRESS, // hTransaction
                ValueLayout.ADDRESS)); // pExtendedParemeter

        regQueryInfoKey = functionMethodHandle(linker, symbolLookup, "RegQueryInfoKeyW", FunctionDescriptor.of(ValueLayout.JAVA_INT,
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

        regQueryValueEx = functionMethodHandle(linker, symbolLookup, "RegQueryValueExW", FunctionDescriptor.of(ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS, // hKey
                ValueLayout.ADDRESS, // lpValueName
                ValueLayout.ADDRESS, // lpReserved
                ValueLayout.ADDRESS, // lpType
                ValueLayout.ADDRESS, // lpData
                ValueLayout.ADDRESS)); // lpcbData

        // RegRenameKey does not work before Windows Vista / Windows Server 2008
        regRenameKey = optionalFunctionMethodHandle(linker, symbolLookup, "RegRenameKey", FunctionDescriptor.of(ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS, // hKey
                ValueLayout.ADDRESS, // lpSubKeyName
                ValueLayout.ADDRESS)); // lpNewKeyName

        regSetValueEx = functionMethodHandle(linker, symbolLookup, "RegSetValueExW", FunctionDescriptor.of(ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS, // hKey
                ValueLayout.ADDRESS, // lpValueName
                ValueLayout.JAVA_INT, // Reserved
                ValueLayout.JAVA_INT, // dwType
                ValueLayout.ADDRESS, // lpData
                ValueLayout.JAVA_INT)); // cbData
    }

    @Override
    public int RegCloseKey(
            MemorySegment hKey) {

        try {
            return (int) regCloseKey.invokeExact(
                    hKey);
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public int RegConnectRegistry(
            MemorySegment lpMachineName,
            MemorySegment hKey,
            MemorySegment phkResult) {

        try {
            return (int) regConnectRegistry.invokeExact(
                    lpMachineName,
                    hKey,
                    phkResult);
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public int RegCreateKeyEx(
            MemorySegment hKey,
            MemorySegment lpSubKey,
            int Reserved, // NOSONAR
            MemorySegment lpClass,
            int dwOptions,
            int samDesired,
            MemorySegment lpSecurityAttributes,
            MemorySegment phkResult,
            MemorySegment lpdwDisposition) {

        try {
            return (int) regCreateKeyEx.invokeExact(
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

    @Override
    public int RegCreateKeyTransacted(
            MemorySegment hKey,
            MemorySegment lpSubKey,
            int Reserved, // NOSONAR
            MemorySegment lpClass,
            int dwOptions,
            int samDesired,
            MemorySegment lpSecurityAttributes,
            MemorySegment phkResult,
            MemorySegment lpdwDisposition,
            MemorySegment hTransaction,
            MemorySegment pExtendedParemeter) {

        MethodHandle regCreateKeyTransactedHandle = regCreateKeyTransacted.orElseThrow(UnsupportedOperationException::new);
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

    @Override
    public boolean isRegCreateKeyTransactedEnabled() {
        return regCreateKeyTransacted.isPresent();
    }

    @Override
    public int RegDeleteKey(
            MemorySegment hKey,
            MemorySegment lpSubKey) {

        try {
            return (int) regDeleteKey.invokeExact(
                    hKey,
                    lpSubKey);
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public int RegDeleteKeyTransacted(
            MemorySegment hKey,
            MemorySegment lpSubKey,
            int samDesired,
            int Reserved, // NOSONAR
            MemorySegment hTransaction,
            MemorySegment pExtendedParameter) {

        MethodHandle regDeleteKeyTransactedHandle = regDeleteKeyTransacted.orElseThrow(UnsupportedOperationException::new);
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

    @Override
    public boolean isRegDeleteKeyTransactedEnabled() {
        return regDeleteKeyTransacted.isPresent();
    }

    @Override
    public int RegDeleteValue(
            MemorySegment hKey,
            MemorySegment lpValueName) {

        try {
            return (int) regDeleteValue.invokeExact(
                    hKey,
                    lpValueName);
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public int RegEnumKeyEx(
            MemorySegment hKey,
            int dwIndex,
            MemorySegment lpName,
            MemorySegment lpcchName,
            MemorySegment lpReserved,
            MemorySegment lpClass,
            MemorySegment lpcchClass,
            MemorySegment lpftLastWriteTime) {

        try {
            return (int) regEnumKeyEx.invokeExact(
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

    @Override
    public int RegEnumValue(
            MemorySegment hKey,
            int dwIndex,
            MemorySegment lpValueName,
            MemorySegment lpcchValueName,
            MemorySegment lpReserved,
            MemorySegment lpType,
            MemorySegment lpData,
            MemorySegment lpcbData) {

        try {
            return (int) regEnumValue.invokeExact(
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

    @Override
    public int RegOpenKeyEx(
            MemorySegment hKey,
            MemorySegment lpSubKey,
            int ulOptions,
            int samDesired,
            MemorySegment phkResult) {

        try {
            return (int) regOpenKeyEx.invokeExact(
                    hKey,
                    lpSubKey,
                    ulOptions,
                    samDesired,
                    phkResult);
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public int RegOpenKeyTransacted(
            MemorySegment hKey,
            MemorySegment lpSubKey,
            int ulOptions,
            int samDesired,
            MemorySegment phkResult,
            MemorySegment hTransaction,
            MemorySegment pExtendedParemeter) {

        MethodHandle regOpenKeyTransactedHandle = regOpenKeyTransacted.orElseThrow(UnsupportedOperationException::new);
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

    @Override
    public boolean isRegOpenKeyTransactedEnabled() {
        return regOpenKeyTransacted.isPresent();
    }

    @Override
    public int RegQueryInfoKey(
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
            return (int) regQueryInfoKey.invokeExact(
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

    @Override
    public int RegQueryValueEx(
            MemorySegment hKey,
            MemorySegment lpValueName,
            MemorySegment lpReserved,
            MemorySegment lpType,
            MemorySegment lpData,
            MemorySegment lpcbData) {

        try {
            return (int) regQueryValueEx.invokeExact(
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

    @Override
    public int RegRenameKey(
            MemorySegment hKey,
            MemorySegment lpSubKeyName,
            MemorySegment lpNewKeyName) {

        MethodHandle regRenameKeyHandle = regRenameKey.orElseThrow(UnsupportedOperationException::new);
        try {
            return (int) regRenameKeyHandle.invokeExact(
                    hKey,
                    lpSubKeyName,
                    lpNewKeyName);
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public boolean isRegRenameKeyEnabled() {
        return regRenameKey.isPresent();
    }

    @Override
    public int RegSetValueEx(
            MemorySegment hKey,
            MemorySegment lpValueName,
            int Reserved, // NOSONAR
            int dwType,
            MemorySegment lpData,
            int cbData) {

        try {
            return (int) regSetValueEx.invokeExact(
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
