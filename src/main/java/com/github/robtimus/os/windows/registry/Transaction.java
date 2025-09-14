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

import static com.github.robtimus.os.windows.registry.foreign.ForeignUtils.allocateInt;
import static com.github.robtimus.os.windows.registry.foreign.ForeignUtils.getInt;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.IntFunction;
import com.github.robtimus.os.windows.registry.foreign.CaptureState;
import com.github.robtimus.os.windows.registry.foreign.Kernel32;
import com.github.robtimus.os.windows.registry.foreign.KtmTypes;
import com.github.robtimus.os.windows.registry.foreign.KtmW32;
import com.github.robtimus.os.windows.registry.foreign.WString;
import com.github.robtimus.os.windows.registry.foreign.WinDef.HANDLE;
import com.github.robtimus.os.windows.registry.foreign.WinError;
import com.github.robtimus.os.windows.registry.foreign.WinNT;

/**
 * A representation of transactions for working with the Windows registry.
 * Transaction can be started or by-passed using {@link TransactionalState}.
 *
 * @author Rob Spoor
 * @since 2.0
 */
public final class Transaction {

    static KtmW32 ktmW32 = KtmW32.INSTANCE;
    static Kernel32 kernel32 = Kernel32.INSTANCE;

    private final MemorySegment handle;
    private final Duration timeout;
    private final String description;

    private boolean autoCommit;

    private Transaction(MemorySegment handle, Duration timeout, String description) {
        this.handle = handle;
        this.timeout = timeout;
        this.description = description;

        this.autoCommit = true;
    }

    MemorySegment handle() {
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
        return status(Status::name, _ -> unknownStatus, _ -> unknownStatus);
    }

    private <T> T status(Function<Status, T> statusMapper, IntFunction<T> errorMapper, IntFunction<T> invalidValueMapper) {
        try (Arena allocator = Arena.ofConfined()) {
            MemorySegment outcome = allocateInt(allocator);
            MemorySegment captureState = CaptureState.allocate(allocator);
            boolean result = ktmW32.GetTransactionInformation(
                    handle,
                    outcome,
                    MemorySegment.NULL,
                    MemorySegment.NULL,
                    MemorySegment.NULL,
                    0,
                    MemorySegment.NULL,
                    captureState);
            if (!result) {
                int errorCode = CaptureState.getLastError(captureState);
                if (errorCode == WinError.ERROR_INVALID_HANDLE) {
                    return statusMapper.apply(Status.CLOSED);
                }
                return errorMapper.apply(errorCode);
            }
            return switch (getInt(outcome)) {
                case WinNT.TRANSACTION_OUTCOME.TransactionOutcomeUndetermined -> statusMapper.apply(Status.ACTIVE);
                case WinNT.TRANSACTION_OUTCOME.TransactionOutcomeCommitted -> statusMapper.apply(Status.COMMITTED);
                case WinNT.TRANSACTION_OUTCOME.TransactionOutcomeAborted -> statusMapper.apply(Status.ROLLED_BACK);
                default -> invalidValueMapper.apply(getInt(outcome));
            };
        }
    }

    /**
     * Returns whether the transaction will be automatically committed when it ends. The default is {@code true}.
     *
     * @return {@code true} if the transaction will be automatically committed when it ends, or {@code false} otherwise.
     */
    public boolean autoCommit() {
        return autoCommit;
    }

    /**
     * Sets whether to automatically commit the transaction when it ends. The default is {@code true}.
     *
     * @param autoCommit {@code true} to automatically commit the transaction when it ends, or {@code false} otherwise.
     */
    public void autoCommit(boolean autoCommit) {
        this.autoCommit = autoCommit;
    }

    /**
     * Commits the transaction.
     *
     * @throws TransactionException If the transaction could not be committed.
     */
    public void commit() {
        try (Arena allocator = Arena.ofConfined()) {
            MemorySegment captureState = CaptureState.allocate(allocator);
            if (!ktmW32.CommitTransaction(handle, captureState)) {
                throw new TransactionException(CaptureState.getLastError(captureState));
            }
        }
    }

    /**
     * Rolls back the transaction.
     *
     * @throws TransactionException If the transaction could not be rolled back.
     */
    public void rollback() {
        try (Arena allocator = Arena.ofConfined()) {
            MemorySegment captureState = CaptureState.allocate(allocator);
            if (!ktmW32.RollbackTransaction(handle, captureState)) {
                throw new TransactionException(CaptureState.getLastError(captureState));
            }
        }
    }

    @Override
    @SuppressWarnings("nls")
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Transaction[timeout=").append(timeout);
        if (description != null) {
            sb.append(",description=").append(description);
        }
        String statusString = statusString();
        sb.append(",status=").append(statusString);
        if (Status.ACTIVE.name().equals(statusString)) {
            sb.append(",autoCommit=").append(autoCommit);
        }
        sb.append(']');
        return sb.toString();
    }

    static Transaction create(Duration timeout, String description) {
        int timeoutInMillis = Math.toIntExact(timeout.toMillis());

        try (Arena allocator = Arena.ofConfined()) {
            MemorySegment captureState = CaptureState.allocate(allocator);
            MemorySegment handle = ktmW32.CreateTransaction(
                    MemorySegment.NULL,
                    MemorySegment.NULL,
                    KtmTypes.TRANSACTION_DO_NOT_PROMOTE,
                    0,
                    0,
                    timeoutInMillis,
                    description == null ? MemorySegment.NULL : WString.allocate(allocator, description),
                    captureState);
            if (HANDLE.isInvalid(handle)) {
                throw new TransactionException(CaptureState.getLastError(captureState));
            }
            return new Transaction(handle, timeout, description);
        }
    }

    // Keep this method package private so it cannot be called inside transactional actions
    // It means Transaction cannot implement AutoCloseable
    void close() {
        try (Arena allocator = Arena.ofConfined()) {
            MemorySegment captureState = CaptureState.allocate(allocator);
            if (!kernel32.CloseHandle(handle, captureState)) {
                throw new TransactionException(CaptureState.getLastError(captureState));
            }
        }
    }

    /**
     * Returns the current transaction, if one exists.
     *
     * @return An {@link Optional} describing the current transaction if one exists, or {@link Optional#empty()} otherwise.
     */
    public static Optional<Transaction> current() {
        return Registry.currentContext() instanceof Registry.Context.Transactional transactionalContext
                ? Optional.of(transactionalContext.transaction())
                : Optional.empty();
    }

    /**
     * The possible transaction statuses.
     *
     * @author Rob Spoor
     * @since 2.0
     */
    public enum Status {
        /** Indicates the transaction is still active. */
        ACTIVE,

        /** Indicates the transaction has been committed. */
        COMMITTED,

        /** Indicates the transaction has been rolled back. */
        ROLLED_BACK,

        /** Indicates the transaction has been closed. */
        CLOSED,
    }
}
