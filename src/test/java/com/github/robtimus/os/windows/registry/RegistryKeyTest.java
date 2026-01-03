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

import static com.github.robtimus.os.windows.registry.ForeignTestUtils.eqPointer;
import static com.github.robtimus.os.windows.registry.ForeignTestUtils.isNULL;
import static com.github.robtimus.os.windows.registry.ForeignTestUtils.newHKEY;
import static com.github.robtimus.os.windows.registry.ForeignTestUtils.setHKEY;
import static com.github.robtimus.os.windows.registry.RegistryKeyMocks.mockValue;
import static com.github.robtimus.os.windows.registry.TransactionMocks.mockCloseHandle;
import static com.github.robtimus.os.windows.registry.TransactionMocks.mockCommitTransaction;
import static com.github.robtimus.os.windows.registry.TransactionMocks.mockCreateTransaction;
import static com.github.robtimus.os.windows.registry.TransactionMocks.mockCreateTransactions;
import static com.github.robtimus.os.windows.registry.TransactionMocks.mockGetTransactionStatus;
import static com.github.robtimus.os.windows.registry.TransactionOption.description;
import static com.github.robtimus.os.windows.registry.TransactionOption.infiniteTimeout;
import static com.github.robtimus.os.windows.registry.TransactionOption.timeout;
import static com.github.robtimus.os.windows.registry.foreign.Advapi32.RegCloseKey;
import static com.github.robtimus.os.windows.registry.foreign.Advapi32.RegCreateKeyEx;
import static com.github.robtimus.os.windows.registry.foreign.Advapi32.RegCreateKeyTransacted;
import static com.github.robtimus.os.windows.registry.foreign.Advapi32.RegDeleteKeyEx;
import static com.github.robtimus.os.windows.registry.foreign.Advapi32.RegDeleteKeyTransacted;
import static com.github.robtimus.os.windows.registry.foreign.Advapi32.RegOpenKeyEx;
import static com.github.robtimus.os.windows.registry.foreign.Advapi32.RegOpenKeyTransacted;
import static com.github.robtimus.os.windows.registry.foreign.Advapi32.RegQueryInfoKey;
import static com.github.robtimus.os.windows.registry.foreign.Advapi32.RegQueryValueEx;
import static com.github.robtimus.os.windows.registry.foreign.Kernel32.CloseHandle;
import static com.github.robtimus.os.windows.registry.foreign.KtmW32.CommitTransaction;
import static com.github.robtimus.os.windows.registry.foreign.KtmW32.CreateTransaction;
import static com.github.robtimus.os.windows.registry.foreign.KtmW32.GetTransactionInformation;
import static com.github.robtimus.os.windows.registry.foreign.WindowsConstants.ERROR_FILE_NOT_FOUND;
import static com.github.robtimus.os.windows.registry.foreign.WindowsConstants.ERROR_INVALID_HANDLE;
import static com.github.robtimus.os.windows.registry.foreign.WindowsConstants.ERROR_SUCCESS;
import static com.github.robtimus.os.windows.registry.foreign.WindowsConstants.HKEY_CURRENT_USER;
import static com.github.robtimus.os.windows.registry.foreign.WindowsConstants.KEY_READ;
import static com.github.robtimus.os.windows.registry.foreign.WindowsConstants.REG_CREATED_NEW_KEY;
import static com.github.robtimus.os.windows.registry.foreign.WindowsConstants.REG_OPTION_NON_VOLATILE;
import static com.github.robtimus.os.windows.registry.foreign.WindowsConstants.TRANSACTION_DO_NOT_PROMOTE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.times;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoField;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import com.github.robtimus.os.windows.registry.foreign.WindowsTypes.FILETIME;
import com.github.robtimus.os.windows.registry.foreign.WindowsTypes.TRANSACTION_OUTCOME;
import com.sun.jna.platform.win32.WinBase.SYSTEMTIME;

@SuppressWarnings("nls")
class RegistryKeyTest extends RegistryTestBase {

    private static final LocalRegistry REGISTRY = Registry.local();

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

            advapi32.when(() -> RegQueryInfoKey(notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(),
                    notNull(), notNull(), notNull()))
                    .thenAnswer(i -> {
                        MemorySegment fileTime = i.getArgument(11, MemorySegment.class);
                        copyInstantToFileTime(instant, fileTime);

                        return ERROR_SUCCESS;
                    });

            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER;
            assertEquals(instant, registryKey.lastWriteTime());
        }

        @Test
        @DisplayName("negative file time")
        void testNegativeFileTime() {
            advapi32.when(() -> RegQueryInfoKey(notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(),
                    notNull(), notNull(), notNull(), notNull()))
                    .thenAnswer(i -> {
                        MemorySegment fileTime = i.getArgument(11, MemorySegment.class);
                        FILETIME.dwHighDateTime(fileTime, -1);
                        FILETIME.dwLowDateTime(fileTime, 0);

                        return ERROR_SUCCESS;
                    });

            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER;
            assertEquals(RegistryKey.FILETIME_BASE, registryKey.lastWriteTime());
        }

        @Test
        @DisplayName("failure")
        void testFailure() {
            advapi32.when(() -> RegQueryInfoKey(notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(),
                    notNull(), notNull(), notNull()))
                    .thenReturn(ERROR_INVALID_HANDLE);

            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER;
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

            advapi32.when(() -> RegQueryInfoKey(notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(),
                    notNull(), notNull(), notNull()))
                    .thenAnswer(i -> {
                        i.getArgument(4, MemorySegment.class).set(ValueLayout.JAVA_INT, 0, 10);
                        i.getArgument(7, MemorySegment.class).set(ValueLayout.JAVA_INT, 0, 20);

                        MemorySegment fileTime = i.getArgument(11, MemorySegment.class);
                        copyInstantToFileTime(instant, fileTime);

                        return ERROR_SUCCESS;
                    });

            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER;
            RegistryKey.Attributes attributes = registryKey.attributes();

            assertEquals(10, attributes.subKeyCount());
            assertEquals(20, attributes.valueCount());
            assertEquals(instant, attributes.lastWriteTime());
        }

        @Test
        @DisplayName("failure")
        void testFailure() {
            advapi32.when(() -> RegQueryInfoKey(notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(),
                    notNull(), notNull(), notNull()))
                    .thenReturn(ERROR_INVALID_HANDLE);

            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER;
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

            mockValue(HKEY_CURRENT_USER, stringValue);

            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER;
            String value = registryKey.getStringValue("string");
            assertEquals(stringValue.value(), value);
        }

        @Test
        @DisplayName("non-existing value")
        void testNonExistingValue() {
            advapi32.when(() -> RegQueryValueEx(eq(HKEY_CURRENT_USER), notNull(), notNull(), notNull(), isNULL(), notNull()))
                    .thenReturn(ERROR_FILE_NOT_FOUND);

            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER;
            NoSuchRegistryValueException exception = assertThrows(NoSuchRegistryValueException.class, () -> registryKey.getStringValue("string"));
            assertEquals("HKEY_CURRENT_USER", exception.path());
            assertEquals("string", exception.name());
        }

        @Test
        @DisplayName("failure")
        void testFailure() {
            mockValue(HKEY_CURRENT_USER, StringValue.of("string", "value"), ERROR_INVALID_HANDLE);

            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER;
            InvalidRegistryHandleException exception = assertThrows(InvalidRegistryHandleException.class, () -> registryKey.getStringValue("string"));
            assertEquals("HKEY_CURRENT_USER", exception.path());
        }

        @Test
        @DisplayName("wrong value type")
        void testWrongValueType() {
            DWordValue dwordValue = DWordValue.of("dword", 13);

            mockValue(HKEY_CURRENT_USER, dwordValue);

            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER;
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

            mockValue(HKEY_CURRENT_USER, stringValue);

            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER;
            Optional<String> value = registryKey.findStringValue("string");
            assertEquals(Optional.of(stringValue.value()), value);
        }

        @Test
        @DisplayName("non-existing value")
        void testNonExistingValue() {
            advapi32.when(() -> RegQueryValueEx(eq(HKEY_CURRENT_USER), notNull(), notNull(), notNull(), isNULL(), notNull()))
                    .thenReturn(ERROR_FILE_NOT_FOUND);

            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER;
            Optional<String> value = registryKey.findStringValue("string");
            assertEquals(Optional.empty(), value);
        }

        @Test
        @DisplayName("failure")
        void testFailure() {
            mockValue(HKEY_CURRENT_USER, StringValue.of("string", "value"), ERROR_INVALID_HANDLE);

            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER;
            InvalidRegistryHandleException exception = assertThrows(InvalidRegistryHandleException.class,
                    () -> registryKey.findStringValue("string"));
            assertEquals("HKEY_CURRENT_USER", exception.path());
        }

        @Test
        @DisplayName("wrong value type")
        void testWrongValueType() {
            DWordValue dwordValue = DWordValue.of("dword", 13);

            mockValue(HKEY_CURRENT_USER, dwordValue);

            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER;
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

            mockValue(HKEY_CURRENT_USER, dwordValue);

            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER;
            int value = registryKey.getDWordValue("dword");
            assertEquals(dwordValue.value(), value);
        }

        @Test
        @DisplayName("non-existing value")
        void testNonExistingValue() {
            advapi32.when(() -> RegQueryValueEx(eq(HKEY_CURRENT_USER), notNull(), notNull(), notNull(), isNULL(), notNull()))
                    .thenReturn(ERROR_FILE_NOT_FOUND);

            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER;
            NoSuchRegistryValueException exception = assertThrows(NoSuchRegistryValueException.class, () -> registryKey.getDWordValue("dword"));
            assertEquals("HKEY_CURRENT_USER", exception.path());
            assertEquals("dword", exception.name());
        }

        @Test
        @DisplayName("failure")
        void testFailure() {
            mockValue(HKEY_CURRENT_USER, DWordValue.of("dword", 13), ERROR_INVALID_HANDLE);

            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER;
            InvalidRegistryHandleException exception = assertThrows(InvalidRegistryHandleException.class, () -> registryKey.getDWordValue("dword"));
            assertEquals("HKEY_CURRENT_USER", exception.path());
        }

        @Test
        @DisplayName("wrong value type")
        void testWrongValueType() {
            StringValue stringValue = StringValue.of("string", "test");

            mockValue(HKEY_CURRENT_USER, stringValue);

            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER;
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

            mockValue(HKEY_CURRENT_USER, dwordValue);

            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER;
            OptionalInt value = registryKey.findDWordValue("dword");
            assertEquals(OptionalInt.of(dwordValue.value()), value);
        }

        @Test
        @DisplayName("non-existing value")
        void testNonExistingValue() {
            advapi32.when(() -> RegQueryValueEx(eq(HKEY_CURRENT_USER), notNull(), notNull(), notNull(), isNULL(), notNull()))
                    .thenReturn(ERROR_FILE_NOT_FOUND);

            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER;
            OptionalInt value = registryKey.findDWordValue("dword");
            assertEquals(OptionalInt.empty(), value);
        }

        @Test
        @DisplayName("failure")
        void testFailure() {
            mockValue(HKEY_CURRENT_USER, DWordValue.of("dword", 13), ERROR_INVALID_HANDLE);

            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER;
            InvalidRegistryHandleException exception = assertThrows(InvalidRegistryHandleException.class, () -> registryKey.findDWordValue("dword"));
            assertEquals("HKEY_CURRENT_USER", exception.path());
        }

        @Test
        @DisplayName("wrong value type")
        void testWrongValueType() {
            StringValue stringValue = StringValue.of("string", "test");

            mockValue(HKEY_CURRENT_USER, stringValue);

            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER;
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

            mockValue(HKEY_CURRENT_USER, qwordValue);

            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER;
            long value = registryKey.getQWordValue("qword");
            assertEquals(qwordValue.value(), value);
        }

        @Test
        @DisplayName("non-existing value")
        void testNonExistingValue() {
            advapi32.when(() -> RegQueryValueEx(eq(HKEY_CURRENT_USER), notNull(), notNull(), notNull(), isNULL(), notNull()))
                    .thenReturn(ERROR_FILE_NOT_FOUND);

            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER;
            NoSuchRegistryValueException exception = assertThrows(NoSuchRegistryValueException.class, () -> registryKey.getQWordValue("qword"));
            assertEquals("HKEY_CURRENT_USER", exception.path());
            assertEquals("qword", exception.name());
        }

        @Test
        @DisplayName("failure")
        void testFailure() {
            mockValue(HKEY_CURRENT_USER, QWordValue.of("qword", 13L), ERROR_INVALID_HANDLE);

            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER;
            InvalidRegistryHandleException exception = assertThrows(InvalidRegistryHandleException.class, () -> registryKey.getQWordValue("qword"));
            assertEquals("HKEY_CURRENT_USER", exception.path());
        }

        @Test
        @DisplayName("wrong value type")
        void testWrongValueType() {
            StringValue stringValue = StringValue.of("string", "test");

            mockValue(HKEY_CURRENT_USER, stringValue);

            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER;
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

            mockValue(HKEY_CURRENT_USER, qwordValue);

            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER;
            OptionalLong value = registryKey.findQWordValue("qword");
            assertEquals(OptionalLong.of(qwordValue.value()), value);
        }

        @Test
        @DisplayName("non-existing value")
        void testNonExistingValue() {
            advapi32.when(() -> RegQueryValueEx(eq(HKEY_CURRENT_USER), notNull(), notNull(), notNull(), isNULL(), notNull()))
                    .thenReturn(ERROR_FILE_NOT_FOUND);

            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER;
            OptionalLong value = registryKey.findQWordValue("qword");
            assertEquals(OptionalLong.empty(), value);
        }

        @Test
        @DisplayName("failure")
        void testFailure() {
            mockValue(HKEY_CURRENT_USER, QWordValue.of("qword", 13L), ERROR_INVALID_HANDLE);

            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER;
            InvalidRegistryHandleException exception = assertThrows(InvalidRegistryHandleException.class, () -> registryKey.findQWordValue("qword"));
            assertEquals("HKEY_CURRENT_USER", exception.path());
        }

        @Test
        @DisplayName("wrong value type")
        void testWrongValueType() {
            StringValue stringValue = StringValue.of("string", "test");

            mockValue(HKEY_CURRENT_USER, stringValue);

            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER;
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

                advapi32.when(() -> RegQueryInfoKey(notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(),
                        notNull(), notNull(), notNull(), notNull()))
                        .thenAnswer(i -> {
                            MemorySegment fileTime = i.getArgument(11, MemorySegment.class);
                            copyInstantToFileTime(instant, fileTime);

                            return ERROR_SUCCESS;
                        });

                RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER;
                try (RegistryKey.Handle handle = registryKey.handle()) {
                    assertEquals(instant, handle.lastWriteTime());
                }
            }

            @Test
            @DisplayName("negative file time")
            void testNegativeFileTime() {
                advapi32.when(() -> RegQueryInfoKey(notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(),
                        notNull(), notNull(), notNull(), notNull()))
                        .thenAnswer(i -> {
                            MemorySegment fileTime = i.getArgument(11, MemorySegment.class);
                            FILETIME.dwHighDateTime(fileTime, -1);
                            FILETIME.dwLowDateTime(fileTime, 0);

                            return ERROR_SUCCESS;
                        });

                RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER;
                try (var _ = registryKey.handle()) {
                    assertEquals(RegistryKey.FILETIME_BASE, registryKey.lastWriteTime());
                }
            }

            @Test
            @DisplayName("failure")
            void testFailure() {
                advapi32.when(() -> RegQueryInfoKey(notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(),
                        notNull(), notNull(), notNull()))
                        .thenReturn(ERROR_INVALID_HANDLE);

                RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER;
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

                advapi32.when(() -> RegQueryInfoKey(notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(),
                        notNull(), notNull(), notNull(), notNull()))
                        .thenAnswer(i -> {
                            i.getArgument(4, MemorySegment.class).set(ValueLayout.JAVA_INT, 0, 10);
                            i.getArgument(7, MemorySegment.class).set(ValueLayout.JAVA_INT, 0, 20);

                            MemorySegment fileTime = i.getArgument(11, MemorySegment.class);
                            copyInstantToFileTime(instant, fileTime);

                            return ERROR_SUCCESS;
                        });

                RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER;
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
                advapi32.when(() -> RegQueryInfoKey(notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(), notNull(),
                        notNull(), notNull(), notNull()))
                        .thenReturn(ERROR_INVALID_HANDLE);

                RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER;
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

                mockValue(HKEY_CURRENT_USER, stringValue);

                RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER;
                try (RegistryKey.Handle handle = registryKey.handle()) {
                    String value = handle.getStringValue("string");
                    assertEquals(stringValue.value(), value);
                }
            }

            @Test
            @DisplayName("non-existing value")
            void testNonExistingValue() {
                advapi32.when(() -> RegQueryValueEx(eq(HKEY_CURRENT_USER), notNull(), notNull(), notNull(), isNULL(), notNull()))
                        .thenReturn(ERROR_FILE_NOT_FOUND);

                RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER;
                try (RegistryKey.Handle handle = registryKey.handle()) {
                    NoSuchRegistryValueException exception = assertThrows(NoSuchRegistryValueException.class, () -> handle.getStringValue("string"));
                    assertEquals("HKEY_CURRENT_USER", exception.path());
                    assertEquals("string", exception.name());
                }
            }

            @Test
            @DisplayName("failure")
            void testFailure() {
                mockValue(HKEY_CURRENT_USER, StringValue.of("string", "value"), ERROR_INVALID_HANDLE);

                RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER;
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

                mockValue(HKEY_CURRENT_USER, dwordValue);

                RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER;
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

                mockValue(HKEY_CURRENT_USER, stringValue);

                RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER;
                try (RegistryKey.Handle handle = registryKey.handle()) {
                    Optional<String> value = handle.findStringValue("string");
                    assertEquals(Optional.of(stringValue.value()), value);
                }
            }

            @Test
            @DisplayName("non-existing value")
            void testNonExistingValue() {
                advapi32.when(() -> RegQueryValueEx(eq(HKEY_CURRENT_USER), notNull(), notNull(), notNull(), isNULL(), notNull()))
                        .thenReturn(ERROR_FILE_NOT_FOUND);

                RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER;
                try (RegistryKey.Handle handle = registryKey.handle()) {
                    Optional<String> value = handle.findStringValue("string");
                    assertEquals(Optional.empty(), value);
                }
            }

            @Test
            @DisplayName("failure")
            void testFailure() {
                mockValue(HKEY_CURRENT_USER, StringValue.of("string", "value"), ERROR_INVALID_HANDLE);

                RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER;
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

                mockValue(HKEY_CURRENT_USER, dwordValue);

                RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER;
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

                mockValue(HKEY_CURRENT_USER, dwordValue);

                RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER;
                try (RegistryKey.Handle handle = registryKey.handle()) {
                    int value = handle.getDWordValue("dword");
                    assertEquals(dwordValue.value(), value);
                }
            }

            @Test
            @DisplayName("non-existing value")
            void testNonExistingValue() {
                advapi32.when(() -> RegQueryValueEx(eq(HKEY_CURRENT_USER), notNull(), notNull(), notNull(), isNULL(), notNull()))
                        .thenReturn(ERROR_FILE_NOT_FOUND);

                RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER;
                try (RegistryKey.Handle handle = registryKey.handle()) {
                    NoSuchRegistryValueException exception = assertThrows(NoSuchRegistryValueException.class, () -> handle.getDWordValue("dword"));
                    assertEquals("HKEY_CURRENT_USER", exception.path());
                    assertEquals("dword", exception.name());
                }
            }

            @Test
            @DisplayName("failure")
            void testFailure() {
                mockValue(HKEY_CURRENT_USER, DWordValue.of("dword", 13), ERROR_INVALID_HANDLE);

                RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER;
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

                mockValue(HKEY_CURRENT_USER, stringValue);

                RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER;
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

                mockValue(HKEY_CURRENT_USER, dwordValue);

                RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER;
                try (RegistryKey.Handle handle = registryKey.handle()) {
                    OptionalInt value = handle.findDWordValue("dword");
                    assertEquals(OptionalInt.of(dwordValue.value()), value);
                }
            }

            @Test
            @DisplayName("non-existing value")
            void testNonExistingValue() {
                advapi32.when(() -> RegQueryValueEx(eq(HKEY_CURRENT_USER), notNull(), notNull(), notNull(), isNULL(), notNull()))
                        .thenReturn(ERROR_FILE_NOT_FOUND);

                RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER;
                try (RegistryKey.Handle handle = registryKey.handle()) {
                    OptionalInt value = handle.findDWordValue("dword");
                    assertEquals(OptionalInt.empty(), value);
                }
            }

            @Test
            @DisplayName("failure")
            void testFailure() {
                mockValue(HKEY_CURRENT_USER, DWordValue.of("dword", 13), ERROR_INVALID_HANDLE);

                RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER;
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

                mockValue(HKEY_CURRENT_USER, stringValue);

                RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER;
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

                mockValue(HKEY_CURRENT_USER, qwordValue);

                RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER;
                try (RegistryKey.Handle handle = registryKey.handle()) {
                    long value = handle.getQWordValue("qword");
                    assertEquals(qwordValue.value(), value);
                }
            }

            @Test
            @DisplayName("non-existing value")
            void testNonExistingValue() {
                advapi32.when(() -> RegQueryValueEx(eq(HKEY_CURRENT_USER), notNull(), notNull(), notNull(), isNULL(), notNull()))
                        .thenReturn(ERROR_FILE_NOT_FOUND);

                RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER;
                try (RegistryKey.Handle handle = registryKey.handle()) {
                    NoSuchRegistryValueException exception = assertThrows(NoSuchRegistryValueException.class, () -> handle.getQWordValue("qword"));
                    assertEquals("HKEY_CURRENT_USER", exception.path());
                    assertEquals("qword", exception.name());
                }
            }

            @Test
            @DisplayName("failure")
            void testFailure() {
                mockValue(HKEY_CURRENT_USER, QWordValue.of("qword", 13L), ERROR_INVALID_HANDLE);

                RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER;
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

                mockValue(HKEY_CURRENT_USER, stringValue);

                RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER;
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

                mockValue(HKEY_CURRENT_USER, qwordValue);

                RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER;
                try (RegistryKey.Handle handle = registryKey.handle()) {
                    OptionalLong value = handle.findQWordValue("qword");
                    assertEquals(OptionalLong.of(qwordValue.value()), value);
                }
            }

            @Test
            @DisplayName("non-existing value")
            void testNonExistingValue() {
                advapi32.when(() -> RegQueryValueEx(eq(HKEY_CURRENT_USER), notNull(), notNull(), notNull(), isNULL(), notNull()))
                        .thenReturn(ERROR_FILE_NOT_FOUND);

                RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER;
                try (RegistryKey.Handle handle = registryKey.handle()) {
                    OptionalLong value = handle.findQWordValue("qword");
                    assertEquals(OptionalLong.empty(), value);
                }
            }

            @Test
            @DisplayName("failure")
            void testFailure() {
                mockValue(HKEY_CURRENT_USER, QWordValue.of("qword", 13L), ERROR_INVALID_HANDLE);

                RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER;
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

                mockValue(HKEY_CURRENT_USER, stringValue);

                RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER;
                try (RegistryKey.Handle handle = registryKey.handle()) {
                    assertThrows(ClassCastException.class, () -> handle.findQWordValue("string"));
                }
            }
        }
    }

    @Nested
    @DisplayName("transactional")
    class Transactional {

        @Test
        @DisplayName("default non-transactional")
        void testDefaultNonTransactional() {
            String path = "path\\non-transactional";

            MemorySegment hKey = newHKEY(arena);

            advapi32.when(() -> RegCreateKeyEx(eq(HKEY_CURRENT_USER), eqPointer(path), eq(0), notNull(),
                    eq(REG_OPTION_NON_VOLATILE), eq(KEY_READ), isNULL(), notNull(), notNull()))
                    .thenAnswer(i -> {
                        setHKEY(i.getArgument(7, MemorySegment.class), hKey);
                        i.getArgument(8, MemorySegment.class).set(ValueLayout.JAVA_INT, 0, REG_CREATED_NEW_KEY);

                        return ERROR_SUCCESS;
                    });
            advapi32.when(() -> RegOpenKeyEx(eq(HKEY_CURRENT_USER), eqPointer(path), eq(0), eq(KEY_READ), notNull()))
                    .thenAnswer(i -> {
                        setHKEY(i.getArgument(4, MemorySegment.class), hKey);

                        return ERROR_SUCCESS;
                    });
            advapi32.when(() -> RegDeleteKeyEx(eq(HKEY_CURRENT_USER), eqPointer(path), eq(0), eq(0))).thenReturn(ERROR_SUCCESS);
            advapi32.when(() -> RegCloseKey(hKey)).thenReturn(ERROR_SUCCESS);

            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER.resolve(path);

            registryKey.create();
            assertTrue(registryKey.exists());
            registryKey.delete();

            advapi32.verify(() -> RegCreateKeyEx(eq(HKEY_CURRENT_USER), eqPointer(path), eq(0), isNULL(),
                    eq(REG_OPTION_NON_VOLATILE), eq(KEY_READ), isNULL(), notNull(), notNull()));
            advapi32.verify(() -> RegOpenKeyEx(eq(HKEY_CURRENT_USER), eqPointer(path), eq(0), eq(KEY_READ), notNull()));
            advapi32.verify(() -> RegDeleteKeyEx(eq(HKEY_CURRENT_USER), eqPointer(path), eq(0), eq(0)));
            // Closed as part of create and exist calls
            advapi32.verify(() -> RegCloseKey(hKey), times(2));

            advapi32.verifyNoMoreInteractions();
            ktmW32.verifyNoInteractions();
            kernel32.verifyNoInteractions();
        }

        @Test
        @DisplayName("non-transactional")
        void testNonTransactional() {
            String path = "path\\non-transactional";

            MemorySegment hKey = newHKEY(arena);

            advapi32.when(() -> RegCreateKeyEx(eq(HKEY_CURRENT_USER), eqPointer(path), eq(0), notNull(),
                    eq(REG_OPTION_NON_VOLATILE), eq(KEY_READ), isNULL(), notNull(), notNull()))
                    .thenAnswer(i -> {
                        setHKEY(i.getArgument(7, MemorySegment.class), hKey);
                        i.getArgument(8, MemorySegment.class).set(ValueLayout.JAVA_INT, 0, REG_CREATED_NEW_KEY);

                        return ERROR_SUCCESS;
                    });
            advapi32.when(() -> RegOpenKeyEx(eq(HKEY_CURRENT_USER), eqPointer(path), eq(0), eq(KEY_READ), notNull()))
                    .thenAnswer(i -> {
                        setHKEY(i.getArgument(4, MemorySegment.class), hKey);

                        return ERROR_SUCCESS;
                    });
            advapi32.when(() -> RegDeleteKeyEx(eq(HKEY_CURRENT_USER), eqPointer(path), eq(0), eq(0))).thenReturn(ERROR_SUCCESS);
            advapi32.when(() -> RegCloseKey(hKey)).thenReturn(ERROR_SUCCESS);

            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER.resolve(path);

            TransactionalState.notSupported().run(() -> {
                registryKey.create();
                assertTrue(registryKey.exists());
                registryKey.delete();
            });

            advapi32.verify(() -> RegCreateKeyEx(eq(HKEY_CURRENT_USER), eqPointer(path), eq(0), isNULL(),
                    eq(REG_OPTION_NON_VOLATILE), eq(KEY_READ), isNULL(), notNull(), notNull()));
            advapi32.verify(() -> RegOpenKeyEx(eq(HKEY_CURRENT_USER), eqPointer(path), eq(0), eq(KEY_READ), notNull()));
            advapi32.verify(() -> RegDeleteKeyEx(eq(HKEY_CURRENT_USER), eqPointer(path), eq(0), eq(0)));
            // Closed as part of create and exist calls
            advapi32.verify(() -> RegCloseKey(hKey), times(2));

            advapi32.verifyNoMoreInteractions();
            ktmW32.verifyNoInteractions();
            kernel32.verifyNoInteractions();
        }

        @Test
        @DisplayName("transactional")
        void testTransactional() {
            String path = "path\\transactional";

            MemorySegment hKey = newHKEY(arena);

            MemorySegment transaction = mockCreateTransaction(Duration.ofMillis(0), null);
            mockGetTransactionStatus(transaction, TRANSACTION_OUTCOME.TransactionOutcomeUndetermined);
            mockCommitTransaction(transaction);
            mockCloseHandle(transaction);

            advapi32.when(() -> RegCreateKeyTransacted(eq(HKEY_CURRENT_USER), eqPointer(path), eq(0), isNULL(),
                    eq(REG_OPTION_NON_VOLATILE), eq(KEY_READ), isNULL(), notNull(), notNull(), eq(transaction), isNULL()))
                    .thenAnswer(i -> {
                        setHKEY(i.getArgument(7, MemorySegment.class), hKey);
                        i.getArgument(8, MemorySegment.class).set(ValueLayout.JAVA_INT, 0, REG_CREATED_NEW_KEY);

                        return ERROR_SUCCESS;
                    });
            advapi32.when(() -> RegOpenKeyTransacted(eq(HKEY_CURRENT_USER), eqPointer(path), eq(0), eq(KEY_READ), notNull(),
                    eq(transaction), isNULL()))
                    .thenAnswer(i -> {
                        setHKEY(i.getArgument(4, MemorySegment.class), hKey);

                        return ERROR_SUCCESS;
                    });
            advapi32.when(() -> RegDeleteKeyTransacted(eq(HKEY_CURRENT_USER), eqPointer(path), eq(0), eq(0), eq(transaction), isNULL()))
                    .thenReturn(ERROR_SUCCESS);
            advapi32.when(() -> RegCloseKey(hKey)).thenReturn(ERROR_SUCCESS);

            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER.resolve(path);

            TransactionalState.requiresNew().run(() -> {
                registryKey.create();
                assertTrue(registryKey.exists());
                registryKey.delete();
            });

            ktmW32.verify(() -> CreateTransaction(isNULL(), isNULL(), eq(TRANSACTION_DO_NOT_PROMOTE), eq(0), eq(0), eq(0), notNull(), notNull()));
            ktmW32.verify(() -> GetTransactionInformation(eq(transaction), notNull(), isNULL(), isNULL(), isNULL(), eq(0), isNULL(), notNull()));
            ktmW32.verify(() -> CommitTransaction(eq(transaction), notNull()));
            kernel32.verify(() -> CloseHandle(eq(transaction), notNull()));

            advapi32.verify(() -> RegCreateKeyTransacted(eq(HKEY_CURRENT_USER), eqPointer(path), eq(0), notNull(),
                    eq(REG_OPTION_NON_VOLATILE), eq(KEY_READ), isNULL(), notNull(), notNull(), eq(transaction), isNULL()));
            advapi32.verify(() -> RegOpenKeyTransacted(eq(HKEY_CURRENT_USER), eqPointer(path), eq(0), eq(KEY_READ), notNull(),
                    eq(transaction), isNULL()));
            advapi32.verify(() -> RegDeleteKeyTransacted(eq(HKEY_CURRENT_USER), eqPointer(path), eq(0), eq(0), eq(transaction), isNULL()));
            // Closed as part of create and exist calls
            advapi32.verify(() -> RegCloseKey(hKey), times(2));

            advapi32.verifyNoMoreInteractions();
            ktmW32.verifyNoMoreInteractions();
            kernel32.verifyNoMoreInteractions();
        }

        @Test
        @DisplayName("non-transactional in transactional")
        void testNonTransactionalInTransactional() {
            String path = "path\\transactional";

            MemorySegment hKey = newHKEY(arena);

            MemorySegment transaction = mockCreateTransaction(Duration.ofMillis(0), null);
            mockGetTransactionStatus(transaction, TRANSACTION_OUTCOME.TransactionOutcomeUndetermined);
            mockCommitTransaction(transaction);
            mockCloseHandle(transaction);

            advapi32.when(() -> RegCreateKeyEx(eq(HKEY_CURRENT_USER), eqPointer(path), eq(0), isNULL(),
                    eq(REG_OPTION_NON_VOLATILE), eq(KEY_READ), isNULL(), notNull(), notNull()))
                    .thenAnswer(i -> {
                        setHKEY(i.getArgument(7, MemorySegment.class), hKey);
                        i.getArgument(8, MemorySegment.class).set(ValueLayout.JAVA_INT, 0, REG_CREATED_NEW_KEY);

                        return ERROR_SUCCESS;
                    });
            advapi32.when(() -> RegOpenKeyEx(eq(HKEY_CURRENT_USER), eqPointer(path), eq(0), eq(KEY_READ), notNull()))
                    .thenAnswer(i -> {
                        setHKEY(i.getArgument(4, MemorySegment.class), hKey);

                        return ERROR_SUCCESS;
                    });
            advapi32.when(() -> RegDeleteKeyEx(eq(HKEY_CURRENT_USER), eqPointer(path), eq(0), eq(0))).thenReturn(ERROR_SUCCESS);
            advapi32.when(() -> RegCloseKey(hKey)).thenReturn(ERROR_SUCCESS);

            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER.resolve(path);

            String expectedResult = UUID.randomUUID().toString();
            String result = TransactionalState.requiresNew().call(() -> TransactionalState.notSupported().call(() -> {
                registryKey.create();
                assertTrue(registryKey.exists());
                registryKey.delete();

                return expectedResult;
            }));
            assertEquals(expectedResult, result);

            ktmW32.verify(() -> CreateTransaction(isNULL(), isNULL(), eq(TRANSACTION_DO_NOT_PROMOTE), eq(0), eq(0), eq(0), notNull(), notNull()));
            ktmW32.verify(() -> GetTransactionInformation(eq(transaction), notNull(), isNULL(), isNULL(), isNULL(), eq(0), isNULL(), notNull()));
            ktmW32.verify(() -> CommitTransaction(eq(transaction), notNull()));
            kernel32.verify(() -> CloseHandle(eq(transaction), notNull()));

            advapi32.verify(() -> RegCreateKeyEx(eq(HKEY_CURRENT_USER), eqPointer(path), eq(0), notNull(),
                    eq(REG_OPTION_NON_VOLATILE), eq(KEY_READ), isNULL(), notNull(), notNull()));
            advapi32.verify(() -> RegOpenKeyEx(eq(HKEY_CURRENT_USER), eqPointer(path), eq(0), eq(KEY_READ), notNull()));
            advapi32.verify(() -> RegDeleteKeyEx(eq(HKEY_CURRENT_USER), eqPointer(path), eq(0), eq(0)));
            // Closed as part of create and exist calls
            advapi32.verify(() -> RegCloseKey(hKey), times(2));

            advapi32.verifyNoMoreInteractions();
            ktmW32.verifyNoMoreInteractions();
            kernel32.verifyNoMoreInteractions();
        }

        @Test
        @DisplayName("nested transactional")
        void testNestedTransactional() {
            String path = "path\\transactional";

            MemorySegment hKey = newHKEY(arena);

            List<MemorySegment> transactions = mockCreateTransactions(Duration.ofMillis(0), null, 2);
            MemorySegment transaction1 = transactions.get(0);
            mockGetTransactionStatus(transaction1, TRANSACTION_OUTCOME.TransactionOutcomeUndetermined);
            mockCommitTransaction(transaction1);
            mockCloseHandle(transaction1);
            MemorySegment transaction2 = transactions.get(1);
            mockGetTransactionStatus(transaction2, TRANSACTION_OUTCOME.TransactionOutcomeUndetermined);
            mockCommitTransaction(transaction2);
            mockCloseHandle(transaction2);

            ktmW32.when(() -> CreateTransaction(isNULL(), isNULL(), anyInt(), anyInt(), anyInt(), eq(0), notNull(), notNull()))
                    .thenReturn(transaction1, transaction2);
            kernel32.when(() -> CloseHandle(eq(transaction1), notNull())).thenReturn(true);
            kernel32.when(() -> CloseHandle(eq(transaction2), notNull())).thenReturn(true);

            advapi32.when(() -> RegCreateKeyTransacted(eq(HKEY_CURRENT_USER), eqPointer(path), eq(0), isNULL(),
                    eq(REG_OPTION_NON_VOLATILE), eq(KEY_READ), isNULL(), notNull(), notNull(), eq(transaction2), isNULL()))
                    .thenAnswer(i -> {
                        setHKEY(i.getArgument(7, MemorySegment.class), hKey);
                        i.getArgument(8, MemorySegment.class).set(ValueLayout.JAVA_INT, 0, REG_CREATED_NEW_KEY);

                        return ERROR_SUCCESS;
                    });
            advapi32.when(() -> RegOpenKeyTransacted(eq(HKEY_CURRENT_USER), eqPointer(path), eq(0), eq(KEY_READ), notNull(),
                    eq(transaction2), isNULL()))
                    .thenAnswer(i -> {
                        setHKEY(i.getArgument(4, MemorySegment.class), hKey);

                        return ERROR_SUCCESS;
                    });
            advapi32.when(() -> RegDeleteKeyTransacted(eq(HKEY_CURRENT_USER), eqPointer(path), eq(0), eq(0), eq(transaction2), isNULL()))
                    .thenReturn(ERROR_SUCCESS);
            advapi32.when(() -> RegCloseKey(hKey)).thenReturn(ERROR_SUCCESS);

            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER.resolve(path);

            String expectedResult = UUID.randomUUID().toString();
            String result = TransactionalState.required().call(() -> TransactionalState.requiresNew().call(() -> {
                registryKey.create();
                assertTrue(registryKey.exists());
                registryKey.delete();

                return expectedResult;
            }));
            assertEquals(expectedResult, result);

            ktmW32.verify(() -> CreateTransaction(isNULL(), isNULL(), eq(TRANSACTION_DO_NOT_PROMOTE), eq(0), eq(0), eq(0), notNull(), notNull()),
                    times(2));
            ktmW32.verify(() -> GetTransactionInformation(eq(transaction1), notNull(), isNULL(), isNULL(), isNULL(), eq(0), isNULL(), notNull()));
            ktmW32.verify(() -> CommitTransaction(eq(transaction1), notNull()));
            kernel32.verify(() -> CloseHandle(eq(transaction1), notNull()));
            ktmW32.verify(() -> GetTransactionInformation(eq(transaction2), notNull(), isNULL(), isNULL(), isNULL(), eq(0), isNULL(), notNull()));
            ktmW32.verify(() -> CommitTransaction(eq(transaction2), notNull()));
            kernel32.verify(() -> CloseHandle(eq(transaction2), notNull()));

            advapi32.verify(() -> RegCreateKeyTransacted(eq(HKEY_CURRENT_USER), eqPointer(path), eq(0), notNull(),
                    eq(REG_OPTION_NON_VOLATILE), eq(KEY_READ), isNULL(), notNull(), notNull(), eq(transaction2), isNULL()));
            advapi32.verify(() -> RegOpenKeyTransacted(eq(HKEY_CURRENT_USER), eqPointer(path), eq(0), eq(KEY_READ), notNull(),
                    eq(transaction2), isNULL()));
            advapi32.verify(() -> RegDeleteKeyTransacted(eq(HKEY_CURRENT_USER), eqPointer(path), eq(0), eq(0), eq(transaction2), isNULL()));
            // Closed as part of create and exist calls
            advapi32.verify(() -> RegCloseKey(hKey), times(2));

            advapi32.verifyNoMoreInteractions();
            ktmW32.verifyNoMoreInteractions();
            kernel32.verifyNoMoreInteractions();
        }

        @Test
        @DisplayName("transactional with custom timeout")
        void testTransactionalWithCustomTimeout() {
            String path = "path\\transactional";

            MemorySegment hKey = newHKEY(arena);

            MemorySegment transaction = mockCreateTransaction(Duration.ofMillis(100), null);
            mockGetTransactionStatus(transaction, TRANSACTION_OUTCOME.TransactionOutcomeUndetermined);
            mockCommitTransaction(transaction);
            mockCloseHandle(transaction);

            advapi32.when(() -> RegCreateKeyTransacted(eq(HKEY_CURRENT_USER), eqPointer(path), eq(0), isNULL(),
                    eq(REG_OPTION_NON_VOLATILE), eq(KEY_READ), isNULL(), notNull(), notNull(), eq(transaction), isNULL()))
                    .thenAnswer(i -> {
                        setHKEY(i.getArgument(7, MemorySegment.class), hKey);
                        i.getArgument(8, MemorySegment.class).set(ValueLayout.JAVA_INT, 0, REG_CREATED_NEW_KEY);

                        return ERROR_SUCCESS;
                    });
            advapi32.when(() -> RegOpenKeyTransacted(eq(HKEY_CURRENT_USER), eqPointer(path), eq(0), eq(KEY_READ), notNull(),
                    eq(transaction), isNULL()))
                    .thenAnswer(i -> {
                        setHKEY(i.getArgument(4, MemorySegment.class), hKey);

                        return ERROR_SUCCESS;
                    });
            advapi32.when(() -> RegDeleteKeyTransacted(eq(HKEY_CURRENT_USER), eqPointer(path), eq(0), eq(0), eq(transaction), isNULL()))
                    .thenReturn(ERROR_SUCCESS);
            advapi32.when(() -> RegCloseKey(hKey)).thenReturn(ERROR_SUCCESS);

            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER.resolve(path);

            TransactionalState.required(timeout(Duration.ofMillis(100))).run(() -> {
                registryKey.create();
                assertTrue(registryKey.exists());
                registryKey.delete();
            });

            ktmW32.verify(() -> CreateTransaction(isNULL(), isNULL(), eq(TRANSACTION_DO_NOT_PROMOTE), eq(0), eq(0), eq(100), notNull(), notNull()));
            ktmW32.verify(() -> GetTransactionInformation(eq(transaction), notNull(), isNULL(), isNULL(), isNULL(), eq(0), isNULL(), notNull()));
            ktmW32.verify(() -> CommitTransaction(eq(transaction), notNull()));
            kernel32.verify(() -> CloseHandle(eq(transaction), notNull()));

            advapi32.verify(() -> RegCreateKeyTransacted(eq(HKEY_CURRENT_USER), eqPointer(path), eq(0), notNull(),
                    eq(REG_OPTION_NON_VOLATILE), eq(KEY_READ), isNULL(), notNull(), notNull(), eq(transaction), isNULL()));
            advapi32.verify(() -> RegOpenKeyTransacted(eq(HKEY_CURRENT_USER), eqPointer(path), eq(0), eq(KEY_READ), notNull(),
                    eq(transaction), isNULL()));
            advapi32.verify(() -> RegDeleteKeyTransacted(eq(HKEY_CURRENT_USER), eqPointer(path), eq(0), eq(0), eq(transaction), isNULL()));
            // Closed as part of create and exist calls
            advapi32.verify(() -> RegCloseKey(hKey), times(2));

            advapi32.verifyNoMoreInteractions();
            ktmW32.verifyNoMoreInteractions();
            kernel32.verifyNoMoreInteractions();
        }

        @Test
        @DisplayName("transactional with infinite timeout")
        void testTransactionalWithInfiniteTimeout() {
            String path = "path\\transactional";

            MemorySegment hKey = newHKEY(arena);

            MemorySegment transaction = mockCreateTransaction(Duration.ofMillis(0), null);
            mockGetTransactionStatus(transaction, TRANSACTION_OUTCOME.TransactionOutcomeUndetermined);
            mockCommitTransaction(transaction);
            mockCloseHandle(transaction);

            advapi32.when(() -> RegCreateKeyTransacted(eq(HKEY_CURRENT_USER), eqPointer(path), eq(0), isNULL(),
                    eq(REG_OPTION_NON_VOLATILE), eq(KEY_READ), isNULL(), notNull(), notNull(), eq(transaction), isNULL()))
                    .thenAnswer(i -> {
                        setHKEY(i.getArgument(7, MemorySegment.class), hKey);
                        i.getArgument(8, MemorySegment.class).set(ValueLayout.JAVA_INT, 0, REG_CREATED_NEW_KEY);

                        return ERROR_SUCCESS;
                    });
            advapi32.when(() -> RegOpenKeyTransacted(eq(HKEY_CURRENT_USER), eqPointer(path), eq(0), eq(KEY_READ), notNull(),
                    eq(transaction), isNULL()))
                    .thenAnswer(i -> {
                        setHKEY(i.getArgument(4, MemorySegment.class), hKey);

                        return ERROR_SUCCESS;
                    });
            advapi32.when(() -> RegDeleteKeyTransacted(eq(HKEY_CURRENT_USER), eqPointer(path), eq(0), eq(0), eq(transaction), isNULL()))
                    .thenReturn(ERROR_SUCCESS);
            advapi32.when(() -> RegCloseKey(hKey)).thenReturn(ERROR_SUCCESS);

            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER.resolve(path);

            TransactionalState.required(infiniteTimeout()).run(() -> {
                registryKey.create();
                assertTrue(registryKey.exists());
                registryKey.delete();
            });

            ktmW32.verify(() -> CreateTransaction(isNULL(), isNULL(), eq(TRANSACTION_DO_NOT_PROMOTE), eq(0), eq(0), eq(0), notNull(), notNull()));
            ktmW32.verify(() -> GetTransactionInformation(eq(transaction), notNull(), isNULL(), isNULL(), isNULL(), eq(0), isNULL(), notNull()));
            ktmW32.verify(() -> CommitTransaction(eq(transaction), notNull()));
            kernel32.verify(() -> CloseHandle(eq(transaction), notNull()));

            advapi32.verify(() -> RegCreateKeyTransacted(eq(HKEY_CURRENT_USER), eqPointer(path), eq(0), notNull(),
                    eq(REG_OPTION_NON_VOLATILE), eq(KEY_READ), isNULL(), notNull(), notNull(), eq(transaction), isNULL()));
            advapi32.verify(() -> RegOpenKeyTransacted(eq(HKEY_CURRENT_USER), eqPointer(path), eq(0), eq(KEY_READ), notNull(),
                    eq(transaction), isNULL()));
            advapi32.verify(() -> RegDeleteKeyTransacted(eq(HKEY_CURRENT_USER), eqPointer(path), eq(0), eq(0), eq(transaction), isNULL()));
            // Closed as part of create and exist calls
            advapi32.verify(() -> RegCloseKey(hKey), times(2));

            advapi32.verifyNoMoreInteractions();
            ktmW32.verifyNoMoreInteractions();
            kernel32.verifyNoMoreInteractions();
        }

        @Test
        @DisplayName("transactional with description")
        void testTransactionalWithDescription() {
            String path = "path\\transactional";

            MemorySegment hKey = newHKEY(arena);

            MemorySegment transaction = mockCreateTransaction(Duration.ofMillis(0), "test");
            mockGetTransactionStatus(transaction, TRANSACTION_OUTCOME.TransactionOutcomeUndetermined);
            mockCommitTransaction(transaction);
            mockCloseHandle(transaction);

            advapi32.when(() -> RegCreateKeyTransacted(eq(HKEY_CURRENT_USER), eqPointer(path), eq(0), isNULL(),
                    eq(REG_OPTION_NON_VOLATILE), eq(KEY_READ), isNULL(), notNull(), notNull(), eq(transaction), isNULL()))
                    .thenAnswer(i -> {
                        setHKEY(i.getArgument(7, MemorySegment.class), hKey);
                        i.getArgument(8, MemorySegment.class).set(ValueLayout.JAVA_INT, 0, REG_CREATED_NEW_KEY);

                        return ERROR_SUCCESS;
                    });
            advapi32.when(() -> RegOpenKeyTransacted(eq(HKEY_CURRENT_USER), eqPointer(path), eq(0), eq(KEY_READ), notNull(),
                    eq(transaction), isNULL()))
                    .thenAnswer(i -> {
                        setHKEY(i.getArgument(4, MemorySegment.class), hKey);

                        return ERROR_SUCCESS;
                    });
            advapi32.when(() -> RegDeleteKeyTransacted(eq(HKEY_CURRENT_USER), eqPointer(path), eq(0), eq(0), eq(transaction), isNULL()))
                    .thenReturn(ERROR_SUCCESS);
            advapi32.when(() -> RegCloseKey(hKey)).thenReturn(ERROR_SUCCESS);

            RegistryKey registryKey = REGISTRY.HKEY_CURRENT_USER.resolve(path);

            TransactionalState.required(description("test")).run(() -> {
                registryKey.create();
                assertTrue(registryKey.exists());
                registryKey.delete();
            });

            ktmW32.verify(() -> CreateTransaction(isNULL(), isNULL(), eq(TRANSACTION_DO_NOT_PROMOTE), eq(0), eq(0), eq(0), eqPointer("test"),
                    notNull()));
            ktmW32.verify(() -> GetTransactionInformation(eq(transaction), notNull(), isNULL(), isNULL(), isNULL(), eq(0), isNULL(), notNull()));
            ktmW32.verify(() -> CommitTransaction(eq(transaction), notNull()));
            kernel32.verify(() -> CloseHandle(eq(transaction), notNull()));

            advapi32.verify(() -> RegCreateKeyTransacted(eq(HKEY_CURRENT_USER), eqPointer(path), eq(0), notNull(),
                    eq(REG_OPTION_NON_VOLATILE), eq(KEY_READ), isNULL(), notNull(), notNull(), eq(transaction), isNULL()));
            advapi32.verify(() -> RegOpenKeyTransacted(eq(HKEY_CURRENT_USER), eqPointer(path), eq(0), eq(KEY_READ), notNull(),
                    eq(transaction), isNULL()));
            advapi32.verify(() -> RegDeleteKeyTransacted(eq(HKEY_CURRENT_USER), eqPointer(path), eq(0), eq(0), eq(transaction), isNULL()));
            // Closed as part of create and exist calls
            advapi32.verify(() -> RegCloseKey(hKey), times(2));

            advapi32.verifyNoMoreInteractions();
            ktmW32.verifyNoMoreInteractions();
            kernel32.verifyNoMoreInteractions();
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
