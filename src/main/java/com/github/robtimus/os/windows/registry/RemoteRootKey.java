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

import static com.github.robtimus.os.windows.registry.Advapi32.RegQueryInfoKey;
import static com.github.robtimus.os.windows.registry.WindowsConstants.ERROR_ACCESS_DENIED;
import static com.github.robtimus.os.windows.registry.WindowsConstants.ERROR_FILE_NOT_FOUND;
import static com.github.robtimus.os.windows.registry.WindowsConstants.ERROR_SUCCESS;
import java.lang.foreign.MemorySegment;
import java.lang.ref.Cleaner;
import java.util.Optional;
import java.util.function.IntPredicate;

final class RemoteRootKey extends RegistryKey {

    private final String machineName;
    private final LocalRootKey local;
    private final MemorySegment hKey;
    private final Handle handle;
    private final Cleaner.Cleanable cleanable;

    RemoteRootKey(String machineName, LocalRootKey local, MemorySegment hKey) {
        this.machineName = machineName;
        this.local = local;
        this.hKey = hKey;
        this.handle = new Handle();
        this.cleanable = closeOnClean(this, hKey, local.name(), machineName);
    }

    MemorySegment hKey() {
        return hKey;
    }

    // structural

    @Override
    public String name() {
        return local.name();
    }

    @Override
    public String path() {
        return local.path();
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
        RegistryKey resolved = local.resolve(relativePath);
        return resolved.isRoot() ? this : new RemoteSubKey(this, (LocalSubKey) resolved);
    }

    @Override
    RegistryKey resolveChild(String name) {
        RegistryKey resolved = local.resolveChild(name);
        return new RemoteSubKey(this, (LocalSubKey) resolved);
    }

    // other

    @Override
    public boolean exists() {
        int code = checkHKEY();
        if (code == ERROR_SUCCESS) {
            return true;
        }
        if (code == ERROR_FILE_NOT_FOUND) {
            return false;
        }
        throw RegistryException.forKey(code, path(), machineName);
    }

    @Override
    public boolean isAccessible() {
        int code = checkHKEY();
        if (code == ERROR_SUCCESS) {
            return true;
        }
        if (code == ERROR_FILE_NOT_FOUND || code == ERROR_ACCESS_DENIED) {
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
        if (code != ERROR_SUCCESS) {
            throw RegistryException.forKey(code, path(), machineName());
        }
        return handle;
    }

    @Override
    Optional<RegistryKey.Handle> handle(int samDesired, IntPredicate ignoreError) {
        int code = checkHKEY();
        if (code == ERROR_SUCCESS) {
            return Optional.of(handle);
        }
        if (ignoreError.test(code)) {
            return Optional.empty();
        }
        throw RegistryException.forKey(code, path(), machineName());
    }

    private int checkHKEY() {
        return RegQueryInfoKey(
                hKey,
                MemorySegment.NULL,
                MemorySegment.NULL,
                MemorySegment.NULL,
                MemorySegment.NULL,
                MemorySegment.NULL,
                MemorySegment.NULL,
                MemorySegment.NULL,
                MemorySegment.NULL,
                MemorySegment.NULL,
                MemorySegment.NULL,
                MemorySegment.NULL);
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
        return local.equals(other.local) && machineName.equals(other.machineName);
    }

    @Override
    public int hashCode() {
        int result = 1;
        result = 31 * result + local.hashCode();
        result = 31 * result + machineName.hashCode();
        return result;
    }

    @Override
    @SuppressWarnings("nls")
    public String toString() {
        return local.toString() + "@" + machineName;
    }

    /*
     * Don't let RemoteRootKey implement AutoCloseable, and keep this method package-protected,
     * to prevent this being called directly instead of through RemoteRegistry.close()
     */
    void close() {
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
