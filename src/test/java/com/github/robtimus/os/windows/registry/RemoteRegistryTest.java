/*
 * RemoteRegistryTest.java
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

import static com.github.robtimus.os.windows.registry.ForeignTestUtils.eqPointer;
import static com.github.robtimus.os.windows.registry.RegistryKeyMocks.mockClose;
import static com.github.robtimus.os.windows.registry.RegistryKeyMocks.mockConnectAndClose;
import static com.github.robtimus.os.windows.registry.foreign.Advapi32.RegCloseKey;
import static com.github.robtimus.os.windows.registry.foreign.Advapi32.RegConnectRegistry;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.never;
import java.lang.foreign.MemorySegment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import com.github.robtimus.os.windows.registry.foreign.WinError;
import com.github.robtimus.os.windows.registry.foreign.WinReg;

@SuppressWarnings("nls")
@TestInstance(Lifecycle.PER_CLASS)
class RemoteRegistryTest extends RegistryTestBase {

    @Nested
    @DisplayName("connect")
    class Connect {

        @Test
        @DisplayName("succes")
        void testSuccess() {
            MemorySegment hklmKey = mockConnectAndClose(WinReg.HKEY_LOCAL_MACHINE, "test-machine");
            MemorySegment hkuKey = mockConnectAndClose(WinReg.HKEY_USERS, "test-machine");

            try (var _ = RemoteRegistry.connect("test-machine")) {
                // No need to do anything
            }

            advapi32.verify(() -> RegConnectRegistry(eqPointer("test-machine"), eq(WinReg.HKEY_LOCAL_MACHINE), notNull()));
            advapi32.verify(() -> RegConnectRegistry(eqPointer("test-machine"), eq(WinReg.HKEY_USERS), notNull()));
            advapi32.verify(() -> RegCloseKey(hklmKey));
            advapi32.verify(() -> RegCloseKey(hkuKey));
        }

        @Test
        @DisplayName("HKLM failure")
        void testHKLMFailure() {
            advapi32.when(() -> RegConnectRegistry(eqPointer("test-machine"), eq(WinReg.HKEY_LOCAL_MACHINE), notNull()))
                    .thenReturn(WinError.ERROR_BAD_NETPATH);

            RegistryException exception = assertThrows(RegistryException.class, () -> RemoteRegistry.connect("test-machine"));
            assertEquals("HKEY_LOCAL_MACHINE", exception.path());

            advapi32.verify(() -> RegConnectRegistry(eqPointer("test-machine"), eq(WinReg.HKEY_LOCAL_MACHINE), notNull()));
            advapi32.verify(() -> RegConnectRegistry(eqPointer("test-machine"), eq(WinReg.HKEY_USERS), notNull()), never());
            advapi32.verify(() -> RegCloseKey(notNull()), never());
        }

        @Test
        @DisplayName("HKU failure")
        void testFailure() {
            MemorySegment hklmKey = mockConnectAndClose(WinReg.HKEY_LOCAL_MACHINE, "test-machine");

            advapi32.when(() -> RegConnectRegistry(eqPointer("test-machine"), eq(WinReg.HKEY_USERS), notNull()))
                    .thenReturn(WinError.ERROR_BAD_NETPATH);

            RegistryException exception = assertThrows(RegistryException.class, () -> RemoteRegistry.connect("test-machine"));
            assertEquals("HKEY_USERS", exception.path());

            advapi32.verify(() -> RegConnectRegistry(eqPointer("test-machine"), eq(WinReg.HKEY_LOCAL_MACHINE), notNull()));
            advapi32.verify(() -> RegConnectRegistry(eqPointer("test-machine"), eq(WinReg.HKEY_USERS), notNull()));
            // Verify that RegCloseKey is called exactly one, with hklmKey
            advapi32.verify(() -> RegCloseKey(hklmKey));
            advapi32.verify(() -> RegCloseKey(notNull()));
        }
    }

    @Nested
    @DisplayName("close")
    class Close {

        @Test
        @DisplayName("HKLM failure")
        void testHKLMFailure() {
            MemorySegment hklmKey = mockConnectAndClose(WinReg.HKEY_LOCAL_MACHINE, "test-machine");
            MemorySegment hkuKey = mockConnectAndClose(WinReg.HKEY_USERS, "test-machine");

            mockClose(hklmKey, WinError.ERROR_INVALID_HANDLE);

            @SuppressWarnings("resource")
            RemoteRegistry remoteRegistry = RemoteRegistry.connect("test-machine");
            RegistryException exception = assertThrows(RegistryException.class, remoteRegistry::close);
            assertEquals("HKEY_LOCAL_MACHINE", exception.path());

            assertEquals(0, exception.getSuppressed().length);

            advapi32.verify(() -> RegConnectRegistry(eqPointer("test-machine"), eq(WinReg.HKEY_LOCAL_MACHINE), notNull()));
            advapi32.verify(() -> RegConnectRegistry(eqPointer("test-machine"), eq(WinReg.HKEY_USERS), notNull()));
            advapi32.verify(() -> RegCloseKey(hklmKey));
            advapi32.verify(() -> RegCloseKey(hkuKey));
        }

        @Test
        @DisplayName("HKU failure")
        void testHKUFailure() {
            MemorySegment hklmKey = mockConnectAndClose(WinReg.HKEY_LOCAL_MACHINE, "test-machine");
            MemorySegment hkuKey = mockConnectAndClose(WinReg.HKEY_USERS, "test-machine");

            mockClose(hkuKey, WinError.ERROR_INVALID_HANDLE);

            @SuppressWarnings("resource")
            RemoteRegistry remoteRegistry = RemoteRegistry.connect("test-machine");
            RegistryException exception = assertThrows(RegistryException.class, remoteRegistry::close);
            assertEquals("HKEY_USERS", exception.path());

            assertEquals(0, exception.getSuppressed().length);

            advapi32.verify(() -> RegConnectRegistry(eqPointer("test-machine"), eq(WinReg.HKEY_LOCAL_MACHINE), notNull()));
            advapi32.verify(() -> RegConnectRegistry(eqPointer("test-machine"), eq(WinReg.HKEY_USERS), notNull()));
            advapi32.verify(() -> RegCloseKey(hklmKey));
            advapi32.verify(() -> RegCloseKey(hkuKey));
        }

        @Test
        @DisplayName("duplicate failure")
        void testDuplicateFailure() {
            MemorySegment hklmKey = mockConnectAndClose(WinReg.HKEY_LOCAL_MACHINE, "test-machine");
            MemorySegment hkuKey = mockConnectAndClose(WinReg.HKEY_USERS, "test-machine");

            mockClose(hklmKey, WinError.ERROR_INVALID_HANDLE);
            mockClose(hkuKey, WinError.ERROR_INVALID_HANDLE);

            @SuppressWarnings("resource")
            RemoteRegistry remoteRegistry = RemoteRegistry.connect("test-machine");
            RegistryException exception = assertThrows(RegistryException.class, remoteRegistry::close);
            assertEquals("HKEY_LOCAL_MACHINE", exception.path());

            Throwable[] suppressed = exception.getSuppressed();
            assertEquals(1, suppressed.length);

            RegistryException suppressedException = assertInstanceOf(RegistryException.class, suppressed[0]);
            assertEquals("HKEY_USERS", suppressedException.path());

            advapi32.verify(() -> RegConnectRegistry(eqPointer("test-machine"), eq(WinReg.HKEY_LOCAL_MACHINE), notNull()));
            advapi32.verify(() -> RegConnectRegistry(eqPointer("test-machine"), eq(WinReg.HKEY_USERS), notNull()));
            advapi32.verify(() -> RegCloseKey(hklmKey));
            advapi32.verify(() -> RegCloseKey(hkuKey));
        }
    }
}
