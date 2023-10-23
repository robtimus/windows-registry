/*
 * RemoteSubKey.java
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
import com.sun.jna.platform.win32.WinReg.HKEY;

final class RemoteSubKey extends RegistryKey {

    private final RemoteRootKey root;
    private final SubKey local;

    RemoteSubKey(RemoteRootKey root, SubKey local) {
        this.root = root;
        this.local = local;
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
        return root.machineName();
    }

    // traversal

    @Override
    public boolean isRoot() {
        return false;
    }

    @Override
    public RegistryKey root() {
        return root;
    }

    @Override
    public Optional<RegistryKey> parent() {
        return local.parent()
                .map(p -> p.isRoot() ? root : new RemoteSubKey(root, (SubKey) p));
    }

    @Override
    public RegistryKey resolve(String relativePath) {
        RegistryKey resolved = local.resolve(relativePath);
        return resolved.isRoot() ? root : new RemoteSubKey(root, (SubKey) resolved);
    }

    @Override
    RegistryKey resolveChild(String name) {
        RegistryKey resolved = local.resolveChild(name);
        return new RemoteSubKey(root, (SubKey) resolved);
    }

    // other

    @Override
    public boolean exists() {
        return local.exists(root.hKey(), machineName());
    }

    @Override
    public void create() {
        local.create(root.hKey(), machineName());
    }

    @Override
    public boolean createIfNotExists() {
        return local.createIfNotExists(root.hKey(), machineName());
    }

    @Override
    public RegistryKey renameTo(String newName) {
        SubKey renamed = local.renameTo(root.hKey(), newName, machineName());
        return new RemoteSubKey(root, renamed);
    }

    @Override
    public void delete() {
        local.delete(root.hKey(), machineName());
    }

    @Override
    public boolean deleteIfExists() {
        return local.deleteIfExists(root.hKey(), machineName());
    }

    // handles

    @Override
    Handle handle(int samDesired, boolean create) {
        HKEY hKey = create
                ? local.createOrOpenKey(root.hKey(), samDesired, machineName())
                : local.openKey(root.hKey(), samDesired, machineName());
        return new Handle(hKey);
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
        RemoteSubKey other = (RemoteSubKey) o;
        return root.equals(other.root) && local.equals(other.local);
    }

    @Override
    public int hashCode() {
        int result = 1;
        result = 31 * result + root.hashCode();
        result = 31 * result + local.hashCode();
        return result;
    }

    @Override
    @SuppressWarnings("nls")
    public String toString() {
        return local.toString() + "@" + root.machineName();
    }

    private final class Handle extends RegistryKey.Handle {

        private final Cleaner.Cleanable cleanable;

        private Handle(HKEY hKey) {
            super(hKey);
            this.cleanable = closeOnClean(this, hKey, path(), machineName());
        }

        @Override
        public void close() {
            cleanable.clean();
        }

        @Override
        void close(RuntimeException exception) {
            try {
                cleanable.clean();
            } catch (RuntimeException e) {
                exception.addSuppressed(e);
            }
        }
    }
}
