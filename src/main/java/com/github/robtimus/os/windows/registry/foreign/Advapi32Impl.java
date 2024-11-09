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
import java.lang.foreign.Linker;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.Optional;
import com.github.robtimus.os.windows.registry.foreign.WinDef.FILETIME;
import com.github.robtimus.os.windows.registry.foreign.WinDef.HKEY;

final class Advapi32Impl extends ApiImpl implements Advapi32 {

    private final MethodHandle regCloseKey;
    private final MethodHandle regConnectRegistry;
    private final MethodHandle regCreateKeyEx;
    private final MethodHandle regDeleteKey;
    private final MethodHandle regDeleteValue;
    private final MethodHandle regEnumKeyEx;
    private final MethodHandle regEnumValue;
    private final MethodHandle regOpenKeyEx;
    private final MethodHandle regQueryInfoKey;
    private final MethodHandle regQueryValueEx;
    private final Optional<MethodHandle> regRenameKey;
    private final MethodHandle regSetValueEx;

    @SuppressWarnings("nls")
    Advapi32Impl() {
        Linker linker = Linker.nativeLinker();
        SymbolLookup symbolLookup = SymbolLookup.libraryLookup("Advapi32", ARENA);

        regCloseKey = functionMethodHandle(linker, symbolLookup, "RegCloseKey", ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS); // hKey

        regConnectRegistry = functionMethodHandle(linker, symbolLookup, "RegConnectRegistryW", ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS, // lpMachineName
                        ValueLayout.ADDRESS, // hKey
                        ValueLayout.ADDRESS); // phkResult

        regCreateKeyEx = functionMethodHandle(linker, symbolLookup, "RegCreateKeyExW", ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS, // hKey
                        ValueLayout.ADDRESS, // lpSubKey
                        ValueLayout.JAVA_INT, // Reserved
                        ValueLayout.ADDRESS, // lpClass
                        ValueLayout.JAVA_INT, // dwOptions
                        ValueLayout.JAVA_INT, // samDesired
                        ValueLayout.ADDRESS, // lpSecurityAttributes
                        ValueLayout.ADDRESS, // phkResult
                        ValueLayout.ADDRESS); // lpdwDisposition

        regDeleteKey = functionMethodHandle(linker, symbolLookup, "RegDeleteKeyW", ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS, // hKey
                        ValueLayout.ADDRESS); // lpSubKey

        regDeleteValue = functionMethodHandle(linker, symbolLookup, "RegDeleteValueW", ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS, // hKey
                        ValueLayout.ADDRESS); // lpValueName

        regEnumKeyEx = functionMethodHandle(linker, symbolLookup, "RegEnumKeyExW", ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS, // hKey
                        ValueLayout.JAVA_INT, // dwIndex
                        ValueLayout.ADDRESS, // lpName
                        ValueLayout.ADDRESS, // lpcchName
                        ValueLayout.ADDRESS, // lpReserved
                        ValueLayout.ADDRESS, // lpClass
                        ValueLayout.ADDRESS, // lpcchClass
                        ValueLayout.ADDRESS); // lpftLastWriteTime

        regEnumValue = functionMethodHandle(linker, symbolLookup, "RegEnumValueW", ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS, // hKey
                        ValueLayout.JAVA_INT, // dwIndex
                        ValueLayout.ADDRESS, // lpValueName
                        ValueLayout.ADDRESS, // lpcchValueName
                        ValueLayout.ADDRESS, // lpReserved
                        ValueLayout.ADDRESS, // lpType
                        ValueLayout.ADDRESS, // lpData
                        ValueLayout.ADDRESS); // lpcbData

        regOpenKeyEx = functionMethodHandle(linker, symbolLookup, "RegOpenKeyExW", ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS, // hKey
                        ValueLayout.ADDRESS, // lpSubKey
                        ValueLayout.JAVA_INT, // ulOptions
                        ValueLayout.JAVA_INT, // samDesired
                        ValueLayout.ADDRESS); // phkResult

        regQueryInfoKey = functionMethodHandle(linker, symbolLookup, "RegQueryInfoKeyW", ValueLayout.JAVA_INT,
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
                        ValueLayout.ADDRESS); // lpftLastWriteTime

        regQueryValueEx = functionMethodHandle(linker, symbolLookup, "RegQueryValueExW", ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS, // hKey
                        ValueLayout.ADDRESS, // lpValueName
                        ValueLayout.ADDRESS, // lpReserved
                        ValueLayout.ADDRESS, // lpType
                        ValueLayout.ADDRESS, // lpData
                        ValueLayout.ADDRESS); // lpcbData

        // RegRenameKey does not work before Windows Vista / Windows Server 2008
        regRenameKey = optionalFunctionMethodHandle(linker, symbolLookup, "RegRenameKey", ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS, // hKey
                        ValueLayout.ADDRESS, // lpSubKeyName
                        ValueLayout.ADDRESS); // lpNewKeyName

        regSetValueEx = functionMethodHandle(linker, symbolLookup, "RegSetValueExW", ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS, // hKey
                        ValueLayout.ADDRESS, // lpValueName
                        ValueLayout.JAVA_INT, // Reserved
                        ValueLayout.JAVA_INT, // dwType
                        ValueLayout.ADDRESS, // lpData
                        ValueLayout.JAVA_INT); // cbData
    }

    @Override
    public int RegCloseKey(
            HKEY hKey) {

        try {
            return (int) regCloseKey.invokeExact(
                    hKey.segment());
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public int RegConnectRegistry(
            StringPointer lpMachineName,
            HKEY hKey,
            HKEY.Reference phkResult) {

        try {
            return (int) regConnectRegistry.invokeExact(
                    segment(lpMachineName),
                    hKey.segment(),
                    phkResult.segment());
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public int RegCreateKeyEx(
            HKEY hKey,
            StringPointer lpSubKey,
            int Reserved, // NOSONAR
            StringPointer lpClass,
            int dwOptions,
            int samDesired,
            NullPointer lpSecurityAttributes,
            HKEY.Reference phkResult,
            IntPointer lpdwDisposition) {

        try {
            return (int) regCreateKeyEx.invokeExact(
                    hKey.segment(),
                    lpSubKey.segment(),
                    Reserved,
                    segment(lpClass),
                    dwOptions,
                    samDesired,
                    segment(lpSecurityAttributes),
                    phkResult.segment(),
                    segment(lpdwDisposition));
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public int RegDeleteKey(
            HKEY hKey,
            StringPointer lpSubKey) {

        try {
            return (int) regDeleteKey.invokeExact(
                    hKey.segment(),
                    lpSubKey.segment());
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public int RegDeleteValue(
            HKEY hKey,
            StringPointer lpValueName) {

        try {
            return (int) regDeleteValue.invokeExact(
                    hKey.segment(),
                    segment(lpValueName));
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public int RegEnumKeyEx(
            HKEY hKey,
            int dwIndex,
            StringPointer lpName,
            IntPointer lpcchName,
            NullPointer lpReserved,
            StringPointer lpClass,
            IntPointer lpcchClass,
            FILETIME lpftLastWriteTime) {

        try {
            return (int) regEnumKeyEx.invokeExact(
                    hKey.segment(),
                    dwIndex,
                    lpName.segment(),
                    lpcchName.segment(),
                    segment(lpReserved),
                    segment(lpClass),
                    segment(lpcchClass),
                    segment(lpftLastWriteTime));
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public int RegEnumValue(
            HKEY hKey,
            int dwIndex,
            StringPointer lpValueName,
            IntPointer lpcchValueName,
            NullPointer lpReserved,
            IntPointer lpType,
            BytePointer lpData,
            IntPointer lpcbData) {

        try {
            return (int) regEnumValue.invokeExact(
                    hKey.segment(),
                    dwIndex,
                    lpValueName.segment(),
                    lpcchValueName.segment(),
                    segment(lpReserved),
                    segment(lpType),
                    segment(lpData),
                    segment(lpcbData));
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public int RegOpenKeyEx(
            HKEY hKey,
            StringPointer lpSubKey,
            int ulOptions,
            int samDesired,
            HKEY.Reference phkResult) {

        try {
            return (int) regOpenKeyEx.invokeExact(
                    hKey.segment(),
                    segment(lpSubKey),
                    ulOptions,
                    samDesired,
                    phkResult.segment());
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public int RegQueryInfoKey(
            HKEY hKey,
            StringPointer lpClass,
            IntPointer lpcchClass,
            NullPointer lpReserved,
            IntPointer lpcSubKeys,
            IntPointer lpcbMaxSubKeyLen,
            IntPointer lpcbMaxClassLen,
            IntPointer lpcValues,
            IntPointer lpcbMaxValueNameLen,
            IntPointer lpcbMaxValueLen,
            IntPointer lpcbSecurityDescriptor,
            FILETIME lpftLastWriteTime) {

        try {
            return (int) regQueryInfoKey.invokeExact(
                    hKey.segment(),
                    segment(lpClass),
                    segment(lpcchClass),
                    segment(lpReserved),
                    segment(lpcSubKeys),
                    segment(lpcbMaxSubKeyLen),
                    segment(lpcbMaxClassLen),
                    segment(lpcValues),
                    segment(lpcbMaxValueNameLen),
                    segment(lpcbMaxValueLen),
                    segment(lpcbSecurityDescriptor),
                    segment(lpftLastWriteTime));
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public int RegQueryValueEx(
            HKEY hKey,
            StringPointer lpValueName,
            NullPointer lpReserved,
            IntPointer lpType,
            BytePointer lpData,
            IntPointer lpcbData) {

        try {
            return (int) regQueryValueEx.invokeExact(
                    hKey.segment(),
                    segment(lpValueName),
                    segment(lpReserved),
                    segment(lpType),
                    segment(lpData),
                    segment(lpcbData));
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public int RegRenameKey(
            HKEY hKey,
            StringPointer lpSubKeyName,
            StringPointer lpNewKeyName) {

        MethodHandle regRenameKeyHandle = regRenameKey.orElseThrow(UnsupportedOperationException::new);
        try {
            return (int) regRenameKeyHandle.invokeExact(
                    hKey.segment(),
                    segment(lpSubKeyName),
                    lpNewKeyName.segment());
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
            HKEY hKey,
            StringPointer lpValueName,
            int Reserved, // NOSONAR
            int dwType,
            BytePointer lpData,
            int cbData) {

        try {
            return (int) regSetValueEx.invokeExact(
                    hKey.segment(),
                    segment(lpValueName),
                    Reserved,
                    dwType,
                    segment(lpData),
                    cbData);
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        }
    }
}
