/*
 * KtmW32Test.java
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

import static com.github.robtimus.os.windows.registry.foreign.WindowsConstants.ERROR_INVALID_HANDLE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class KtmW32Test {

    // Use an invalid memory segment to trigger invalid handle errors
    private static final MemorySegment INVALID_HANDLE = MemorySegment.NULL;

    @Test
    @DisplayName("CreateTransaction")
    void testCreateTransaction() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment captureState = CaptureState.allocate(arena);

            MemorySegment handle = KtmW32.CreateTransaction(
                    MemorySegment.NULL,
                    MemorySegment.NULL,
                    0,
                    0,
                    0,
                    0,
                    MemorySegment.NULL,
                    captureState);

            assertNotEquals(MemorySegment.NULL, handle);

            boolean result = Kernel32.CloseHandle(handle, captureState);

            assertTrue(result);
        }
    }

    @Test
    @DisplayName("CommitTransaction")
    void testCommitTransaction() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment captureState = CaptureState.allocate(arena);

            boolean result = KtmW32.CommitTransaction(INVALID_HANDLE, captureState);

            assertInvalidHandle(result, captureState);
        }
    }

    @Test
    @DisplayName("RollbackTransaction")
    void testRollbackTransaction() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment captureState = CaptureState.allocate(arena);

            boolean result = KtmW32.RollbackTransaction(INVALID_HANDLE, captureState);

            assertInvalidHandle(result, captureState);
        }
    }

    @Nested
    @DisplayName("GetTransactionInformation")
    class GetTransactionInformation {

        @Test
        @DisplayName("minimal arguments")
        void testMinimalArguments() {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment captureState = CaptureState.allocate(arena);

                boolean result = KtmW32.GetTransactionInformation(
                        INVALID_HANDLE,
                        MemorySegment.NULL,
                        MemorySegment.NULL,
                        MemorySegment.NULL,
                        MemorySegment.NULL,
                        0,
                        MemorySegment.NULL,
                        captureState);

                assertInvalidHandle(result, captureState);
            }
        }

        @Test
        @DisplayName("all arguments")
        void testAllArguments() {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment captureState = CaptureState.allocate(arena);

                MemorySegment outcome = arena.allocate(ValueLayout.JAVA_INT);
                MemorySegment isolationLevel = arena.allocate(ValueLayout.JAVA_INT);
                MemorySegment isolationFlags = arena.allocate(ValueLayout.JAVA_INT);
                MemorySegment timeout = arena.allocate(ValueLayout.JAVA_INT);
                int bufferLength = 100;
                MemorySegment description = arena.allocate(bufferLength);

                boolean result = KtmW32.GetTransactionInformation(
                        INVALID_HANDLE,
                        outcome,
                        isolationLevel,
                        isolationFlags,
                        timeout,
                        bufferLength,
                        description,
                        captureState);

                assertInvalidHandle(result, captureState);
            }
        }
    }

    private void assertInvalidHandle(boolean result, MemorySegment captureState) {
        assertFalse(result);
        assertEquals(ERROR_INVALID_HANDLE, CaptureState.getLastError(captureState));
    }
}
