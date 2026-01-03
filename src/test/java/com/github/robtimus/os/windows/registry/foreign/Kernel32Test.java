/*
 * Kernel32Test.java
 * Copyright 2023 Rob Spoor
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

package com.github.robtimus.os.windows.registry.foreign;

import static com.github.robtimus.os.windows.registry.foreign.WindowsConstants.ERROR_ACCESS_DENIED;
import static com.github.robtimus.os.windows.registry.foreign.WindowsConstants.ERROR_ALREADY_EXISTS;
import static com.github.robtimus.os.windows.registry.foreign.WindowsConstants.ERROR_SUCCESS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import com.sun.jna.platform.win32.Kernel32Util;

class Kernel32Test {

    @ParameterizedTest
    @DisplayName("expandEnvironmentStrings")
    @ValueSource(strings = { "value with environment string %USERNAME%", "value without environment string" })
    @NullAndEmptySource
    void testExpandEnvironmentStrings(String input) {
        String result = Kernel32.expandEnvironmentStrings(input);

        String expected = Kernel32Util.expandEnvironmentStrings(input);

        assertEquals(expected, result);
    }

    @DisplayName("formatMessage")
    @Nested
    class FormatMessage {

        @ParameterizedTest
        @DisplayName("success")
        @ValueSource(ints = { ERROR_SUCCESS, ERROR_ACCESS_DENIED, ERROR_ALREADY_EXISTS })
        void testFormatMessage(int code) {
            String result = Kernel32.formatMessage(code);

            String expected = Kernel32Util.formatMessage(code);

            assertEquals(expected, result);
        }

        @Test
        @DisplayName("unknown code")
        void testFormatMessageWithError() {
            IllegalStateException exception = assertThrows(IllegalStateException.class, () -> Kernel32.formatMessage(-1));
            // 317 is ERROR_MR_MID_NOT_FOUND: The system cannot find message text for message number 0x%1 in the message file for %2.
            assertEquals(Messages.Kernel32.formatMessageError(-1, 317), exception.getMessage());
        }
    }
}
