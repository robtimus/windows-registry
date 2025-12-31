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
import static com.github.robtimus.os.windows.registry.foreign.ForeignTestUtils.eqPointer;
import static com.github.robtimus.os.windows.registry.foreign.ForeignTestUtils.isNULL;
import static com.github.robtimus.os.windows.registry.foreign.ForeignTestUtils.newHKEY;
import static com.github.robtimus.os.windows.registry.foreign.ForeignTestUtils.notNULL;
import static com.github.robtimus.os.windows.registry.foreign.ForeignTestUtils.setHKEY;
import static com.github.robtimus.os.windows.registry.foreign.ForeignUtils.setInt;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.lang.foreign.MemorySegment;
import java.util.Arrays;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import com.github.robtimus.os.windows.registry.foreign.Advapi32;
import com.github.robtimus.os.windows.registry.foreign.WString;
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

    static MemorySegment mockOpenAndClose(MemorySegment hKey, String path) {
        MemorySegment result = mockOpen(hKey, path);
        mockClose(hKey);
        return result;
    }

    static MemorySegment mockOpen(MemorySegment hKey, String path) {
        MemorySegment result = newHKEY();

        when(RegistryKey.api.RegOpenKeyEx(eq(hKey), eqPointer(path), anyInt(), anyInt(), notNull())).thenAnswer(i -> {
            setHKEY(i.getArgument(4, MemorySegment.class), result);

            return WinError.ERROR_SUCCESS;
        });

        return result;
    }

    static void mockOpenFailure(MemorySegment hKey, String path, int result) {
        doReturn(result).when(RegistryKey.api).RegOpenKeyEx(eq(hKey), eqPointer(path), anyInt(), anyInt(), notNull());
    }

    static MemorySegment mockConnectAndClose(MemorySegment hKey, String machineName) {
        MemorySegment result = mockConnect(hKey, machineName);
        mockClose(hKey);
        return result;
    }

    static MemorySegment mockConnect(MemorySegment hKey, String machineName) {
        MemorySegment result = newHKEY();

        when(RegistryKey.api.RegConnectRegistry(eqPointer(machineName), eq(hKey), notNull())).thenAnswer(i -> {
            setHKEY(i.getArgument(2, MemorySegment.class), result);

            return WinError.ERROR_SUCCESS;
        });

        return result;
    }

    static void mockClose(MemorySegment hKey) {
        mockClose(hKey, WinError.ERROR_SUCCESS);
    }

    static void mockClose(MemorySegment hKey, int result) {
        doReturn(result).when(RegistryKey.api).RegCloseKey(hKey);
    }

    static void mockSubKeys(MemorySegment hKey, String... names) {
        int maxLength = Arrays.stream(names)
                .mapToInt(String::length)
                .max()
                .orElse(0);

        when(RegistryKey.api.RegQueryInfoKey(eq(hKey), notNull(), notNull(), notNull(), notNull(), notNULL(), notNull(), notNull(), notNull(),
                notNull(), notNull(), notNull()))
                .thenAnswer(i -> {
                    setInt(i.getArgument(5, MemorySegment.class), maxLength);
                    return WinError.ERROR_SUCCESS;
                });
        when(RegistryKey.api.RegEnumKeyEx(eq(hKey), anyInt(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull())).thenAnswer(i -> {
            int index = i.getArgument(1, Integer.class);
            if (index >= names.length) {
                return WinError.ERROR_NO_MORE_ITEMS;
            }
            String name = names[index];

            MemorySegment lpName = i.getArgument(2, MemorySegment.class);
            WString.copy(name, lpName, 0);
            setInt(i.getArgument(3, MemorySegment.class), name.length());

            return WinError.ERROR_SUCCESS;
        });
    }

    static void mockValues(MemorySegment hKey, SettableRegistryValue... values) {
        int maxNameLength = Arrays.stream(values)
                .map(RegistryValue::name)
                .mapToInt(String::length)
                .max()
                .orElseThrow();
        MemorySegment[] datas = Arrays.stream(values)
                .map(value -> value.rawData(ALLOCATOR))
                .toArray(MemorySegment[]::new);
        int maxValueLength = Arrays.stream(datas)
                .mapToLong(MemorySegment::byteSize)
                .mapToInt(Math::toIntExact)
                .max()
                .orElseThrow();

        when(RegistryKey.api.RegQueryInfoKey(eq(hKey), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNULL(),
                notNULL(), notNull(), notNull()))
                .thenAnswer(i -> {
                    setInt(i.getArgument(8, MemorySegment.class), maxNameLength);
                    setInt(i.getArgument(9, MemorySegment.class), maxValueLength);
                    return WinError.ERROR_SUCCESS;
                });
        when(RegistryKey.api.RegEnumValue(eq(hKey), anyInt(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull())).thenAnswer(i -> {
            int index = i.getArgument(1, Integer.class);
            if (index >= values.length) {
                return WinError.ERROR_NO_MORE_ITEMS;
            }
            String name = values[index].name();
            MemorySegment data = datas[index];

            MemorySegment lpValueName = i.getArgument(2, MemorySegment.class);
            WString.copy(name, lpValueName, 0);
            setInt(i.getArgument(3, MemorySegment.class), name.length());

            setInt(i.getArgument(5, MemorySegment.class), values[index].type());
            copyData(data, i.getArgument(6, MemorySegment.class));
            setInt(i.getArgument(7, MemorySegment.class), data.byteSize());

            return WinError.ERROR_SUCCESS;
        });
    }

    static void mockValue(MemorySegment hKey, SettableRegistryValue value) {
        mockValue(hKey, value, WinError.ERROR_SUCCESS);
    }

    static void mockValue(MemorySegment hKey, SettableRegistryValue value, int returnCode) {
        MemorySegment data = value.rawData(ALLOCATOR);

        when(RegistryKey.api.RegQueryValueEx(eq(hKey), eqPointer(value.name()), notNull(), notNull(), isNULL(), notNull())).thenAnswer(i -> {
            setInt(i.getArgument(3, MemorySegment.class), value.type());
            setInt(i.getArgument(5, MemorySegment.class), data.byteSize());

            return WinError.ERROR_MORE_DATA;
        });
        when(RegistryKey.api.RegQueryValueEx(eq(hKey), eqPointer(value.name()), notNull(), isNULL(), notNull(), notNull())).thenAnswer(i -> {
            copyData(data, i.getArgument(4, MemorySegment.class));
            setInt(i.getArgument(5, MemorySegment.class), data.byteSize());

            return returnCode;
        });
    }
}
