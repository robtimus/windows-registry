/*
 * Kernel32UtilsTest.java
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import com.sun.jna.platform.win32.Kernel32Util;

class Kernel32UtilsTest {

    @ParameterizedTest
    @DisplayName("expandEnvironmentStrings")
    @ValueSource(strings = { "value with environment string %USERNAME%", "value without environment string" })
    @NullAndEmptySource
    void testExpandEnvironmentStrings(String input) {
        String result = Kernel32Utils.expandEnvironmentStrings(input);

        String expected = Kernel32Util.expandEnvironmentStrings(input);

        assertEquals(expected, result);
    }

    @ParameterizedTest
    @DisplayName("formatMessage")
    @ValueSource(ints = { WinError.ERROR_SUCCESS, WinError.ERROR_ACCESS_DENIED, WinError.ERROR_ALREADY_EXISTS })
    void testFormatMessage(int code) {
        String result = Kernel32Utils.formatMessage(code);

        String expected = Kernel32Util.formatMessage(code);

        assertEquals(expected, result);
    }
}
