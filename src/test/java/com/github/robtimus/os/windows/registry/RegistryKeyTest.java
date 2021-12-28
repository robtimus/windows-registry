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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import com.sun.jna.platform.win32.Advapi32;
import com.sun.jna.platform.win32.WinError;
import com.sun.jna.ptr.IntByReference;

@SuppressWarnings("nls")
class RegistryKeyTest {

    @BeforeEach
    void mockApi() {
        RegistryKey.api = mock(Advapi32.class);
    }

    @AfterEach
    void restoreApi() {
        RegistryKey.api = Advapi32.INSTANCE;
    }

    @Nested
    @DisplayName("resolved")
    class Resolved {

        @Test
        @DisplayName("name")
        void testName() {
            RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER.resolve("Software\\JavaSoft\\Prefs");
            assertEquals("Prefs", registryKey.name());
        }

        @Test
        @DisplayName("path")
        void testPath() {
            RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER.resolve("Software\\JavaSoft\\Prefs");
            assertEquals("HKEY_CURRENT_USER\\Software\\JavaSoft\\Prefs", registryKey.path());
        }

        @Test
        @DisplayName("isRoot")
        void testIsRoot() {
            RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER.resolve("Software\\JavaSoft\\Prefs");
            assertFalse(registryKey.isRoot());
        }

        @Test
        @DisplayName("root")
        void testRoot() {
            RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER.resolve("Software\\JavaSoft\\Prefs");
            assertSame(RegistryKey.HKEY_CURRENT_USER, registryKey.root());
        }

        @Test
        @DisplayName("parent")
        void testParent() {
            RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER.resolve("Software\\JavaSoft\\Prefs");

            Optional<RegistryKey> parent = registryKey.parent();
            assertEquals(Optional.of("HKEY_CURRENT_USER\\Software\\JavaSoft"), parent.map(RegistryKey::path));

            parent = parent.flatMap(RegistryKey::parent);
            assertEquals(Optional.of("HKEY_CURRENT_USER\\Software"), parent.map(RegistryKey::path));

            parent = parent.flatMap(RegistryKey::parent);
            assertEquals(Optional.of(RegistryKey.HKEY_CURRENT_USER), parent);
        }

        @ParameterizedTest(name = "{0} => {1}")
        @CsvSource({
                ", HKEY_CURRENT_USER\\Software\\JavaSoft\\Prefs",
                "., HKEY_CURRENT_USER\\Software\\JavaSoft\\Prefs",
                ".., HKEY_CURRENT_USER\\Software\\JavaSoft",
                "..\\.., HKEY_CURRENT_USER\\Software",
                "..\\..\\.., HKEY_CURRENT_USER",
                "..\\..\\..\\.., HKEY_CURRENT_USER",
                "child, HKEY_CURRENT_USER\\Software\\JavaSoft\\Prefs\\child",
                "..\\..\\..\\..\\..\\Something\\..\\..\\Something else\\\\.\\leaf, HKEY_CURRENT_USER\\Something else\\leaf"
        })
        @DisplayName("resolve")
        void testResolve(String relativePath, String expectedPath) {
            RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER.resolve("Software\\JavaSoft\\Prefs");
            RegistryKey resolved = registryKey.resolve(relativePath != null ? relativePath : "");
            assertEquals(expectedPath, resolved.path());
        }
    }

    @Nested
    @DisplayName("from subKeys")
    class FromSubKeys {

        @Test
        @DisplayName("name")
        void testName() {
            RegistryKey registryKey = testKey();
            assertEquals("Prefs", registryKey.name());
        }

        @Test
        @DisplayName("path")
        void testPathKeys() {
            RegistryKey registryKey = testKey();
            assertEquals("HKEY_CURRENT_USER\\Software\\JavaSoft\\Prefs", registryKey.path());
        }

        @Test
        @DisplayName("isRoot")
        void testIsRoot() {
            RegistryKey registryKey = testKey();
            assertFalse(registryKey.isRoot());
        }

        @Test
        @DisplayName("root")
        void testRoot() {
            RegistryKey registryKey = testKey();
            assertSame(RegistryKey.HKEY_CURRENT_USER, registryKey.root());
        }

        @Test
        @DisplayName("parent")
        void testParent() {
            RegistryKey registryKey = testKey();

            Optional<RegistryKey> parent = registryKey.parent();
            assertEquals(Optional.of("HKEY_CURRENT_USER\\Software\\JavaSoft"), parent.map(RegistryKey::path));

            parent = parent.flatMap(RegistryKey::parent);
            assertEquals(Optional.of("HKEY_CURRENT_USER\\Software"), parent.map(RegistryKey::path));

            parent = parent.flatMap(RegistryKey::parent);
            assertEquals(Optional.of(RegistryKey.HKEY_CURRENT_USER), parent);
        }

        @ParameterizedTest(name = "{0} => {1}")
        @CsvSource({
                ", HKEY_CURRENT_USER\\Software\\JavaSoft\\Prefs",
                "., HKEY_CURRENT_USER\\Software\\JavaSoft\\Prefs",
                ".., HKEY_CURRENT_USER\\Software\\JavaSoft",
                "..\\.., HKEY_CURRENT_USER\\Software",
                "..\\..\\.., HKEY_CURRENT_USER",
                "..\\..\\..\\.., HKEY_CURRENT_USER",
                "child, HKEY_CURRENT_USER\\Software\\JavaSoft\\Prefs\\child",
                "..\\..\\..\\..\\..\\Something\\..\\..\\Something else\\\\.\\leaf, HKEY_CURRENT_USER\\Something else\\leaf"
        })
        @DisplayName("resolve")
        void testResolve(String relativePath, String expectedPath) {
            RegistryKey registryKey = testKey();
            RegistryKey resolved = registryKey.resolve(relativePath != null ? relativePath : "");
            assertEquals(expectedPath, resolved.path());
        }

        private RegistryKey testKey() {
            mockSubKeys("Prefs");

            try (Stream<RegistryKey> stream = RegistryKey.HKEY_CURRENT_USER.resolve("Software\\JavaSoft").subKeys()) {
                return stream.findFirst()
                        .orElseThrow();
            }
        }
    }

    private void mockSubKeys(String... names) {
        int maxLength = Arrays.stream(names)
                .mapToInt(String::length)
                .max()
                .orElseThrow();

        when(RegistryKey.api.RegOpenKeyEx(any(), any(), anyInt(), anyInt(), any())).thenReturn(WinError.ERROR_SUCCESS);
        when(RegistryKey.api.RegCloseKey(any())).thenReturn(WinError.ERROR_SUCCESS);

        when(RegistryKey.api.RegQueryInfoKey(any(), any(), any(), any(), any(), notNull(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(i -> {
                    i.getArgument(5, IntByReference.class).setValue(maxLength);
                    return WinError.ERROR_SUCCESS;
                });
        when(RegistryKey.api.RegEnumKeyEx(any(), anyInt(), any(), any(), any(), any(), any(), any())).thenAnswer(i -> {
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

    private void mockValues(RegistryValue... values) {
        int maxNameLength = Arrays.stream(values)
                .map(RegistryValue::name)
                .mapToInt(String::length)
                .max()
                .orElseThrow();
        byte[][] datas = Arrays.stream(values)
                .map(RegistryValue::rawData)
                .toArray(byte[][]::new);
        int maxValueLength = Arrays.stream(datas)
                .mapToInt(d -> d.length)
                .max()
                .orElseThrow();

        when(RegistryKey.api.RegOpenKeyEx(any(), any(), anyInt(), anyInt(), any())).thenReturn(WinError.ERROR_SUCCESS);
        when(RegistryKey.api.RegCloseKey(any())).thenReturn(WinError.ERROR_SUCCESS);

        when(RegistryKey.api.RegQueryInfoKey(any(), any(), any(), any(), any(), any(), any(), any(), notNull(), notNull(), any(), any()))
                .thenAnswer(i -> {
                    i.getArgument(8, IntByReference.class).setValue(maxNameLength);
                    i.getArgument(9, IntByReference.class).setValue(maxValueLength);
                    return WinError.ERROR_SUCCESS;
                });
        when(RegistryKey.api.RegEnumValue(any(), anyInt(), any(), any(), any(), any(), any(byte[].class), any())).thenAnswer(i -> {
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
}
