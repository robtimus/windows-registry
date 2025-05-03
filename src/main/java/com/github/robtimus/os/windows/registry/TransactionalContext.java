/*
 * TransactionalContext.java
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

import java.time.Duration;
import java.util.Objects;

/**
 * An object representing a state with an active transaction. Transactions will only be started when {@link #run(Action)} or {@link #call(Callable)}
 * is called, and automatically closed when the called method ends.
 *
 * @author Rob Spoor
 * @since 2.0
 */
public final class TransactionalContext {

    private Duration timeout;
    private String description;

    TransactionalContext() {
        timeout = Duration.ZERO;
        description = null;
    }

    /**
     * Sets the transaction timeout.
     * Use a {@link Duration#isZero() zero} or {@link Duration#isNegative() negative} duration for an infinite timeout.
     *
     * @param timeout The transaction timeout to set.
     * @return This object.
     * @throws NullPointerException If the given timeout is {@code null}.
     */
    public TransactionalContext withTimeout(Duration timeout) {
        this.timeout = Objects.requireNonNull(timeout);
        return this;
    }

    /**
     * Sets an infinite transaction timeout.
     * This method is shorthand for calling {@link #withTimeout(Duration)} with {@link Duration#ZERO}.
     *
     * @return This object.
     */
    public TransactionalContext withInfiniteTimeout() {
        return withTimeout(Duration.ZERO);
    }

    /**
     * Sets the transaction description.
     *
     * @param description The optional transaction description.
     * @return This object.
     */
    public TransactionalContext withDescription(String description) {
        this.description = description;
        return this;
    }

    /**
     * Runs an action.
     *
     * @param <X> The type of exception thrown by the action.
     * @param action The action to run.
     * @throws NullPointerException If the given action is {@code null}.
     * @throws UnsupportedOperationException If the current Windows version does not support transactions.
     * @throws TransactionException If no transaction could be created.
     * @throws X If the action completes with an exception.
     * @see RegistryFeature#TRANSACTIONS
     */
    public <X extends Throwable> void run(Action<X> action) throws X {
        Objects.requireNonNull(action);
        call(transaction -> {
            action.run(transaction);
            return null;
        });
    }

    /**
     * Runs an action.
     *
     * @param <R> The action's result type.
     * @param <X> The type of exception thrown by the action.
     * @param action The action to run.
     * @return The result of the action.
     * @throws NullPointerException If the given action is {@code null}.
     * @throws UnsupportedOperationException If the current Windows version does not support transactions.
     * @throws TransactionException If no transaction could be created.
     * @throws X If the action completes with an exception.
     * @see RegistryFeature#TRANSACTIONS
     */
    public <R, X extends Throwable> R call(Callable<? extends R, X> action) throws X {
        Objects.requireNonNull(action);

        if (!RegistryFeature.TRANSACTIONS.isEnabled()) {
            throw new UnsupportedOperationException(Messages.RegistryFeature.notEnabled(RegistryFeature.TRANSACTIONS));
        }

        // Transaction cannot implement AutoCloseable because that would require the close method to be public,
        // so create an AutoCloseable wrapper around it
        final class CloseableTransaction implements AutoCloseable {

            private final Transaction transaction;

            private CloseableTransaction(Transaction transaction) {
                this.transaction = transaction;
            }

            @Override
            public void close() {
                transaction.close();
            }
        }

        Transaction transaction = Transaction.create(timeout, description);
        try (var _ = new CloseableTransaction(transaction)) {
            return RegistryKey.callWithTransaction(transaction, action::call);
        }
    }

    /**
     * A transactional action.
     *
     * @author Rob Spoor
     * @param <X> The type of exception thrown by the action.
     * @since 2.0
     */
    public interface Action<X extends Throwable> {

        /**
         * Runs the transactional action.
         *
         * @param transaction The current transaction.
         * @throws X If the action completes with an exception.
         */
        void run(Transaction transaction) throws X;
    }

    /**
     * A transactional action that returns a result.
     *
     * @author Rob Spoor
     * @param <R> The result type.
     * @param <X> The type of exception thrown by the action.
     * @since 2.0
     */
    public interface Callable<R, X extends Throwable> {

        /**
         * Runs the transactional action.
         *
         * @param transaction The current transaction.
         * @return The result of the action.
         * @throws X If the action completes with an exception.
         */
        R call(Transaction transaction) throws X;
    }
}
