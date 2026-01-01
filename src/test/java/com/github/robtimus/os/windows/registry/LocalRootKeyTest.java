/*
 * LocalRootKeyTest.java
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

import static com.github.robtimus.os.windows.registry.RegistryKeyMocks.mockSubKeys;
import static com.github.robtimus.os.windows.registry.RegistryKeyMocks.mockValue;
import static com.github.robtimus.os.windows.registry.RegistryKeyMocks.mockValues;
import static com.github.robtimus.os.windows.registry.RegistryValueTest.randomData;
import static com.github.robtimus.os.windows.registry.foreign.Advapi32.RegCloseKey;
import static com.github.robtimus.os.windows.registry.foreign.Advapi32.RegCreateKeyEx;
import static com.github.robtimus.os.windows.registry.foreign.Advapi32.RegDeleteValue;
import static com.github.robtimus.os.windows.registry.foreign.Advapi32.RegEnumKeyEx;
import static com.github.robtimus.os.windows.registry.foreign.Advapi32.RegEnumValue;
import static com.github.robtimus.os.windows.registry.foreign.Advapi32.RegOpenKeyEx;
import static com.github.robtimus.os.windows.registry.foreign.Advapi32.RegQueryInfoKey;
import static com.github.robtimus.os.windows.registry.foreign.Advapi32.RegQueryValueEx;
import static com.github.robtimus.os.windows.registry.foreign.Advapi32.RegSetValueEx;
import static com.github.robtimus.os.windows.registry.foreign.ForeignTestUtils.ALLOCATOR;
import static com.github.robtimus.os.windows.registry.foreign.ForeignTestUtils.eqBytes;
import static com.github.robtimus.os.windows.registry.foreign.ForeignTestUtils.eqPointer;
import static com.github.robtimus.os.windows.registry.foreign.ForeignTestUtils.eqSize;
import static com.github.robtimus.os.windows.registry.foreign.ForeignTestUtils.isNULL;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import java.lang.foreign.MemorySegment;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import com.github.robtimus.os.windows.registry.foreign.WinError;
import com.github.robtimus.os.windows.registry.foreign.WinNT;
import com.github.robtimus.os.windows.registry.foreign.WinReg;

@SuppressWarnings("nls")
class LocalRootKeyTest extends RegistryTestBase {

    private static final LocalRegistry REGISTRY = Registry.local();

    @AfterEach
    void verifyRootKeysNotOpenedCreatedOrClosed() {
        advapi32.verify(() -> RegOpenKeyEx(notNull(), notNull(), anyInt(), anyInt(), notNull()), never());
        advapi32.verify(() -> RegCreateKeyEx(notNull(), notNull(), anyInt(), notNull(), anyInt(), anyInt(), notNull(), notNull(), notNull()),
                never());
        advapi32.verify(() -> RegCloseKey(notNull()), never());
    }

    @Test
    @DisplayName("name")
    void testName() {
        assertEquals("HKEY_CURRENT_USER", REGISTRY.HKEY_CURRENT_USER.name());
    }

    @Test
    @DisplayName("path")
    void testPath() {
        assertEquals("HKEY_CURRENT_USER", REGISTRY.HKEY_CURRENT_USER.path());
    }

    @Test
    @DisplayName("isRoot")
    void testIsRoot() {
        assertTrue(REGISTRY.HKEY_CURRENT_USER.isRoot());
    }

    @Test
    @DisplayName("root")
    void testRoot() {
        assertSame(REGISTRY.HKEY_CURRENT_USER, REGISTRY.HKEY_CURRENT_USER.root());
    }

    @Test
    @DisplayName("parent")
    void testParent() {
        assertEquals(Optional.empty(), REGISTRY.HKEY_CURRENT_USER.parent());
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
            "..\\..\\..\\..\\..\\Something\\..\\..\\Something else\\\\.\\leaf, HKEY_CURRENT_USER\\Something else\\leaf",
            "\\absolute, HKEY_CURRENT_USER\\absolute",
            "child\\, HKEY_CURRENT_USER\\child",
            "\\, HKEY_CURRENT_USER",
            "\\\\\\, HKEY_CURRENT_USER"
    })
    @DisplayName("resolve")
    void testResolve(String relativePath, String expectedPath) {
        RegistryKey resolved = REGISTRY.HKEY_CURRENT_USER.resolve(relativePath != null ? relativePath : "");
        assertEquals(expectedPath, resolved.path());
    }

    @Nested
    @DisplayName("subKeys")
    class SubKeys {

        @Test
        @DisplayName("success")
        void testSuccess() {
            mockSubKeys(WinReg.HKEY_CURRENT_USER, "child1", "child2", "child3");

            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER;
            try (Stream<RegistryKey> stream = registryKey.subKeys()) {
                List<RegistryKey> subKeys = stream.toList();

                List<RegistryKey> expected = List.of(
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
            advapi32.when(() -> RegQueryInfoKey(eq(WinReg.HKEY_CURRENT_USER), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(),
                    notNull(), notNull(), notNull(), notNull(), notNull()))
                    .thenReturn(WinError.ERROR_FILE_NOT_FOUND);

            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER;
            NoSuchRegistryKeyException exception = assertThrows(NoSuchRegistryKeyException.class, registryKey::subKeys);
            assertEquals("HKEY_CURRENT_USER", exception.path());
        }

        @Test
        @DisplayName("enum failure")
        void testEnumFailure() {
            advapi32.when(() -> RegQueryInfoKey(eq(WinReg.HKEY_CURRENT_USER), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(),
                    notNull(), notNull(), notNull(), notNull(), notNull()))
                    .thenReturn(WinError.ERROR_SUCCESS);

            advapi32.when(() -> RegEnumKeyEx(eq(WinReg.HKEY_CURRENT_USER), eq(0), notNull(), notNull(), notNull(), notNull(), notNull(), notNull()))
                    .thenReturn(WinError.ERROR_FILE_NOT_FOUND);

            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER;
            try (Stream<RegistryKey> stream = registryKey.subKeys()) {
                NoSuchRegistryKeyException exception = assertThrows(NoSuchRegistryKeyException.class, stream::toList);
                assertEquals("HKEY_CURRENT_USER", exception.path());
            }
        }
    }

    @Nested
    @DisplayName("traverse")
    class Traverse {

        @Test
        @DisplayName("maxDepth == 0")
        void testMaxDepthIsZero() {
            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER;
            try (Stream<RegistryKey> stream = registryKey.traverse(0)) {
                List<RegistryKey> registryKeys = stream.toList();

                List<RegistryKey> expected = List.of(registryKey);

                assertEquals(expected, registryKeys);
            }

            advapi32.verify(() -> RegOpenKeyEx(notNull(), notNull(), anyInt(), anyInt(), notNull()), never());
            advapi32.verify(() -> RegCloseKey(notNull()), never());
        }

        @Nested
        @DisplayName("maxDepth == 1")
        class MaxDepthIsOne {

            @Test
            @DisplayName("subKeys first")
            void testSubKeysFirst() {
                mockSubKeys(WinReg.HKEY_CURRENT_USER, "subKey1", "subKey2", "subKey3");

                RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER;
                try (Stream<RegistryKey> stream = registryKey.traverse(1, RegistryKey.TraverseOption.SUB_KEYS_FIRST)) {
                    List<RegistryKey> registryKeys = stream.toList();

                    List<RegistryKey> expected = List.of(
                            registryKey.resolve("subKey1"),
                            registryKey.resolve("subKey2"),
                            registryKey.resolve("subKey3"),
                            registryKey
                    );

                    assertEquals(expected, registryKeys);
                }
            }

            @Test
            @DisplayName("subKeys not first")
            void testSubKeysNotFirst() {
                mockSubKeys(WinReg.HKEY_CURRENT_USER, "subKey1", "subKey2", "subKey3");

                RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER;
                try (Stream<RegistryKey> stream = registryKey.traverse(1)) {
                    List<RegistryKey> registryKeys = stream.toList();

                    List<RegistryKey> expected = List.of(
                            registryKey,
                            registryKey.resolve("subKey1"),
                            registryKey.resolve("subKey2"),
                            registryKey.resolve("subKey3")
                    );

                    assertEquals(expected, registryKeys);
                }
            }
        }

        // Testing with maxDepth > 1 conflicts with the assertions in teardown

        @Test
        @DisplayName("negative maxDepth")
        void testNegativeMaxDepth() {
            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER;
            assertThrows(IllegalArgumentException.class, () -> registryKey.traverse(-1));
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
                StringValue stringValue = StringValue.of("string", "value");
                BinaryValue binaryValue = BinaryValue.of("binary", randomData());
                DWordValue wordValue = DWordValue.of("dword", 13);

                mockValues(WinReg.HKEY_CURRENT_USER, stringValue, binaryValue, wordValue);

                RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER;
                try (Stream<RegistryValue> stream = registryKey.values()) {
                    List<RegistryValue> values = stream.toList();

                    List<RegistryValue> expected = List.of(stringValue, binaryValue, wordValue);

                    assertEquals(expected, values);
                }
            }

            @Test
            @DisplayName("with name filter")
            void testWithNameFilter() {
                StringValue stringValue = StringValue.of("string", "value");
                BinaryValue binaryValue = BinaryValue.of("binary", randomData());
                DWordValue wordValue = DWordValue.of("dword", 13);

                mockValues(WinReg.HKEY_CURRENT_USER, stringValue, binaryValue, wordValue);

                RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER;
                RegistryValue.Filter filter = RegistryValue.filter().name(s -> s.contains("i"));
                try (Stream<RegistryValue> stream = registryKey.values(filter)) {
                    List<RegistryValue> values = stream.toList();

                    List<RegistryValue> expected = List.of(stringValue, binaryValue);

                    assertEquals(expected, values);
                }
            }

            @Test
            @DisplayName("with type filter")
            void testWithTypeFilter() {
                StringValue stringValue = StringValue.of("string", "value");
                BinaryValue binaryValue = BinaryValue.of("binary", randomData());
                DWordValue wordValue = DWordValue.of("dword", 13);

                mockValues(WinReg.HKEY_CURRENT_USER, stringValue, binaryValue, wordValue);

                RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER;
                RegistryValue.Filter filter = RegistryValue.filter().strings().words();
                try (Stream<RegistryValue> stream = registryKey.values(filter)) {
                    List<RegistryValue> values = stream.toList();

                    List<RegistryValue> expected = List.of(stringValue, wordValue);

                    assertEquals(expected, values);
                }
            }
        }

        @Test
        @DisplayName("query failure")
        void testQueryFailure() {
            advapi32.when(() -> RegQueryInfoKey(eq(WinReg.HKEY_CURRENT_USER), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(),
                    notNull(), notNull(), notNull(), notNull(), notNull()))
                    .thenReturn(WinError.ERROR_FILE_NOT_FOUND);

            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER;
            NoSuchRegistryKeyException exception = assertThrows(NoSuchRegistryKeyException.class, registryKey::values);
            assertEquals("HKEY_CURRENT_USER", exception.path());
        }

        @Test
        @DisplayName("enum failure")
        void testEnumFailure() {
            advapi32.when(() -> RegQueryInfoKey(eq(WinReg.HKEY_CURRENT_USER), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(),
                    notNull(), notNull(), notNull(), notNull(), notNull()))
                    .thenReturn(WinError.ERROR_SUCCESS);

            advapi32.when(() -> RegEnumValue(eq(WinReg.HKEY_CURRENT_USER), eq(0), notNull(), notNull(), notNull(), notNull(), notNull(), notNull()))
                    .thenReturn(WinError.ERROR_FILE_NOT_FOUND);

            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER;
            try (Stream<RegistryValue> stream = registryKey.values()) {
                NoSuchRegistryKeyException exception = assertThrows(NoSuchRegistryKeyException.class, stream::toList);
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
            StringValue stringValue = StringValue.of("string", "value");

            mockValue(WinReg.HKEY_CURRENT_USER, stringValue);

            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER;
            StringValue value = registryKey.getValue("string", StringValue.class);
            assertEquals(stringValue, value);
        }

        @Test
        @DisplayName("non-existing value")
        void testNonExistingValue() {
            advapi32.when(() -> RegQueryValueEx(eq(WinReg.HKEY_CURRENT_USER), notNull(), notNull(), notNull(), isNULL(), notNull()))
                    .thenReturn(WinError.ERROR_FILE_NOT_FOUND);

            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER;
            NoSuchRegistryValueException exception = assertThrows(NoSuchRegistryValueException.class,
                    () -> registryKey.getValue("string", RegistryValue.class));
            assertEquals("HKEY_CURRENT_USER", exception.path());
            assertEquals("string", exception.name());
        }

        @Test
        @DisplayName("failure")
        void testFailure() {
            mockValue(WinReg.HKEY_CURRENT_USER, StringValue.of("string", "value"), WinError.ERROR_INVALID_HANDLE);

            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER;
            InvalidRegistryHandleException exception = assertThrows(InvalidRegistryHandleException.class,
                    () -> registryKey.getValue("string", RegistryValue.class));
            assertEquals("HKEY_CURRENT_USER", exception.path());
        }

        @Test
        @DisplayName("wrong value type")
        void testWrongValueType() {
            StringValue stringValue = StringValue.of("string", "value");

            mockValue(WinReg.HKEY_CURRENT_USER, stringValue);

            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER;
            assertThrows(ClassCastException.class, () -> registryKey.getValue("string", DWordValue.class));
        }
    }

    @Nested
    @DisplayName("findValue")
    class FindValue {

        @Test
        @DisplayName("success")
        void testSuccess() {
            StringValue stringValue = StringValue.of("string", "value");

            mockValue(WinReg.HKEY_CURRENT_USER, stringValue);

            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER;
            Optional<StringValue> value = registryKey.findValue("string", StringValue.class);
            assertEquals(Optional.of(stringValue), value);
        }

        @Test
        @DisplayName("non-existing value")
        void testNonExistingValue() {
            advapi32.when(() -> RegQueryValueEx(eq(WinReg.HKEY_CURRENT_USER), notNull(), notNull(), notNull(), isNULL(), notNull()))
                    .thenReturn(WinError.ERROR_FILE_NOT_FOUND);

            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER;
            Optional<DWordValue> value = registryKey.findValue("string", DWordValue.class);
            assertEquals(Optional.empty(), value);
        }

        @Test
        @DisplayName("failure")
        void testFailure() {
            mockValue(WinReg.HKEY_CURRENT_USER, StringValue.of("string", "value"), WinError.ERROR_INVALID_HANDLE);

            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER;
            InvalidRegistryHandleException exception = assertThrows(InvalidRegistryHandleException.class,
                    () -> registryKey.findValue("string", RegistryValue.class));
            assertEquals("HKEY_CURRENT_USER", exception.path());
        }

        @Test
        @DisplayName("wrong value type")
        void testWrongValueType() {
            StringValue stringValue = StringValue.of("string", "value");

            mockValue(WinReg.HKEY_CURRENT_USER, stringValue);

            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER;
            assertThrows(ClassCastException.class, () -> registryKey.findValue("string", DWordValue.class));
        }
    }

    @Nested
    @DisplayName("setValue")
    class SetValue {

        @Test
        @DisplayName("success")
        void testSuccess() {
            StringValue stringValue = StringValue.of("string", "value");
            MemorySegment data = stringValue.rawData(ALLOCATOR);

            advapi32.when(() -> RegSetValueEx(notNull(), eqPointer("string"), anyInt(), eq(WinNT.REG_SZ), eqBytes(data), anyInt()))
                    .thenReturn(WinError.ERROR_SUCCESS);

            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER;
            registryKey.setValue(stringValue);

            advapi32.verify(() -> RegSetValueEx(notNull(), eqPointer("string"), anyInt(), eq(WinNT.REG_SZ), eqBytes(data), eqSize(data)));
        }

        @Test
        @DisplayName("failure")
        void testFailure() {
            StringValue stringValue = StringValue.of("string", "value");

            advapi32.when(() -> RegSetValueEx(notNull(), notNull(), anyInt(), anyInt(), notNull(), anyInt()))
                    .thenReturn(WinError.ERROR_INVALID_HANDLE);

            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER;
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
            advapi32.when(() -> RegDeleteValue(notNull(), eqPointer("string")))
                    .thenReturn(WinError.ERROR_SUCCESS);

            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER;
            registryKey.deleteValue("string");

            advapi32.verify(() -> RegDeleteValue(notNull(), eqPointer("string")));
        }

        @Test
        @DisplayName("failure")
        void testFailure() {
            advapi32.when(() -> RegDeleteValue(notNull(), notNull()))
                    .thenReturn(WinError.ERROR_FILE_NOT_FOUND);

            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER;
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
                advapi32.when(() -> RegDeleteValue(notNull(), eqPointer("string")))
                        .thenReturn(WinError.ERROR_SUCCESS);

                RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER;
                assertTrue(registryKey.deleteValueIfExists("string"));

                advapi32.verify(() -> RegDeleteValue(notNull(), eqPointer("string")));
            }

            @Test
            @DisplayName("value didn't exist")
            void testValueDidntExist() {
                advapi32.when(() -> RegDeleteValue(notNull(), eqPointer("string")))
                        .thenReturn(WinError.ERROR_FILE_NOT_FOUND);

                RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER;
                assertFalse(registryKey.deleteValueIfExists("string"));

                advapi32.verify(() -> RegDeleteValue(notNull(), eqPointer("string")));
            }
        }

        @Test
        @DisplayName("failure")
        void testFailure() {
            advapi32.when(() -> RegDeleteValue(notNull(), notNull()))
                    .thenReturn(WinError.ERROR_INVALID_HANDLE);

            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER;
            InvalidRegistryHandleException exception = assertThrows(InvalidRegistryHandleException.class,
                    () -> registryKey.deleteValueIfExists("string"));
            assertEquals("HKEY_CURRENT_USER", exception.path());
        }
    }

    @Test
    @DisplayName("exists")
    void testExists() {
        assertTrue(REGISTRY.HKEY_CURRENT_USER.exists());
    }

    @Nested
    @DisplayName("ifExists")
    class IfExists {

        @Test
        @DisplayName("with consumer")
        void testWithConsumer() {
            @SuppressWarnings("unchecked")
            Consumer<RegistryKey.Handle> action = mock(Consumer.class);

            REGISTRY.HKEY_CURRENT_USER.ifExists(action);

            try (RegistryKey.Handle handle = REGISTRY.HKEY_CURRENT_USER.handle()) {
                verify(action).accept(handle);
            }
        }

        @Test
        @DisplayName("with function")
        void testWithFunction() {
            Function<RegistryKey.Handle, String> action = RegistryKey.Handle::toString;

            Optional<String> result = REGISTRY.HKEY_CURRENT_USER.ifExists(action);

            try (RegistryKey.Handle handle = REGISTRY.HKEY_CURRENT_USER.handle()) {
                assertEquals(Optional.of(handle.toString()), result);
            }
        }
    }

    @Test
    @DisplayName("isAccessible")
    void testIsAccessible() {
        assertTrue(REGISTRY.HKEY_CURRENT_USER.isAccessible());
    }

    @Nested
    @DisplayName("ifAccessible")
    class IfAccessible {

        @Test
        @DisplayName("with consumer")
        void testWithConsumer() {
            @SuppressWarnings("unchecked")
            Consumer<RegistryKey.Handle> action = mock(Consumer.class);

            REGISTRY.HKEY_CURRENT_USER.ifAccessible(action);

            try (RegistryKey.Handle handle = REGISTRY.HKEY_CURRENT_USER.handle()) {
                verify(action).accept(handle);
            }
        }

        @Test
        @DisplayName("with function")
        void testWithFunction() {
            Function<RegistryKey.Handle, String> action = RegistryKey.Handle::toString;

            Optional<String> result = REGISTRY.HKEY_CURRENT_USER.ifAccessible(action);

            try (RegistryKey.Handle handle = REGISTRY.HKEY_CURRENT_USER.handle()) {
                assertEquals(Optional.of(handle.toString()), result);
            }
        }
    }

    @Test
    @DisplayName("create")
    void testCreate() {
        RegistryKeyAlreadyExistsException exception = assertThrows(RegistryKeyAlreadyExistsException.class, REGISTRY.HKEY_CURRENT_USER::create);
        assertEquals("HKEY_CURRENT_USER", exception.path());
    }

    @Test
    @DisplayName("createIfNotExists")
    void testCreateIfNotExists() {
        assertFalse(REGISTRY.HKEY_CURRENT_USER::createIfNotExists);
    }

    @Nested
    @DisplayName("renameTo")
    class RenameTo {

        @Test
        @DisplayName("valid name")
        void testValidName() {
            assertThrows(UnsupportedOperationException.class, () -> REGISTRY.HKEY_CURRENT_USER.renameTo("foo"));
        }

        @Test
        @DisplayName("invalid name")
        void testInvalidName() {
            assertThrows(UnsupportedOperationException.class, () -> REGISTRY.HKEY_CURRENT_USER.renameTo("\\foo"));
        }
    }

    @Test
    @DisplayName("delete")
    void testDelete() {
        assertThrows(UnsupportedOperationException.class, REGISTRY.HKEY_CURRENT_USER::delete);
    }

    @Test
    @DisplayName("deleteIfExists")
    void testDeleteIfExists() {
        assertThrows(UnsupportedOperationException.class, REGISTRY.HKEY_CURRENT_USER::deleteIfExists);
    }

    @Nested
    @DisplayName("handle")
    class Handle {

        @Test
        @DisplayName("with no arguments")
        void testNoArguments() {
            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER;
            assertDoesNotThrow(() -> {
                try (var _ = registryKey.handle()) {
                    // Do nothing
                }
            });
        }

        @Test
        @DisplayName("with CREATE")
        void testWithCreate() {
            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER;
            assertDoesNotThrow(() -> {
                try (var _ = registryKey.handle(RegistryKey.HandleOption.CREATE)) {
                    // Do nothing
                }
            });
        }

        @Test
        @DisplayName("with MANAGE_VALUES")
        void testWithManageValues() {
            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER;
            assertDoesNotThrow(() -> {
                try (var _ = registryKey.handle(RegistryKey.HandleOption.MANAGE_VALUES)) {
                    // Do nothing
                }
            });
        }

        @Test
        @DisplayName("with CREATE and MANAGE_VALUES")
        void testWithCreateAndManageValues() {
            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER;
            assertDoesNotThrow(() -> {
                try (var _ = registryKey.handle(RegistryKey.HandleOption.CREATE, RegistryKey.HandleOption.MANAGE_VALUES)) {
                    // Do nothing
                }
            });
        }

        @Test
        @DisplayName("close twice")
        void testCloseTwice() {
            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER;
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
    void testEquals(LocalRootKey value, Object other, boolean expected) {
        assertEquals(expected, value.equals(other));
    }

    static Arguments[] equalsArguments() {
        RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER;

        return new Arguments[] {
                arguments(registryKey, registryKey, true),
                arguments(registryKey, registryKey.resolve(""), true),
                arguments(registryKey, registryKey.resolve("test"), false),
                arguments(registryKey, new LocalRootKey(WinReg.HKEY_CURRENT_USER, "HKEY_CURRENT_USER"), false),
                arguments(registryKey, REGISTRY.HKEY_LOCAL_MACHINE, false),
                arguments(registryKey, "foo", false),
                arguments(registryKey, null, false),
        };
    }

    @Test
    @DisplayName("hashCode")
    void testHashCode() {
        RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER;

        assertEquals(registryKey.hashCode(), registryKey.hashCode());
    }
}
