/*
 * SubKey.java
 * Copyright 2020 Rob Spoor
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

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.Optional;
import com.sun.jna.platform.win32.WinError;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.platform.win32.WinReg.HKEY;
import com.sun.jna.platform.win32.WinReg.HKEYByReference;
import com.sun.jna.ptr.IntByReference;

final class SubKey extends RegistryKey {

    private final RootKey root;

    private final String path;
    private final Deque<String> pathParts;

    SubKey(RootKey root, Deque<String> pathParts) {
        this.root = root;

        this.path = String.join(SEPARATOR, pathParts);
        this.pathParts = pathParts;
    }

    // structural

    @Override
    public String name() {
        return pathParts.getLast();
    }

    @Override
    public String path() {
        return root.name() + SEPARATOR + path;
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
        if (pathParts.size() == 1) {
            // Only one part, so the parent is the root
            return Optional.of(root);
        }

        Deque<String> parentPathParts = new ArrayDeque<>(pathParts);
        parentPathParts.removeLast();
        SubKey parent = new SubKey(root, parentPathParts);
        return Optional.of(parent);
    }

    @Override
    public RegistryKey resolve(String relativePath) {
        if (relativePath.isEmpty() || ".".equals(relativePath)) { //$NON-NLS-1$
            return this;
        }
        if (relativePath.startsWith(SEPARATOR)) {
            return root.resolve(relativePath, Collections.emptyList());
        }
        return root.resolve(relativePath, pathParts);
    }

    @Override
    RegistryKey resolveChild(String name) {
        return root.resolveChild(name, pathParts);
    }

    // other

    @Override
    public boolean exists() {
        return exists(root.hKey);
    }

    boolean exists(HKEY rootHKey) {
        HKEYByReference phkResult = new HKEYByReference();
        int code = api.RegOpenKeyEx(rootHKey, path, 0, WinNT.KEY_READ, phkResult);
        if (code == WinError.ERROR_SUCCESS) {
            closeKey(phkResult.getValue());
            return true;
        }
        if (code == WinError.ERROR_FILE_NOT_FOUND) {
            return false;
        }
        throw RegistryException.of(code, path());
    }

    @Override
    public void create() {
        create(root.hKey);
    }

    void create(HKEY rootHKey) {
        if (createOrOpen(rootHKey) == WinNT.REG_OPENED_EXISTING_KEY) {
            throw new RegistryKeyAlreadyExistsException(path());
        }
    }

    @Override
    public boolean createIfNotExists() {
        return createIfNotExists(root.hKey);
    }

    boolean createIfNotExists(HKEY rootHKey) {
        return createOrOpen(rootHKey) == WinNT.REG_CREATED_NEW_KEY;
    }

    private int createOrOpen(HKEY rootHKey) {
        HKEYByReference phkResult = new HKEYByReference();
        IntByReference lpdwDisposition = new IntByReference();

        int code = api.RegCreateKeyEx(rootHKey, path, 0, null, WinNT.REG_OPTION_NON_VOLATILE, WinNT.KEY_READ, null, phkResult, lpdwDisposition);
        if (code == WinError.ERROR_SUCCESS) {
            closeKey(phkResult.getValue());
            return lpdwDisposition.getValue();
        }
        throw RegistryException.of(code, path());
    }

    @Override
    public RegistryKey renameTo(String newName) {
        return renameTo(root.hKey, newName);
    }

    SubKey renameTo(HKEY rootHKey, String newName) {
        if (newName.contains(SEPARATOR)) {
            throw new IllegalArgumentException(Messages.RegistryKey.nameContainsBackslash.get(newName));
        }

        Deque<String> newPathParts = new ArrayDeque<>(pathParts);
        newPathParts.removeLast();
        newPathParts.addLast(newName);
        SubKey renamed = new SubKey(root, newPathParts);

        int code = api.RegRenameKey(rootHKey, path, newName);
        if (code == WinError.ERROR_SUCCESS) {
            return renamed;
        }
        if (code == WinError.ERROR_ACCESS_DENIED && renamed.exists(rootHKey)) {
            throw new RegistryKeyAlreadyExistsException(renamed.path());
        }
        throw RegistryException.of(code, path());
    }

    @Override
    public void delete() {
        delete(root.hKey);
    }

    void delete(HKEY rootHKey) {
        int code = api.RegDeleteKey(rootHKey, path);
        if (code != WinError.ERROR_SUCCESS) {
            throw RegistryException.of(code, path());
        }
    }

    @Override
    public boolean deleteIfExists() {
        return deleteIfExists(root.hKey);
    }

    boolean deleteIfExists(HKEY rootHKey) {
        int code = api.RegDeleteKey(rootHKey, path);
        if (code == WinError.ERROR_SUCCESS) {
            return true;
        }
        if (code == WinError.ERROR_FILE_NOT_FOUND) {
            return false;
        }
        throw RegistryException.of(code, path());
    }

    // handles

    @Override
    Handle handle(int samDesired, boolean create) {
        return new Handle(samDesired, create);
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
        SubKey other = (SubKey) o;
        return root.equals(other.root) && path.equals(other.path);
    }

    @Override
    public int hashCode() {
        int result = 1;
        result = 31 * result + root.hashCode();
        result = 31 * result + path.hashCode();
        return result;
    }

    HKEY openKey(HKEY rootHKey, int samDesired) {
        HKEYByReference phkResult = new HKEYByReference();
        int code = api.RegOpenKeyEx(rootHKey, path, 0, samDesired, phkResult);
        if (code == WinError.ERROR_SUCCESS) {
            return phkResult.getValue();
        }
        throw RegistryException.of(code, path());
    }

    HKEY createOrOpenKey(HKEY rootHKey, int samDesired) {
        HKEYByReference phkResult = new HKEYByReference();

        int code = api.RegCreateKeyEx(rootHKey, path, 0, null, WinNT.REG_OPTION_NON_VOLATILE, samDesired, null, phkResult, null);
        if (code == WinError.ERROR_SUCCESS) {
            return phkResult.getValue();
        }
        throw RegistryException.of(code, path());
    }

    final class Handle extends RegistryKey.Handle {

        private boolean closed;

        private Handle(int samDesired, boolean create) {
            super(create ? createOrOpenKey(root.hKey, samDesired) : openKey(root.hKey, samDesired));
            this.closed = false;
        }

        @Override
        public void close() {
            if (!closed) {
                closeKey(hKey);
                closed = true;
            }
        }

        @Override
        void close(RuntimeException exception) {
            // No need to check the current state; this method is only called for non-exposed handles
            int code = api.RegCloseKey(hKey);
            if (code == WinError.ERROR_SUCCESS) {
                closed = true;
            } else {
                exception.addSuppressed(RegistryException.of(code, path()));
            }
        }
    }
}
