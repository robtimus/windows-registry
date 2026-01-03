/*
 * LocalSubKeyTest.java
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

import static com.github.robtimus.os.windows.registry.Advapi32.RegCloseKey;
import static com.github.robtimus.os.windows.registry.Advapi32.RegCreateKeyEx;
import static com.github.robtimus.os.windows.registry.Advapi32.RegDeleteKeyEx;
import static com.github.robtimus.os.windows.registry.Advapi32.RegDeleteValue;
import static com.github.robtimus.os.windows.registry.Advapi32.RegEnumKeyEx;
import static com.github.robtimus.os.windows.registry.Advapi32.RegEnumValue;
import static com.github.robtimus.os.windows.registry.Advapi32.RegOpenKeyEx;
import static com.github.robtimus.os.windows.registry.Advapi32.RegQueryInfoKey;
import static com.github.robtimus.os.windows.registry.Advapi32.RegQueryValueEx;
import static com.github.robtimus.os.windows.registry.Advapi32.RegRenameKey;
import static com.github.robtimus.os.windows.registry.Advapi32.RegSetValueEx;
import static com.github.robtimus.os.windows.registry.ForeignTestUtils.eqBytes;
import static com.github.robtimus.os.windows.registry.ForeignTestUtils.eqPointer;
import static com.github.robtimus.os.windows.registry.ForeignTestUtils.eqSize;
import static com.github.robtimus.os.windows.registry.ForeignTestUtils.isNULL;
import static com.github.robtimus.os.windows.registry.ForeignTestUtils.newHKEY;
import static com.github.robtimus.os.windows.registry.ForeignTestUtils.setHKEY;
import static com.github.robtimus.os.windows.registry.RegistryKeyMocks.mockClose;
import static com.github.robtimus.os.windows.registry.RegistryKeyMocks.mockOpen;
import static com.github.robtimus.os.windows.registry.RegistryKeyMocks.mockOpenAndClose;
import static com.github.robtimus.os.windows.registry.RegistryKeyMocks.mockOpenFailure;
import static com.github.robtimus.os.windows.registry.RegistryKeyMocks.mockSubKeys;
import static com.github.robtimus.os.windows.registry.RegistryKeyMocks.mockValue;
import static com.github.robtimus.os.windows.registry.RegistryKeyMocks.mockValues;
import static com.github.robtimus.os.windows.registry.RegistryValueTest.randomData;
import static com.github.robtimus.os.windows.registry.WindowsConstants.ERROR_ACCESS_DENIED;
import static com.github.robtimus.os.windows.registry.WindowsConstants.ERROR_FILE_NOT_FOUND;
import static com.github.robtimus.os.windows.registry.WindowsConstants.ERROR_INVALID_HANDLE;
import static com.github.robtimus.os.windows.registry.WindowsConstants.ERROR_SUCCESS;
import static com.github.robtimus.os.windows.registry.WindowsConstants.HKEY_CURRENT_USER;
import static com.github.robtimus.os.windows.registry.WindowsConstants.KEY_READ;
import static com.github.robtimus.os.windows.registry.WindowsConstants.KEY_SET_VALUE;
import static com.github.robtimus.os.windows.registry.WindowsConstants.REG_CREATED_NEW_KEY;
import static com.github.robtimus.os.windows.registry.WindowsConstants.REG_OPENED_EXISTING_KEY;
import static com.github.robtimus.os.windows.registry.WindowsConstants.REG_SZ;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

@SuppressWarnings("nls")
class LocalSubKeyTest extends RegistryTestBase {

    private static final LocalRegistry REGISTRY = Registry.local();

    @Nested
    @DisplayName("resolved")
    class Resolved {

        @Test
        @DisplayName("name")
        void testName() {
            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER.resolve("Software\\JavaSoft\\Prefs");
            assertEquals("Prefs", registryKey.name());
        }

        @Test
        @DisplayName("path")
        void testPath() {
            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER.resolve("Software\\JavaSoft\\Prefs");
            assertEquals("HKEY_CURRENT_USER\\Software\\JavaSoft\\Prefs", registryKey.path());
        }

        @Test
        @DisplayName("isRoot")
        void testIsRoot() {
            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER.resolve("Software\\JavaSoft\\Prefs");
            assertFalse(registryKey.isRoot());
        }

        @Test
        @DisplayName("root")
        void testRoot() {
            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER.resolve("Software\\JavaSoft\\Prefs");
            assertSame(REGISTRY.HKEY_CURRENT_USER, registryKey.root());
        }

        @Test
        @DisplayName("parent")
        void testParent() {
            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER.resolve("Software\\JavaSoft\\Prefs");

            Optional<RegistryKey> parent = registryKey.parent();
            assertEquals(Optional.of("HKEY_CURRENT_USER\\Software\\JavaSoft"), parent.map(RegistryKey::path));

            parent = parent.flatMap(RegistryKey::parent);
            assertEquals(Optional.of("HKEY_CURRENT_USER\\Software"), parent.map(RegistryKey::path));

            parent = parent.flatMap(RegistryKey::parent);
            assertEquals(Optional.of(REGISTRY.HKEY_CURRENT_USER), parent);
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
                "..\\..\\..\\..\\..\\Something\\..\\..\\Something else\\\\.\\leaf, HKEY_CURRENT_USER\\Something else\\leaf",
                "\\absolute, HKEY_CURRENT_USER\\absolute",
                "child\\, HKEY_CURRENT_USER\\Software\\JavaSoft\\Prefs\\child",
                "\\, HKEY_CURRENT_USER",
                "\\\\\\, HKEY_CURRENT_USER"
        })
        @DisplayName("resolve")
        void testResolve(String relativePath, String expectedPath) {
            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER.resolve("Software\\JavaSoft\\Prefs");
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
            assertSame(REGISTRY.HKEY_CURRENT_USER, registryKey.root());
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
            assertEquals(Optional.of(REGISTRY.HKEY_CURRENT_USER), parent);
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
                "..\\..\\..\\..\\..\\Something\\..\\..\\Something else\\\\.\\leaf, HKEY_CURRENT_USER\\Something else\\leaf",
                "\\absolute, HKEY_CURRENT_USER\\absolute",
                "child\\, HKEY_CURRENT_USER\\Software\\JavaSoft\\Prefs\\child",
                "\\, HKEY_CURRENT_USER",
                "\\\\\\, HKEY_CURRENT_USER"
        })
        @DisplayName("resolve")
        void testResolve(String relativePath, String expectedPath) {
            RegistryKey registryKey = testKey();
            RegistryKey resolved = registryKey.resolve(relativePath != null ? relativePath : "");
            assertEquals(expectedPath, resolved.path());
        }

        private RegistryKey testKey() {
            MemorySegment hKey = mockOpenAndClose(HKEY_CURRENT_USER, "Software\\JavaSoft");

            mockSubKeys(hKey, "Prefs");

            try (Stream<RegistryKey> stream = REGISTRY.HKEY_CURRENT_USER.resolve("Software\\JavaSoft").subKeys()) {
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
            MemorySegment hKey = mockOpenAndClose(HKEY_CURRENT_USER, "Software\\JavaSoft\\Prefs");

            mockSubKeys(hKey, "child1", "child2", "child3");

            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER.resolve("Software\\JavaSoft\\Prefs");
            try (Stream<RegistryKey> stream = registryKey.subKeys()) {
                List<RegistryKey> subKeys = stream.toList();

                List<RegistryKey> expected = List.of(
                        registryKey.resolve("child1"),
                        registryKey.resolve("child2"),
                        registryKey.resolve("child3")
                );

                assertEquals(expected, subKeys);
            }

            advapi32.verify(() -> RegOpenKeyEx(eq(HKEY_CURRENT_USER), eqPointer("Software\\JavaSoft\\Prefs"), anyInt(), anyInt(), notNull()));
            advapi32.verify(() -> RegCloseKey(hKey));
        }

        @Test
        @DisplayName("non-existing key")
        void testNonExistingKey() {
            mockOpenFailure(HKEY_CURRENT_USER, "path\\non-existing", ERROR_FILE_NOT_FOUND);

            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER.resolve("path\\non-existing");
            NoSuchRegistryKeyException exception = assertThrows(NoSuchRegistryKeyException.class, registryKey::subKeys);
            assertEquals("HKEY_CURRENT_USER\\path\\non-existing", exception.path());

            advapi32.verify(() -> RegOpenKeyEx(eq(HKEY_CURRENT_USER), eqPointer("path\\non-existing"), anyInt(), anyInt(), notNull()));
            advapi32.verify(() -> RegCloseKey(notNull()), never());
        }

        @Nested
        @DisplayName("query failure")
        class QueryFailure {

            @Test
            @DisplayName("with successful close")
            void testSuccessfulClose() {
                MemorySegment hKey = mockOpenAndClose(HKEY_CURRENT_USER, "path\\failure");

                advapi32.when(() -> RegQueryInfoKey(eq(hKey), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(),
                        notNull(), notNull(), notNull()))
                        .thenReturn(ERROR_FILE_NOT_FOUND);

                RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER.resolve("path\\failure");
                NoSuchRegistryKeyException exception = assertThrows(NoSuchRegistryKeyException.class, registryKey::subKeys);
                assertEquals("HKEY_CURRENT_USER\\path\\failure", exception.path());

                advapi32.verify(() -> RegOpenKeyEx(eq(HKEY_CURRENT_USER), eqPointer("path\\failure"), anyInt(), anyInt(), notNull()));
                advapi32.verify(() -> RegCloseKey(hKey));
            }

            @Test
            @DisplayName("with close failure")
            void testCloseFailure() {
                MemorySegment hKey = mockOpen(HKEY_CURRENT_USER, "path\\failure");

                mockClose(hKey, ERROR_INVALID_HANDLE);

                advapi32.when(() -> RegQueryInfoKey(eq(hKey), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(),
                        notNull(), notNull(), notNull()))
                        .thenReturn(ERROR_FILE_NOT_FOUND);

                RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER.resolve("path\\failure");
                NoSuchRegistryKeyException exception = assertThrows(NoSuchRegistryKeyException.class, registryKey::subKeys);
                assertEquals("HKEY_CURRENT_USER\\path\\failure", exception.path());
                assertThat(exception.getSuppressed(), arrayContaining(instanceOf(InvalidRegistryHandleException.class)));

                advapi32.verify(() -> RegOpenKeyEx(eq(HKEY_CURRENT_USER), eqPointer("path\\failure"), anyInt(), anyInt(), notNull()));
                advapi32.verify(() -> RegCloseKey(hKey));
            }
        }

        @Test
        @DisplayName("enum failure")
        void testEnumFailure() {
            MemorySegment hKey = mockOpenAndClose(HKEY_CURRENT_USER, "path\\failure");

            advapi32.when(() -> RegQueryInfoKey(eq(hKey), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(),
                    notNull(), notNull(), notNull()))
                    .thenReturn(ERROR_SUCCESS);

            advapi32.when(() -> RegEnumKeyEx(eq(hKey), eq(0), notNull(), notNull(), notNull(), notNull(), notNull(), notNull()))
                    .thenReturn(ERROR_FILE_NOT_FOUND);

            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER.resolve("path\\failure");
            try (Stream<RegistryKey> stream = registryKey.subKeys()) {
                NoSuchRegistryKeyException exception = assertThrows(NoSuchRegistryKeyException.class, stream::toList);
                assertEquals("HKEY_CURRENT_USER\\path\\failure", exception.path());
            }

            advapi32.verify(() -> RegOpenKeyEx(eq(HKEY_CURRENT_USER), eqPointer("path\\failure"), anyInt(), anyInt(), notNull()));
            advapi32.verify(() -> RegCloseKey(hKey));
        }
    }

    @Nested
    @DisplayName("traverse")
    class Traverse {

        @Test
        @DisplayName("maxDepth == 0")
        void testMaxDepthIsZero() {
            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER.resolve("path");
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
                MemorySegment hKey = mockOpenAndClose(HKEY_CURRENT_USER, "path");

                mockSubKeys(hKey, "subKey1", "subKey2", "subKey3");

                RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER.resolve("path");
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

                advapi32.verify(() -> RegOpenKeyEx(eq(HKEY_CURRENT_USER), eqPointer("path"), anyInt(), anyInt(), notNull()));
                advapi32.verify(() -> RegOpenKeyEx(notNull(), notNull(), anyInt(), anyInt(), notNull()));
                advapi32.verify(() -> RegCloseKey(hKey));
                advapi32.verify(() -> RegCloseKey(notNull()));
            }

            @Test
            @DisplayName("subKeys not first")
            void testSubKeysNotFirst() {
                MemorySegment hKey = mockOpenAndClose(HKEY_CURRENT_USER, "path");

                mockSubKeys(hKey, "subKey1", "subKey2", "subKey3");

                RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER.resolve("path");
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

                advapi32.verify(() -> RegOpenKeyEx(eq(HKEY_CURRENT_USER), eqPointer("path"), anyInt(), anyInt(), notNull()));
                advapi32.verify(() -> RegOpenKeyEx(notNull(), notNull(), anyInt(), anyInt(), notNull()));
                advapi32.verify(() -> RegCloseKey(hKey));
                advapi32.verify(() -> RegCloseKey(notNull()));
            }
        }

        @Nested
        @DisplayName("no maxDepth")
        class NoMaxDepth {

            @Test
            @DisplayName("subKeys first")
            @SuppressWarnings("squid:S5961")
            void testSubKeysFirst() {
                MemorySegment hKey = mockOpenAndClose(HKEY_CURRENT_USER, "path");
                MemorySegment subKey1 = mockOpenAndClose(HKEY_CURRENT_USER, "path\\subKey1");
                MemorySegment subKey2 = mockOpenAndClose(HKEY_CURRENT_USER, "path\\subKey2");
                MemorySegment subKey3 = mockOpenAndClose(HKEY_CURRENT_USER, "path\\subKey3");
                MemorySegment subKey11 = mockOpenAndClose(HKEY_CURRENT_USER, "path\\subKey1\\subKey11");
                MemorySegment subKey12 = mockOpenAndClose(HKEY_CURRENT_USER, "path\\subKey1\\subKey12");
                MemorySegment subKey13 = mockOpenAndClose(HKEY_CURRENT_USER, "path\\subKey1\\subKey13");
                MemorySegment subKey21 = mockOpenAndClose(HKEY_CURRENT_USER, "path\\subKey2\\subKey21");
                MemorySegment subKey22 = mockOpenAndClose(HKEY_CURRENT_USER, "path\\subKey2\\subKey22");
                MemorySegment subKey23 = mockOpenAndClose(HKEY_CURRENT_USER, "path\\subKey2\\subKey23");
                MemorySegment subKey31 = mockOpenAndClose(HKEY_CURRENT_USER, "path\\subKey3\\subKey31");
                MemorySegment subKey32 = mockOpenAndClose(HKEY_CURRENT_USER, "path\\subKey3\\subKey32");
                MemorySegment subKey33 = mockOpenAndClose(HKEY_CURRENT_USER, "path\\subKey3\\subKey33");

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

                RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER.resolve("path");
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

                advapi32.verify(() -> RegOpenKeyEx(eq(HKEY_CURRENT_USER), eqPointer("path"), anyInt(), anyInt(), notNull()));
                advapi32.verify(() -> RegOpenKeyEx(eq(HKEY_CURRENT_USER), eqPointer("path\\subKey1"), anyInt(), anyInt(), notNull()));
                advapi32.verify(() -> RegOpenKeyEx(eq(HKEY_CURRENT_USER), eqPointer("path\\subKey2"), anyInt(), anyInt(), notNull()));
                advapi32.verify(() -> RegOpenKeyEx(eq(HKEY_CURRENT_USER), eqPointer("path\\subKey3"), anyInt(), anyInt(), notNull()));
                advapi32.verify(() -> RegOpenKeyEx(eq(HKEY_CURRENT_USER), eqPointer("path\\subKey1\\subKey11"), anyInt(), anyInt(), notNull()));
                advapi32.verify(() -> RegOpenKeyEx(eq(HKEY_CURRENT_USER), eqPointer("path\\subKey1\\subKey12"), anyInt(), anyInt(), notNull()));
                advapi32.verify(() -> RegOpenKeyEx(eq(HKEY_CURRENT_USER), eqPointer("path\\subKey1\\subKey13"), anyInt(), anyInt(), notNull()));
                advapi32.verify(() -> RegOpenKeyEx(eq(HKEY_CURRENT_USER), eqPointer("path\\subKey2\\subKey21"), anyInt(), anyInt(), notNull()));
                advapi32.verify(() -> RegOpenKeyEx(eq(HKEY_CURRENT_USER), eqPointer("path\\subKey2\\subKey22"), anyInt(), anyInt(), notNull()));
                advapi32.verify(() -> RegOpenKeyEx(eq(HKEY_CURRENT_USER), eqPointer("path\\subKey2\\subKey23"), anyInt(), anyInt(), notNull()));
                advapi32.verify(() -> RegOpenKeyEx(eq(HKEY_CURRENT_USER), eqPointer("path\\subKey3\\subKey31"), anyInt(), anyInt(), notNull()));
                advapi32.verify(() -> RegOpenKeyEx(eq(HKEY_CURRENT_USER), eqPointer("path\\subKey3\\subKey32"), anyInt(), anyInt(), notNull()));
                advapi32.verify(() -> RegOpenKeyEx(eq(HKEY_CURRENT_USER), eqPointer("path\\subKey3\\subKey33"), anyInt(), anyInt(), notNull()));
                advapi32.verify(() -> RegOpenKeyEx(notNull(), notNull(), anyInt(), anyInt(), notNull()), times(13));
                advapi32.verify(() -> RegCloseKey(hKey));
                advapi32.verify(() -> RegCloseKey(subKey1));
                advapi32.verify(() -> RegCloseKey(subKey2));
                advapi32.verify(() -> RegCloseKey(subKey3));
                advapi32.verify(() -> RegCloseKey(subKey11));
                advapi32.verify(() -> RegCloseKey(subKey12));
                advapi32.verify(() -> RegCloseKey(subKey13));
                advapi32.verify(() -> RegCloseKey(subKey21));
                advapi32.verify(() -> RegCloseKey(subKey22));
                advapi32.verify(() -> RegCloseKey(subKey23));
                advapi32.verify(() -> RegCloseKey(subKey31));
                advapi32.verify(() -> RegCloseKey(subKey32));
                advapi32.verify(() -> RegCloseKey(subKey33));
                advapi32.verify(() -> RegCloseKey(notNull()), times(13));
            }

            @Test
            @DisplayName("subKeys not first")
            @SuppressWarnings("squid:S5961")
            void testSubKeysNotFirst() {
                MemorySegment hKey = mockOpenAndClose(HKEY_CURRENT_USER, "path");
                MemorySegment subKey1 = mockOpenAndClose(HKEY_CURRENT_USER, "path\\subKey1");
                MemorySegment subKey2 = mockOpenAndClose(HKEY_CURRENT_USER, "path\\subKey2");
                MemorySegment subKey3 = mockOpenAndClose(HKEY_CURRENT_USER, "path\\subKey3");
                MemorySegment subKey11 = mockOpenAndClose(HKEY_CURRENT_USER, "path\\subKey1\\subKey11");
                MemorySegment subKey12 = mockOpenAndClose(HKEY_CURRENT_USER, "path\\subKey1\\subKey12");
                MemorySegment subKey13 = mockOpenAndClose(HKEY_CURRENT_USER, "path\\subKey1\\subKey13");
                MemorySegment subKey21 = mockOpenAndClose(HKEY_CURRENT_USER, "path\\subKey2\\subKey21");
                MemorySegment subKey22 = mockOpenAndClose(HKEY_CURRENT_USER, "path\\subKey2\\subKey22");
                MemorySegment subKey23 = mockOpenAndClose(HKEY_CURRENT_USER, "path\\subKey2\\subKey23");
                MemorySegment subKey31 = mockOpenAndClose(HKEY_CURRENT_USER, "path\\subKey3\\subKey31");
                MemorySegment subKey32 = mockOpenAndClose(HKEY_CURRENT_USER, "path\\subKey3\\subKey32");
                MemorySegment subKey33 = mockOpenAndClose(HKEY_CURRENT_USER, "path\\subKey3\\subKey33");

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

                RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER.resolve("path");
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

                advapi32.verify(() -> RegOpenKeyEx(eq(HKEY_CURRENT_USER), eqPointer("path"), anyInt(), anyInt(), notNull()));
                advapi32.verify(() -> RegOpenKeyEx(eq(HKEY_CURRENT_USER), eqPointer("path\\subKey1"), anyInt(), anyInt(), notNull()));
                advapi32.verify(() -> RegOpenKeyEx(eq(HKEY_CURRENT_USER), eqPointer("path\\subKey2"), anyInt(), anyInt(), notNull()));
                advapi32.verify(() -> RegOpenKeyEx(eq(HKEY_CURRENT_USER), eqPointer("path\\subKey3"), anyInt(), anyInt(), notNull()));
                advapi32.verify(() -> RegOpenKeyEx(eq(HKEY_CURRENT_USER), eqPointer("path\\subKey1\\subKey11"), anyInt(), anyInt(), notNull()));
                advapi32.verify(() -> RegOpenKeyEx(eq(HKEY_CURRENT_USER), eqPointer("path\\subKey1\\subKey12"), anyInt(), anyInt(), notNull()));
                advapi32.verify(() -> RegOpenKeyEx(eq(HKEY_CURRENT_USER), eqPointer("path\\subKey1\\subKey13"), anyInt(), anyInt(), notNull()));
                advapi32.verify(() -> RegOpenKeyEx(eq(HKEY_CURRENT_USER), eqPointer("path\\subKey2\\subKey21"), anyInt(), anyInt(), notNull()));
                advapi32.verify(() -> RegOpenKeyEx(eq(HKEY_CURRENT_USER), eqPointer("path\\subKey2\\subKey22"), anyInt(), anyInt(), notNull()));
                advapi32.verify(() -> RegOpenKeyEx(eq(HKEY_CURRENT_USER), eqPointer("path\\subKey2\\subKey23"), anyInt(), anyInt(), notNull()));
                advapi32.verify(() -> RegOpenKeyEx(eq(HKEY_CURRENT_USER), eqPointer("path\\subKey3\\subKey31"), anyInt(), anyInt(), notNull()));
                advapi32.verify(() -> RegOpenKeyEx(eq(HKEY_CURRENT_USER), eqPointer("path\\subKey3\\subKey32"), anyInt(), anyInt(), notNull()));
                advapi32.verify(() -> RegOpenKeyEx(eq(HKEY_CURRENT_USER), eqPointer("path\\subKey3\\subKey33"), anyInt(), anyInt(), notNull()));
                advapi32.verify(() -> RegOpenKeyEx(notNull(), notNull(), anyInt(), anyInt(), notNull()), times(13));
                advapi32.verify(() -> RegCloseKey(hKey));
                advapi32.verify(() -> RegCloseKey(subKey1));
                advapi32.verify(() -> RegCloseKey(subKey2));
                advapi32.verify(() -> RegCloseKey(subKey3));
                advapi32.verify(() -> RegCloseKey(subKey11));
                advapi32.verify(() -> RegCloseKey(subKey12));
                advapi32.verify(() -> RegCloseKey(subKey13));
                advapi32.verify(() -> RegCloseKey(subKey21));
                advapi32.verify(() -> RegCloseKey(subKey22));
                advapi32.verify(() -> RegCloseKey(subKey23));
                advapi32.verify(() -> RegCloseKey(subKey31));
                advapi32.verify(() -> RegCloseKey(subKey32));
                advapi32.verify(() -> RegCloseKey(subKey33));
                advapi32.verify(() -> RegCloseKey(notNull()), times(13));
            }
        }

        @Test
        @DisplayName("negative maxDepth")
        void testNegativeMaxDepth() {
            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER.resolve("path");
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

                MemorySegment hKey = mockOpenAndClose(HKEY_CURRENT_USER, "Software\\JavaSoft\\Prefs");

                mockValues(hKey, stringValue, binaryValue, wordValue);

                RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER.resolve("Software\\JavaSoft\\Prefs");
                try (Stream<RegistryValue> stream = registryKey.values()) {
                    List<RegistryValue> values = stream.toList();

                    List<RegistryValue> expected = List.of(stringValue, binaryValue, wordValue);

                    assertEquals(expected, values);
                }

                advapi32.verify(() -> RegOpenKeyEx(eq(HKEY_CURRENT_USER), eqPointer("Software\\JavaSoft\\Prefs"), anyInt(), anyInt(), notNull()));
                advapi32.verify(() -> RegCloseKey(hKey));
            }

            @Test
            @DisplayName("with name filter")
            void testWithNameFilter() {
                StringValue stringValue = StringValue.of("string", "value");
                BinaryValue binaryValue = BinaryValue.of("binary", randomData());
                DWordValue wordValue = DWordValue.of("dword", 13);

                MemorySegment hKey = mockOpenAndClose(HKEY_CURRENT_USER, "Software\\JavaSoft\\Prefs");

                mockValues(hKey, stringValue, binaryValue, wordValue);

                RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER.resolve("Software\\JavaSoft\\Prefs");
                RegistryValue.Filter filter = RegistryValue.filter().name(s -> s.contains("i"));
                try (Stream<RegistryValue> stream = registryKey.values(filter)) {
                    List<RegistryValue> values = stream.toList();

                    List<RegistryValue> expected = List.of(stringValue, binaryValue);

                    assertEquals(expected, values);
                }

                advapi32.verify(() -> RegOpenKeyEx(eq(HKEY_CURRENT_USER), eqPointer("Software\\JavaSoft\\Prefs"), anyInt(), anyInt(), notNull()));
                advapi32.verify(() -> RegCloseKey(hKey));
            }

            @Test
            @DisplayName("with type filter")
            void testWithTypeFilter() {
                StringValue stringValue = StringValue.of("string", "value");
                BinaryValue binaryValue = BinaryValue.of("binary", randomData());
                DWordValue wordValue = DWordValue.of("dword", 13);

                MemorySegment hKey = mockOpenAndClose(HKEY_CURRENT_USER, "Software\\JavaSoft\\Prefs");

                mockValues(hKey, stringValue, binaryValue, wordValue);

                RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER.resolve("Software\\JavaSoft\\Prefs");
                RegistryValue.Filter filter = RegistryValue.filter().strings().words();
                try (Stream<RegistryValue> stream = registryKey.values(filter)) {
                    List<RegistryValue> values = stream.toList();

                    List<RegistryValue> expected = List.of(stringValue, wordValue);

                    assertEquals(expected, values);
                }

                advapi32.verify(() -> RegOpenKeyEx(eq(HKEY_CURRENT_USER), eqPointer("Software\\JavaSoft\\Prefs"), anyInt(), anyInt(), notNull()));
                advapi32.verify(() -> RegCloseKey(hKey));
            }
        }

        @Test
        @DisplayName("non-existing key")
        void testNonExistingKey() {
            mockOpenFailure(HKEY_CURRENT_USER, "path\\non-existing", ERROR_FILE_NOT_FOUND);

            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER.resolve("path\\non-existing");
            NoSuchRegistryKeyException exception = assertThrows(NoSuchRegistryKeyException.class, registryKey::values);
            assertEquals("HKEY_CURRENT_USER\\path\\non-existing", exception.path());

            advapi32.verify(() -> RegOpenKeyEx(eq(HKEY_CURRENT_USER), eqPointer("path\\non-existing"), anyInt(), anyInt(), notNull()));
            advapi32.verify(() -> RegCloseKey(notNull()), never());
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
                    MemorySegment hKey = mockOpenAndClose(HKEY_CURRENT_USER, "path\\failure");

                    advapi32.when(() -> RegQueryInfoKey(eq(hKey), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(),
                            notNull(), notNull(), notNull(), notNull()))
                            .thenReturn(ERROR_FILE_NOT_FOUND);

                    RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER.resolve("path\\failure");
                    NoSuchRegistryKeyException exception = assertThrows(NoSuchRegistryKeyException.class, registryKey::values);
                    assertEquals("HKEY_CURRENT_USER\\path\\failure", exception.path());

                    advapi32.verify(() -> RegOpenKeyEx(eq(HKEY_CURRENT_USER), eqPointer("path\\failure"), anyInt(), anyInt(), notNull()));
                    advapi32.verify(() -> RegCloseKey(hKey));
                }

                @Test
                @DisplayName("with close failure")
                void testCloseFailure() {
                    MemorySegment hKey = mockOpen(HKEY_CURRENT_USER, "path\\failure");

                    mockClose(hKey, ERROR_INVALID_HANDLE);

                    advapi32.when(() -> RegQueryInfoKey(eq(hKey), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(),
                            notNull(), notNull(), notNull(), notNull()))
                            .thenReturn(ERROR_FILE_NOT_FOUND);

                    RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER.resolve("path\\failure");
                    NoSuchRegistryKeyException exception = assertThrows(NoSuchRegistryKeyException.class, registryKey::values);
                    assertEquals("HKEY_CURRENT_USER\\path\\failure", exception.path());
                    assertThat(exception.getSuppressed(), arrayContaining(instanceOf(InvalidRegistryHandleException.class)));

                    advapi32.verify(() -> RegOpenKeyEx(eq(HKEY_CURRENT_USER), eqPointer("path\\failure"), anyInt(), anyInt(), notNull()));
                    advapi32.verify(() -> RegCloseKey(hKey));
                }
            }

            @Nested
            @DisplayName("with filter")
            class WithFilter {

                @Test
                @DisplayName("with successful close")
                void testSuccessfulClose() {
                    MemorySegment hKey = mockOpenAndClose(HKEY_CURRENT_USER, "path\\failure");

                    advapi32.when(() -> RegQueryInfoKey(eq(hKey), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(),
                            notNull(), notNull(), notNull(), notNull()))
                            .thenReturn(ERROR_FILE_NOT_FOUND);

                    RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER.resolve("path\\failure");
                    RegistryValue.Filter filter = RegistryValue.filter().strings();
                    NoSuchRegistryKeyException exception = assertThrows(NoSuchRegistryKeyException.class, () -> registryKey.values(filter));
                    assertEquals("HKEY_CURRENT_USER\\path\\failure", exception.path());

                    advapi32.verify(() -> RegOpenKeyEx(eq(HKEY_CURRENT_USER), eqPointer("path\\failure"), anyInt(), anyInt(), notNull()));
                    advapi32.verify(() -> RegCloseKey(hKey));
                }

                @Test
                @DisplayName("with close failure")
                void testCloseFailure() {
                    MemorySegment hKey = mockOpen(HKEY_CURRENT_USER, "path\\failure");

                    mockClose(hKey, ERROR_INVALID_HANDLE);

                    advapi32.when(() -> RegQueryInfoKey(eq(hKey), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(),
                            notNull(), notNull(), notNull(), notNull()))
                            .thenReturn(ERROR_FILE_NOT_FOUND);

                    RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER.resolve("path\\failure");
                    RegistryValue.Filter filter = RegistryValue.filter().strings();
                    NoSuchRegistryKeyException exception = assertThrows(NoSuchRegistryKeyException.class, () -> registryKey.values(filter));
                    assertEquals("HKEY_CURRENT_USER\\path\\failure", exception.path());
                    assertThat(exception.getSuppressed(), arrayContaining(instanceOf(InvalidRegistryHandleException.class)));

                    advapi32.verify(() -> RegOpenKeyEx(eq(HKEY_CURRENT_USER), eqPointer("path\\failure"), anyInt(), anyInt(), notNull()));
                    advapi32.verify(() -> RegCloseKey(hKey));
                }
            }
        }

        @Test
        @DisplayName("enum failure")
        void testEnumFailure() {
            MemorySegment hKey = mockOpenAndClose(HKEY_CURRENT_USER, "path\\failure");

            advapi32.when(() -> RegQueryInfoKey(eq(hKey), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(),
                    notNull(), notNull(), notNull()))
                    .thenReturn(ERROR_SUCCESS);

            advapi32.when(() -> RegEnumValue(eq(hKey), eq(0), notNull(), notNull(), notNull(), notNull(), notNull(), notNull()))
                    .thenReturn(ERROR_FILE_NOT_FOUND);

            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER.resolve("path\\failure");
            try (Stream<RegistryValue> stream = registryKey.values()) {
                NoSuchRegistryKeyException exception = assertThrows(NoSuchRegistryKeyException.class, stream::toList);
                assertEquals("HKEY_CURRENT_USER\\path\\failure", exception.path());
            }

            advapi32.verify(() -> RegOpenKeyEx(eq(HKEY_CURRENT_USER), eqPointer("path\\failure"), anyInt(), anyInt(), notNull()));
            advapi32.verify(() -> RegCloseKey(hKey));
        }
    }

    @Nested
    @DisplayName("getValue")
    class GetValue {

        @Test
        @DisplayName("success")
        void testSuccess() {
            StringValue stringValue = StringValue.of("string", "value");

            MemorySegment hKey = mockOpenAndClose(HKEY_CURRENT_USER, "Software\\JavaSoft\\Prefs");

            mockValue(hKey, stringValue);

            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER.resolve("Software\\JavaSoft\\Prefs");
            StringValue value = registryKey.getValue("string", StringValue.class);
            assertEquals(stringValue, value);

            advapi32.verify(() -> RegOpenKeyEx(eq(HKEY_CURRENT_USER), eqPointer("Software\\JavaSoft\\Prefs"), anyInt(), anyInt(), notNull()));
            advapi32.verify(() -> RegCloseKey(hKey));
        }

        @Test
        @DisplayName("non-existing key")
        void testNonExistingKey() {
            mockOpenFailure(HKEY_CURRENT_USER, "path\\non-existing", ERROR_FILE_NOT_FOUND);

            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER.resolve("path\\non-existing");
            NoSuchRegistryKeyException exception = assertThrows(NoSuchRegistryKeyException.class,
                    () -> registryKey.getValue("string", RegistryValue.class));
            assertEquals("HKEY_CURRENT_USER\\path\\non-existing", exception.path());

            advapi32.verify(() -> RegOpenKeyEx(eq(HKEY_CURRENT_USER), eqPointer("path\\non-existing"), anyInt(), anyInt(), notNull()));
            advapi32.verify(() -> RegCloseKey(notNull()), never());
        }

        @Test
        @DisplayName("non-existing value")
        void testNonExistingValue() {
            MemorySegment hKey = mockOpenAndClose(HKEY_CURRENT_USER, "path\\non-existing");

            advapi32.when(() -> RegQueryValueEx(eq(hKey), eqPointer("string"), notNull(), notNull(), isNULL(), notNull()))
                    .thenReturn(ERROR_FILE_NOT_FOUND);

            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER.resolve("path\\non-existing");
            NoSuchRegistryValueException exception = assertThrows(NoSuchRegistryValueException.class,
                    () -> registryKey.getValue("string", RegistryValue.class));
            assertEquals("HKEY_CURRENT_USER\\path\\non-existing", exception.path());
            assertEquals("string", exception.name());

            advapi32.verify(() -> RegOpenKeyEx(eq(HKEY_CURRENT_USER), eqPointer("path\\non-existing"), anyInt(), anyInt(), notNull()));
            advapi32.verify(() -> RegCloseKey(hKey));
        }

        @Test
        @DisplayName("failure")
        void testFailure() {
            MemorySegment hKey = mockOpenAndClose(HKEY_CURRENT_USER, "path\\failure");

            mockValue(hKey, StringValue.of("string", "value"), ERROR_INVALID_HANDLE);

            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER.resolve("path\\failure");
            InvalidRegistryHandleException exception = assertThrows(InvalidRegistryHandleException.class,
                    () -> registryKey.getValue("string", RegistryValue.class));
            assertEquals("HKEY_CURRENT_USER\\path\\failure", exception.path());

            advapi32.verify(() -> RegOpenKeyEx(eq(HKEY_CURRENT_USER), eqPointer("path\\failure"), anyInt(), anyInt(), notNull()));
            advapi32.verify(() -> RegCloseKey(hKey));
        }

        @Test
        @DisplayName("wrong value type")
        void testWrongValueType() {
            StringValue stringValue = StringValue.of("string", "value");

            MemorySegment hKey = mockOpenAndClose(HKEY_CURRENT_USER, "Software\\JavaSoft\\Prefs");

            mockValue(hKey, stringValue);

            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER.resolve("Software\\JavaSoft\\Prefs");
            assertThrows(ClassCastException.class, () -> registryKey.getValue("string", DWordValue.class));

            advapi32.verify(() -> RegOpenKeyEx(eq(HKEY_CURRENT_USER), eqPointer("Software\\JavaSoft\\Prefs"), anyInt(), anyInt(), notNull()));
            advapi32.verify(() -> RegCloseKey(hKey));
        }
    }

    @Nested
    @DisplayName("findValue")
    class FindValue {

        @Test
        @DisplayName("success")
        void testSuccess() {
            StringValue stringValue = StringValue.of("string", "value");

            MemorySegment hKey = mockOpenAndClose(HKEY_CURRENT_USER, "Software\\JavaSoft\\Prefs");

            mockValue(hKey, stringValue);

            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER.resolve("Software\\JavaSoft\\Prefs");
            Optional<StringValue> value = registryKey.findValue("string", StringValue.class);
            assertEquals(Optional.of(stringValue), value);

            advapi32.verify(() -> RegOpenKeyEx(eq(HKEY_CURRENT_USER), eqPointer("Software\\JavaSoft\\Prefs"), anyInt(), anyInt(), notNull()));
            advapi32.verify(() -> RegCloseKey(hKey));
        }

        @Test
        @DisplayName("non-existing key")
        void testNonExistingKey() {
            mockOpenFailure(HKEY_CURRENT_USER, "path\\non-existing", ERROR_FILE_NOT_FOUND);

            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER.resolve("path\\non-existing");
            NoSuchRegistryKeyException exception = assertThrows(NoSuchRegistryKeyException.class,
                    () -> registryKey.findValue("string", RegistryValue.class));
            assertEquals("HKEY_CURRENT_USER\\path\\non-existing", exception.path());

            advapi32.verify(() -> RegOpenKeyEx(eq(HKEY_CURRENT_USER), eqPointer("path\\non-existing"), anyInt(), anyInt(), notNull()));
            advapi32.verify(() -> RegCloseKey(notNull()), never());
        }

        @Test
        @DisplayName("non-existing value")
        void testNonExistingValue() {
            MemorySegment hKey = mockOpenAndClose(HKEY_CURRENT_USER, "path\\non-existing");

            advapi32.when(() -> RegQueryValueEx(eq(hKey), eqPointer("string"), notNull(), notNull(), isNULL(), notNull()))
                    .thenReturn(ERROR_FILE_NOT_FOUND);

            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER.resolve("path\\non-existing");
            Optional<DWordValue> value = registryKey.findValue("string", DWordValue.class);
            assertEquals(Optional.empty(), value);

            advapi32.verify(() -> RegOpenKeyEx(eq(HKEY_CURRENT_USER), eqPointer("path\\non-existing"), anyInt(), anyInt(), notNull()));
            advapi32.verify(() -> RegCloseKey(hKey));
        }

        @Test
        @DisplayName("failure")
        void testFailure() {
            MemorySegment hKey = mockOpenAndClose(HKEY_CURRENT_USER, "path\\failure");

            mockValue(hKey, StringValue.of("string", "value"), ERROR_INVALID_HANDLE);

            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER.resolve("path\\failure");
            InvalidRegistryHandleException exception = assertThrows(InvalidRegistryHandleException.class,
                    () -> registryKey.findValue("string", RegistryValue.class));
            assertEquals("HKEY_CURRENT_USER\\path\\failure", exception.path());

            advapi32.verify(() -> RegOpenKeyEx(eq(HKEY_CURRENT_USER), eqPointer("path\\failure"), anyInt(), anyInt(), notNull()));
            advapi32.verify(() -> RegCloseKey(hKey));
        }

        @Test
        @DisplayName("wrong value type")
        void testWrongValueType() {
            StringValue stringValue = StringValue.of("string", "value");

            MemorySegment hKey = mockOpenAndClose(HKEY_CURRENT_USER, "Software\\JavaSoft\\Prefs");

            mockValue(hKey, stringValue);

            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER.resolve("Software\\JavaSoft\\Prefs");
            assertThrows(ClassCastException.class, () -> registryKey.findValue("string", DWordValue.class));

            advapi32.verify(() -> RegOpenKeyEx(eq(HKEY_CURRENT_USER), eqPointer("Software\\JavaSoft\\Prefs"), anyInt(), anyInt(), notNull()));
            advapi32.verify(() -> RegCloseKey(hKey));
        }
    }

    @Nested
    @DisplayName("setValue")
    class SetValue {

        @Test
        @DisplayName("success")
        void testSuccess() {
            StringValue stringValue = StringValue.of("string", "value");
            MemorySegment data = stringValue.rawData(arena);

            MemorySegment hKey = mockOpenAndClose(HKEY_CURRENT_USER, "Software\\JavaSoft\\Prefs");

            advapi32.when(() -> RegSetValueEx(eq(hKey), eqPointer("string"), anyInt(), eq(REG_SZ), eqBytes(data), eqSize(data)))
                    .thenReturn(ERROR_SUCCESS);

            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER.resolve("Software\\JavaSoft\\Prefs");
            registryKey.setValue(stringValue);

            advapi32.verify(() -> RegSetValueEx(eq(hKey), eqPointer("string"), anyInt(), eq(REG_SZ), eqBytes(data), eqSize(data)));

            advapi32.verify(() -> RegOpenKeyEx(eq(HKEY_CURRENT_USER), eqPointer("Software\\JavaSoft\\Prefs"), anyInt(), anyInt(), notNull()));
            advapi32.verify(() -> RegCloseKey(hKey));
        }

        @Test
        @DisplayName("non-existing key")
        void testNonExistingKey() {
            StringValue stringValue = StringValue.of("string", "value");

            mockOpenFailure(HKEY_CURRENT_USER, "path\\non-existing", ERROR_FILE_NOT_FOUND);

            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER.resolve("path\\non-existing");
            NoSuchRegistryKeyException exception = assertThrows(NoSuchRegistryKeyException.class, () -> registryKey.setValue(stringValue));
            assertEquals("HKEY_CURRENT_USER\\path\\non-existing", exception.path());

            advapi32.verify(() -> RegOpenKeyEx(eq(HKEY_CURRENT_USER), eqPointer("path\\non-existing"), anyInt(), anyInt(), notNull()));
            advapi32.verify(() -> RegCloseKey(notNull()), never());
            advapi32.verify(() -> RegSetValueEx(notNull(), notNull(), anyInt(), anyInt(), notNull(), anyInt()), never());
        }

        @Test
        @DisplayName("failure")
        void testFailure() {
            StringValue stringValue = StringValue.of("string", "value");

            MemorySegment hKey = mockOpenAndClose(HKEY_CURRENT_USER, "path\\failure");

            advapi32.when(() -> RegSetValueEx(eq(hKey), eqPointer("string"), anyInt(), eq(REG_SZ), notNull(), anyInt()))
                    .thenReturn(ERROR_INVALID_HANDLE);

            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER.resolve("path\\failure");
            InvalidRegistryHandleException exception = assertThrows(InvalidRegistryHandleException.class, () -> registryKey.setValue(stringValue));
            assertEquals("HKEY_CURRENT_USER\\path\\failure", exception.path());

            advapi32.verify(() -> RegOpenKeyEx(eq(HKEY_CURRENT_USER), eqPointer("path\\failure"), anyInt(), anyInt(), notNull()));
            advapi32.verify(() -> RegCloseKey(hKey));
        }
    }

    @Nested
    @DisplayName("deleteValue")
    class DeleteValue {

        @Test
        @DisplayName("success")
        void testSuccess() {
            MemorySegment hKey = mockOpenAndClose(HKEY_CURRENT_USER, "Software\\JavaSoft\\Prefs");

            advapi32.when(() -> RegDeleteValue(eq(hKey), eqPointer("string"))).thenReturn(ERROR_SUCCESS);

            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER.resolve("Software\\JavaSoft\\Prefs");
            registryKey.deleteValue("string");

            advapi32.verify(() -> RegDeleteValue(eq(hKey), eqPointer("string")));

            advapi32.verify(() -> RegOpenKeyEx(eq(HKEY_CURRENT_USER), eqPointer("Software\\JavaSoft\\Prefs"), anyInt(), anyInt(), notNull()));
            advapi32.verify(() -> RegCloseKey(hKey));
        }

        @Test
        @DisplayName("non-existing key")
        void testNonExistingKey() {
            mockOpenFailure(HKEY_CURRENT_USER, "path\\non-existing", ERROR_FILE_NOT_FOUND);

            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER.resolve("path\\non-existing");
            NoSuchRegistryKeyException exception = assertThrows(NoSuchRegistryKeyException.class, () -> registryKey.deleteValue("string"));
            assertEquals("HKEY_CURRENT_USER\\path\\non-existing", exception.path());

            advapi32.verify(() -> RegOpenKeyEx(eq(HKEY_CURRENT_USER), eqPointer("path\\non-existing"), anyInt(), anyInt(), notNull()));
            advapi32.verify(() -> RegCloseKey(notNull()), never());
            advapi32.verify(() -> RegDeleteValue(notNull(), notNull()), never());
        }

        @Test
        @DisplayName("failure")
        void testFailure() {
            MemorySegment hKey = mockOpenAndClose(HKEY_CURRENT_USER, "path\\failure");

            advapi32.when(() -> RegDeleteValue(eq(hKey), eqPointer("string"))).thenReturn(ERROR_FILE_NOT_FOUND);

            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER.resolve("path\\failure");
            NoSuchRegistryValueException exception = assertThrows(NoSuchRegistryValueException.class, () -> registryKey.deleteValue("string"));
            assertEquals("HKEY_CURRENT_USER\\path\\failure", exception.path());
            assertEquals("string", exception.name());

            advapi32.verify(() -> RegOpenKeyEx(eq(HKEY_CURRENT_USER), eqPointer("path\\failure"), anyInt(), anyInt(), notNull()));
            advapi32.verify(() -> RegCloseKey(hKey));
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
                MemorySegment hKey = mockOpenAndClose(HKEY_CURRENT_USER, "Software\\JavaSoft\\Prefs");

                advapi32.when(() -> RegDeleteValue(eq(hKey), eqPointer("string"))).thenReturn(ERROR_SUCCESS);

                RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER.resolve("Software\\JavaSoft\\Prefs");
                assertTrue(registryKey.deleteValueIfExists("string"));

                advapi32.verify(() -> RegDeleteValue(eq(hKey), eqPointer("string")));

                advapi32.verify(() -> RegOpenKeyEx(eq(HKEY_CURRENT_USER), eqPointer("Software\\JavaSoft\\Prefs"), anyInt(), anyInt(), notNull()));
                advapi32.verify(() -> RegCloseKey(hKey));
            }

            @Test
            @DisplayName("value didn't exist")
            void testValueDidntExist() {
                MemorySegment hKey = mockOpenAndClose(HKEY_CURRENT_USER, "Software\\JavaSoft\\Prefs");

                advapi32.when(() -> RegDeleteValue(eq(hKey), eqPointer("string"))).thenReturn(ERROR_FILE_NOT_FOUND);

                RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER.resolve("Software\\JavaSoft\\Prefs");
                assertFalse(registryKey.deleteValueIfExists("string"));

                advapi32.verify(() -> RegDeleteValue(eq(hKey), eqPointer("string")));

                advapi32.verify(() -> RegOpenKeyEx(eq(HKEY_CURRENT_USER), eqPointer("Software\\JavaSoft\\Prefs"), anyInt(), anyInt(), notNull()));
                advapi32.verify(() -> RegCloseKey(hKey));
            }
        }

        @Test
        @DisplayName("non-existing key")
        void testNonExistingKey() {
            mockOpenFailure(HKEY_CURRENT_USER, "path\\non-existing", ERROR_FILE_NOT_FOUND);

            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER.resolve("path\\non-existing");
            NoSuchRegistryKeyException exception = assertThrows(NoSuchRegistryKeyException.class, () -> registryKey.deleteValueIfExists("string"));
            assertEquals("HKEY_CURRENT_USER\\path\\non-existing", exception.path());

            advapi32.verify(() -> RegOpenKeyEx(eq(HKEY_CURRENT_USER), eqPointer("path\\non-existing"), anyInt(), anyInt(), notNull()));
            advapi32.verify(() -> RegCloseKey(notNull()), never());
            advapi32.verify(() -> RegDeleteValue(notNull(), notNull()), never());
        }

        @Test
        @DisplayName("failure")
        void testFailure() {
            MemorySegment hKey = mockOpenAndClose(HKEY_CURRENT_USER, "path\\failure");

            advapi32.when(() -> RegDeleteValue(eq(hKey), eqPointer("string"))).thenReturn(ERROR_INVALID_HANDLE);

            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER.resolve("path\\failure");
            InvalidRegistryHandleException exception = assertThrows(InvalidRegistryHandleException.class,
                    () -> registryKey.deleteValueIfExists("string"));
            assertEquals("HKEY_CURRENT_USER\\path\\failure", exception.path());

            advapi32.verify(() -> RegOpenKeyEx(eq(HKEY_CURRENT_USER), eqPointer("path\\failure"), anyInt(), anyInt(), notNull()));
            advapi32.verify(() -> RegCloseKey(hKey));
        }
    }

    @Nested
    @DisplayName("exists")
    class Exists {

        @Test
        @DisplayName("existing")
        void testExisting() {
            MemorySegment hKey = mockOpenAndClose(HKEY_CURRENT_USER, "path\\existing");

            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER.resolve("path\\existing");
            assertTrue(registryKey.exists());

            advapi32.verify(() -> RegOpenKeyEx(eq(HKEY_CURRENT_USER), eqPointer("path\\existing"), anyInt(), anyInt(), notNull()));
            advapi32.verify(() -> RegCloseKey(hKey));
        }

        @Test
        @DisplayName("non-existing")
        void testNonExisting() {
            mockOpenFailure(HKEY_CURRENT_USER, "path\\non-existing", ERROR_FILE_NOT_FOUND);

            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER.resolve("path\\non-existing");
            assertFalse(registryKey.exists());

            advapi32.verify(() -> RegOpenKeyEx(eq(HKEY_CURRENT_USER), eqPointer("path\\non-existing"), anyInt(), anyInt(), notNull()));
            advapi32.verify(() -> RegCloseKey(notNull()), never());
        }

        @Test
        @DisplayName("failure")
        void testFailure() {
            mockOpenFailure(HKEY_CURRENT_USER, "path\\failure", ERROR_ACCESS_DENIED);

            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER.resolve("path\\failure");
            RegistryAccessDeniedException exception = assertThrows(RegistryAccessDeniedException.class, registryKey::exists);
            assertEquals("HKEY_CURRENT_USER\\path\\failure", exception.path());

            advapi32.verify(() -> RegOpenKeyEx(eq(HKEY_CURRENT_USER), eqPointer("path\\failure"), anyInt(), anyInt(), notNull()));
            advapi32.verify(() -> RegCloseKey(notNull()), never());
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
                MemorySegment hKey = mockOpenAndClose(HKEY_CURRENT_USER, "path\\existing");

                RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER.resolve("path\\existing");

                @SuppressWarnings("unchecked")
                Consumer<RegistryKey.Handle> action = mock(Consumer.class);

                registryKey.ifExists(action);

                verify(action).accept(any());

                advapi32.verify(() -> RegOpenKeyEx(eq(HKEY_CURRENT_USER), eqPointer("path\\existing"), anyInt(), anyInt(), notNull()));
                advapi32.verify(() -> RegCloseKey(hKey));
            }

            @Test
            @DisplayName("non-existing")
            @SuppressWarnings("resource")
            void testNonExisting() {
                mockOpenFailure(HKEY_CURRENT_USER, "path\\not-found", ERROR_FILE_NOT_FOUND);

                RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER.resolve("path\\not-found");

                @SuppressWarnings("unchecked")
                Consumer<RegistryKey.Handle> action = mock(Consumer.class);

                registryKey.ifExists(action);

                verify(action, never()).accept(any());

                advapi32.verify(() -> RegOpenKeyEx(eq(HKEY_CURRENT_USER), eqPointer("path\\not-found"), anyInt(), anyInt(), notNull()));
                advapi32.verify(() -> RegCloseKey(notNull()), never());
            }

            @Test
            @DisplayName("failure")
            @SuppressWarnings("resource")
            void testFailure() {
                mockOpenFailure(HKEY_CURRENT_USER, "path\\failure", ERROR_ACCESS_DENIED);

                RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER.resolve("path\\failure");

                @SuppressWarnings("unchecked")
                Consumer<RegistryKey.Handle> action = mock(Consumer.class);

                RegistryAccessDeniedException exception = assertThrows(RegistryAccessDeniedException.class, () -> registryKey.ifExists(action));
                assertEquals("HKEY_CURRENT_USER\\path\\failure", exception.path());

                verify(action, never()).accept(any());

                advapi32.verify(() -> RegOpenKeyEx(eq(HKEY_CURRENT_USER), eqPointer("path\\failure"), anyInt(), anyInt(), notNull()));
                advapi32.verify(() -> RegCloseKey(notNull()), never());
            }
        }

        @Nested
        @DisplayName("with function")
        class WithFunction {

            @Test
            @DisplayName("success")
            void testSuccess() {
                MemorySegment hKey = mockOpenAndClose(HKEY_CURRENT_USER, "path\\existing");

                RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER.resolve("path\\existing");

                Function<RegistryKey.Handle, String> action = _ -> "new handle";

                Optional<String> result = registryKey.ifExists(action);

                assertEquals(Optional.of("new handle"), result);

                advapi32.verify(() -> RegOpenKeyEx(eq(HKEY_CURRENT_USER), eqPointer("path\\existing"), anyInt(), anyInt(), notNull()));
                advapi32.verify(() -> RegCloseKey(hKey));
            }

            @Test
            @DisplayName("non-existing")
            @SuppressWarnings("resource")
            void testNonExisting() {
                mockOpenFailure(HKEY_CURRENT_USER, "path\\not-found", ERROR_FILE_NOT_FOUND);

                RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER.resolve("path\\not-found");

                @SuppressWarnings("unchecked")
                Function<RegistryKey.Handle, String> action = mock(Function.class);

                registryKey.ifExists(action);

                verify(action, never()).apply(any());

                advapi32.verify(() -> RegOpenKeyEx(eq(HKEY_CURRENT_USER), eqPointer("path\\not-found"), anyInt(), anyInt(), notNull()));
                advapi32.verify(() -> RegCloseKey(notNull()), never());
            }

            @Test
            @DisplayName("failure")
            @SuppressWarnings("resource")
            void testFailure() {
                mockOpenFailure(HKEY_CURRENT_USER, "path\\failure", ERROR_ACCESS_DENIED);

                RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER.resolve("path\\failure");

                @SuppressWarnings("unchecked")
                Function<RegistryKey.Handle, String> action = mock(Function.class);

                RegistryAccessDeniedException exception = assertThrows(RegistryAccessDeniedException.class, () -> registryKey.ifExists(action));
                assertEquals("HKEY_CURRENT_USER\\path\\failure", exception.path());

                verify(action, never()).apply(any());

                advapi32.verify(() -> RegOpenKeyEx(eq(HKEY_CURRENT_USER), eqPointer("path\\failure"), anyInt(), anyInt(), notNull()));
                advapi32.verify(() -> RegCloseKey(notNull()), never());
            }
        }
    }

    @Nested
    @DisplayName("isAccessible")
    class IsAccessible {

        @Test
        @DisplayName("existing")
        void testExisting() {
            MemorySegment hKey = mockOpenAndClose(HKEY_CURRENT_USER, "path\\existing");

            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER.resolve("path\\existing");
            assertTrue(registryKey.isAccessible());

            advapi32.verify(() -> RegOpenKeyEx(eq(HKEY_CURRENT_USER), eqPointer("path\\existing"), anyInt(), anyInt(), notNull()));
            advapi32.verify(() -> RegCloseKey(hKey));
        }

        @Test
        @DisplayName("non-existing")
        void testNonExisting() {
            mockOpenFailure(HKEY_CURRENT_USER, "path\\non-existing", ERROR_FILE_NOT_FOUND);

            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER.resolve("path\\non-existing");
            assertFalse(registryKey.isAccessible());

            advapi32.verify(() -> RegOpenKeyEx(eq(HKEY_CURRENT_USER), eqPointer("path\\non-existing"), anyInt(), anyInt(), notNull()));
            advapi32.verify(() -> RegCloseKey(notNull()), never());
        }

        @Test
        @DisplayName("non-accessible")
        void testNonAccessible() {
            mockOpenFailure(HKEY_CURRENT_USER, "path\\non-accessible", ERROR_ACCESS_DENIED);

            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER.resolve("path\\non-accessible");
            assertFalse(registryKey.isAccessible());

            advapi32.verify(() -> RegOpenKeyEx(eq(HKEY_CURRENT_USER), eqPointer("path\\non-accessible"), anyInt(), anyInt(), notNull()));
            advapi32.verify(() -> RegCloseKey(notNull()), never());
        }

        @Test
        @DisplayName("failure")
        void testFailure() {
            mockOpenFailure(HKEY_CURRENT_USER, "path\\failure", ERROR_INVALID_HANDLE);

            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER.resolve("path\\failure");
            InvalidRegistryHandleException exception = assertThrows(InvalidRegistryHandleException.class, registryKey::isAccessible);
            assertEquals("HKEY_CURRENT_USER\\path\\failure", exception.path());

            advapi32.verify(() -> RegOpenKeyEx(eq(HKEY_CURRENT_USER), eqPointer("path\\failure"), anyInt(), anyInt(), notNull()));
            advapi32.verify(() -> RegCloseKey(notNull()), never());
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
                MemorySegment hKey = mockOpenAndClose(HKEY_CURRENT_USER, "path\\existing");

                RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER.resolve("path\\existing");

                @SuppressWarnings("unchecked")
                Consumer<RegistryKey.Handle> action = mock(Consumer.class);

                registryKey.ifAccessible(action);

                verify(action).accept(any());

                advapi32.verify(() -> RegOpenKeyEx(eq(HKEY_CURRENT_USER), eqPointer("path\\existing"), anyInt(), anyInt(), notNull()));
                advapi32.verify(() -> RegCloseKey(hKey));
            }

            @Test
            @DisplayName("non-existing")
            @SuppressWarnings("resource")
            void testNonExisting() {
                mockOpenFailure(HKEY_CURRENT_USER, "path\\non-existing", ERROR_FILE_NOT_FOUND);

                RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER.resolve("path\\non-existing");

                @SuppressWarnings("unchecked")
                Consumer<RegistryKey.Handle> action = mock(Consumer.class);

                registryKey.ifAccessible(action);

                verify(action, never()).accept(any());

                advapi32.verify(() -> RegOpenKeyEx(eq(HKEY_CURRENT_USER), eqPointer("path\\non-existing"), anyInt(), anyInt(), notNull()));
                advapi32.verify(() -> RegCloseKey(notNull()), never());
            }

            @Test
            @DisplayName("access denied")
            @SuppressWarnings("resource")
            void testAccessDenied() {
                mockOpenFailure(HKEY_CURRENT_USER, "path\\access-denied", ERROR_ACCESS_DENIED);

                RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER.resolve("path\\access-denied");

                @SuppressWarnings("unchecked")
                Consumer<RegistryKey.Handle> action = mock(Consumer.class);

                registryKey.ifAccessible(action);

                verify(action, never()).accept(any());

                advapi32.verify(() -> RegOpenKeyEx(eq(HKEY_CURRENT_USER), eqPointer("path\\access-denied"), anyInt(), anyInt(), notNull()));
                advapi32.verify(() -> RegCloseKey(notNull()), never());
            }

            @Test
            @DisplayName("failure")
            @SuppressWarnings("resource")
            void testFailure() {
                mockOpenFailure(HKEY_CURRENT_USER, "path\\failure", ERROR_INVALID_HANDLE);

                RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER.resolve("path\\failure");

                @SuppressWarnings("unchecked")
                Consumer<RegistryKey.Handle> action = mock(Consumer.class);

                InvalidRegistryHandleException exception = assertThrows(InvalidRegistryHandleException.class, () -> registryKey.ifAccessible(action));
                assertEquals("HKEY_CURRENT_USER\\path\\failure", exception.path());

                verify(action, never()).accept(any());

                advapi32.verify(() -> RegOpenKeyEx(eq(HKEY_CURRENT_USER), eqPointer("path\\failure"), anyInt(), anyInt(), notNull()));
                advapi32.verify(() -> RegCloseKey(notNull()), never());
            }
        }

        @Nested
        @DisplayName("with function")
        class WithFunction {

            @Test
            @DisplayName("success")
            void testSuccess() {
                MemorySegment hKey = mockOpenAndClose(HKEY_CURRENT_USER, "path\\existing");

                RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER.resolve("path\\existing");

                Function<RegistryKey.Handle, String> action = _ -> "new handle";

                Optional<String> result = registryKey.ifAccessible(action);

                assertEquals(Optional.of("new handle"), result);

                advapi32.verify(() -> RegOpenKeyEx(eq(HKEY_CURRENT_USER), eqPointer("path\\existing"), anyInt(), anyInt(), notNull()));
                advapi32.verify(() -> RegCloseKey(hKey));
            }

            @Test
            @DisplayName("non-existing")
            @SuppressWarnings("resource")
            void testNonExisting() {
                mockOpenFailure(HKEY_CURRENT_USER, "path\\non-existing", ERROR_FILE_NOT_FOUND);

                RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER.resolve("path\\non-existing");

                @SuppressWarnings("unchecked")
                Function<RegistryKey.Handle, String> action = mock(Function.class);

                registryKey.ifAccessible(action);

                verify(action, never()).apply(any());

                advapi32.verify(() -> RegOpenKeyEx(eq(HKEY_CURRENT_USER), eqPointer("path\\non-existing"), anyInt(), anyInt(), notNull()));
                advapi32.verify(() -> RegCloseKey(notNull()), never());
            }

            @Test
            @DisplayName("access denied")
            @SuppressWarnings("resource")
            void testAccessDenied() {
                mockOpenFailure(HKEY_CURRENT_USER, "path\\access-denied", ERROR_ACCESS_DENIED);

                RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER.resolve("path\\access-denied");

                @SuppressWarnings("unchecked")
                Function<RegistryKey.Handle, String> action = mock(Function.class);

                registryKey.ifAccessible(action);

                verify(action, never()).apply(any());

                advapi32.verify(() -> RegOpenKeyEx(eq(HKEY_CURRENT_USER), eqPointer("path\\access-denied"), anyInt(), anyInt(), notNull()));
                advapi32.verify(() -> RegCloseKey(notNull()), never());
            }

            @Test
            @DisplayName("failure")
            @SuppressWarnings("resource")
            void testFailure() {
                mockOpenFailure(HKEY_CURRENT_USER, "path\\failure", ERROR_INVALID_HANDLE);

                RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER.resolve("path\\failure");

                @SuppressWarnings("unchecked")
                Function<RegistryKey.Handle, String> action = mock(Function.class);

                InvalidRegistryHandleException exception = assertThrows(InvalidRegistryHandleException.class, () -> registryKey.ifAccessible(action));
                assertEquals("HKEY_CURRENT_USER\\path\\failure", exception.path());

                verify(action, never()).apply(any());

                advapi32.verify(() -> RegOpenKeyEx(eq(HKEY_CURRENT_USER), eqPointer("path\\failure"), anyInt(), anyInt(), notNull()));
                advapi32.verify(() -> RegCloseKey(notNull()), never());
            }
        }
    }

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("non-existing")
        void testCreateNonExisting() {
            MemorySegment hKey = newHKEY(arena);

            advapi32.when(() -> RegCreateKeyEx(eq(HKEY_CURRENT_USER), eqPointer("path\\new"),
                    anyInt(), notNull(), anyInt(), anyInt(), notNull(), notNull(), notNull()))
                    .thenAnswer(i -> {
                        setHKEY(i.getArgument(7, MemorySegment.class), hKey);
                        i.getArgument(8, MemorySegment.class).set(ValueLayout.JAVA_INT, 0, REG_CREATED_NEW_KEY);

                        return ERROR_SUCCESS;
                    });

            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER.resolve("path\\new");
            registryKey.create();

            advapi32.verify(() -> RegCreateKeyEx(eq(HKEY_CURRENT_USER), eqPointer("path\\new"),
                    anyInt(), notNull(), anyInt(), anyInt(), notNull(), notNull(), notNull()));
            advapi32.verify(() -> RegCloseKey(hKey));
        }

        @Test
        @DisplayName("existing")
        void testCreateExisting() {
            MemorySegment hKey = newHKEY(arena);

            advapi32.when(() -> RegCreateKeyEx(eq(HKEY_CURRENT_USER), eqPointer("path\\existing"),
                    anyInt(), notNull(), anyInt(), anyInt(), notNull(), notNull(), notNull()))
                    .thenAnswer(i -> {
                        setHKEY(i.getArgument(7, MemorySegment.class), hKey);
                        i.getArgument(8, MemorySegment.class).set(ValueLayout.JAVA_INT, 0, REG_OPENED_EXISTING_KEY);

                        return ERROR_SUCCESS;
                    });

            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER.resolve("path\\existing");
            RegistryKeyAlreadyExistsException exception = assertThrows(RegistryKeyAlreadyExistsException.class, registryKey::create);
            assertEquals("HKEY_CURRENT_USER\\path\\existing", exception.path());

            advapi32.verify(() -> RegCreateKeyEx(eq(HKEY_CURRENT_USER), eqPointer("path\\existing"),
                    anyInt(), notNull(), anyInt(), anyInt(), notNull(), notNull(), notNull()));
            advapi32.verify(() -> RegCloseKey(hKey));
        }

        @Test
        @DisplayName("failure")
        void testFailure() {
            advapi32.when(() -> RegCreateKeyEx(eq(HKEY_CURRENT_USER), eqPointer("path\\failure"),
                    anyInt(), notNull(), anyInt(), anyInt(), notNull(), notNull(), notNull()))
                    .thenReturn(ERROR_INVALID_HANDLE);

            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER.resolve("path\\failure");
            InvalidRegistryHandleException exception = assertThrows(InvalidRegistryHandleException.class, registryKey::create);
            assertEquals("HKEY_CURRENT_USER\\path\\failure", exception.path());

            advapi32.verify(() -> RegCreateKeyEx(eq(HKEY_CURRENT_USER), eqPointer("path\\failure"),
                    anyInt(), notNull(), anyInt(), anyInt(), notNull(), notNull(), notNull()));
            advapi32.verify(() -> RegCloseKey(notNull()), never());
        }
    }

    @Nested
    @DisplayName("createIfNotExists")
    class CreateIfNotExists {

        @Test
        @DisplayName("non-existing")
        void testCreateNonExisting() {
            MemorySegment hKey = newHKEY(arena);

            advapi32.when(() -> RegCreateKeyEx(eq(HKEY_CURRENT_USER), eqPointer("path\\new"),
                    anyInt(), notNull(), anyInt(), anyInt(), notNull(), notNull(), notNull()))
                    .thenAnswer(i -> {
                        setHKEY(i.getArgument(7, MemorySegment.class), hKey);
                        i.getArgument(8, MemorySegment.class).set(ValueLayout.JAVA_INT, 0, REG_CREATED_NEW_KEY);

                        return ERROR_SUCCESS;
                    });

            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER.resolve("path\\new");
            assertTrue(registryKey.createIfNotExists());

            advapi32.verify(() -> RegCreateKeyEx(eq(HKEY_CURRENT_USER), eqPointer("path\\new"),
                    anyInt(), notNull(), anyInt(), anyInt(), notNull(), notNull(), notNull()));
            advapi32.verify(() -> RegCloseKey(hKey));
        }

        @Test
        @DisplayName("existing")
        void testCreateExisting() {
            MemorySegment hKey = newHKEY(arena);

            advapi32.when(() -> RegCreateKeyEx(eq(HKEY_CURRENT_USER), eqPointer("path\\existing"),
                    anyInt(), notNull(), anyInt(), anyInt(), notNull(), notNull(), notNull()))
                    .thenAnswer(i -> {
                        setHKEY(i.getArgument(7, MemorySegment.class), hKey);
                        i.getArgument(8, MemorySegment.class).set(ValueLayout.JAVA_INT, 0, REG_OPENED_EXISTING_KEY);

                        return ERROR_SUCCESS;
                    });

            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER.resolve("path\\existing");
            assertFalse(registryKey.createIfNotExists());

            advapi32.verify(() -> RegCreateKeyEx(eq(HKEY_CURRENT_USER), eqPointer("path\\existing"),
                    anyInt(), notNull(), anyInt(), anyInt(), notNull(), notNull(), notNull()));
            advapi32.verify(() -> RegCloseKey(hKey));
        }

        @Test
        @DisplayName("failure")
        void testFailure() {
            advapi32.when(() -> RegCreateKeyEx(eq(HKEY_CURRENT_USER), eqPointer("path\\failure"),
                    anyInt(), notNull(), anyInt(), anyInt(), notNull(), notNull(), notNull()))
                    .thenReturn(ERROR_INVALID_HANDLE);

            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER.resolve("path\\failure");
            InvalidRegistryHandleException exception = assertThrows(InvalidRegistryHandleException.class, registryKey::createIfNotExists);
            assertEquals("HKEY_CURRENT_USER\\path\\failure", exception.path());

            advapi32.verify(() -> RegCreateKeyEx(eq(HKEY_CURRENT_USER), eqPointer("path\\failure"),
                    anyInt(), notNull(), anyInt(), anyInt(), notNull(), notNull(), notNull()));
            advapi32.verify(() -> RegCloseKey(notNull()), never());
        }
    }

    @Nested
    @DisplayName("renameTo")
    class RenameTo {

        @Test
        @DisplayName("existing")
        void testRenameExisting() {
            advapi32.when(() -> RegRenameKey(eq(HKEY_CURRENT_USER), eqPointer("path\\existing"), eqPointer("foo")))
                    .thenReturn(ERROR_SUCCESS);

            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER.resolve("path\\existing");
            RegistryKey renamed = registryKey.renameTo("foo");
            assertEquals(registryKey.resolve("..\\foo"), renamed);

            advapi32.verify(() -> RegRenameKey(eq(HKEY_CURRENT_USER), eqPointer("path\\existing"), eqPointer("foo")));
            advapi32.verify(() -> RegOpenKeyEx(notNull(), notNull(), anyInt(), anyInt(), notNull()), never());
            advapi32.verify(() -> RegCloseKey(notNull()), never());
        }

        @Test
        @DisplayName("non-existing")
        void testNonExisting() {
            advapi32.when(() -> RegRenameKey(eq(HKEY_CURRENT_USER), eqPointer("path\\non-existing"), eqPointer("foo")))
                    .thenReturn(ERROR_FILE_NOT_FOUND);

            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER.resolve("path\\non-existing");
            NoSuchRegistryKeyException exception = assertThrows(NoSuchRegistryKeyException.class, () -> registryKey.renameTo("foo"));
            assertEquals("HKEY_CURRENT_USER\\path\\non-existing", exception.path());

            advapi32.verify(() -> RegRenameKey(eq(HKEY_CURRENT_USER), eqPointer("path\\non-existing"), eqPointer("foo")));
            advapi32.verify(() -> RegOpenKeyEx(notNull(), notNull(), anyInt(), anyInt(), notNull()), never());
            advapi32.verify(() -> RegCloseKey(notNull()), never());
        }

        @Test
        @DisplayName("target exists")
        void testTargetExists() {
            advapi32.when(() -> RegRenameKey(eq(HKEY_CURRENT_USER), eqPointer("path\\existing"), eqPointer("foo")))
                    .thenReturn(ERROR_ACCESS_DENIED);

            MemorySegment targetHkey = mockOpenAndClose(HKEY_CURRENT_USER, "path\\foo");

            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER.resolve("path\\existing");
            RegistryKeyAlreadyExistsException exception = assertThrows(RegistryKeyAlreadyExistsException.class, () -> registryKey.renameTo("foo"));
            assertEquals("HKEY_CURRENT_USER\\path\\foo", exception.path());

            advapi32.verify(() -> RegRenameKey(eq(HKEY_CURRENT_USER), eqPointer("path\\existing"), eqPointer("foo")));
            advapi32.verify(() -> RegOpenKeyEx(eq(HKEY_CURRENT_USER), not(eqPointer("path\\foo")), anyInt(), anyInt(), notNull()), never());
            advapi32.verify(() -> RegCloseKey(not(eq(targetHkey))), never());
        }

        @Test
        @DisplayName("access denied")
        void testAccessDenied() {
            advapi32.when(() -> RegRenameKey(eq(HKEY_CURRENT_USER), eqPointer("path\\existing"), eqPointer("foo")))
                    .thenReturn(ERROR_ACCESS_DENIED);

            mockOpenFailure(HKEY_CURRENT_USER, "path\\foo", ERROR_FILE_NOT_FOUND);

            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER.resolve("path\\existing");
            RegistryAccessDeniedException exception = assertThrows(RegistryAccessDeniedException.class, () -> registryKey.renameTo("foo"));
            assertEquals("HKEY_CURRENT_USER\\path\\existing", exception.path());

            advapi32.verify(() -> RegRenameKey(eq(HKEY_CURRENT_USER), eqPointer("path\\existing"), eqPointer("foo")));
            advapi32.verify(() -> RegOpenKeyEx(not(eq(HKEY_CURRENT_USER)), not(eqPointer("path\\foo")), anyInt(), anyInt(), notNull()),
                    never());
            advapi32.verify(() -> RegCloseKey(notNull()), never());
        }

        @Test
        @DisplayName("failure")
        void testFailure() {
            advapi32.when(() -> RegRenameKey(eq(HKEY_CURRENT_USER), eqPointer("path\\existing"), eqPointer("foo")))
                    .thenReturn(ERROR_INVALID_HANDLE);

            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER.resolve("path\\existing");
            InvalidRegistryHandleException exception = assertThrows(InvalidRegistryHandleException.class, () -> registryKey.renameTo("foo"));
            assertEquals("HKEY_CURRENT_USER\\path\\existing", exception.path());

            advapi32.verify(() -> RegRenameKey(eq(HKEY_CURRENT_USER), eqPointer("path\\existing"), eqPointer("foo")));
            advapi32.verify(() -> RegOpenKeyEx(notNull(), notNull(), anyInt(), anyInt(), notNull()), never());
            advapi32.verify(() -> RegCloseKey(notNull()), never());
        }

        // Cannot test function not available, as this is now caught early on when creating Advapi32Impl instances
        // This will lookup the actual Windows calls already at startup time

        @Test
        @DisplayName("invalid name")
        void testInvalidName() {
            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER.resolve("path\\existing");

            assertThrows(IllegalArgumentException.class, () -> registryKey.renameTo("\\foo"));

            advapi32.verify(() -> RegRenameKey(notNull(), notNull(), notNull()), never());
        }
    }

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("existing")
        void testDeleteExisting() {
            advapi32.when(() -> RegDeleteKeyEx(eq(HKEY_CURRENT_USER), eqPointer("path\\existing"), eq(0), eq(0)))
                    .thenReturn(ERROR_SUCCESS);

            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER.resolve("path\\existing");
            registryKey.delete();

            advapi32.verify(() -> RegDeleteKeyEx(eq(HKEY_CURRENT_USER), eqPointer("path\\existing"), eq(0), eq(0)));
            advapi32.verify(() -> RegOpenKeyEx(notNull(), notNull(), anyInt(), anyInt(), notNull()), never());
            advapi32.verify(() -> RegCloseKey(notNull()), never());
        }

        @Test
        @DisplayName("non-existing")
        void testDeleteNonExisting() {
            advapi32.when(() -> RegDeleteKeyEx(eq(HKEY_CURRENT_USER), eqPointer("path\\non-existing"), eq(0), eq(0)))
                    .thenReturn(ERROR_FILE_NOT_FOUND);

            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER.resolve("path\\non-existing");
            NoSuchRegistryKeyException exception = assertThrows(NoSuchRegistryKeyException.class, registryKey::delete);
            assertEquals("HKEY_CURRENT_USER\\path\\non-existing", exception.path());

            advapi32.verify(() -> RegOpenKeyEx(notNull(), notNull(), anyInt(), anyInt(), notNull()), never());
            advapi32.verify(() -> RegCloseKey(notNull()), never());
        }

        @Test
        @DisplayName("failure")
        void testFailure() {
            advapi32.when(() -> RegDeleteKeyEx(eq(HKEY_CURRENT_USER), eqPointer("path\\failure"), eq(0), eq(0)))
                    .thenReturn(ERROR_INVALID_HANDLE);

            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER.resolve("path\\failure");
            InvalidRegistryHandleException exception = assertThrows(InvalidRegistryHandleException.class, registryKey::delete);
            assertEquals("HKEY_CURRENT_USER\\path\\failure", exception.path());

            advapi32.verify(() -> RegOpenKeyEx(notNull(), notNull(), anyInt(), anyInt(), notNull()), never());
            advapi32.verify(() -> RegCloseKey(notNull()), never());
        }
    }

    @Nested
    @DisplayName("deleteIfExists")
    class DeleteIfExists {

        @Test
        @DisplayName("existing")
        void testDeleteExisting() {
            advapi32.when(() -> RegDeleteKeyEx(eq(HKEY_CURRENT_USER), eqPointer("path\\existing"), eq(0), eq(0)))
                    .thenReturn(ERROR_SUCCESS);

            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER.resolve("path\\existing");
            assertTrue(registryKey.deleteIfExists());

            advapi32.verify(() -> RegDeleteKeyEx(eq(HKEY_CURRENT_USER), eqPointer("path\\existing"), eq(0), eq(0)));
            advapi32.verify(() -> RegOpenKeyEx(notNull(), notNull(), anyInt(), anyInt(), notNull()), never());
            advapi32.verify(() -> RegCloseKey(notNull()), never());
        }

        @Test
        @DisplayName("non-existing")
        void testDeleteNonExisting() {
            advapi32.when(() -> RegDeleteKeyEx(eq(HKEY_CURRENT_USER), eqPointer("path\\non-existing"), eq(0), eq(0)))
                    .thenReturn(ERROR_FILE_NOT_FOUND);

            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER.resolve("path\\non-existing");
            assertFalse(registryKey.deleteIfExists());

            advapi32.verify(() -> RegDeleteKeyEx(eq(HKEY_CURRENT_USER), eqPointer("path\\non-existing"), eq(0), eq(0)));
            advapi32.verify(() -> RegOpenKeyEx(notNull(), notNull(), anyInt(), anyInt(), notNull()), never());
            advapi32.verify(() -> RegCloseKey(notNull()), never());
        }

        @Test
        @DisplayName("failure")
        void testFailure() {
            advapi32.when(() -> RegDeleteKeyEx(eq(HKEY_CURRENT_USER), eqPointer("path\\failure"), eq(0), eq(0)))
                    .thenReturn(ERROR_INVALID_HANDLE);

            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER.resolve("path\\failure");
            InvalidRegistryHandleException exception = assertThrows(InvalidRegistryHandleException.class, registryKey::deleteIfExists);
            assertEquals("HKEY_CURRENT_USER\\path\\failure", exception.path());

            advapi32.verify(() -> RegOpenKeyEx(notNull(), notNull(), anyInt(), anyInt(), notNull()), never());
            advapi32.verify(() -> RegCloseKey(notNull()), never());
        }
    }

    @Nested
    @DisplayName("handle")
    class Handle {

        @Test
        @DisplayName("with no arguments")
        void testNoArguments() {
            MemorySegment hKey = mockOpenAndClose(HKEY_CURRENT_USER, "Software\\JavaSoft\\Prefs");

            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER.resolve("Software\\JavaSoft\\Prefs");
            try (var _ = registryKey.handle()) {
                // Do nothing
            }

            advapi32.verify(() -> RegCreateKeyEx(notNull(), notNull(), anyInt(), notNull(), anyInt(), anyInt(), notNull(), notNull(), notNull()),
                    never());
            advapi32.verify(() -> RegOpenKeyEx(eq(HKEY_CURRENT_USER), eqPointer("Software\\JavaSoft\\Prefs"), anyInt(), eq(KEY_READ), notNull()));
            advapi32.verify(() -> RegCloseKey(hKey));
        }

        @Test
        @DisplayName("with CREATE")
        void testWithCreate() {
            MemorySegment hKey = newHKEY(arena);

            advapi32.when(() -> RegCreateKeyEx(eq(HKEY_CURRENT_USER), eqPointer("Software\\JavaSoft\\Prefs"),
                    anyInt(), notNull(), anyInt(), anyInt(), notNull(), notNull(), notNull()))
                    .thenAnswer(i -> {
                        setHKEY(i.getArgument(7, MemorySegment.class), hKey);
                        // disposition doesn't matter

                        return ERROR_SUCCESS;
                    });

            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER.resolve("Software\\JavaSoft\\Prefs");
            try (var _ = registryKey.handle(RegistryKey.HandleOption.CREATE)) {
                // Do nothing
            }

            advapi32.verify(() -> RegOpenKeyEx(notNull(), notNull(), anyInt(), anyInt(), notNull()), never());
            advapi32.verify(() -> RegCreateKeyEx(eq(HKEY_CURRENT_USER), eqPointer("Software\\JavaSoft\\Prefs"),
                    anyInt(), notNull(), anyInt(), eq(KEY_READ), notNull(), notNull(), notNull()));
            advapi32.verify(() -> RegCloseKey(hKey));
        }

        @Test
        @DisplayName("with MANAGE_VALUES")
        void testWithManageValues() {
            MemorySegment hKey = mockOpenAndClose(HKEY_CURRENT_USER, "Software\\JavaSoft\\Prefs");

            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER.resolve("Software\\JavaSoft\\Prefs");
            try (var _ = registryKey.handle(RegistryKey.HandleOption.MANAGE_VALUES)) {
                // Do nothing
            }

            advapi32.verify(() -> RegCreateKeyEx(notNull(), notNull(), anyInt(), notNull(), anyInt(), anyInt(), notNull(), notNull(), notNull()),
                    never());
            advapi32.verify(() -> RegOpenKeyEx(eq(HKEY_CURRENT_USER), eqPointer("Software\\JavaSoft\\Prefs"),
                    anyInt(), eq(KEY_READ | KEY_SET_VALUE), notNull()));
            advapi32.verify(() -> RegCloseKey(hKey));
        }

        @Test
        @DisplayName("with CREATE and MANAGE_VALUES")
        void testWithCreateAndManageValues() {
            MemorySegment hKey = newHKEY(arena);

            advapi32.when(() -> RegCreateKeyEx(eq(HKEY_CURRENT_USER), eqPointer("Software\\JavaSoft\\Prefs"),
                    anyInt(), notNull(), anyInt(), anyInt(), notNull(), notNull(), notNull()))
                    .thenAnswer(i -> {
                        setHKEY(i.getArgument(7, MemorySegment.class), hKey);
                        // disposition doesn't matter

                        return ERROR_SUCCESS;
                    });

            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER.resolve("Software\\JavaSoft\\Prefs");
            try (var _ = registryKey.handle(RegistryKey.HandleOption.CREATE, RegistryKey.HandleOption.MANAGE_VALUES)) {
                // Do nothing
            }

            advapi32.verify(() -> RegOpenKeyEx(notNull(), notNull(), anyInt(), anyInt(), notNull()), never());
            advapi32.verify(() -> RegCreateKeyEx(eq(HKEY_CURRENT_USER), eqPointer("Software\\JavaSoft\\Prefs"),
                    anyInt(), notNull(), anyInt(), eq(KEY_READ | KEY_SET_VALUE), notNull(), notNull(), notNull()));
            advapi32.verify(() -> RegCloseKey(hKey));
        }

        @Test
        @DisplayName("close twice")
        void testCloseTwice() {
            MemorySegment hKey = mockOpenAndClose(HKEY_CURRENT_USER, "Software\\JavaSoft\\Prefs");

            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER.resolve("Software\\JavaSoft\\Prefs");
            try (RegistryKey.Handle handle = registryKey.handle()) {
                handle.close();
            }

            advapi32.verify(() -> RegCreateKeyEx(notNull(), notNull(), anyInt(), notNull(), anyInt(), anyInt(), notNull(), notNull(), notNull()),
                    never());
            advapi32.verify(() -> RegOpenKeyEx(eq(HKEY_CURRENT_USER), eqPointer("Software\\JavaSoft\\Prefs"),
                    anyInt(), eq(KEY_READ), notNull()));
            advapi32.verify(() -> RegCloseKey(hKey));
        }

        @Test
        @DisplayName("open failure")
        void testOpenFailure() {
            mockOpenFailure(HKEY_CURRENT_USER, "path\\failure", ERROR_ACCESS_DENIED);

            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER.resolve("path\\failure");
            RegistryAccessDeniedException exception = assertThrows(RegistryAccessDeniedException.class, registryKey::handle);
            assertEquals("HKEY_CURRENT_USER\\path\\failure", exception.path());

            advapi32.verify(() -> RegOpenKeyEx(eq(HKEY_CURRENT_USER), eqPointer("path\\failure"), anyInt(), anyInt(), notNull()));
            advapi32.verify(() -> RegCreateKeyEx(notNull(), notNull(), anyInt(), notNull(), anyInt(), anyInt(), notNull(), notNull(), notNull()),
                    never());
            advapi32.verify(() -> RegCloseKey(notNull()), never());
        }

        @Test
        @DisplayName("create failure")
        void testCreateFailure() {
            advapi32.when(() -> RegCreateKeyEx(eq(HKEY_CURRENT_USER), eqPointer("path\\failure"),
                    anyInt(), notNull(), anyInt(), anyInt(), notNull(), notNull(), notNull()))
                    .thenReturn(ERROR_ACCESS_DENIED);

            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER.resolve("path\\failure");
            RegistryAccessDeniedException exception = assertThrows(RegistryAccessDeniedException.class,
                    () -> registryKey.handle(RegistryKey.HandleOption.CREATE));
            assertEquals("HKEY_CURRENT_USER\\path\\failure", exception.path());

            advapi32.verify(() -> RegCreateKeyEx(eq(HKEY_CURRENT_USER), eqPointer("path\\failure"),
                    anyInt(), notNull(), anyInt(), anyInt(), notNull(), notNull(), notNull()));
            advapi32.verify(() -> RegOpenKeyEx(notNull(), notNull(), anyInt(), anyInt(), notNull()), never());
            advapi32.verify(() -> RegCloseKey(notNull()), never());
        }

        @Test
        @DisplayName("close failure")
        void testCloseFailure() {
            MemorySegment hKey = mockOpen(HKEY_CURRENT_USER, "path\\failure");

            mockClose(hKey, ERROR_INVALID_HANDLE);

            mockValue(hKey, StringValue.of("test", "test"), ERROR_ACCESS_DENIED);

            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER.resolve("path\\failure");
            RegistryAccessDeniedException exception = assertThrows(RegistryAccessDeniedException.class, () -> triggerCloseFailure(registryKey));
            assertEquals("HKEY_CURRENT_USER\\path\\failure", exception.path());
            assertThat(exception.getSuppressed(), arrayContaining(instanceOf(InvalidRegistryHandleException.class)));

            advapi32.verify(() -> RegOpenKeyEx(eq(HKEY_CURRENT_USER), eqPointer("path\\failure"), anyInt(), anyInt(), notNull()));
            advapi32.verify(() -> RegCloseKey(hKey));
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
                REGISTRY.HKEY_CURRENT_USER.resolve("Software\\JavaSoft\\Prefs"),
                REGISTRY.HKEY_CURRENT_USER.resolve("Software\\JavaSoft"),
                REGISTRY.HKEY_CURRENT_USER.resolve("Software"),
                REGISTRY.HKEY_CURRENT_USER,
                REGISTRY.HKEY_LOCAL_MACHINE.resolve("Software\\JavaSoft\\Prefs"),
                REGISTRY.HKEY_LOCAL_MACHINE.resolve("Software\\JavaSoft"),
                REGISTRY.HKEY_LOCAL_MACHINE.resolve("Software"),
                REGISTRY.HKEY_LOCAL_MACHINE
        );
        registryKeys.sort(null);

        List<RegistryKey> expected = List.of(
                REGISTRY.HKEY_CURRENT_USER,
                REGISTRY.HKEY_CURRENT_USER.resolve("Software"),
                REGISTRY.HKEY_CURRENT_USER.resolve("Software\\JavaSoft"),
                REGISTRY.HKEY_CURRENT_USER.resolve("Software\\JavaSoft\\Prefs"),
                REGISTRY.HKEY_LOCAL_MACHINE,
                REGISTRY.HKEY_LOCAL_MACHINE.resolve("Software"),
                REGISTRY.HKEY_LOCAL_MACHINE.resolve("Software\\JavaSoft"),
                REGISTRY.HKEY_LOCAL_MACHINE.resolve("Software\\JavaSoft\\Prefs")
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
        RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER.resolve("Software\\JavaSoft\\Prefs");

        return new Arguments[] {
                arguments(registryKey, registryKey, true),
                arguments(registryKey, REGISTRY.HKEY_CURRENT_USER.resolve("Software\\JavaSoft\\Prefs"), true),
                arguments(registryKey, REGISTRY.HKEY_CURRENT_USER.resolve("Software\\JavaSoft\\prefs"), false),
                arguments(registryKey, REGISTRY.HKEY_LOCAL_MACHINE.resolve("Software\\JavaSoft\\Prefs"), false),
                arguments(registryKey, "foo", false),
                arguments(registryKey, null, false),
        };
    }

    @Test
    @DisplayName("hashCode")
    void testHashCode() {
        RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER.resolve("Software\\JavaSoft\\Prefs");

        assertEquals(registryKey.hashCode(), registryKey.hashCode());
        assertEquals(registryKey.hashCode(), REGISTRY.HKEY_CURRENT_USER.resolve("Software\\JavaSoft\\Prefs").hashCode());
    }
}
