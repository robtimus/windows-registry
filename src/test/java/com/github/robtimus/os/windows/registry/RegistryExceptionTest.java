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

import static com.github.robtimus.os.windows.registry.WindowsConstants.ERROR_ACCESS_DENIED;
import static com.github.robtimus.os.windows.registry.WindowsConstants.ERROR_ALREADY_EXISTS;
import static com.github.robtimus.os.windows.registry.WindowsConstants.ERROR_FILE_NOT_FOUND;
import static com.github.robtimus.os.windows.registry.WindowsConstants.ERROR_INVALID_HANDLE;
import static com.github.robtimus.os.windows.registry.WindowsConstants.ERROR_KEY_DELETED;
import static com.sun.jna.platform.win32.WinError.ERROR_REGISTRY_CORRUPT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

@SuppressWarnings("nls")
class RegistryExceptionTest {

    private static final int OTHER_ERROR = ERROR_REGISTRY_CORRUPT;

    @Test
    @DisplayName("RegistryExistsException(int, String)")
    void testConstructorWithoutMachineName() {
        RegistryException exception = new RegistryException(ERROR_ALREADY_EXISTS, "path");

        assertEquals(ERROR_ALREADY_EXISTS, exception.errorCode());
        assertEquals("path", exception.path());
        assertNull(exception.machineName());
    }

    @Nested
    @DisplayName("forKey")
    class ForKey {

        @ParameterizedTest(name = "machineName = {0}")
        @DisplayName("ERROR_KEY_DELETED")
        @ValueSource(strings = "machine")
        @NullSource
        void testKeyDeleted(String machineName) {
            RegistryException exception = RegistryException.forKey(ERROR_KEY_DELETED, "path", machineName);
            assertInstanceOf(NoSuchRegistryKeyException.class, exception);
            assertEquals(ERROR_KEY_DELETED, exception.errorCode());
            assertEquals("path", exception.path());
            assertEquals(machineName, exception.machineName());
        }

        @ParameterizedTest(name = "machineName = {0}")
        @DisplayName("ERROR_FILE_NOT_FOUND")
        @ValueSource(strings = "machine")
        @NullSource
        void testFileNotFound(String machineName) {
            RegistryException exception = RegistryException.forKey(ERROR_FILE_NOT_FOUND, "path", machineName);
            assertInstanceOf(NoSuchRegistryKeyException.class, exception);
            assertEquals(ERROR_FILE_NOT_FOUND, exception.errorCode());
            assertEquals("path", exception.path());
            assertEquals(machineName, exception.machineName());
        }

        @ParameterizedTest(name = "machineName = {0}")
        @DisplayName("ERROR_ACCESS_DENIED")
        @ValueSource(strings = "machine")
        @NullSource
        void testAccessDenied(String machineName) {
            RegistryException exception = RegistryException.forKey(ERROR_ACCESS_DENIED, "path", machineName);
            assertInstanceOf(RegistryAccessDeniedException.class, exception);
            assertEquals(ERROR_ACCESS_DENIED, exception.errorCode());
            assertEquals("path", exception.path());
            assertEquals(machineName, exception.machineName());
        }

        @ParameterizedTest(name = "machineName = {0}")
        @DisplayName("ERROR_INVALID_HANDLE")
        @ValueSource(strings = "machine")
        @NullSource
        void testInvalidHandle(String machineName) {
            RegistryException exception = RegistryException.forKey(ERROR_INVALID_HANDLE, "path", machineName);
            assertInstanceOf(InvalidRegistryHandleException.class, exception);
            assertEquals(ERROR_INVALID_HANDLE, exception.errorCode());
            assertEquals("path", exception.path());
            assertEquals(machineName, exception.machineName());
        }

        @ParameterizedTest(name = "machineName = {0}")
        @DisplayName("other")
        @ValueSource(strings = "machine")
        @NullSource
        void testOther(String machineName) {
            RegistryException exception = RegistryException.forKey(OTHER_ERROR, "path", machineName);
            assertEquals(RegistryException.class, exception.getClass());
            assertEquals(OTHER_ERROR, exception.errorCode());
            assertEquals("path", exception.path());
            assertEquals(machineName, exception.machineName());
        }
    }

    @Nested
    @DisplayName("forValue")
    class ForValue {

        @ParameterizedTest(name = "machineName = {0}")
        @DisplayName("ERROR_KEY_DELETED")
        @ValueSource(strings = "machine")
        @NullSource
        void testKeyDeleted(String machineName) {
            RegistryException exception = RegistryException.forValue(ERROR_KEY_DELETED, "path", machineName, "name");
            assertInstanceOf(NoSuchRegistryKeyException.class, exception);
            assertEquals(ERROR_KEY_DELETED, exception.errorCode());
            assertEquals("path", exception.path());
            assertEquals(machineName, exception.machineName());
        }

        @ParameterizedTest(name = "machineName = {0}")
        @DisplayName("ERROR_FILE_NOT_FOUND")
        @ValueSource(strings = "machine")
        @NullSource
        void testFileNotFound(String machineName) {
            RegistryException exception = RegistryException.forValue(ERROR_FILE_NOT_FOUND, "path", machineName, "name");
            NoSuchRegistryValueException valueException = assertInstanceOf(NoSuchRegistryValueException.class, exception);
            assertEquals(ERROR_FILE_NOT_FOUND, valueException.errorCode());
            assertEquals("path", valueException.path());
            assertEquals(machineName, exception.machineName());
            assertEquals("name", valueException.name());
        }

        @ParameterizedTest(name = "machineName = {0}")
        @DisplayName("ERROR_ACCESS_DENIED")
        @ValueSource(strings = "machine")
        @NullSource
        void testAccessDenied(String machineName) {
            RegistryException exception = RegistryException.forValue(ERROR_ACCESS_DENIED, "path", machineName, "name");
            assertInstanceOf(RegistryAccessDeniedException.class, exception);
            assertEquals(ERROR_ACCESS_DENIED, exception.errorCode());
            assertEquals("path", exception.path());
            assertEquals(machineName, exception.machineName());
        }

        @ParameterizedTest(name = "machineName = {0}")
        @DisplayName("ERROR_INVALID_HANDLE")
        @ValueSource(strings = "machine")
        @NullSource
        void testInvalidHandle(String machineName) {
            RegistryException exception = RegistryException.forValue(ERROR_INVALID_HANDLE, "path", machineName, "name");
            assertInstanceOf(InvalidRegistryHandleException.class, exception);
            assertEquals(ERROR_INVALID_HANDLE, exception.errorCode());
            assertEquals("path", exception.path());
            assertEquals(machineName, exception.machineName());
        }

        @ParameterizedTest(name = "machineName = {0}")
        @DisplayName("other")
        @ValueSource(strings = "machine")
        @NullSource
        void testOther(String machineName) {
            RegistryException exception = RegistryException.forValue(OTHER_ERROR, "path", machineName, "name");
            assertEquals(RegistryException.class, exception.getClass());
            assertEquals(OTHER_ERROR, exception.errorCode());
            assertEquals("path", exception.path());
            assertEquals(machineName, exception.machineName());
        }
    }
}
