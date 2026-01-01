/*
 * TransactionException.java
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

import com.github.robtimus.os.windows.registry.foreign.Kernel32;

/**
 * Thrown when an error occurred while trying to use a transaction.
 *
 * @author Rob Spoor
 * @since 2.0
 */
@SuppressWarnings("serial")
public class TransactionException extends RuntimeException {

    private final int errorCode;

    /**
     * Creates a new exception.
     *
     * @param errorCode The error code that was returned from the Windows API.
     */
    public TransactionException(int errorCode) {
        super(createMessage(errorCode));
        this.errorCode = errorCode;
    }

    private static String createMessage(int errorCode) {
        return Kernel32.formatMessage(errorCode);
    }

    /**
     * Returns the error code that was returned from the Windows API.
     *
     * @return The error code that was returned from the Windows API.
     */
    public int errorCode() {
        return errorCode;
    }
}
