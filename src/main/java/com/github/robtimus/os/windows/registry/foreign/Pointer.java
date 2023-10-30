/*
 * Pointer.java
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

import java.lang.foreign.MemorySegment;

@SuppressWarnings("javadoc")
public abstract class Pointer {

    private final MemorySegment segment;

    Pointer(MemorySegment segment) {
        this.segment = segment;
    }

    public int size() {
        return Math.toIntExact(segment.byteSize());
    }

    final MemorySegment segment() {
        return segment;
    }

    public void clear() {
        segment().fill((byte) 0);
    }
}
