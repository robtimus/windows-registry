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

import static com.github.robtimus.os.windows.registry.foreign.ForeignTestUtils.ALLOCATOR;
import static com.github.robtimus.os.windows.registry.foreign.ForeignUtils.allocateBytes;
import static com.github.robtimus.os.windows.registry.foreign.ForeignUtils.allocateInt;
import static com.github.robtimus.os.windows.registry.foreign.ForeignUtils.getInt;
import static com.github.robtimus.os.windows.registry.foreign.ForeignUtils.toByteArray;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
            MemorySegment phkResult = HKEY.allocateRef(ALLOCATOR);

            int code = Advapi32.RegConnectRegistry(MemorySegment.NULL, INVALID_HKEY, phkResult);

            assertInvalidHandle(code);
            assertNullReference(phkResult);
        }

        @Test
        @DisplayName("all arguments")
        void testAllArguments() {
            MemorySegment lpMachineName = WString.allocate(ALLOCATOR, "localhost");
            MemorySegment phkResult = HKEY.allocateRef(ALLOCATOR);

            int code = Advapi32.RegConnectRegistry(lpMachineName, INVALID_HKEY, phkResult);

            assertInvalidHandle(code);
            assertNullReference(phkResult);
        }
    }

    @Nested
    @DisplayName("RegCreateKeyEx")
    class RegCreateKeyEx {

        @Test
        @DisplayName("minimal arguments")
        void testMinimalArguments() {
            MemorySegment lpSubKey = WString.allocate(ALLOCATOR, "sub");
            MemorySegment phkResult = HKEY.allocateRef(ALLOCATOR);

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

        @Test
        @DisplayName("all arguments")
        void testAllArguments() {
            MemorySegment lpSubKey = WString.allocate(ALLOCATOR, "sub");
            // Don't set lpClass, it's never set in any other code
            MemorySegment phkResult = HKEY.allocateRef(ALLOCATOR);
            MemorySegment lpdwDisposition = allocateInt(ALLOCATOR);

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

    @Test
    @DisplayName("RegDeleteKeyEx")
    void testRegDeleteKey() {
        MemorySegment lpSubKey = WString.allocate(ALLOCATOR, "sub");

        int code = Advapi32.RegDeleteKeyEx(INVALID_HKEY, lpSubKey, 0, 0);

        assertInvalidHandle(code);
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
            MemorySegment lpValueName = WString.allocate(ALLOCATOR, "val");

            int code = Advapi32.RegDeleteValue(INVALID_HKEY, lpValueName);

            assertInvalidHandle(code);
        }
    }

    @Nested
    @DisplayName("RegEnumKeyEx")
    class RegEnumKeyEx {

        @Test
        @DisplayName("minimal arguments")
        void testMinimalArguments() {
            MemorySegment lpName = WString.allocate(ALLOCATOR, 100);
            MemorySegment lpcchName = allocateInt(ALLOCATOR, 100);

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
            assertEquals(100, getInt(lpcchName));
        }

        @Test
        @DisplayName("all arguments")
        void testAllArguments() {
            MemorySegment lpName = WString.allocate(ALLOCATOR, 100);
            MemorySegment lpcchName = allocateInt(ALLOCATOR, 100);
            MemorySegment lpClass = WString.allocate(ALLOCATOR, 100);
            MemorySegment lpcchClass = allocateInt(ALLOCATOR, 100);
            MemorySegment lpftLastWriteTime = FILETIME.allocate(ALLOCATOR);

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
            assertEquals(100, getInt(lpcchName));
            assertUninitializedString(lpClass);
            assertEquals(100, getInt(lpcchClass));

            assertEquals(0, FILETIME.dwLowDateTime(lpftLastWriteTime));
            assertEquals(0, FILETIME.dwHighDateTime(lpftLastWriteTime));
        }
    }

    @Nested
    @DisplayName("RegEnumValue")
    class RegEnumValue {

        @Test
        @DisplayName("minimal arguments")
        void testMinimalArguments() {
            MemorySegment lpValueName = WString.allocate(ALLOCATOR, 100);
            MemorySegment lpcchValueName = allocateInt(ALLOCATOR, 100);

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
            assertEquals(100, getInt(lpcchValueName));
        }

        @Test
        @DisplayName("all arguments")
        void testAllArguments() {
            MemorySegment lpValueName = WString.allocate(ALLOCATOR, 100);
            MemorySegment lpcchValueName = allocateInt(ALLOCATOR, 100);
            MemorySegment lpType = allocateInt(ALLOCATOR);
            MemorySegment lpData = allocateBytes(ALLOCATOR, 100);
            MemorySegment lpcbData = allocateInt(ALLOCATOR, 100);

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
            assertEquals(100, getInt(lpcchValueName));
            assertUninitializedInt(lpType);
            assertUninitializedBytes(lpData);
            assertEquals(100, getInt(lpcbData));
        }
    }

    @Test
    @DisplayName("RegOpenKeyEx")
    void testRegOpenKeyEx() {
        MemorySegment lpSubKey = WString.allocate(ALLOCATOR, "sub");
        MemorySegment phkResult = HKEY.allocateRef(ALLOCATOR);

        int code = Advapi32.RegOpenKeyEx(
                INVALID_HKEY,
                lpSubKey,
                0,
                WinNT.KEY_READ,
                phkResult);

        assertInvalidHandle(code);
        assertNullReference(phkResult);
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
            // Don't set lpClass or lpcchClass, they're never set in any other code
            MemorySegment lpcSubKeys = allocateInt(ALLOCATOR);
            MemorySegment lpcbMaxSubKeyLen = allocateInt(ALLOCATOR);
            MemorySegment lpcbMaxClassLen = allocateInt(ALLOCATOR);
            MemorySegment lpcValues = allocateInt(ALLOCATOR);
            MemorySegment lpcbMaxValueNameLen = allocateInt(ALLOCATOR);
            MemorySegment lpcbMaxValueLen = allocateInt(ALLOCATOR);
            MemorySegment lpcbSecurityDescriptor = allocateInt(ALLOCATOR);
            MemorySegment lpftLastWriteTime = FILETIME.allocate(ALLOCATOR);

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
            MemorySegment lpValueName = WString.allocate(ALLOCATOR, "val");
            MemorySegment lpType = allocateInt(ALLOCATOR);
            MemorySegment lpData = allocateBytes(ALLOCATOR, 100);
            MemorySegment lpcbData = allocateInt(ALLOCATOR, 100);

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
            assertEquals(100, getInt(lpcbData));
        }
    }

    @Nested
    @DisplayName("RegRenameKey")
    class RegRenameKey {

        @Test
        @DisplayName("minimal arguments")
        void testMinimalArguments() {
            MemorySegment lpNewKeyName = WString.allocate(ALLOCATOR, "new");

            int code = Advapi32.RegRenameKey(
                    INVALID_HKEY,
                    MemorySegment.NULL,
                    lpNewKeyName);

            assertInvalidHandle(code);
        }

        @Test
        @DisplayName("all arguments")
        void testAllArguments() {
            MemorySegment lpSubKeyName = WString.allocate(ALLOCATOR, "old");
            MemorySegment lpNewKeyName = WString.allocate(ALLOCATOR, "new");

            int code = Advapi32.RegRenameKey(
                    INVALID_HKEY,
                    lpSubKeyName,
                    lpNewKeyName);

            assertInvalidHandle(code);
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
            MemorySegment lpValueName = WString.allocate(ALLOCATOR, "val");
            MemorySegment lpData = allocateInt(ALLOCATOR, ValueLayout.JAVA_INT.withOrder(ByteOrder.LITTLE_ENDIAN), 100);

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
        assertEquals(0, getInt(segment));
    }

    private void assertUninitializedBytes(MemorySegment segment) {
        assertArrayEquals(new byte[Math.toIntExact(segment.byteSize())], toByteArray(segment));
    }
}
