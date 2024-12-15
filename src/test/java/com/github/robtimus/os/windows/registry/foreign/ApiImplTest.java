/*
 * ApiImplTest.java
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

import static com.github.robtimus.os.windows.registry.foreign.ForeignUtils.ARENA;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@SuppressWarnings("nls")
class ApiImplTest {

    @Nested
    @DisplayName("functionMethodHandle")
    class FunctionMethodHandle {

        @Test
        @DisplayName("function not found")
        void testFunctionNotFound() {
            Linker linker = Linker.nativeLinker();
            SymbolLookup symbolLookup = SymbolLookup.libraryLookup("Advapi32", ARENA);
            FunctionDescriptor function = FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS);

            IllegalStateException exception = assertThrows(IllegalStateException.class,
                    () -> ApiImpl.functionMethodHandle(linker, symbolLookup, "nonExistingFunction", function));

            assertEquals(Messages.ApiImpl.functionNotFound("nonExistingFunction"), exception.getMessage());
        }
    }

    @Nested
    @DisplayName("optionalFunctionMethodHandle")
    class OptionalFunctionMethodHandle {

        @Test
        @DisplayName("function not found")
        void testFunctionNotFound() {
            Linker linker = Linker.nativeLinker();
            SymbolLookup symbolLookup = SymbolLookup.libraryLookup("Advapi32", ARENA);
            FunctionDescriptor function = FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS);

            assertEquals(Optional.empty(),
                    ApiImpl.optionalFunctionMethodHandle(linker, symbolLookup, "nonExistingFunction", function));
        }
    }
}
