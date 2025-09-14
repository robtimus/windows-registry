/*
 * TransactionTest.java
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

import static com.github.robtimus.os.windows.registry.foreign.ForeignTestUtils.ALLOCATOR;
import static com.github.robtimus.os.windows.registry.foreign.ForeignTestUtils.eqPointer;
import static com.github.robtimus.os.windows.registry.foreign.ForeignTestUtils.isNULL;
import static com.github.robtimus.os.windows.registry.foreign.ForeignTestUtils.setLastError;
import static com.github.robtimus.os.windows.registry.foreign.ForeignUtils.setInt;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.lang.foreign.MemorySegment;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import com.github.robtimus.os.windows.registry.foreign.Kernel32Utils;
import com.github.robtimus.os.windows.registry.foreign.KtmTypes;
import com.github.robtimus.os.windows.registry.foreign.WinError;
import com.github.robtimus.os.windows.registry.foreign.WinNT;

@SuppressWarnings("nls")
class TransactionTest extends TransactionTestBase {

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("null description")
        void testNullDescription() {
            Duration timeout = Duration.ofMillis(100);
            MemorySegment handle = ALLOCATOR.allocate(0);

            when(Transaction.ktmW32.CreateTransaction(isNULL(), isNULL(), anyInt(), anyInt(), anyInt(), anyInt(), isNULL(), notNull()))
                    .thenReturn(handle);

            Transaction transaction = Transaction.create(timeout, null);

            assertSame(handle, transaction.handle());
            assertEquals(timeout, transaction.timeout());
            assertEquals(Optional.empty(), transaction.description());

            verify(Transaction.ktmW32)
                    .CreateTransaction(isNULL(), isNULL(), eq(KtmTypes.TRANSACTION_DO_NOT_PROMOTE), eq(0), eq(0), eq(100), isNULL(), notNull());
        }

        @Test
        @DisplayName("non-null description")
        void testNonNullDescription() {
            Duration timeout = Duration.ofMillis(100);
            MemorySegment handle = ALLOCATOR.allocate(0);

            when(Transaction.ktmW32.CreateTransaction(isNULL(), isNULL(), anyInt(), anyInt(), anyInt(), anyInt(), eqPointer("test"), notNull()))
                    .thenReturn(handle);

            Transaction transaction = Transaction.create(timeout, "test");

            assertSame(handle, transaction.handle());
            assertEquals(timeout, transaction.timeout());
            assertEquals(Optional.of("test"), transaction.description());

            verify(Transaction.ktmW32)
                    .CreateTransaction(isNULL(), isNULL(), eq(KtmTypes.TRANSACTION_DO_NOT_PROMOTE), eq(0), eq(0), eq(100), eqPointer("test"),
                            notNull());
        }

        @Test
        @DisplayName("invalid handle")
        void testInvalidHandle() {
            Duration timeout = Duration.ofMillis(100);
            MemorySegment handle = MemorySegment.ofAddress(-1);

            when(Transaction.ktmW32.CreateTransaction(isNULL(), isNULL(), anyInt(), anyInt(), anyInt(), anyInt(), notNull(), notNull()))
                    .thenAnswer(i -> {
                        MemorySegment captureState = i.getArgument(7);
                        setLastError(captureState, WinError.ERROR_INVALID_HANDLE);

                        return handle;
                    });

            TransactionException exception = assertThrows(TransactionException.class, () -> Transaction.create(timeout, null));
            assertEquals(WinError.ERROR_INVALID_HANDLE, exception.errorCode());
            assertEquals(Kernel32Utils.formatMessage(WinError.ERROR_INVALID_HANDLE), exception.getMessage());
        }
    }

    @Nested
    @DisplayName("status")
    class Status {

        private Transaction transaction;

        @BeforeEach
        void setup() {
            transaction = createTransaction();
        }

        @ParameterizedTest
        @DisplayName("valid status")
        @CsvSource({
                "1, ACTIVE",
                "2, COMMITTED",
                "3, ROLLED_BACK"
        })
        void testValidStatus(int outcome, Transaction.Status expected) {
            when(Transaction.ktmW32.GetTransactionInformation(eq(transaction.handle()), notNull(), isNULL(), isNULL(), isNULL(), eq(0), isNULL(),
                    notNull()))
                    .thenAnswer(i -> {
                        MemorySegment outcomeSegment = i.getArgument(1);
                        setInt(outcomeSegment, outcome);

                        return true;
                    });

            Transaction.Status actual = transaction.status();

            assertEquals(expected, actual);
        }

        @Test
        @DisplayName("unknown status")
        void testUnknownStatus() {
            when(Transaction.ktmW32.GetTransactionInformation(eq(transaction.handle()), notNull(), isNULL(), isNULL(), isNULL(), eq(0), isNULL(),
                    notNull()))
                    .thenAnswer(i -> {
                        MemorySegment outcomeSegment = i.getArgument(1);
                        setInt(outcomeSegment, -1);

                        return true;
                    });

            IllegalStateException exception = assertThrows(IllegalStateException.class, transaction::status);
            assertEquals(Messages.Transaction.unsupportedOutcome(-1), exception.getMessage());
        }

        @Test
        @DisplayName("closed")
        void testClosed() {
            when(Transaction.ktmW32.GetTransactionInformation(eq(transaction.handle()), notNull(), isNULL(), isNULL(), isNULL(), eq(0), isNULL(),
                    notNull()))
                    .thenAnswer(i -> {
                        MemorySegment captureState = i.getArgument(7);
                        setLastError(captureState, WinError.ERROR_INVALID_HANDLE);

                        return false;
                    });

            Transaction.Status actual = transaction.status();

            assertEquals(Transaction.Status.CLOSED, actual);
        }

        @Test
        @DisplayName("non-closed failure")
        void testNonClosedError() {
            when(Transaction.ktmW32.GetTransactionInformation(eq(transaction.handle()), notNull(), isNULL(), isNULL(), isNULL(), eq(0), isNULL(),
                    notNull()))
                    .thenAnswer(i -> {
                        MemorySegment captureState = i.getArgument(7);
                        setLastError(captureState, WinError.ERROR_ACCESS_DENIED);

                        return false;
                    });

            TransactionException exception = assertThrows(TransactionException.class, transaction::status);
            assertEquals(WinError.ERROR_ACCESS_DENIED, exception.errorCode());
            assertEquals(Kernel32Utils.formatMessage(WinError.ERROR_ACCESS_DENIED), exception.getMessage());
        }
    }

    @Nested
    @DisplayName("commit")
    class Commit {

        private Transaction transaction;

        @BeforeEach
        void setup() {
            transaction = createTransaction();
        }

        @Test
        @DisplayName("success")
        void testSuccess() {
            when(Transaction.ktmW32.CommitTransaction(eq(transaction.handle()), notNull())).thenReturn(true);

            assertDoesNotThrow(transaction::commit);

            verify(Transaction.ktmW32).CommitTransaction(eq(transaction.handle()), notNull());
        }

        @Test
        @DisplayName("failure")
        void testFailure() {
            when(Transaction.ktmW32.CommitTransaction(eq(transaction.handle()), notNull())).thenAnswer(i -> {
                MemorySegment captureState = i.getArgument(1);
                setLastError(captureState, WinError.ERROR_INVALID_HANDLE);

                return false;
            });

            TransactionException exception = assertThrows(TransactionException.class, transaction::commit);
            assertEquals(WinError.ERROR_INVALID_HANDLE, exception.errorCode());
            assertEquals(Kernel32Utils.formatMessage(WinError.ERROR_INVALID_HANDLE), exception.getMessage());

            verify(Transaction.ktmW32).CommitTransaction(eq(transaction.handle()), notNull());
        }
    }

    @Nested
    @DisplayName("rollback")
    class Rollback {

        private Transaction transaction;

        @BeforeEach
        void setup() {
            transaction = createTransaction();
        }

        @Test
        @DisplayName("success")
        void testSuccess() {
            when(Transaction.ktmW32.RollbackTransaction(eq(transaction.handle()), notNull())).thenReturn(true);

            assertDoesNotThrow(transaction::rollback);

            verify(Transaction.ktmW32).RollbackTransaction(eq(transaction.handle()), notNull());
        }

        @Test
        @DisplayName("failure")
        void testFailure() {
            when(Transaction.ktmW32.RollbackTransaction(eq(transaction.handle()), notNull())).thenAnswer(i -> {
                MemorySegment captureState = i.getArgument(1);
                setLastError(captureState, WinError.ERROR_INVALID_HANDLE);

                return false;
            });

            TransactionException exception = assertThrows(TransactionException.class, transaction::rollback);
            assertEquals(WinError.ERROR_INVALID_HANDLE, exception.errorCode());
            assertEquals(Kernel32Utils.formatMessage(WinError.ERROR_INVALID_HANDLE), exception.getMessage());

            verify(Transaction.ktmW32).RollbackTransaction(eq(transaction.handle()), notNull());
        }
    }

    @Nested
    @DisplayName("toString")
    class ToString {

        @ParameterizedTest
        @DisplayName("active")
        @ValueSource(booleans = { true, false })
        void testActiveStatus(boolean autoCommit) {
            Transaction transaction = createTransaction();
            transaction.autoCommit(autoCommit);
            assertEquals(autoCommit, transaction.autoCommit());

            when(Transaction.ktmW32.GetTransactionInformation(eq(transaction.handle()), notNull(), isNULL(), isNULL(), isNULL(), eq(0), isNULL(),
                    notNull()))
                    .thenAnswer(i -> {
                        MemorySegment outcomeSegment = i.getArgument(1);
                        setInt(outcomeSegment, WinNT.TRANSACTION_OUTCOME.TransactionOutcomeUndetermined);

                        return true;
                    });

            String actual = transaction.toString();

            String expected = "Transaction[timeout=%s,status=ACTIVE,autoCommit=%b]".formatted(transaction.timeout(), autoCommit);

            assertEquals(expected, actual);
        }

        @ParameterizedTest
        @DisplayName("valid status")
        @CsvSource({
                "2, COMMITTED",
                "3, ROLLED_BACK"
        })
        void testValidStatus(int outcome, Transaction.Status expectedStatus) {
            Transaction transaction = createTransaction();

            when(Transaction.ktmW32.GetTransactionInformation(eq(transaction.handle()), notNull(), isNULL(), isNULL(), isNULL(), eq(0), isNULL(),
                    notNull()))
                    .thenAnswer(i -> {
                        MemorySegment outcomeSegment = i.getArgument(1);
                        setInt(outcomeSegment, outcome);

                        return true;
                    });

            String actual = transaction.toString();

            String expected = "Transaction[timeout=%s,status=%s]".formatted(transaction.timeout(), expectedStatus);

            assertEquals(expected, actual);
        }

        @Test
        @DisplayName("unknown status")
        void testUnknownStatus() {
            Transaction transaction = createTransaction();

            when(Transaction.ktmW32.GetTransactionInformation(eq(transaction.handle()), notNull(), isNULL(), isNULL(), isNULL(), eq(0), isNULL(),
                    notNull()))
                    .thenAnswer(i -> {
                        MemorySegment outcomeSegment = i.getArgument(1);
                        setInt(outcomeSegment, -1);

                        return true;
                    });

            String actual = transaction.toString();

            String expected = "Transaction[timeout=%s,status=UNKNOWN]".formatted(transaction.timeout());

            assertEquals(expected, actual);
        }

        @Test
        @DisplayName("closed")
        void testClosed() {
            Transaction transaction = createTransaction();

            when(Transaction.ktmW32.GetTransactionInformation(eq(transaction.handle()), notNull(), isNULL(), isNULL(), isNULL(), eq(0), isNULL(),
                    notNull()))
                    .thenAnswer(i -> {
                        MemorySegment captureState = i.getArgument(7);
                        setLastError(captureState, WinError.ERROR_INVALID_HANDLE);

                        return false;
                    });
            String actual = transaction.toString();

            String expected = "Transaction[timeout=%s,status=CLOSED]".formatted(transaction.timeout());

            assertEquals(expected, actual);
        }

        @Test
        @DisplayName("non-closed error")
        void testNonClosedError() {
            Transaction transaction = createTransaction();

            when(Transaction.ktmW32.GetTransactionInformation(eq(transaction.handle()), notNull(), isNULL(), isNULL(), isNULL(), eq(0), isNULL(),
                    notNull()))
                    .thenAnswer(i -> {
                        MemorySegment captureState = i.getArgument(7);
                        setLastError(captureState, WinError.ERROR_ACCESS_DENIED);

                        return false;
                    });

            String actual = transaction.toString();

            String expected = "Transaction[timeout=%s,status=UNKNOWN]".formatted(transaction.timeout());

            assertEquals(expected, actual);
        }

        @Test
        @DisplayName("non-null description")
        void testNonNullDescription() {
            Transaction transaction = createTransaction("test");

            when(Transaction.ktmW32.GetTransactionInformation(eq(transaction.handle()), notNull(), isNULL(), isNULL(), isNULL(), eq(0), isNULL(),
                    notNull()))
                    .thenAnswer(i -> {
                        MemorySegment outcomeSegment = i.getArgument(1);
                        setInt(outcomeSegment, WinNT.TRANSACTION_OUTCOME.TransactionOutcomeCommitted);

                        return true;
                    });

            String actual = transaction.toString();

            String expected = "Transaction[timeout=%s,description=test,status=COMMITTED]".formatted(transaction.timeout());

            assertEquals(expected, actual);
        }
    }

    @Nested
    @DisplayName("close")
    class Close {

        private Transaction transaction;

        @BeforeEach
        void setup() {
            transaction = createTransaction();
        }

        @Test
        @DisplayName("success")
        void testSuccess() {
            when(Transaction.kernel32.CloseHandle(eq(transaction.handle()), notNull())).thenReturn(true);

            assertDoesNotThrow(transaction::close);

            verify(Transaction.kernel32).CloseHandle(eq(transaction.handle()), notNull());
        }

        @Test
        @DisplayName("failure")
        void testFailure() {
            when(Transaction.kernel32.CloseHandle(eq(transaction.handle()), notNull())).thenAnswer(i -> {
                MemorySegment captureState = i.getArgument(1);
                setLastError(captureState, WinError.ERROR_INVALID_HANDLE);

                return false;
            });

            TransactionException exception = assertThrows(TransactionException.class, transaction::close);
            assertEquals(WinError.ERROR_INVALID_HANDLE, exception.errorCode());
            assertEquals(Kernel32Utils.formatMessage(WinError.ERROR_INVALID_HANDLE), exception.getMessage());

            verify(Transaction.kernel32).CloseHandle(eq(transaction.handle()), notNull());
        }
    }

    @Nested
    @DisplayName("current")
    class Current {

        private Transaction transaction;

        @BeforeEach
        void setup() {
            transaction = createTransaction();
        }

        @Test
        @DisplayName("no current transaction initially")
        void testNoCurrentTransactionInitially() {
            assertEquals(Optional.empty(), Transaction.current());
        }

        @Test
        @DisplayName("current transaction exists when running with transaction")
        void testCurrentTransactionExistsWhenRunningWithTransaction() {
            Registry.callWithTransaction(transaction, () -> {
                assertEquals(Optional.of(transaction), Transaction.current());
                return null;
            });
        }

        @Test
        @DisplayName("current transaction does not exists when transaction is paused")
        void testCurrentTransactionDoesNotExistsWhenTransactionIsPaused() {
            Registry.callWithTransaction(transaction, () -> Registry.callWithoutTransaction(() -> {
                assertEquals(Optional.empty(), Transaction.current());
                return null;
            }));
        }
    }
}
