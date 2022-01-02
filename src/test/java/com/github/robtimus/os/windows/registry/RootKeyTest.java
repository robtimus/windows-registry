/*
 * RootKeyTest.java
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
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import com.sun.jna.platform.win32.WinError;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.platform.win32.WinReg;

@SuppressWarnings("nls")
class RootKeyTest extends RegistryKeyTest {

    @Override
    @AfterEach
    void teardown() {
        verify(RegistryKey.api, never()).RegOpenKeyEx(any(), any(), anyInt(), anyInt(), any());
        verify(RegistryKey.api, never()).RegCreateKeyEx(any(), any(), anyInt(), any(), anyInt(), anyInt(), any(), any(), any());
        verify(RegistryKey.api, never()).RegCloseKey(any());

        super.teardown();
    }

    @Test
    @DisplayName("name")
    void testName() {
        assertEquals("HKEY_CURRENT_USER", RegistryKey.HKEY_CURRENT_USER.name());
    }

    @Test
    @DisplayName("path")
    void testPath() {
        assertEquals("HKEY_CURRENT_USER", RegistryKey.HKEY_CURRENT_USER.path());
    }

    @Test
    @DisplayName("isRoot")
    void testIsRoot() {
        assertTrue(RegistryKey.HKEY_CURRENT_USER.isRoot());
    }

    @Test
    @DisplayName("root")
    void testRoot() {
        assertSame(RegistryKey.HKEY_CURRENT_USER, RegistryKey.HKEY_CURRENT_USER.root());
    }

    @Test
    @DisplayName("parent")
    void testParent() {
        assertEquals(Optional.empty(), RegistryKey.HKEY_CURRENT_USER.parent());
    }

    @ParameterizedTest(name = "{0} => {1}")
    @CsvSource({
            ", HKEY_CURRENT_USER",
            "., HKEY_CURRENT_USER",
            ".., HKEY_CURRENT_USER",
            "..\\.., HKEY_CURRENT_USER",
            "..\\..\\.., HKEY_CURRENT_USER",
            "..\\..\\..\\.., HKEY_CURRENT_USER",
            "child, HKEY_CURRENT_USER\\child",
            "..\\..\\..\\..\\..\\Something\\..\\..\\Something else\\\\.\\leaf, HKEY_CURRENT_USER\\Something else\\leaf"
    })
    @DisplayName("resolve")
    void testResolve(String relativePath, String expectedPath) {
        RegistryKey resolved = RegistryKey.HKEY_CURRENT_USER.resolve(relativePath != null ? relativePath : "");
        assertEquals(expectedPath, resolved.path());
    }

    @Nested
    @DisplayName("subKeys")
    class SubKeys {

        @Test
        @DisplayName("success")
        void testSuccess() {
            mockSubKeys(WinReg.HKEY_CURRENT_USER, "child1", "child2", "child3");

            RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER;
            try (Stream<RegistryKey> stream = registryKey.subKeys()) {
                List<RegistryKey> subKeys = stream.collect(Collectors.toList());

                List<RegistryKey> expected = Arrays.asList(
                        registryKey.resolve("child1"),
                        registryKey.resolve("child2"),
                        registryKey.resolve("child3")
                );

                assertEquals(expected, subKeys);
            }
        }

        @Test
        @DisplayName("query failure")
        void testQueryFailure() {
            when(RegistryKey.api.RegQueryInfoKey(eq(WinReg.HKEY_CURRENT_USER),
                    any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(WinError.ERROR_FILE_NOT_FOUND);

            RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER;
            NoSuchRegistryKeyException exception = assertThrows(NoSuchRegistryKeyException.class, registryKey::subKeys);
            assertEquals("HKEY_CURRENT_USER", exception.path());
        }

        @Test
        @DisplayName("enum failure")
        void testEnumFailure() {
            when(RegistryKey.api.RegQueryInfoKey(eq(WinReg.HKEY_CURRENT_USER),
                    any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(WinError.ERROR_SUCCESS);

            when(RegistryKey.api.RegEnumKeyEx(eq(WinReg.HKEY_CURRENT_USER), eq(0), any(), any(), any(), any(), any(), any()))
                    .thenReturn(WinError.ERROR_FILE_NOT_FOUND);

            RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER;
            try (Stream<RegistryKey> stream = registryKey.subKeys()) {
                Collector<RegistryKey, ?, ?> collector = Collectors.toList();
                NoSuchRegistryKeyException exception = assertThrows(NoSuchRegistryKeyException.class, () -> stream.collect(collector));
                assertEquals("HKEY_CURRENT_USER", exception.path());
            }
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

                mockValues(WinReg.HKEY_CURRENT_USER, stringValue, binaryValue, wordValue);

                RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER;
                try (Stream<RegistryValue> stream = registryKey.values()) {
                    List<RegistryValue> values = stream.collect(Collectors.toList());

                    List<RegistryValue> expected = Arrays.asList(stringValue, binaryValue, wordValue);

                    assertEquals(expected, values);
                }
            }

            @Test
            @DisplayName("with name filter")
            void testWithNameFilter() {
                StringRegistryValue stringValue = new StringRegistryValue("string", "value");
                BinaryRegistryValue binaryValue = new BinaryRegistryValue("binary", randomData());
                DWordRegistryValue wordValue = new DWordRegistryValue("dword", 13);

                mockValues(WinReg.HKEY_CURRENT_USER, stringValue, binaryValue, wordValue);

                RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER;
                RegistryValue.Filter filter = RegistryValue.filter().name(s -> s.contains("i"));
                try (Stream<RegistryValue> stream = registryKey.values(filter)) {
                    List<RegistryValue> values = stream.collect(Collectors.toList());

                    List<RegistryValue> expected = Arrays.asList(stringValue, binaryValue);

                    assertEquals(expected, values);
                }
            }

            @Test
            @DisplayName("with type filter")
            void testWithTypeFilter() {
                StringRegistryValue stringValue = new StringRegistryValue("string", "value");
                BinaryRegistryValue binaryValue = new BinaryRegistryValue("binary", randomData());
                DWordRegistryValue wordValue = new DWordRegistryValue("dword", 13);

                mockValues(WinReg.HKEY_CURRENT_USER, stringValue, binaryValue, wordValue);

                RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER;
                RegistryValue.Filter filter = RegistryValue.filter().strings().words();
                try (Stream<RegistryValue> stream = registryKey.values(filter)) {
                    List<RegistryValue> values = stream.collect(Collectors.toList());

                    List<RegistryValue> expected = Arrays.asList(stringValue, wordValue);

                    assertEquals(expected, values);
                }
            }
        }

        @Test
        @DisplayName("query failure")
        void testQueryFailure() {
            when(RegistryKey.api.RegQueryInfoKey(eq(WinReg.HKEY_CURRENT_USER),
                    any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(WinError.ERROR_FILE_NOT_FOUND);

            RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER;
            NoSuchRegistryKeyException exception = assertThrows(NoSuchRegistryKeyException.class, registryKey::values);
            assertEquals("HKEY_CURRENT_USER", exception.path());
        }

        @Test
        @DisplayName("enum failure")
        void testEnumFailure() {
            when(RegistryKey.api.RegQueryInfoKey(eq(WinReg.HKEY_CURRENT_USER),
                    any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(WinError.ERROR_SUCCESS);

            when(RegistryKey.api.RegEnumValue(eq(WinReg.HKEY_CURRENT_USER), eq(0), any(), any(), any(), any(), any(byte[].class), any()))
                    .thenReturn(WinError.ERROR_FILE_NOT_FOUND);

            RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER;
            try (Stream<RegistryValue> stream = registryKey.values()) {
                Collector<RegistryValue, ?, ?> collector = Collectors.toList();
                NoSuchRegistryKeyException exception = assertThrows(NoSuchRegistryKeyException.class, () -> stream.collect(collector));
                assertEquals("HKEY_CURRENT_USER", exception.path());
            }
        }
    }

    @Nested
    @DisplayName("getValue")
    class GetValue {

        @Test
        @DisplayName("success")
        void testSuccess() {
            StringRegistryValue stringValue = new StringRegistryValue("string", "value");

            mockValue(WinReg.HKEY_CURRENT_USER, stringValue);

            RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER;
            Optional<RegistryValue> value = registryKey.getValue("string");
            assertEquals(Optional.of(stringValue), value);
        }

        @Test
        @DisplayName("non-existing value")
        void testNonExistingValue() {
            when(RegistryKey.api.RegQueryValueEx(eq(WinReg.HKEY_CURRENT_USER), any(), anyInt(), any(), (byte[]) isNull(), any()))
                    .thenReturn(WinError.ERROR_FILE_NOT_FOUND);

            RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER;
            Optional<RegistryValue> value = registryKey.getValue("string");
            assertEquals(Optional.empty(), value);
        }

        @Test
        @DisplayName("failure")
        void testFailure() {
            mockValue(WinReg.HKEY_CURRENT_USER, new StringRegistryValue("string", "value"), WinError.ERROR_INVALID_HANDLE);

            RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER;
            InvalidRegistryHandleException exception = assertThrows(InvalidRegistryHandleException.class, () -> registryKey.getValue("string"));
            assertEquals("HKEY_CURRENT_USER", exception.path());
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

            when(RegistryKey.api.RegSetValueEx(any(), eq("string"), anyInt(), eq(WinNT.REG_SZ), (byte[]) isNull(), anyInt()))
                    .thenReturn(WinError.ERROR_SUCCESS);

            RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER;
            registryKey.setValue(stringValue);

            ArgumentCaptor<byte[]> dataCaptor = ArgumentCaptor.forClass(byte[].class);
            verify(RegistryKey.api).RegSetValueEx(any(), eq("string"), anyInt(), eq(WinNT.REG_SZ), dataCaptor.capture(), eq(data.length));
            assertArrayEquals(data, dataCaptor.getValue());
        }

        @Test
        @DisplayName("failure")
        void testFailure() {
            StringRegistryValue stringValue = new StringRegistryValue("string", "value");

            when(RegistryKey.api.RegSetValueEx(any(), any(), anyInt(), anyInt(), any(byte[].class), anyInt()))
                    .thenReturn(WinError.ERROR_INVALID_HANDLE);

            RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER;
            InvalidRegistryHandleException exception = assertThrows(InvalidRegistryHandleException.class, () -> registryKey.setValue(stringValue));
            assertEquals("HKEY_CURRENT_USER", exception.path());
        }
    }

    @Nested
    @DisplayName("deleteValue")
    class DeleteValue {

        @Test
        @DisplayName("success")
        void testSuccess() {
            when(RegistryKey.api.RegDeleteValue(any(), eq("string"))).thenReturn(WinError.ERROR_SUCCESS);

            RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER;
            registryKey.deleteValue("string");

            verify(RegistryKey.api).RegDeleteValue(any(), eq("string"));
        }

        @Test
        @DisplayName("failure")
        void testFailure() {
            when(RegistryKey.api.RegDeleteValue(any(), any())).thenReturn(WinError.ERROR_FILE_NOT_FOUND);

            RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER;
            NoSuchRegistryValueException exception = assertThrows(NoSuchRegistryValueException.class, () -> registryKey.deleteValue("string"));
            assertEquals("HKEY_CURRENT_USER", exception.path());
            assertEquals("string", exception.name());
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
                when(RegistryKey.api.RegDeleteValue(any(), eq("string"))).thenReturn(WinError.ERROR_SUCCESS);

                RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER;
                assertTrue(registryKey.deleteValueIfExists("string"));

                verify(RegistryKey.api).RegDeleteValue(any(), eq("string"));
            }

            @Test
            @DisplayName("value didn't exist")
            void testValueDidntExist() {
                when(RegistryKey.api.RegDeleteValue(any(), eq("string"))).thenReturn(WinError.ERROR_FILE_NOT_FOUND);

                RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER;
                assertFalse(registryKey.deleteValueIfExists("string"));

                verify(RegistryKey.api).RegDeleteValue(any(), eq("string"));
            }
        }

        @Test
        @DisplayName("failure")
        void testFailure() {
            when(RegistryKey.api.RegDeleteValue(any(), any())).thenReturn(WinError.ERROR_INVALID_HANDLE);

            RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER;
            InvalidRegistryHandleException exception = assertThrows(InvalidRegistryHandleException.class,
                    () -> registryKey.deleteValueIfExists("string"));
            assertEquals("HKEY_CURRENT_USER", exception.path());
        }
    }

    @Test
    @DisplayName("exists")
    void testExists() {
        assertTrue(RegistryKey.HKEY_CURRENT_USER.exists());
    }

    @Test
    @DisplayName("create")
    void testCreate() {
        RegistryKeyAlreadyExistsException exception = assertThrows(RegistryKeyAlreadyExistsException.class, RegistryKey.HKEY_CURRENT_USER::create);
        assertEquals("HKEY_CURRENT_USER", exception.path());
    }

    @Test
    @DisplayName("createIfNotExists")
    void testCreateIfNotExists() {
        assertFalse(RegistryKey.HKEY_CURRENT_USER::createIfNotExists);
    }

    @Test
    @DisplayName("createAll")
    void testCreateAll() {
        assertFalse(RegistryKey.HKEY_CURRENT_USER::createAll);
    }

    @Test
    @DisplayName("delete")
    void testDelete() {
        assertThrows(UnsupportedOperationException.class, RegistryKey.HKEY_CURRENT_USER::delete);
    }

    @Test
    @DisplayName("deleteIfExists")
    void testDeleteIfExists() {
        assertThrows(UnsupportedOperationException.class, RegistryKey.HKEY_CURRENT_USER::deleteIfExists);
    }

    @Nested
    @DisplayName("handle")
    class Handle {

        @Test
        @DisplayName("with no arguments")
        void testNoArguments() {
            RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER;
            assertDoesNotThrow(() -> {
                try (RegistryKey.Handle handle = registryKey.handle()) {
                    // Do nothing
                }
            });
        }

        @Test
        @DisplayName("with CREATE")
        void testWithCreate() {
            RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER;
            assertDoesNotThrow(() -> {
                try (RegistryKey.Handle handle = registryKey.handle(RegistryKey.HandleOption.CREATE)) {
                    // Do nothing
                }
            });
        }

        @Test
        @DisplayName("with MANAGE_VALUES")
        void testWithManageValues() {
            RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER;
            assertDoesNotThrow(() -> {
                try (RegistryKey.Handle handle = registryKey.handle(RegistryKey.HandleOption.MANAGE_VALUES)) {
                    // Do nothing
                }
            });
        }

        @Test
        @DisplayName("with CREATE and MANAGE_VALUES")
        void testWithCreateAndManageValues() {
            RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER;
            assertDoesNotThrow(() -> {
                try (RegistryKey.Handle handle = registryKey.handle(RegistryKey.HandleOption.CREATE, RegistryKey.HandleOption.MANAGE_VALUES)) {
                    // Do nothing
                }
            });
        }

        @Test
        @DisplayName("close twice")
        void testCloseTwice() {
            RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER;
            assertDoesNotThrow(() -> {
                try (RegistryKey.Handle handle = registryKey.handle()) {
                    handle.close();
                }
            });
        }
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("equalsArguments")
    @DisplayName("equals")
    void testEquals(RootKey value, Object other, boolean expected) {
        assertEquals(expected, value.equals(other));
    }

    static Arguments[] equalsArguments() {
        RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER;

        return new Arguments[] {
                arguments(registryKey, registryKey, true),
                arguments(registryKey, registryKey.resolve(""), true),
                arguments(registryKey, registryKey.resolve("test"), false),
                arguments(registryKey, new RootKey(WinReg.HKEY_CURRENT_USER, "HKEY_CURRENT_USER"), false),
                arguments(registryKey, RegistryKey.HKEY_LOCAL_MACHINE, false),
                arguments(registryKey, "foo", false),
                arguments(registryKey, null, false),
        };
    }

    @Test
    @DisplayName("hashCode")
    void testHashCode() {
        RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER;

        assertEquals(registryKey.hashCode(), registryKey.hashCode());
    }
}
