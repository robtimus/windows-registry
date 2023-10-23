/*
 * RegistryAccessDeniedExceptionTest.java
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

package com.github.robtimus.os.windows.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.sun.jna.platform.win32.WinError;

@SuppressWarnings("nls")
class RegistryAccessDeniedExceptionTest {

    @Test
    @DisplayName("RegistryAccessDeniedException(String)")
    void testConstructorWithoutMachineName() {
        RegistryAccessDeniedException exception = new RegistryAccessDeniedException("path");

        assertEquals(WinError.ERROR_ACCESS_DENIED, exception.errorCode());
        assertEquals("path", exception.path());
        assertNull(exception.machineName());
    }
}
