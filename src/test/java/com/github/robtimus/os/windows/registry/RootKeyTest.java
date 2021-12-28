/*
 * RootKeyTest.java
 * Copyright 2021 Rob Spoor
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import com.sun.jna.platform.win32.Advapi32;

@SuppressWarnings("nls")
class RootKeyTest {

    @BeforeEach
    void mockApi() {
        RegistryKey.api = mock(Advapi32.class);
    }

    @AfterEach
    void restoreApi() {
        RegistryKey.api = Advapi32.INSTANCE;
    }

    @Test
    @DisplayName("name")
    void testName() {
        assertEquals("HKEY_CURRENT_USER", RegistryKey.HKEY_CURRENT_USER.name());
    }

    @Test
    @DisplayName("path")
    void testPath() {
        assertEquals("HKEY_CURRENT_USER", RegistryKey.HKEY_CURRENT_USER.path());
    }

    @Test
    @DisplayName("isRoot")
    void testIsRoot() {
        assertTrue(RegistryKey.HKEY_CURRENT_USER.isRoot());
    }

    @Test
    @DisplayName("root")
    void testRoot() {
        assertSame(RegistryKey.HKEY_CURRENT_USER, RegistryKey.HKEY_CURRENT_USER.root());
    }

    @Test
    @DisplayName("parent")
    void testParent() {
        assertEquals(Optional.empty(), RegistryKey.HKEY_CURRENT_USER.parent());
    }

    @ParameterizedTest(name = "{0} => {1}")
    @CsvSource({
            ", HKEY_CURRENT_USER",
            "., HKEY_CURRENT_USER",
            ".., HKEY_CURRENT_USER",
            "..\\.., HKEY_CURRENT_USER",
            "..\\..\\.., HKEY_CURRENT_USER",
            "..\\..\\..\\.., HKEY_CURRENT_USER",
            "child, HKEY_CURRENT_USER\\child",
            "..\\..\\..\\..\\..\\Something\\..\\..\\Something else\\\\.\\leaf, HKEY_CURRENT_USER\\Something else\\leaf"
    })
    @DisplayName("resolve")
    void testResolve(String relativePath, String expectedPath) {
        RegistryKey resolved = RegistryKey.HKEY_CURRENT_USER.resolve(relativePath != null ? relativePath : "");
        assertEquals(expectedPath, resolved.path());
    }

    @Test
    @DisplayName("exists")
    void testExists() {
        assertTrue(RegistryKey.HKEY_CURRENT_USER.exists());
    }

    @Test
    @DisplayName("create")
    void testCreate() {
        assertThrows(RegistryKeyAlreadyExistsException.class, RegistryKey.HKEY_CURRENT_USER::create);
    }

    @Test
    @DisplayName("createIfNotExists")
    void testCreateIfNotExists() {
        assertFalse(RegistryKey.HKEY_CURRENT_USER::createIfNotExists);
    }

    @Test
    @DisplayName("delete")
    void testDelete() {
        assertThrows(UnsupportedOperationException.class, RegistryKey.HKEY_CURRENT_USER::delete);
    }

    @Test
    @DisplayName("deleteIfExists")
    void testDeleteIfExists() {
        assertThrows(UnsupportedOperationException.class, RegistryKey.HKEY_CURRENT_USER::deleteIfExists);
    }
}
