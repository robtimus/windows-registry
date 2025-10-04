/*
 * RemoteRootKeyTest.java
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
import static com.github.robtimus.os.windows.registry.foreign.ForeignTestUtils.ALLOCATOR;
import static com.github.robtimus.os.windows.registry.foreign.ForeignTestUtils.eqBytes;
import static com.github.robtimus.os.windows.registry.foreign.ForeignTestUtils.eqPointer;
import static com.github.robtimus.os.windows.registry.foreign.ForeignTestUtils.eqSize;
import static com.github.robtimus.os.windows.registry.foreign.ForeignTestUtils.isNULL;
import static com.github.robtimus.os.windows.registry.foreign.ForeignTestUtils.notNULL;
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
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import com.github.robtimus.os.windows.registry.foreign.WinError;
import com.github.robtimus.os.windows.registry.foreign.WinNT;
import com.github.robtimus.os.windows.registry.foreign.WinReg;

@SuppressWarnings("nls")
@TestInstance(Lifecycle.PER_CLASS)
class RemoteRootKeyTest extends RegistryKeyTestBase {

    private RemoteRegistry remoteRegistry;
    private RegistryKey remoteRoot;
    private MemorySegment hklmHKey;
    private MemorySegment hkuHKey;
    private MemorySegment rootHKey;

    // Using autoCloseArguments = true will attempt to close arguments after teardown() is called, which means that the actual native API is used.
    // Not closing arguments means they get closed at a later time, which interferes with other tests.
    // Therefore, manually keep a list of AutoCloseable objects that are closed afterwards.
    private final List<AutoCloseable> autoCloseables = new ArrayList<>();

    @Override
    @BeforeEach
    void setup() {
        super.setup();

        hklmHKey = mockConnectAndClose(WinReg.HKEY_LOCAL_MACHINE, "test-machine");
        hkuHKey = mockConnectAndClose(WinReg.HKEY_USERS, "test-machine");
        rootHKey = hklmHKey;

        remoteRegistry = Registry.at("test-machine").connect();
        remoteRoot = remoteRegistry.HKEY_LOCAL_MACHINE;

        autoCloseables.clear();
    }

    @Override
    @AfterEach
    void teardown() {
        // close twice
        remoteRegistry.close();
        remoteRegistry.close();

        verify(RegistryKey.api).RegConnectRegistry(eqPointer("test-machine"), eq(WinReg.HKEY_LOCAL_MACHINE), notNull());
        verify(RegistryKey.api).RegConnectRegistry(eqPointer("test-machine"), eq(WinReg.HKEY_USERS), notNull());
        verify(RegistryKey.api).RegCloseKey(hklmHKey);
        verify(RegistryKey.api).RegCloseKey(hkuHKey);

        verify(RegistryKey.api, never()).RegOpenKeyEx(notNull(), notNull(), anyInt(), anyInt(), notNull());
        verify(RegistryKey.api, never()).RegCreateKeyEx(notNull(), notNull(), anyInt(), notNull(), anyInt(), anyInt(), notNull(), notNull(),
                notNull());
        verify(RegistryKey.api, times(2)).RegCloseKey(notNull());

        autoCloseables.forEach(c -> assertDoesNotThrow(c::close));

        super.teardown();
    }

    @Test
    @DisplayName("name")
    void testName() {
        assertEquals("HKEY_LOCAL_MACHINE", remoteRoot.name());
    }

    @Test
    @DisplayName("path")
    void testPath() {
        assertEquals("HKEY_LOCAL_MACHINE", remoteRoot.path());
    }

    @Test
    @DisplayName("isRoot")
    void testIsRoot() {
        assertTrue(remoteRoot.isRoot());
    }

    @Test
    @DisplayName("root")
    void testRoot() {
        assertSame(remoteRoot, remoteRoot.root());
    }

    @Test
    @DisplayName("parent")
    void testParent() {
        assertEquals(Optional.empty(), remoteRoot.parent());
    }

    @ParameterizedTest(name = "{0} => {1}")
    @CsvSource({
            ", HKEY_LOCAL_MACHINE",
            "., HKEY_LOCAL_MACHINE",
            ".., HKEY_LOCAL_MACHINE",
            "..\\.., HKEY_LOCAL_MACHINE",
            "..\\..\\.., HKEY_LOCAL_MACHINE",
            "..\\..\\..\\.., HKEY_LOCAL_MACHINE",
            "child, HKEY_LOCAL_MACHINE\\child",
            "..\\..\\..\\..\\..\\Something\\..\\..\\Something else\\\\.\\leaf, HKEY_LOCAL_MACHINE\\Something else\\leaf",
            "\\absolute, HKEY_LOCAL_MACHINE\\absolute",
            "child\\, HKEY_LOCAL_MACHINE\\child",
            "\\, HKEY_LOCAL_MACHINE",
            "\\\\\\, HKEY_LOCAL_MACHINE"
    })
    @DisplayName("resolve")
    void testResolve(String relativePath, String expectedPath) {
        RegistryKey resolved = remoteRoot.resolve(relativePath != null ? relativePath : "");
        assertEquals(expectedPath, resolved.path());
    }

    @Nested
    @DisplayName("subKeys")
    class SubKeys {

        @Test
        @DisplayName("success")
        void testSuccess() {
            mockSubKeys(rootHKey, "child1", "child2", "child3");

            RegistryKey registryKey = remoteRoot;
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
            doReturn(WinError.ERROR_FILE_NOT_FOUND).when(RegistryKey.api)
                    .RegQueryInfoKey(eq(rootHKey), notNull(), notNull(), notNull(), notNull(), notNULL(), notNull(), notNull(), notNull(), notNull(),
                            notNull(), notNull());

            RegistryKey registryKey = remoteRoot;
            NoSuchRegistryKeyException exception = assertThrows(NoSuchRegistryKeyException.class, registryKey::subKeys);
            assertEquals("HKEY_LOCAL_MACHINE", exception.path());
            assertEquals("test-machine", exception.machineName());
        }

        @Test
        @DisplayName("enum failure")
        void testEnumFailure() {
            doReturn(WinError.ERROR_SUCCESS).when(RegistryKey.api)
                    .RegQueryInfoKey(eq(rootHKey), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(),
                            notNull(), notNull());

            when(RegistryKey.api.RegEnumKeyEx(eq(rootHKey), eq(0), notNull(), notNull(), notNull(), notNull(), notNull(), notNull()))
                    .thenReturn(WinError.ERROR_FILE_NOT_FOUND);

            RegistryKey registryKey = remoteRoot;
            try (Stream<RegistryKey> stream = registryKey.subKeys()) {
                NoSuchRegistryKeyException exception = assertThrows(NoSuchRegistryKeyException.class, stream::toList);
                assertEquals("HKEY_LOCAL_MACHINE", exception.path());
                assertEquals("test-machine", exception.machineName());
            }
        }
    }

    @Nested
    @DisplayName("traverse")
    class Traverse {

        @Test
        @DisplayName("maxDepth == 0")
        void testMaxDepthIsZero() {
            RegistryKey registryKey = remoteRoot;
            try (Stream<RegistryKey> stream = registryKey.traverse(0)) {
                List<RegistryKey> registryKeys = stream.toList();

                List<RegistryKey> expected = List.of(registryKey);

                assertEquals(expected, registryKeys);
            }

            verify(RegistryKey.api, never()).RegOpenKeyEx(notNull(), notNull(), anyInt(), anyInt(), notNull());
            verify(RegistryKey.api, never()).RegCloseKey(notNull());
        }

        @Nested
        @DisplayName("maxDepth == 1")
        class MaxDepthIsOne {

            @Test
            @DisplayName("subKeys first")
            void testSubKeysFirst() {
                mockSubKeys(rootHKey, "subKey1", "subKey2", "subKey3");

                RegistryKey registryKey = remoteRoot;
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
                mockSubKeys(rootHKey, "subKey1", "subKey2", "subKey3");

                RegistryKey registryKey = remoteRoot;
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
            RegistryKey registryKey = remoteRoot;
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

                mockValues(rootHKey, stringValue, binaryValue, wordValue);

                RegistryKey registryKey = remoteRoot;
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

                mockValues(rootHKey, stringValue, binaryValue, wordValue);

                RegistryKey registryKey = remoteRoot;
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

                mockValues(rootHKey, stringValue, binaryValue, wordValue);

                RegistryKey registryKey = remoteRoot;
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
            doReturn(WinError.ERROR_FILE_NOT_FOUND).when(RegistryKey.api)
                    .RegQueryInfoKey(eq(rootHKey), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNULL(), notNULL(),
                            notNull(), notNull());

            RegistryKey registryKey = remoteRoot;
            NoSuchRegistryKeyException exception = assertThrows(NoSuchRegistryKeyException.class, registryKey::values);
            assertEquals("HKEY_LOCAL_MACHINE", exception.path());
            assertEquals("test-machine", exception.machineName());
        }

        @Test
        @DisplayName("enum failure")
        void testEnumFailure() {
            doReturn(WinError.ERROR_SUCCESS).when(RegistryKey.api)
                    .RegQueryInfoKey(eq(rootHKey), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(),
                            notNull(), notNull());

            when(RegistryKey.api.RegEnumValue(eq(rootHKey), eq(0), notNull(), notNull(), notNull(), notNull(), notNull(), notNull()))
                    .thenReturn(WinError.ERROR_FILE_NOT_FOUND);

            RegistryKey registryKey = remoteRoot;
            try (Stream<RegistryValue> stream = registryKey.values()) {
                NoSuchRegistryKeyException exception = assertThrows(NoSuchRegistryKeyException.class, stream::toList);
                assertEquals("HKEY_LOCAL_MACHINE", exception.path());
                assertEquals("test-machine", exception.machineName());
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

            mockValue(rootHKey, stringValue);

            RegistryKey registryKey = remoteRoot;
            StringValue value = registryKey.getValue("string", StringValue.class);
            assertEquals(stringValue, value);
        }

        @Test
        @DisplayName("non-existing value")
        void testNonExistingValue() {
            doReturn(WinError.ERROR_FILE_NOT_FOUND).when(RegistryKey.api)
                    .RegQueryValueEx(eq(rootHKey), notNull(), notNull(), notNull(), isNULL(), notNull());

            RegistryKey registryKey = remoteRoot;
            NoSuchRegistryValueException exception = assertThrows(NoSuchRegistryValueException.class,
                    () -> registryKey.getValue("string", RegistryValue.class));
            assertEquals("HKEY_LOCAL_MACHINE", exception.path());
            assertEquals("test-machine", exception.machineName());
            assertEquals("string", exception.name());
        }

        @Test
        @DisplayName("failure")
        void testFailure() {
            mockValue(rootHKey, StringValue.of("string", "value"), WinError.ERROR_INVALID_HANDLE);

            RegistryKey registryKey = remoteRoot;
            InvalidRegistryHandleException exception = assertThrows(InvalidRegistryHandleException.class,
                    () -> registryKey.getValue("string", RegistryValue.class));
            assertEquals("HKEY_LOCAL_MACHINE", exception.path());
            assertEquals("test-machine", exception.machineName());
        }

        @Test
        @DisplayName("wrong value type")
        void testWrongValueType() {
            StringValue stringValue = StringValue.of("string", "value");

            mockValue(rootHKey, stringValue);

            RegistryKey registryKey = remoteRoot;
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

            mockValue(rootHKey, stringValue);

            RegistryKey registryKey = remoteRoot;
            Optional<StringValue> value = registryKey.findValue("string", StringValue.class);
            assertEquals(Optional.of(stringValue), value);
        }

        @Test
        @DisplayName("non-existing value")
        void testNonExistingValue() {
            doReturn(WinError.ERROR_FILE_NOT_FOUND).when(RegistryKey.api)
                    .RegQueryValueEx(eq(rootHKey), notNull(), notNull(), notNull(), isNULL(), notNull());

            RegistryKey registryKey = remoteRoot;
            Optional<DWordValue> value = registryKey.findValue("string", DWordValue.class);
            assertEquals(Optional.empty(), value);
        }

        @Test
        @DisplayName("failure")
        void testFailure() {
            mockValue(rootHKey, StringValue.of("string", "value"), WinError.ERROR_INVALID_HANDLE);

            RegistryKey registryKey = remoteRoot;
            InvalidRegistryHandleException exception = assertThrows(InvalidRegistryHandleException.class,
                    () -> registryKey.findValue("string", RegistryValue.class));
            assertEquals("HKEY_LOCAL_MACHINE", exception.path());
            assertEquals("test-machine", exception.machineName());
        }

        @Test
        @DisplayName("wrong value type")
        void testWrongValueType() {
            StringValue stringValue = StringValue.of("string", "value");

            mockValue(rootHKey, stringValue);

            RegistryKey registryKey = remoteRoot;
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

            when(RegistryKey.api.RegSetValueEx(notNull(), eqPointer("string"), anyInt(), eq(WinNT.REG_SZ), eqBytes(data), anyInt()))
                    .thenReturn(WinError.ERROR_SUCCESS);

            RegistryKey registryKey = remoteRoot;
            registryKey.setValue(stringValue);

            verify(RegistryKey.api)
                    .RegSetValueEx(notNull(), eqPointer("string"), anyInt(), eq(WinNT.REG_SZ), eqBytes(data), eqSize(data));
        }

        @Test
        @DisplayName("failure")
        void testFailure() {
            StringValue stringValue = StringValue.of("string", "value");

            when(RegistryKey.api.RegSetValueEx(notNull(), notNull(), anyInt(), anyInt(), notNull(), anyInt()))
                    .thenReturn(WinError.ERROR_INVALID_HANDLE);

            RegistryKey registryKey = remoteRoot;
            InvalidRegistryHandleException exception = assertThrows(InvalidRegistryHandleException.class, () -> registryKey.setValue(stringValue));
            assertEquals("HKEY_LOCAL_MACHINE", exception.path());
            assertEquals("test-machine", exception.machineName());
        }
    }

    @Nested
    @DisplayName("deleteValue")
    class DeleteValue {

        @Test
        @DisplayName("success")
        void testSuccess() {
            doReturn(WinError.ERROR_SUCCESS).when(RegistryKey.api).RegDeleteValue(notNull(), eqPointer("string"));

            RegistryKey registryKey = remoteRoot;
            registryKey.deleteValue("string");

            verify(RegistryKey.api).RegDeleteValue(notNull(), eqPointer("string"));
        }

        @Test
        @DisplayName("failure")
        void testFailure() {
            doReturn(WinError.ERROR_FILE_NOT_FOUND).when(RegistryKey.api).RegDeleteValue(notNull(), notNull());

            RegistryKey registryKey = remoteRoot;
            NoSuchRegistryValueException exception = assertThrows(NoSuchRegistryValueException.class, () -> registryKey.deleteValue("string"));
            assertEquals("HKEY_LOCAL_MACHINE", exception.path());
            assertEquals("test-machine", exception.machineName());
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
                doReturn(WinError.ERROR_SUCCESS).when(RegistryKey.api).RegDeleteValue(notNull(), eqPointer("string"));

                RegistryKey registryKey = remoteRoot;
                assertTrue(registryKey.deleteValueIfExists("string"));

                verify(RegistryKey.api).RegDeleteValue(notNull(), eqPointer("string"));
            }

            @Test
            @DisplayName("value didn't exist")
            void testValueDidntExist() {
                doReturn(WinError.ERROR_FILE_NOT_FOUND).when(RegistryKey.api).RegDeleteValue(notNull(), eqPointer("string"));

                RegistryKey registryKey = remoteRoot;
                assertFalse(registryKey.deleteValueIfExists("string"));

                verify(RegistryKey.api).RegDeleteValue(notNull(), eqPointer("string"));
            }
        }

        @Test
        @DisplayName("failure")
        void testFailure() {
            doReturn(WinError.ERROR_INVALID_HANDLE).when(RegistryKey.api).RegDeleteValue(notNull(), notNull());

            RegistryKey registryKey = remoteRoot;
            InvalidRegistryHandleException exception = assertThrows(InvalidRegistryHandleException.class,
                    () -> registryKey.deleteValueIfExists("string"));
            assertEquals("HKEY_LOCAL_MACHINE", exception.path());
            assertEquals("test-machine", exception.machineName());
        }
    }

    @Nested
    @DisplayName("exists")
    class Exists {

        @Test
        @DisplayName("success")
        void testSuccess() {
            doReturn(WinError.ERROR_SUCCESS).when(RegistryKey.api)
                    .RegQueryInfoKey(eq(rootHKey), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(),
                            notNull(), notNull());

            assertTrue(remoteRoot.exists());

            verify(RegistryKey.api).RegQueryInfoKey(eq(rootHKey), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(),
                    notNull(), notNull(), notNull(), notNull());
        }

        @Test
        @DisplayName("non-existing")
        void testNonExisting() {
            doReturn(WinError.ERROR_FILE_NOT_FOUND).when(RegistryKey.api)
                    .RegQueryInfoKey(eq(rootHKey), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(),
                            notNull(), notNull());

            assertFalse(remoteRoot.exists());

            verify(RegistryKey.api).RegQueryInfoKey(eq(rootHKey), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(),
                    notNull(), notNull(), notNull(), notNull());
        }

        @Test
        @DisplayName("failure")
        void testFailure() {
            doReturn(WinError.ERROR_ACCESS_DENIED).when(RegistryKey.api)
                    .RegQueryInfoKey(eq(rootHKey), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(),
                            notNull(), notNull());

            RegistryAccessDeniedException exception = assertThrows(RegistryAccessDeniedException.class, remoteRoot::exists);
            assertEquals("HKEY_LOCAL_MACHINE", exception.path());
            assertEquals("test-machine", exception.machineName());

            verify(RegistryKey.api).RegQueryInfoKey(eq(rootHKey), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(),
                    notNull(), notNull(), notNull(), notNull());
        }
    }

    @Nested
    @DisplayName("ifExists")
    class IfExists {

        @Nested
        @DisplayName("with consumer")
        class WithConsumer {

            @Test
            @DisplayName("success")
            void testSuccess() {
                doReturn(WinError.ERROR_SUCCESS).when(RegistryKey.api)
                        .RegQueryInfoKey(eq(rootHKey), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(),
                                notNull(), notNull(), notNull());

                @SuppressWarnings("unchecked")
                Consumer<RegistryKey.Handle> action = mock(Consumer.class);

                remoteRoot.ifExists(action);

                verify(RegistryKey.api).RegQueryInfoKey(eq(rootHKey), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(),
                        notNull(), notNull(), notNull(), notNull());

                try (RegistryKey.Handle handle = remoteRoot.handle()) {
                    verify(action).accept(handle);
                }
            }

            @Test
            @DisplayName("non-existing")
            @SuppressWarnings("resource")
            void testNonExisting() {
                doReturn(WinError.ERROR_FILE_NOT_FOUND).when(RegistryKey.api)
                        .RegQueryInfoKey(eq(rootHKey), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(),
                                notNull(), notNull(), notNull());

                @SuppressWarnings("unchecked")
                Consumer<RegistryKey.Handle> action = mock(Consumer.class);

                remoteRoot.ifExists(action);

                verify(action, never()).accept(any());

                verify(RegistryKey.api).RegQueryInfoKey(eq(rootHKey), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(),
                        notNull(), notNull(), notNull(), notNull());
            }

            @Test
            @DisplayName("failure")
            @SuppressWarnings("resource")
            void testFailure() {
                doReturn(WinError.ERROR_ACCESS_DENIED).when(RegistryKey.api)
                        .RegQueryInfoKey(eq(rootHKey), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(),
                                notNull(), notNull(), notNull());

                @SuppressWarnings("unchecked")
                Consumer<RegistryKey.Handle> action = mock(Consumer.class);

                RegistryAccessDeniedException exception = assertThrows(RegistryAccessDeniedException.class, () -> remoteRoot.ifExists(action));
                assertEquals("HKEY_LOCAL_MACHINE", exception.path());
                assertEquals("test-machine", exception.machineName());

                verify(action, never()).accept(any());

                verify(RegistryKey.api).RegQueryInfoKey(eq(rootHKey), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(),
                        notNull(), notNull(), notNull(), notNull());
            }
        }

        @Nested
        @DisplayName("with function")
        class WithFunction {

            @Test
            @DisplayName("success")
            void testSuccess() {
                doReturn(WinError.ERROR_SUCCESS).when(RegistryKey.api)
                        .RegQueryInfoKey(eq(rootHKey), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(),
                                notNull(), notNull(), notNull());

                Function<RegistryKey.Handle, String> action = RegistryKey.Handle::toString;

                Optional<String> result = remoteRoot.ifExists(action);

                verify(RegistryKey.api).RegQueryInfoKey(eq(rootHKey), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(),
                        notNull(), notNull(), notNull(), notNull());

                try (RegistryKey.Handle handle = remoteRoot.handle()) {
                    assertEquals(Optional.of(handle.toString()), result);
                }
            }

            @Test
            @DisplayName("non-existing")
            @SuppressWarnings("resource")
            void testNonExisting() {
                doReturn(WinError.ERROR_FILE_NOT_FOUND).when(RegistryKey.api)
                        .RegQueryInfoKey(eq(rootHKey), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(),
                                notNull(), notNull(), notNull());

                @SuppressWarnings("unchecked")
                Function<RegistryKey.Handle, String> action = mock(Function.class);

                remoteRoot.ifExists(action);

                verify(action, never()).apply(any());

                verify(RegistryKey.api).RegQueryInfoKey(eq(rootHKey), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(),
                        notNull(), notNull(), notNull(), notNull());
            }

            @Test
            @DisplayName("failure")
            @SuppressWarnings("resource")
            void testFailure() {
                doReturn(WinError.ERROR_ACCESS_DENIED).when(RegistryKey.api)
                        .RegQueryInfoKey(eq(rootHKey), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(),
                                notNull(), notNull(), notNull());

                @SuppressWarnings("unchecked")
                Function<RegistryKey.Handle, String> action = mock(Function.class);

                RegistryAccessDeniedException exception = assertThrows(RegistryAccessDeniedException.class, () -> remoteRoot.ifExists(action));
                assertEquals("HKEY_LOCAL_MACHINE", exception.path());
                assertEquals("test-machine", exception.machineName());

                verify(action, never()).apply(any());

                verify(RegistryKey.api).RegQueryInfoKey(eq(rootHKey), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(),
                        notNull(), notNull(), notNull(), notNull());
            }
        }
    }

    @Nested
    @DisplayName("isAccessible")
    class IsAccessible {

        @Test
        @DisplayName("success")
        void testSuccess() {
            doReturn(WinError.ERROR_SUCCESS).when(RegistryKey.api)
                    .RegQueryInfoKey(eq(rootHKey), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(),
                            notNull(), notNull());

            assertTrue(remoteRoot.isAccessible());

            verify(RegistryKey.api).RegQueryInfoKey(eq(rootHKey), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(),
                    notNull(), notNull(), notNull(), notNull());
        }

        @Test
        @DisplayName("non-existing")
        void testNonExisting() {
            doReturn(WinError.ERROR_FILE_NOT_FOUND).when(RegistryKey.api)
                    .RegQueryInfoKey(eq(rootHKey), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(),
                            notNull(), notNull());

            assertFalse(remoteRoot.isAccessible());

            verify(RegistryKey.api).RegQueryInfoKey(eq(rootHKey), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(),
                    notNull(), notNull(), notNull(), notNull());
        }

        @Test
        @DisplayName("access denied")
        void testAccessDenied() {
            doReturn(WinError.ERROR_ACCESS_DENIED).when(RegistryKey.api)
                    .RegQueryInfoKey(eq(rootHKey), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(),
                            notNull(), notNull());

            assertFalse(remoteRoot.isAccessible());

            verify(RegistryKey.api).RegQueryInfoKey(eq(rootHKey), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(),
                    notNull(), notNull(), notNull(), notNull());
        }

        @Test
        @DisplayName("failure")
        void testFailure() {
            doReturn(WinError.ERROR_INVALID_HANDLE).when(RegistryKey.api)
                    .RegQueryInfoKey(eq(rootHKey), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(),
                            notNull(), notNull());

            InvalidRegistryHandleException exception = assertThrows(InvalidRegistryHandleException.class, remoteRoot::isAccessible);
            assertEquals("HKEY_LOCAL_MACHINE", exception.path());
            assertEquals("test-machine", exception.machineName());

            verify(RegistryKey.api).RegQueryInfoKey(eq(rootHKey), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(),
                    notNull(), notNull(), notNull(), notNull());
        }
    }

    @Nested
    @DisplayName("ifAccessible")
    class IfAccessible {

        @Nested
        @DisplayName("with consumer")
        class WithConsumer {

            @Test
            @DisplayName("success")
            void testSuccess() {
                doReturn(WinError.ERROR_SUCCESS).when(RegistryKey.api)
                        .RegQueryInfoKey(eq(rootHKey), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(),
                                notNull(), notNull(), notNull());

                @SuppressWarnings("unchecked")
                Consumer<RegistryKey.Handle> action = mock(Consumer.class);

                remoteRoot.ifAccessible(action);

                verify(RegistryKey.api).RegQueryInfoKey(eq(rootHKey), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(),
                        notNull(), notNull(), notNull(), notNull());

                try (RegistryKey.Handle handle = remoteRoot.handle()) {
                    verify(action).accept(handle);
                }
            }

            @Test
            @DisplayName("non-existing")
            @SuppressWarnings("resource")
            void testNonExisting() {
                doReturn(WinError.ERROR_FILE_NOT_FOUND).when(RegistryKey.api)
                        .RegQueryInfoKey(eq(rootHKey), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(),
                                notNull(), notNull(), notNull());

                @SuppressWarnings("unchecked")
                Consumer<RegistryKey.Handle> action = mock(Consumer.class);

                remoteRoot.ifAccessible(action);

                verify(action, never()).accept(any());

                verify(RegistryKey.api).RegQueryInfoKey(eq(rootHKey), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(),
                        notNull(), notNull(), notNull(), notNull());
            }

            @Test
            @DisplayName("access denied")
            @SuppressWarnings("resource")
            void testAccessDenied() {
                doReturn(WinError.ERROR_ACCESS_DENIED).when(RegistryKey.api)
                        .RegQueryInfoKey(eq(rootHKey), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(),
                                notNull(), notNull(), notNull());

                @SuppressWarnings("unchecked")
                Consumer<RegistryKey.Handle> action = mock(Consumer.class);

                remoteRoot.ifAccessible(action);

                verify(action, never()).accept(any());

                verify(RegistryKey.api).RegQueryInfoKey(eq(rootHKey), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(),
                        notNull(), notNull(), notNull(), notNull());
            }

            @Test
            @DisplayName("failure")
            @SuppressWarnings("resource")
            void testFailure() {
                doReturn(WinError.ERROR_INVALID_HANDLE).when(RegistryKey.api)
                        .RegQueryInfoKey(eq(rootHKey), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(),
                                notNull(), notNull(), notNull());

                @SuppressWarnings("unchecked")
                Consumer<RegistryKey.Handle> action = mock(Consumer.class);

                InvalidRegistryHandleException exception = assertThrows(InvalidRegistryHandleException.class, () -> remoteRoot.ifAccessible(action));
                assertEquals("HKEY_LOCAL_MACHINE", exception.path());
                assertEquals("test-machine", exception.machineName());

                verify(action, never()).accept(any());

                verify(RegistryKey.api).RegQueryInfoKey(eq(rootHKey), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(),
                        notNull(), notNull(), notNull(), notNull());
            }
        }

        @Nested
        @DisplayName("with function")
        class WithFunction {

            @Test
            @DisplayName("success")
            void testSuccess() {
                doReturn(WinError.ERROR_SUCCESS).when(RegistryKey.api)
                        .RegQueryInfoKey(eq(rootHKey), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(),
                                notNull(), notNull(), notNull());

                Function<RegistryKey.Handle, String> action = RegistryKey.Handle::toString;

                Optional<String> result = remoteRoot.ifAccessible(action);

                verify(RegistryKey.api).RegQueryInfoKey(eq(rootHKey), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(),
                        notNull(), notNull(), notNull(), notNull());

                try (RegistryKey.Handle handle = remoteRoot.handle()) {
                    assertEquals(Optional.of(handle.toString()), result);
                }
            }

            @Test
            @DisplayName("non-existing")
            @SuppressWarnings("resource")
            void testNonExisting() {
                doReturn(WinError.ERROR_FILE_NOT_FOUND).when(RegistryKey.api)
                        .RegQueryInfoKey(eq(rootHKey), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(),
                                notNull(), notNull(), notNull());

                @SuppressWarnings("unchecked")
                Function<RegistryKey.Handle, String> action = mock(Function.class);

                Optional<String> result = remoteRoot.ifAccessible(action);

                assertEquals(Optional.empty(), result);

                verify(action, never()).apply(any());

                verify(RegistryKey.api).RegQueryInfoKey(eq(rootHKey), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(),
                        notNull(), notNull(), notNull(), notNull());
            }

            @Test
            @DisplayName("access denied")
            @SuppressWarnings("resource")
            void testAccessDenied() {
                doReturn(WinError.ERROR_ACCESS_DENIED).when(RegistryKey.api)
                        .RegQueryInfoKey(eq(rootHKey), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(),
                                notNull(), notNull(), notNull());

                @SuppressWarnings("unchecked")
                Function<RegistryKey.Handle, String> action = mock(Function.class);

                Optional<String> result = remoteRoot.ifAccessible(action);

                assertEquals(Optional.empty(), result);

                verify(action, never()).apply(any());

                verify(RegistryKey.api).RegQueryInfoKey(eq(rootHKey), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(),
                        notNull(), notNull(), notNull(), notNull());
            }

            @Test
            @DisplayName("failure")
            @SuppressWarnings("resource")
            void testFailure() {
                doReturn(WinError.ERROR_INVALID_HANDLE).when(RegistryKey.api)
                        .RegQueryInfoKey(eq(rootHKey), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(),
                                notNull(), notNull(), notNull());

                @SuppressWarnings("unchecked")
                Function<RegistryKey.Handle, String> action = mock(Function.class);

                InvalidRegistryHandleException exception = assertThrows(InvalidRegistryHandleException.class, () -> remoteRoot.ifAccessible(action));
                assertEquals("HKEY_LOCAL_MACHINE", exception.path());
                assertEquals("test-machine", exception.machineName());

                verify(action, never()).apply(any());

                verify(RegistryKey.api).RegQueryInfoKey(eq(rootHKey), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(),
                        notNull(), notNull(), notNull(), notNull());
            }
        }
    }

    @Test
    @DisplayName("create")
    void testCreate() {
        RegistryKeyAlreadyExistsException exception = assertThrows(RegistryKeyAlreadyExistsException.class, remoteRoot::create);
        assertEquals("HKEY_LOCAL_MACHINE", exception.path());
        assertEquals("test-machine", exception.machineName());

        verify(RegistryKey.api, never()).RegQueryInfoKey(eq(rootHKey), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(),
                notNull(), notNull(), notNull(), notNull());
    }

    @Nested
    @DisplayName("createIfNotExists")
    class CreateIfNotExists {

        @Test
        @DisplayName("success")
        void testSuccess() {
            doReturn(WinError.ERROR_SUCCESS).when(RegistryKey.api)
                    .RegQueryInfoKey(eq(rootHKey), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(),
                            notNull(), notNull());

            assertFalse(remoteRoot::createIfNotExists);

            verify(RegistryKey.api).RegQueryInfoKey(eq(rootHKey), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(),
                    notNull(), notNull(), notNull(), notNull());
        }

        @Test
        @DisplayName("failure")
        void testFailure() {
            doReturn(WinError.ERROR_INVALID_HANDLE).when(RegistryKey.api)
                    .RegQueryInfoKey(eq(rootHKey), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(),
                            notNull(), notNull());

            InvalidRegistryHandleException exception = assertThrows(InvalidRegistryHandleException.class, remoteRoot::createIfNotExists);
            assertEquals("HKEY_LOCAL_MACHINE", exception.path());
            assertEquals("test-machine", exception.machineName());

            verify(RegistryKey.api).RegQueryInfoKey(eq(rootHKey), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(),
                    notNull(), notNull(), notNull(), notNull());
        }
    }

    @Nested
    @DisplayName("renameTo")
    class RenameTo {

        @Test
        @DisplayName("valid name")
        void testValidName() {
            assertThrows(UnsupportedOperationException.class, () -> remoteRoot.renameTo("foo"));
        }

        @Test
        @DisplayName("invalid name")
        void testInvalidName() {
            assertThrows(UnsupportedOperationException.class, () -> remoteRoot.renameTo("\\foo"));
        }
    }

    @Test
    @DisplayName("delete")
    void testDelete() {
        assertThrows(UnsupportedOperationException.class, remoteRoot::delete);
    }

    @Test
    @DisplayName("deleteIfExists")
    void testDeleteIfExists() {
        assertThrows(UnsupportedOperationException.class, remoteRoot::deleteIfExists);
    }

    @Nested
    @DisplayName("handle")
    class Handle {

        @Test
        @DisplayName("with no arguments")
        void testNoArguments() {
            RegistryKey registryKey = remoteRoot;
            assertDoesNotThrow(() -> {
                try (var _ = registryKey.handle()) {
                    // Do nothing
                }
            });
        }

        @Test
        @DisplayName("with CREATE")
        void testWithCreate() {
            RegistryKey registryKey = remoteRoot;
            assertDoesNotThrow(() -> {
                try (var _ = registryKey.handle(RegistryKey.HandleOption.CREATE)) {
                    // Do nothing
                }
            });
        }

        @Test
        @DisplayName("with MANAGE_VALUES")
        void testWithManageValues() {
            RegistryKey registryKey = remoteRoot;
            assertDoesNotThrow(() -> {
                try (var _ = registryKey.handle(RegistryKey.HandleOption.MANAGE_VALUES)) {
                    // Do nothing
                }
            });
        }

        @Test
        @DisplayName("with CREATE and MANAGE_VALUES")
        void testWithCreateAndManageValues() {
            RegistryKey registryKey = remoteRoot;
            assertDoesNotThrow(() -> {
                try (var _ = registryKey.handle(RegistryKey.HandleOption.CREATE, RegistryKey.HandleOption.MANAGE_VALUES)) {
                    // Do nothing
                }
            });
        }

        @Test
        @DisplayName("close twice")
        void testCloseTwice() {
            RegistryKey registryKey = remoteRoot;
            assertDoesNotThrow(() -> {
                try (RegistryKey.Handle handle = registryKey.handle()) {
                    handle.close();
                }
            });
        }

        @Test
        @DisplayName("verify failure")
        void testVerifyFailure() {
            doReturn(WinError.ERROR_INVALID_HANDLE).when(RegistryKey.api)
                    .RegQueryInfoKey(eq(rootHKey), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(),
                            notNull(), notNull());

            InvalidRegistryHandleException exception = assertThrows(InvalidRegistryHandleException.class, remoteRoot::handle);
            assertEquals("HKEY_LOCAL_MACHINE", exception.path());
            assertEquals("test-machine", exception.machineName());
        }
    }

    @ParameterizedTest(name = "{1}", autoCloseArguments = false)
    @MethodSource("equalsArguments")
    @DisplayName("equals")
    void testEquals(RemoteRootKey value, Object other, boolean expected) {
        assertEquals(expected, value.equals(other));
        if (other instanceof AutoCloseable closeable) {
            autoCloseables.add(closeable);
        }
    }

    @SuppressWarnings("resource")
    Arguments[] equalsArguments() {
        RegistryKey registryKey = remoteRoot;

        return new Arguments[] {
                arguments(registryKey, registryKey, true),
                arguments(registryKey, new RemoteRootKey("test-machine", LocalRootKey.HKEY_LOCAL_MACHINE, rootHKey), true),
                arguments(registryKey, registryKey.resolve(""), true),
                arguments(registryKey, registryKey.resolve("test"), false),
                arguments(registryKey, new RemoteRootKey("test-machine2", LocalRootKey.HKEY_LOCAL_MACHINE, rootHKey), false),
                arguments(registryKey, new RemoteRootKey("test-machine", LocalRootKey.HKEY_CURRENT_USER, rootHKey), false),
                arguments(registryKey, Registry.local().HKEY_LOCAL_MACHINE, false),
                arguments(registryKey, "foo", false),
                arguments(registryKey, null, false),
        };
    }

    @Test
    @DisplayName("hashCode")
    void testHashCode() {
        RegistryKey registryKey = remoteRoot;

        assertEquals(registryKey.hashCode(), registryKey.hashCode());
    }

    @Test
    @DisplayName("close twice")
    void testCloseTwice() {
        // The second time is called by teardown
        assertDoesNotThrow(remoteRegistry::close);
    }
}
