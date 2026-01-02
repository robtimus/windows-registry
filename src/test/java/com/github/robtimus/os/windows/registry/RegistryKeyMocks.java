/*
 * RegistryKeyMocks.java
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

import static com.github.robtimus.os.windows.registry.ForeignTestUtils.copyData;
import static com.github.robtimus.os.windows.registry.ForeignTestUtils.eqPointer;
import static com.github.robtimus.os.windows.registry.ForeignTestUtils.isNULL;
import static com.github.robtimus.os.windows.registry.ForeignTestUtils.newHKEY;
import static com.github.robtimus.os.windows.registry.ForeignTestUtils.notNULL;
import static com.github.robtimus.os.windows.registry.ForeignTestUtils.setHKEY;
import static com.github.robtimus.os.windows.registry.RegistryTestBase.advapi32;
import static com.github.robtimus.os.windows.registry.RegistryTestBase.arena;
import static com.github.robtimus.os.windows.registry.foreign.Advapi32.RegCloseKey;
import static com.github.robtimus.os.windows.registry.foreign.Advapi32.RegConnectRegistry;
import static com.github.robtimus.os.windows.registry.foreign.Advapi32.RegEnumKeyEx;
import static com.github.robtimus.os.windows.registry.foreign.Advapi32.RegEnumValue;
import static com.github.robtimus.os.windows.registry.foreign.Advapi32.RegOpenKeyEx;
import static com.github.robtimus.os.windows.registry.foreign.Advapi32.RegQueryInfoKey;
import static com.github.robtimus.os.windows.registry.foreign.Advapi32.RegQueryValueEx;
import static java.lang.Math.toIntExact;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.notNull;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Arrays;
import com.github.robtimus.os.windows.registry.foreign.WString;
import com.github.robtimus.os.windows.registry.foreign.WinError;

final class RegistryKeyMocks {

    private RegistryKeyMocks() {
    }

    static MemorySegment mockOpenAndClose(MemorySegment hKey, String path) {
        MemorySegment result = mockOpen(hKey, path);
        mockClose(hKey);
        return result;
    }

    static MemorySegment mockOpen(MemorySegment hKey, String path) {
        MemorySegment result = newHKEY(arena);

        advapi32.when(() -> RegOpenKeyEx(eq(hKey), eqPointer(path), anyInt(), anyInt(), notNull())).thenAnswer(i -> {
            setHKEY(i.getArgument(4, MemorySegment.class), result);

            return WinError.ERROR_SUCCESS;
        });

        return result;
    }

    static void mockOpenFailure(MemorySegment hKey, String path, int result) {
        advapi32.when(() -> RegOpenKeyEx(eq(hKey), eqPointer(path), anyInt(), anyInt(), notNull())).thenReturn(result);
    }

    static MemorySegment mockConnectAndClose(MemorySegment hKey, String machineName) {
        MemorySegment result = mockConnect(hKey, machineName);
        mockClose(hKey);
        return result;
    }

    static MemorySegment mockConnect(MemorySegment hKey, String machineName) {
        MemorySegment result = newHKEY(arena);

        advapi32.when(() -> RegConnectRegistry(eqPointer(machineName), eq(hKey), notNull())).thenAnswer(i -> {
            setHKEY(i.getArgument(2, MemorySegment.class), result);

            return WinError.ERROR_SUCCESS;
        });

        return result;
    }

    static void mockClose(MemorySegment hKey) {
        mockClose(hKey, WinError.ERROR_SUCCESS);
    }

    static void mockClose(MemorySegment hKey, int result) {
        advapi32.when(() -> RegCloseKey(hKey)).thenReturn(result);
    }

    static void mockSubKeys(MemorySegment hKey, String... names) {
        int maxLength = Arrays.stream(names)
                .mapToInt(String::length)
                .max()
                .orElse(0);

        advapi32.when(() -> RegQueryInfoKey(eq(hKey), notNull(), notNull(), notNull(), notNull(), notNULL(), notNull(), notNull(), notNull(),
                notNull(), notNull(), notNull()))
                .thenAnswer(i -> {
                    i.getArgument(5, MemorySegment.class).set(ValueLayout.JAVA_INT, 0, maxLength);
                    return WinError.ERROR_SUCCESS;
                });
        advapi32.when(() -> RegEnumKeyEx(eq(hKey), anyInt(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull())).thenAnswer(i -> {
            int index = i.getArgument(1, Integer.class);
            if (index >= names.length) {
                return WinError.ERROR_NO_MORE_ITEMS;
            }
            String name = names[index];

            MemorySegment lpName = i.getArgument(2, MemorySegment.class);
            WString.copy(name, lpName, 0);
            i.getArgument(3, MemorySegment.class).set(ValueLayout.JAVA_INT, 0, name.length());

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
                .map(value -> value.rawData(arena))
                .toArray(MemorySegment[]::new);
        int maxValueLength = Arrays.stream(datas)
                .mapToLong(MemorySegment::byteSize)
                .mapToInt(Math::toIntExact)
                .max()
                .orElseThrow();

        advapi32.when(() -> RegQueryInfoKey(eq(hKey), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNULL(),
                notNULL(), notNull(), notNull()))
                .thenAnswer(i -> {
                    i.getArgument(8, MemorySegment.class).set(ValueLayout.JAVA_INT, 0, maxNameLength);
                    i.getArgument(9, MemorySegment.class).set(ValueLayout.JAVA_INT, 0, maxValueLength);
                    return WinError.ERROR_SUCCESS;
                });
        advapi32.when(() -> RegEnumValue(eq(hKey), anyInt(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull())).thenAnswer(i -> {
            int index = i.getArgument(1, Integer.class);
            if (index >= values.length) {
                return WinError.ERROR_NO_MORE_ITEMS;
            }
            String name = values[index].name();
            MemorySegment data = datas[index];

            MemorySegment lpValueName = i.getArgument(2, MemorySegment.class);
            WString.copy(name, lpValueName, 0);
            i.getArgument(3, MemorySegment.class).set(ValueLayout.JAVA_INT, 0, name.length());

            i.getArgument(5, MemorySegment.class).set(ValueLayout.JAVA_INT, 0, values[index].type());
            copyData(data, i.getArgument(6, MemorySegment.class));
            i.getArgument(7, MemorySegment.class).set(ValueLayout.JAVA_INT, 0, toIntExact(data.byteSize()));

            return WinError.ERROR_SUCCESS;
        });
    }

    static void mockValue(MemorySegment hKey, SettableRegistryValue value) {
        mockValue(hKey, value, WinError.ERROR_SUCCESS);
    }

    static void mockValue(MemorySegment hKey, SettableRegistryValue value, int returnCode) {
        MemorySegment data = value.rawData(arena);

        advapi32.when(() -> RegQueryValueEx(eq(hKey), eqPointer(value.name()), notNull(), notNull(), isNULL(), notNull())).thenAnswer(i -> {
            i.getArgument(3, MemorySegment.class).set(ValueLayout.JAVA_INT, 0, value.type());
            i.getArgument(5, MemorySegment.class).set(ValueLayout.JAVA_INT, 0, toIntExact(data.byteSize()));

            return WinError.ERROR_MORE_DATA;
        });
        advapi32.when(() -> RegQueryValueEx(eq(hKey), eqPointer(value.name()), notNull(), isNULL(), notNull(), notNull())).thenAnswer(i -> {
            copyData(data, i.getArgument(4, MemorySegment.class));
            i.getArgument(5, MemorySegment.class).set(ValueLayout.JAVA_INT, 0, toIntExact(data.byteSize()));

            return returnCode;
        });
    }
}
