/*
 * RegistryKeyTest.java
 * Copyright 2022 Rob Spoor
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import com.sun.jna.platform.win32.WinError;
import com.sun.jna.platform.win32.WinReg;

@SuppressWarnings("nls")
class RegistryKeyTest extends RegistryKeyTestBase {

    // Use RootKey for these tests

    @Nested
    @DisplayName("getStringValue")
    class GetStringValue {

        @Test
        @DisplayName("success")
        void testSuccess() {
            StringValue stringValue = StringValue.of("string", "value");

            mockValue(WinReg.HKEY_CURRENT_USER, stringValue);

            RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER;
            String value = registryKey.getStringValue("string");
            assertEquals(stringValue.value(), value);
        }

        @Test
        @DisplayName("non-existing value")
        void testNonExistingValue() {
            when(RegistryKey.api.RegQueryValueEx(eq(WinReg.HKEY_CURRENT_USER), any(), anyInt(), any(), (byte[]) isNull(), any()))
                    .thenReturn(WinError.ERROR_FILE_NOT_FOUND);

            RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER;
            NoSuchRegistryValueException exception = assertThrows(NoSuchRegistryValueException.class, () -> registryKey.getStringValue("string"));
            assertEquals("HKEY_CURRENT_USER", exception.path());
            assertEquals("string", exception.name());
        }

        @Test
        @DisplayName("failure")
        void testFailure() {
            mockValue(WinReg.HKEY_CURRENT_USER, StringValue.of("string", "value"), WinError.ERROR_INVALID_HANDLE);

            RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER;
            InvalidRegistryHandleException exception = assertThrows(InvalidRegistryHandleException.class, () -> registryKey.getStringValue("string"));
            assertEquals("HKEY_CURRENT_USER", exception.path());
        }

        @Test
        @DisplayName("wrong value type")
        void testWrongValueType() {
            DWordValue dwordValue = DWordValue.of("dword", 13);

            mockValue(WinReg.HKEY_CURRENT_USER, dwordValue);

            RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER;
            assertThrows(ClassCastException.class, () -> registryKey.getStringValue("dword"));
        }
    }

    @Nested
    @DisplayName("findStringValue")
    class FindStringValue {

        @Test
        @DisplayName("success")
        void testSuccess() {
            StringValue stringValue = StringValue.of("string", "value");

            mockValue(WinReg.HKEY_CURRENT_USER, stringValue);

            RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER;
            Optional<String> value = registryKey.findStringValue("string");
            assertEquals(Optional.of(stringValue.value()), value);
        }

        @Test
        @DisplayName("non-existing value")
        void testNonExistingValue() {
            when(RegistryKey.api.RegQueryValueEx(eq(WinReg.HKEY_CURRENT_USER), any(), anyInt(), any(), (byte[]) isNull(), any()))
                    .thenReturn(WinError.ERROR_FILE_NOT_FOUND);

            RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER;
            Optional<String> value = registryKey.findStringValue("string");
            assertEquals(Optional.empty(), value);
        }

        @Test
        @DisplayName("failure")
        void testFailure() {
            mockValue(WinReg.HKEY_CURRENT_USER, StringValue.of("string", "value"), WinError.ERROR_INVALID_HANDLE);

            RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER;
            InvalidRegistryHandleException exception = assertThrows(InvalidRegistryHandleException.class,
                    () -> registryKey.findStringValue("string"));
            assertEquals("HKEY_CURRENT_USER", exception.path());
        }

        @Test
        @DisplayName("wrong value type")
        void testWrongValueType() {
            DWordValue dwordValue = DWordValue.of("dword", 13);

            mockValue(WinReg.HKEY_CURRENT_USER, dwordValue);

            RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER;
            assertThrows(ClassCastException.class, () -> registryKey.findStringValue("dword"));
        }
    }

    @Nested
    @DisplayName("getDWordValue")
    class GetDWordValue {

        @Test
        @DisplayName("success")
        void testSuccess() {
            DWordValue dwordValue = DWordValue.of("dword", 13);

            mockValue(WinReg.HKEY_CURRENT_USER, dwordValue);

            RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER;
            int value = registryKey.getDWordValue("dword");
            assertEquals(dwordValue.value(), value);
        }

        @Test
        @DisplayName("non-existing value")
        void testNonExistingValue() {
            when(RegistryKey.api.RegQueryValueEx(eq(WinReg.HKEY_CURRENT_USER), any(), anyInt(), any(), (byte[]) isNull(), any()))
                    .thenReturn(WinError.ERROR_FILE_NOT_FOUND);

            RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER;
            NoSuchRegistryValueException exception = assertThrows(NoSuchRegistryValueException.class, () -> registryKey.getDWordValue("dword"));
            assertEquals("HKEY_CURRENT_USER", exception.path());
            assertEquals("dword", exception.name());
        }

        @Test
        @DisplayName("failure")
        void testFailure() {
            mockValue(WinReg.HKEY_CURRENT_USER, DWordValue.of("dword", 13), WinError.ERROR_INVALID_HANDLE);

            RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER;
            InvalidRegistryHandleException exception = assertThrows(InvalidRegistryHandleException.class, () -> registryKey.getDWordValue("dword"));
            assertEquals("HKEY_CURRENT_USER", exception.path());
        }

        @Test
        @DisplayName("wrong value type")
        void testWrongValueType() {
            StringValue stringValue = StringValue.of("string", "test");

            mockValue(WinReg.HKEY_CURRENT_USER, stringValue);

            RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER;
            assertThrows(ClassCastException.class, () -> registryKey.getDWordValue("string"));
        }
    }

    @Nested
    @DisplayName("findDWordValue")
    class FindDWordValue {

        @Test
        @DisplayName("success")
        void testSuccess() {
            DWordValue dwordValue = DWordValue.of("dword", 13);

            mockValue(WinReg.HKEY_CURRENT_USER, dwordValue);

            RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER;
            OptionalInt value = registryKey.findDWordValue("dword");
            assertEquals(OptionalInt.of(dwordValue.value()), value);
        }

        @Test
        @DisplayName("non-existing value")
        void testNonExistingValue() {
            when(RegistryKey.api.RegQueryValueEx(eq(WinReg.HKEY_CURRENT_USER), any(), anyInt(), any(), (byte[]) isNull(), any()))
                    .thenReturn(WinError.ERROR_FILE_NOT_FOUND);

            RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER;
            OptionalInt value = registryKey.findDWordValue("dword");
            assertEquals(OptionalInt.empty(), value);
        }

        @Test
        @DisplayName("failure")
        void testFailure() {
            mockValue(WinReg.HKEY_CURRENT_USER, DWordValue.of("dword", 13), WinError.ERROR_INVALID_HANDLE);

            RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER;
            InvalidRegistryHandleException exception = assertThrows(InvalidRegistryHandleException.class, () -> registryKey.findDWordValue("dword"));
            assertEquals("HKEY_CURRENT_USER", exception.path());
        }

        @Test
        @DisplayName("wrong value type")
        void testWrongValueType() {
            StringValue stringValue = StringValue.of("string", "test");

            mockValue(WinReg.HKEY_CURRENT_USER, stringValue);

            RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER;
            assertThrows(ClassCastException.class, () -> registryKey.findDWordValue("string"));
        }
    }

    @Nested
    @DisplayName("getQWordValue")
    class GetQWordValue {

        @Test
        @DisplayName("success")
        void testSuccess() {
            QWordValue qwordValue = QWordValue.of("qword", 13L);

            mockValue(WinReg.HKEY_CURRENT_USER, qwordValue);

            RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER;
            long value = registryKey.getQWordValue("qword");
            assertEquals(qwordValue.value(), value);
        }

        @Test
        @DisplayName("non-existing value")
        void testNonExistingValue() {
            when(RegistryKey.api.RegQueryValueEx(eq(WinReg.HKEY_CURRENT_USER), any(), anyInt(), any(), (byte[]) isNull(), any()))
                    .thenReturn(WinError.ERROR_FILE_NOT_FOUND);

            RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER;
            NoSuchRegistryValueException exception = assertThrows(NoSuchRegistryValueException.class, () -> registryKey.getQWordValue("qword"));
            assertEquals("HKEY_CURRENT_USER", exception.path());
            assertEquals("qword", exception.name());
        }

        @Test
        @DisplayName("failure")
        void testFailure() {
            mockValue(WinReg.HKEY_CURRENT_USER, QWordValue.of("qword", 13L), WinError.ERROR_INVALID_HANDLE);

            RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER;
            InvalidRegistryHandleException exception = assertThrows(InvalidRegistryHandleException.class, () -> registryKey.getQWordValue("qword"));
            assertEquals("HKEY_CURRENT_USER", exception.path());
        }

        @Test
        @DisplayName("wrong value type")
        void testWrongValueType() {
            StringValue stringValue = StringValue.of("string", "test");

            mockValue(WinReg.HKEY_CURRENT_USER, stringValue);

            RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER;
            assertThrows(ClassCastException.class, () -> registryKey.getQWordValue("string"));
        }
    }

    @Nested
    @DisplayName("findQWordValue")
    class FindQWordValue {

        @Test
        @DisplayName("success")
        void testSuccess() {
            QWordValue qwordValue = QWordValue.of("qword", 13L);

            mockValue(WinReg.HKEY_CURRENT_USER, qwordValue);

            RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER;
            OptionalLong value = registryKey.findQWordValue("qword");
            assertEquals(OptionalLong.of(qwordValue.value()), value);
        }

        @Test
        @DisplayName("non-existing value")
        void testNonExistingValue() {
            when(RegistryKey.api.RegQueryValueEx(eq(WinReg.HKEY_CURRENT_USER), any(), anyInt(), any(), (byte[]) isNull(), any()))
                    .thenReturn(WinError.ERROR_FILE_NOT_FOUND);

            RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER;
            OptionalLong value = registryKey.findQWordValue("qword");
            assertEquals(OptionalLong.empty(), value);
        }

        @Test
        @DisplayName("failure")
        void testFailure() {
            mockValue(WinReg.HKEY_CURRENT_USER, QWordValue.of("qword", 13L), WinError.ERROR_INVALID_HANDLE);

            RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER;
            InvalidRegistryHandleException exception = assertThrows(InvalidRegistryHandleException.class, () -> registryKey.findQWordValue("qword"));
            assertEquals("HKEY_CURRENT_USER", exception.path());
        }

        @Test
        @DisplayName("wrong value type")
        void testWrongValueType() {
            StringValue stringValue = StringValue.of("string", "test");

            mockValue(WinReg.HKEY_CURRENT_USER, stringValue);

            RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER;
            assertThrows(ClassCastException.class, () -> registryKey.findQWordValue("string"));
        }
    }

    @Nested
    @DisplayName("Handle")
    class Handle {

        @Nested
        @DisplayName("getStringValue")
        class GetStringValue {

            @Test
            @DisplayName("success")
            void testSuccess() {
                StringValue stringValue = StringValue.of("string", "value");

                mockValue(WinReg.HKEY_CURRENT_USER, stringValue);

                RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER;
                try (RegistryKey.Handle handle = registryKey.handle()) {
                    String value = handle.getStringValue("string");
                    assertEquals(stringValue.value(), value);
                }
            }

            @Test
            @DisplayName("non-existing value")
            void testNonExistingValue() {
                when(RegistryKey.api.RegQueryValueEx(eq(WinReg.HKEY_CURRENT_USER), any(), anyInt(), any(), (byte[]) isNull(), any()))
                        .thenReturn(WinError.ERROR_FILE_NOT_FOUND);

                RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER;
                try (RegistryKey.Handle handle = registryKey.handle()) {
                    NoSuchRegistryValueException exception = assertThrows(NoSuchRegistryValueException.class, () -> handle.getStringValue("string"));
                    assertEquals("HKEY_CURRENT_USER", exception.path());
                    assertEquals("string", exception.name());
                }
            }

            @Test
            @DisplayName("failure")
            void testFailure() {
                mockValue(WinReg.HKEY_CURRENT_USER, StringValue.of("string", "value"), WinError.ERROR_INVALID_HANDLE);

                RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER;
                try (RegistryKey.Handle handle = registryKey.handle()) {
                    InvalidRegistryHandleException exception = assertThrows(InvalidRegistryHandleException.class,
                            () -> handle.getStringValue("string"));
                    assertEquals("HKEY_CURRENT_USER", exception.path());
                }
            }

            @Test
            @DisplayName("wrong value type")
            void testWrongValueType() {
                DWordValue dwordValue = DWordValue.of("dword", 13);

                mockValue(WinReg.HKEY_CURRENT_USER, dwordValue);

                RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER;
                try (RegistryKey.Handle handle = registryKey.handle()) {
                    assertThrows(ClassCastException.class, () -> handle.getStringValue("dword"));
                }
            }
        }

        @Nested
        @DisplayName("findStringValue")
        class FindStringValue {

            @Test
            @DisplayName("success")
            void testSuccess() {
                StringValue stringValue = StringValue.of("string", "value");

                mockValue(WinReg.HKEY_CURRENT_USER, stringValue);

                RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER;
                try (RegistryKey.Handle handle = registryKey.handle()) {
                    Optional<String> value = handle.findStringValue("string");
                    assertEquals(Optional.of(stringValue.value()), value);
                }
            }

            @Test
            @DisplayName("non-existing value")
            void testNonExistingValue() {
                when(RegistryKey.api.RegQueryValueEx(eq(WinReg.HKEY_CURRENT_USER), any(), anyInt(), any(), (byte[]) isNull(), any()))
                        .thenReturn(WinError.ERROR_FILE_NOT_FOUND);

                RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER;
                try (RegistryKey.Handle handle = registryKey.handle()) {
                    Optional<String> value = handle.findStringValue("string");
                    assertEquals(Optional.empty(), value);
                }
            }

            @Test
            @DisplayName("failure")
            void testFailure() {
                mockValue(WinReg.HKEY_CURRENT_USER, StringValue.of("string", "value"), WinError.ERROR_INVALID_HANDLE);

                RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER;
                try (RegistryKey.Handle handle = registryKey.handle()) {
                    InvalidRegistryHandleException exception = assertThrows(InvalidRegistryHandleException.class,
                            () -> handle.findStringValue("string"));
                    assertEquals("HKEY_CURRENT_USER", exception.path());
                }
            }

            @Test
            @DisplayName("wrong value type")
            void testWrongValueType() {
                DWordValue dwordValue = DWordValue.of("dword", 13);

                mockValue(WinReg.HKEY_CURRENT_USER, dwordValue);

                RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER;
                try (RegistryKey.Handle handle = registryKey.handle()) {
                    assertThrows(ClassCastException.class, () -> handle.findStringValue("dword"));
                }
            }
        }

        @Nested
        @DisplayName("getDWordValue")
        class GetDWordValue {

            @Test
            @DisplayName("success")
            void testSuccess() {
                DWordValue dwordValue = DWordValue.of("dword", 13);

                mockValue(WinReg.HKEY_CURRENT_USER, dwordValue);

                RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER;
                try (RegistryKey.Handle handle = registryKey.handle()) {
                    int value = handle.getDWordValue("dword");
                    assertEquals(dwordValue.value(), value);
                }
            }

            @Test
            @DisplayName("non-existing value")
            void testNonExistingValue() {
                when(RegistryKey.api.RegQueryValueEx(eq(WinReg.HKEY_CURRENT_USER), any(), anyInt(), any(), (byte[]) isNull(), any()))
                        .thenReturn(WinError.ERROR_FILE_NOT_FOUND);

                RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER;
                try (RegistryKey.Handle handle = registryKey.handle()) {
                    NoSuchRegistryValueException exception = assertThrows(NoSuchRegistryValueException.class, () -> handle.getDWordValue("dword"));
                    assertEquals("HKEY_CURRENT_USER", exception.path());
                    assertEquals("dword", exception.name());
                }
            }

            @Test
            @DisplayName("failure")
            void testFailure() {
                mockValue(WinReg.HKEY_CURRENT_USER, DWordValue.of("dword", 13), WinError.ERROR_INVALID_HANDLE);

                RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER;
                try (RegistryKey.Handle handle = registryKey.handle()) {
                    InvalidRegistryHandleException exception = assertThrows(InvalidRegistryHandleException.class,
                            () -> handle.getDWordValue("dword"));
                    assertEquals("HKEY_CURRENT_USER", exception.path());
                }
            }

            @Test
            @DisplayName("wrong value type")
            void testWrongValueType() {
                StringValue stringValue = StringValue.of("string", "test");

                mockValue(WinReg.HKEY_CURRENT_USER, stringValue);

                RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER;
                try (RegistryKey.Handle handle = registryKey.handle()) {
                    assertThrows(ClassCastException.class, () -> handle.getDWordValue("string"));
                }
            }
        }

        @Nested
        @DisplayName("findDWordValue")
        class FindDWordValue {

            @Test
            @DisplayName("success")
            void testSuccess() {
                DWordValue dwordValue = DWordValue.of("dword", 13);

                mockValue(WinReg.HKEY_CURRENT_USER, dwordValue);

                RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER;
                try (RegistryKey.Handle handle = registryKey.handle()) {
                    OptionalInt value = handle.findDWordValue("dword");
                    assertEquals(OptionalInt.of(dwordValue.value()), value);
                }
            }

            @Test
            @DisplayName("non-existing value")
            void testNonExistingValue() {
                when(RegistryKey.api.RegQueryValueEx(eq(WinReg.HKEY_CURRENT_USER), any(), anyInt(), any(), (byte[]) isNull(), any()))
                        .thenReturn(WinError.ERROR_FILE_NOT_FOUND);

                RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER;
                try (RegistryKey.Handle handle = registryKey.handle()) {
                    OptionalInt value = handle.findDWordValue("dword");
                    assertEquals(OptionalInt.empty(), value);
                }
            }

            @Test
            @DisplayName("failure")
            void testFailure() {
                mockValue(WinReg.HKEY_CURRENT_USER, DWordValue.of("dword", 13), WinError.ERROR_INVALID_HANDLE);

                RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER;
                try (RegistryKey.Handle handle = registryKey.handle()) {
                    InvalidRegistryHandleException exception = assertThrows(InvalidRegistryHandleException.class,
                            () -> handle.findDWordValue("dword"));
                    assertEquals("HKEY_CURRENT_USER", exception.path());
                }
            }

            @Test
            @DisplayName("wrong value type")
            void testWrongValueType() {
                StringValue stringValue = StringValue.of("string", "test");

                mockValue(WinReg.HKEY_CURRENT_USER, stringValue);

                RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER;
                try (RegistryKey.Handle handle = registryKey.handle()) {
                    assertThrows(ClassCastException.class, () -> handle.findDWordValue("string"));
                }
            }
        }

        @Nested
        @DisplayName("getQWordValue")
        class GetQWordValue {

            @Test
            @DisplayName("success")
            void testSuccess() {
                QWordValue qwordValue = QWordValue.of("qword", 13L);

                mockValue(WinReg.HKEY_CURRENT_USER, qwordValue);

                RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER;
                try (RegistryKey.Handle handle = registryKey.handle()) {
                    long value = handle.getQWordValue("qword");
                    assertEquals(qwordValue.value(), value);
                }
            }

            @Test
            @DisplayName("non-existing value")
            void testNonExistingValue() {
                when(RegistryKey.api.RegQueryValueEx(eq(WinReg.HKEY_CURRENT_USER), any(), anyInt(), any(), (byte[]) isNull(), any()))
                        .thenReturn(WinError.ERROR_FILE_NOT_FOUND);

                RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER;
                try (RegistryKey.Handle handle = registryKey.handle()) {
                    NoSuchRegistryValueException exception = assertThrows(NoSuchRegistryValueException.class, () -> handle.getQWordValue("qword"));
                    assertEquals("HKEY_CURRENT_USER", exception.path());
                    assertEquals("qword", exception.name());
                }
            }

            @Test
            @DisplayName("failure")
            void testFailure() {
                mockValue(WinReg.HKEY_CURRENT_USER, QWordValue.of("qword", 13L), WinError.ERROR_INVALID_HANDLE);

                RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER;
                try (RegistryKey.Handle handle = registryKey.handle()) {
                    InvalidRegistryHandleException exception = assertThrows(InvalidRegistryHandleException.class,
                            () -> handle.getQWordValue("qword"));
                    assertEquals("HKEY_CURRENT_USER", exception.path());
                }
            }

            @Test
            @DisplayName("wrong value type")
            void testWrongValueType() {
                StringValue stringValue = StringValue.of("string", "test");

                mockValue(WinReg.HKEY_CURRENT_USER, stringValue);

                RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER;
                try (RegistryKey.Handle handle = registryKey.handle()) {
                    assertThrows(ClassCastException.class, () -> handle.getQWordValue("string"));
                }
            }
        }

        @Nested
        @DisplayName("findQWordValue")
        class FindQWordValue {

            @Test
            @DisplayName("success")
            void testSuccess() {
                QWordValue qwordValue = QWordValue.of("qword", 13L);

                mockValue(WinReg.HKEY_CURRENT_USER, qwordValue);

                RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER;
                try (RegistryKey.Handle handle = registryKey.handle()) {
                    OptionalLong value = handle.findQWordValue("qword");
                    assertEquals(OptionalLong.of(qwordValue.value()), value);
                }
            }

            @Test
            @DisplayName("non-existing value")
            void testNonExistingValue() {
                when(RegistryKey.api.RegQueryValueEx(eq(WinReg.HKEY_CURRENT_USER), any(), anyInt(), any(), (byte[]) isNull(), any()))
                        .thenReturn(WinError.ERROR_FILE_NOT_FOUND);

                RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER;
                try (RegistryKey.Handle handle = registryKey.handle()) {
                    OptionalLong value = handle.findQWordValue("qword");
                    assertEquals(OptionalLong.empty(), value);
                }
            }

            @Test
            @DisplayName("failure")
            void testFailure() {
                mockValue(WinReg.HKEY_CURRENT_USER, QWordValue.of("qword", 13L), WinError.ERROR_INVALID_HANDLE);

                RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER;
                try (RegistryKey.Handle handle = registryKey.handle()) {
                    InvalidRegistryHandleException exception = assertThrows(InvalidRegistryHandleException.class,
                            () -> handle.findQWordValue("qword"));
                    assertEquals("HKEY_CURRENT_USER", exception.path());
                }
            }

            @Test
            @DisplayName("wrong value type")
            void testWrongValueType() {
                StringValue stringValue = StringValue.of("string", "test");

                mockValue(WinReg.HKEY_CURRENT_USER, stringValue);

                RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER;
                try (RegistryKey.Handle handle = registryKey.handle()) {
                    assertThrows(ClassCastException.class, () -> handle.findQWordValue("string"));
                }
            }
        }
    }
}
