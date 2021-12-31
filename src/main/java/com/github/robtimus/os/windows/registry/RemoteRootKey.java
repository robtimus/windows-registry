/*
 * RemoteRootKey.java
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
import com.sun.jna.platform.win32.WinError;
import com.sun.jna.platform.win32.WinReg.HKEY;

final class RemoteRootKey extends RemoteRegistryKey {

    final String machineName;
    final HKEY hKey;
    private final RootKey rootKey;
    private final Handle handle;

    RemoteRootKey(String machineName, RootKey rootKey, HKEY hKey) {
        this.machineName = machineName;
        this.rootKey = rootKey;
        this.hKey = hKey;
        this.handle = new Handle();
    }

    // structural

    @Override
    public String name() {
        return rootKey.name();
    }

    @Override
    public String path() {
        return rootKey.path();
    }

    // traversal

    @Override
    public boolean isRoot() {
        return true;
    }

    @Override
    public RegistryKey root() {
        return this;
    }

    @Override
    public Optional<RegistryKey> parent() {
        return Optional.empty();
    }

    @Override
    public RegistryKey resolve(String relativePath) {
        RegistryKey resolved = rootKey.resolve(relativePath);
        return resolved.isRoot() ? this : new RemoteSubKey(this, (SubKey) resolved);
    }

    @Override
    RegistryKey resolveChild(String name) {
        RegistryKey resolved = rootKey.resolveChild(name);
        return new RemoteSubKey(this, (SubKey) resolved);
    }

    // other

    @Override
    public boolean exists() {
        int code = api.RegQueryInfoKey(hKey, null, null, null, null, null, null, null, null, null, null, null);
        return code == WinError.ERROR_SUCCESS;
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
    Handle handle(int samDesired, boolean create) {
        return handle;
    }

    // Comparable / Object

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || o.getClass() != getClass()) {
            return false;
        }
        RemoteRootKey other = (RemoteRootKey) o;
        return rootKey.equals(other.rootKey) && machineName.equals(other.machineName);
    }

    @Override
    public int hashCode() {
        int result = 1;
        result = 31 * result + rootKey.hashCode();
        result = 31 * result + machineName.hashCode();
        return result;
    }

    @Override
    @SuppressWarnings("nls")
    public String toString() {
        return rootKey.toString() + "@" + machineName;
    }

    @Override
    public void close() {
        closeKey(hKey);
    }

    private final class Handle extends RegistryKey.Handle {

        private Handle() {
            super(RemoteRootKey.this.hKey);
        }

        @Override
        public void close() {
            // Don't close hKey
        }

        @Override
        void close(RuntimeException exception) {
            // Don't close hKey
        }
    }
}
