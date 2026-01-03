/*
 * WindowsApiTest.java
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

import static com.github.robtimus.os.windows.registry.WindowsApi.optionalSymbolLookup;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import java.lang.foreign.Arena;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

@SuppressWarnings("nls")
final class WindowsApiTest {

    private WindowsApiTest() {
    }

    @Nested
    @DisplayName("optionalSymbolLookup")
    class OptionalSymbolLookup {

        @Test
        @DisplayName("library found")
        @EnabledOnOs(OS.WINDOWS)
        void testLibraryFound() {
            String name = "Kernel32";

            try (Arena arena = Arena.ofConfined()) {
                assertNotEquals(Optional.empty(), optionalSymbolLookup(name, arena));
            }
        }

        @Test
        @DisplayName("library not found")
        void testLibraryNotFound() {
            String name = UUID.randomUUID().toString();

            try (Arena arena = Arena.ofConfined()) {
                assertEquals(Optional.empty(), optionalSymbolLookup(name, arena));
            }
        }
    }
}
