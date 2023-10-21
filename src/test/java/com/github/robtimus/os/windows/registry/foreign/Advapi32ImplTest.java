/*
 * Advapi32ImplTest.java
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
class Advapi32ImplTest {

    // Use an HKEY with an invalid memory segment to trigger invalid handle errors
    private static final HKEY INVALID_HKEY = new HKEY(MemorySegment.NULL);

    @Test
    @DisplayName("RegCloseKey")
    void testRegCloseKey() {
        int code = Advapi32.INSTANCE.RegCloseKey(INVALID_HKEY);

        assertInvalidHandle(code);
    }

    @Nested
    @DisplayName("RegConnectRegistry")
    class RegConnectRegistry {

        @Test
        @DisplayName("minimal arguments")
        void testMinimalArguments() {
            HKEY.Reference phkResult = HKEY.uninitializedReference(ALLOCATOR);

            int code = Advapi32.INSTANCE.RegConnectRegistry(null, INVALID_HKEY, phkResult);

            assertInvalidHandle(code);
            assertNullReference(phkResult);
        }

        @Test
        @DisplayName("all arguments")
        void testAllArguments() {
            StringPointer lpMachineName = StringPointer.withValue("localhost", ALLOCATOR);
            HKEY.Reference phkResult = HKEY.uninitializedReference(ALLOCATOR);

            int code = Advapi32.INSTANCE.RegConnectRegistry(lpMachineName, INVALID_HKEY, phkResult);

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
            StringPointer lpSubKey = StringPointer.withValue("sub", ALLOCATOR);
            HKEY.Reference phkResult = HKEY.uninitializedReference(ALLOCATOR);

            int code = Advapi32.INSTANCE.RegCreateKeyEx(INVALID_HKEY, lpSubKey, 0, null, WinNT.REG_OPTION_NON_VOLATILE, WinNT.KEY_READ, null,
                    phkResult, null);

            assertInvalidHandle(code);
            assertNullReference(phkResult);
        }

        @Test
        @DisplayName("all arguments")
        void testAllArguments() {
            StringPointer lpSubKey = StringPointer.withValue("sub", ALLOCATOR);
            // Don't set lpClass, it's never set in any other code
            HKEY.Reference phkResult = HKEY.uninitializedReference(ALLOCATOR);
            IntPointer lpdwDisposition = IntPointer.uninitialized(ALLOCATOR);

            int code = Advapi32.INSTANCE.RegCreateKeyEx(INVALID_HKEY, lpSubKey, 0, null, WinNT.REG_OPTION_NON_VOLATILE, WinNT.KEY_READ, null,
                    phkResult, lpdwDisposition);

            assertInvalidHandle(code);
            assertNullReference(phkResult);
            assertUninitialized(lpdwDisposition);
        }
    }

    @Test
    @DisplayName("RegDeleteKey")
    void testRegDeleteKey() {
        StringPointer lpSubKey = StringPointer.withValue("sub", ALLOCATOR);

        int code = Advapi32.INSTANCE.RegDeleteKey(INVALID_HKEY, lpSubKey);

        assertInvalidHandle(code);
    }

    @Nested
    @DisplayName("RegDeleteValue")
    class RegDeleteValue {

        @Test
        @DisplayName("minimal arguments")
        void testMinimalArguments() {
            int code = Advapi32.INSTANCE.RegDeleteValue(INVALID_HKEY, null);

            assertInvalidHandle(code);
        }

        @Test
        @DisplayName("all arguments")
        void testAllArguments() {
            StringPointer lpValueName = StringPointer.withValue("val", ALLOCATOR);

            int code = Advapi32.INSTANCE.RegDeleteValue(INVALID_HKEY, lpValueName);

            assertInvalidHandle(code);
        }
    }

    @Nested
    @DisplayName("RegEnumKeyEx")
    class RegEnumKeyEx {

        @Test
        @DisplayName("minimal arguments")
        void testMinimalArguments() {
            StringPointer lpName = StringPointer.uninitialized(100, ALLOCATOR);
            IntPointer lpcchName = IntPointer.withValue(100, ALLOCATOR);

            int code = Advapi32.INSTANCE.RegEnumKeyEx(INVALID_HKEY, 0, lpName, lpcchName, null, null, null, null);

            assertInvalidHandle(code);
            assertUninitialized(lpName);
            assertEquals(100, lpcchName.value());
        }

        @Test
        @DisplayName("all arguments")
        void testAllArguments() {
            StringPointer lpName = StringPointer.uninitialized(100, ALLOCATOR);
            IntPointer lpcchName = IntPointer.withValue(100, ALLOCATOR);
            StringPointer lpClass = StringPointer.uninitialized(100, ALLOCATOR);
            IntPointer lpcchClass = IntPointer.withValue(100, ALLOCATOR);
            FILETIME lpftLastWriteTime = new FILETIME(ALLOCATOR);

            int code = Advapi32.INSTANCE.RegEnumKeyEx(INVALID_HKEY, 0, lpName, lpcchName, null, lpClass, lpcchClass, lpftLastWriteTime);

            assertInvalidHandle(code);
            assertUninitialized(lpName);
            assertEquals(100, lpcchName.value());
            assertUninitialized(lpClass);
            assertEquals(100, lpcchClass.value());

            assertEquals(0, lpftLastWriteTime.dwLowDateTime());
            assertEquals(0, lpftLastWriteTime.dwHighDateTime());
        }
    }

    @Nested
    @DisplayName("RegEnumValue")
    class RegEnumValue {

        @Test
        @DisplayName("minimal arguments")
        void testMinimalArguments() {
            StringPointer lpValueName = StringPointer.uninitialized(100, ALLOCATOR);
            IntPointer lpcchValueName = IntPointer.withValue(100, ALLOCATOR);

            int code = Advapi32.INSTANCE.RegEnumValue(INVALID_HKEY, 0, lpValueName, lpcchValueName, null, null, null, null);

            assertInvalidHandle(code);
            assertUninitialized(lpValueName);
            assertEquals(100, lpcchValueName.value());
        }

        @Test
        @DisplayName("all arguments")
        void testAllArguments() {
            StringPointer lpValueName = StringPointer.uninitialized(100, ALLOCATOR);
            IntPointer lpcchValueName = IntPointer.withValue(100, ALLOCATOR);
            IntPointer lpType = IntPointer.uninitialized(ALLOCATOR);
            BytePointer lpData = BytePointer.unitialized(100, ALLOCATOR);
            IntPointer lpcbData = IntPointer.withValue(100, ALLOCATOR);

            int code = Advapi32.INSTANCE.RegEnumValue(INVALID_HKEY, 0, lpValueName, lpcchValueName, null, lpType, lpData, lpcbData);

            assertInvalidHandle(code);
            assertUninitialized(lpValueName);
            assertEquals(100, lpcchValueName.value());
            assertUninitialized(lpType);
            assertUninitialized(lpData);
            assertEquals(100, lpcbData.value());
        }
    }

    @Test
    @DisplayName("RegOpenKeyEx")
    void testRegOpenKeyEx() {
        StringPointer lpSubKey = StringPointer.withValue("sub", ALLOCATOR);
        HKEY.Reference phkResult = HKEY.uninitializedReference(ALLOCATOR);

        int code = Advapi32.INSTANCE.RegOpenKeyEx(INVALID_HKEY, lpSubKey, 0, WinNT.KEY_READ, phkResult);

        assertInvalidHandle(code);
        assertNullReference(phkResult);
    }

    @Nested
    @DisplayName("RegQueryInfoKey")
    class RegQueryInfoKey {

        @Test
        @DisplayName("minimal arguments")
        void testMinimalArguments() {
            int code = Advapi32.INSTANCE.RegQueryInfoKey(INVALID_HKEY, null, null, null, null, null, null, null, null, null, null, null);

            assertInvalidHandle(code);
        }

        @Test
        @DisplayName("all arguments")
        void testAllArguments() {
            // Don't set lpClass or lpcchClass, they're never set in any other code
            IntPointer lpcSubKeys = IntPointer.uninitialized(ALLOCATOR);
            IntPointer lpcbMaxSubKeyLen = IntPointer.uninitialized(ALLOCATOR);
            IntPointer lpcbMaxClassLen = IntPointer.uninitialized(ALLOCATOR);
            IntPointer lpcValues = IntPointer.uninitialized(ALLOCATOR);
            IntPointer lpcbMaxValueNameLen = IntPointer.uninitialized(ALLOCATOR);
            IntPointer lpcbMaxValueLen = IntPointer.uninitialized(ALLOCATOR);
            IntPointer lpcbSecurityDescriptor = IntPointer.uninitialized(ALLOCATOR);
            FILETIME lpftLastWriteTime = new FILETIME(ALLOCATOR);

            int code = Advapi32.INSTANCE.RegQueryInfoKey(INVALID_HKEY, null, null, null, lpcSubKeys, lpcbMaxSubKeyLen, lpcbMaxClassLen,
                    lpcValues, lpcbMaxValueNameLen, lpcbMaxValueLen, lpcbSecurityDescriptor, lpftLastWriteTime);

            assertInvalidHandle(code);
            assertUninitialized(lpcSubKeys);
            assertUninitialized(lpcbMaxSubKeyLen);
            assertUninitialized(lpcbMaxClassLen);
            assertUninitialized(lpcValues);
            assertUninitialized(lpcbMaxValueNameLen);
            assertUninitialized(lpcbMaxValueLen);
            assertUninitialized(lpcbSecurityDescriptor);

            assertEquals(0, lpftLastWriteTime.dwLowDateTime());
            assertEquals(0, lpftLastWriteTime.dwHighDateTime());
        }
    }

    @Nested
    @DisplayName("RegQueryValueEx")
    class RegQueryValueEx {

        @Test
        @DisplayName("minimal arguments")
        void testMinimalArguments() {
            int code = Advapi32.INSTANCE.RegQueryValueEx(INVALID_HKEY, null, null, null, null, null);

            assertInvalidHandle(code);
        }

        @Test
        @DisplayName("all arguments")
        void testAllArguments() {
            StringPointer lpValueName = StringPointer.withValue("val", ALLOCATOR);
            IntPointer lpType = IntPointer.uninitialized(ALLOCATOR);
            BytePointer lpData = BytePointer.unitialized(100, ALLOCATOR);
            IntPointer lpcbData = IntPointer.withValue(100, ALLOCATOR);

            int code = Advapi32.INSTANCE.RegQueryValueEx(INVALID_HKEY, lpValueName, null, lpType, lpData, lpcbData);

            assertInvalidHandle(code);
            assertUninitialized(lpType);
            assertUninitialized(lpData);
            assertEquals(100, lpcbData.value());
        }
    }

    @Nested
    @DisplayName("RegRenameKey")
    class RegRenameKey {

        @Test
        @DisplayName("minimal arguments")
        void testMinimalArguments() {
            StringPointer lpNewKeyName = StringPointer.withValue("new", ALLOCATOR);

            int code = Advapi32.INSTANCE.RegRenameKey(INVALID_HKEY, null, lpNewKeyName);

            assertInvalidHandle(code);
        }

        @Test
        @DisplayName("all arguments")
        void testAllArguments() {
            StringPointer lpSubKeyName = StringPointer.withValue("old", ALLOCATOR);
            StringPointer lpNewKeyName = StringPointer.withValue("new", ALLOCATOR);

            int code = Advapi32.INSTANCE.RegRenameKey(INVALID_HKEY, lpSubKeyName, lpNewKeyName);

            assertInvalidHandle(code);
        }
    }

    @Nested
    @DisplayName("RegSetValueEx")
    class RegSetValueEx {

        @Test
        @DisplayName("minimal arguments")
        void testMinimalArguments() {
            int code = Advapi32.INSTANCE.RegSetValueEx(INVALID_HKEY, null, 0, WinNT.REG_BINARY, null, 0);

            assertInvalidHandle(code);
        }

        @Test
        @DisplayName("all arguments")
        void testAllArguments() {
            StringPointer lpValueName = StringPointer.withValue("val", ALLOCATOR);
            BytePointer lpData = BytePointer.withInt(100, ValueLayout.JAVA_INT.withOrder(ByteOrder.LITTLE_ENDIAN), ALLOCATOR);

            int code = Advapi32.INSTANCE.RegSetValueEx(INVALID_HKEY, lpValueName, 0, WinNT.REG_DWORD_LITTLE_ENDIAN, lpData, 100);

            assertInvalidHandle(code);
        }
    }

    private void assertInvalidHandle(int code) {
        assertEquals(WinError.ERROR_INVALID_HANDLE, code);
    }

    private void assertNullReference(HKEY.Reference phkResult) {
        HKEY hKey = phkResult.value();
        assertEquals(MemorySegment.NULL, hKey.segment());
    }

    private void assertUninitialized(StringPointer pointer) {
        assertEquals("", pointer.value());
    }

    private void assertUninitialized(IntPointer pointer) {
        assertEquals(0, pointer.value());
    }

    private void assertUninitialized(BytePointer pointer) {
        assertArrayEquals(new byte[pointer.size()], pointer.toByteArray());
    }
}
