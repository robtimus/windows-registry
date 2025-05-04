/*
 * TransactionOption.java
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
import java.util.Objects;

/**
 * An option that can be used when creating transactions.
 *
 * @author Rob Spoor
 * @since 2.0
 */
public sealed interface TransactionOption permits TransactionTimeout, TransactionDescription {

    /**
     * Returns an option that specifies the transaction timeout.
     *
     * @param timeout The transaction timeout. Use a zero value for an infinite timeout.
     * @return An option that specifies the transaction timeout.
     * @throws NullPointerException If the given timeout is {@code null}.
     * @throws IllegalArgumentException If the given timeout is negative.
     */
    static TransactionOption timeout(Duration timeout) {
        if (timeout.isNegative()) {
            throw new IllegalArgumentException(Messages.Transaction.negativeTimeout(timeout));
        }
        return new TransactionTimeout(timeout);
    }

    /**
     * Returns an option that specifies the transaction timeout. This method is an alias for {@link #timeout(Duration)} with a zero duration.
     *
     * @return An option that specifies the transaction timeout.
     */
    static TransactionOption infiniteTimeout() {
        return new TransactionTimeout(Duration.ZERO);
    }

    /**
     * Returns an option that specifies the transaction description.
     *
     * @param description The transaction description.
     * @return An option that specifies the transaction description.
     * @throws NullPointerException If the given description is {@code null}.
     */
    static TransactionOption description(String description) {
        Objects.requireNonNull(description);
        return new TransactionDescription(description);
    }
}
