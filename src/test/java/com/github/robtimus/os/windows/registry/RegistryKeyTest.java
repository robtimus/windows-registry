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

import static com.github.robtimus.os.windows.registry.foreign.ForeignTestUtils.ALLOCATOR;
import static com.github.robtimus.os.windows.registry.foreign.ForeignTestUtils.eqPointer;
import static com.github.robtimus.os.windows.registry.foreign.ForeignTestUtils.isNULL;
import static com.github.robtimus.os.windows.registry.foreign.ForeignTestUtils.newHKEY;
import static com.github.robtimus.os.windows.registry.foreign.ForeignTestUtils.setHKEY;
import static com.github.robtimus.os.windows.registry.foreign.ForeignUtils.setInt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import java.lang.foreign.MemorySegment;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoField;
import java.util.GregorianCalendar;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import com.github.robtimus.os.windows.registry.foreign.Kernel32;
import com.github.robtimus.os.windows.registry.foreign.KtmTypes;
import com.github.robtimus.os.windows.registry.foreign.KtmW32;
import com.github.robtimus.os.windows.registry.foreign.WinDef.FILETIME;
import com.github.robtimus.os.windows.registry.foreign.WinError;
import com.github.robtimus.os.windows.registry.foreign.WinNT;
import com.github.robtimus.os.windows.registry.foreign.WinReg;
import com.sun.jna.platform.win32.WinBase.SYSTEMTIME;

@SuppressWarnings("nls")
class RegistryKeyTest extends RegistryKeyTestBase {

    // Use RootKey for these tests

    @Nested
    @DisplayName("lastWriteTime")
    class LastWriteTime {

        @Test
        @DisplayName("success")
        void testSuccess() {
            Instant instant = Instant.now().with(ChronoField.NANO_OF_SECOND, TimeUnit.MILLISECONDS.toNanos(100))
                    .atOffset(ZoneOffset.UTC)
                    .toInstant();

            when(RegistryKey.api.RegQueryInfoKey(notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(),
                    notNull(), notNull(), notNull()))
                    .thenAnswer(i -> {
                        MemorySegment fileTime = i.getArgument(11, MemorySegment.class);
                        copyInstantToFileTime(instant, fileTime);

                        return WinError.ERROR_SUCCESS;
                    });

            RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER;
            assertEquals(instant, registryKey.lastWriteTime());
        }

        @Test
        @DisplayName("negative file time")
        void testNegativeFileTime() {
            doAnswer(i -> {
                MemorySegment fileTime = i.getArgument(11, MemorySegment.class);
                FILETIME.dwHighDateTime(fileTime, -1);
                FILETIME.dwLowDateTime(fileTime, 0);

                return WinError.ERROR_SUCCESS;
            }).when(RegistryKey.api).RegQueryInfoKey(notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(),
                    notNull(), notNull(), notNull(), notNull());

            RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER;
            assertEquals(RegistryKey.FILETIME_BASE, registryKey.lastWriteTime());
        }

        @Test
        @DisplayName("failure")
        void testFailure() {
            doReturn(WinError.ERROR_INVALID_HANDLE).when(RegistryKey.api)
                    .RegQueryInfoKey(notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(),
                            notNull(), notNull());

            RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER;
            InvalidRegistryHandleException exception = assertThrows(InvalidRegistryHandleException.class, registryKey::lastWriteTime);
            assertEquals("HKEY_CURRENT_USER", exception.path());
        }
    }

    @Nested
    @DisplayName("attributes")
    class Attributes {

        @Test
        @DisplayName("success")
        void testSuccess() {
            Instant instant = Instant.now().with(ChronoField.NANO_OF_SECOND, TimeUnit.MILLISECONDS.toNanos(100));

            when(RegistryKey.api.RegQueryInfoKey(notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(),
                    notNull(), notNull(), notNull()))
                    .thenAnswer(i -> {
                        setInt(i.getArgument(4, MemorySegment.class), 10);
                        setInt(i.getArgument(7, MemorySegment.class), 20);

                        MemorySegment fileTime = i.getArgument(11, MemorySegment.class);
                        copyInstantToFileTime(instant, fileTime);

                        return WinError.ERROR_SUCCESS;
                    });

            RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER;
            RegistryKey.Attributes attributes = registryKey.attributes();

            assertEquals(10, attributes.subKeyCount());
            assertEquals(20, attributes.valueCount());
            assertEquals(instant, attributes.lastWriteTime());
        }

        @Test
        @DisplayName("failure")
        void testFailure() {
            doReturn(WinError.ERROR_INVALID_HANDLE).when(RegistryKey.api)
                    .RegQueryInfoKey(notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(),
                            notNull(), notNull());

            RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER;
            InvalidRegistryHandleException exception = assertThrows(InvalidRegistryHandleException.class, registryKey::attributes);
            assertEquals("HKEY_CURRENT_USER", exception.path());
        }
    }

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
            doReturn(WinError.ERROR_FILE_NOT_FOUND).when(RegistryKey.api)
                    .RegQueryValueEx(eq(WinReg.HKEY_CURRENT_USER), notNull(), notNull(), notNull(), isNULL(), notNull());

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
            doReturn(WinError.ERROR_FILE_NOT_FOUND).when(RegistryKey.api)
                    .RegQueryValueEx(eq(WinReg.HKEY_CURRENT_USER), notNull(), notNull(), notNull(), isNULL(), notNull());

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
            doReturn(WinError.ERROR_FILE_NOT_FOUND).when(RegistryKey.api)
                    .RegQueryValueEx(eq(WinReg.HKEY_CURRENT_USER), notNull(), notNull(), notNull(), isNULL(), notNull());

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
            doReturn(WinError.ERROR_FILE_NOT_FOUND).when(RegistryKey.api)
                    .RegQueryValueEx(eq(WinReg.HKEY_CURRENT_USER), notNull(), notNull(), notNull(), isNULL(), notNull());

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
            doReturn(WinError.ERROR_FILE_NOT_FOUND).when(RegistryKey.api)
                    .RegQueryValueEx(eq(WinReg.HKEY_CURRENT_USER), notNull(), notNull(), notNull(), isNULL(), notNull());

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
            doReturn(WinError.ERROR_FILE_NOT_FOUND).when(RegistryKey.api)
                    .RegQueryValueEx(eq(WinReg.HKEY_CURRENT_USER), notNull(), notNull(), notNull(), isNULL(), notNull());

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

        // Use RootKey for these tests

        @Nested
        @DisplayName("lastWriteTime")
        class LastWriteTime {

            @Test
            @DisplayName("success")
            void testSuccess() {
                Instant instant = Instant.now().with(ChronoField.NANO_OF_SECOND, TimeUnit.MILLISECONDS.toNanos(100));

                when(RegistryKey.api.RegQueryInfoKey(notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(),
                        notNull(), notNull(), notNull(), notNull()))
                        .thenAnswer(i -> {
                            MemorySegment fileTime = i.getArgument(11, MemorySegment.class);
                            copyInstantToFileTime(instant, fileTime);

                            return WinError.ERROR_SUCCESS;
                        });

                RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER;
                try (RegistryKey.Handle handle = registryKey.handle()) {
                    assertEquals(instant, handle.lastWriteTime());
                }
            }

            @Test
            @DisplayName("negative file time")
            void testNegativeFileTime() {
                doAnswer(i -> {
                    MemorySegment fileTime = i.getArgument(11, MemorySegment.class);
                    FILETIME.dwHighDateTime(fileTime, -1);
                    FILETIME.dwLowDateTime(fileTime, 0);

                    return WinError.ERROR_SUCCESS;
                }).when(RegistryKey.api).RegQueryInfoKey(notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(),
                        notNull(), notNull(), notNull(), notNull());

                RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER;
                try (var _ = registryKey.handle()) {
                    assertEquals(RegistryKey.FILETIME_BASE, registryKey.lastWriteTime());
                }
            }

            @Test
            @DisplayName("failure")
            void testFailure() {
                doReturn(WinError.ERROR_INVALID_HANDLE).when(RegistryKey.api)
                        .RegQueryInfoKey(notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(),
                                notNull(), notNull());

                RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER;
                try (RegistryKey.Handle handle = registryKey.handle()) {
                    InvalidRegistryHandleException exception = assertThrows(InvalidRegistryHandleException.class, handle::lastWriteTime);
                    assertEquals("HKEY_CURRENT_USER", exception.path());
                }
            }
        }

        @Nested
        @DisplayName("attributes")
        class Attributes {

            @Test
            @DisplayName("success")
            void testSuccess() {
                Instant instant = Instant.now().with(ChronoField.NANO_OF_SECOND, TimeUnit.MILLISECONDS.toNanos(100));

                when(RegistryKey.api.RegQueryInfoKey(notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(),
                        notNull(), notNull(), notNull(), notNull()))
                        .thenAnswer(i -> {
                            setInt(i.getArgument(4, MemorySegment.class), 10);
                            setInt(i.getArgument(7, MemorySegment.class), 20);

                            MemorySegment fileTime = i.getArgument(11, MemorySegment.class);
                            copyInstantToFileTime(instant, fileTime);

                            return WinError.ERROR_SUCCESS;
                        });

                RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER;
                try (RegistryKey.Handle handle = registryKey.handle()) {
                    RegistryKey.Attributes attributes = handle.attributes();

                    assertEquals(10, attributes.subKeyCount());
                    assertEquals(20, attributes.valueCount());
                    assertEquals(instant, attributes.lastWriteTime());
                }
            }

            @Test
            @DisplayName("failure")
            void testFailure() {
                doReturn(WinError.ERROR_INVALID_HANDLE).when(RegistryKey.api)
                        .RegQueryInfoKey(notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(),
                                notNull(), notNull());

                RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER;
                try (RegistryKey.Handle handle = registryKey.handle()) {
                    InvalidRegistryHandleException exception = assertThrows(InvalidRegistryHandleException.class, handle::attributes);
                    assertEquals("HKEY_CURRENT_USER", exception.path());
                }
            }
        }

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
                doReturn(WinError.ERROR_FILE_NOT_FOUND).when(RegistryKey.api)
                        .RegQueryValueEx(eq(WinReg.HKEY_CURRENT_USER), notNull(), notNull(), notNull(), isNULL(), notNull());

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
                doReturn(WinError.ERROR_FILE_NOT_FOUND).when(RegistryKey.api)
                        .RegQueryValueEx(eq(WinReg.HKEY_CURRENT_USER), notNull(), notNull(), notNull(), isNULL(), notNull());

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
                doReturn(WinError.ERROR_FILE_NOT_FOUND).when(RegistryKey.api)
                        .RegQueryValueEx(eq(WinReg.HKEY_CURRENT_USER), notNull(), notNull(), notNull(), isNULL(), notNull());

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
                doReturn(WinError.ERROR_FILE_NOT_FOUND).when(RegistryKey.api)
                        .RegQueryValueEx(eq(WinReg.HKEY_CURRENT_USER), notNull(), notNull(), notNull(), isNULL(), notNull());

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
                doReturn(WinError.ERROR_FILE_NOT_FOUND).when(RegistryKey.api)
                        .RegQueryValueEx(eq(WinReg.HKEY_CURRENT_USER), notNull(), notNull(), notNull(), isNULL(), notNull());

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
                doReturn(WinError.ERROR_FILE_NOT_FOUND).when(RegistryKey.api)
                        .RegQueryValueEx(eq(WinReg.HKEY_CURRENT_USER), notNull(), notNull(), notNull(), isNULL(), notNull());

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

    @Nested
    @DisplayName("transactional")
    class Transactional {

        @BeforeEach
        void setup() {
            Transaction.ktmW32 = mock(KtmW32.class);
            Transaction.kernel32 = mock(Kernel32.class);
        }

        @AfterEach
        void teardown() {
            Transaction.ktmW32 = KtmW32.INSTANCE;
            Transaction.kernel32 = Kernel32.INSTANCE;
        }

        @Test
        @DisplayName("default non-transactional")
        void testDefaultNonTransactional() {
            String path = "path\\non-transactional";

            MemorySegment hKey = newHKEY();

            when(RegistryKey.api.RegCreateKeyEx(eq(WinReg.HKEY_CURRENT_USER), eqPointer(path), eq(0), notNull(),
                    eq(WinNT.REG_OPTION_NON_VOLATILE), eq(WinNT.KEY_READ), isNULL(), notNull(), notNull()))
                    .thenAnswer(i -> {
                        setHKEY(i.getArgument(7, MemorySegment.class), hKey);
                        setInt(i.getArgument(8, MemorySegment.class), WinNT.REG_CREATED_NEW_KEY);

                        return WinError.ERROR_SUCCESS;
                    });
            when(RegistryKey.api.RegOpenKeyEx(eq(WinReg.HKEY_CURRENT_USER), eqPointer(path), eq(0), eq(WinNT.KEY_READ), notNull()))
                    .thenAnswer(i -> {
                        setHKEY(i.getArgument(4, MemorySegment.class), hKey);

                        return WinError.ERROR_SUCCESS;
                    });
            when(RegistryKey.api.RegDeleteKey(eq(WinReg.HKEY_CURRENT_USER), eqPointer(path))).thenReturn(WinError.ERROR_SUCCESS);
            when(RegistryKey.api.RegCloseKey(hKey)).thenReturn(WinError.ERROR_SUCCESS);

            RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER.resolve(path);

            registryKey.create();
            assertTrue(registryKey.exists());
            registryKey.delete();

            verify(RegistryKey.api).RegCreateKeyEx(eq(WinReg.HKEY_CURRENT_USER), eqPointer(path), eq(0), isNULL(),
                    eq(WinNT.REG_OPTION_NON_VOLATILE), eq(WinNT.KEY_READ), isNULL(), notNull(), notNull());
            verify(RegistryKey.api).RegOpenKeyEx(eq(WinReg.HKEY_CURRENT_USER), eqPointer(path), eq(0), eq(WinNT.KEY_READ), notNull());
            verify(RegistryKey.api).RegDeleteKey(eq(WinReg.HKEY_CURRENT_USER), eqPointer(path));
            // Closed as part of create and exist calls
            verify(RegistryKey.api, times(2)).RegCloseKey(hKey);
            verifyNoMoreInteractions(RegistryKey.api);
            verifyNoInteractions(Transaction.ktmW32);
            verifyNoInteractions(Transaction.kernel32);
        }

        @Test
        @DisplayName("non-transactional")
        void testNonTransactional() {
            String path = "path\\non-transactional";

            MemorySegment hKey = newHKEY();

            when(RegistryKey.api.RegCreateKeyEx(eq(WinReg.HKEY_CURRENT_USER), eqPointer(path), eq(0), notNull(),
                    eq(WinNT.REG_OPTION_NON_VOLATILE), eq(WinNT.KEY_READ), isNULL(), notNull(), notNull()))
                    .thenAnswer(i -> {
                        setHKEY(i.getArgument(7, MemorySegment.class), hKey);
                        setInt(i.getArgument(8, MemorySegment.class), WinNT.REG_CREATED_NEW_KEY);

                        return WinError.ERROR_SUCCESS;
                    });
            when(RegistryKey.api.RegOpenKeyEx(eq(WinReg.HKEY_CURRENT_USER), eqPointer(path), eq(0), eq(WinNT.KEY_READ), notNull()))
                    .thenAnswer(i -> {
                        setHKEY(i.getArgument(4, MemorySegment.class), hKey);

                        return WinError.ERROR_SUCCESS;
                    });
            when(RegistryKey.api.RegDeleteKey(eq(WinReg.HKEY_CURRENT_USER), eqPointer(path))).thenReturn(WinError.ERROR_SUCCESS);
            when(RegistryKey.api.RegCloseKey(hKey)).thenReturn(WinError.ERROR_SUCCESS);

            RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER.resolve(path);

            RegistryKey.nonTransactional().run(() -> {
                registryKey.create();
                assertTrue(registryKey.exists());
                registryKey.delete();
            });

            verify(RegistryKey.api).RegCreateKeyEx(eq(WinReg.HKEY_CURRENT_USER), eqPointer(path), eq(0), isNULL(),
                    eq(WinNT.REG_OPTION_NON_VOLATILE), eq(WinNT.KEY_READ), isNULL(), notNull(), notNull());
            verify(RegistryKey.api).RegOpenKeyEx(eq(WinReg.HKEY_CURRENT_USER), eqPointer(path), eq(0), eq(WinNT.KEY_READ), notNull());
            verify(RegistryKey.api).RegDeleteKey(eq(WinReg.HKEY_CURRENT_USER), eqPointer(path));
            // Closed as part of create and exist calls
            verify(RegistryKey.api, times(2)).RegCloseKey(hKey);
            verifyNoMoreInteractions(RegistryKey.api);
            verifyNoInteractions(Transaction.ktmW32);
            verifyNoInteractions(Transaction.kernel32);
        }

        @Test
        @DisplayName("transactional")
        void testTransactional() {
            String path = "path\\transactional";

            MemorySegment hKey = newHKEY();
            MemorySegment transaction = ALLOCATOR.allocate(0);

            when(Transaction.ktmW32.CreateTransaction(isNULL(), isNULL(), anyInt(), anyInt(), anyInt(), eq(0), notNull(), notNull()))
                    .thenReturn(transaction);
            when(Transaction.kernel32.CloseHandle(eq(transaction), notNull())).thenReturn(true);

            when(RegistryKey.api.RegCreateKeyTransacted(eq(WinReg.HKEY_CURRENT_USER), eqPointer(path), eq(0), isNULL(),
                    eq(WinNT.REG_OPTION_NON_VOLATILE), eq(WinNT.KEY_READ), isNULL(), notNull(), notNull(), eq(transaction), isNULL()))
                    .thenAnswer(i -> {
                        setHKEY(i.getArgument(7, MemorySegment.class), hKey);
                        setInt(i.getArgument(8, MemorySegment.class), WinNT.REG_CREATED_NEW_KEY);

                        return WinError.ERROR_SUCCESS;
                    });
            when(RegistryKey.api.RegOpenKeyTransacted(eq(WinReg.HKEY_CURRENT_USER), eqPointer(path), eq(0), eq(WinNT.KEY_READ), notNull(),
                    eq(transaction), isNULL()))
                    .thenAnswer(i -> {
                        setHKEY(i.getArgument(4, MemorySegment.class), hKey);

                        return WinError.ERROR_SUCCESS;
                    });
            when(RegistryKey.api.RegDeleteKeyTransacted(eq(WinReg.HKEY_CURRENT_USER), eqPointer(path), eq(0), eq(0), eq(transaction), isNULL()))
                    .thenReturn(WinError.ERROR_SUCCESS);
            when(RegistryKey.api.RegCloseKey(hKey)).thenReturn(WinError.ERROR_SUCCESS);

            RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER.resolve(path);

            RegistryKey.transactional().run(_ -> {
                registryKey.create();
                assertTrue(registryKey.exists());
                registryKey.delete();
            });

            verify(Transaction.ktmW32).CreateTransaction(isNULL(), isNULL(), eq(KtmTypes.TRANSACTION_DO_NOT_PROMOTE), eq(0), eq(0), eq(0),
                    notNull(), notNull());
            verify(Transaction.kernel32).CloseHandle(eq(transaction), notNull());

            verify(RegistryKey.api).RegCreateKeyTransacted(eq(WinReg.HKEY_CURRENT_USER), eqPointer(path), eq(0), notNull(),
                    eq(WinNT.REG_OPTION_NON_VOLATILE), eq(WinNT.KEY_READ), isNULL(), notNull(), notNull(), eq(transaction), isNULL());
            verify(RegistryKey.api).RegOpenKeyTransacted(eq(WinReg.HKEY_CURRENT_USER), eqPointer(path), eq(0), eq(WinNT.KEY_READ), notNull(),
                    eq(transaction), isNULL());
            verify(RegistryKey.api).RegDeleteKeyTransacted(eq(WinReg.HKEY_CURRENT_USER), eqPointer(path), eq(0), eq(0), eq(transaction), isNULL());
            // Closed as part of create and exist calls
            verify(RegistryKey.api, times(2)).RegCloseKey(hKey);
            verifyNoMoreInteractions(RegistryKey.api);
            verifyNoMoreInteractions(Transaction.ktmW32);
            verifyNoMoreInteractions(Transaction.kernel32);
        }

        @Test
        @DisplayName("non-transactional in transactional")
        void testNonTransactionalInTransactional() {
            String path = "path\\transactional";

            MemorySegment hKey = newHKEY();
            MemorySegment transaction = ALLOCATOR.allocate(0);

            when(Transaction.ktmW32.CreateTransaction(isNULL(), isNULL(), anyInt(), anyInt(), anyInt(), eq(0), notNull(), notNull()))
                    .thenReturn(transaction);
            when(Transaction.kernel32.CloseHandle(eq(transaction), notNull())).thenReturn(true);

            when(RegistryKey.api.RegCreateKeyEx(eq(WinReg.HKEY_CURRENT_USER), eqPointer(path), eq(0), isNULL(),
                    eq(WinNT.REG_OPTION_NON_VOLATILE), eq(WinNT.KEY_READ), isNULL(), notNull(), notNull()))
                    .thenAnswer(i -> {
                        setHKEY(i.getArgument(7, MemorySegment.class), hKey);
                        setInt(i.getArgument(8, MemorySegment.class), WinNT.REG_CREATED_NEW_KEY);

                        return WinError.ERROR_SUCCESS;
                    });
            when(RegistryKey.api.RegOpenKeyEx(eq(WinReg.HKEY_CURRENT_USER), eqPointer(path), eq(0), eq(WinNT.KEY_READ), notNull()))
                    .thenAnswer(i -> {
                        setHKEY(i.getArgument(4, MemorySegment.class), hKey);

                        return WinError.ERROR_SUCCESS;
                    });
            when(RegistryKey.api.RegDeleteKey(eq(WinReg.HKEY_CURRENT_USER), eqPointer(path))).thenReturn(WinError.ERROR_SUCCESS);
            when(RegistryKey.api.RegCloseKey(hKey)).thenReturn(WinError.ERROR_SUCCESS);

            RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER.resolve(path);

            String expectedResult = UUID.randomUUID().toString();
            String result = RegistryKey.transactional().call(_ -> RegistryKey.nonTransactional().call(() -> {
                registryKey.create();
                assertTrue(registryKey.exists());
                registryKey.delete();

                return expectedResult;
            }));
            assertEquals(expectedResult, result);

            verify(Transaction.ktmW32).CreateTransaction(isNULL(), isNULL(), eq(KtmTypes.TRANSACTION_DO_NOT_PROMOTE), eq(0), eq(0), eq(0),
                    notNull(), notNull());
            verify(Transaction.kernel32).CloseHandle(eq(transaction), notNull());

            verify(RegistryKey.api).RegCreateKeyEx(eq(WinReg.HKEY_CURRENT_USER), eqPointer(path), eq(0), notNull(),
                    eq(WinNT.REG_OPTION_NON_VOLATILE), eq(WinNT.KEY_READ), isNULL(), notNull(), notNull());
            verify(RegistryKey.api).RegOpenKeyEx(eq(WinReg.HKEY_CURRENT_USER), eqPointer(path), eq(0), eq(WinNT.KEY_READ), notNull());
            verify(RegistryKey.api).RegDeleteKey(eq(WinReg.HKEY_CURRENT_USER), eqPointer(path));
            // Closed as part of create and exist calls
            verify(RegistryKey.api, times(2)).RegCloseKey(hKey);
            verifyNoMoreInteractions(RegistryKey.api);
            verifyNoMoreInteractions(Transaction.ktmW32);
            verifyNoMoreInteractions(Transaction.kernel32);
        }

        @Test
        @DisplayName("nestedd transactional")
        void testNestedTransactional() {
            String path = "path\\transactional";

            MemorySegment hKey = newHKEY();
            MemorySegment transaction1 = ALLOCATOR.allocate(0);
            MemorySegment transaction2 = ALLOCATOR.allocate(0);

            when(Transaction.ktmW32.CreateTransaction(isNULL(), isNULL(), anyInt(), anyInt(), anyInt(), eq(0), notNull(), notNull()))
                    .thenReturn(transaction1, transaction2);
            when(Transaction.kernel32.CloseHandle(eq(transaction1), notNull())).thenReturn(true);
            when(Transaction.kernel32.CloseHandle(eq(transaction2), notNull())).thenReturn(true);

            when(RegistryKey.api.RegCreateKeyTransacted(eq(WinReg.HKEY_CURRENT_USER), eqPointer(path), eq(0), isNULL(),
                    eq(WinNT.REG_OPTION_NON_VOLATILE), eq(WinNT.KEY_READ), isNULL(), notNull(), notNull(), eq(transaction2), isNULL()))
                    .thenAnswer(i -> {
                        setHKEY(i.getArgument(7, MemorySegment.class), hKey);
                        setInt(i.getArgument(8, MemorySegment.class), WinNT.REG_CREATED_NEW_KEY);

                        return WinError.ERROR_SUCCESS;
                    });
            when(RegistryKey.api.RegOpenKeyTransacted(eq(WinReg.HKEY_CURRENT_USER), eqPointer(path), eq(0), eq(WinNT.KEY_READ), notNull(),
                    eq(transaction2), isNULL()))
                    .thenAnswer(i -> {
                        setHKEY(i.getArgument(4, MemorySegment.class), hKey);

                        return WinError.ERROR_SUCCESS;
                    });
            when(RegistryKey.api.RegDeleteKeyTransacted(eq(WinReg.HKEY_CURRENT_USER), eqPointer(path), eq(0), eq(0), eq(transaction2), isNULL()))
                    .thenReturn(WinError.ERROR_SUCCESS);
            when(RegistryKey.api.RegCloseKey(hKey)).thenReturn(WinError.ERROR_SUCCESS);

            RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER.resolve(path);

            String expectedResult = UUID.randomUUID().toString();
            String result = RegistryKey.transactional().call(_ -> RegistryKey.transactional().call(_ -> {
                registryKey.create();
                assertTrue(registryKey.exists());
                registryKey.delete();

                return expectedResult;
            }));
            assertEquals(expectedResult, result);

            verify(Transaction.ktmW32, times(2)).CreateTransaction(isNULL(), isNULL(), eq(KtmTypes.TRANSACTION_DO_NOT_PROMOTE), eq(0), eq(0), eq(0),
                    notNull(), notNull());
            verify(Transaction.kernel32).CloseHandle(eq(transaction1), notNull());
            verify(Transaction.kernel32).CloseHandle(eq(transaction2), notNull());

            verify(RegistryKey.api).RegCreateKeyTransacted(eq(WinReg.HKEY_CURRENT_USER), eqPointer(path), eq(0), notNull(),
                    eq(WinNT.REG_OPTION_NON_VOLATILE), eq(WinNT.KEY_READ), isNULL(), notNull(), notNull(), eq(transaction2), isNULL());
            verify(RegistryKey.api).RegOpenKeyTransacted(eq(WinReg.HKEY_CURRENT_USER), eqPointer(path), eq(0), eq(WinNT.KEY_READ), notNull(),
                    eq(transaction2), isNULL());
            verify(RegistryKey.api).RegDeleteKeyTransacted(eq(WinReg.HKEY_CURRENT_USER), eqPointer(path), eq(0), eq(0), eq(transaction2), isNULL());
            // Closed as part of create and exist calls
            verify(RegistryKey.api, times(2)).RegCloseKey(hKey);
            verifyNoMoreInteractions(RegistryKey.api);
            verifyNoMoreInteractions(Transaction.ktmW32);
            verifyNoMoreInteractions(Transaction.kernel32);
        }

        @Test
        @DisplayName("transactional with custom timeout")
        void testTransactionalWithCustomTimeout() {
            String path = "path\\transactional";

            MemorySegment hKey = newHKEY();
            MemorySegment transaction = ALLOCATOR.allocate(0);

            when(Transaction.ktmW32.CreateTransaction(isNULL(), isNULL(), anyInt(), anyInt(), anyInt(), eq(100), notNull(), notNull()))
                    .thenReturn(transaction);
            when(Transaction.kernel32.CloseHandle(eq(transaction), notNull())).thenReturn(true);

            when(RegistryKey.api.RegCreateKeyTransacted(eq(WinReg.HKEY_CURRENT_USER), eqPointer(path), eq(0), isNULL(),
                    eq(WinNT.REG_OPTION_NON_VOLATILE), eq(WinNT.KEY_READ), isNULL(), notNull(), notNull(), eq(transaction), isNULL()))
                    .thenAnswer(i -> {
                        setHKEY(i.getArgument(7, MemorySegment.class), hKey);
                        setInt(i.getArgument(8, MemorySegment.class), WinNT.REG_CREATED_NEW_KEY);

                        return WinError.ERROR_SUCCESS;
                    });
            when(RegistryKey.api.RegOpenKeyTransacted(eq(WinReg.HKEY_CURRENT_USER), eqPointer(path), eq(0), eq(WinNT.KEY_READ), notNull(),
                    eq(transaction), isNULL()))
                    .thenAnswer(i -> {
                        setHKEY(i.getArgument(4, MemorySegment.class), hKey);

                        return WinError.ERROR_SUCCESS;
                    });
            when(RegistryKey.api.RegDeleteKeyTransacted(eq(WinReg.HKEY_CURRENT_USER), eqPointer(path), eq(0), eq(0), eq(transaction), isNULL()))
                    .thenReturn(WinError.ERROR_SUCCESS);
            when(RegistryKey.api.RegCloseKey(hKey)).thenReturn(WinError.ERROR_SUCCESS);

            RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER.resolve(path);

            RegistryKey.transactional()
                    .withTimeout(Duration.ofMillis(100))
                    .run(_ -> {
                        registryKey.create();
                        assertTrue(registryKey.exists());
                        registryKey.delete();
                    });

            verify(Transaction.ktmW32).CreateTransaction(isNULL(), isNULL(), eq(KtmTypes.TRANSACTION_DO_NOT_PROMOTE), eq(0), eq(0), eq(100),
                    notNull(), notNull());
            verify(Transaction.kernel32).CloseHandle(eq(transaction), notNull());

            verify(RegistryKey.api).RegCreateKeyTransacted(eq(WinReg.HKEY_CURRENT_USER), eqPointer(path), eq(0), notNull(),
                    eq(WinNT.REG_OPTION_NON_VOLATILE), eq(WinNT.KEY_READ), isNULL(), notNull(), notNull(), eq(transaction), isNULL());
            verify(RegistryKey.api).RegOpenKeyTransacted(eq(WinReg.HKEY_CURRENT_USER), eqPointer(path), eq(0), eq(WinNT.KEY_READ), notNull(),
                    eq(transaction), isNULL());
            verify(RegistryKey.api).RegDeleteKeyTransacted(eq(WinReg.HKEY_CURRENT_USER), eqPointer(path), eq(0), eq(0), eq(transaction), isNULL());
            // Closed as part of create and exist calls
            verify(RegistryKey.api, times(2)).RegCloseKey(hKey);
            verifyNoMoreInteractions(RegistryKey.api);
            verifyNoMoreInteractions(Transaction.ktmW32);
            verifyNoMoreInteractions(Transaction.kernel32);
        }

        @Test
        @DisplayName("transactional with infinite timeout")
        void testTransactionalWithInfiniteTimeout() {
            String path = "path\\transactional";

            MemorySegment hKey = newHKEY();
            MemorySegment transaction = ALLOCATOR.allocate(0);

            when(Transaction.ktmW32.CreateTransaction(isNULL(), isNULL(), anyInt(), anyInt(), anyInt(), eq(0), notNull(), notNull()))
                    .thenReturn(transaction);
            when(Transaction.kernel32.CloseHandle(eq(transaction), notNull())).thenReturn(true);

            when(RegistryKey.api.RegCreateKeyTransacted(eq(WinReg.HKEY_CURRENT_USER), eqPointer(path), eq(0), isNULL(),
                    eq(WinNT.REG_OPTION_NON_VOLATILE), eq(WinNT.KEY_READ), isNULL(), notNull(), notNull(), eq(transaction), isNULL()))
                    .thenAnswer(i -> {
                        setHKEY(i.getArgument(7, MemorySegment.class), hKey);
                        setInt(i.getArgument(8, MemorySegment.class), WinNT.REG_CREATED_NEW_KEY);

                        return WinError.ERROR_SUCCESS;
                    });
            when(RegistryKey.api.RegOpenKeyTransacted(eq(WinReg.HKEY_CURRENT_USER), eqPointer(path), eq(0), eq(WinNT.KEY_READ), notNull(),
                    eq(transaction), isNULL()))
                    .thenAnswer(i -> {
                        setHKEY(i.getArgument(4, MemorySegment.class), hKey);

                        return WinError.ERROR_SUCCESS;
                    });
            when(RegistryKey.api.RegDeleteKeyTransacted(eq(WinReg.HKEY_CURRENT_USER), eqPointer(path), eq(0), eq(0), eq(transaction), isNULL()))
                    .thenReturn(WinError.ERROR_SUCCESS);
            when(RegistryKey.api.RegCloseKey(hKey)).thenReturn(WinError.ERROR_SUCCESS);

            RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER.resolve(path);

            RegistryKey.transactional()
                    .withInfiniteTimeout()
                    .run(_ -> {
                        registryKey.create();
                        assertTrue(registryKey.exists());
                        registryKey.delete();
                    });

            verify(Transaction.ktmW32).CreateTransaction(isNULL(), isNULL(), eq(KtmTypes.TRANSACTION_DO_NOT_PROMOTE), eq(0), eq(0), eq(0),
                    notNull(), notNull());
            verify(Transaction.kernel32).CloseHandle(eq(transaction), notNull());

            verify(RegistryKey.api).RegCreateKeyTransacted(eq(WinReg.HKEY_CURRENT_USER), eqPointer(path), eq(0), notNull(),
                    eq(WinNT.REG_OPTION_NON_VOLATILE), eq(WinNT.KEY_READ), isNULL(), notNull(), notNull(), eq(transaction), isNULL());
            verify(RegistryKey.api).RegOpenKeyTransacted(eq(WinReg.HKEY_CURRENT_USER), eqPointer(path), eq(0), eq(WinNT.KEY_READ), notNull(),
                    eq(transaction), isNULL());
            verify(RegistryKey.api).RegDeleteKeyTransacted(eq(WinReg.HKEY_CURRENT_USER), eqPointer(path), eq(0), eq(0), eq(transaction), isNULL());
            // Closed as part of create and exist calls
            verify(RegistryKey.api, times(2)).RegCloseKey(hKey);
            verifyNoMoreInteractions(RegistryKey.api);
            verifyNoMoreInteractions(Transaction.ktmW32);
            verifyNoMoreInteractions(Transaction.kernel32);
        }

        @Test
        @DisplayName("transactional with description")
        void testTransactionalWithDescription() {
            String path = "path\\transactional";

            MemorySegment hKey = newHKEY();
            MemorySegment transaction = ALLOCATOR.allocate(0);

            when(Transaction.ktmW32.CreateTransaction(isNULL(), isNULL(), anyInt(), anyInt(), anyInt(), eq(0), eqPointer("test"), notNull()))
                    .thenReturn(transaction);
            when(Transaction.kernel32.CloseHandle(eq(transaction), notNull())).thenReturn(true);

            when(RegistryKey.api.RegCreateKeyTransacted(eq(WinReg.HKEY_CURRENT_USER), eqPointer(path), eq(0), isNULL(),
                    eq(WinNT.REG_OPTION_NON_VOLATILE), eq(WinNT.KEY_READ), isNULL(), notNull(), notNull(), eq(transaction), isNULL()))
                    .thenAnswer(i -> {
                        setHKEY(i.getArgument(7, MemorySegment.class), hKey);
                        setInt(i.getArgument(8, MemorySegment.class), WinNT.REG_CREATED_NEW_KEY);

                        return WinError.ERROR_SUCCESS;
                    });
            when(RegistryKey.api.RegOpenKeyTransacted(eq(WinReg.HKEY_CURRENT_USER), eqPointer(path), eq(0), eq(WinNT.KEY_READ), notNull(),
                    eq(transaction), isNULL()))
                    .thenAnswer(i -> {
                        setHKEY(i.getArgument(4, MemorySegment.class), hKey);

                        return WinError.ERROR_SUCCESS;
                    });
            when(RegistryKey.api.RegDeleteKeyTransacted(eq(WinReg.HKEY_CURRENT_USER), eqPointer(path), eq(0), eq(0), eq(transaction), isNULL()))
                    .thenReturn(WinError.ERROR_SUCCESS);
            when(RegistryKey.api.RegCloseKey(hKey)).thenReturn(WinError.ERROR_SUCCESS);

            RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER.resolve(path);

            RegistryKey.transactional()
                    .withDescription("test")
                    .run(_ -> {
                        registryKey.create();
                        assertTrue(registryKey.exists());
                        registryKey.delete();
                    });

            verify(Transaction.ktmW32).CreateTransaction(isNULL(), isNULL(), eq(KtmTypes.TRANSACTION_DO_NOT_PROMOTE), eq(0), eq(0), eq(0),
                    eqPointer("test"), notNull());
            verify(Transaction.kernel32).CloseHandle(eq(transaction), notNull());

            verify(RegistryKey.api).RegCreateKeyTransacted(eq(WinReg.HKEY_CURRENT_USER), eqPointer(path), eq(0), notNull(),
                    eq(WinNT.REG_OPTION_NON_VOLATILE), eq(WinNT.KEY_READ), isNULL(), notNull(), notNull(), eq(transaction), isNULL());
            verify(RegistryKey.api).RegOpenKeyTransacted(eq(WinReg.HKEY_CURRENT_USER), eqPointer(path), eq(0), eq(WinNT.KEY_READ), notNull(),
                    eq(transaction), isNULL());
            verify(RegistryKey.api).RegDeleteKeyTransacted(eq(WinReg.HKEY_CURRENT_USER), eqPointer(path), eq(0), eq(0), eq(transaction), isNULL());
            // Closed as part of create and exist calls
            verify(RegistryKey.api, times(2)).RegCloseKey(hKey);
            verifyNoMoreInteractions(RegistryKey.api);
            verifyNoMoreInteractions(Transaction.ktmW32);
            verifyNoMoreInteractions(Transaction.kernel32);
        }
    }

    private void copyInstantToFileTime(Instant instant, MemorySegment fileTime) {
        com.sun.jna.platform.win32.WinBase.FILETIME jnaFileTime = new com.sun.jna.platform.win32.WinBase.FILETIME();
        jnaFileTime.dwLowDateTime = FILETIME.dwLowDateTime(fileTime);
        jnaFileTime.dwHighDateTime = FILETIME.dwHighDateTime(fileTime);

        SYSTEMTIME systemTime = new SYSTEMTIME(GregorianCalendar.from(instant.atZone(ZoneId.of("UTC"))));
        com.sun.jna.platform.win32.Kernel32.INSTANCE.SystemTimeToFileTime(systemTime, jnaFileTime);

        FILETIME.dwLowDateTime(fileTime, jnaFileTime.dwLowDateTime);
        FILETIME.dwHighDateTime(fileTime, jnaFileTime.dwHighDateTime);
    }
}
