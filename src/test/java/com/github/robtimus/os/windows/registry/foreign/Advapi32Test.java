/*
 * Advapi32Test.java
 * Copyright 2023 Rob Spoor
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

package com.github.robtimus.os.windows.registry.foreign;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteOrder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import com.github.robtimus.os.windows.registry.foreign.WinDef.FILETIME;
import com.github.robtimus.os.windows.registry.foreign.WinDef.HKEY;

@SuppressWarnings("nls")
class Advapi32Test {

    // Use an invalid memory segment to trigger invalid handle errors
    private static final MemorySegment INVALID_HKEY = MemorySegment.NULL;

    @Test
    @DisplayName("RegCloseKey")
    void testRegCloseKey() {
        int code = Advapi32.RegCloseKey(INVALID_HKEY);

        assertInvalidHandle(code);
    }

    @Nested
    @DisplayName("RegConnectRegistry")
    class RegConnectRegistry {

        @Test
        @DisplayName("minimal arguments")
        void testMinimalArguments() {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment phkResult = HKEY.allocateRef(arena);

                int code = Advapi32.RegConnectRegistry(MemorySegment.NULL, INVALID_HKEY, phkResult);

                assertInvalidHandle(code);
                assertNullReference(phkResult);
            }
        }

        @Test
        @DisplayName("all arguments")
        void testAllArguments() {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment lpMachineName = WString.allocate(arena, "localhost");
                MemorySegment phkResult = HKEY.allocateRef(arena);

                int code = Advapi32.RegConnectRegistry(lpMachineName, INVALID_HKEY, phkResult);

                assertInvalidHandle(code);
                assertNullReference(phkResult);
            }
        }
    }

    @Nested
    @DisplayName("RegCreateKeyEx")
    class RegCreateKeyEx {

        @Test
        @DisplayName("minimal arguments")
        void testMinimalArguments() {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment lpSubKey = WString.allocate(arena, "sub");
                MemorySegment phkResult = HKEY.allocateRef(arena);

                int code = Advapi32.RegCreateKeyEx(
                        INVALID_HKEY,
                        lpSubKey,
                        0,
                        MemorySegment.NULL,
                        WinNT.REG_OPTION_NON_VOLATILE,
                        WinNT.KEY_READ,
                        MemorySegment.NULL,
                        phkResult,
                        MemorySegment.NULL);

                assertInvalidHandle(code);
                assertNullReference(phkResult);
            }
        }

        @Test
        @DisplayName("all arguments")
        void testAllArguments() {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment lpSubKey = WString.allocate(arena, "sub");
                // Don't set lpClass, it's never set in any other code
                MemorySegment phkResult = HKEY.allocateRef(arena);
                MemorySegment lpdwDisposition = arena.allocate(ValueLayout.JAVA_INT);

                int code = Advapi32.RegCreateKeyEx(
                        INVALID_HKEY,
                        lpSubKey,
                        0,
                        MemorySegment.NULL,
                        WinNT.REG_OPTION_NON_VOLATILE,
                        WinNT.KEY_READ,
                        MemorySegment.NULL,
                        phkResult,
                        lpdwDisposition);

                assertInvalidHandle(code);
                assertNullReference(phkResult);
                assertUninitializedInt(lpdwDisposition);
            }
        }
    }

    @Test
    @DisplayName("RegDeleteKeyEx")
    void testRegDeleteKey() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment lpSubKey = WString.allocate(arena, "sub");

            int code = Advapi32.RegDeleteKeyEx(INVALID_HKEY, lpSubKey, 0, 0);

            assertInvalidHandle(code);
        }
    }

    @Nested
    @DisplayName("RegDeleteValue")
    class RegDeleteValue {

        @Test
        @DisplayName("minimal arguments")
        void testMinimalArguments() {
            int code = Advapi32.RegDeleteValue(INVALID_HKEY, MemorySegment.NULL);

            assertInvalidHandle(code);
        }

        @Test
        @DisplayName("all arguments")
        void testAllArguments() {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment lpValueName = WString.allocate(arena, "val");

                int code = Advapi32.RegDeleteValue(INVALID_HKEY, lpValueName);

                assertInvalidHandle(code);
            }
        }
    }

    @Nested
    @DisplayName("RegEnumKeyEx")
    class RegEnumKeyEx {

        @Test
        @DisplayName("minimal arguments")
        void testMinimalArguments() {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment lpName = WString.allocate(arena, 100);
                MemorySegment lpcchName = arena.allocateFrom(ValueLayout.JAVA_INT, 100);

                int code = Advapi32.RegEnumKeyEx(
                        INVALID_HKEY,
                        0,
                        lpName,
                        lpcchName,
                        MemorySegment.NULL,
                        MemorySegment.NULL,
                        MemorySegment.NULL,
                        MemorySegment.NULL);

                assertInvalidHandle(code);
                assertUninitializedString(lpName);
                assertEquals(100, lpcchName.get(ValueLayout.JAVA_INT, 0));
            }
        }

        @Test
        @DisplayName("all arguments")
        void testAllArguments() {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment lpName = WString.allocate(arena, 100);
                MemorySegment lpcchName = arena.allocateFrom(ValueLayout.JAVA_INT, 100);
                MemorySegment lpClass = WString.allocate(arena, 100);
                MemorySegment lpcchClass = arena.allocateFrom(ValueLayout.JAVA_INT, 100);
                MemorySegment lpftLastWriteTime = FILETIME.allocate(arena);

                int code = Advapi32.RegEnumKeyEx(
                        INVALID_HKEY,
                        0,
                        lpName,
                        lpcchName,
                        MemorySegment.NULL,
                        lpClass,
                        lpcchClass,
                        lpftLastWriteTime);

                assertInvalidHandle(code);
                assertUninitializedString(lpName);
                assertEquals(100, lpcchName.get(ValueLayout.JAVA_INT, 0));
                assertUninitializedString(lpClass);
                assertEquals(100, lpcchClass.get(ValueLayout.JAVA_INT, 0));

                assertEquals(0, FILETIME.dwLowDateTime(lpftLastWriteTime));
                assertEquals(0, FILETIME.dwHighDateTime(lpftLastWriteTime));
            }
        }
    }

    @Nested
    @DisplayName("RegEnumValue")
    class RegEnumValue {

        @Test
        @DisplayName("minimal arguments")
        void testMinimalArguments() {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment lpValueName = WString.allocate(arena, 100);
                MemorySegment lpcchValueName = arena.allocateFrom(ValueLayout.JAVA_INT, 100);

                int code = Advapi32.RegEnumValue(
                        INVALID_HKEY,
                        0,
                        lpValueName,
                        lpcchValueName,
                        MemorySegment.NULL,
                        MemorySegment.NULL,
                        MemorySegment.NULL,
                        MemorySegment.NULL);

                assertInvalidHandle(code);
                assertUninitializedString(lpValueName);
                assertEquals(100, lpcchValueName.get(ValueLayout.JAVA_INT, 0));
            }
        }

        @Test
        @DisplayName("all arguments")
        void testAllArguments() {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment lpValueName = WString.allocate(arena, 100);
                MemorySegment lpcchValueName = arena.allocateFrom(ValueLayout.JAVA_INT, 100);
                MemorySegment lpType = arena.allocate(ValueLayout.JAVA_INT);
                MemorySegment lpData = arena.allocate(ValueLayout.JAVA_BYTE, 100);
                MemorySegment lpcbData = arena.allocateFrom(ValueLayout.JAVA_INT, 100);

                int code = Advapi32.RegEnumValue(
                        INVALID_HKEY,
                        0,
                        lpValueName,
                        lpcchValueName,
                        MemorySegment.NULL,
                        lpType,
                        lpData,
                        lpcbData);

                assertInvalidHandle(code);
                assertUninitializedString(lpValueName);
                assertEquals(100, lpcchValueName.get(ValueLayout.JAVA_INT, 0));
                assertUninitializedInt(lpType);
                assertUninitializedBytes(lpData);
                assertEquals(100, lpcbData.get(ValueLayout.JAVA_INT, 0));
            }
        }
    }

    @Test
    @DisplayName("RegOpenKeyEx")
    void testRegOpenKeyEx() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment lpSubKey = WString.allocate(arena, "sub");
            MemorySegment phkResult = HKEY.allocateRef(arena);

            int code = Advapi32.RegOpenKeyEx(
                    INVALID_HKEY,
                    lpSubKey,
                    0,
                    WinNT.KEY_READ,
                    phkResult);

            assertInvalidHandle(code);
            assertNullReference(phkResult);
        }
    }

    @Nested
    @DisplayName("RegQueryInfoKey")
    class RegQueryInfoKey {

        @Test
        @DisplayName("minimal arguments")
        void testMinimalArguments() {
            int code = Advapi32.RegQueryInfoKey(
                    INVALID_HKEY,
                    MemorySegment.NULL,
                    MemorySegment.NULL,
                    MemorySegment.NULL,
                    MemorySegment.NULL,
                    MemorySegment.NULL,
                    MemorySegment.NULL,
                    MemorySegment.NULL,
                    MemorySegment.NULL,
                    MemorySegment.NULL,
                    MemorySegment.NULL,
                    MemorySegment.NULL);

            assertInvalidHandle(code);
        }

        @Test
        @DisplayName("all arguments")
        void testAllArguments() {
            try (Arena arena = Arena.ofConfined()) {
                // Don't set lpClass or lpcchClass, they're never set in any other code
                MemorySegment lpcSubKeys = arena.allocate(ValueLayout.JAVA_INT);
                MemorySegment lpcbMaxSubKeyLen = arena.allocate(ValueLayout.JAVA_INT);
                MemorySegment lpcbMaxClassLen = arena.allocate(ValueLayout.JAVA_INT);
                MemorySegment lpcValues = arena.allocate(ValueLayout.JAVA_INT);
                MemorySegment lpcbMaxValueNameLen = arena.allocate(ValueLayout.JAVA_INT);
                MemorySegment lpcbMaxValueLen = arena.allocate(ValueLayout.JAVA_INT);
                MemorySegment lpcbSecurityDescriptor = arena.allocate(ValueLayout.JAVA_INT);
                MemorySegment lpftLastWriteTime = FILETIME.allocate(arena);

                int code = Advapi32.RegQueryInfoKey(
                        INVALID_HKEY,
                        MemorySegment.NULL,
                        MemorySegment.NULL,
                        MemorySegment.NULL,
                        lpcSubKeys,
                        lpcbMaxSubKeyLen,
                        lpcbMaxClassLen,
                        lpcValues,
                        lpcbMaxValueNameLen,
                        lpcbMaxValueLen,
                        lpcbSecurityDescriptor,
                        lpftLastWriteTime);

                assertInvalidHandle(code);
                assertUninitializedInt(lpcSubKeys);
                assertUninitializedInt(lpcbMaxSubKeyLen);
                assertUninitializedInt(lpcbMaxClassLen);
                assertUninitializedInt(lpcValues);
                assertUninitializedInt(lpcbMaxValueNameLen);
                assertUninitializedInt(lpcbMaxValueLen);
                assertUninitializedInt(lpcbSecurityDescriptor);

                assertEquals(0, FILETIME.dwLowDateTime(lpftLastWriteTime));
                assertEquals(0, FILETIME.dwHighDateTime(lpftLastWriteTime));
            }
        }
    }

    @Nested
    @DisplayName("RegQueryValueEx")
    class RegQueryValueEx {

        @Test
        @DisplayName("minimal arguments")
        void testMinimalArguments() {
            int code = Advapi32.RegQueryValueEx(
                    INVALID_HKEY,
                    MemorySegment.NULL,
                    MemorySegment.NULL,
                    MemorySegment.NULL,
                    MemorySegment.NULL,
                    MemorySegment.NULL);

            assertInvalidHandle(code);
        }

        @Test
        @DisplayName("all arguments")
        void testAllArguments() {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment lpValueName = WString.allocate(arena, "val");
                MemorySegment lpType = arena.allocate(ValueLayout.JAVA_INT);
                MemorySegment lpData = arena.allocate(ValueLayout.JAVA_BYTE, 100);
                MemorySegment lpcbData = arena.allocateFrom(ValueLayout.JAVA_INT, 100);

                int code = Advapi32.RegQueryValueEx(
                        INVALID_HKEY,
                        lpValueName,
                        MemorySegment.NULL,
                        lpType,
                        lpData,
                        lpcbData);

                assertInvalidHandle(code);
                assertUninitializedInt(lpType);
                assertUninitializedBytes(lpData);
                assertEquals(100, lpcbData.get(ValueLayout.JAVA_INT, 0));
            }
        }
    }

    @Nested
    @DisplayName("RegRenameKey")
    class RegRenameKey {

        @Test
        @DisplayName("minimal arguments")
        void testMinimalArguments() {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment lpNewKeyName = WString.allocate(arena, "new");

                int code = Advapi32.RegRenameKey(
                        INVALID_HKEY,
                        MemorySegment.NULL,
                        lpNewKeyName);

                assertInvalidHandle(code);
            }
        }

        @Test
        @DisplayName("all arguments")
        void testAllArguments() {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment lpSubKeyName = WString.allocate(arena, "old");
                MemorySegment lpNewKeyName = WString.allocate(arena, "new");

                int code = Advapi32.RegRenameKey(
                        INVALID_HKEY,
                        lpSubKeyName,
                        lpNewKeyName);

                assertInvalidHandle(code);
            }
        }
    }

    @Nested
    @DisplayName("RegSetValueEx")
    class RegSetValueEx {

        @Test
        @DisplayName("minimal arguments")
        void testMinimalArguments() {
            int code = Advapi32.RegSetValueEx(
                    INVALID_HKEY,
                    MemorySegment.NULL,
                    0,
                    WinNT.REG_BINARY,
                    MemorySegment.NULL,
                    0);

            assertInvalidHandle(code);
        }

        @Test
        @DisplayName("all arguments")
        void testAllArguments() {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment lpValueName = WString.allocate(arena, "val");
                MemorySegment lpData = arena.allocateFrom(ValueLayout.JAVA_INT.withOrder(ByteOrder.LITTLE_ENDIAN), 100);

                int code = Advapi32.RegSetValueEx(
                        INVALID_HKEY,
                        lpValueName,
                        0,
                        WinNT.REG_DWORD_LITTLE_ENDIAN,
                        lpData,
                        100);

                assertInvalidHandle(code);
            }
        }
    }

    private void assertInvalidHandle(int code) {
        assertEquals(WinError.ERROR_INVALID_HANDLE, code);
    }

    private void assertNullReference(MemorySegment phkResult) {
        MemorySegment hKey = HKEY.target(phkResult);
        assertEquals(MemorySegment.NULL, hKey);
    }

    private void assertUninitializedString(MemorySegment segment) {
        assertEquals("", WString.getString(segment));
    }

    private void assertUninitializedInt(MemorySegment segment) {
        assertEquals(0, segment.get(ValueLayout.JAVA_INT, 0));
    }

    private void assertUninitializedBytes(MemorySegment segment) {
        assertArrayEquals(new byte[Math.toIntExact(segment.byteSize())], segment.toArray(ValueLayout.JAVA_BYTE));
    }
}
