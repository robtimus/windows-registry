/*
 * TransactionRequiredException.java
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

/**
 * Thrown when a {@link TransactionalState#mandatory() mandatory transactional state} is used without a current transaction.
 *
 * @author Rob Spoor
 * @since 2.0
 */
@SuppressWarnings("serial")
public class TransactionRequiredException extends RuntimeException {

    /**
     * Creates a new exception.
     *
     * @param message The exception message.
     */
    public TransactionRequiredException(String message) {
        super(message);
    }
}
