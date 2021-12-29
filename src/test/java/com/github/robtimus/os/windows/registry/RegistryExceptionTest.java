/*
 * RegistryExceptionTest.java
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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import com.sun.jna.platform.win32.WinError;

@SuppressWarnings("nls")
final class RegistryExceptionTest {

    private RegistryExceptionTest() {
    }

    @Nested
    @DisplayName("of(int, String)")
    class OfForKey {

        @Test
        @DisplayName("ERROR_KEY_DELETED")
        void testKeyDeleted() {
            RegistryException exception = RegistryException.of(WinError.ERROR_KEY_DELETED, "path");
            assertInstanceOf(NoSuchRegistryKeyException.class, exception);
            assertEquals(WinError.ERROR_KEY_DELETED, exception.errorCode());
            assertEquals("path", exception.path());
        }

        @Test
        @DisplayName("ERROR_FILE_NOT_FOUND")
        void testFileNotFound() {
            RegistryException exception = RegistryException.of(WinError.ERROR_FILE_NOT_FOUND, "path");
            assertInstanceOf(NoSuchRegistryKeyException.class, exception);
            assertEquals(WinError.ERROR_FILE_NOT_FOUND, exception.errorCode());
            assertEquals("path", exception.path());
        }

        @Test
        @DisplayName("ERROR_ACCESS_DENIED")
        void testAccessDenied() {
            RegistryException exception = RegistryException.of(WinError.ERROR_ACCESS_DENIED, "path");
            assertInstanceOf(RegistryAccessDeniedException.class, exception);
            assertEquals(WinError.ERROR_ACCESS_DENIED, exception.errorCode());
            assertEquals("path", exception.path());
        }

        @Test
        @DisplayName("ERROR_INVALID_HANDLE")
        void testInvalidHandle() {
            RegistryException exception = RegistryException.of(WinError.ERROR_INVALID_HANDLE, "path");
            assertInstanceOf(InvalidRegistryHandleException.class, exception);
            assertEquals(WinError.ERROR_INVALID_HANDLE, exception.errorCode());
            assertEquals("path", exception.path());
        }

        @Test
        @DisplayName("other")
        void testOther() {
            RegistryException exception = RegistryException.of(WinError.ERROR_REGISTRY_CORRUPT, "path");
            assertEquals(RegistryException.class, exception.getClass());
            assertEquals(WinError.ERROR_REGISTRY_CORRUPT, exception.errorCode());
            assertEquals("path", exception.path());
        }
    }

    @Nested
    @DisplayName("of(int, String, String)")
    class OfForValue {

        @Test
        @DisplayName("ERROR_KEY_DELETED")
        void testKeyDeleted() {
            RegistryException exception = RegistryException.of(WinError.ERROR_KEY_DELETED, "path", "name");
            assertInstanceOf(NoSuchRegistryKeyException.class, exception);
            assertEquals(WinError.ERROR_KEY_DELETED, exception.errorCode());
            assertEquals("path", exception.path());
        }

        @Test
        @DisplayName("ERROR_FILE_NOT_FOUND")
        void testFileNotFound() {
            RegistryException exception = RegistryException.of(WinError.ERROR_FILE_NOT_FOUND, "path", "name");
            NoSuchRegistryValueException valueException = assertInstanceOf(NoSuchRegistryValueException.class, exception);
            assertEquals(WinError.ERROR_FILE_NOT_FOUND, valueException.errorCode());
            assertEquals("path", valueException.path());
            assertEquals("name", valueException.name());
        }

        @Test
        @DisplayName("ERROR_ACCESS_DENIED")
        void testAccessDenied() {
            RegistryException exception = RegistryException.of(WinError.ERROR_ACCESS_DENIED, "path", "name");
            assertInstanceOf(RegistryAccessDeniedException.class, exception);
            assertEquals(WinError.ERROR_ACCESS_DENIED, exception.errorCode());
            assertEquals("path", exception.path());
        }

        @Test
        @DisplayName("ERROR_INVALID_HANDLE")
        void testInvalidHandle() {
            RegistryException exception = RegistryException.of(WinError.ERROR_INVALID_HANDLE, "path", "name");
            assertInstanceOf(InvalidRegistryHandleException.class, exception);
            assertEquals(WinError.ERROR_INVALID_HANDLE, exception.errorCode());
            assertEquals("path", exception.path());
        }

        @Test
        @DisplayName("other")
        void testOther() {
            RegistryException exception = RegistryException.of(WinError.ERROR_REGISTRY_CORRUPT, "path", "name");
            assertEquals(RegistryException.class, exception.getClass());
            assertEquals(WinError.ERROR_REGISTRY_CORRUPT, exception.errorCode());
            assertEquals("path", exception.path());
        }
    }
}
