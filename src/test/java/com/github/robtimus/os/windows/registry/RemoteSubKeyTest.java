/*
 * RemoteSubKeyTest.java
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
import static com.github.robtimus.os.windows.registry.foreign.ForeignTestUtils.newHKEY;
import static com.github.robtimus.os.windows.registry.foreign.ForeignTestUtils.setHKEY;
import static com.github.robtimus.os.windows.registry.foreign.ForeignUtils.setInt;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayContaining;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.AdditionalMatchers.not;
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
import java.util.Arrays;
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
class RemoteSubKeyTest extends RegistryKeyTestBase {

    private RemoteRegistryKey remoteRoot;
    private MemorySegment rootHKey;

    @Override
    @BeforeEach
    void setup() {
        super.setup();

        rootHKey = mockConnectAndClose(WinReg.HKEY_LOCAL_MACHINE, "test-machine");

        remoteRoot = RemoteRegistryKey.HKEY_LOCAL_MACHINE.at("test-machine");
    }

    @Override
    @AfterEach
    void teardown() {
        remoteRoot.close();

        verify(RegistryKey.api).RegConnectRegistry(eqPointer("test-machine"), eq(WinReg.HKEY_LOCAL_MACHINE), notNull());
        verify(RegistryKey.api).RegCloseKey(rootHKey);

        super.teardown();
    }

    @Test
    @DisplayName("name")
    void testName() {
        RegistryKey registryKey = remoteRoot.resolve("Software\\JavaSoft\\Prefs");
        assertEquals("Prefs", registryKey.name());
    }

    @Test
    @DisplayName("path")
    void testPath() {
        RegistryKey registryKey = remoteRoot.resolve("Software\\JavaSoft\\Prefs");
        assertEquals("HKEY_LOCAL_MACHINE\\Software\\JavaSoft\\Prefs", registryKey.path());
    }

    @Test
    @DisplayName("isRoot")
    void testIsRoot() {
        RegistryKey registryKey = remoteRoot.resolve("Software\\JavaSoft\\Prefs");
        assertFalse(registryKey.isRoot());
    }

    @Test
    @DisplayName("root")
    void testRoot() {
        RegistryKey registryKey = remoteRoot.resolve("Software\\JavaSoft\\Prefs");
        assertSame(remoteRoot, registryKey.root());
    }

    @Test
    @DisplayName("parent")
    void testParent() {
        RegistryKey registryKey = remoteRoot.resolve("Software\\JavaSoft\\Prefs");

        Optional<RegistryKey> parent = registryKey.parent();
        assertEquals(Optional.of("HKEY_LOCAL_MACHINE\\Software\\JavaSoft"), parent.map(RegistryKey::path));

        parent = parent.flatMap(RegistryKey::parent);
        assertEquals(Optional.of("HKEY_LOCAL_MACHINE\\Software"), parent.map(RegistryKey::path));

        parent = parent.flatMap(RegistryKey::parent);
        assertEquals(Optional.of(remoteRoot), parent);
    }

    @ParameterizedTest(name = "{0} => {1}")
    @CsvSource({
            ", HKEY_LOCAL_MACHINE\\Software\\JavaSoft\\Prefs",
            "., HKEY_LOCAL_MACHINE\\Software\\JavaSoft\\Prefs",
            ".., HKEY_LOCAL_MACHINE\\Software\\JavaSoft",
            "..\\.., HKEY_LOCAL_MACHINE\\Software",
            "..\\..\\.., HKEY_LOCAL_MACHINE",
            "..\\..\\..\\.., HKEY_LOCAL_MACHINE",
            "child, HKEY_LOCAL_MACHINE\\Software\\JavaSoft\\Prefs\\child",
            "..\\..\\..\\..\\..\\Something\\..\\..\\Something else\\\\.\\leaf, HKEY_LOCAL_MACHINE\\Something else\\leaf",
            "\\absolute, HKEY_LOCAL_MACHINE\\absolute",
            "child\\, HKEY_LOCAL_MACHINE\\Software\\JavaSoft\\Prefs\\child",
            "\\, HKEY_LOCAL_MACHINE",
            "\\\\\\, HKEY_LOCAL_MACHINE"
    })
    @DisplayName("resolve")
    void testResolve(String relativePath, String expectedPath) {
        RegistryKey registryKey = remoteRoot.resolve("Software\\JavaSoft\\Prefs");
        RegistryKey resolved = registryKey.resolve(relativePath != null ? relativePath : "");
        assertEquals(expectedPath, resolved.path());
    }

    @Nested
    @DisplayName("subKeys")
    class SubKeys {

        @Test
        @DisplayName("success")
        void testSuccess() {
            MemorySegment hKey = mockOpenAndClose(rootHKey, "Software\\JavaSoft\\Prefs");

            mockSubKeys(hKey, "child1", "child2", "child3");

            RegistryKey registryKey = remoteRoot.resolve("Software\\JavaSoft\\Prefs");
            try (Stream<RegistryKey> stream = registryKey.subKeys()) {
                List<RegistryKey> subKeys = stream.toList();

                List<RegistryKey> expected = List.of(
                        registryKey.resolve("child1"),
                        registryKey.resolve("child2"),
                        registryKey.resolve("child3")
                );

                assertEquals(expected, subKeys);
            }

            verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eqPointer("Software\\JavaSoft\\Prefs"), anyInt(), anyInt(), notNull());
            verify(RegistryKey.api).RegCloseKey(hKey);
        }

        @Test
        @DisplayName("non-existing key")
        void testNonExistingKey() {
            mockOpenFailure(rootHKey, "path\\non-existing", WinError.ERROR_FILE_NOT_FOUND);

            RegistryKey registryKey = remoteRoot.resolve("path\\non-existing");
            NoSuchRegistryKeyException exception = assertThrows(NoSuchRegistryKeyException.class, registryKey::subKeys);
            assertEquals("HKEY_LOCAL_MACHINE\\path\\non-existing", exception.path());
            assertEquals("test-machine", exception.machineName());

            verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eqPointer("path\\non-existing"), anyInt(), anyInt(), notNull());
            verify(RegistryKey.api, never()).RegCloseKey(notNull());
        }

        @Nested
        @DisplayName("query failure")
        class QueryFailure {

            @Test
            @DisplayName("with successful close")
            void testSuccessfulClose() {
                MemorySegment hKey = mockOpenAndClose(rootHKey, "path\\failure");

                when(RegistryKey.api.RegQueryInfoKey(eq(hKey), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(),
                        notNull(), notNull(), notNull()))
                        .thenReturn(WinError.ERROR_FILE_NOT_FOUND);

                RegistryKey registryKey = remoteRoot.resolve("path\\failure");
                NoSuchRegistryKeyException exception = assertThrows(NoSuchRegistryKeyException.class, registryKey::subKeys);
                assertEquals("HKEY_LOCAL_MACHINE\\path\\failure", exception.path());
                assertEquals("test-machine", exception.machineName());

                verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eqPointer("path\\failure"), anyInt(), anyInt(), notNull());
                verify(RegistryKey.api).RegCloseKey(hKey);
            }

            @Test
            @DisplayName("with close failure")
            void testCloseFailure() {
                MemorySegment hKey = mockOpen(rootHKey, "path\\failure");

                mockClose(hKey, WinError.ERROR_INVALID_HANDLE);

                when(RegistryKey.api.RegQueryInfoKey(eq(hKey), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(),
                        notNull(), notNull(), notNull()))
                        .thenReturn(WinError.ERROR_FILE_NOT_FOUND);

                RegistryKey registryKey = remoteRoot.resolve("path\\failure");
                NoSuchRegistryKeyException exception = assertThrows(NoSuchRegistryKeyException.class, registryKey::subKeys);
                assertEquals("HKEY_LOCAL_MACHINE\\path\\failure", exception.path());
                assertEquals("test-machine", exception.machineName());
                assertThat(exception.getSuppressed(), arrayContaining(instanceOf(InvalidRegistryHandleException.class)));

                verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eqPointer("path\\failure"), anyInt(), anyInt(), notNull());
                verify(RegistryKey.api).RegCloseKey(hKey);
            }
        }

        @Test
        @DisplayName("enum failure")
        void testEnumFailure() {
            MemorySegment hKey = mockOpenAndClose(rootHKey, "path\\failure");

            when(RegistryKey.api.RegQueryInfoKey(eq(hKey), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(),
                    notNull(), notNull(), notNull()))
                    .thenReturn(WinError.ERROR_SUCCESS);

            when(RegistryKey.api.RegEnumKeyEx(eq(hKey), eq(0), notNull(), notNull(), notNull(), notNull(), notNull(), notNull()))
                    .thenReturn(WinError.ERROR_FILE_NOT_FOUND);

            RegistryKey registryKey = remoteRoot.resolve("path\\failure");
            try (Stream<RegistryKey> stream = registryKey.subKeys()) {
                NoSuchRegistryKeyException exception = assertThrows(NoSuchRegistryKeyException.class, stream::toList);
                assertEquals("HKEY_LOCAL_MACHINE\\path\\failure", exception.path());
                assertEquals("test-machine", exception.machineName());
            }

            verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eqPointer("path\\failure"), anyInt(), anyInt(), notNull());
            verify(RegistryKey.api).RegCloseKey(hKey);
        }
    }

    @Nested
    @DisplayName("traverse")
    class Traverse {

        @Test
        @DisplayName("maxDepth == 0")
        void testMaxDepthIsZero() {
            RegistryKey registryKey = remoteRoot.resolve("path");
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
                MemorySegment hKey = mockOpenAndClose(rootHKey, "path");

                mockSubKeys(hKey, "subKey1", "subKey2", "subKey3");

                RegistryKey registryKey = remoteRoot.resolve("path");
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

                verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eqPointer("path"), anyInt(), anyInt(), notNull());
                verify(RegistryKey.api).RegOpenKeyEx(notNull(), notNull(), anyInt(), anyInt(), notNull());
                verify(RegistryKey.api).RegCloseKey(hKey);
                verify(RegistryKey.api).RegCloseKey(notNull());
            }

            @Test
            @DisplayName("subKeys not first")
            void testSubKeysNotFirst() {
                MemorySegment hKey = mockOpenAndClose(rootHKey, "path");

                mockSubKeys(hKey, "subKey1", "subKey2", "subKey3");

                RegistryKey registryKey = remoteRoot.resolve("path");
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

                verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eqPointer("path"), anyInt(), anyInt(), notNull());
                verify(RegistryKey.api).RegOpenKeyEx(notNull(), notNull(), anyInt(), anyInt(), notNull());
                verify(RegistryKey.api).RegCloseKey(hKey);
                verify(RegistryKey.api).RegCloseKey(notNull());
            }
        }

        @Nested
        @DisplayName("no maxDepth")
        class NoMaxDepth {

            @Test
            @DisplayName("subKeys first")
            void testSubKeysFirst() {
                MemorySegment hKey = mockOpenAndClose(rootHKey, "path");
                MemorySegment subKey1 = mockOpenAndClose(rootHKey, "path\\subKey1");
                MemorySegment subKey2 = mockOpenAndClose(rootHKey, "path\\subKey2");
                MemorySegment subKey3 = mockOpenAndClose(rootHKey, "path\\subKey3");
                MemorySegment subKey11 = mockOpenAndClose(rootHKey, "path\\subKey1\\subKey11");
                MemorySegment subKey12 = mockOpenAndClose(rootHKey, "path\\subKey1\\subKey12");
                MemorySegment subKey13 = mockOpenAndClose(rootHKey, "path\\subKey1\\subKey13");
                MemorySegment subKey21 = mockOpenAndClose(rootHKey, "path\\subKey2\\subKey21");
                MemorySegment subKey22 = mockOpenAndClose(rootHKey, "path\\subKey2\\subKey22");
                MemorySegment subKey23 = mockOpenAndClose(rootHKey, "path\\subKey2\\subKey23");
                MemorySegment subKey31 = mockOpenAndClose(rootHKey, "path\\subKey3\\subKey31");
                MemorySegment subKey32 = mockOpenAndClose(rootHKey, "path\\subKey3\\subKey32");
                MemorySegment subKey33 = mockOpenAndClose(rootHKey, "path\\subKey3\\subKey33");

                mockSubKeys(hKey, "subKey1", "subKey2", "subKey3");
                mockSubKeys(subKey1, "subKey11", "subKey12", "subKey13");
                mockSubKeys(subKey2, "subKey21", "subKey22", "subKey23");
                mockSubKeys(subKey3, "subKey31", "subKey32", "subKey33");
                mockSubKeys(subKey11);
                mockSubKeys(subKey12);
                mockSubKeys(subKey13);
                mockSubKeys(subKey21);
                mockSubKeys(subKey22);
                mockSubKeys(subKey23);
                mockSubKeys(subKey31);
                mockSubKeys(subKey32);
                mockSubKeys(subKey33);

                RegistryKey registryKey = remoteRoot.resolve("path");
                try (Stream<RegistryKey> stream = registryKey.traverse(RegistryKey.TraverseOption.SUB_KEYS_FIRST)) {
                    List<RegistryKey> registryKeys = stream.toList();

                    List<RegistryKey> expected = List.of(
                            registryKey.resolve("subKey1\\subKey11"),
                            registryKey.resolve("subKey1\\subKey12"),
                            registryKey.resolve("subKey1\\subKey13"),
                            registryKey.resolve("subKey1"),
                            registryKey.resolve("subKey2\\subKey21"),
                            registryKey.resolve("subKey2\\subKey22"),
                            registryKey.resolve("subKey2\\subKey23"),
                            registryKey.resolve("subKey2"),
                            registryKey.resolve("subKey3\\subKey31"),
                            registryKey.resolve("subKey3\\subKey32"),
                            registryKey.resolve("subKey3\\subKey33"),
                            registryKey.resolve("subKey3"),
                            registryKey
                    );

                    assertEquals(expected, registryKeys);
                }

                verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eqPointer("path"), anyInt(), anyInt(), notNull());
                verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eqPointer("path\\subKey1"), anyInt(), anyInt(), notNull());
                verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eqPointer("path\\subKey2"), anyInt(), anyInt(), notNull());
                verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eqPointer("path\\subKey3"), anyInt(), anyInt(), notNull());
                verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eqPointer("path\\subKey1\\subKey11"), anyInt(), anyInt(), notNull());
                verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eqPointer("path\\subKey1\\subKey12"), anyInt(), anyInt(), notNull());
                verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eqPointer("path\\subKey1\\subKey13"), anyInt(), anyInt(), notNull());
                verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eqPointer("path\\subKey2\\subKey21"), anyInt(), anyInt(), notNull());
                verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eqPointer("path\\subKey2\\subKey22"), anyInt(), anyInt(), notNull());
                verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eqPointer("path\\subKey2\\subKey23"), anyInt(), anyInt(), notNull());
                verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eqPointer("path\\subKey3\\subKey31"), anyInt(), anyInt(), notNull());
                verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eqPointer("path\\subKey3\\subKey32"), anyInt(), anyInt(), notNull());
                verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eqPointer("path\\subKey3\\subKey33"), anyInt(), anyInt(), notNull());
                verify(RegistryKey.api, times(13)).RegOpenKeyEx(notNull(), notNull(), anyInt(), anyInt(), notNull());
                verify(RegistryKey.api).RegCloseKey(hKey);
                verify(RegistryKey.api).RegCloseKey(subKey1);
                verify(RegistryKey.api).RegCloseKey(subKey2);
                verify(RegistryKey.api).RegCloseKey(subKey3);
                verify(RegistryKey.api).RegCloseKey(subKey11);
                verify(RegistryKey.api).RegCloseKey(subKey12);
                verify(RegistryKey.api).RegCloseKey(subKey13);
                verify(RegistryKey.api).RegCloseKey(subKey21);
                verify(RegistryKey.api).RegCloseKey(subKey22);
                verify(RegistryKey.api).RegCloseKey(subKey23);
                verify(RegistryKey.api).RegCloseKey(subKey31);
                verify(RegistryKey.api).RegCloseKey(subKey32);
                verify(RegistryKey.api).RegCloseKey(subKey33);
                verify(RegistryKey.api, times(13)).RegCloseKey(notNull());
            }

            @Test
            @DisplayName("subKeys not first")
            void testSubKeysNotFirst() {
                MemorySegment hKey = mockOpenAndClose(rootHKey, "path");
                MemorySegment subKey1 = mockOpenAndClose(rootHKey, "path\\subKey1");
                MemorySegment subKey2 = mockOpenAndClose(rootHKey, "path\\subKey2");
                MemorySegment subKey3 = mockOpenAndClose(rootHKey, "path\\subKey3");
                MemorySegment subKey11 = mockOpenAndClose(rootHKey, "path\\subKey1\\subKey11");
                MemorySegment subKey12 = mockOpenAndClose(rootHKey, "path\\subKey1\\subKey12");
                MemorySegment subKey13 = mockOpenAndClose(rootHKey, "path\\subKey1\\subKey13");
                MemorySegment subKey21 = mockOpenAndClose(rootHKey, "path\\subKey2\\subKey21");
                MemorySegment subKey22 = mockOpenAndClose(rootHKey, "path\\subKey2\\subKey22");
                MemorySegment subKey23 = mockOpenAndClose(rootHKey, "path\\subKey2\\subKey23");
                MemorySegment subKey31 = mockOpenAndClose(rootHKey, "path\\subKey3\\subKey31");
                MemorySegment subKey32 = mockOpenAndClose(rootHKey, "path\\subKey3\\subKey32");
                MemorySegment subKey33 = mockOpenAndClose(rootHKey, "path\\subKey3\\subKey33");

                mockSubKeys(hKey, "subKey1", "subKey2", "subKey3");
                mockSubKeys(subKey1, "subKey11", "subKey12", "subKey13");
                mockSubKeys(subKey2, "subKey21", "subKey22", "subKey23");
                mockSubKeys(subKey3, "subKey31", "subKey32", "subKey33");
                mockSubKeys(subKey11);
                mockSubKeys(subKey12);
                mockSubKeys(subKey13);
                mockSubKeys(subKey21);
                mockSubKeys(subKey22);
                mockSubKeys(subKey23);
                mockSubKeys(subKey31);
                mockSubKeys(subKey32);
                mockSubKeys(subKey33);

                RegistryKey registryKey = remoteRoot.resolve("path");
                try (Stream<RegistryKey> stream = registryKey.traverse(Integer.MAX_VALUE)) {
                    List<RegistryKey> registryKeys = stream.toList();

                    List<RegistryKey> expected = List.of(
                            registryKey,
                            registryKey.resolve("subKey1"),
                            registryKey.resolve("subKey1\\subKey11"),
                            registryKey.resolve("subKey1\\subKey12"),
                            registryKey.resolve("subKey1\\subKey13"),
                            registryKey.resolve("subKey2"),
                            registryKey.resolve("subKey2\\subKey21"),
                            registryKey.resolve("subKey2\\subKey22"),
                            registryKey.resolve("subKey2\\subKey23"),
                            registryKey.resolve("subKey3"),
                            registryKey.resolve("subKey3\\subKey31"),
                            registryKey.resolve("subKey3\\subKey32"),
                            registryKey.resolve("subKey3\\subKey33")
                    );

                    assertEquals(expected, registryKeys);
                }

                verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eqPointer("path"), anyInt(), anyInt(), notNull());
                verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eqPointer("path\\subKey1"), anyInt(), anyInt(), notNull());
                verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eqPointer("path\\subKey2"), anyInt(), anyInt(), notNull());
                verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eqPointer("path\\subKey3"), anyInt(), anyInt(), notNull());
                verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eqPointer("path\\subKey1\\subKey11"), anyInt(), anyInt(), notNull());
                verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eqPointer("path\\subKey1\\subKey12"), anyInt(), anyInt(), notNull());
                verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eqPointer("path\\subKey1\\subKey13"), anyInt(), anyInt(), notNull());
                verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eqPointer("path\\subKey2\\subKey21"), anyInt(), anyInt(), notNull());
                verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eqPointer("path\\subKey2\\subKey22"), anyInt(), anyInt(), notNull());
                verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eqPointer("path\\subKey2\\subKey23"), anyInt(), anyInt(), notNull());
                verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eqPointer("path\\subKey3\\subKey31"), anyInt(), anyInt(), notNull());
                verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eqPointer("path\\subKey3\\subKey32"), anyInt(), anyInt(), notNull());
                verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eqPointer("path\\subKey3\\subKey33"), anyInt(), anyInt(), notNull());
                verify(RegistryKey.api, times(13)).RegOpenKeyEx(notNull(), notNull(), anyInt(), anyInt(), notNull());
                verify(RegistryKey.api).RegCloseKey(hKey);
                verify(RegistryKey.api).RegCloseKey(subKey1);
                verify(RegistryKey.api).RegCloseKey(subKey2);
                verify(RegistryKey.api).RegCloseKey(subKey3);
                verify(RegistryKey.api).RegCloseKey(subKey11);
                verify(RegistryKey.api).RegCloseKey(subKey12);
                verify(RegistryKey.api).RegCloseKey(subKey13);
                verify(RegistryKey.api).RegCloseKey(subKey21);
                verify(RegistryKey.api).RegCloseKey(subKey22);
                verify(RegistryKey.api).RegCloseKey(subKey23);
                verify(RegistryKey.api).RegCloseKey(subKey31);
                verify(RegistryKey.api).RegCloseKey(subKey32);
                verify(RegistryKey.api).RegCloseKey(subKey33);
                verify(RegistryKey.api, times(13)).RegCloseKey(notNull());
            }
        }

        @Test
        @DisplayName("negative maxDepth")
        void testNegativeMaxDepth() {
            RegistryKey registryKey = remoteRoot.resolve("path");
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

                MemorySegment hKey = mockOpenAndClose(rootHKey, "Software\\JavaSoft\\Prefs");

                mockValues(hKey, stringValue, binaryValue, wordValue);

                RegistryKey registryKey = remoteRoot.resolve("Software\\JavaSoft\\Prefs");
                try (Stream<RegistryValue> stream = registryKey.values()) {
                    List<RegistryValue> values = stream.toList();

                    List<RegistryValue> expected = List.of(stringValue, binaryValue, wordValue);

                    assertEquals(expected, values);
                }

                verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eqPointer("Software\\JavaSoft\\Prefs"), anyInt(), anyInt(), notNull());
                verify(RegistryKey.api).RegCloseKey(hKey);
            }

            @Test
            @DisplayName("with name filter")
            void testWithNameFilter() {
                StringValue stringValue = StringValue.of("string", "value");
                BinaryValue binaryValue = BinaryValue.of("binary", randomData());
                DWordValue wordValue = DWordValue.of("dword", 13);

                MemorySegment hKey = mockOpenAndClose(rootHKey, "Software\\JavaSoft\\Prefs");

                mockValues(hKey, stringValue, binaryValue, wordValue);

                RegistryKey registryKey = remoteRoot.resolve("Software\\JavaSoft\\Prefs");
                RegistryValue.Filter filter = RegistryValue.filter().name(s -> s.contains("i"));
                try (Stream<RegistryValue> stream = registryKey.values(filter)) {
                    List<RegistryValue> values = stream.toList();

                    List<RegistryValue> expected = List.of(stringValue, binaryValue);

                    assertEquals(expected, values);
                }

                verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eqPointer("Software\\JavaSoft\\Prefs"), anyInt(), anyInt(), notNull());
                verify(RegistryKey.api).RegCloseKey(hKey);
            }

            @Test
            @DisplayName("with type filter")
            void testWithTypeFilter() {
                StringValue stringValue = StringValue.of("string", "value");
                BinaryValue binaryValue = BinaryValue.of("binary", randomData());
                DWordValue wordValue = DWordValue.of("dword", 13);

                MemorySegment hKey = mockOpenAndClose(rootHKey, "Software\\JavaSoft\\Prefs");

                mockValues(hKey, stringValue, binaryValue, wordValue);

                RegistryKey registryKey = remoteRoot.resolve("Software\\JavaSoft\\Prefs");
                RegistryValue.Filter filter = RegistryValue.filter().strings().words();
                try (Stream<RegistryValue> stream = registryKey.values(filter)) {
                    List<RegistryValue> values = stream.toList();

                    List<RegistryValue> expected = List.of(stringValue, wordValue);

                    assertEquals(expected, values);
                }

                verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eqPointer("Software\\JavaSoft\\Prefs"), anyInt(), anyInt(), notNull());
                verify(RegistryKey.api).RegCloseKey(hKey);
            }
        }

        @Test
        @DisplayName("non-existing key")
        void testNonExistingKey() {
            mockOpenFailure(rootHKey, "path\\non-existing", WinError.ERROR_FILE_NOT_FOUND);

            RegistryKey registryKey = remoteRoot.resolve("path\\non-existing");
            NoSuchRegistryKeyException exception = assertThrows(NoSuchRegistryKeyException.class, registryKey::values);
            assertEquals("HKEY_LOCAL_MACHINE\\path\\non-existing", exception.path());
            assertEquals("test-machine", exception.machineName());

            verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eqPointer("path\\non-existing"), anyInt(), anyInt(), notNull());
            verify(RegistryKey.api, never()).RegCloseKey(notNull());
        }

        @Nested
        @DisplayName("query failure")
        class QueryFailure {

            @Nested
            @DisplayName("without filter")
            class WithoutFilter {

                @Test
                @DisplayName("with successful close")
                void testSuccessfulClose() {
                    MemorySegment hKey = mockOpenAndClose(rootHKey, "path\\failure");

                    when(RegistryKey.api.RegQueryInfoKey(eq(hKey), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(),
                            notNull(), notNull(), notNull(), notNull()))
                            .thenReturn(WinError.ERROR_FILE_NOT_FOUND);

                    RegistryKey registryKey = remoteRoot.resolve("path\\failure");
                    NoSuchRegistryKeyException exception = assertThrows(NoSuchRegistryKeyException.class, registryKey::values);
                    assertEquals("HKEY_LOCAL_MACHINE\\path\\failure", exception.path());
                    assertEquals("test-machine", exception.machineName());

                    verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eqPointer("path\\failure"), anyInt(), anyInt(), notNull());
                    verify(RegistryKey.api).RegCloseKey(hKey);
                }

                @Test
                @DisplayName("with close failure")
                void testCloseFailure() {
                    MemorySegment hKey = mockOpen(rootHKey, "path\\failure");

                    mockClose(hKey, WinError.ERROR_INVALID_HANDLE);

                    when(RegistryKey.api.RegQueryInfoKey(eq(hKey), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(),
                            notNull(), notNull(), notNull(), notNull()))
                            .thenReturn(WinError.ERROR_FILE_NOT_FOUND);

                    RegistryKey registryKey = remoteRoot.resolve("path\\failure");
                    NoSuchRegistryKeyException exception = assertThrows(NoSuchRegistryKeyException.class, registryKey::values);
                    assertEquals("HKEY_LOCAL_MACHINE\\path\\failure", exception.path());
                    assertEquals("test-machine", exception.machineName());
                    assertThat(exception.getSuppressed(), arrayContaining(instanceOf(InvalidRegistryHandleException.class)));

                    verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eqPointer("path\\failure"), anyInt(), anyInt(), notNull());
                    verify(RegistryKey.api).RegCloseKey(hKey);
                }
            }

            @Nested
            @DisplayName("with filter")
            class WithFilter {

                @Test
                @DisplayName("with successful close")
                void testSuccessfulClose() {
                    MemorySegment hKey = mockOpenAndClose(rootHKey, "path\\failure");

                    when(RegistryKey.api.RegQueryInfoKey(eq(hKey), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(),
                            notNull(), notNull(), notNull(), notNull()))
                            .thenReturn(WinError.ERROR_FILE_NOT_FOUND);

                    RegistryKey registryKey = remoteRoot.resolve("path\\failure");
                    RegistryValue.Filter filter = RegistryValue.filter().strings();
                    NoSuchRegistryKeyException exception = assertThrows(NoSuchRegistryKeyException.class, () -> registryKey.values(filter));
                    assertEquals("HKEY_LOCAL_MACHINE\\path\\failure", exception.path());
                    assertEquals("test-machine", exception.machineName());

                    verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eqPointer("path\\failure"), anyInt(), anyInt(), notNull());
                    verify(RegistryKey.api).RegCloseKey(hKey);
                }

                @Test
                @DisplayName("with close failure")
                void testCloseFailure() {
                    MemorySegment hKey = mockOpen(rootHKey, "path\\failure");

                    mockClose(hKey, WinError.ERROR_INVALID_HANDLE);

                    when(RegistryKey.api.RegQueryInfoKey(eq(hKey), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(),
                            notNull(), notNull(), notNull(), notNull()))
                            .thenReturn(WinError.ERROR_FILE_NOT_FOUND);

                    RegistryKey registryKey = remoteRoot.resolve("path\\failure");
                    RegistryValue.Filter filter = RegistryValue.filter().strings();
                    NoSuchRegistryKeyException exception = assertThrows(NoSuchRegistryKeyException.class, () -> registryKey.values(filter));
                    assertEquals("HKEY_LOCAL_MACHINE\\path\\failure", exception.path());
                    assertEquals("test-machine", exception.machineName());
                    assertThat(exception.getSuppressed(), arrayContaining(instanceOf(InvalidRegistryHandleException.class)));

                    verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eqPointer("path\\failure"), anyInt(), anyInt(), notNull());
                    verify(RegistryKey.api).RegCloseKey(hKey);
                }
            }
        }

        @Test
        @DisplayName("enum failure")
        void testEnumFailure() {
            MemorySegment hKey = mockOpenAndClose(rootHKey, "path\\failure");

            when(RegistryKey.api.RegQueryInfoKey(eq(hKey), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(),
                    notNull(), notNull(), notNull()))
                    .thenReturn(WinError.ERROR_SUCCESS);

            when(RegistryKey.api.RegEnumValue(eq(hKey), eq(0), notNull(), notNull(), notNull(), notNull(), notNull(), notNull()))
                    .thenReturn(WinError.ERROR_FILE_NOT_FOUND);

            RegistryKey registryKey = remoteRoot.resolve("path\\failure");
            try (Stream<RegistryValue> stream = registryKey.values()) {
                NoSuchRegistryKeyException exception = assertThrows(NoSuchRegistryKeyException.class, stream::toList);
                assertEquals("HKEY_LOCAL_MACHINE\\path\\failure", exception.path());
                assertEquals("test-machine", exception.machineName());
            }

            verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eqPointer("path\\failure"), anyInt(), anyInt(), notNull());
            verify(RegistryKey.api).RegCloseKey(hKey);
        }
    }

    @Nested
    @DisplayName("getValue")
    class GetValue {

        @Test
        @DisplayName("success")
        void testSuccess() {
            StringValue stringValue = StringValue.of("string", "value");

            MemorySegment hKey = mockOpenAndClose(rootHKey, "Software\\JavaSoft\\Prefs");

            mockValue(hKey, stringValue);

            RegistryKey registryKey = remoteRoot.resolve("Software\\JavaSoft\\Prefs");
            StringValue value = registryKey.getValue("string", StringValue.class);
            assertEquals(stringValue, value);

            verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eqPointer("Software\\JavaSoft\\Prefs"), anyInt(), anyInt(), notNull());
            verify(RegistryKey.api).RegCloseKey(hKey);
        }

        @Test
        @DisplayName("non-existing key")
        void testNonExistingKey() {
            mockOpenFailure(rootHKey, "path\\non-existing", WinError.ERROR_FILE_NOT_FOUND);

            RegistryKey registryKey = remoteRoot.resolve("path\\non-existing");
            NoSuchRegistryKeyException exception = assertThrows(NoSuchRegistryKeyException.class,
                    () -> registryKey.getValue("string", RegistryValue.class));
            assertEquals("HKEY_LOCAL_MACHINE\\path\\non-existing", exception.path());
            assertEquals("test-machine", exception.machineName());

            verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eqPointer("path\\non-existing"), anyInt(), anyInt(), notNull());
            verify(RegistryKey.api, never()).RegCloseKey(notNull());
        }

        @Test
        @DisplayName("non-existing value")
        void testNonExistingValue() {
            MemorySegment hKey = mockOpenAndClose(rootHKey, "path\\non-existing");

            when(RegistryKey.api.RegQueryValueEx(eq(hKey), eqPointer("string"), notNull(), notNull(), isNULL(), notNull()))
                    .thenReturn(WinError.ERROR_FILE_NOT_FOUND);

            RegistryKey registryKey = remoteRoot.resolve("path\\non-existing");
            NoSuchRegistryValueException exception = assertThrows(NoSuchRegistryValueException.class,
                    () -> registryKey.getValue("string", RegistryValue.class));
            assertEquals("HKEY_LOCAL_MACHINE\\path\\non-existing", exception.path());
            assertEquals("test-machine", exception.machineName());
            assertEquals("string", exception.name());

            verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eqPointer("path\\non-existing"), anyInt(), anyInt(), notNull());
            verify(RegistryKey.api).RegCloseKey(hKey);
        }

        @Test
        @DisplayName("failure")
        void testFailure() {
            MemorySegment hKey = mockOpenAndClose(rootHKey, "path\\failure");

            mockValue(hKey, StringValue.of("string", "value"), WinError.ERROR_INVALID_HANDLE);

            RegistryKey registryKey = remoteRoot.resolve("path\\failure");
            InvalidRegistryHandleException exception = assertThrows(InvalidRegistryHandleException.class,
                    () -> registryKey.getValue("string", RegistryValue.class));
            assertEquals("HKEY_LOCAL_MACHINE\\path\\failure", exception.path());
            assertEquals("test-machine", exception.machineName());

            verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eqPointer("path\\failure"), anyInt(), anyInt(), notNull());
            verify(RegistryKey.api).RegCloseKey(hKey);
        }

        @Test
        @DisplayName("wrong value type")
        void testWrongValueType() {
            StringValue stringValue = StringValue.of("string", "value");

            MemorySegment hKey = mockOpenAndClose(rootHKey, "Software\\JavaSoft\\Prefs");

            mockValue(hKey, stringValue);

            RegistryKey registryKey = remoteRoot.resolve("Software\\JavaSoft\\Prefs");
            assertThrows(ClassCastException.class, () -> registryKey.getValue("string", DWordValue.class));

            verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eqPointer("Software\\JavaSoft\\Prefs"), anyInt(), anyInt(), notNull());
            verify(RegistryKey.api).RegCloseKey(hKey);
        }
    }

    @Nested
    @DisplayName("findValue")
    class FindValue {

        @Test
        @DisplayName("success")
        void testSuccess() {
            StringValue stringValue = StringValue.of("string", "value");

            MemorySegment hKey = mockOpenAndClose(rootHKey, "Software\\JavaSoft\\Prefs");

            mockValue(hKey, stringValue);

            RegistryKey registryKey = remoteRoot.resolve("Software\\JavaSoft\\Prefs");
            Optional<StringValue> value = registryKey.findValue("string", StringValue.class);
            assertEquals(Optional.of(stringValue), value);

            verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eqPointer("Software\\JavaSoft\\Prefs"), anyInt(), anyInt(), notNull());
            verify(RegistryKey.api).RegCloseKey(hKey);
        }

        @Test
        @DisplayName("non-existing key")
        void testNonExistingKey() {
            mockOpenFailure(rootHKey, "path\\non-existing", WinError.ERROR_FILE_NOT_FOUND);

            RegistryKey registryKey = remoteRoot.resolve("path\\non-existing");
            NoSuchRegistryKeyException exception = assertThrows(NoSuchRegistryKeyException.class,
                    () -> registryKey.findValue("string", RegistryValue.class));
            assertEquals("HKEY_LOCAL_MACHINE\\path\\non-existing", exception.path());
            assertEquals("test-machine", exception.machineName());

            verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eqPointer("path\\non-existing"), anyInt(), anyInt(), notNull());
            verify(RegistryKey.api, never()).RegCloseKey(notNull());
        }

        @Test
        @DisplayName("non-existing value")
        void testNonExistingValue() {
            MemorySegment hKey = mockOpenAndClose(rootHKey, "path\\non-existing");

            when(RegistryKey.api.RegQueryValueEx(eq(hKey), eqPointer("string"), notNull(), notNull(), isNULL(), notNull()))
                    .thenReturn(WinError.ERROR_FILE_NOT_FOUND);

            RegistryKey registryKey = remoteRoot.resolve("path\\non-existing");
            Optional<DWordValue> value = registryKey.findValue("string", DWordValue.class);
            assertEquals(Optional.empty(), value);

            verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eqPointer("path\\non-existing"), anyInt(), anyInt(), notNull());
            verify(RegistryKey.api).RegCloseKey(hKey);
        }

        @Test
        @DisplayName("failure")
        void testFailure() {
            MemorySegment hKey = mockOpenAndClose(rootHKey, "path\\failure");

            mockValue(hKey, StringValue.of("string", "value"), WinError.ERROR_INVALID_HANDLE);

            RegistryKey registryKey = remoteRoot.resolve("path\\failure");
            InvalidRegistryHandleException exception = assertThrows(InvalidRegistryHandleException.class,
                    () -> registryKey.findValue("string", RegistryValue.class));
            assertEquals("HKEY_LOCAL_MACHINE\\path\\failure", exception.path());
            assertEquals("test-machine", exception.machineName());

            verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eqPointer("path\\failure"), anyInt(), anyInt(), notNull());
            verify(RegistryKey.api).RegCloseKey(hKey);
        }

        @Test
        @DisplayName("wrong value type")
        void testWrongValueType() {
            StringValue stringValue = StringValue.of("string", "value");

            MemorySegment hKey = mockOpenAndClose(rootHKey, "Software\\JavaSoft\\Prefs");

            mockValue(hKey, stringValue);

            RegistryKey registryKey = remoteRoot.resolve("Software\\JavaSoft\\Prefs");
            assertThrows(ClassCastException.class, () -> registryKey.findValue("string", DWordValue.class));

            verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eqPointer("Software\\JavaSoft\\Prefs"), anyInt(), anyInt(), notNull());
            verify(RegistryKey.api).RegCloseKey(hKey);
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

            MemorySegment hKey = mockOpenAndClose(rootHKey, "Software\\JavaSoft\\Prefs");

            doReturn(WinError.ERROR_SUCCESS).when(RegistryKey.api)
                    .RegSetValueEx(eq(hKey), eqPointer("string"), anyInt(), eq(WinNT.REG_SZ), eqBytes(data), eqSize(data));

            RegistryKey registryKey = remoteRoot.resolve("Software\\JavaSoft\\Prefs");
            registryKey.setValue(stringValue);

            verify(RegistryKey.api).RegSetValueEx(eq(hKey), eqPointer("string"), anyInt(), eq(WinNT.REG_SZ), eqBytes(data), eqSize(data));

            verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eqPointer("Software\\JavaSoft\\Prefs"), anyInt(), anyInt(), notNull());
            verify(RegistryKey.api).RegCloseKey(hKey);
        }

        @Test
        @DisplayName("non-existing key")
        void testNonExistingKey() {
            StringValue stringValue = StringValue.of("string", "value");

            mockOpenFailure(rootHKey, "path\\non-existing", WinError.ERROR_FILE_NOT_FOUND);

            RegistryKey registryKey = remoteRoot.resolve("path\\non-existing");
            NoSuchRegistryKeyException exception = assertThrows(NoSuchRegistryKeyException.class, () -> registryKey.setValue(stringValue));
            assertEquals("HKEY_LOCAL_MACHINE\\path\\non-existing", exception.path());
            assertEquals("test-machine", exception.machineName());

            verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eqPointer("path\\non-existing"), anyInt(), anyInt(), notNull());
            verify(RegistryKey.api, never()).RegCloseKey(notNull());
            verify(RegistryKey.api, never()).RegSetValueEx(notNull(), notNull(), anyInt(), anyInt(), notNull(), anyInt());
        }

        @Test
        @DisplayName("failure")
        void testFailure() {
            StringValue stringValue = StringValue.of("string", "value");

            MemorySegment hKey = mockOpenAndClose(rootHKey, "path\\failure");

            when(RegistryKey.api.RegSetValueEx(eq(hKey), eqPointer("string"), anyInt(), eq(WinNT.REG_SZ), notNull(), anyInt()))
                    .thenReturn(WinError.ERROR_INVALID_HANDLE);

            RegistryKey registryKey = remoteRoot.resolve("path\\failure");
            InvalidRegistryHandleException exception = assertThrows(InvalidRegistryHandleException.class, () -> registryKey.setValue(stringValue));
            assertEquals("HKEY_LOCAL_MACHINE\\path\\failure", exception.path());
            assertEquals("test-machine", exception.machineName());

            verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eqPointer("path\\failure"), anyInt(), anyInt(), notNull());
            verify(RegistryKey.api).RegCloseKey(hKey);
        }
    }

    @Nested
    @DisplayName("deleteValue")
    class DeleteValue {

        @Test
        @DisplayName("success")
        void testSuccess() {
            MemorySegment hKey = mockOpenAndClose(rootHKey, "Software\\JavaSoft\\Prefs");

            when(RegistryKey.api.RegDeleteValue(eq(hKey), eqPointer("string"))).thenReturn(WinError.ERROR_SUCCESS);

            RegistryKey registryKey = remoteRoot.resolve("Software\\JavaSoft\\Prefs");
            registryKey.deleteValue("string");

            verify(RegistryKey.api).RegDeleteValue(eq(hKey), eqPointer("string"));

            verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eqPointer("Software\\JavaSoft\\Prefs"), anyInt(), anyInt(), notNull());
            verify(RegistryKey.api).RegCloseKey(hKey);
        }

        @Test
        @DisplayName("non-existing key")
        void testNonExistingKey() {
            mockOpenFailure(rootHKey, "path\\non-existing", WinError.ERROR_FILE_NOT_FOUND);

            RegistryKey registryKey = remoteRoot.resolve("path\\non-existing");
            NoSuchRegistryKeyException exception = assertThrows(NoSuchRegistryKeyException.class, () -> registryKey.deleteValue("string"));
            assertEquals("HKEY_LOCAL_MACHINE\\path\\non-existing", exception.path());
            assertEquals("test-machine", exception.machineName());

            verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eqPointer("path\\non-existing"), anyInt(), anyInt(), notNull());
            verify(RegistryKey.api, never()).RegCloseKey(notNull());
            verify(RegistryKey.api, never()).RegDeleteValue(notNull(), notNull());
        }

        @Test
        @DisplayName("failure")
        void testFailure() {
            MemorySegment hKey = mockOpenAndClose(rootHKey, "path\\failure");

            when(RegistryKey.api.RegDeleteValue(eq(hKey), eqPointer("string"))).thenReturn(WinError.ERROR_FILE_NOT_FOUND);

            RegistryKey registryKey = remoteRoot.resolve("path\\failure");
            NoSuchRegistryValueException exception = assertThrows(NoSuchRegistryValueException.class, () -> registryKey.deleteValue("string"));
            assertEquals("HKEY_LOCAL_MACHINE\\path\\failure", exception.path());
            assertEquals("test-machine", exception.machineName());
            assertEquals("string", exception.name());

            verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eqPointer("path\\failure"), anyInt(), anyInt(), notNull());
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
                MemorySegment hKey = mockOpenAndClose(rootHKey, "Software\\JavaSoft\\Prefs");

                when(RegistryKey.api.RegDeleteValue(eq(hKey), eqPointer("string"))).thenReturn(WinError.ERROR_SUCCESS);

                RegistryKey registryKey = remoteRoot.resolve("Software\\JavaSoft\\Prefs");
                assertTrue(registryKey.deleteValueIfExists("string"));

                verify(RegistryKey.api).RegDeleteValue(eq(hKey), eqPointer("string"));

                verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eqPointer("Software\\JavaSoft\\Prefs"), anyInt(), anyInt(), notNull());
                verify(RegistryKey.api).RegCloseKey(hKey);
            }

            @Test
            @DisplayName("value didn't exist")
            void testValueDidntExist() {
                MemorySegment hKey = mockOpenAndClose(rootHKey, "Software\\JavaSoft\\Prefs");

                when(RegistryKey.api.RegDeleteValue(eq(hKey), eqPointer("string"))).thenReturn(WinError.ERROR_FILE_NOT_FOUND);

                RegistryKey registryKey = remoteRoot.resolve("Software\\JavaSoft\\Prefs");
                assertFalse(registryKey.deleteValueIfExists("string"));

                verify(RegistryKey.api).RegDeleteValue(eq(hKey), eqPointer("string"));

                verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eqPointer("Software\\JavaSoft\\Prefs"), anyInt(), anyInt(), notNull());
                verify(RegistryKey.api).RegCloseKey(hKey);
            }
        }

        @Test
        @DisplayName("non-existing key")
        void testNonExistingKey() {
            mockOpenFailure(rootHKey, "path\\non-existing", WinError.ERROR_FILE_NOT_FOUND);

            RegistryKey registryKey = remoteRoot.resolve("path\\non-existing");
            NoSuchRegistryKeyException exception = assertThrows(NoSuchRegistryKeyException.class, () -> registryKey.deleteValueIfExists("string"));
            assertEquals("HKEY_LOCAL_MACHINE\\path\\non-existing", exception.path());
            assertEquals("test-machine", exception.machineName());

            verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eqPointer("path\\non-existing"), anyInt(), anyInt(), notNull());
            verify(RegistryKey.api, never()).RegCloseKey(notNull());
            verify(RegistryKey.api, never()).RegDeleteValue(notNull(), notNull());
        }

        @Test
        @DisplayName("failure")
        void testFailure() {
            MemorySegment hKey = mockOpenAndClose(rootHKey, "path\\failure");

            when(RegistryKey.api.RegDeleteValue(eq(hKey), eqPointer("string"))).thenReturn(WinError.ERROR_INVALID_HANDLE);

            RegistryKey registryKey = remoteRoot.resolve("path\\failure");
            InvalidRegistryHandleException exception = assertThrows(InvalidRegistryHandleException.class,
                    () -> registryKey.deleteValueIfExists("string"));
            assertEquals("HKEY_LOCAL_MACHINE\\path\\failure", exception.path());
            assertEquals("test-machine", exception.machineName());

            verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eqPointer("path\\failure"), anyInt(), anyInt(), notNull());
            verify(RegistryKey.api).RegCloseKey(hKey);
        }
    }

    @Nested
    @DisplayName("exists")
    class Exists {

        @Test
        @DisplayName("existing")
        void testExisting() {
            MemorySegment hKey = mockOpenAndClose(rootHKey, "path\\existing");

            RegistryKey registryKey = remoteRoot.resolve("path\\existing");
            assertTrue(registryKey.exists());

            verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eqPointer("path\\existing"), anyInt(), anyInt(), notNull());
            verify(RegistryKey.api).RegCloseKey(hKey);
        }

        @Test
        @DisplayName("non-existing")
        void testNonExisting() {
            mockOpenFailure(rootHKey, "path\\non-existing", WinError.ERROR_FILE_NOT_FOUND);

            RegistryKey registryKey = remoteRoot.resolve("path\\non-existing");
            assertFalse(registryKey.exists());

            verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eqPointer("path\\non-existing"), anyInt(), anyInt(), notNull());
            verify(RegistryKey.api, never()).RegCloseKey(notNull());
        }

        @Test
        @DisplayName("failure")
        void testFailure() {
            mockOpenFailure(rootHKey, "path\\failure", WinError.ERROR_ACCESS_DENIED);

            RegistryKey registryKey = remoteRoot.resolve("path\\failure");
            RegistryAccessDeniedException exception = assertThrows(RegistryAccessDeniedException.class, registryKey::exists);
            assertEquals("HKEY_LOCAL_MACHINE\\path\\failure", exception.path());
            assertEquals("test-machine", exception.machineName());

            verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eqPointer("path\\failure"), anyInt(), anyInt(), notNull());
            verify(RegistryKey.api, never()).RegCloseKey(notNull());
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
            @SuppressWarnings("resource")
            void testSuccess() {
                MemorySegment hKey = mockOpenAndClose(rootHKey, "path\\existing");

                RegistryKey registryKey = remoteRoot.resolve("path\\existing");

                @SuppressWarnings("unchecked")
                Consumer<RegistryKey.Handle> action = mock(Consumer.class);

                registryKey.ifExists(action);

                verify(action).accept(any());

                verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eqPointer("path\\existing"), anyInt(), anyInt(), notNull());
                verify(RegistryKey.api).RegCloseKey(hKey);
            }

            @Test
            @DisplayName("non-existing")
            @SuppressWarnings("resource")
            void testNonExisting() {
                mockOpenFailure(rootHKey, "path\\not-found", WinError.ERROR_FILE_NOT_FOUND);

                RegistryKey registryKey = remoteRoot.resolve("path\\not-found");

                @SuppressWarnings("unchecked")
                Consumer<RegistryKey.Handle> action = mock(Consumer.class);

                registryKey.ifExists(action);

                verify(action, never()).accept(any());

                verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eqPointer("path\\not-found"), anyInt(), anyInt(), notNull());
                verify(RegistryKey.api, never()).RegCloseKey(notNull());
            }

            @Test
            @DisplayName("failure")
            @SuppressWarnings("resource")
            void testFailure() {
                mockOpenFailure(rootHKey, "path\\failure", WinError.ERROR_ACCESS_DENIED);

                RegistryKey registryKey = remoteRoot.resolve("path\\failure");

                @SuppressWarnings("unchecked")
                Consumer<RegistryKey.Handle> action = mock(Consumer.class);

                RegistryAccessDeniedException exception = assertThrows(RegistryAccessDeniedException.class, () -> registryKey.ifExists(action));
                assertEquals("HKEY_LOCAL_MACHINE\\path\\failure", exception.path());
                assertEquals("test-machine", exception.machineName());

                verify(action, never()).accept(any());

                verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eqPointer("path\\failure"), anyInt(), anyInt(), notNull());
                verify(RegistryKey.api, never()).RegCloseKey(notNull());
            }
        }

        @Nested
        @DisplayName("with function")
        class WithFunction {

            @Test
            @DisplayName("success")
            void testSuccess() {
                MemorySegment hKey = mockOpenAndClose(rootHKey, "path\\existing");

                RegistryKey registryKey = remoteRoot.resolve("path\\existing");

                Function<RegistryKey.Handle, String> action = _ -> "new handle";

                Optional<String> result = registryKey.ifExists(action);

                assertEquals(Optional.of("new handle"), result);

                verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eqPointer("path\\existing"), anyInt(), anyInt(), notNull());
                verify(RegistryKey.api).RegCloseKey(hKey);
            }

            @Test
            @DisplayName("non-existing")
            @SuppressWarnings("resource")
            void testNonExisting() {
                mockOpenFailure(rootHKey, "path\\not-found", WinError.ERROR_FILE_NOT_FOUND);

                RegistryKey registryKey = remoteRoot.resolve("path\\not-found");

                @SuppressWarnings("unchecked")
                Function<RegistryKey.Handle, String> action = mock(Function.class);

                registryKey.ifExists(action);

                verify(action, never()).apply(any());

                verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eqPointer("path\\not-found"), anyInt(), anyInt(), notNull());
                verify(RegistryKey.api, never()).RegCloseKey(notNull());
            }

            @Test
            @DisplayName("failure")
            @SuppressWarnings("resource")
            void testFailure() {
                mockOpenFailure(rootHKey, "path\\failure", WinError.ERROR_ACCESS_DENIED);

                RegistryKey registryKey = remoteRoot.resolve("path\\failure");

                @SuppressWarnings("unchecked")
                Function<RegistryKey.Handle, String> action = mock(Function.class);

                RegistryAccessDeniedException exception = assertThrows(RegistryAccessDeniedException.class, () -> registryKey.ifExists(action));
                assertEquals("HKEY_LOCAL_MACHINE\\path\\failure", exception.path());
                assertEquals("test-machine", exception.machineName());

                verify(action, never()).apply(any());

                verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eqPointer("path\\failure"), anyInt(), anyInt(), notNull());
                verify(RegistryKey.api, never()).RegCloseKey(notNull());
            }
        }
    }

    @Nested
    @DisplayName("isAccessible")
    class IsAccessible {

        @Test
        @DisplayName("existing")
        void testExisting() {
            MemorySegment hKey = mockOpenAndClose(rootHKey, "path\\existing");

            RegistryKey registryKey = remoteRoot.resolve("path\\existing");
            assertTrue(registryKey.isAccessible());

            verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eqPointer("path\\existing"), anyInt(), anyInt(), notNull());
            verify(RegistryKey.api).RegCloseKey(hKey);
        }

        @Test
        @DisplayName("non-existing")
        void testNonExisting() {
            mockOpenFailure(rootHKey, "path\\non-existing", WinError.ERROR_FILE_NOT_FOUND);

            RegistryKey registryKey = remoteRoot.resolve("path\\non-existing");
            assertFalse(registryKey.isAccessible());

            verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eqPointer("path\\non-existing"), anyInt(), anyInt(), notNull());
            verify(RegistryKey.api, never()).RegCloseKey(notNull());
        }

        @Test
        @DisplayName("non-accessible")
        void testNonAccessible() {
            mockOpenFailure(rootHKey, "path\\non-accessible", WinError.ERROR_ACCESS_DENIED);

            RegistryKey registryKey = remoteRoot.resolve("path\\non-accessible");
            assertFalse(registryKey.isAccessible());

            verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eqPointer("path\\non-accessible"), anyInt(), anyInt(), notNull());
            verify(RegistryKey.api, never()).RegCloseKey(notNull());
        }

        @Test
        @DisplayName("failure")
        void testFailure() {
            mockOpenFailure(rootHKey, "path\\failure", WinError.ERROR_INVALID_HANDLE);

            RegistryKey registryKey = remoteRoot.resolve("path\\failure");
            InvalidRegistryHandleException exception = assertThrows(InvalidRegistryHandleException.class, registryKey::isAccessible);
            assertEquals("HKEY_LOCAL_MACHINE\\path\\failure", exception.path());
            assertEquals("test-machine", exception.machineName());

            verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eqPointer("path\\failure"), anyInt(), anyInt(), notNull());
            verify(RegistryKey.api, never()).RegCloseKey(notNull());
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
            @SuppressWarnings("resource")
            void testSuccess() {
                MemorySegment hKey = mockOpenAndClose(rootHKey, "path\\existing");

                RegistryKey registryKey = remoteRoot.resolve("path\\existing");

                @SuppressWarnings("unchecked")
                Consumer<RegistryKey.Handle> action = mock(Consumer.class);

                registryKey.ifAccessible(action);

                verify(action).accept(any());

                verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eqPointer("path\\existing"), anyInt(), anyInt(), notNull());
                verify(RegistryKey.api).RegCloseKey(hKey);
            }

            @Test
            @DisplayName("non-existing")
            @SuppressWarnings("resource")
            void testNonExisting() {
                mockOpenFailure(rootHKey, "path\\non-existing", WinError.ERROR_FILE_NOT_FOUND);

                RegistryKey registryKey = remoteRoot.resolve("path\\non-existing");

                @SuppressWarnings("unchecked")
                Consumer<RegistryKey.Handle> action = mock(Consumer.class);

                registryKey.ifAccessible(action);

                verify(action, never()).accept(any());

                verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eqPointer("path\\non-existing"), anyInt(), anyInt(), notNull());
                verify(RegistryKey.api, never()).RegCloseKey(notNull());
            }

            @Test
            @DisplayName("access denied")
            @SuppressWarnings("resource")
            void testAccessDenied() {
                mockOpenFailure(rootHKey, "path\\access-denied", WinError.ERROR_ACCESS_DENIED);

                RegistryKey registryKey = remoteRoot.resolve("path\\access-denied");

                @SuppressWarnings("unchecked")
                Consumer<RegistryKey.Handle> action = mock(Consumer.class);

                registryKey.ifAccessible(action);

                verify(action, never()).accept(any());

                verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eqPointer("path\\access-denied"), anyInt(), anyInt(), notNull());
                verify(RegistryKey.api, never()).RegCloseKey(notNull());
            }

            @Test
            @DisplayName("failure")
            @SuppressWarnings("resource")
            void testFailure() {
                mockOpenFailure(rootHKey, "path\\failure", WinError.ERROR_INVALID_HANDLE);

                RegistryKey registryKey = remoteRoot.resolve("path\\failure");

                @SuppressWarnings("unchecked")
                Consumer<RegistryKey.Handle> action = mock(Consumer.class);

                InvalidRegistryHandleException exception = assertThrows(InvalidRegistryHandleException.class, () -> registryKey.ifAccessible(action));
                assertEquals("HKEY_LOCAL_MACHINE\\path\\failure", exception.path());
                assertEquals("test-machine", exception.machineName());

                verify(action, never()).accept(any());

                verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eqPointer("path\\failure"), anyInt(), anyInt(), notNull());
                verify(RegistryKey.api, never()).RegCloseKey(notNull());
            }
        }

        @Nested
        @DisplayName("with function")
        class WithFunction {

            @Test
            @DisplayName("success")
            void testSuccess() {
                MemorySegment hKey = mockOpenAndClose(rootHKey, "path\\existing");

                RegistryKey registryKey = remoteRoot.resolve("path\\existing");

                Function<RegistryKey.Handle, String> action = _ -> "new handle";

                Optional<String> result = registryKey.ifAccessible(action);

                assertEquals(Optional.of("new handle"), result);

                verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eqPointer("path\\existing"), anyInt(), anyInt(), notNull());
                verify(RegistryKey.api).RegCloseKey(hKey);
            }

            @Test
            @DisplayName("non-existing")
            @SuppressWarnings("resource")
            void testNonExisting() {
                mockOpenFailure(rootHKey, "path\\non-existing", WinError.ERROR_FILE_NOT_FOUND);

                RegistryKey registryKey = remoteRoot.resolve("path\\non-existing");

                @SuppressWarnings("unchecked")
                Function<RegistryKey.Handle, String> action = mock(Function.class);

                registryKey.ifAccessible(action);

                verify(action, never()).apply(any());

                verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eqPointer("path\\non-existing"), anyInt(), anyInt(), notNull());
                verify(RegistryKey.api, never()).RegCloseKey(notNull());
            }

            @Test
            @DisplayName("access denied")
            @SuppressWarnings("resource")
            void testAccessDenied() {
                mockOpenFailure(rootHKey, "path\\access-denied", WinError.ERROR_ACCESS_DENIED);

                RegistryKey registryKey = remoteRoot.resolve("path\\access-denied");

                @SuppressWarnings("unchecked")
                Function<RegistryKey.Handle, String> action = mock(Function.class);

                registryKey.ifAccessible(action);

                verify(action, never()).apply(any());

                verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eqPointer("path\\access-denied"), anyInt(), anyInt(), notNull());
                verify(RegistryKey.api, never()).RegCloseKey(notNull());
            }

            @Test
            @DisplayName("failure")
            @SuppressWarnings("resource")
            void testFailure() {
                mockOpenFailure(rootHKey, "path\\failure", WinError.ERROR_INVALID_HANDLE);

                RegistryKey registryKey = remoteRoot.resolve("path\\failure");

                @SuppressWarnings("unchecked")
                Function<RegistryKey.Handle, String> action = mock(Function.class);

                InvalidRegistryHandleException exception = assertThrows(InvalidRegistryHandleException.class, () -> registryKey.ifAccessible(action));
                assertEquals("HKEY_LOCAL_MACHINE\\path\\failure", exception.path());
                assertEquals("test-machine", exception.machineName());

                verify(action, never()).apply(any());

                verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eqPointer("path\\failure"), anyInt(), anyInt(), notNull());
                verify(RegistryKey.api, never()).RegCloseKey(notNull());
            }
        }
    }

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("non-existing")
        void testCreateNonExisting() {
            MemorySegment hKey = newHKEY();

            when(RegistryKey.api.RegCreateKeyEx(eq(rootHKey), eqPointer("path\\new"),
                    anyInt(), notNull(), anyInt(), anyInt(), notNull(), notNull(), notNull()))
                    .thenAnswer(i -> {
                        setHKEY(i.getArgument(7, MemorySegment.class), hKey);
                        setInt(i.getArgument(8, MemorySegment.class), WinNT.REG_CREATED_NEW_KEY);

                        return WinError.ERROR_SUCCESS;
                    });

            RegistryKey registryKey = remoteRoot.resolve("path\\new");
            registryKey.create();

            verify(RegistryKey.api)
                    .RegCreateKeyEx(eq(rootHKey), eqPointer("path\\new"), anyInt(), notNull(), anyInt(), anyInt(), notNull(), notNull(), notNull());
            verify(RegistryKey.api).RegCloseKey(hKey);
        }

        @Test
        @DisplayName("existing")
        void testCreateExisting() {
            MemorySegment hKey = newHKEY();

            when(RegistryKey.api.RegCreateKeyEx(eq(rootHKey), eqPointer("path\\existing"),
                    anyInt(), notNull(), anyInt(), anyInt(), notNull(), notNull(), notNull()))
                    .thenAnswer(i -> {
                        setHKEY(i.getArgument(7, MemorySegment.class), hKey);
                        setInt(i.getArgument(8, MemorySegment.class), WinNT.REG_OPENED_EXISTING_KEY);

                        return WinError.ERROR_SUCCESS;
                    });

            RegistryKey registryKey = remoteRoot.resolve("path\\existing");
            RegistryKeyAlreadyExistsException exception = assertThrows(RegistryKeyAlreadyExistsException.class, registryKey::create);
            assertEquals("HKEY_LOCAL_MACHINE\\path\\existing", exception.path());
            assertEquals("test-machine", exception.machineName());

            verify(RegistryKey.api).RegCreateKeyEx(eq(rootHKey), eqPointer("path\\existing"), anyInt(), notNull(), anyInt(), anyInt(), notNull(),
                    notNull(), notNull());
            verify(RegistryKey.api).RegCloseKey(hKey);
        }

        @Test
        @DisplayName("failure")
        void testFailure() {
            doReturn(WinError.ERROR_INVALID_HANDLE).when(RegistryKey.api)
                    .RegCreateKeyEx(eq(rootHKey), eqPointer("path\\failure"), anyInt(), notNull(), anyInt(), anyInt(), notNull(), notNull(),
                            notNull());

            RegistryKey registryKey = remoteRoot.resolve("path\\failure");
            InvalidRegistryHandleException exception = assertThrows(InvalidRegistryHandleException.class, registryKey::create);
            assertEquals("HKEY_LOCAL_MACHINE\\path\\failure", exception.path());
            assertEquals("test-machine", exception.machineName());

            verify(RegistryKey.api).RegCreateKeyEx(eq(rootHKey), eqPointer("path\\failure"), anyInt(), notNull(), anyInt(), anyInt(), notNull(),
                    notNull(), notNull());
            verify(RegistryKey.api, never()).RegCloseKey(notNull());
        }
    }

    @Nested
    @DisplayName("createIfNotExists")
    class CreateIfNotExists {

        @Test
        @DisplayName("non-existing")
        void testCreateNonExisting() {
            MemorySegment hKey = newHKEY();

            when(RegistryKey.api.RegCreateKeyEx(eq(rootHKey), eqPointer("path\\new"), anyInt(), notNull(), anyInt(), anyInt(), notNull(), notNull(),
                    notNull()))
                    .thenAnswer(i -> {
                        setHKEY(i.getArgument(7, MemorySegment.class), hKey);
                        setInt(i.getArgument(8, MemorySegment.class), WinNT.REG_CREATED_NEW_KEY);

                        return WinError.ERROR_SUCCESS;
                    });

            RegistryKey registryKey = remoteRoot.resolve("path\\new");
            assertTrue(registryKey.createIfNotExists());

            verify(RegistryKey.api)
                    .RegCreateKeyEx(eq(rootHKey), eqPointer("path\\new"), anyInt(), notNull(), anyInt(), anyInt(), notNull(), notNull(), notNull());
            verify(RegistryKey.api).RegCloseKey(hKey);
        }

        @Test
        @DisplayName("existing")
        void testCreateExisting() {
            MemorySegment hKey = newHKEY();

            when(RegistryKey.api.RegCreateKeyEx(eq(rootHKey), eqPointer("path\\existing"),
                    anyInt(), notNull(), anyInt(), anyInt(), notNull(), notNull(), notNull()))
                    .thenAnswer(i -> {
                        setHKEY(i.getArgument(7, MemorySegment.class), hKey);
                        setInt(i.getArgument(8, MemorySegment.class), WinNT.REG_OPENED_EXISTING_KEY);

                        return WinError.ERROR_SUCCESS;
                    });

            RegistryKey registryKey = remoteRoot.resolve("path\\existing");
            assertFalse(registryKey.createIfNotExists());

            verify(RegistryKey.api).RegCreateKeyEx(eq(rootHKey), eqPointer("path\\existing"), anyInt(), notNull(), anyInt(), anyInt(), notNull(),
                    notNull(), notNull());
            verify(RegistryKey.api).RegCloseKey(hKey);
        }

        @Test
        @DisplayName("failure")
        void testFailure() {
            doReturn(WinError.ERROR_INVALID_HANDLE).when(RegistryKey.api)
                    .RegCreateKeyEx(eq(rootHKey), eqPointer("path\\failure"), anyInt(), notNull(), anyInt(), anyInt(), notNull(), notNull(),
                            notNull());

            RegistryKey registryKey = remoteRoot.resolve("path\\failure");
            InvalidRegistryHandleException exception = assertThrows(InvalidRegistryHandleException.class, registryKey::createIfNotExists);
            assertEquals("HKEY_LOCAL_MACHINE\\path\\failure", exception.path());
            assertEquals("test-machine", exception.machineName());

            verify(RegistryKey.api).RegCreateKeyEx(eq(rootHKey), eqPointer("path\\failure"), anyInt(), notNull(), anyInt(), anyInt(), notNull(),
                    notNull(), notNull());
            verify(RegistryKey.api, never()).RegCloseKey(notNull());
        }
    }

    @Nested
    @DisplayName("renameTo")
    class RenameTo {

        @Test
        @DisplayName("existing")
        void testRenameExisting() {
            doReturn(WinError.ERROR_SUCCESS).when(RegistryKey.api).RegRenameKey(eq(rootHKey), eqPointer("path\\existing"), eqPointer("foo"));

            RegistryKey registryKey = remoteRoot.resolve("path\\existing");
            RegistryKey renamed = registryKey.renameTo("foo");
            assertEquals(registryKey.resolve("..\\foo"), renamed);

            verify(RegistryKey.api).RegRenameKey(eq(rootHKey), eqPointer("path\\existing"), eqPointer("foo"));
            verify(RegistryKey.api, never()).RegOpenKeyEx(notNull(), notNull(), anyInt(), anyInt(), notNull());
            verify(RegistryKey.api, never()).RegCloseKey(notNull());
        }

        @Test
        @DisplayName("non-existing")
        void testNonExisting() {
            doReturn(WinError.ERROR_FILE_NOT_FOUND).when(RegistryKey.api)
                    .RegRenameKey(eq(rootHKey), eqPointer("path\\non-existing"), eqPointer("foo"));

            RegistryKey registryKey = remoteRoot.resolve("path\\non-existing");
            NoSuchRegistryKeyException exception = assertThrows(NoSuchRegistryKeyException.class, () -> registryKey.renameTo("foo"));
            assertEquals("HKEY_LOCAL_MACHINE\\path\\non-existing", exception.path());
            assertEquals("test-machine", exception.machineName());

            verify(RegistryKey.api).RegRenameKey(eq(rootHKey), eqPointer("path\\non-existing"), eqPointer("foo"));
            verify(RegistryKey.api, never()).RegOpenKeyEx(notNull(), notNull(), anyInt(), anyInt(), notNull());
            verify(RegistryKey.api, never()).RegCloseKey(notNull());
        }

        @Test
        @DisplayName("target exists")
        void testTargetExists() {
            doReturn(WinError.ERROR_ACCESS_DENIED).when(RegistryKey.api)
                    .RegRenameKey(eq(rootHKey), eqPointer("path\\existing"), eqPointer("foo"));

            MemorySegment targetHkey = mockOpenAndClose(rootHKey, "path\\foo");

            RegistryKey registryKey = remoteRoot.resolve("path\\existing");
            RegistryKeyAlreadyExistsException exception = assertThrows(RegistryKeyAlreadyExistsException.class, () -> registryKey.renameTo("foo"));
            assertEquals("HKEY_LOCAL_MACHINE\\path\\foo", exception.path());
            assertEquals("test-machine", exception.machineName());

            verify(RegistryKey.api).RegRenameKey(eq(rootHKey), eqPointer("path\\existing"), eqPointer("foo"));
            verify(RegistryKey.api, never()).RegOpenKeyEx(eq(rootHKey), not(eqPointer("path\\foo")), anyInt(), anyInt(), notNull());
            verify(RegistryKey.api, never()).RegCloseKey(not(eq(targetHkey)));
        }

        @Test
        @DisplayName("access denied")
        void testAccessDenied() {
            doReturn(WinError.ERROR_ACCESS_DENIED).when(RegistryKey.api)
                    .RegRenameKey(eq(rootHKey), eqPointer("path\\existing"), eqPointer("foo"));

            mockOpenFailure(rootHKey, "path\\foo", WinError.ERROR_FILE_NOT_FOUND);

            RegistryKey registryKey = remoteRoot.resolve("path\\existing");
            RegistryAccessDeniedException exception = assertThrows(RegistryAccessDeniedException.class, () -> registryKey.renameTo("foo"));
            assertEquals("HKEY_LOCAL_MACHINE\\path\\existing", exception.path());
            assertEquals("test-machine", exception.machineName());

            verify(RegistryKey.api).RegRenameKey(eq(rootHKey), eqPointer("path\\existing"), eqPointer("foo"));
            verify(RegistryKey.api, never()).RegOpenKeyEx(not(eq(rootHKey)), not(eqPointer("path\\foo")), anyInt(), anyInt(), notNull());
            verify(RegistryKey.api, never()).RegCloseKey(notNull());
        }

        @Test
        @DisplayName("failure")
        void testFailure() {
            doReturn(WinError.ERROR_INVALID_HANDLE).when(RegistryKey.api)
                    .RegRenameKey(eq(rootHKey), eqPointer("path\\existing"), eqPointer("foo"));

            RegistryKey registryKey = remoteRoot.resolve("path\\existing");
            InvalidRegistryHandleException exception = assertThrows(InvalidRegistryHandleException.class, () -> registryKey.renameTo("foo"));
            assertEquals("HKEY_LOCAL_MACHINE\\path\\existing", exception.path());
            assertEquals("test-machine", exception.machineName());

            verify(RegistryKey.api).RegRenameKey(eq(rootHKey), eqPointer("path\\existing"), eqPointer("foo"));
            verify(RegistryKey.api, never()).RegOpenKeyEx(notNull(), notNull(), anyInt(), anyInt(), notNull());
            verify(RegistryKey.api, never()).RegCloseKey(notNull());
        }

        // Cannot test function not available, as this is now caught early on when creating Advapi32Impl instances
        // This will lookup the actual Windows calls already at startup time

        @Test
        @DisplayName("invalid name")
        void testInvalidName() {
            RegistryKey registryKey = remoteRoot.resolve("path\\existing");

            assertThrows(IllegalArgumentException.class, () -> registryKey.renameTo("\\foo"));

            verify(RegistryKey.api, never()).RegRenameKey(notNull(), notNull(), notNull());
        }
    }

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("existing")
        void testDeleteExisting() {
            doReturn(WinError.ERROR_SUCCESS).when(RegistryKey.api).RegDeleteKeyEx(eq(rootHKey), eqPointer("path\\existing"), eq(0), eq(0));

            RegistryKey registryKey = remoteRoot.resolve("path\\existing");
            registryKey.delete();

            verify(RegistryKey.api).RegDeleteKeyEx(eq(rootHKey), eqPointer("path\\existing"), eq(0), eq(0));
            verify(RegistryKey.api, never()).RegOpenKeyEx(notNull(), notNull(), anyInt(), anyInt(), notNull());
            verify(RegistryKey.api, never()).RegCloseKey(notNull());
        }

        @Test
        @DisplayName("non-existing")
        void testDeleteNonExisting() {
            doReturn(WinError.ERROR_FILE_NOT_FOUND).when(RegistryKey.api).RegDeleteKeyEx(eq(rootHKey), eqPointer("path\\non-existing"), eq(0), eq(0));

            RegistryKey registryKey = remoteRoot.resolve("path\\non-existing");
            NoSuchRegistryKeyException exception = assertThrows(NoSuchRegistryKeyException.class, registryKey::delete);
            assertEquals("HKEY_LOCAL_MACHINE\\path\\non-existing", exception.path());
            assertEquals("test-machine", exception.machineName());

            verify(RegistryKey.api, never()).RegOpenKeyEx(notNull(), notNull(), anyInt(), anyInt(), notNull());
            verify(RegistryKey.api, never()).RegCloseKey(notNull());
        }

        @Test
        @DisplayName("failure")
        void testFailure() {
            doReturn(WinError.ERROR_INVALID_HANDLE).when(RegistryKey.api).RegDeleteKeyEx(eq(rootHKey), eqPointer("path\\failure"), eq(0), eq(0));

            RegistryKey registryKey = remoteRoot.resolve("path\\failure");
            InvalidRegistryHandleException exception = assertThrows(InvalidRegistryHandleException.class, registryKey::delete);
            assertEquals("HKEY_LOCAL_MACHINE\\path\\failure", exception.path());
            assertEquals("test-machine", exception.machineName());

            verify(RegistryKey.api, never()).RegOpenKeyEx(notNull(), notNull(), anyInt(), anyInt(), notNull());
            verify(RegistryKey.api, never()).RegCloseKey(notNull());
        }
    }

    @Nested
    @DisplayName("deleteIfExists")
    class DeleteIfExists {

        @Test
        @DisplayName("existing")
        void testDeleteExisting() {
            doReturn(WinError.ERROR_SUCCESS).when(RegistryKey.api).RegDeleteKeyEx(eq(rootHKey), eqPointer("path\\existing"), eq(0), eq(0));

            RegistryKey registryKey = remoteRoot.resolve("path\\existing");
            assertTrue(registryKey.deleteIfExists());

            verify(RegistryKey.api).RegDeleteKeyEx(eq(rootHKey), eqPointer("path\\existing"), eq(0), eq(0));
            verify(RegistryKey.api, never()).RegOpenKeyEx(notNull(), notNull(), anyInt(), anyInt(), notNull());
            verify(RegistryKey.api, never()).RegCloseKey(notNull());
        }

        @Test
        @DisplayName("non-existing")
        void testDeleteNonExisting() {
            doReturn(WinError.ERROR_FILE_NOT_FOUND).when(RegistryKey.api).RegDeleteKeyEx(eq(rootHKey), eqPointer("path\\non-existing"), eq(0), eq(0));

            RegistryKey registryKey = remoteRoot.resolve("path\\non-existing");
            assertFalse(registryKey.deleteIfExists());

            verify(RegistryKey.api).RegDeleteKeyEx(eq(rootHKey), eqPointer("path\\non-existing"), eq(0), eq(0));
            verify(RegistryKey.api, never()).RegOpenKeyEx(notNull(), notNull(), anyInt(), anyInt(), notNull());
            verify(RegistryKey.api, never()).RegCloseKey(notNull());
        }

        @Test
        @DisplayName("failure")
        void testFailure() {
            doReturn(WinError.ERROR_INVALID_HANDLE).when(RegistryKey.api).RegDeleteKeyEx(eq(rootHKey), eqPointer("path\\failure"), eq(0), eq(0));

            RegistryKey registryKey = remoteRoot.resolve("path\\failure");
            InvalidRegistryHandleException exception = assertThrows(InvalidRegistryHandleException.class, registryKey::deleteIfExists);
            assertEquals("HKEY_LOCAL_MACHINE\\path\\failure", exception.path());
            assertEquals("test-machine", exception.machineName());

            verify(RegistryKey.api, never()).RegOpenKeyEx(notNull(), notNull(), anyInt(), anyInt(), notNull());
            verify(RegistryKey.api, never()).RegCloseKey(notNull());
        }
    }

    @Nested
    @DisplayName("handle")
    class Handle {

        @Test
        @DisplayName("with no arguments")
        void testNoArguments() {
            MemorySegment hKey = mockOpenAndClose(rootHKey, "Software\\JavaSoft\\Prefs");

            RegistryKey registryKey = remoteRoot.resolve("Software\\JavaSoft\\Prefs");
            try (var _ = registryKey.handle()) {
                // Do nothing
            }

            verify(RegistryKey.api, never()).RegCreateKeyEx(notNull(), notNull(), anyInt(), notNull(), anyInt(), anyInt(), notNull(), notNull(),
                    notNull());
            verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eqPointer("Software\\JavaSoft\\Prefs"), anyInt(), eq(WinNT.KEY_READ), notNull());
            verify(RegistryKey.api).RegCloseKey(hKey);
        }

        @Test
        @DisplayName("with CREATE")
        void testWithCreate() {
            MemorySegment hKey = newHKEY();

            when(RegistryKey.api.RegCreateKeyEx(eq(rootHKey), eqPointer("Software\\JavaSoft\\Prefs"),
                    anyInt(), notNull(), anyInt(), anyInt(), notNull(), notNull(), notNull()))
                    .thenAnswer(i -> {
                        setHKEY(i.getArgument(7, MemorySegment.class), hKey);
                        // disposition doesn't matter

                        return WinError.ERROR_SUCCESS;
                    });

            RegistryKey registryKey = remoteRoot.resolve("Software\\JavaSoft\\Prefs");
            try (var _ = registryKey.handle(RegistryKey.HandleOption.CREATE)) {
                // Do nothing
            }

            verify(RegistryKey.api, never()).RegOpenKeyEx(notNull(), notNull(), anyInt(), anyInt(), notNull());
            verify(RegistryKey.api).RegCreateKeyEx(eq(rootHKey), eqPointer("Software\\JavaSoft\\Prefs"),
                    anyInt(), notNull(), anyInt(), eq(WinNT.KEY_READ), notNull(), notNull(), notNull());
            verify(RegistryKey.api).RegCloseKey(hKey);
        }

        @Test
        @DisplayName("with MANAGE_VALUES")
        void testWithManageValues() {
            MemorySegment hKey = mockOpenAndClose(rootHKey, "Software\\JavaSoft\\Prefs");

            RegistryKey registryKey = remoteRoot.resolve("Software\\JavaSoft\\Prefs");
            try (var _ = registryKey.handle(RegistryKey.HandleOption.MANAGE_VALUES)) {
                // Do nothing
            }

            verify(RegistryKey.api, never()).RegCreateKeyEx(notNull(), notNull(), anyInt(), notNull(), anyInt(), anyInt(), notNull(), notNull(),
                    notNull());
            verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eqPointer("Software\\JavaSoft\\Prefs"), anyInt(),
                    eq(WinNT.KEY_READ | WinNT.KEY_SET_VALUE), notNull());
            verify(RegistryKey.api).RegCloseKey(hKey);
        }

        @Test
        @DisplayName("with CREATE and MANAGE_VALUES")
        void testWithCreateAndManageValues() {
            MemorySegment hKey = newHKEY();

            when(RegistryKey.api.RegCreateKeyEx(eq(rootHKey), eqPointer("Software\\JavaSoft\\Prefs"),
                    anyInt(), notNull(), anyInt(), anyInt(), notNull(), notNull(), notNull()))
                    .thenAnswer(i -> {
                        setHKEY(i.getArgument(7, MemorySegment.class), hKey);
                        // disposition doesn't matter

                        return WinError.ERROR_SUCCESS;
                    });

            RegistryKey registryKey = remoteRoot.resolve("Software\\JavaSoft\\Prefs");
            try (var _ = registryKey.handle(RegistryKey.HandleOption.CREATE, RegistryKey.HandleOption.MANAGE_VALUES)) {
                // Do nothing
            }

            verify(RegistryKey.api, never()).RegOpenKeyEx(notNull(), notNull(), anyInt(), anyInt(), notNull());
            verify(RegistryKey.api).RegCreateKeyEx(eq(rootHKey), eqPointer("Software\\JavaSoft\\Prefs"),
                    anyInt(), notNull(), anyInt(), eq(WinNT.KEY_READ | WinNT.KEY_SET_VALUE), notNull(), notNull(), notNull());
            verify(RegistryKey.api).RegCloseKey(hKey);
        }

        @Test
        @DisplayName("close twice")
        void testCloseTwice() {
            MemorySegment hKey = mockOpenAndClose(rootHKey, "Software\\JavaSoft\\Prefs");

            RegistryKey registryKey = remoteRoot.resolve("Software\\JavaSoft\\Prefs");
            try (RegistryKey.Handle handle = registryKey.handle()) {
                handle.close();
            }

            verify(RegistryKey.api, never()).RegCreateKeyEx(notNull(), notNull(), anyInt(), notNull(), anyInt(), anyInt(), notNull(), notNull(),
                    notNull());
            verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eqPointer("Software\\JavaSoft\\Prefs"), anyInt(), eq(WinNT.KEY_READ), notNull());
            verify(RegistryKey.api).RegCloseKey(hKey);
        }

        @Test
        @DisplayName("open failure")
        void testOpenFailure() {
            mockOpenFailure(rootHKey, "path\\failure", WinError.ERROR_ACCESS_DENIED);

            RegistryKey registryKey = remoteRoot.resolve("path\\failure");
            RegistryAccessDeniedException exception = assertThrows(RegistryAccessDeniedException.class, registryKey::handle);
            assertEquals("HKEY_LOCAL_MACHINE\\path\\failure", exception.path());
            assertEquals("test-machine", exception.machineName());

            verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eqPointer("path\\failure"), anyInt(), anyInt(), notNull());
            verify(RegistryKey.api, never()).RegCreateKeyEx(notNull(), notNull(), anyInt(), notNull(), anyInt(), anyInt(), notNull(), notNull(),
                    notNull());
            verify(RegistryKey.api, never()).RegCloseKey(notNull());
        }

        @Test
        @DisplayName("create failure")
        void testCreateFailure() {
            doReturn(WinError.ERROR_ACCESS_DENIED).when(RegistryKey.api)
                    .RegCreateKeyEx(eq(rootHKey), eqPointer("path\\failure"), anyInt(), notNull(), anyInt(), anyInt(), notNull(), notNull(),
                            notNull());

            RegistryKey registryKey = remoteRoot.resolve("path\\failure");
            RegistryAccessDeniedException exception = assertThrows(RegistryAccessDeniedException.class,
                    () -> registryKey.handle(RegistryKey.HandleOption.CREATE));
            assertEquals("HKEY_LOCAL_MACHINE\\path\\failure", exception.path());
            assertEquals("test-machine", exception.machineName());

            verify(RegistryKey.api).RegCreateKeyEx(eq(rootHKey), eqPointer("path\\failure"), anyInt(), notNull(), anyInt(), anyInt(), notNull(),
                    notNull(), notNull());
            verify(RegistryKey.api, never()).RegOpenKeyEx(notNull(), notNull(), anyInt(), anyInt(), notNull());
            verify(RegistryKey.api, never()).RegCloseKey(notNull());
        }

        @Test
        @DisplayName("close failure")
        void testCloseFailure() {
            MemorySegment hKey = mockOpen(rootHKey, "path\\failure");

            mockClose(hKey, WinError.ERROR_INVALID_HANDLE);

            mockValue(hKey, StringValue.of("test", "test"), WinError.ERROR_ACCESS_DENIED);

            RegistryKey registryKey = remoteRoot.resolve("path\\failure");
            RegistryAccessDeniedException exception = assertThrows(RegistryAccessDeniedException.class, () -> triggerCloseFailure(registryKey));
            assertEquals("HKEY_LOCAL_MACHINE\\path\\failure", exception.path());
            assertEquals("test-machine", exception.machineName());
            assertThat(exception.getSuppressed(), arrayContaining(instanceOf(InvalidRegistryHandleException.class)));

            verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eqPointer("path\\failure"), anyInt(), anyInt(), notNull());
            verify(RegistryKey.api).RegCloseKey(hKey);
        }

        private void triggerCloseFailure(RegistryKey registryKey) {
            try (RegistryKey.Handle handle = registryKey.handle()) {
                handle.getValue("test", RegistryValue.class);
            }
        }
    }

    @Test
    @DisplayName("compareTo")
    void testCompareTo() {
        List<RegistryKey> registryKeys = Arrays.asList(
                remoteRoot.resolve("Software\\JavaSoft\\Prefs"),
                remoteRoot.resolve("Software\\JavaSoft"),
                remoteRoot.resolve("Software"),
                remoteRoot,
                RegistryKey.HKEY_CURRENT_USER.resolve("Software\\JavaSoft\\Prefs"),
                RegistryKey.HKEY_CURRENT_USER.resolve("Software\\JavaSoft"),
                RegistryKey.HKEY_CURRENT_USER.resolve("Software"),
                RegistryKey.HKEY_CURRENT_USER
        );
        registryKeys.sort(null);

        List<RegistryKey> expected = List.of(
                RegistryKey.HKEY_CURRENT_USER,
                RegistryKey.HKEY_CURRENT_USER.resolve("Software"),
                RegistryKey.HKEY_CURRENT_USER.resolve("Software\\JavaSoft"),
                RegistryKey.HKEY_CURRENT_USER.resolve("Software\\JavaSoft\\Prefs"),
                remoteRoot,
                remoteRoot.resolve("Software"),
                remoteRoot.resolve("Software\\JavaSoft"),
                remoteRoot.resolve("Software\\JavaSoft\\Prefs")
        );

        assertEquals(expected, registryKeys);
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("equalsArguments")
    @DisplayName("equals")
    void testEquals(RegistryKey value, Object other, boolean expected) {
        assertEquals(expected, value.equals(other));
    }

    Arguments[] equalsArguments() {
        RegistryKey registryKey = remoteRoot.resolve("Software\\JavaSoft\\Prefs");

        return new Arguments[] {
                arguments(registryKey, registryKey, true),
                arguments(registryKey, remoteRoot.resolve("Software\\JavaSoft\\Prefs"), true),
                arguments(registryKey, remoteRoot.resolve("Software\\JavaSoft\\prefs"), false),
                arguments(registryKey, RegistryKey.HKEY_LOCAL_MACHINE.resolve("Software\\JavaSoft\\Prefs"), false),
                arguments(registryKey, "foo", false),
                arguments(registryKey, null, false),
        };
    }

    @Test
    @DisplayName("hashCode")
    void testHashCode() {
        RegistryKey registryKey = remoteRoot.resolve("Software\\JavaSoft\\Prefs");

        assertEquals(registryKey.hashCode(), registryKey.hashCode());
        assertEquals(registryKey.hashCode(), remoteRoot.resolve("Software\\JavaSoft\\Prefs").hashCode());
    }
}
