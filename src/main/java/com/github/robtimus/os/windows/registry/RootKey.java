/*
 * RootKey.java
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

import java.util.Optional;
import com.sun.jna.platform.win32.WinReg.HKEY;

final class RootKey extends RegistryKey {

    final HKEY hKey;
    private final String name;
    private final Handle handle;

    RootKey(HKEY hKey, String name) {
        this.hKey = hKey;
        this.name = name;
        this.handle = new Handle();
    }

    // structural

    @Override
    public String name() {
        return name;
    }

    @Override
    public String path() {
        return name();
    }

    // traversal

    @Override
    public Optional<RegistryKey> parent() {
        return Optional.empty();
    }

    // other

    @Override
    public boolean exists() {
        return true;
    }

    @Override
    public void create() {
        throw new RegistryKeyAlreadyExistsException(path());
    }

    @Override
    public boolean createIfNotExists() {
        return false;
    }

    @Override
    public void delete() {
        throw new UnsupportedOperationException(Messages.RegistryKey.cannotDeleteRoot.get(path()));
    }

    @Override
    public boolean deleteIfExists() {
        throw new UnsupportedOperationException(Messages.RegistryKey.cannotDeleteRoot.get(path()));
    }

    // handles

    @Override
    public Handle handle() {
        return handle;
    }

    @Override
    public Handle handle(HandleOption... options) {
        return handle;
    }

    @Override
    Handle handle(int samDesired) {
        return handle;
    }

    // Comparable / Object

    @Override
    public boolean equals(Object o) {
        return this == o;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(this);
    }

    private final class Handle extends RegistryKey.Handle {

        private Handle() {
            super(hKey);
        }

        @Override
        public void close() {
            // Don't close hKey
        }
    }
}