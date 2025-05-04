/*
 * TransactionalStateTest.java
 * Copyright 2025 Rob Spoor
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

import static com.github.robtimus.os.windows.registry.TransactionOption.description;
import static com.github.robtimus.os.windows.registry.TransactionOption.timeout;
import static com.github.robtimus.os.windows.registry.foreign.ForeignTestUtils.eqPointer;
import static com.github.robtimus.os.windows.registry.foreign.ForeignTestUtils.isNULL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import java.lang.foreign.MemorySegment;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.github.robtimus.os.windows.registry.foreign.WinNT;

@SuppressWarnings("nls")
class TransactionalStateTest extends TransactionTestBase {

    @Nested
    @DisplayName("mandatory")
    class Mandatory {

        @Test
        @DisplayName("called without current transaction")
        void testWithoutCurrentTransaction() {
            TransactionalState transactionalState = TransactionalState.mandatory();

            TransactionalState.Action<?> action = mock();

            TransactionRequiredException exception = assertThrows(TransactionRequiredException.class, () -> transactionalState.run(action));
            assertEquals(Messages.Transaction.transactionRequired(), exception.getMessage());

            verifyNoInteractions(action);
        }

        @Test
        @DisplayName("called with current transaction")
        void testWithCurrentTransaction() {
            Transaction transaction = createTransaction();

            String expectedResult = UUID.randomUUID().toString();

            String result = RegistryKey.callWithTransaction(transaction, () -> TransactionalState.mandatory().call(() -> {
                assertEquals(Optional.of(transaction), Transaction.current());

                return expectedResult;
            }));

            assertEquals(expectedResult, result);
        }

        @Test
        @DisplayName("toString")
        void testToString() {
            TransactionalState transactionalState = TransactionalState.mandatory();

            assertEquals("mandatory", transactionalState.toString());
        }
    }

    @Nested
    @DisplayName("required")
    class Required {

        @Nested
        @DisplayName("without options")
        class WithoutOptions {

            @Test
            @DisplayName("called without current transaction")
            void testWithoutCurrentTransaction() {
                MemorySegment handle = mockCreateTransaction(Duration.ofMillis(0), null);
                mockGetTransactionStatus(handle, WinNT.TRANSACTION_OUTCOME.TransactionOutcomeUndetermined);
                mockCommitTransaction(handle);
                mockCloseHandle(handle);

                String expectedResult = UUID.randomUUID().toString();

                String result = TransactionalState.required().call(() -> {
                    Transaction currentTransaction = Transaction.current().orElse(null);
                    assertNotNull(currentTransaction);
                    assertSame(handle, currentTransaction.handle());

                    return expectedResult;
                });

                assertEquals(expectedResult, result);

                verify(Transaction.ktmW32).CreateTransaction(isNULL(), isNULL(), anyInt(), anyInt(), anyInt(), eq(0), isNULL(), notNull());
                verify(Transaction.ktmW32).GetTransactionInformation(eq(handle), notNull(), isNULL(), isNULL(), isNULL(), eq(0), isNULL(), notNull());
                verify(Transaction.ktmW32).CommitTransaction(eq(handle), notNull());
                verify(Transaction.kernel32).CloseHandle(eq(handle), notNull());
                verifyNoMoreInteractions(Transaction.ktmW32, Transaction.kernel32);
            }

            @Test
            @DisplayName("called with current transaction")
            void testWithCurrentTransaction() {
                Transaction transaction = createTransaction();

                String expectedResult = UUID.randomUUID().toString();

                String result = RegistryKey.callWithTransaction(transaction, () -> TransactionalState.required().call(() -> {
                    assertEquals(Optional.of(transaction), Transaction.current());

                    return expectedResult;
                }));

                assertEquals(expectedResult, result);

                verify(Transaction.ktmW32).CreateTransaction(isNULL(), isNULL(), anyInt(), anyInt(), anyInt(), eq(100), isNULL(), notNull());
                verifyNoMoreInteractions(Transaction.ktmW32, Transaction.kernel32);
            }

            @Test
            @DisplayName("toString")
            void testToString() {
                TransactionalState transactionalState = TransactionalState.required();

                assertEquals("required()", transactionalState.toString());
            }
        }

        @Nested
        @DisplayName("with timeout")
        class WithTimeout {

            @Test
            @DisplayName("called without current transaction")
            void testWithoutCurrentTransaction() {
                MemorySegment handle = mockCreateTransaction(Duration.ofMillis(100), null);
                mockGetTransactionStatus(handle, WinNT.TRANSACTION_OUTCOME.TransactionOutcomeUndetermined);
                mockCommitTransaction(handle);
                mockCloseHandle(handle);

                String expectedResult = UUID.randomUUID().toString();

                String result = TransactionalState.required(timeout(Duration.ofMillis(100))).call(() -> {
                    Transaction currentTransaction = Transaction.current().orElse(null);
                    assertNotNull(currentTransaction);
                    assertSame(handle, currentTransaction.handle());

                    return expectedResult;
                });

                assertEquals(expectedResult, result);

                verify(Transaction.ktmW32).CreateTransaction(isNULL(), isNULL(), anyInt(), anyInt(), anyInt(), eq(100), isNULL(), notNull());
                verify(Transaction.ktmW32).GetTransactionInformation(eq(handle), notNull(), isNULL(), isNULL(), isNULL(), eq(0), isNULL(), notNull());
                verify(Transaction.ktmW32).CommitTransaction(eq(handle), notNull());
                verify(Transaction.kernel32).CloseHandle(eq(handle), notNull());
                verifyNoMoreInteractions(Transaction.ktmW32, Transaction.kernel32);
            }

            @Test
            @DisplayName("called with current transaction")
            void testWithCurrentTransaction() {
                Transaction transaction = createTransaction(Duration.ofMillis(0), null);

                String expectedResult = UUID.randomUUID().toString();

                String result = RegistryKey.callWithTransaction(transaction,
                        () -> TransactionalState.required(timeout(Duration.ofMillis(100))).call(() -> {
                            assertEquals(Optional.of(transaction), Transaction.current());

                            return expectedResult;
                        }));

                assertEquals(expectedResult, result);

                verify(Transaction.ktmW32).CreateTransaction(isNULL(), isNULL(), anyInt(), anyInt(), anyInt(), eq(0), isNULL(), notNull());
                verifyNoMoreInteractions(Transaction.ktmW32, Transaction.kernel32);
            }

            @Test
            @DisplayName("toString")
            void testToString() {
                Duration timeout = Duration.ofMillis(100);

                TransactionalState transactionalState = TransactionalState.required(timeout(timeout));

                assertEquals("required(timeout=%s)".formatted(timeout), transactionalState.toString());
            }
        }

        @Nested
        @DisplayName("with description")
        class WithDescription {

            @Test
            @DisplayName("called without current transaction")
            void testWithoutCurrentTransaction() {
                MemorySegment handle = mockCreateTransaction(Duration.ofMillis(0), "test");
                mockGetTransactionStatus(handle, WinNT.TRANSACTION_OUTCOME.TransactionOutcomeUndetermined);
                mockCommitTransaction(handle);
                mockCloseHandle(handle);

                String expectedResult = UUID.randomUUID().toString();

                String result = TransactionalState.required(description("test")).call(() -> {
                    Transaction currentTransaction = Transaction.current().orElse(null);
                    assertNotNull(currentTransaction);
                    assertSame(handle, currentTransaction.handle());

                    return expectedResult;
                });

                assertEquals(expectedResult, result);

                verify(Transaction.ktmW32).CreateTransaction(isNULL(), isNULL(), anyInt(), anyInt(), anyInt(), eq(0), eqPointer("test"), notNull());
                verify(Transaction.ktmW32).GetTransactionInformation(eq(handle), notNull(), isNULL(), isNULL(), isNULL(), eq(0), isNULL(), notNull());
                verify(Transaction.ktmW32).CommitTransaction(eq(handle), notNull());
                verify(Transaction.kernel32).CloseHandle(eq(handle), notNull());
                verifyNoMoreInteractions(Transaction.ktmW32, Transaction.kernel32);
            }

            @Test
            @DisplayName("called with current transaction")
            void testWithCurrentTransaction() {
                Transaction transaction = createTransaction(Duration.ofMillis(0), null);

                String expectedResult = UUID.randomUUID().toString();

                String result = RegistryKey.callWithTransaction(transaction,
                        () -> TransactionalState.required(description("test")).call(() -> {
                            assertEquals(Optional.of(transaction), Transaction.current());

                            return expectedResult;
                        }));

                assertEquals(expectedResult, result);

                verify(Transaction.ktmW32).CreateTransaction(isNULL(), isNULL(), anyInt(), anyInt(), anyInt(), eq(0), isNULL(), notNull());
                verifyNoMoreInteractions(Transaction.ktmW32, Transaction.kernel32);
            }

            @Test
            @DisplayName("toString")
            void testToString() {
                TransactionalState transactionalState = TransactionalState.required(description("test"));

                assertEquals("required(description=test)", transactionalState.toString());
            }
        }

        @Nested
        @DisplayName("with all options")
        class WithAllOptions {

            @Test
            @DisplayName("called without current transaction")
            void testWithoutCurrentTransaction() {
                MemorySegment handle = mockCreateTransaction(Duration.ofMillis(100), "test");
                mockGetTransactionStatus(handle, WinNT.TRANSACTION_OUTCOME.TransactionOutcomeUndetermined);
                mockCommitTransaction(handle);
                mockCloseHandle(handle);

                String expectedResult = UUID.randomUUID().toString();

                String result = TransactionalState.required(timeout(Duration.ofMillis(100)), description("test")).call(() -> {
                    Transaction currentTransaction = Transaction.current().orElse(null);
                    assertNotNull(currentTransaction);
                    assertSame(handle, currentTransaction.handle());

                    return expectedResult;
                });

                assertEquals(expectedResult, result);

                verify(Transaction.ktmW32).CreateTransaction(isNULL(), isNULL(), anyInt(), anyInt(), anyInt(), eq(100), eqPointer("test"), notNull());
                verify(Transaction.ktmW32).GetTransactionInformation(eq(handle), notNull(), isNULL(), isNULL(), isNULL(), eq(0), isNULL(), notNull());
                verify(Transaction.ktmW32).CommitTransaction(eq(handle), notNull());
                verify(Transaction.kernel32).CloseHandle(eq(handle), notNull());
                verifyNoMoreInteractions(Transaction.ktmW32, Transaction.kernel32);
            }

            @Test
            @DisplayName("called with current transaction")
            void testWithCurrentTransaction() {
                Transaction transaction = createTransaction(Duration.ofMillis(0), null);

                String expectedResult = UUID.randomUUID().toString();

                String result = RegistryKey.callWithTransaction(transaction,
                        () -> TransactionalState.required(timeout(Duration.ofMillis(100)), description("test")).call(() -> {
                            assertEquals(Optional.of(transaction), Transaction.current());

                            return expectedResult;
                        }));

                assertEquals(expectedResult, result);

                verify(Transaction.ktmW32).CreateTransaction(isNULL(), isNULL(), anyInt(), anyInt(), anyInt(), eq(0), isNULL(), notNull());
                verifyNoMoreInteractions(Transaction.ktmW32, Transaction.kernel32);
            }

            @Test
            @DisplayName("toString")
            void testToString() {
                Duration timeout = Duration.ofMillis(100);

                TransactionalState transactionalState = TransactionalState.required(timeout(timeout), description("test"));

                assertEquals("required(description=test, timeout=%s)".formatted(timeout), transactionalState.toString());
            }
        }

        @Test
        @DisplayName("with null option")
        void testWithNullOption() {
            TransactionOption option = timeout(Duration.ofMillis(0));
            assertThrows(NullPointerException.class, () -> TransactionalState.required(option, null));
        }

        @Test
        @DisplayName("with duplicate option type")
        void testWithDuplicateOptionType() {
            TransactionOption option1 = timeout(Duration.ofMillis(0));
            TransactionOption option2 = description("test");
            TransactionOption option3 = timeout(Duration.ofMillis(0));
            assertThrows(IllegalStateException.class, () -> TransactionalState.required(option1, option2, option3));
        }
    }

    @Nested
    @DisplayName("requiresNew")
    class RequiresNew {

        @Nested
        @DisplayName("without options")
        class WithoutOptions {

            @Test
            @DisplayName("called without current transaction")
            void testWithoutCurrentTransaction() {
                MemorySegment handle = mockCreateTransaction(Duration.ofMillis(0), null);
                mockGetTransactionStatus(handle, WinNT.TRANSACTION_OUTCOME.TransactionOutcomeUndetermined);
                mockCommitTransaction(handle);
                mockCloseHandle(handle);

                String expectedResult = UUID.randomUUID().toString();

                String result = TransactionalState.requiresNew().call(() -> {
                    Transaction currentTransaction = Transaction.current().orElse(null);
                    assertNotNull(currentTransaction);
                    assertSame(handle, currentTransaction.handle());

                    return expectedResult;
                });

                assertEquals(expectedResult, result);

                verify(Transaction.ktmW32).CreateTransaction(isNULL(), isNULL(), anyInt(), anyInt(), anyInt(), eq(0), isNULL(), notNull());
                verify(Transaction.ktmW32).GetTransactionInformation(eq(handle), notNull(), isNULL(), isNULL(), isNULL(), eq(0), isNULL(), notNull());
                verify(Transaction.ktmW32).CommitTransaction(eq(handle), notNull());
                verify(Transaction.kernel32).CloseHandle(eq(handle), notNull());
                verifyNoMoreInteractions(Transaction.ktmW32, Transaction.kernel32);
            }

            @Test
            @DisplayName("called with current transaction")
            void testWithCurrentTransaction() {
                Transaction transaction = createTransaction();

                MemorySegment handle = mockCreateTransaction(Duration.ofMillis(0), null);
                mockGetTransactionStatus(handle, WinNT.TRANSACTION_OUTCOME.TransactionOutcomeUndetermined);
                mockCommitTransaction(handle);
                mockCloseHandle(handle);

                String expectedResult = UUID.randomUUID().toString();

                String result = RegistryKey.callWithTransaction(transaction, () -> TransactionalState.requiresNew().call(() -> {
                    Transaction currentTransaction = Transaction.current().orElse(null);
                    assertNotNull(currentTransaction);
                    assertNotEquals(transaction, currentTransaction);
                    assertSame(handle, currentTransaction.handle());

                    return expectedResult;
                }));

                assertEquals(expectedResult, result);

                verify(Transaction.ktmW32).CreateTransaction(isNULL(), isNULL(), anyInt(), anyInt(), anyInt(), eq(100), isNULL(), notNull());
                verify(Transaction.ktmW32).CreateTransaction(isNULL(), isNULL(), anyInt(), anyInt(), anyInt(), eq(0), isNULL(), notNull());
                verify(Transaction.ktmW32).GetTransactionInformation(eq(handle), notNull(), isNULL(), isNULL(), isNULL(), eq(0), isNULL(), notNull());
                verify(Transaction.ktmW32).CommitTransaction(eq(handle), notNull());
                verify(Transaction.kernel32).CloseHandle(eq(handle), notNull());
                verifyNoMoreInteractions(Transaction.ktmW32, Transaction.kernel32);
            }

            @Test
            @DisplayName("toString")
            void testToString() {
                TransactionalState transactionalState = TransactionalState.requiresNew();

                assertEquals("requiresNew()", transactionalState.toString());
            }
        }

        @Nested
        @DisplayName("with timeout")
        class WithTimeout {

            @Test
            @DisplayName("called without current transaction")
            void testWithoutCurrentTransaction() {
                MemorySegment handle = mockCreateTransaction(Duration.ofMillis(100), null);
                mockGetTransactionStatus(handle, WinNT.TRANSACTION_OUTCOME.TransactionOutcomeUndetermined);
                mockCommitTransaction(handle);
                mockCloseHandle(handle);

                String expectedResult = UUID.randomUUID().toString();

                String result = TransactionalState.requiresNew(timeout(Duration.ofMillis(100))).call(() -> {
                    Transaction currentTransaction = Transaction.current().orElse(null);
                    assertNotNull(currentTransaction);
                    assertSame(handle, currentTransaction.handle());

                    return expectedResult;
                });

                assertEquals(expectedResult, result);

                verify(Transaction.ktmW32).CreateTransaction(isNULL(), isNULL(), anyInt(), anyInt(), anyInt(), eq(100), isNULL(), notNull());
                verify(Transaction.ktmW32).GetTransactionInformation(eq(handle), notNull(), isNULL(), isNULL(), isNULL(), eq(0), isNULL(), notNull());
                verify(Transaction.ktmW32).CommitTransaction(eq(handle), notNull());
                verify(Transaction.kernel32).CloseHandle(eq(handle), notNull());
                verifyNoMoreInteractions(Transaction.ktmW32, Transaction.kernel32);
            }

            @Test
            @DisplayName("called with current transaction")
            void testWithCurrentTransaction() {
                Transaction transaction = createTransaction(Duration.ofMillis(0), null);

                MemorySegment handle = mockCreateTransaction(Duration.ofMillis(100), null);
                mockGetTransactionStatus(handle, WinNT.TRANSACTION_OUTCOME.TransactionOutcomeUndetermined);
                mockCommitTransaction(handle);
                mockCloseHandle(handle);

                String expectedResult = UUID.randomUUID().toString();

                String result = RegistryKey.callWithTransaction(transaction,
                        () -> TransactionalState.requiresNew(timeout(Duration.ofMillis(100))).call(() -> {
                            Transaction currentTransaction = Transaction.current().orElse(null);
                            assertNotNull(currentTransaction);
                            assertNotEquals(transaction, currentTransaction);
                            assertSame(handle, currentTransaction.handle());

                            return expectedResult;
                        }));

                assertEquals(expectedResult, result);

                verify(Transaction.ktmW32).CreateTransaction(isNULL(), isNULL(), anyInt(), anyInt(), anyInt(), eq(0), isNULL(), notNull());
                verify(Transaction.ktmW32).CreateTransaction(isNULL(), isNULL(), anyInt(), anyInt(), anyInt(), eq(100), isNULL(), notNull());
                verify(Transaction.ktmW32).GetTransactionInformation(eq(handle), notNull(), isNULL(), isNULL(), isNULL(), eq(0), isNULL(), notNull());
                verify(Transaction.ktmW32).CommitTransaction(eq(handle), notNull());
                verify(Transaction.kernel32).CloseHandle(eq(handle), notNull());
                verifyNoMoreInteractions(Transaction.ktmW32, Transaction.kernel32);
            }

            @Test
            @DisplayName("toString")
            void testToString() {
                Duration timeout = Duration.ofMillis(100);

                TransactionalState transactionalState = TransactionalState.requiresNew(timeout(timeout));

                assertEquals("requiresNew(timeout=%s)".formatted(timeout), transactionalState.toString());
            }
        }

        @Nested
        @DisplayName("with description")
        class WithDescription {

            @Test
            @DisplayName("called without current transaction")
            void testWithoutCurrentTransaction() {
                MemorySegment handle = mockCreateTransaction(Duration.ofMillis(0), "test");
                mockGetTransactionStatus(handle, WinNT.TRANSACTION_OUTCOME.TransactionOutcomeUndetermined);
                mockCommitTransaction(handle);
                mockCloseHandle(handle);

                String expectedResult = UUID.randomUUID().toString();

                String result = TransactionalState.requiresNew(description("test")).call(() -> {
                    Transaction currentTransaction = Transaction.current().orElse(null);
                    assertNotNull(currentTransaction);
                    assertSame(handle, currentTransaction.handle());

                    return expectedResult;
                });

                assertEquals(expectedResult, result);

                verify(Transaction.ktmW32).CreateTransaction(isNULL(), isNULL(), anyInt(), anyInt(), anyInt(), eq(0), eqPointer("test"), notNull());
                verify(Transaction.ktmW32).GetTransactionInformation(eq(handle), notNull(), isNULL(), isNULL(), isNULL(), eq(0), isNULL(), notNull());
                verify(Transaction.ktmW32).CommitTransaction(eq(handle), notNull());
                verify(Transaction.kernel32).CloseHandle(eq(handle), notNull());
                verifyNoMoreInteractions(Transaction.ktmW32, Transaction.kernel32);
            }

            @Test
            @DisplayName("called with current transaction")
            void testWithCurrentTransaction() {
                Transaction transaction = createTransaction(Duration.ofMillis(0), null);

                MemorySegment handle = mockCreateTransaction(Duration.ofMillis(0), "test");
                mockGetTransactionStatus(handle, WinNT.TRANSACTION_OUTCOME.TransactionOutcomeUndetermined);
                mockCommitTransaction(handle);
                mockCloseHandle(handle);

                String expectedResult = UUID.randomUUID().toString();

                String result = RegistryKey.callWithTransaction(transaction,
                        () -> TransactionalState.requiresNew(description("test")).call(() -> {
                            Transaction currentTransaction = Transaction.current().orElse(null);
                            assertNotNull(currentTransaction);
                            assertNotEquals(transaction, currentTransaction);
                            assertSame(handle, currentTransaction.handle());

                            return expectedResult;
                        }));

                assertEquals(expectedResult, result);

                verify(Transaction.ktmW32).CreateTransaction(isNULL(), isNULL(), anyInt(), anyInt(), anyInt(), eq(0), isNULL(), notNull());
                verify(Transaction.ktmW32).CreateTransaction(isNULL(), isNULL(), anyInt(), anyInt(), anyInt(), eq(0), eqPointer("test"), notNull());
                verify(Transaction.ktmW32).GetTransactionInformation(eq(handle), notNull(), isNULL(), isNULL(), isNULL(), eq(0), isNULL(), notNull());
                verify(Transaction.ktmW32).CommitTransaction(eq(handle), notNull());
                verify(Transaction.kernel32).CloseHandle(eq(handle), notNull());
                verifyNoMoreInteractions(Transaction.ktmW32, Transaction.kernel32);
            }

            @Test
            @DisplayName("toString")
            void testToString() {
                TransactionalState transactionalState = TransactionalState.requiresNew(description("test"));

                assertEquals("requiresNew(description=test)", transactionalState.toString());
            }
        }

        @Nested
        @DisplayName("with all options")
        class WithAllOptions {

            @Test
            @DisplayName("called without current transaction")
            void testWithoutCurrentTransaction() {
                MemorySegment handle = mockCreateTransaction(Duration.ofMillis(100), "test");
                mockGetTransactionStatus(handle, WinNT.TRANSACTION_OUTCOME.TransactionOutcomeUndetermined);
                mockCommitTransaction(handle);
                mockCloseHandle(handle);

                String expectedResult = UUID.randomUUID().toString();

                String result = TransactionalState.requiresNew(timeout(Duration.ofMillis(100)), description("test")).call(() -> {
                    Transaction currentTransaction = Transaction.current().orElse(null);
                    assertNotNull(currentTransaction);
                    assertSame(handle, currentTransaction.handle());

                    return expectedResult;
                });

                assertEquals(expectedResult, result);

                verify(Transaction.ktmW32).CreateTransaction(isNULL(), isNULL(), anyInt(), anyInt(), anyInt(), eq(100), eqPointer("test"), notNull());
                verify(Transaction.ktmW32).GetTransactionInformation(eq(handle), notNull(), isNULL(), isNULL(), isNULL(), eq(0), isNULL(), notNull());
                verify(Transaction.ktmW32).CommitTransaction(eq(handle), notNull());
                verify(Transaction.kernel32).CloseHandle(eq(handle), notNull());
                verifyNoMoreInteractions(Transaction.ktmW32, Transaction.kernel32);
            }

            @Test
            @DisplayName("called with current transaction")
            void testWithCurrentTransaction() {
                Transaction transaction = createTransaction(Duration.ofMillis(0), null);

                MemorySegment handle = mockCreateTransaction(Duration.ofMillis(100), "test");
                mockGetTransactionStatus(handle, WinNT.TRANSACTION_OUTCOME.TransactionOutcomeUndetermined);
                mockCommitTransaction(handle);
                mockCloseHandle(handle);

                String expectedResult = UUID.randomUUID().toString();

                String result = RegistryKey.callWithTransaction(transaction,
                        () -> TransactionalState.requiresNew(timeout(Duration.ofMillis(100)), description("test")).call(() -> {
                            Transaction currentTransaction = Transaction.current().orElse(null);
                            assertNotNull(currentTransaction);
                            assertNotEquals(transaction, currentTransaction);
                            assertSame(handle, currentTransaction.handle());

                            return expectedResult;
                        }));

                assertEquals(expectedResult, result);

                verify(Transaction.ktmW32).CreateTransaction(isNULL(), isNULL(), anyInt(), anyInt(), anyInt(), eq(0), isNULL(), notNull());
                verify(Transaction.ktmW32).CreateTransaction(isNULL(), isNULL(), anyInt(), anyInt(), anyInt(), eq(100), eqPointer("test"), notNull());
                verify(Transaction.ktmW32).GetTransactionInformation(eq(handle), notNull(), isNULL(), isNULL(), isNULL(), eq(0), isNULL(), notNull());
                verify(Transaction.ktmW32).CommitTransaction(eq(handle), notNull());
                verify(Transaction.kernel32).CloseHandle(eq(handle), notNull());
                verifyNoMoreInteractions(Transaction.ktmW32, Transaction.kernel32);
            }

            @Test
            @DisplayName("toString")
            void testToString() {
                Duration timeout = Duration.ofMillis(100);

                TransactionalState transactionalState = TransactionalState.requiresNew(timeout(timeout), description("test"));

                assertEquals("requiresNew(description=test, timeout=%s)".formatted(timeout), transactionalState.toString());
            }
        }

        @Test
        @DisplayName("with null option")
        void testWithNullOption() {
            TransactionOption option = timeout(Duration.ofMillis(0));
            assertThrows(NullPointerException.class, () -> TransactionalState.requiresNew(option, null));
        }

        @Test
        @DisplayName("with duplicate option type")
        void testWithDuplicateOptionType() {
            TransactionOption option1 = timeout(Duration.ofMillis(0));
            TransactionOption option2 = description("test");
            TransactionOption option3 = timeout(Duration.ofMillis(0));
            assertThrows(IllegalStateException.class, () -> TransactionalState.requiresNew(option1, option2, option3));
        }
    }

    @Nested
    @DisplayName("supports")
    class Supports {

        @Test
        @DisplayName("called without current transaction")
        void testWithoutCurrentTransaction() {
            String expectedResult = UUID.randomUUID().toString();

            String result = TransactionalState.supports().call(() -> {
                assertEquals(Optional.empty(), Transaction.current());

                return expectedResult;
            });

            assertEquals(expectedResult, result);
        }

        @Test
        @DisplayName("called with current transaction")
        void testWithCurrentTransaction() {
            Transaction transaction = createTransaction();

            String expectedResult = UUID.randomUUID().toString();

            String result = RegistryKey.callWithTransaction(transaction, () -> TransactionalState.supports().call(() -> {
                assertEquals(Optional.of(transaction), Transaction.current());

                return expectedResult;
            }));

            assertEquals(expectedResult, result);
        }

        @Test
        @DisplayName("toString")
        void testToString() {
            TransactionalState transactionalState = TransactionalState.supports();

            assertEquals("supports", transactionalState.toString());
        }
    }

    @Nested
    @DisplayName("notSupported")
    class NotSupported {

        @Test
        @DisplayName("called without current transaction")
        void testWithoutCurrentTransaction() {
            String expectedResult = UUID.randomUUID().toString();

            String result = TransactionalState.notSupported().call(() -> {
                assertEquals(Optional.empty(), Transaction.current());

                return expectedResult;
            });

            assertEquals(expectedResult, result);
        }

        @Test
        @DisplayName("called with current transaction")
        void testWithCurrentTransaction() {
            Transaction transaction = createTransaction();

            String expectedResult = UUID.randomUUID().toString();

            String result = RegistryKey.callWithTransaction(transaction, () -> TransactionalState.notSupported().call(() -> {
                assertEquals(Optional.empty(), Transaction.current());

                return expectedResult;
            }));

            assertEquals(expectedResult, result);
        }

        @Test
        @DisplayName("toString")
        void testToString() {
            TransactionalState transactionalState = TransactionalState.notSupported();

            assertEquals("notSupported", transactionalState.toString());
        }
    }

    @Nested
    @DisplayName("never")
    class Never {

        @Test
        @DisplayName("called without current transaction")
        void testWithoutCurrentTransaction() {
            String expectedResult = UUID.randomUUID().toString();

            String result = TransactionalState.never().call(() -> {
                assertEquals(Optional.empty(), Transaction.current());

                return expectedResult;
            });

            assertEquals(expectedResult, result);
        }

        @Test
        @DisplayName("called with current transaction")
        void testWithCurrentTransaction() {
            Transaction transaction = createTransaction();

            TransactionalState transactionalState = TransactionalState.never();

            TransactionalState.Action<?> action = mock();

            TransactionNotAllowedException exception = RegistryKey.callWithTransaction(transaction,
                    () -> assertThrows(TransactionNotAllowedException.class, () -> transactionalState.run(action)));
            assertEquals(Messages.Transaction.transactionNotAllowed(), exception.getMessage());

            verifyNoInteractions(action);
        }

        @Test
        @DisplayName("toString")
        void testToString() {
            TransactionalState transactionalState = TransactionalState.never();

            assertEquals("never", transactionalState.toString());
        }
    }

    @Nested
    @DisplayName("callActionWithNewTransaction")
    class CallActionWithNewTransaction {

        // Use required for testing

        @Test
        @DisplayName("autoCommit and active")
        void testAutoCommitAndActive() {
            MemorySegment handle = mockCreateTransaction(Duration.ofMillis(0), null);
            mockGetTransactionStatus(handle, WinNT.TRANSACTION_OUTCOME.TransactionOutcomeUndetermined);
            mockCommitTransaction(handle);
            mockCloseHandle(handle);

            TransactionalState.required().run(() -> {
                assertNotEquals(Optional.empty(), Transaction.current());
            });

            verify(Transaction.ktmW32).CreateTransaction(isNULL(), isNULL(), anyInt(), anyInt(), anyInt(), eq(0), isNULL(), notNull());
            verify(Transaction.ktmW32).GetTransactionInformation(eq(handle), notNull(), isNULL(), isNULL(), isNULL(), eq(0), isNULL(), notNull());
            verify(Transaction.ktmW32).CommitTransaction(eq(handle), notNull());
            verify(Transaction.kernel32).CloseHandle(eq(handle), notNull());
            verifyNoMoreInteractions(Transaction.ktmW32, Transaction.kernel32);
        }

        @ParameterizedTest
        @DisplayName("autoCommit and not active")
        @ValueSource(ints = {
                WinNT.TRANSACTION_OUTCOME.TransactionOutcomeCommitted,
                WinNT.TRANSACTION_OUTCOME.TransactionOutcomeAborted
        })
        void testAutoCommitAndNotActive(int outcome) {
            MemorySegment handle = mockCreateTransaction(Duration.ofMillis(0), null);
            mockGetTransactionStatus(handle, outcome);
            mockCloseHandle(handle);

            TransactionalState.required().run(() -> {
                assertNotEquals(Optional.empty(), Transaction.current());
            });

            verify(Transaction.ktmW32).CreateTransaction(isNULL(), isNULL(), anyInt(), anyInt(), anyInt(), eq(0), isNULL(), notNull());
            verify(Transaction.ktmW32).GetTransactionInformation(eq(handle), notNull(), isNULL(), isNULL(), isNULL(), eq(0), isNULL(), notNull());
            verify(Transaction.kernel32).CloseHandle(eq(handle), notNull());
            verifyNoMoreInteractions(Transaction.ktmW32, Transaction.kernel32);
        }

        @Test
        @DisplayName("autoCommit and status error")
        void testAutoCommitAndStatusError() {
            MemorySegment handle = mockCreateTransaction(Duration.ofMillis(0), null);
            mockGetTransactionStatus(handle, -1);
            mockCloseHandle(handle);

            TransactionalState.Action<?> action = mock();

            TransactionalState transactionalState = TransactionalState.required();

            IllegalStateException exception = assertThrows(IllegalStateException.class, () -> transactionalState.run(action));
            assertEquals(Messages.Transaction.unsupportedOutcome(-1), exception.getMessage());

            verify(Transaction.ktmW32).CreateTransaction(isNULL(), isNULL(), anyInt(), anyInt(), anyInt(), eq(0), isNULL(), notNull());
            verify(Transaction.ktmW32).GetTransactionInformation(eq(handle), notNull(), isNULL(), isNULL(), isNULL(), eq(0), isNULL(), notNull());
            verify(Transaction.kernel32).CloseHandle(eq(handle), notNull());
            verifyNoMoreInteractions(Transaction.ktmW32, Transaction.kernel32);
        }

        @Test
        @DisplayName("no autoCommit")
        void testNoAutoCommit() {
            MemorySegment handle = mockCreateTransaction(Duration.ofMillis(0), null);
            mockCloseHandle(handle);

            TransactionalState.required().run(() -> {
                Transaction currentTransaction = Transaction.current().orElse(null);
                assertNotNull(currentTransaction);
                currentTransaction.autoCommit(false);
            });

            verify(Transaction.ktmW32).CreateTransaction(isNULL(), isNULL(), anyInt(), anyInt(), anyInt(), eq(0), isNULL(), notNull());
            verify(Transaction.kernel32).CloseHandle(eq(handle), notNull());
            verifyNoMoreInteractions(Transaction.ktmW32, Transaction.kernel32);
        }
    }
}
