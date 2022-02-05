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
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayContaining;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.AdditionalMatchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import com.sun.jna.platform.win32.WinError;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.platform.win32.WinReg;
import com.sun.jna.platform.win32.WinReg.HKEY;
import com.sun.jna.platform.win32.WinReg.HKEYByReference;
import com.sun.jna.ptr.IntByReference;

@SuppressWarnings("nls")
@TestInstance(Lifecycle.PER_CLASS)
class RemoteSubKeyTest extends RegistryKeyTestBase {

    private RemoteRegistryKey remoteRoot;
    private HKEY rootHKey;

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

        verify(RegistryKey.api).RegConnectRegistry(eq("test-machine"), eq(WinReg.HKEY_LOCAL_MACHINE), any());
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
            HKEY hKey = mockOpenAndClose(rootHKey, "Software\\JavaSoft\\Prefs");

            mockSubKeys(hKey, "child1", "child2", "child3");

            RegistryKey registryKey = remoteRoot.resolve("Software\\JavaSoft\\Prefs");
            try (Stream<RegistryKey> stream = registryKey.subKeys()) {
                List<RegistryKey> subKeys = stream.collect(Collectors.toList());

                List<RegistryKey> expected = Arrays.asList(
                        registryKey.resolve("child1"),
                        registryKey.resolve("child2"),
                        registryKey.resolve("child3")
                );

                assertEquals(expected, subKeys);
            }

            verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eq("Software\\JavaSoft\\Prefs"), anyInt(), anyInt(), any());
            verify(RegistryKey.api).RegCloseKey(hKey);
        }

        @Test
        @DisplayName("non-existing key")
        void testNonExistingKey() {
            mockOpenFailure(rootHKey, "path\\non-existing", WinError.ERROR_FILE_NOT_FOUND);

            RegistryKey registryKey = remoteRoot.resolve("path\\non-existing");
            NoSuchRegistryKeyException exception = assertThrows(NoSuchRegistryKeyException.class, registryKey::subKeys);
            assertEquals("HKEY_LOCAL_MACHINE\\path\\non-existing", exception.path());

            verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eq("path\\non-existing"), anyInt(), anyInt(), any());
            verify(RegistryKey.api, never()).RegCloseKey(any());
        }

        @Nested
        @DisplayName("query failure")
        class QueryFailure {

            @Test
            @DisplayName("with successful close")
            void testSuccessfulClose() {
                HKEY hKey = mockOpenAndClose(rootHKey, "path\\failure");

                when(RegistryKey.api.RegQueryInfoKey(eq(hKey), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                        .thenReturn(WinError.ERROR_FILE_NOT_FOUND);

                RegistryKey registryKey = remoteRoot.resolve("path\\failure");
                NoSuchRegistryKeyException exception = assertThrows(NoSuchRegistryKeyException.class, registryKey::subKeys);
                assertEquals("HKEY_LOCAL_MACHINE\\path\\failure", exception.path());

                verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eq("path\\failure"), anyInt(), anyInt(), any());
                verify(RegistryKey.api).RegCloseKey(hKey);
            }

            @Test
            @DisplayName("with close failure")
            void testCloseFailure() {
                HKEY hKey = mockOpen(rootHKey, "path\\failure");

                mockClose(hKey, WinError.ERROR_INVALID_HANDLE);

                when(RegistryKey.api.RegQueryInfoKey(eq(hKey), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                        .thenReturn(WinError.ERROR_FILE_NOT_FOUND);

                RegistryKey registryKey = remoteRoot.resolve("path\\failure");
                NoSuchRegistryKeyException exception = assertThrows(NoSuchRegistryKeyException.class, registryKey::subKeys);
                assertEquals("HKEY_LOCAL_MACHINE\\path\\failure", exception.path());
                assertThat(exception.getSuppressed(), arrayContaining(instanceOf(InvalidRegistryHandleException.class)));

                verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eq("path\\failure"), anyInt(), anyInt(), any());
                verify(RegistryKey.api).RegCloseKey(hKey);
            }
        }

        @Test
        @DisplayName("enum failure")
        void testEnumFailure() {
            HKEY hKey = mockOpenAndClose(rootHKey, "path\\failure");

            when(RegistryKey.api.RegQueryInfoKey(eq(hKey), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(WinError.ERROR_SUCCESS);

            when(RegistryKey.api.RegEnumKeyEx(eq(hKey), eq(0), any(), any(), any(), any(), any(), any()))
                    .thenReturn(WinError.ERROR_FILE_NOT_FOUND);

            RegistryKey registryKey = remoteRoot.resolve("path\\failure");
            try (Stream<RegistryKey> stream = registryKey.subKeys()) {
                Collector<RegistryKey, ?, ?> collector = Collectors.toList();
                NoSuchRegistryKeyException exception = assertThrows(NoSuchRegistryKeyException.class, () -> stream.collect(collector));
                assertEquals("HKEY_LOCAL_MACHINE\\path\\failure", exception.path());
            }

            verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eq("path\\failure"), anyInt(), anyInt(), any());
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
                List<RegistryKey> registryKeys = stream.collect(Collectors.toList());

                List<RegistryKey> expected = Arrays.asList(registryKey);

                assertEquals(expected, registryKeys);
            }

            verify(RegistryKey.api, never()).RegOpenKeyEx(any(), any(), anyInt(), anyInt(), any());
            verify(RegistryKey.api, never()).RegCloseKey(any());
        }

        @Nested
        @DisplayName("maxDepth == 1")
        class MaxDepthIsOne {

            @Test
            @DisplayName("subKeys first")
            void testSubKeysFirst() {
                HKEY hKey = mockOpenAndClose(rootHKey, "path");

                mockSubKeys(hKey, "subKey1", "subKey2", "subKey3");

                RegistryKey registryKey = remoteRoot.resolve("path");
                try (Stream<RegistryKey> stream = registryKey.traverse(1, RegistryKey.TraverseOption.SUB_KEYS_FIRST)) {
                    List<RegistryKey> registryKeys = stream.collect(Collectors.toList());

                    List<RegistryKey> expected = Arrays.asList(
                            registryKey.resolve("subKey1"),
                            registryKey.resolve("subKey2"),
                            registryKey.resolve("subKey3"),
                            registryKey
                    );

                    assertEquals(expected, registryKeys);
                }

                verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eq("path"), anyInt(), anyInt(), any());
                verify(RegistryKey.api).RegOpenKeyEx(any(), any(), anyInt(), anyInt(), any());
                verify(RegistryKey.api).RegCloseKey(hKey);
                verify(RegistryKey.api).RegCloseKey(any());
            }

            @Test
            @DisplayName("subKeys not first")
            void testSubKeysNotFirst() {
                HKEY hKey = mockOpenAndClose(rootHKey, "path");

                mockSubKeys(hKey, "subKey1", "subKey2", "subKey3");

                RegistryKey registryKey = remoteRoot.resolve("path");
                try (Stream<RegistryKey> stream = registryKey.traverse(1)) {
                    List<RegistryKey> registryKeys = stream.collect(Collectors.toList());

                    List<RegistryKey> expected = Arrays.asList(
                            registryKey,
                            registryKey.resolve("subKey1"),
                            registryKey.resolve("subKey2"),
                            registryKey.resolve("subKey3")
                    );

                    assertEquals(expected, registryKeys);
                }

                verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eq("path"), anyInt(), anyInt(), any());
                verify(RegistryKey.api).RegOpenKeyEx(any(), any(), anyInt(), anyInt(), any());
                verify(RegistryKey.api).RegCloseKey(hKey);
                verify(RegistryKey.api).RegCloseKey(any());
            }
        }

        @Nested
        @DisplayName("no maxDepth")
        class NoMaxDepth {

            @Test
            @DisplayName("subKeys first")
            void testSubKeysFirst() {
                HKEY hKey = mockOpenAndClose(rootHKey, "path");
                HKEY subKey1 = mockOpenAndClose(rootHKey, "path\\subKey1");
                HKEY subKey2 = mockOpenAndClose(rootHKey, "path\\subKey2");
                HKEY subKey3 = mockOpenAndClose(rootHKey, "path\\subKey3");
                HKEY subKey11 = mockOpenAndClose(rootHKey, "path\\subKey1\\subKey11");
                HKEY subKey12 = mockOpenAndClose(rootHKey, "path\\subKey1\\subKey12");
                HKEY subKey13 = mockOpenAndClose(rootHKey, "path\\subKey1\\subKey13");
                HKEY subKey21 = mockOpenAndClose(rootHKey, "path\\subKey2\\subKey21");
                HKEY subKey22 = mockOpenAndClose(rootHKey, "path\\subKey2\\subKey22");
                HKEY subKey23 = mockOpenAndClose(rootHKey, "path\\subKey2\\subKey23");
                HKEY subKey31 = mockOpenAndClose(rootHKey, "path\\subKey3\\subKey31");
                HKEY subKey32 = mockOpenAndClose(rootHKey, "path\\subKey3\\subKey32");
                HKEY subKey33 = mockOpenAndClose(rootHKey, "path\\subKey3\\subKey33");

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
                    List<RegistryKey> registryKeys = stream.collect(Collectors.toList());

                    List<RegistryKey> expected = Arrays.asList(
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

                verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eq("path"), anyInt(), anyInt(), any());
                verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eq("path\\subKey1"), anyInt(), anyInt(), any());
                verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eq("path\\subKey2"), anyInt(), anyInt(), any());
                verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eq("path\\subKey3"), anyInt(), anyInt(), any());
                verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eq("path\\subKey1\\subKey11"), anyInt(), anyInt(), any());
                verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eq("path\\subKey1\\subKey12"), anyInt(), anyInt(), any());
                verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eq("path\\subKey1\\subKey13"), anyInt(), anyInt(), any());
                verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eq("path\\subKey2\\subKey21"), anyInt(), anyInt(), any());
                verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eq("path\\subKey2\\subKey22"), anyInt(), anyInt(), any());
                verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eq("path\\subKey2\\subKey23"), anyInt(), anyInt(), any());
                verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eq("path\\subKey3\\subKey31"), anyInt(), anyInt(), any());
                verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eq("path\\subKey3\\subKey32"), anyInt(), anyInt(), any());
                verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eq("path\\subKey3\\subKey33"), anyInt(), anyInt(), any());
                verify(RegistryKey.api, times(13)).RegOpenKeyEx(any(), any(), anyInt(), anyInt(), any());
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
                verify(RegistryKey.api, times(13)).RegCloseKey(any());
            }

            @Test
            @DisplayName("subKeys not first")
            void testSubKeysNotFirst() {
                HKEY hKey = mockOpenAndClose(rootHKey, "path");
                HKEY subKey1 = mockOpenAndClose(rootHKey, "path\\subKey1");
                HKEY subKey2 = mockOpenAndClose(rootHKey, "path\\subKey2");
                HKEY subKey3 = mockOpenAndClose(rootHKey, "path\\subKey3");
                HKEY subKey11 = mockOpenAndClose(rootHKey, "path\\subKey1\\subKey11");
                HKEY subKey12 = mockOpenAndClose(rootHKey, "path\\subKey1\\subKey12");
                HKEY subKey13 = mockOpenAndClose(rootHKey, "path\\subKey1\\subKey13");
                HKEY subKey21 = mockOpenAndClose(rootHKey, "path\\subKey2\\subKey21");
                HKEY subKey22 = mockOpenAndClose(rootHKey, "path\\subKey2\\subKey22");
                HKEY subKey23 = mockOpenAndClose(rootHKey, "path\\subKey2\\subKey23");
                HKEY subKey31 = mockOpenAndClose(rootHKey, "path\\subKey3\\subKey31");
                HKEY subKey32 = mockOpenAndClose(rootHKey, "path\\subKey3\\subKey32");
                HKEY subKey33 = mockOpenAndClose(rootHKey, "path\\subKey3\\subKey33");

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
                    List<RegistryKey> registryKeys = stream.collect(Collectors.toList());

                    List<RegistryKey> expected = Arrays.asList(
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

                verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eq("path"), anyInt(), anyInt(), any());
                verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eq("path\\subKey1"), anyInt(), anyInt(), any());
                verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eq("path\\subKey2"), anyInt(), anyInt(), any());
                verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eq("path\\subKey3"), anyInt(), anyInt(), any());
                verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eq("path\\subKey1\\subKey11"), anyInt(), anyInt(), any());
                verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eq("path\\subKey1\\subKey12"), anyInt(), anyInt(), any());
                verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eq("path\\subKey1\\subKey13"), anyInt(), anyInt(), any());
                verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eq("path\\subKey2\\subKey21"), anyInt(), anyInt(), any());
                verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eq("path\\subKey2\\subKey22"), anyInt(), anyInt(), any());
                verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eq("path\\subKey2\\subKey23"), anyInt(), anyInt(), any());
                verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eq("path\\subKey3\\subKey31"), anyInt(), anyInt(), any());
                verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eq("path\\subKey3\\subKey32"), anyInt(), anyInt(), any());
                verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eq("path\\subKey3\\subKey33"), anyInt(), anyInt(), any());
                verify(RegistryKey.api, times(13)).RegOpenKeyEx(any(), any(), anyInt(), anyInt(), any());
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
                verify(RegistryKey.api, times(13)).RegCloseKey(any());
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

                HKEY hKey = mockOpenAndClose(rootHKey, "Software\\JavaSoft\\Prefs");

                mockValues(hKey, stringValue, binaryValue, wordValue);

                RegistryKey registryKey = remoteRoot.resolve("Software\\JavaSoft\\Prefs");
                try (Stream<RegistryValue> stream = registryKey.values()) {
                    List<RegistryValue> values = stream.collect(Collectors.toList());

                    List<RegistryValue> expected = Arrays.asList(stringValue, binaryValue, wordValue);

                    assertEquals(expected, values);
                }

                verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eq("Software\\JavaSoft\\Prefs"), anyInt(), anyInt(), any());
                verify(RegistryKey.api).RegCloseKey(hKey);
            }

            @Test
            @DisplayName("with name filter")
            void testWithNameFilter() {
                StringValue stringValue = StringValue.of("string", "value");
                BinaryValue binaryValue = BinaryValue.of("binary", randomData());
                DWordValue wordValue = DWordValue.of("dword", 13);

                HKEY hKey = mockOpenAndClose(rootHKey, "Software\\JavaSoft\\Prefs");

                mockValues(hKey, stringValue, binaryValue, wordValue);

                RegistryKey registryKey = remoteRoot.resolve("Software\\JavaSoft\\Prefs");
                RegistryValue.Filter filter = RegistryValue.filter().name(s -> s.contains("i"));
                try (Stream<RegistryValue> stream = registryKey.values(filter)) {
                    List<RegistryValue> values = stream.collect(Collectors.toList());

                    List<RegistryValue> expected = Arrays.asList(stringValue, binaryValue);

                    assertEquals(expected, values);
                }

                verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eq("Software\\JavaSoft\\Prefs"), anyInt(), anyInt(), any());
                verify(RegistryKey.api).RegCloseKey(hKey);
            }

            @Test
            @DisplayName("with type filter")
            void testWithTypeFilter() {
                StringValue stringValue = StringValue.of("string", "value");
                BinaryValue binaryValue = BinaryValue.of("binary", randomData());
                DWordValue wordValue = DWordValue.of("dword", 13);

                HKEY hKey = mockOpenAndClose(rootHKey, "Software\\JavaSoft\\Prefs");

                mockValues(hKey, stringValue, binaryValue, wordValue);

                RegistryKey registryKey = remoteRoot.resolve("Software\\JavaSoft\\Prefs");
                RegistryValue.Filter filter = RegistryValue.filter().strings().words();
                try (Stream<RegistryValue> stream = registryKey.values(filter)) {
                    List<RegistryValue> values = stream.collect(Collectors.toList());

                    List<RegistryValue> expected = Arrays.asList(stringValue, wordValue);

                    assertEquals(expected, values);
                }

                verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eq("Software\\JavaSoft\\Prefs"), anyInt(), anyInt(), any());
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

            verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eq("path\\non-existing"), anyInt(), anyInt(), any());
            verify(RegistryKey.api, never()).RegCloseKey(any());
        }

        @Nested
        @DisplayName("query failure")
        class QueryFailure {

            @Test
            @DisplayName("with successful close")
            void testSuccessfulClose() {
                HKEY hKey = mockOpenAndClose(rootHKey, "path\\failure");

                when(RegistryKey.api.RegQueryInfoKey(eq(hKey), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                        .thenReturn(WinError.ERROR_FILE_NOT_FOUND);

                RegistryKey registryKey = remoteRoot.resolve("path\\failure");
                NoSuchRegistryKeyException exception = assertThrows(NoSuchRegistryKeyException.class, registryKey::values);
                assertEquals("HKEY_LOCAL_MACHINE\\path\\failure", exception.path());

                verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eq("path\\failure"), anyInt(), anyInt(), any());
                verify(RegistryKey.api).RegCloseKey(hKey);
            }

            @Test
            @DisplayName("with close failure")
            void testCloseFailure() {
                HKEY hKey = mockOpen(rootHKey, "path\\failure");

                mockClose(hKey, WinError.ERROR_INVALID_HANDLE);

                when(RegistryKey.api.RegQueryInfoKey(eq(hKey), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                        .thenReturn(WinError.ERROR_FILE_NOT_FOUND);

                RegistryKey registryKey = remoteRoot.resolve("path\\failure");
                NoSuchRegistryKeyException exception = assertThrows(NoSuchRegistryKeyException.class, registryKey::values);
                assertEquals("HKEY_LOCAL_MACHINE\\path\\failure", exception.path());
                assertThat(exception.getSuppressed(), arrayContaining(instanceOf(InvalidRegistryHandleException.class)));

                verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eq("path\\failure"), anyInt(), anyInt(), any());
                verify(RegistryKey.api).RegCloseKey(hKey);
            }
        }

        @Test
        @DisplayName("enum failure")
        void testEnumFailure() {
            HKEY hKey = mockOpenAndClose(rootHKey, "path\\failure");

            when(RegistryKey.api.RegQueryInfoKey(eq(hKey), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(WinError.ERROR_SUCCESS);

            when(RegistryKey.api.RegEnumValue(eq(hKey), eq(0), any(), any(), any(), any(), any(byte[].class), any()))
                    .thenReturn(WinError.ERROR_FILE_NOT_FOUND);

            RegistryKey registryKey = remoteRoot.resolve("path\\failure");
            try (Stream<RegistryValue> stream = registryKey.values()) {
                Collector<RegistryValue, ?, ?> collector = Collectors.toList();
                NoSuchRegistryKeyException exception = assertThrows(NoSuchRegistryKeyException.class, () -> stream.collect(collector));
                assertEquals("HKEY_LOCAL_MACHINE\\path\\failure", exception.path());
            }

            verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eq("path\\failure"), anyInt(), anyInt(), any());
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

            HKEY hKey = mockOpenAndClose(rootHKey, "Software\\JavaSoft\\Prefs");

            mockValue(hKey, stringValue);

            RegistryKey registryKey = remoteRoot.resolve("Software\\JavaSoft\\Prefs");
            StringValue value = registryKey.getValue("string", StringValue.class);
            assertEquals(stringValue, value);

            verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eq("Software\\JavaSoft\\Prefs"), anyInt(), anyInt(), any());
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

            verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eq("path\\non-existing"), anyInt(), anyInt(), any());
            verify(RegistryKey.api, never()).RegCloseKey(any());
        }

        @Test
        @DisplayName("non-existing value")
        void testNonExistingValue() {
            HKEY hKey = mockOpenAndClose(rootHKey, "path\\non-existing");

            when(RegistryKey.api.RegQueryValueEx(eq(hKey), eq("string"), anyInt(), any(), (byte[]) isNull(), any()))
                    .thenReturn(WinError.ERROR_FILE_NOT_FOUND);

            RegistryKey registryKey = remoteRoot.resolve("path\\non-existing");
            NoSuchRegistryValueException exception = assertThrows(NoSuchRegistryValueException.class,
                    () -> registryKey.getValue("string", RegistryValue.class));
            assertEquals("HKEY_LOCAL_MACHINE\\path\\non-existing", exception.path());
            assertEquals("string", exception.name());

            verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eq("path\\non-existing"), anyInt(), anyInt(), any());
            verify(RegistryKey.api).RegCloseKey(hKey);
        }

        @Test
        @DisplayName("failure")
        void testFailure() {
            HKEY hKey = mockOpenAndClose(rootHKey, "path\\failure");

            mockValue(hKey, StringValue.of("string", "value"), WinError.ERROR_INVALID_HANDLE);

            RegistryKey registryKey = remoteRoot.resolve("path\\failure");
            InvalidRegistryHandleException exception = assertThrows(InvalidRegistryHandleException.class,
                    () -> registryKey.getValue("string", RegistryValue.class));
            assertEquals("HKEY_LOCAL_MACHINE\\path\\failure", exception.path());

            verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eq("path\\failure"), anyInt(), anyInt(), any());
            verify(RegistryKey.api).RegCloseKey(hKey);
        }

        @Test
        @DisplayName("wrong value type")
        void testWrongValueType() {
            StringValue stringValue = StringValue.of("string", "value");

            HKEY hKey = mockOpenAndClose(rootHKey, "Software\\JavaSoft\\Prefs");

            mockValue(hKey, stringValue);

            RegistryKey registryKey = remoteRoot.resolve("Software\\JavaSoft\\Prefs");
            assertThrows(ClassCastException.class, () -> registryKey.getValue("string", DWordValue.class));

            verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eq("Software\\JavaSoft\\Prefs"), anyInt(), anyInt(), any());
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

            HKEY hKey = mockOpenAndClose(rootHKey, "Software\\JavaSoft\\Prefs");

            mockValue(hKey, stringValue);

            RegistryKey registryKey = remoteRoot.resolve("Software\\JavaSoft\\Prefs");
            Optional<StringValue> value = registryKey.findValue("string", StringValue.class);
            assertEquals(Optional.of(stringValue), value);

            verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eq("Software\\JavaSoft\\Prefs"), anyInt(), anyInt(), any());
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

            verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eq("path\\non-existing"), anyInt(), anyInt(), any());
            verify(RegistryKey.api, never()).RegCloseKey(any());
        }

        @Test
        @DisplayName("non-existing value")
        void testNonExistingValue() {
            HKEY hKey = mockOpenAndClose(rootHKey, "path\\non-existing");

            when(RegistryKey.api.RegQueryValueEx(eq(hKey), eq("string"), anyInt(), any(), (byte[]) isNull(), any()))
                    .thenReturn(WinError.ERROR_FILE_NOT_FOUND);

            RegistryKey registryKey = remoteRoot.resolve("path\\non-existing");
            Optional<DWordValue> value = registryKey.findValue("string", DWordValue.class);
            assertEquals(Optional.empty(), value);

            verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eq("path\\non-existing"), anyInt(), anyInt(), any());
            verify(RegistryKey.api).RegCloseKey(hKey);
        }

        @Test
        @DisplayName("failure")
        void testFailure() {
            HKEY hKey = mockOpenAndClose(rootHKey, "path\\failure");

            mockValue(hKey, StringValue.of("string", "value"), WinError.ERROR_INVALID_HANDLE);

            RegistryKey registryKey = remoteRoot.resolve("path\\failure");
            InvalidRegistryHandleException exception = assertThrows(InvalidRegistryHandleException.class,
                    () -> registryKey.findValue("string", RegistryValue.class));
            assertEquals("HKEY_LOCAL_MACHINE\\path\\failure", exception.path());

            verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eq("path\\failure"), anyInt(), anyInt(), any());
            verify(RegistryKey.api).RegCloseKey(hKey);
        }

        @Test
        @DisplayName("wrong value type")
        void testWrongValueType() {
            StringValue stringValue = StringValue.of("string", "value");

            HKEY hKey = mockOpenAndClose(rootHKey, "Software\\JavaSoft\\Prefs");

            mockValue(hKey, stringValue);

            RegistryKey registryKey = remoteRoot.resolve("Software\\JavaSoft\\Prefs");
            assertThrows(ClassCastException.class, () -> registryKey.findValue("string", DWordValue.class));

            verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eq("Software\\JavaSoft\\Prefs"), anyInt(), anyInt(), any());
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
            byte[] data = stringValue.rawData();

            HKEY hKey = mockOpenAndClose(rootHKey, "Software\\JavaSoft\\Prefs");

            when(RegistryKey.api.RegSetValueEx(eq(rootHKey), eq("string"), anyInt(), eq(WinNT.REG_SZ),
                    any(byte[].class), eq(data.length)))
                    .thenReturn(WinError.ERROR_SUCCESS);

            RegistryKey registryKey = remoteRoot.resolve("Software\\JavaSoft\\Prefs");
            registryKey.setValue(stringValue);

            ArgumentCaptor<byte[]> dataCaptor = ArgumentCaptor.forClass(byte[].class);
            verify(RegistryKey.api).RegSetValueEx(eq(hKey), eq("string"), anyInt(), eq(WinNT.REG_SZ), dataCaptor.capture(), eq(data.length));
            assertArrayEquals(data, dataCaptor.getValue());

            verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eq("Software\\JavaSoft\\Prefs"), anyInt(), anyInt(), any());
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

            verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eq("path\\non-existing"), anyInt(), anyInt(), any());
            verify(RegistryKey.api, never()).RegCloseKey(any());
            verify(RegistryKey.api, never()).RegSetValueEx(any(), any(), anyInt(), anyInt(), any(byte[].class), anyInt());
        }

        @Test
        @DisplayName("failure")
        void testFailure() {
            StringValue stringValue = StringValue.of("string", "value");

            HKEY hKey = mockOpenAndClose(rootHKey, "path\\failure");

            when(RegistryKey.api.RegSetValueEx(eq(hKey), eq("string"), anyInt(), eq(WinNT.REG_SZ), any(byte[].class), anyInt()))
                    .thenReturn(WinError.ERROR_INVALID_HANDLE);

            RegistryKey registryKey = remoteRoot.resolve("path\\failure");
            InvalidRegistryHandleException exception = assertThrows(InvalidRegistryHandleException.class, () -> registryKey.setValue(stringValue));
            assertEquals("HKEY_LOCAL_MACHINE\\path\\failure", exception.path());

            verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eq("path\\failure"), anyInt(), anyInt(), any());
            verify(RegistryKey.api).RegCloseKey(hKey);
        }
    }

    @Nested
    @DisplayName("deleteValue")
    class DeleteValue {

        @Test
        @DisplayName("success")
        void testSuccess() {
            HKEY hKey = mockOpenAndClose(rootHKey, "Software\\JavaSoft\\Prefs");

            when(RegistryKey.api.RegDeleteValue(hKey, "string")).thenReturn(WinError.ERROR_SUCCESS);

            RegistryKey registryKey = remoteRoot.resolve("Software\\JavaSoft\\Prefs");
            registryKey.deleteValue("string");

            verify(RegistryKey.api).RegDeleteValue(hKey, "string");

            verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eq("Software\\JavaSoft\\Prefs"), anyInt(), anyInt(), any());
            verify(RegistryKey.api).RegCloseKey(hKey);
        }

        @Test
        @DisplayName("non-existing key")
        void testNonExistingKey() {
            mockOpenFailure(rootHKey, "path\\non-existing", WinError.ERROR_FILE_NOT_FOUND);

            RegistryKey registryKey = remoteRoot.resolve("path\\non-existing");
            NoSuchRegistryKeyException exception = assertThrows(NoSuchRegistryKeyException.class, () -> registryKey.deleteValue("string"));
            assertEquals("HKEY_LOCAL_MACHINE\\path\\non-existing", exception.path());

            verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eq("path\\non-existing"), anyInt(), anyInt(), any());
            verify(RegistryKey.api, never()).RegCloseKey(any());
            verify(RegistryKey.api, never()).RegDeleteValue(any(), any());
        }

        @Test
        @DisplayName("failure")
        void testFailure() {
            HKEY hKey = mockOpenAndClose(rootHKey, "path\\failure");

            when(RegistryKey.api.RegDeleteValue(hKey, "string")).thenReturn(WinError.ERROR_FILE_NOT_FOUND);

            RegistryKey registryKey = remoteRoot.resolve("path\\failure");
            NoSuchRegistryValueException exception = assertThrows(NoSuchRegistryValueException.class, () -> registryKey.deleteValue("string"));
            assertEquals("HKEY_LOCAL_MACHINE\\path\\failure", exception.path());
            assertEquals("string", exception.name());

            verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eq("path\\failure"), anyInt(), anyInt(), any());
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
                HKEY hKey = mockOpenAndClose(rootHKey, "Software\\JavaSoft\\Prefs");

                when(RegistryKey.api.RegDeleteValue(hKey, "string")).thenReturn(WinError.ERROR_SUCCESS);

                RegistryKey registryKey = remoteRoot.resolve("Software\\JavaSoft\\Prefs");
                assertTrue(registryKey.deleteValueIfExists("string"));

                verify(RegistryKey.api).RegDeleteValue(hKey, "string");

                verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eq("Software\\JavaSoft\\Prefs"), anyInt(), anyInt(), any());
                verify(RegistryKey.api).RegCloseKey(hKey);
            }

            @Test
            @DisplayName("value didn't exist")
            void testValueDidntExist() {
                HKEY hKey = mockOpenAndClose(rootHKey, "Software\\JavaSoft\\Prefs");

                when(RegistryKey.api.RegDeleteValue(hKey, "string")).thenReturn(WinError.ERROR_FILE_NOT_FOUND);

                RegistryKey registryKey = remoteRoot.resolve("Software\\JavaSoft\\Prefs");
                assertFalse(registryKey.deleteValueIfExists("string"));

                verify(RegistryKey.api).RegDeleteValue(hKey, "string");

                verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eq("Software\\JavaSoft\\Prefs"), anyInt(), anyInt(), any());
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

            verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eq("path\\non-existing"), anyInt(), anyInt(), any());
            verify(RegistryKey.api, never()).RegCloseKey(any());
            verify(RegistryKey.api, never()).RegDeleteValue(any(), any());
        }

        @Test
        @DisplayName("failure")
        void testFailure() {
            HKEY hKey = mockOpenAndClose(rootHKey, "path\\failure");

            when(RegistryKey.api.RegDeleteValue(hKey, "string")).thenReturn(WinError.ERROR_INVALID_HANDLE);

            RegistryKey registryKey = remoteRoot.resolve("path\\failure");
            InvalidRegistryHandleException exception = assertThrows(InvalidRegistryHandleException.class,
                    () -> registryKey.deleteValueIfExists("string"));
            assertEquals("HKEY_LOCAL_MACHINE\\path\\failure", exception.path());

            verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eq("path\\failure"), anyInt(), anyInt(), any());
            verify(RegistryKey.api).RegCloseKey(hKey);
        }
    }

    @Nested
    @DisplayName("exists")
    class Exists {

        @Test
        @DisplayName("existing")
        void testExisting() {
            HKEY hKey = mockOpenAndClose(rootHKey, "path\\existing");

            RegistryKey registryKey = remoteRoot.resolve("path\\existing");
            assertTrue(registryKey.exists());

            verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eq("path\\existing"), anyInt(), anyInt(), any());
            verify(RegistryKey.api).RegCloseKey(hKey);
        }

        @Test
        @DisplayName("non-existing")
        void testNonExisting() {
            mockOpenFailure(rootHKey, "path\\non-existing", WinError.ERROR_FILE_NOT_FOUND);

            RegistryKey registryKey = remoteRoot.resolve("path\\non-existing");
            assertFalse(registryKey.exists());

            verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eq("path\\non-existing"), anyInt(), anyInt(), any());
            verify(RegistryKey.api, never()).RegCloseKey(any());
        }

        @Test
        @DisplayName("failure")
        void testFailure() {
            mockOpenFailure(rootHKey, "path\\failure", WinError.ERROR_INVALID_HANDLE);

            RegistryKey registryKey = remoteRoot.resolve("path\\failure");
            InvalidRegistryHandleException exception = assertThrows(InvalidRegistryHandleException.class, registryKey::exists);
            assertEquals("HKEY_LOCAL_MACHINE\\path\\failure", exception.path());

            verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eq("path\\failure"), anyInt(), anyInt(), any());
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

            when(RegistryKey.api.RegCreateKeyEx(eq(rootHKey), eq("path\\new"), anyInt(), any(), anyInt(), anyInt(), any(), any(), any()))
                    .thenAnswer(i -> {
                        i.getArgument(7, HKEYByReference.class).setValue(hKey);
                        i.getArgument(8, IntByReference.class).setValue(WinNT.REG_CREATED_NEW_KEY);

                        return WinError.ERROR_SUCCESS;
                    });

            RegistryKey registryKey = remoteRoot.resolve("path\\new");
            registryKey.create();

            verify(RegistryKey.api).RegCreateKeyEx(eq(rootHKey), eq("path\\new"), anyInt(), any(), anyInt(), anyInt(), any(), any(), any());
            verify(RegistryKey.api).RegCloseKey(hKey);
        }

        @Test
        @DisplayName("existing")
        void testCreateExisting() {
            HKEY hKey = newHKEY();

            when(RegistryKey.api.RegCreateKeyEx(eq(rootHKey), eq("path\\existing"), anyInt(), any(), anyInt(), anyInt(), any(), any(), any()))
                    .thenAnswer(i -> {
                        i.getArgument(7, HKEYByReference.class).setValue(hKey);
                        i.getArgument(8, IntByReference.class).setValue(WinNT.REG_OPENED_EXISTING_KEY);

                        return WinError.ERROR_SUCCESS;
                    });

            RegistryKey registryKey = remoteRoot.resolve("path\\existing");
            RegistryKeyAlreadyExistsException exception = assertThrows(RegistryKeyAlreadyExistsException.class, registryKey::create);
            assertEquals("HKEY_LOCAL_MACHINE\\path\\existing", exception.path());

            verify(RegistryKey.api).RegCreateKeyEx(eq(rootHKey), eq("path\\existing"), anyInt(), any(), anyInt(), anyInt(), any(), any(), any());
            verify(RegistryKey.api).RegCloseKey(hKey);
        }

        @Test
        @DisplayName("failure")
        void testFailure() {
            when(RegistryKey.api.RegCreateKeyEx(eq(rootHKey), eq("path\\failure"), anyInt(), any(), anyInt(), anyInt(), any(), any(), any()))
                    .thenReturn(WinError.ERROR_INVALID_HANDLE);

            RegistryKey registryKey = remoteRoot.resolve("path\\failure");
            InvalidRegistryHandleException exception = assertThrows(InvalidRegistryHandleException.class, registryKey::create);
            assertEquals("HKEY_LOCAL_MACHINE\\path\\failure", exception.path());

            verify(RegistryKey.api).RegCreateKeyEx(eq(rootHKey), eq("path\\failure"), anyInt(), any(), anyInt(), anyInt(), any(), any(), any());
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

            when(RegistryKey.api.RegCreateKeyEx(eq(rootHKey), eq("path\\new"), anyInt(), any(), anyInt(), anyInt(), any(), any(), any()))
                    .thenAnswer(i -> {
                        i.getArgument(7, HKEYByReference.class).setValue(hKey);
                        i.getArgument(8, IntByReference.class).setValue(WinNT.REG_CREATED_NEW_KEY);

                        return WinError.ERROR_SUCCESS;
                    });

            RegistryKey registryKey = remoteRoot.resolve("path\\new");
            assertTrue(registryKey.createIfNotExists());

            verify(RegistryKey.api).RegCreateKeyEx(eq(rootHKey), eq("path\\new"), anyInt(), any(), anyInt(), anyInt(), any(), any(), any());
            verify(RegistryKey.api).RegCloseKey(hKey);
        }

        @Test
        @DisplayName("existing")
        void testCreateExisting() {
            HKEY hKey = newHKEY();

            when(RegistryKey.api.RegCreateKeyEx(eq(rootHKey), eq("path\\existing"), anyInt(), any(), anyInt(), anyInt(), any(), any(), any()))
                    .thenAnswer(i -> {
                        i.getArgument(7, HKEYByReference.class).setValue(hKey);
                        i.getArgument(8, IntByReference.class).setValue(WinNT.REG_OPENED_EXISTING_KEY);

                        return WinError.ERROR_SUCCESS;
                    });

            RegistryKey registryKey = remoteRoot.resolve("path\\existing");
            assertFalse(registryKey.createIfNotExists());

            verify(RegistryKey.api).RegCreateKeyEx(eq(rootHKey), eq("path\\existing"), anyInt(), any(), anyInt(), anyInt(), any(), any(), any());
            verify(RegistryKey.api).RegCloseKey(hKey);
        }

        @Test
        @DisplayName("failure")
        void testFailure() {
            when(RegistryKey.api.RegCreateKeyEx(eq(rootHKey), eq("path\\failure"), anyInt(), any(), anyInt(), anyInt(), any(), any(), any()))
                    .thenReturn(WinError.ERROR_INVALID_HANDLE);

            RegistryKey registryKey = remoteRoot.resolve("path\\failure");
            InvalidRegistryHandleException exception = assertThrows(InvalidRegistryHandleException.class, registryKey::createIfNotExists);
            assertEquals("HKEY_LOCAL_MACHINE\\path\\failure", exception.path());

            verify(RegistryKey.api).RegCreateKeyEx(eq(rootHKey), eq("path\\failure"), anyInt(), any(), anyInt(), anyInt(), any(), any(), any());
            verify(RegistryKey.api, never()).RegCloseKey(any());
        }
    }

    @Nested
    @DisplayName("renameTo")
    class RenameTo {

        @Test
        @DisplayName("existing")
        void testRenameExisting() {
            when(RegistryKey.api.RegRenameKey(rootHKey, "path\\existing", "foo")).thenReturn(WinError.ERROR_SUCCESS);

            RegistryKey registryKey = remoteRoot.resolve("path\\existing");
            RegistryKey renamed = registryKey.renameTo("foo");
            assertEquals(registryKey.resolve("..\\foo"), renamed);

            verify(RegistryKey.api).RegRenameKey(rootHKey, "path\\existing", "foo");
            verify(RegistryKey.api, never()).RegOpenKeyEx(any(), any(), anyInt(), anyInt(), any());
            verify(RegistryKey.api, never()).RegCloseKey(any());
        }

        @Test
        @DisplayName("non-existing")
        void testNonExisting() {
            when(RegistryKey.api.RegRenameKey(rootHKey, "path\\non-existing", "foo")).thenReturn(WinError.ERROR_FILE_NOT_FOUND);

            RegistryKey registryKey = remoteRoot.resolve("path\\non-existing");
            NoSuchRegistryKeyException exception = assertThrows(NoSuchRegistryKeyException.class, () -> registryKey.renameTo("foo"));
            assertEquals("HKEY_LOCAL_MACHINE\\path\\non-existing", exception.path());

            verify(RegistryKey.api).RegRenameKey(rootHKey, "path\\non-existing", "foo");
            verify(RegistryKey.api, never()).RegOpenKeyEx(any(), any(), anyInt(), anyInt(), any());
            verify(RegistryKey.api, never()).RegCloseKey(any());
        }

        @Test
        @DisplayName("target exists")
        void testTargetExists() {
            when(RegistryKey.api.RegRenameKey(rootHKey, "path\\existing", "foo")).thenReturn(WinError.ERROR_ACCESS_DENIED);

            HKEY targetHkey = mockOpenAndClose(rootHKey, "path\\foo");

            RegistryKey registryKey = remoteRoot.resolve("path\\existing");
            RegistryKeyAlreadyExistsException exception = assertThrows(RegistryKeyAlreadyExistsException.class, () -> registryKey.renameTo("foo"));
            assertEquals("HKEY_LOCAL_MACHINE\\path\\foo", exception.path());

            verify(RegistryKey.api).RegRenameKey(rootHKey, "path\\existing", "foo");
            verify(RegistryKey.api, never()).RegOpenKeyEx(eq(rootHKey), not(eq("path\\foo")), anyInt(), anyInt(), any());
            verify(RegistryKey.api, never()).RegCloseKey(not(eq(targetHkey)));
        }

        @Test
        @DisplayName("access denied")
        void testAccessDenied() {
            when(RegistryKey.api.RegRenameKey(rootHKey, "path\\existing", "foo")).thenReturn(WinError.ERROR_ACCESS_DENIED);

            mockOpenFailure(rootHKey, "path\\foo", WinError.ERROR_FILE_NOT_FOUND);

            RegistryKey registryKey = remoteRoot.resolve("path\\existing");
            RegistryAccessDeniedException exception = assertThrows(RegistryAccessDeniedException.class, () -> registryKey.renameTo("foo"));
            assertEquals("HKEY_LOCAL_MACHINE\\path\\existing", exception.path());

            verify(RegistryKey.api).RegRenameKey(rootHKey, "path\\existing", "foo");
            verify(RegistryKey.api, never()).RegOpenKeyEx(not(eq(rootHKey)), not(eq("path\\foo")), anyInt(), anyInt(), any());
            verify(RegistryKey.api, never()).RegCloseKey(any());
        }

        @Test
        @DisplayName("access failure")
        void testFailure() {
            when(RegistryKey.api.RegRenameKey(rootHKey, "path\\existing", "foo")).thenReturn(WinError.ERROR_INVALID_HANDLE);

            RegistryKey registryKey = remoteRoot.resolve("path\\existing");
            InvalidRegistryHandleException exception = assertThrows(InvalidRegistryHandleException.class, () -> registryKey.renameTo("foo"));
            assertEquals("HKEY_LOCAL_MACHINE\\path\\existing", exception.path());

            verify(RegistryKey.api).RegRenameKey(rootHKey, "path\\existing", "foo");
            verify(RegistryKey.api, never()).RegOpenKeyEx(any(), any(), anyInt(), anyInt(), any());
            verify(RegistryKey.api, never()).RegCloseKey(any());
        }

        @Test
        @DisplayName("function not available")
        void testFunctionNotAvailable() {
            when(RegistryKey.api.RegRenameKey(rootHKey, "path\\existing", "foo"))
                    .thenThrow(new UnsatisfiedLinkError("Error looking up function 'RegRenameKey': The specified procedure could not be found."));

            RegistryKey registryKey = remoteRoot.resolve("path\\existing");
            UnsupportedOperationException exception = assertThrows(UnsupportedOperationException.class, () -> registryKey.renameTo("foo"));
            assertInstanceOf(UnsatisfiedLinkError.class, exception.getCause());

            verify(RegistryKey.api).RegRenameKey(rootHKey, "path\\existing", "foo");
            verify(RegistryKey.api, never()).RegOpenKeyEx(any(), any(), anyInt(), anyInt(), any());
            verify(RegistryKey.api, never()).RegCloseKey(any());
        }

        @Test
        @DisplayName("invalid name")
        void testInvalidName() {
            RegistryKey registryKey = remoteRoot.resolve("path\\existing");

            assertThrows(IllegalArgumentException.class, () -> registryKey.renameTo("\\foo"));

            verify(RegistryKey.api, never()).RegRenameKey(any(), any(), any());
        }
    }

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("existing")
        void testDeleteExisting() {
            when(RegistryKey.api.RegDeleteKey(rootHKey, "path\\existing")).thenReturn(WinError.ERROR_SUCCESS);

            RegistryKey registryKey = remoteRoot.resolve("path\\existing");
            registryKey.delete();

            verify(RegistryKey.api).RegDeleteKey(rootHKey, "path\\existing");
            verify(RegistryKey.api, never()).RegOpenKeyEx(any(), any(), anyInt(), anyInt(), any());
            verify(RegistryKey.api, never()).RegCloseKey(any());
        }

        @Test
        @DisplayName("non-existing")
        void testDeleteNonExisting() {
            when(RegistryKey.api.RegDeleteKey(rootHKey, "path\\non-existing")).thenReturn(WinError.ERROR_FILE_NOT_FOUND);

            RegistryKey registryKey = remoteRoot.resolve("path\\non-existing");
            NoSuchRegistryKeyException exception = assertThrows(NoSuchRegistryKeyException.class, registryKey::delete);
            assertEquals("HKEY_LOCAL_MACHINE\\path\\non-existing", exception.path());

            verify(RegistryKey.api, never()).RegOpenKeyEx(any(), any(), anyInt(), anyInt(), any());
            verify(RegistryKey.api, never()).RegCloseKey(any());
        }

        @Test
        @DisplayName("failure")
        void testFailure() {
            when(RegistryKey.api.RegDeleteKey(rootHKey, "path\\failure")).thenReturn(WinError.ERROR_INVALID_HANDLE);

            RegistryKey registryKey = remoteRoot.resolve("path\\failure");
            InvalidRegistryHandleException exception = assertThrows(InvalidRegistryHandleException.class, registryKey::delete);
            assertEquals("HKEY_LOCAL_MACHINE\\path\\failure", exception.path());

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
            when(RegistryKey.api.RegDeleteKey(rootHKey, "path\\existing")).thenReturn(WinError.ERROR_SUCCESS);

            RegistryKey registryKey = remoteRoot.resolve("path\\existing");
            assertTrue(registryKey.deleteIfExists());

            verify(RegistryKey.api).RegDeleteKey(rootHKey, "path\\existing");
            verify(RegistryKey.api, never()).RegOpenKeyEx(any(), any(), anyInt(), anyInt(), any());
            verify(RegistryKey.api, never()).RegCloseKey(any());
        }

        @Test
        @DisplayName("non-existing")
        void testDeleteNonExisting() {
            when(RegistryKey.api.RegDeleteKey(rootHKey, "path\\non-existing")).thenReturn(WinError.ERROR_FILE_NOT_FOUND);

            RegistryKey registryKey = remoteRoot.resolve("path\\non-existing");
            assertFalse(registryKey.deleteIfExists());

            verify(RegistryKey.api).RegDeleteKey(rootHKey, "path\\non-existing");
            verify(RegistryKey.api, never()).RegOpenKeyEx(any(), any(), anyInt(), anyInt(), any());
            verify(RegistryKey.api, never()).RegCloseKey(any());
        }

        @Test
        @DisplayName("failure")
        void testFailure() {
            when(RegistryKey.api.RegDeleteKey(rootHKey, "path\\failure")).thenReturn(WinError.ERROR_INVALID_HANDLE);

            RegistryKey registryKey = remoteRoot.resolve("path\\failure");
            InvalidRegistryHandleException exception = assertThrows(InvalidRegistryHandleException.class, registryKey::deleteIfExists);
            assertEquals("HKEY_LOCAL_MACHINE\\path\\failure", exception.path());

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
            HKEY hKey = mockOpenAndClose(rootHKey, "Software\\JavaSoft\\Prefs");

            RegistryKey registryKey = remoteRoot.resolve("Software\\JavaSoft\\Prefs");
            try (RegistryKey.Handle handle = registryKey.handle()) {
                // Do nothing
            }

            verify(RegistryKey.api, never()).RegCreateKeyEx(any(), any(), anyInt(), any(), anyInt(), anyInt(), any(), any(), any());
            verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eq("Software\\JavaSoft\\Prefs"), anyInt(), eq(WinNT.KEY_READ), any());
            verify(RegistryKey.api).RegCloseKey(hKey);
        }

        @Test
        @DisplayName("with CREATE")
        void testWithCreate() {
            HKEY hKey = newHKEY();

            when(RegistryKey.api.RegCreateKeyEx(eq(rootHKey), eq("Software\\JavaSoft\\Prefs"),
                    anyInt(), any(), anyInt(), anyInt(), any(), any(), any()))
                    .thenAnswer(i -> {
                        i.getArgument(7, HKEYByReference.class).setValue(hKey);
                        // disposition doesn't matter

                        return WinError.ERROR_SUCCESS;
                    });

            RegistryKey registryKey = remoteRoot.resolve("Software\\JavaSoft\\Prefs");
            try (RegistryKey.Handle handle = registryKey.handle(RegistryKey.HandleOption.CREATE)) {
                // Do nothing
            }

            verify(RegistryKey.api, never()).RegOpenKeyEx(any(), any(), anyInt(), anyInt(), any());
            verify(RegistryKey.api).RegCreateKeyEx(eq(rootHKey), eq("Software\\JavaSoft\\Prefs"),
                    anyInt(), any(), anyInt(), eq(WinNT.KEY_READ), any(), any(), any());
            verify(RegistryKey.api).RegCloseKey(hKey);
        }

        @Test
        @DisplayName("with MANAGE_VALUES")
        void testWithManageValues() {
            HKEY hKey = mockOpenAndClose(rootHKey, "Software\\JavaSoft\\Prefs");

            RegistryKey registryKey = remoteRoot.resolve("Software\\JavaSoft\\Prefs");
            try (RegistryKey.Handle handle = registryKey.handle(RegistryKey.HandleOption.MANAGE_VALUES)) {
                // Do nothing
            }

            verify(RegistryKey.api, never()).RegCreateKeyEx(any(), any(), anyInt(), any(), anyInt(), anyInt(), any(), any(), any());
            verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eq("Software\\JavaSoft\\Prefs"), anyInt(),
                    eq(WinNT.KEY_READ | WinNT.KEY_SET_VALUE), any());
            verify(RegistryKey.api).RegCloseKey(hKey);
        }

        @Test
        @DisplayName("with CREATE and MANAGE_VALUES")
        void testWithCreateAndManageValues() {
            HKEY hKey = newHKEY();

            when(RegistryKey.api.RegCreateKeyEx(eq(rootHKey), eq("Software\\JavaSoft\\Prefs"),
                    anyInt(), any(), anyInt(), anyInt(), any(), any(), any()))
                    .thenAnswer(i -> {
                        i.getArgument(7, HKEYByReference.class).setValue(hKey);
                        // disposition doesn't matter

                        return WinError.ERROR_SUCCESS;
                    });

            RegistryKey registryKey = remoteRoot.resolve("Software\\JavaSoft\\Prefs");
            try (RegistryKey.Handle handle = registryKey.handle(RegistryKey.HandleOption.CREATE, RegistryKey.HandleOption.MANAGE_VALUES)) {
                // Do nothing
            }

            verify(RegistryKey.api, never()).RegOpenKeyEx(any(), any(), anyInt(), anyInt(), any());
            verify(RegistryKey.api).RegCreateKeyEx(eq(rootHKey), eq("Software\\JavaSoft\\Prefs"),
                    anyInt(), any(), anyInt(), eq(WinNT.KEY_READ | WinNT.KEY_SET_VALUE), any(), any(), any());
            verify(RegistryKey.api).RegCloseKey(hKey);
        }

        @Test
        @DisplayName("close twice")
        void testCloseTwice() {
            HKEY hKey = mockOpenAndClose(rootHKey, "Software\\JavaSoft\\Prefs");

            RegistryKey registryKey = remoteRoot.resolve("Software\\JavaSoft\\Prefs");
            try (RegistryKey.Handle handle = registryKey.handle()) {
                handle.close();
            }

            verify(RegistryKey.api, never()).RegCreateKeyEx(any(), any(), anyInt(), any(), anyInt(), anyInt(), any(), any(), any());
            verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eq("Software\\JavaSoft\\Prefs"), anyInt(), eq(WinNT.KEY_READ), any());
            verify(RegistryKey.api).RegCloseKey(hKey);
        }

        @Test
        @DisplayName("open failure")
        void testOpenFailure() {
            mockOpenFailure(rootHKey, "path\\failure", WinError.ERROR_ACCESS_DENIED);

            RegistryKey registryKey = remoteRoot.resolve("path\\failure");
            RegistryAccessDeniedException exception = assertThrows(RegistryAccessDeniedException.class, registryKey::handle);
            assertEquals("HKEY_LOCAL_MACHINE\\path\\failure", exception.path());

            verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eq("path\\failure"), anyInt(), anyInt(), any());
            verify(RegistryKey.api, never()).RegCreateKeyEx(any(), any(), anyInt(), any(), anyInt(), anyInt(), any(), any(), any());
            verify(RegistryKey.api, never()).RegCloseKey(any());
        }

        @Test
        @DisplayName("create failure")
        void testCreateFailure() {
            when(RegistryKey.api.RegCreateKeyEx(eq(rootHKey), eq("path\\failure"), anyInt(), any(), anyInt(), anyInt(), any(), any(), any()))
                    .thenReturn(WinError.ERROR_ACCESS_DENIED);

            RegistryKey registryKey = remoteRoot.resolve("path\\failure");
            RegistryAccessDeniedException exception = assertThrows(RegistryAccessDeniedException.class,
                    () -> registryKey.handle(RegistryKey.HandleOption.CREATE));
            assertEquals("HKEY_LOCAL_MACHINE\\path\\failure", exception.path());

            verify(RegistryKey.api).RegCreateKeyEx(eq(rootHKey), eq("path\\failure"), anyInt(), any(), anyInt(), anyInt(), any(), any(), any());
            verify(RegistryKey.api, never()).RegOpenKeyEx(any(), any(), anyInt(), anyInt(), any());
            verify(RegistryKey.api, never()).RegCloseKey(any());
        }

        @Test
        @DisplayName("close failure")
        void testCloseFailure() {
            HKEY hKey = mockOpen(rootHKey, "path\\failure");

            mockClose(hKey, WinError.ERROR_INVALID_HANDLE);

            mockValue(hKey, StringValue.of("test", "test"), WinError.ERROR_ACCESS_DENIED);

            RegistryKey registryKey = remoteRoot.resolve("path\\failure");
            RegistryAccessDeniedException exception = assertThrows(RegistryAccessDeniedException.class, () -> triggerCloseFailure(registryKey));
            assertEquals("HKEY_LOCAL_MACHINE\\path\\failure", exception.path());
            assertThat(exception.getSuppressed(), arrayContaining(instanceOf(InvalidRegistryHandleException.class)));

            verify(RegistryKey.api).RegOpenKeyEx(eq(rootHKey), eq("path\\failure"), anyInt(), anyInt(), any());
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

        List<RegistryKey> expected = Arrays.asList(
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
