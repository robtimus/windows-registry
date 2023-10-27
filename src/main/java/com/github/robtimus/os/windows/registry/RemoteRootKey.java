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

import java.lang.ref.Cleaner;
import java.util.Optional;
import java.util.function.IntPredicate;
import com.sun.jna.platform.win32.WinError;
import com.sun.jna.platform.win32.WinReg.HKEY;

final class RemoteRootKey extends RemoteRegistryKey {

    private final String machineName;
    private final RootKey rootKey;
    private final HKEY hKey;
    private final Handle handle;
    private final Cleaner.Cleanable cleanable;

    RemoteRootKey(String machineName, RootKey rootKey, HKEY hKey) {
        this.machineName = machineName;
        this.rootKey = rootKey;
        this.hKey = hKey;
        this.handle = new Handle();
        this.cleanable = closeOnClean(this, hKey, rootKey.name(), machineName);
    }

    HKEY hKey() {
        return hKey;
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

    @Override
    String machineName() {
        return machineName;
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
        int code = checkHKEY();
        if (code == WinError.ERROR_SUCCESS) {
            return true;
        }
        if (code == WinError.ERROR_FILE_NOT_FOUND) {
            return false;
        }
        throw RegistryException.forKey(code, path(), machineName);
    }

    @Override
    public boolean isAccessible() {
        int code = checkHKEY();
        if (code == WinError.ERROR_SUCCESS) {
            return true;
        }
        if (code == WinError.ERROR_FILE_NOT_FOUND || code == WinError.ERROR_ACCESS_DENIED) {
            return false;
        }
        throw RegistryException.forKey(code, path(), machineName);
    }

    @Override
    public void create() {
        throw new RegistryKeyAlreadyExistsException(path(), machineName);
    }

    @Override
    public boolean createIfNotExists() {
        // verify the handle using exists
        exists();
        return false;
    }

    @Override
    public RegistryKey renameTo(String newName) {
        throw new UnsupportedOperationException(Messages.RegistryKey.cannotRenameRoot(path()));
    }

    @Override
    public void delete() {
        throw new UnsupportedOperationException(Messages.RegistryKey.cannotDeleteRoot(path()));
    }

    @Override
    public boolean deleteIfExists() {
        throw new UnsupportedOperationException(Messages.RegistryKey.cannotDeleteRoot(path()));
    }

    // handles

    @Override
    RegistryKey.Handle handle(int samDesired, boolean create) {
        int code = checkHKEY();
        if (code != WinError.ERROR_SUCCESS) {
            throw RegistryException.forKey(code, path(), machineName());
        }
        return handle;
    }

    @Override
    Optional<RegistryKey.Handle> handle(int samDesired, IntPredicate ignoreError) {
        int code = checkHKEY();
        if (code == WinError.ERROR_SUCCESS) {
            return Optional.of(handle);
        }
        if (ignoreError.test(code)) {
            return Optional.empty();
        }
        throw RegistryException.forKey(code, path(), machineName());
    }

    private int checkHKEY() {
        return api.RegQueryInfoKey(hKey, null, null, null, null, null, null, null, null, null, null, null);
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
        cleanable.clean();
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
