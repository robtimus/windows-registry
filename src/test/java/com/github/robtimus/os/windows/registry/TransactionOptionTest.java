/*
 * TransactionOptionTest.java
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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@SuppressWarnings("nls")
class TransactionOptionTest {

    @Nested
    @DisplayName("timeout")
    class Timeout {

        @Test
        @DisplayName("null timeout")
        void testNullTimeout() {
            assertThrows(NullPointerException.class, () -> timeout(null));
        }

        @Test
        @DisplayName("negative timeout")
        void testNegativeTimeout() {
            Duration timeout = Duration.ofNanos(-1);

            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> timeout(timeout));
            assertEquals(Messages.Transaction.negativeTimeout(timeout), exception.getMessage());
        }

        @ParameterizedTest
        @DisplayName("non-negative timeout")
        @ValueSource(ints = { 0, 100 })
        void testNonNegativeTimeout(int millis) {
            Duration timeout = Duration.ofMillis(millis);

            TransactionTimeout transactionTimeout = assertInstanceOf(TransactionTimeout.class, timeout(timeout));
            assertEquals(timeout, transactionTimeout.timeout());
        }

        @Test
        @DisplayName("toString")
        void testToString() {
            Duration timeout = Duration.ofMillis(100);

            TransactionOption option = timeout(timeout);

            assertEquals("timeout=" + timeout, option.toString());
        }
    }

    @Nested
    @DisplayName("description")
    class Description {

        @Test
        @DisplayName("null description")
        void testNullDescription() {
            assertThrows(NullPointerException.class, () -> description(null));
        }

        @Test
        @DisplayName("non-null description")
        void testNonNullDescription() {
            TransactionDescription transactionDescription = assertInstanceOf(TransactionDescription.class, description("test"));
            assertEquals("test", transactionDescription.description());
        }

        @Test
        @DisplayName("toString")
        void testToString() {
            TransactionOption option = description("test");

            assertEquals("description=test", option.toString());
        }
    }
}
