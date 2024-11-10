/*
 * Transaction.java
 * Copyright 2024 Rob Spoor
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

import java.lang.foreign.Arena;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.IntFunction;
import com.github.robtimus.os.windows.registry.foreign.IntPointer;
import com.github.robtimus.os.windows.registry.foreign.Kernel32;
import com.github.robtimus.os.windows.registry.foreign.KtmTypes;
import com.github.robtimus.os.windows.registry.foreign.KtmW32;
import com.github.robtimus.os.windows.registry.foreign.StringPointer;
import com.github.robtimus.os.windows.registry.foreign.WinDef.HANDLE;
import com.github.robtimus.os.windows.registry.foreign.WinError;
import com.github.robtimus.os.windows.registry.foreign.WinNT;

/**
 * A representation of transactions for working with the Windows registry.
 *
 * @author Rob Spoor
 * @since 2.0
 */
public final class Transaction {

    static KtmW32 ktmW32 = KtmW32.INSTANCE;
    static Kernel32 kernel32 = Kernel32.INSTANCE;

    private final HANDLE handle;
    private final Duration timeout;
    private final String description;

    private Transaction(HANDLE handle, Duration timeout, String description) {
        this.handle = handle;
        this.timeout = timeout;
        this.description = description;
    }

    HANDLE handle() {
        return handle;
    }

    /**
     * Returns the transaction timeout.
     *
     * @return The transaction timeout. If {@link Duration#isZero() zero} there is an infinite timeout.
     */
    public Duration timeout() {
        return timeout;
    }

    /**
     * Returns the transaction description.
     *
     * @return An {@link Optional} describing the transaction description, or {@link Optional#empty()} if the transaction has no description.
     */
    public Optional<String> description() {
        return Optional.ofNullable(description);
    }

    /**
     * Returns the transaction status.
     *
     * @return The transaction status.
     * @throws TransactionException If the transaction status could not be determined.
     */
    public Status status() {
        return status(Function.identity(), errorCode -> {
            throw new TransactionException(errorCode);
        }, value -> {
            throw new IllegalStateException(Messages.Transaction.unsupportedOutcome(value));
        });
    }

    private String statusString() {
        final String unknownStatus = "UNKNOWN"; //$NON-NLS-1$
        return status(Status::name, code -> unknownStatus, value -> unknownStatus);
    }

    private <T> T status(Function<Status, T> statusMapper, IntFunction<T> errorMapper, IntFunction<T> invalidValueMapper) {
        try (Arena arena = Arena.ofConfined()) {
            IntPointer outcome = IntPointer.uninitialized(arena);
            if (!ktmW32.GetTransactionInformation(handle, outcome, null, null, null, 0, null)) {
                int errorCode = getLastError();
                if (errorCode == WinError.ERROR_INVALID_HANDLE) {
                    return statusMapper.apply(Status.CLOSED);
                }
                return errorMapper.apply(errorCode);
            }
            return switch (outcome.value()) {
                case WinNT.TRANSACTION_OUTCOME.TransactionOutcomeUndetermined -> statusMapper.apply(Status.ACTIVE);
                case WinNT.TRANSACTION_OUTCOME.TransactionOutcomeCommitted -> statusMapper.apply(Status.COMMITTED);
                case WinNT.TRANSACTION_OUTCOME.TransactionOutcomeAborted -> statusMapper.apply(Status.ROLLED_BACK);
                default -> invalidValueMapper.apply(outcome.value());
            };
        }
    }

    /**
     * Commits the transaction.
     *
     * @throws TransactionException If the transaction could not be committed.
     */
    public void commit() {
        if (!ktmW32.CommitTransaction(handle)) {
            // Tests have shown a 0 result if called immediately; add some tries
            throw new TransactionException(getLastError());
        }
    }

    /**
     * Rolls back the transaction.
     *
     * @throws TransactionException If the transaction could not be rolled back.
     */
    public void rollback() {
        if (!ktmW32.RollbackTransaction(handle)) {
            // Tests have shown a 0 result if called immediately; add some tries
            throw new TransactionException(getLastError());
        }
    }

    private int getLastError() {
        for (int i = 0; i < 3; i++) {
            int error = kernel32.GetLastError();
            if (error != 0) {
                return error;
            }
        }
        return 0;
    }

    @Override
    @SuppressWarnings("nls")
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Transaction[timeout=").append(timeout);
        if (description != null) {
            sb.append(",description=").append(description);
        }
        sb.append(",status=").append(statusString());
        sb.append(']');
        return sb.toString();
    }

    static Transaction create(Duration timeout, String description) {
        int timeoutInMillis = Math.toIntExact(timeout.toMillis());

        try (Arena allocator = Arena.ofConfined()) {
            HANDLE handle = ktmW32.CreateTransaction(null, null, KtmTypes.TRANSACTION_DO_NOT_PROMOTE, 0, 0, timeoutInMillis,
                    StringPointer.withNullableValue(description, allocator));
            if (handle.isInvalid()) {
                throw new TransactionException(kernel32.GetLastError());
            }
            return new Transaction(handle, timeout, description);
        }
    }

    // Keep this method package private so it cannot be called inside transactional actions
    // It means Transaction cannot implement AutoCloseable
    void close() {
        if (!kernel32.CloseHandle(handle)) {
            throw new TransactionException(kernel32.GetLastError());
        }
    }

    /**
     * The possible transaction statuses.
     *
     * @author Rob Spoor
     * @since 2.0
     */
    public enum Status {
        /** Indicates the transaction is still active. */
        ACTIVE(),

        /** Indicates the transaction has been committed. */
        COMMITTED,

        /** Indicates the transaction has been rolled back. */
        ROLLED_BACK,

        /** Indicates the transaction has been closed. */
        CLOSED,
    }
}
