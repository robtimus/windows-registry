/*
 * RegistryTestBase.java
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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;
import java.lang.foreign.Arena;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.MockedStatic;
import com.github.robtimus.os.windows.registry.foreign.Advapi32;
import com.github.robtimus.os.windows.registry.foreign.Kernel32;
import com.github.robtimus.os.windows.registry.foreign.KtmW32;

class RegistryTestBase {

    static Arena arena;

    // Mock all 3 Windows API classes to avoid performing actual calls by accident
    static MockedStatic<Advapi32> advapi32;
    static MockedStatic<KtmW32> ktmW32;
    static MockedStatic<Kernel32> kernel32;

    @BeforeEach
    void setupArena() {
        arena = Arena.ofConfined();
    }

    @AfterEach
    void closeArena() {
        arena.close();
    }

    @BeforeEach
    void setupMocks() {
        // Check these two, to call the enabled methods before any mocking
        assertTrue(RegistryFeature.RENAME_KEY.isEnabled());
        assertTrue(RegistryFeature.TRANSACTIONS.isEnabled());

        advapi32 = mockStatic();
        ktmW32 = mockStatic();
        kernel32 = mockStatic();
    }

    @AfterEach
    void closeMocks() {
        advapi32.close();
        ktmW32.close();
        kernel32.close();
    }
}
