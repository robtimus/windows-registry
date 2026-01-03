/*
 * WindowsApi.java
 * Copyright 2026 Rob Spoor
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

import java.lang.foreign.Arena;
import java.lang.foreign.SymbolLookup;
import java.util.Optional;

abstract class WindowsApi {

    /*
     * Use an Arena that is managed by the garbage collector. It will be eligible for garbage collection when this class is unloaded,
     * and therefore the Arena and everything in this class and its sub classes have the same lifespan.
     */
    static final Arena ARENA = Arena.ofAuto();

    WindowsApi() {
    }

    static Optional<SymbolLookup> optionalSymbolLookup(String name, Arena arena) {
        try {
            return Optional.of(SymbolLookup.libraryLookup(name, arena));
        } catch (IllegalArgumentException e) {
            System.getLogger("windows-registry").log(System.Logger.Level.WARNING, e.getMessage()); //$NON-NLS-1$
            return Optional.empty();
        }
    }
}
