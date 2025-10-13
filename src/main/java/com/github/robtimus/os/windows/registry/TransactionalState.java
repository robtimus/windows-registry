/*
 * TransactionalState.java
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

import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.github.robtimus.os.windows.registry.Transaction.Status;

/**
 * The possible transactional states.
 *
 * @author Rob Spoor
 * @since 2.0
 */
public abstract sealed class TransactionalState {

    private static final TransactionalState MANDATORY = new Mandatory();
    private static final TransactionalState REQUIRED = new Required(Map.of());
    private static final TransactionalState REQUIRES_NEW = new RequiresNew(Map.of());
    private static final TransactionalState SUPPORTS = new Supports();
    private static final TransactionalState NOT_SUPPORTED = new NotSupported();
    private static final TransactionalState NEVER = new Never();

    private TransactionalState() {
    }

    /**
     * Returns a transactional state that will cause an exception to be thrown if no current transaction exists.
     *
     * @return A transactional state that will cause an exception to be thrown if no current transaction exists.
     */
    public static TransactionalState mandatory() {
        return MANDATORY;
    }

    /**
     * Returns a transactional state that will use the current transaction if one exists, or create a new one otherwise.
     *
     * @param options The options to use when creating a new transaction. They are ignored if a current transaction exists.
     * @return A transactional state that will use the current transaction if one exists, or create a new one otherwise.
     * @throws NullPointerException If any of the given options is {@code null}.
     * @throws IllegalStateException If the given options contain different occurrences for the same option, e.g. multiple timeouts.
     */
    public static TransactionalState required(TransactionOption... options) {
        return options.length == 0
                ? REQUIRED
                : new Required(toMap(options));
    }

    /**
     * Returns a transactional state that will create a new transaction.
     *
     * @param options The options to use when creating a new transaction.
     * @return A transactional state that will create a new transaction.
     * @throws NullPointerException If any of the given options is {@code null}.
     * @throws IllegalStateException If the given options contain different occurrences for the same option, e.g. multiple timeouts.
     */
    public static TransactionalState requiresNew(TransactionOption... options) {
        return options.length == 0
                ? REQUIRES_NEW
                : new RequiresNew(toMap(options));
    }

    /**
     * Returns a transactional state that will use the current transaction if one exists, or no transaction otherwise.
     *
     * @return A transactional state that will use the current transaction if one exists, or no transaction otherwise.
     */
    public static TransactionalState supports() {
        return SUPPORTS;
    }

    /**
     * Returns a transactional state that will not use any transaction. If a current transaction exists it will be ignored.
     *
     * @return A transactional state that will not use any transaction.
     */
    public static TransactionalState notSupported() {
        return NOT_SUPPORTED;
    }

    /**
     * Returns a transactional state that will cause an exception to be thrown if a current transaction exists.
     *
     * @return A transactional state that will cause an exception to be thrown if a current transaction exists.
     */
    public static TransactionalState never() {
        return NEVER;
    }

    private static Map<Class<? extends TransactionOption>, TransactionOption> toMap(TransactionOption... options) {
        return Arrays.stream(options)
                .collect(Collectors.toMap(TransactionOption::getClass, Function.identity()));
    }

    private static String format(Map<Class<? extends TransactionOption>, TransactionOption> options, String prefix, String postfix) {
        return options.values()
                .stream()
                .map(Object::toString)
                .sorted()
                .collect(Collectors.joining(", ", prefix, postfix)); //$NON-NLS-1$
    }

    /**
     * Runs an action.
     *
     * @param <X> The type of exception thrown by the action.
     * @param action The action to run.
     * @throws NullPointerException If the given action is {@code null}.
     * @throws UnsupportedOperationException If the current Windows version does not support transactions.
     * @throws TransactionException If a required transaction could not be created (optional).
     * @throws TransactionRequiredException If a {@link #mandatory() mandatory transactional state} is used without a current transaction (optional).
     * @throws TransactionNotAllowedException If a {@link #never() transactional state that does not allow transactions} is used with a current
     *         transaction (optional).
     * @throws X If the action completes with an exception.
     * @see RegistryFeature#TRANSACTIONS
     */
    public <X extends Throwable> void run(Action<X> action) throws X {
        Objects.requireNonNull(action);
        call(() -> {
            action.run();
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
     * @throws TransactionException If a required transaction could not be created (optional).
     * @throws TransactionRequiredException If a {@link #mandatory() mandatory transactional state} is used without a current transaction (optional).
     * @throws TransactionNotAllowedException If a {@link #never() transactional state that does not allow transactions} is used with a current
     *         transaction (optional).
     * @throws X If the action completes with an exception.
     * @see RegistryFeature#TRANSACTIONS
     */
    public <R, X extends Throwable> R call(Callable<? extends R, X> action) throws X {
        Objects.requireNonNull(action);

        if (!RegistryFeature.TRANSACTIONS.isEnabled()) {
            throw new UnsupportedOperationException(Messages.RegistryFeature.notEnabled(RegistryFeature.TRANSACTIONS));
        }

        return callAction(action);
    }

    abstract <R, X extends Throwable> R callAction(Callable<? extends R, X> action) throws X;

    <R, X extends Throwable> R callActionWithNewTransaction(Callable<? extends R, X> action,
                                                            Map<Class<? extends TransactionOption>, TransactionOption> options) throws X {

        // Transaction cannot implement AutoCloseable because that would require the close method to be public,
        // so create an AutoCloseable wrapper around it
        interface TransactionCloseable extends AutoCloseable {

            @Override
            void close();

            static TransactionCloseable forTransaction(Transaction transaction) {
                return transaction::close;
            }
        }

        TransactionTimeout timeoutOption = getOption(options, TransactionTimeout.class);
        Duration timeout = timeoutOption != null ? timeoutOption.timeout() : Duration.ZERO;

        TransactionDescription descriptionOption = getOption(options, TransactionDescription.class);
        String description = descriptionOption != null ? descriptionOption.description() : null;

        Transaction transaction = Transaction.create(timeout, description);
        try (var _ = TransactionCloseable.forTransaction(transaction)) {
            return Registry.callWithTransaction(transaction, () -> {
                R result = action.call();
                if (transaction.autoCommit() && transaction.status() == Status.ACTIVE) {
                    transaction.commit();
                }
                return result;
            });
        }
    }

    private <O extends TransactionOption> O getOption(Map<Class<? extends TransactionOption>, TransactionOption> options, Class<O> type) {
        return type.cast(options.get(type));
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
         * @throws X If the action completes with an exception.
         */
        void run() throws X;
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
         * @return The result of the action.
         * @throws X If the action completes with an exception.
         */
        R call() throws X;
    }

    static final class Mandatory extends TransactionalState {

        @Override
        <R, X extends Throwable> R callAction(Callable<? extends R, X> action) throws X {
            if (Registry.currentContext() instanceof Registry.Context.NonTransactional) {
                throw new TransactionRequiredException(Messages.Transaction.transactionRequired());
            }
            return action.call();
        }

        @Override
        @SuppressWarnings("nls")
        public String toString() {
            return "mandatory";
        }
    }

    static final class Required extends TransactionalState {

        private final Map<Class<? extends TransactionOption>, TransactionOption> options;

        private Required(Map<Class<? extends TransactionOption>, TransactionOption> options) {
            this.options = options;
        }

        @Override
        <R, X extends Throwable> R callAction(Callable<? extends R, X> action) throws X {
            if (Registry.currentContext() instanceof Registry.Context.Transactional) {
                return action.call();
            }
            return callActionWithNewTransaction(action, options);
        }

        @Override
        @SuppressWarnings("nls")
        public String toString() {
            return format(options, "required(", ")");
        }
    }

    static final class RequiresNew extends TransactionalState {

        private final Map<Class<? extends TransactionOption>, TransactionOption> options;

        private RequiresNew(Map<Class<? extends TransactionOption>, TransactionOption> options) {
            this.options = options;
        }

        @Override
        <R, X extends Throwable> R callAction(Callable<? extends R, X> action) throws X {
            return callActionWithNewTransaction(action, options);
        }

        @Override
        @SuppressWarnings("nls")
        public String toString() {
            return format(options, "requiresNew(", ")");
        }
    }

    static final class Supports extends TransactionalState {

        @Override
        <R, X extends Throwable> R callAction(Callable<? extends R, X> action) throws X {
            return action.call();
        }

        @Override
        @SuppressWarnings("nls")
        public String toString() {
            return "supports";
        }
    }

    static final class NotSupported extends TransactionalState {

        @Override
        <R, X extends Throwable> R callAction(Callable<? extends R, X> action) throws X {
            return Registry.callWithoutTransaction(action);
        }

        @Override
        @SuppressWarnings("nls")
        public String toString() {
            return "notSupported";
        }
    }

    static final class Never extends TransactionalState {

        @Override
        <R, X extends Throwable> R callAction(Callable<? extends R, X> action) throws X {
            if (Registry.currentContext() instanceof Registry.Context.Transactional) {
                throw new TransactionNotAllowedException(Messages.Transaction.transactionNotAllowed());
            }
            return action.call();
        }

        @Override
        @SuppressWarnings("nls")
        public String toString() {
            return "never";
        }
    }
}
