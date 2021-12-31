/*
 * RemoteRegistryKeyTest.java
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import com.sun.jna.platform.win32.WinError;
import com.sun.jna.platform.win32.WinReg;
import com.sun.jna.platform.win32.WinReg.HKEY;

@SuppressWarnings("nls")
@TestInstance(Lifecycle.PER_CLASS)
class RemoteRegistryKeyTest extends RegistryKeyTest {

    @Nested
    @DisplayName("Connector")
    class Connector {

        @Nested
        @DisplayName("at")
        class At {

            @Test
            @DisplayName("succes")
            void testSuccess() {
                HKEY hKey = mockConnectAndClose(WinReg.HKEY_LOCAL_MACHINE, "test-machine");

                try (RemoteRegistryKey registryKey = RemoteRegistryKey.HKEY_LOCAL_MACHINE.at("test-machine")) {
                    // No need to do anything
                }

                verify(RegistryKey.api).RegConnectRegistry(eq("test-machine"), eq(WinReg.HKEY_LOCAL_MACHINE), any());
                verify(RegistryKey.api).RegCloseKey(hKey);
            }

            @Test
            @DisplayName("failure")
            void testFailure() {
                when(RegistryKey.api.RegConnectRegistry(eq("test-machine"), eq(WinReg.HKEY_LOCAL_MACHINE), any()))
                        .thenReturn(WinError.ERROR_BAD_NETPATH);

                RegistryException exception = assertThrows(RegistryException.class, () -> RemoteRegistryKey.HKEY_LOCAL_MACHINE.at("test-machine"));
                assertEquals("HKEY_LOCAL_MACHINE", exception.path());

                verify(RegistryKey.api).RegConnectRegistry(eq("test-machine"), eq(WinReg.HKEY_LOCAL_MACHINE), any());
                verify(RegistryKey.api, never()).RegCloseKey(any());
            }
        }
    }
}
