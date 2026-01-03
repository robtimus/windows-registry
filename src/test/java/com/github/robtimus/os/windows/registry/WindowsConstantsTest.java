/*
 * WindowsConstantsTest.java
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

package com.github.robtimus.os.windows.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import java.lang.foreign.MemorySegment;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.junit.jupiter.params.support.ParameterDeclarations;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.WinReg;

class WindowsConstantsTest {

    @ParameterizedTest(name = "HKEY = {0}, expected = {1}")
    @ArgumentsSource(HKEYArgumentsProvider.class)
    @DisplayName("HKEY constants")
    void testHKEY(MemorySegment hKey, WinReg.HKEY expected) {
        Pointer pointer = expected.getPointer();

        assertEquals(Pointer.nativeValue(pointer), hKey.address());
    }

    private static final class HKEYArgumentsProvider implements ArgumentsProvider {

        @Override
        public Stream<? extends Arguments> provideArguments(ParameterDeclarations parameters, ExtensionContext context) {
            return Stream.of(
                    arguments(WindowsConstants.HKEY_CLASSES_ROOT, WinReg.HKEY_CLASSES_ROOT),
                    arguments(WindowsConstants.HKEY_CURRENT_USER, WinReg.HKEY_CURRENT_USER),
                    arguments(WindowsConstants.HKEY_LOCAL_MACHINE, WinReg.HKEY_LOCAL_MACHINE),
                    arguments(WindowsConstants.HKEY_USERS, WinReg.HKEY_USERS),
                    arguments(WindowsConstants.HKEY_CURRENT_CONFIG, WinReg.HKEY_CURRENT_CONFIG)
            );
        }
    }
}
