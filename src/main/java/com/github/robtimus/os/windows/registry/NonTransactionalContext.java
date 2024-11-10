/*
 * NonTransactionalContext.java
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

import java.util.Objects;

/**
 * An object representing a state without active transaction.
 *
 * @author Rob Spoor
 * @since 2.0
 */
public final class NonTransactionalContext {

    /**
     * Runs an action.
     *
     * @param <X> The type of exception thrown by the action.
     * @param action The action to run.
     * @throws NullPointerException If the given action is {@code null}.
     * @throws X If the action completes with an exception.
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
     * @throws X If the action completes with an exception.
     */
    public <R, X extends Throwable> R call(Callable<? extends R, X> action) throws X {
        Objects.requireNonNull(action);
        return RegistryKey.callWithoutTransaction(action);
    }

    /**
     * A non-transactional action.
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
     * A non-transactional action that returns a result.
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
}
