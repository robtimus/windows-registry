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

import static com.github.robtimus.os.windows.registry.RegistryValueTest.randomData;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayContaining;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import com.sun.jna.platform.win32.Advapi32;
import com.sun.jna.platform.win32.WinError;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.platform.win32.WinReg;
import com.sun.jna.platform.win32.WinReg.HKEY;
import com.sun.jna.platform.win32.WinReg.HKEYByReference;
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
            HKEY hKey = mockOpenAndClose(WinReg.HKEY_CURRENT_USER, "Software\\JavaSoft");

            mockSubKeys(hKey, "Prefs");

            try (Stream<RegistryKey> stream = RegistryKey.HKEY_CURRENT_USER.resolve("Software\\JavaSoft").subKeys()) {
                return stream.findFirst()
                        .orElseThrow();
            }
        }
    }

    @Nested
    @DisplayName("subKeys")
    class SubKeys {

        @Test
        @DisplayName("success")
        void testSuccess() {
            HKEY hKey = mockOpenAndClose(WinReg.HKEY_CURRENT_USER, "Software\\JavaSoft\\Prefs");

            mockSubKeys(hKey, "child1", "child2", "child3");

            RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER.resolve("Software\\JavaSoft\\Prefs");
            try (Stream<RegistryKey> stream = registryKey.subKeys()) {
                List<RegistryKey> subKeys = stream.collect(Collectors.toList());

                List<RegistryKey> expected = Arrays.asList(
                        registryKey.resolve("child1"),
                        registryKey.resolve("child2"),
                        registryKey.resolve("child3")
                );

                assertEquals(expected, subKeys);
            }

            verify(RegistryKey.api).RegOpenKeyEx(eq(WinReg.HKEY_CURRENT_USER), eq("Software\\JavaSoft\\Prefs"), anyInt(), anyInt(), any());
            verify(RegistryKey.api).RegCloseKey(hKey);
        }

        @Test
        @DisplayName("non-existing key")
        void testNonExistingKey() {
            when(RegistryKey.api.RegOpenKeyEx(eq(WinReg.HKEY_CURRENT_USER), eq("path\\non-existing"), anyInt(), anyInt(), any()))
                    .thenReturn(WinError.ERROR_FILE_NOT_FOUND);

            RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER.resolve("path\\non-existing");
            NoSuchRegistryKeyException exception = assertThrows(NoSuchRegistryKeyException.class, registryKey::subKeys);
            assertEquals("HKEY_CURRENT_USER\\path\\non-existing", exception.path());

            verify(RegistryKey.api).RegOpenKeyEx(eq(WinReg.HKEY_CURRENT_USER), eq("path\\non-existing"), anyInt(), anyInt(), any());
            verify(RegistryKey.api, never()).RegCloseKey(any());
        }

        @Nested
        @DisplayName("query failure")
        class QueryFailure {

            @Test
            @DisplayName("with successful close")
            void testSuccessfulClose() {
                HKEY hKey = mockOpenAndClose(WinReg.HKEY_CURRENT_USER, "path\\failure");

                when(RegistryKey.api.RegQueryInfoKey(eq(hKey), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                        .thenReturn(WinError.ERROR_FILE_NOT_FOUND);

                RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER.resolve("path\\failure");
                NoSuchRegistryKeyException exception = assertThrows(NoSuchRegistryKeyException.class, registryKey::subKeys);
                assertEquals("HKEY_CURRENT_USER\\path\\failure", exception.path());

                verify(RegistryKey.api).RegOpenKeyEx(eq(WinReg.HKEY_CURRENT_USER), eq("path\\failure"), anyInt(), anyInt(), any());
                verify(RegistryKey.api).RegCloseKey(hKey);
            }

            @Test
            @DisplayName("with close failure")
            void testCloseFailure() {
                HKEY hKey = mockOpen(WinReg.HKEY_CURRENT_USER, "path\\failure");

                mockClose(hKey, WinError.ERROR_INVALID_HANDLE);

                when(RegistryKey.api.RegQueryInfoKey(eq(hKey), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                        .thenReturn(WinError.ERROR_FILE_NOT_FOUND);

                RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER.resolve("path\\failure");
                NoSuchRegistryKeyException exception = assertThrows(NoSuchRegistryKeyException.class, registryKey::subKeys);
                assertEquals("HKEY_CURRENT_USER\\path\\failure", exception.path());
                assertThat(exception.getSuppressed(), arrayContaining(instanceOf(InvalidRegistryHandleException.class)));

                verify(RegistryKey.api).RegOpenKeyEx(eq(WinReg.HKEY_CURRENT_USER), eq("path\\failure"), anyInt(), anyInt(), any());
                verify(RegistryKey.api).RegCloseKey(hKey);
            }
        }

        @Test
        @DisplayName("enum failure")
        void testEnumFailure() {
            HKEY hKey = mockOpenAndClose(WinReg.HKEY_CURRENT_USER, "path\\failure");

            when(RegistryKey.api.RegQueryInfoKey(eq(hKey), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(WinError.ERROR_SUCCESS);

            when(RegistryKey.api.RegEnumKeyEx(eq(hKey), eq(0), any(), any(), any(), any(), any(), any()))
                    .thenReturn(WinError.ERROR_FILE_NOT_FOUND);

            RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER.resolve("path\\failure");
            try (Stream<RegistryKey> stream = registryKey.subKeys()) {
                Collector<RegistryKey, ?, ?> collector = Collectors.toList();
                NoSuchRegistryKeyException exception = assertThrows(NoSuchRegistryKeyException.class, () -> stream.collect(collector));
                assertEquals("HKEY_CURRENT_USER\\path\\failure", exception.path());
            }

            verify(RegistryKey.api).RegOpenKeyEx(eq(WinReg.HKEY_CURRENT_USER), eq("path\\failure"), anyInt(), anyInt(), any());
            verify(RegistryKey.api).RegCloseKey(hKey);
        }
    }

    @Nested
    @DisplayName("values")
    class Values {

        @Nested
        @DisplayName("success")
        class Success {

            @Test
            @DisplayName("without filter")
            void testWithoutFilter() {
                StringRegistryValue stringValue = new StringRegistryValue("string", "value");
                BinaryRegistryValue binaryValue = new BinaryRegistryValue("binary", randomData());
                DWordRegistryValue wordValue = new DWordRegistryValue("dword", 13);

                HKEY hKey = mockOpenAndClose(WinReg.HKEY_CURRENT_USER, "Software\\JavaSoft\\Prefs");

                mockValues(hKey, stringValue, binaryValue, wordValue);

                RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER.resolve("Software\\JavaSoft\\Prefs");
                try (Stream<RegistryValue> stream = registryKey.values()) {
                    List<RegistryValue> values = stream.collect(Collectors.toList());

                    List<RegistryValue> expected = Arrays.asList(stringValue, binaryValue, wordValue);

                    assertEquals(expected, values);
                }

                verify(RegistryKey.api).RegOpenKeyEx(eq(WinReg.HKEY_CURRENT_USER), eq("Software\\JavaSoft\\Prefs"), anyInt(), anyInt(), any());
                verify(RegistryKey.api).RegCloseKey(hKey);
            }

            @Test
            @DisplayName("with name filter")
            void testWithNameFilter() {
                StringRegistryValue stringValue = new StringRegistryValue("string", "value");
                BinaryRegistryValue binaryValue = new BinaryRegistryValue("binary", randomData());
                DWordRegistryValue wordValue = new DWordRegistryValue("dword", 13);

                HKEY hKey = mockOpenAndClose(WinReg.HKEY_CURRENT_USER, "Software\\JavaSoft\\Prefs");

                mockValues(hKey, stringValue, binaryValue, wordValue);

                RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER.resolve("Software\\JavaSoft\\Prefs");
                RegistryValue.Filter filter = RegistryValue.filter().name(s -> s.contains("i"));
                try (Stream<RegistryValue> stream = registryKey.values(filter)) {
                    List<RegistryValue> values = stream.collect(Collectors.toList());

                    List<RegistryValue> expected = Arrays.asList(stringValue, binaryValue);

                    assertEquals(expected, values);
                }

                verify(RegistryKey.api).RegOpenKeyEx(eq(WinReg.HKEY_CURRENT_USER), eq("Software\\JavaSoft\\Prefs"), anyInt(), anyInt(), any());
                verify(RegistryKey.api).RegCloseKey(hKey);
            }

            @Test
            @DisplayName("with type filter")
            void testWithTypeFilter() {
                StringRegistryValue stringValue = new StringRegistryValue("string", "value");
                BinaryRegistryValue binaryValue = new BinaryRegistryValue("binary", randomData());
                DWordRegistryValue wordValue = new DWordRegistryValue("dword", 13);

                HKEY hKey = mockOpenAndClose(WinReg.HKEY_CURRENT_USER, "Software\\JavaSoft\\Prefs");

                mockValues(hKey, stringValue, binaryValue, wordValue);

                RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER.resolve("Software\\JavaSoft\\Prefs");
                RegistryValue.Filter filter = RegistryValue.filter().strings().words();
                try (Stream<RegistryValue> stream = registryKey.values(filter)) {
                    List<RegistryValue> values = stream.collect(Collectors.toList());

                    List<RegistryValue> expected = Arrays.asList(stringValue, wordValue);

                    assertEquals(expected, values);
                }

                verify(RegistryKey.api).RegOpenKeyEx(eq(WinReg.HKEY_CURRENT_USER), eq("Software\\JavaSoft\\Prefs"), anyInt(), anyInt(), any());
                verify(RegistryKey.api).RegCloseKey(hKey);
            }
        }

        @Test
        @DisplayName("non-existing key")
        void testNonExistingKey() {
            when(RegistryKey.api.RegOpenKeyEx(eq(WinReg.HKEY_CURRENT_USER), eq("path\\non-existing"), anyInt(), anyInt(), any()))
                    .thenReturn(WinError.ERROR_FILE_NOT_FOUND);

            RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER.resolve("path\\non-existing");
            NoSuchRegistryKeyException exception = assertThrows(NoSuchRegistryKeyException.class, registryKey::values);
            assertEquals("HKEY_CURRENT_USER\\path\\non-existing", exception.path());

            verify(RegistryKey.api).RegOpenKeyEx(eq(WinReg.HKEY_CURRENT_USER), eq("path\\non-existing"), anyInt(), anyInt(), any());
            verify(RegistryKey.api, never()).RegCloseKey(any());
        }

        @Nested
        @DisplayName("query failure")
        class QueryFailure {

            @Test
            @DisplayName("with successful close")
            void testSuccessfulClose() {
                HKEY hKey = mockOpenAndClose(WinReg.HKEY_CURRENT_USER, "path\\failure");

                when(RegistryKey.api.RegQueryInfoKey(eq(hKey), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                        .thenReturn(WinError.ERROR_FILE_NOT_FOUND);

                RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER.resolve("path\\failure");
                NoSuchRegistryKeyException exception = assertThrows(NoSuchRegistryKeyException.class, registryKey::values);
                assertEquals("HKEY_CURRENT_USER\\path\\failure", exception.path());

                verify(RegistryKey.api).RegOpenKeyEx(eq(WinReg.HKEY_CURRENT_USER), eq("path\\failure"), anyInt(), anyInt(), any());
                verify(RegistryKey.api).RegCloseKey(hKey);
            }

            @Test
            @DisplayName("with close failure")
            void testCloseFailure() {
                HKEY hKey = mockOpen(WinReg.HKEY_CURRENT_USER, "path\\failure");

                mockClose(hKey, WinError.ERROR_INVALID_HANDLE);

                when(RegistryKey.api.RegQueryInfoKey(eq(hKey), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                        .thenReturn(WinError.ERROR_FILE_NOT_FOUND);

                RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER.resolve("path\\failure");
                NoSuchRegistryKeyException exception = assertThrows(NoSuchRegistryKeyException.class, registryKey::values);
                assertEquals("HKEY_CURRENT_USER\\path\\failure", exception.path());
                assertThat(exception.getSuppressed(), arrayContaining(instanceOf(InvalidRegistryHandleException.class)));

                verify(RegistryKey.api).RegOpenKeyEx(eq(WinReg.HKEY_CURRENT_USER), eq("path\\failure"), anyInt(), anyInt(), any());
                verify(RegistryKey.api).RegCloseKey(hKey);
            }
        }

        @Test
        @DisplayName("enum failure")
        void testEnumFailure() {
            HKEY hKey = mockOpenAndClose(WinReg.HKEY_CURRENT_USER, "path\\failure");

            when(RegistryKey.api.RegQueryInfoKey(eq(hKey), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(WinError.ERROR_SUCCESS);

            when(RegistryKey.api.RegEnumValue(eq(hKey), eq(0), any(), any(), any(), any(), any(byte[].class), any()))
                    .thenReturn(WinError.ERROR_FILE_NOT_FOUND);

            RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER.resolve("path\\failure");
            try (Stream<RegistryValue> stream = registryKey.values()) {
                Collector<RegistryValue, ?, ?> collector = Collectors.toList();
                NoSuchRegistryKeyException exception = assertThrows(NoSuchRegistryKeyException.class, () -> stream.collect(collector));
                assertEquals("HKEY_CURRENT_USER\\path\\failure", exception.path());
            }

            verify(RegistryKey.api).RegOpenKeyEx(eq(WinReg.HKEY_CURRENT_USER), eq("path\\failure"), anyInt(), anyInt(), any());
            verify(RegistryKey.api).RegCloseKey(hKey);
        }
    }

    @Nested
    @DisplayName("getValue")
    class GetValue {

        @Test
        @DisplayName("success")
        void testSuccess() {
            StringRegistryValue stringValue = new StringRegistryValue("string", "value");

            HKEY hKey = mockOpenAndClose(WinReg.HKEY_CURRENT_USER, "Software\\JavaSoft\\Prefs");

            mockValue(hKey, stringValue);

            RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER.resolve("Software\\JavaSoft\\Prefs");
            Optional<RegistryValue> value = registryKey.getValue("string");
            assertEquals(Optional.of(stringValue), value);

            verify(RegistryKey.api).RegOpenKeyEx(eq(WinReg.HKEY_CURRENT_USER), eq("Software\\JavaSoft\\Prefs"), anyInt(), anyInt(), any());
            verify(RegistryKey.api).RegCloseKey(hKey);
        }

        @Test
        @DisplayName("non-existing key")
        void testNonExistingKey() {
            when(RegistryKey.api.RegOpenKeyEx(eq(WinReg.HKEY_CURRENT_USER), eq("path\\non-existing"), anyInt(), anyInt(), any()))
                    .thenReturn(WinError.ERROR_FILE_NOT_FOUND);

            RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER.resolve("path\\non-existing");
            NoSuchRegistryKeyException exception = assertThrows(NoSuchRegistryKeyException.class, () -> registryKey.getValue("string"));
            assertEquals("HKEY_CURRENT_USER\\path\\non-existing", exception.path());

            verify(RegistryKey.api).RegOpenKeyEx(eq(WinReg.HKEY_CURRENT_USER), eq("path\\non-existing"), anyInt(), anyInt(), any());
            verify(RegistryKey.api, never()).RegCloseKey(any());
        }

        @Test
        @DisplayName("non-existing value")
        void testNonExistingValue() {
            HKEY hKey = mockOpenAndClose(WinReg.HKEY_CURRENT_USER, "path\\non-existing");

            when(RegistryKey.api.RegQueryValueEx(eq(hKey), eq("string"), anyInt(), any(), (byte[]) isNull(), any()))
                    .thenReturn(WinError.ERROR_FILE_NOT_FOUND);

            RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER.resolve("path\\non-existing");
            Optional<RegistryValue> value = registryKey.getValue("string");
            assertEquals(Optional.empty(), value);

            verify(RegistryKey.api).RegOpenKeyEx(eq(WinReg.HKEY_CURRENT_USER), eq("path\\non-existing"), anyInt(), anyInt(), any());
            verify(RegistryKey.api).RegCloseKey(hKey);
        }

        @Test
        @DisplayName("failure")
        void testFailure() {
            HKEY hKey = mockOpenAndClose(WinReg.HKEY_CURRENT_USER, "path\\failure");

            mockValue(hKey, new StringRegistryValue("string", "value"), WinError.ERROR_INVALID_HANDLE);

            RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER.resolve("path\\failure");
            InvalidRegistryHandleException exception = assertThrows(InvalidRegistryHandleException.class, () -> registryKey.getValue("string"));
            assertEquals("HKEY_CURRENT_USER\\path\\failure", exception.path());

            verify(RegistryKey.api).RegOpenKeyEx(eq(WinReg.HKEY_CURRENT_USER), eq("path\\failure"), anyInt(), anyInt(), any());
            verify(RegistryKey.api).RegCloseKey(hKey);
        }
    }

    @Nested
    @DisplayName("setValue")
    class SetValue {

        @Test
        @DisplayName("success")
        void testSuccess() {
            StringRegistryValue stringValue = new StringRegistryValue("string", "value");
            byte[] data = stringValue.rawData();

            HKEY hKey = mockOpenAndClose(WinReg.HKEY_CURRENT_USER, "Software\\JavaSoft\\Prefs");

            when(RegistryKey.api.RegSetValueEx(eq(WinReg.HKEY_CURRENT_USER), eq("string"), anyInt(), eq(WinNT.REG_SZ),
                    any(byte[].class), eq(data.length)))
                    .thenReturn(WinError.ERROR_SUCCESS);

            RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER.resolve("Software\\JavaSoft\\Prefs");
            registryKey.setValue(stringValue);

            ArgumentCaptor<byte[]> dataCaptor = ArgumentCaptor.forClass(byte[].class);
            verify(RegistryKey.api).RegSetValueEx(eq(hKey), eq("string"), anyInt(), eq(WinNT.REG_SZ), dataCaptor.capture(), eq(data.length));
            assertArrayEquals(data, dataCaptor.getValue());

            verify(RegistryKey.api).RegOpenKeyEx(eq(WinReg.HKEY_CURRENT_USER), eq("Software\\JavaSoft\\Prefs"), anyInt(), anyInt(), any());
            verify(RegistryKey.api).RegCloseKey(hKey);
        }

        @Test
        @DisplayName("non-existing key")
        void testNonExistingKey() {
            StringRegistryValue stringValue = new StringRegistryValue("string", "value");

            when(RegistryKey.api.RegOpenKeyEx(eq(WinReg.HKEY_CURRENT_USER), eq("path\\non-existing"), anyInt(), anyInt(), any()))
                    .thenReturn(WinError.ERROR_FILE_NOT_FOUND);

            RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER.resolve("path\\non-existing");
            NoSuchRegistryKeyException exception = assertThrows(NoSuchRegistryKeyException.class, () -> registryKey.setValue(stringValue));
            assertEquals("HKEY_CURRENT_USER\\path\\non-existing", exception.path());

            verify(RegistryKey.api).RegOpenKeyEx(eq(WinReg.HKEY_CURRENT_USER), eq("path\\non-existing"), anyInt(), anyInt(), any());
            verify(RegistryKey.api, never()).RegCloseKey(any());
            verify(RegistryKey.api, never()).RegSetValueEx(any(), any(), anyInt(), anyInt(), any(byte[].class), anyInt());
        }

        @Test
        @DisplayName("failure")
        void testFailure() {
            StringRegistryValue stringValue = new StringRegistryValue("string", "value");

            HKEY hKey = mockOpenAndClose(WinReg.HKEY_CURRENT_USER, "path\\failure");

            when(RegistryKey.api.RegSetValueEx(eq(hKey), eq("string"), anyInt(), eq(WinNT.REG_SZ), any(byte[].class), anyInt()))
                    .thenReturn(WinError.ERROR_INVALID_HANDLE);

            RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER.resolve("path\\failure");
            InvalidRegistryHandleException exception = assertThrows(InvalidRegistryHandleException.class, () -> registryKey.setValue(stringValue));
            assertEquals("HKEY_CURRENT_USER\\path\\failure", exception.path());

            verify(RegistryKey.api).RegOpenKeyEx(eq(WinReg.HKEY_CURRENT_USER), eq("path\\failure"), anyInt(), anyInt(), any());
            verify(RegistryKey.api).RegCloseKey(hKey);
        }
    }

    @Nested
    @DisplayName("deleteValue")
    class DeleteValue {

        @Test
        @DisplayName("success")
        void testSuccess() {
            HKEY hKey = mockOpenAndClose(WinReg.HKEY_CURRENT_USER, "Software\\JavaSoft\\Prefs");

            when(RegistryKey.api.RegDeleteValue(hKey, "string")).thenReturn(WinError.ERROR_SUCCESS);

            RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER.resolve("Software\\JavaSoft\\Prefs");
            registryKey.deleteValue("string");

            verify(RegistryKey.api).RegDeleteValue(hKey, "string");

            verify(RegistryKey.api).RegOpenKeyEx(eq(WinReg.HKEY_CURRENT_USER), eq("Software\\JavaSoft\\Prefs"), anyInt(), anyInt(), any());
            verify(RegistryKey.api).RegCloseKey(hKey);
        }

        @Test
        @DisplayName("non-existing key")
        void testNonExistingKey() {
            when(RegistryKey.api.RegOpenKeyEx(eq(WinReg.HKEY_CURRENT_USER), eq("path\\non-existing"), anyInt(), anyInt(), any()))
                    .thenReturn(WinError.ERROR_FILE_NOT_FOUND);

            RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER.resolve("path\\non-existing");
            NoSuchRegistryKeyException exception = assertThrows(NoSuchRegistryKeyException.class, () -> registryKey.deleteValue("string"));
            assertEquals("HKEY_CURRENT_USER\\path\\non-existing", exception.path());

            verify(RegistryKey.api).RegOpenKeyEx(eq(WinReg.HKEY_CURRENT_USER), eq("path\\non-existing"), anyInt(), anyInt(), any());
            verify(RegistryKey.api, never()).RegCloseKey(any());
            verify(RegistryKey.api, never()).RegDeleteValue(any(), any());
        }

        @Test
        @DisplayName("failure")
        void testFailure() {
            HKEY hKey = mockOpenAndClose(WinReg.HKEY_CURRENT_USER, "path\\failure");

            when(RegistryKey.api.RegDeleteValue(hKey, "string")).thenReturn(WinError.ERROR_FILE_NOT_FOUND);

            RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER.resolve("path\\failure");
            NoSuchRegistryValueException exception = assertThrows(NoSuchRegistryValueException.class, () -> registryKey.deleteValue("string"));
            assertEquals("HKEY_CURRENT_USER\\path\\failure", exception.path());
            assertEquals("string", exception.name());

            verify(RegistryKey.api).RegOpenKeyEx(eq(WinReg.HKEY_CURRENT_USER), eq("path\\failure"), anyInt(), anyInt(), any());
            verify(RegistryKey.api).RegCloseKey(hKey);
        }
    }

    @Nested
    @DisplayName("deleteValueIfExists")
    class DeleteValueIfExists {

        @Nested
        @DisplayName("success")
        class Success {

            @Test
            @DisplayName("value existed")
            void testExisted() {
                HKEY hKey = mockOpenAndClose(WinReg.HKEY_CURRENT_USER, "Software\\JavaSoft\\Prefs");

                when(RegistryKey.api.RegDeleteValue(hKey, "string")).thenReturn(WinError.ERROR_SUCCESS);

                RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER.resolve("Software\\JavaSoft\\Prefs");
                assertTrue(registryKey.deleteValueIfExists("string"));

                verify(RegistryKey.api).RegDeleteValue(hKey, "string");

                verify(RegistryKey.api).RegOpenKeyEx(eq(WinReg.HKEY_CURRENT_USER), eq("Software\\JavaSoft\\Prefs"), anyInt(), anyInt(), any());
                verify(RegistryKey.api).RegCloseKey(hKey);
            }

            @Test
            @DisplayName("value didn't exist")
            void testValueDidntExist() {
                HKEY hKey = mockOpenAndClose(WinReg.HKEY_CURRENT_USER, "Software\\JavaSoft\\Prefs");

                when(RegistryKey.api.RegDeleteValue(hKey, "string")).thenReturn(WinError.ERROR_FILE_NOT_FOUND);

                RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER.resolve("Software\\JavaSoft\\Prefs");
                assertFalse(registryKey.deleteValueIfExists("string"));

                verify(RegistryKey.api).RegDeleteValue(hKey, "string");

                verify(RegistryKey.api).RegOpenKeyEx(eq(WinReg.HKEY_CURRENT_USER), eq("Software\\JavaSoft\\Prefs"), anyInt(), anyInt(), any());
                verify(RegistryKey.api).RegCloseKey(hKey);
            }
        }

        @Test
        @DisplayName("non-existing key")
        void testNonExistingKey() {
            when(RegistryKey.api.RegOpenKeyEx(eq(WinReg.HKEY_CURRENT_USER), eq("path\\non-existing"), anyInt(), anyInt(), any()))
                    .thenReturn(WinError.ERROR_FILE_NOT_FOUND);

            RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER.resolve("path\\non-existing");
            NoSuchRegistryKeyException exception = assertThrows(NoSuchRegistryKeyException.class, () -> registryKey.deleteValueIfExists("string"));
            assertEquals("HKEY_CURRENT_USER\\path\\non-existing", exception.path());

            verify(RegistryKey.api).RegOpenKeyEx(eq(WinReg.HKEY_CURRENT_USER), eq("path\\non-existing"), anyInt(), anyInt(), any());
            verify(RegistryKey.api, never()).RegCloseKey(any());
            verify(RegistryKey.api, never()).RegDeleteValue(any(), any());
        }

        @Test
        @DisplayName("failure")
        void testFailure() {
            HKEY hKey = mockOpenAndClose(WinReg.HKEY_CURRENT_USER, "path\\failure");

            when(RegistryKey.api.RegDeleteValue(hKey, "string")).thenReturn(WinError.ERROR_INVALID_HANDLE);

            RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER.resolve("path\\failure");
            InvalidRegistryHandleException exception = assertThrows(InvalidRegistryHandleException.class,
                    () -> registryKey.deleteValueIfExists("string"));
            assertEquals("HKEY_CURRENT_USER\\path\\failure", exception.path());

            verify(RegistryKey.api).RegOpenKeyEx(eq(WinReg.HKEY_CURRENT_USER), eq("path\\failure"), anyInt(), anyInt(), any());
            verify(RegistryKey.api).RegCloseKey(hKey);
        }
    }

    @Nested
    @DisplayName("exists")
    class Exists {

        @Test
        @DisplayName("existing")
        void testExisting() {
            HKEY hKey = mockOpenAndClose(WinReg.HKEY_CURRENT_USER, "path\\existing");

            RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER.resolve("path\\existing");
            assertTrue(registryKey.exists());

            verify(RegistryKey.api).RegOpenKeyEx(eq(WinReg.HKEY_CURRENT_USER), eq("path\\existing"), anyInt(), anyInt(), any());
            verify(RegistryKey.api).RegCloseKey(hKey);
        }

        @Test
        @DisplayName("non-existing")
        void testNonExisting() {
            when(RegistryKey.api.RegOpenKeyEx(eq(WinReg.HKEY_CURRENT_USER), eq("path\\non-existing"), anyInt(), anyInt(), any()))
                    .thenReturn(WinError.ERROR_FILE_NOT_FOUND);

            RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER.resolve("path\\non-existing");
            assertFalse(registryKey.exists());

            verify(RegistryKey.api).RegOpenKeyEx(eq(WinReg.HKEY_CURRENT_USER), eq("path\\non-existing"), anyInt(), anyInt(), any());
            verify(RegistryKey.api, never()).RegCloseKey(any());
        }

        @Test
        @DisplayName("failure")
        void testFailure() {
            when(RegistryKey.api.RegOpenKeyEx(eq(WinReg.HKEY_CURRENT_USER), eq("path\\failure"), anyInt(), anyInt(), any()))
                    .thenReturn(WinError.ERROR_INVALID_HANDLE);

            RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER.resolve("path\\failure");
            InvalidRegistryHandleException exception = assertThrows(InvalidRegistryHandleException.class, registryKey::exists);
            assertEquals("HKEY_CURRENT_USER\\path\\failure", exception.path());

            verify(RegistryKey.api).RegOpenKeyEx(eq(WinReg.HKEY_CURRENT_USER), eq("path\\failure"), anyInt(), anyInt(), any());
            verify(RegistryKey.api, never()).RegCloseKey(any());
        }
    }

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("non-existing")
        void testCreateNonExisting() {
            HKEY hKey = newHKEY();

            when(RegistryKey.api.RegCreateKeyEx(eq(WinReg.HKEY_CURRENT_USER), eq("path\\new"),
                    anyInt(), any(), anyInt(), anyInt(), any(), any(), any()))
                    .thenAnswer(i -> {
                        i.getArgument(7, HKEYByReference.class).setValue(hKey);
                        i.getArgument(8, IntByReference.class).setValue(WinNT.REG_CREATED_NEW_KEY);

                        return WinError.ERROR_SUCCESS;
                    });

            RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER.resolve("path\\new");
            registryKey.create();

            verify(RegistryKey.api).RegCreateKeyEx(eq(WinReg.HKEY_CURRENT_USER), eq("path\\new"),
                    anyInt(), any(), anyInt(), anyInt(), any(), any(), any());
            verify(RegistryKey.api).RegCloseKey(hKey);
        }

        @Test
        @DisplayName("existing")
        void testCreateExisting() {
            HKEY hKey = newHKEY();

            when(RegistryKey.api.RegCreateKeyEx(eq(WinReg.HKEY_CURRENT_USER), eq("path\\existing"),
                    anyInt(), any(), anyInt(), anyInt(), any(), any(), any()))
                    .thenAnswer(i -> {
                        i.getArgument(7, HKEYByReference.class).setValue(hKey);
                        i.getArgument(8, IntByReference.class).setValue(WinNT.REG_OPENED_EXISTING_KEY);

                        return WinError.ERROR_SUCCESS;
                    });

            RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER.resolve("path\\existing");
            RegistryKeyAlreadyExistsException exception = assertThrows(RegistryKeyAlreadyExistsException.class, registryKey::create);
            assertEquals("HKEY_CURRENT_USER\\path\\existing", exception.path());

            verify(RegistryKey.api).RegCreateKeyEx(eq(WinReg.HKEY_CURRENT_USER), eq("path\\existing"),
                    anyInt(), any(), anyInt(), anyInt(), any(), any(), any());
            verify(RegistryKey.api).RegCloseKey(hKey);
        }

        @Test
        @DisplayName("failure")
        void testFailure() {
            when(RegistryKey.api.RegCreateKeyEx(eq(WinReg.HKEY_CURRENT_USER), eq("path\\failure"),
                    anyInt(), any(), anyInt(), anyInt(), any(), any(), any()))
                    .thenReturn(WinError.ERROR_INVALID_HANDLE);

            RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER.resolve("path\\failure");
            InvalidRegistryHandleException exception = assertThrows(InvalidRegistryHandleException.class, registryKey::create);
            assertEquals("HKEY_CURRENT_USER\\path\\failure", exception.path());

            verify(RegistryKey.api).RegCreateKeyEx(eq(WinReg.HKEY_CURRENT_USER), eq("path\\failure"),
                    anyInt(), any(), anyInt(), anyInt(), any(), any(), any());
            verify(RegistryKey.api, never()).RegCloseKey(any());
        }
    }

    @Nested
    @DisplayName("createIfNotExists")
    class CreateIfNotExists {

        @Test
        @DisplayName("non-existing")
        void testCreateNonExisting() {
            HKEY hKey = newHKEY();

            when(RegistryKey.api.RegCreateKeyEx(eq(WinReg.HKEY_CURRENT_USER), eq("path\\new"),
                    anyInt(), any(), anyInt(), anyInt(), any(), any(), any()))
                    .thenAnswer(i -> {
                        i.getArgument(7, HKEYByReference.class).setValue(hKey);
                        i.getArgument(8, IntByReference.class).setValue(WinNT.REG_CREATED_NEW_KEY);

                        return WinError.ERROR_SUCCESS;
                    });

            RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER.resolve("path\\new");
            assertTrue(registryKey.createIfNotExists());

            verify(RegistryKey.api).RegCreateKeyEx(eq(WinReg.HKEY_CURRENT_USER), eq("path\\new"),
                    anyInt(), any(), anyInt(), anyInt(), any(), any(), any());
            verify(RegistryKey.api).RegCloseKey(hKey);
        }

        @Test
        @DisplayName("existing")
        void testCreateExisting() {
            HKEY hKey = newHKEY();

            when(RegistryKey.api.RegCreateKeyEx(eq(WinReg.HKEY_CURRENT_USER), eq("path\\existing"),
                    anyInt(), any(), anyInt(), anyInt(), any(), any(), any()))
                    .thenAnswer(i -> {
                        i.getArgument(7, HKEYByReference.class).setValue(hKey);
                        i.getArgument(8, IntByReference.class).setValue(WinNT.REG_OPENED_EXISTING_KEY);

                        return WinError.ERROR_SUCCESS;
                    });

            RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER.resolve("path\\existing");
            assertFalse(registryKey.createIfNotExists());

            verify(RegistryKey.api).RegCreateKeyEx(eq(WinReg.HKEY_CURRENT_USER), eq("path\\existing"),
                    anyInt(), any(), anyInt(), anyInt(), any(), any(), any());
            verify(RegistryKey.api).RegCloseKey(hKey);
        }

        @Test
        @DisplayName("failure")
        void testFailure() {
            when(RegistryKey.api.RegCreateKeyEx(eq(WinReg.HKEY_CURRENT_USER), eq("path\\failure"),
                    anyInt(), any(), anyInt(), anyInt(), any(), any(), any()))
                    .thenReturn(WinError.ERROR_INVALID_HANDLE);

            RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER.resolve("path\\failure");
            InvalidRegistryHandleException exception = assertThrows(InvalidRegistryHandleException.class, registryKey::createIfNotExists);
            assertEquals("HKEY_CURRENT_USER\\path\\failure", exception.path());

            verify(RegistryKey.api).RegCreateKeyEx(eq(WinReg.HKEY_CURRENT_USER), eq("path\\failure"),
                    anyInt(), any(), anyInt(), anyInt(), any(), any(), any());
            verify(RegistryKey.api, never()).RegCloseKey(any());
        }
    }

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("existing")
        void testDeleteExisting() {
            when(RegistryKey.api.RegDeleteKey(WinReg.HKEY_CURRENT_USER, "path\\existing")).thenReturn(WinError.ERROR_SUCCESS);

            RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER.resolve("path\\existing");
            registryKey.delete();

            verify(RegistryKey.api).RegDeleteKey(WinReg.HKEY_CURRENT_USER, "path\\existing");
            verify(RegistryKey.api, never()).RegOpenKeyEx(any(), any(), anyInt(), anyInt(), any());
            verify(RegistryKey.api, never()).RegCloseKey(any());
        }

        @Test
        @DisplayName("non-existing")
        void testDeleteNonExisting() {
            when(RegistryKey.api.RegDeleteKey(WinReg.HKEY_CURRENT_USER, "path\\non-existing")).thenReturn(WinError.ERROR_FILE_NOT_FOUND);

            RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER.resolve("path\\non-existing");
            NoSuchRegistryKeyException exception = assertThrows(NoSuchRegistryKeyException.class, registryKey::delete);
            assertEquals("HKEY_CURRENT_USER\\path\\non-existing", exception.path());

            verify(RegistryKey.api, never()).RegOpenKeyEx(any(), any(), anyInt(), anyInt(), any());
            verify(RegistryKey.api, never()).RegCloseKey(any());
        }

        @Test
        @DisplayName("failure")
        void testFailure() {
            when(RegistryKey.api.RegDeleteKey(WinReg.HKEY_CURRENT_USER, "path\\failure")).thenReturn(WinError.ERROR_INVALID_HANDLE);

            RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER.resolve("path\\failure");
            InvalidRegistryHandleException exception = assertThrows(InvalidRegistryHandleException.class, registryKey::delete);
            assertEquals("HKEY_CURRENT_USER\\path\\failure", exception.path());

            verify(RegistryKey.api, never()).RegOpenKeyEx(any(), any(), anyInt(), anyInt(), any());
            verify(RegistryKey.api, never()).RegCloseKey(any());
        }
    }

    @Nested
    @DisplayName("deleteIfExists")
    class DeleteIfExists {

        @Test
        @DisplayName("existing")
        void testDeleteExisting() {
            when(RegistryKey.api.RegDeleteKey(WinReg.HKEY_CURRENT_USER, "path\\existing")).thenReturn(WinError.ERROR_SUCCESS);

            RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER.resolve("path\\existing");
            assertTrue(registryKey.deleteIfExists());

            verify(RegistryKey.api).RegDeleteKey(WinReg.HKEY_CURRENT_USER, "path\\existing");
            verify(RegistryKey.api, never()).RegOpenKeyEx(any(), any(), anyInt(), anyInt(), any());
            verify(RegistryKey.api, never()).RegCloseKey(any());
        }

        @Test
        @DisplayName("non-existing")
        void testDeleteNonExisting() {
            when(RegistryKey.api.RegDeleteKey(WinReg.HKEY_CURRENT_USER, "path\\non-existing")).thenReturn(WinError.ERROR_FILE_NOT_FOUND);

            RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER.resolve("path\\non-existing");
            assertFalse(registryKey.deleteIfExists());

            verify(RegistryKey.api).RegDeleteKey(WinReg.HKEY_CURRENT_USER, "path\\non-existing");
            verify(RegistryKey.api, never()).RegOpenKeyEx(any(), any(), anyInt(), anyInt(), any());
            verify(RegistryKey.api, never()).RegCloseKey(any());
        }

        @Test
        @DisplayName("failure")
        void testFailure() {
            when(RegistryKey.api.RegDeleteKey(WinReg.HKEY_CURRENT_USER, "path\\failure")).thenReturn(WinError.ERROR_INVALID_HANDLE);

            RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER.resolve("path\\failure");
            InvalidRegistryHandleException exception = assertThrows(InvalidRegistryHandleException.class, registryKey::deleteIfExists);
            assertEquals("HKEY_CURRENT_USER\\path\\failure", exception.path());

            verify(RegistryKey.api, never()).RegOpenKeyEx(any(), any(), anyInt(), anyInt(), any());
            verify(RegistryKey.api, never()).RegCloseKey(any());
        }
    }

    @Nested
    @DisplayName("handle")
    class Handle {

        @Test
        @DisplayName("with no arguments")
        void testNoArguments() {
            HKEY hKey = mockOpenAndClose(WinReg.HKEY_CURRENT_USER, "Software\\JavaSoft\\Prefs");

            RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER.resolve("Software\\JavaSoft\\Prefs");
            try (RegistryKey.Handle handle = registryKey.handle()) {
                // Do nothing
            }

            verify(RegistryKey.api, never()).RegCreateKeyEx(any(), any(), anyInt(), any(), anyInt(), anyInt(), any(), any(), any());
            verify(RegistryKey.api).RegOpenKeyEx(eq(WinReg.HKEY_CURRENT_USER), eq("Software\\JavaSoft\\Prefs"), anyInt(), eq(WinNT.KEY_READ), any());
            verify(RegistryKey.api).RegCloseKey(hKey);
        }

        @Test
        @DisplayName("with CREATE")
        void testWithCreate() {
            HKEY hKey = newHKEY();

            when(RegistryKey.api.RegCreateKeyEx(eq(WinReg.HKEY_CURRENT_USER), eq("Software\\JavaSoft\\Prefs"),
                    anyInt(), any(), anyInt(), anyInt(), any(), any(), any()))
                    .thenAnswer(i -> {
                        i.getArgument(7, HKEYByReference.class).setValue(hKey);
                        // disposition doesn't matter

                        return WinError.ERROR_SUCCESS;
                    });

            RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER.resolve("Software\\JavaSoft\\Prefs");
            try (RegistryKey.Handle handle = registryKey.handle(RegistryKey.HandleOption.CREATE)) {
                // Do nothing
            }

            verify(RegistryKey.api, never()).RegOpenKeyEx(any(), any(), anyInt(), anyInt(), any());
            verify(RegistryKey.api).RegCreateKeyEx(eq(WinReg.HKEY_CURRENT_USER), eq("Software\\JavaSoft\\Prefs"),
                    anyInt(), any(), anyInt(), eq(WinNT.KEY_READ), any(), any(), any());
            verify(RegistryKey.api).RegCloseKey(hKey);
        }

        @Test
        @DisplayName("with MANAGE_VALUES")
        void testWithManageValues() {
            HKEY hKey = mockOpenAndClose(WinReg.HKEY_CURRENT_USER, "Software\\JavaSoft\\Prefs");

            RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER.resolve("Software\\JavaSoft\\Prefs");
            try (RegistryKey.Handle handle = registryKey.handle(RegistryKey.HandleOption.MANAGE_VALUES)) {
                // Do nothing
            }

            verify(RegistryKey.api, never()).RegCreateKeyEx(any(), any(), anyInt(), any(), anyInt(), anyInt(), any(), any(), any());
            verify(RegistryKey.api).RegOpenKeyEx(eq(WinReg.HKEY_CURRENT_USER), eq("Software\\JavaSoft\\Prefs"), anyInt(),
                    eq(WinNT.KEY_READ | WinNT.KEY_SET_VALUE), any());
            verify(RegistryKey.api).RegCloseKey(hKey);
        }

        @Test
        @DisplayName("with CREATE and MANAGE_VALUES")
        void testWithCreateAndManageValues() {
            HKEY hKey = newHKEY();

            when(RegistryKey.api.RegCreateKeyEx(eq(WinReg.HKEY_CURRENT_USER), eq("Software\\JavaSoft\\Prefs"),
                    anyInt(), any(), anyInt(), anyInt(), any(), any(), any()))
                    .thenAnswer(i -> {
                        i.getArgument(7, HKEYByReference.class).setValue(hKey);
                        // disposition doesn't matter

                        return WinError.ERROR_SUCCESS;
                    });

            RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER.resolve("Software\\JavaSoft\\Prefs");
            try (RegistryKey.Handle handle = registryKey.handle(RegistryKey.HandleOption.CREATE, RegistryKey.HandleOption.MANAGE_VALUES)) {
                // Do nothing
            }

            verify(RegistryKey.api, never()).RegOpenKeyEx(any(), any(), anyInt(), anyInt(), any());
            verify(RegistryKey.api).RegCreateKeyEx(eq(WinReg.HKEY_CURRENT_USER), eq("Software\\JavaSoft\\Prefs"),
                    anyInt(), any(), anyInt(), eq(WinNT.KEY_READ | WinNT.KEY_SET_VALUE), any(), any(), any());
            verify(RegistryKey.api).RegCloseKey(hKey);
        }

        @Test
        @DisplayName("open failure")
        void testOpenFailure() {
            when(RegistryKey.api.RegOpenKeyEx(eq(WinReg.HKEY_CURRENT_USER), eq("path\\failure"), anyInt(), anyInt(), any()))
                    .thenReturn(WinError.ERROR_ACCESS_DENIED);

            RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER.resolve("path\\failure");
            RegistryAccessDeniedException exception = assertThrows(RegistryAccessDeniedException.class, registryKey::handle);
            assertEquals("HKEY_CURRENT_USER\\path\\failure", exception.path());

            verify(RegistryKey.api).RegOpenKeyEx(eq(WinReg.HKEY_CURRENT_USER), eq("path\\failure"), anyInt(), anyInt(), any());
            verify(RegistryKey.api, never()).RegCreateKeyEx(any(), any(), anyInt(), any(), anyInt(), anyInt(), any(), any(), any());
            verify(RegistryKey.api, never()).RegCloseKey(any());
        }

        @Test
        @DisplayName("create failure")
        void testCreateFailure() {
            when(RegistryKey.api.RegCreateKeyEx(eq(WinReg.HKEY_CURRENT_USER), eq("path\\failure"),
                    anyInt(), any(), anyInt(), anyInt(), any(), any(), any()))
                    .thenReturn(WinError.ERROR_ACCESS_DENIED);

            RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER.resolve("path\\failure");
            RegistryAccessDeniedException exception = assertThrows(RegistryAccessDeniedException.class,
                    () -> registryKey.handle(RegistryKey.HandleOption.CREATE));
            assertEquals("HKEY_CURRENT_USER\\path\\failure", exception.path());

            verify(RegistryKey.api).RegCreateKeyEx(eq(WinReg.HKEY_CURRENT_USER), eq("path\\failure"), anyInt(), any(), anyInt(), anyInt(),
                    any(), any(), any());
            verify(RegistryKey.api, never()).RegOpenKeyEx(any(), any(), anyInt(), anyInt(), any());
            verify(RegistryKey.api, never()).RegCloseKey(any());
        }

        @Test
        @DisplayName("close failure")
        void testCloseFailure() {
            HKEY hKey = mockOpen(WinReg.HKEY_CURRENT_USER, "path\\failure");

            mockClose(hKey, WinError.ERROR_INVALID_HANDLE);

            mockValue(hKey, new StringRegistryValue("test", "test"), WinError.ERROR_ACCESS_DENIED);

            RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER.resolve("path\\failure");
            RegistryAccessDeniedException exception = assertThrows(RegistryAccessDeniedException.class, () -> triggerCloseFailure(registryKey));
            assertEquals("HKEY_CURRENT_USER\\path\\failure", exception.path());
            assertThat(exception.getSuppressed(), arrayContaining(instanceOf(InvalidRegistryHandleException.class)));

            verify(RegistryKey.api).RegOpenKeyEx(eq(WinReg.HKEY_CURRENT_USER), eq("path\\failure"), anyInt(), anyInt(), any());
            verify(RegistryKey.api).RegCloseKey(hKey);
        }

        private void triggerCloseFailure(RegistryKey registryKey) {
            try (RegistryKey.Handle handle = registryKey.handle()) {
                handle.getValue("test");
            }
        }
    }

    @Test
    @DisplayName("compareTo")
    void testCompareTo() {
        List<RegistryKey> registryKeys = Arrays.asList(
                RegistryKey.HKEY_CURRENT_USER.resolve("Software\\JavaSoft\\Prefs"),
                RegistryKey.HKEY_CURRENT_USER.resolve("Software\\JavaSoft"),
                RegistryKey.HKEY_CURRENT_USER.resolve("Software"),
                RegistryKey.HKEY_CURRENT_USER,
                RegistryKey.HKEY_LOCAL_MACHINE.resolve("Software\\JavaSoft\\Prefs"),
                RegistryKey.HKEY_LOCAL_MACHINE.resolve("Software\\JavaSoft"),
                RegistryKey.HKEY_LOCAL_MACHINE.resolve("Software"),
                RegistryKey.HKEY_LOCAL_MACHINE
        );
        registryKeys.sort(null);

        List<RegistryKey> expected = Arrays.asList(
                RegistryKey.HKEY_CURRENT_USER,
                RegistryKey.HKEY_CURRENT_USER.resolve("Software"),
                RegistryKey.HKEY_CURRENT_USER.resolve("Software\\JavaSoft"),
                RegistryKey.HKEY_CURRENT_USER.resolve("Software\\JavaSoft\\Prefs"),
                RegistryKey.HKEY_LOCAL_MACHINE,
                RegistryKey.HKEY_LOCAL_MACHINE.resolve("Software"),
                RegistryKey.HKEY_LOCAL_MACHINE.resolve("Software\\JavaSoft"),
                RegistryKey.HKEY_LOCAL_MACHINE.resolve("Software\\JavaSoft\\Prefs")
        );

        assertEquals(expected, registryKeys);
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("equalsArguments")
    @DisplayName("equals")
    void testEquals(RegistryKey value, Object other, boolean expected) {
        assertEquals(expected, value.equals(other));
    }

    static Arguments[] equalsArguments() {
        RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER.resolve("Software\\JavaSoft\\Prefs");

        return new Arguments[] {
                arguments(registryKey, registryKey, true),
                arguments(registryKey, RegistryKey.HKEY_CURRENT_USER.resolve("Software\\JavaSoft\\Prefs"), true),
                arguments(registryKey, RegistryKey.HKEY_CURRENT_USER.resolve("Software\\JavaSoft\\prefs"), false),
                arguments(registryKey, RegistryKey.HKEY_LOCAL_MACHINE.resolve("Software\\JavaSoft\\Prefs"), false),
                arguments(registryKey, "foo", false),
                arguments(registryKey, null, false),
        };
    }

    @Test
    @DisplayName("hashCode")
    void testHashCode() {
        RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER.resolve("Software\\JavaSoft\\Prefs");

        assertEquals(registryKey.hashCode(), registryKey.hashCode());
        assertEquals(registryKey.hashCode(), RegistryKey.HKEY_CURRENT_USER.resolve("Software\\JavaSoft\\Prefs").hashCode());
    }

    private HKEY newHKEY() {
        return new HKEY(1);
    }

    private HKEY mockOpenAndClose(HKEY hKey, String path) {
        HKEY result = mockOpen(hKey, path);
        mockClose(hKey);
        return result;
    }

    private HKEY mockOpen(HKEY hKey, String path) {
        HKEY result = newHKEY();

        when(RegistryKey.api.RegOpenKeyEx(eq(hKey), eq(path), anyInt(), anyInt(), any())).thenAnswer(i -> {
            i.getArgument(4, HKEYByReference.class).setValue(result);

            return WinError.ERROR_SUCCESS;
        });

        return result;
    }

    private void mockClose(HKEY hKey) {
        mockClose(hKey, WinError.ERROR_SUCCESS);
    }

    private void mockClose(HKEY hKey, int result) {
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

    static void mockValues(HKEY hKey, RegistryValue... values) {
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

    static void mockValue(HKEY hKey, RegistryValue value) {
        mockValue(hKey, value, WinError.ERROR_SUCCESS);
    }

    static void mockValue(HKEY hKey, RegistryValue value, int returnCode) {
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
