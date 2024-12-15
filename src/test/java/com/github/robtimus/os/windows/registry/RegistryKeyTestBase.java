/*
 * RegistryKeyTestBase.java
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

import static com.github.robtimus.os.windows.registry.foreign.ForeignTestUtils.ALLOCATOR;
import static com.github.robtimus.os.windows.registry.foreign.ForeignTestUtils.copyData;
import static com.github.robtimus.os.windows.registry.foreign.ForeignTestUtils.eqHKEY;
import static com.github.robtimus.os.windows.registry.foreign.ForeignTestUtils.eqPointer;
import static com.github.robtimus.os.windows.registry.foreign.ForeignTestUtils.newHKEY;
import static com.github.robtimus.os.windows.registry.foreign.ForeignTestUtils.setHKEY;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.util.Arrays;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import com.github.robtimus.os.windows.registry.foreign.Advapi32;
import com.github.robtimus.os.windows.registry.foreign.BytePointer;
import com.github.robtimus.os.windows.registry.foreign.IntPointer;
import com.github.robtimus.os.windows.registry.foreign.StringPointer;
import com.github.robtimus.os.windows.registry.foreign.WinDef.HKEY;
import com.github.robtimus.os.windows.registry.foreign.WinError;

abstract class RegistryKeyTestBase {

    @BeforeEach
    void setup() {
        RegistryKey.api = mock(Advapi32.class);
    }

    @AfterEach
    void teardown() {
        RegistryKey.api = Advapi32.INSTANCE;
    }

    static HKEY mockOpenAndClose(HKEY hKey, String path) {
        HKEY result = mockOpen(hKey, path);
        mockClose(hKey);
        return result;
    }

    static HKEY mockOpen(HKEY hKey, String path) {
        HKEY result = newHKEY();

        when(RegistryKey.api.RegOpenKeyEx(eqHKEY(hKey), eqPointer(path), anyInt(), anyInt(), any())).thenAnswer(i -> {
            setHKEY(i.getArgument(4, HKEY.Reference.class), result);

            return WinError.ERROR_SUCCESS;
        });

        return result;
    }

    static void mockOpenFailure(HKEY hKey, String path, int result) {
        doReturn(result).when(RegistryKey.api).RegOpenKeyEx(eqHKEY(hKey), eqPointer(path), anyInt(), anyInt(), any());
    }

    static HKEY mockConnectAndClose(HKEY hKey, String machineName) {
        HKEY result = mockConnect(hKey, machineName);
        mockClose(hKey);
        return result;
    }

    static HKEY mockConnect(HKEY hKey, String machineName) {
        HKEY result = newHKEY();

        when(RegistryKey.api.RegConnectRegistry(eqPointer(machineName), eqHKEY(hKey), any())).thenAnswer(i -> {
            setHKEY(i.getArgument(2, HKEY.Reference.class), result);

            return WinError.ERROR_SUCCESS;
        });

        return result;
    }

    static void mockClose(HKEY hKey) {
        mockClose(hKey, WinError.ERROR_SUCCESS);
    }

    static void mockClose(HKEY hKey, int result) {
        doReturn(result).when(RegistryKey.api).RegCloseKey(eqHKEY(hKey));
    }

    static void mockSubKeys(HKEY hKey, String... names) {
        int maxLength = Arrays.stream(names)
                .mapToInt(String::length)
                .max()
                .orElse(0);

        when(RegistryKey.api.RegQueryInfoKey(eqHKEY(hKey), any(), any(), any(), any(), notNull(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(i -> {
                    i.getArgument(5, IntPointer.class).value(maxLength);
                    return WinError.ERROR_SUCCESS;
                });
        when(RegistryKey.api.RegEnumKeyEx(eqHKEY(hKey), anyInt(), any(), any(), any(), any(), any(), any())).thenAnswer(i -> {
            int index = i.getArgument(1, Integer.class);
            if (index >= names.length) {
                return WinError.ERROR_NO_MORE_ITEMS;
            }
            String name = names[index];

            StringPointer lpName = i.getArgument(2, StringPointer.class);
            lpName.value(name);

            return WinError.ERROR_SUCCESS;
        });
    }

    static void mockValues(HKEY hKey, SettableRegistryValue... values) {
        int maxNameLength = Arrays.stream(values)
                .map(RegistryValue::name)
                .mapToInt(String::length)
                .max()
                .orElseThrow();
        BytePointer[] datas = Arrays.stream(values)
                .map(value -> value.rawData(ALLOCATOR))
                .toArray(BytePointer[]::new);
        int maxValueLength = Arrays.stream(datas)
                .mapToInt(BytePointer::size)
                .max()
                .orElseThrow();

        when(RegistryKey.api.RegQueryInfoKey(eqHKEY(hKey), any(), any(), any(), any(), any(), any(), any(), notNull(), notNull(), any(), any()))
                .thenAnswer(i -> {
                    i.getArgument(8, IntPointer.class).value(maxNameLength);
                    i.getArgument(9, IntPointer.class).value(maxValueLength);
                    return WinError.ERROR_SUCCESS;
                });
        when(RegistryKey.api.RegEnumValue(eqHKEY(hKey), anyInt(), any(), any(), any(), any(), any(), any())).thenAnswer(i -> {
            int index = i.getArgument(1, Integer.class);
            if (index >= values.length) {
                return WinError.ERROR_NO_MORE_ITEMS;
            }
            String name = values[index].name();
            BytePointer data = datas[index];

            StringPointer lpValueName = i.getArgument(2, StringPointer.class);
            lpValueName.value(name);

            i.getArgument(5, IntPointer.class).value(values[index].type());
            copyData(data, i.getArgument(6, BytePointer.class));
            i.getArgument(7, IntPointer.class).value(data.size());

            return WinError.ERROR_SUCCESS;
        });
    }

    static void mockValue(HKEY hKey, SettableRegistryValue value) {
        mockValue(hKey, value, WinError.ERROR_SUCCESS);
    }

    static void mockValue(HKEY hKey, SettableRegistryValue value, int returnCode) {
        BytePointer data = value.rawData(ALLOCATOR);

        when(RegistryKey.api.RegQueryValueEx(eqHKEY(hKey), eqPointer(value.name()), any(), any(), isNull(), any())).thenAnswer(i -> {
            i.getArgument(3, IntPointer.class).value(value.type());
            i.getArgument(5, IntPointer.class).value(data.size());

            return WinError.ERROR_MORE_DATA;
        });
        when(RegistryKey.api.RegQueryValueEx(eqHKEY(hKey), eqPointer(value.name()), any(), isNull(), any(), any())).thenAnswer(i -> {
            copyData(data, i.getArgument(4, BytePointer.class));
            i.getArgument(5, IntPointer.class).value(data.size());

            return returnCode;
        });
    }
}
