/*
 * CaptureState.java
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

import static java.lang.foreign.MemoryLayout.PathElement.groupElement;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.lang.foreign.StructLayout;
import java.lang.foreign.ValueLayout;
import java.util.Optional;

final class CaptureState {

    @SuppressWarnings("nls")
    static final String GET_LAST_ERROR = "GetLastError";

    static final StructLayout LAYOUT = Linker.Option.captureStateLayout();

    static final long LAST_ERROR_OFFSET = LAYOUT.byteOffset(groupElement(GET_LAST_ERROR));

    static final ValueLayout.OfInt LAST_ERROR_LAYOUT = (ValueLayout.OfInt) LAYOUT.select(groupElement(GET_LAST_ERROR));

    static final Linker.Option LINKER_OPTION = Linker.Option.captureCallState(LAYOUT.memberLayouts()
            .stream()
            .map(MemoryLayout::name)
            .flatMap(Optional::stream)
            .toArray(String[]::new));

    private CaptureState() {
    }

    static MemorySegment allocate(SegmentAllocator allocator) {
        return allocator.allocate(LAYOUT);
    }

    static int getLastError(MemorySegment segment) {
        return segment.get(LAST_ERROR_LAYOUT, LAST_ERROR_OFFSET);
    }
}
