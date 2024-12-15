/*
 * ApiImpl.java
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

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.util.Optional;

abstract class ApiImpl {

    ApiImpl() {
    }

    static MethodHandle functionMethodHandle(Linker linker, SymbolLookup symbolLookup, String name,
            FunctionDescriptor function, Linker.Option... options) {

        return optionalFunctionMethodHandle(linker, symbolLookup, name, function, options)
                .orElseThrow(() -> new IllegalStateException(Messages.ApiImpl.functionNotFound(name)));
    }

    static Optional<MethodHandle> optionalFunctionMethodHandle(Linker linker, SymbolLookup symbolLookup, String name,
            FunctionDescriptor function, Linker.Option... options) {

        return symbolLookup.find(name)
                .map(address -> linker.downcallHandle(address, function, options));
    }

    static MemorySegment segment(Pointer pointer) {
        return pointer != null
                ? pointer.segment()
                : MemorySegment.NULL;
    }

    static MemorySegment segment(Structure structure) {
        return structure != null
                ? structure.segment()
                : MemorySegment.NULL;
    }
}
