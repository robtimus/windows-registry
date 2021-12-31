/*
 * RegistryKeyTest.java
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.util.Arrays;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import com.sun.jna.platform.win32.Advapi32;
import com.sun.jna.platform.win32.WinError;
import com.sun.jna.platform.win32.WinReg.HKEY;
import com.sun.jna.platform.win32.WinReg.HKEYByReference;
import com.sun.jna.ptr.IntByReference;

abstract class RegistryKeyTest {

    @BeforeEach
    void setup() {
        RegistryKey.api = mock(Advapi32.class);
    }

    @AfterEach
    void teardown() {
        RegistryKey.api = Advapi32.INSTANCE;
    }

    static HKEY newHKEY() {
        return new HKEY(1);
    }

    static HKEY newRemoteHKEY() {
        return new HKEY(2);
    }

    static HKEY mockOpenAndClose(HKEY hKey, String path) {
        HKEY result = mockOpen(hKey, path);
        mockClose(hKey);
        return result;
    }

    static HKEY mockOpen(HKEY hKey, String path) {
        HKEY result = newHKEY();

        when(RegistryKey.api.RegOpenKeyEx(eq(hKey), eq(path), anyInt(), anyInt(), any())).thenAnswer(i -> {
            i.getArgument(4, HKEYByReference.class).setValue(result);

            return WinError.ERROR_SUCCESS;
        });

        return result;
    }

    static HKEY mockConnectAndClose(HKEY hKey, String machineName) {
        HKEY result = mockConnect(hKey, machineName);
        mockClose(hKey);
        return result;
    }

    static HKEY mockConnect(HKEY hKey, String machineName) {
        HKEY result = newRemoteHKEY();

        when(RegistryKey.api.RegConnectRegistry(eq(machineName), eq(hKey), any())).thenAnswer(i -> {
            i.getArgument(2, HKEYByReference.class).setValue(result);

            return WinError.ERROR_SUCCESS;
        });

        return result;
    }

    static void mockClose(HKEY hKey) {
        mockClose(hKey, WinError.ERROR_SUCCESS);
    }

    static void mockClose(HKEY hKey, int result) {
        when(RegistryKey.api.RegCloseKey(hKey)).thenReturn(result);
    }

    static void mockSubKeys(HKEY hKey, String... names) {
        int maxLength = Arrays.stream(names)
                .mapToInt(String::length)
                .max()
                .orElseThrow();

        when(RegistryKey.api.RegQueryInfoKey(eq(hKey), any(), any(), any(), any(), notNull(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(i -> {
                    i.getArgument(5, IntByReference.class).setValue(maxLength);
                    return WinError.ERROR_SUCCESS;
                });
        when(RegistryKey.api.RegEnumKeyEx(eq(hKey), anyInt(), any(), any(), any(), any(), any(), any())).thenAnswer(i -> {
            int index = i.getArgument(1, Integer.class);
            if (index >= names.length) {
                return WinError.ERROR_NO_MORE_ITEMS;
            }
            String name = names[index];

            char[] lpName = i.getArgument(2, char[].class);
            Arrays.fill(lpName, '\0');
            name.getChars(0, name.length(), lpName, 0);

            return WinError.ERROR_SUCCESS;
        });
    }

    static void mockValues(HKEY hKey, SettableRegistryValue... values) {
        int maxNameLength = Arrays.stream(values)
                .map(RegistryValue::name)
                .mapToInt(String::length)
                .max()
                .orElseThrow();
        byte[][] datas = Arrays.stream(values)
                .map(SettableRegistryValue::rawData)
                .toArray(byte[][]::new);
        int maxValueLength = Arrays.stream(datas)
                .mapToInt(d -> d.length)
                .max()
                .orElseThrow();

        when(RegistryKey.api.RegQueryInfoKey(eq(hKey), any(), any(), any(), any(), any(), any(), any(), notNull(), notNull(), any(), any()))
                .thenAnswer(i -> {
                    i.getArgument(8, IntByReference.class).setValue(maxNameLength);
                    i.getArgument(9, IntByReference.class).setValue(maxValueLength);
                    return WinError.ERROR_SUCCESS;
                });
        when(RegistryKey.api.RegEnumValue(eq(hKey), anyInt(), any(), any(), any(), any(), any(byte[].class), any())).thenAnswer(i -> {
            int index = i.getArgument(1, Integer.class);
            if (index >= values.length) {
                return WinError.ERROR_NO_MORE_ITEMS;
            }
            String name = values[index].name();
            byte[] data = datas[index];

            char[] lpValueName = i.getArgument(2, char[].class);
            Arrays.fill(lpValueName, '\0');
            name.getChars(0, name.length(), lpValueName, 0);

            i.getArgument(5, IntByReference.class).setValue(values[index].type());
            System.arraycopy(data, 0, i.getArgument(6, byte[].class), 0, data.length);
            i.getArgument(7, IntByReference.class).setValue(data.length);

            return WinError.ERROR_SUCCESS;
        });
    }

    static void mockValue(HKEY hKey, SettableRegistryValue value) {
        mockValue(hKey, value, WinError.ERROR_SUCCESS);
    }

    static void mockValue(HKEY hKey, SettableRegistryValue value, int returnCode) {
        byte[] data = value.rawData();

        when(RegistryKey.api.RegQueryValueEx(eq(hKey), eq(value.name()), anyInt(), any(), (byte[]) isNull(), any())).thenAnswer(i -> {
            i.getArgument(3, IntByReference.class).setValue(value.type());
            i.getArgument(5, IntByReference.class).setValue(data.length);

            return WinError.ERROR_MORE_DATA;
        });
        when(RegistryKey.api.RegQueryValueEx(eq(hKey), eq(value.name()), anyInt(), isNull(), any(byte[].class), any())).thenAnswer(i -> {
            System.arraycopy(data, 0, i.getArgument(4, byte[].class), 0, data.length);
            i.getArgument(5, IntByReference.class).setValue(data.length);

            return returnCode;
        });
    }
}
