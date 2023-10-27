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

import java.lang.ref.Cleaner;
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

    @Override
    String machineName() {
        return null;
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
        return exists(root.hKey(), machineName());
    }

    boolean exists(HKEY rootHKey, String machineName) {
        HKEYByReference phkResult = new HKEYByReference();
        int code = api.RegOpenKeyEx(rootHKey, path, 0, WinNT.KEY_READ, phkResult);
        if (code == WinError.ERROR_SUCCESS) {
            closeKey(phkResult.getValue(), path(), machineName);
            return true;
        }
        if (code == WinError.ERROR_FILE_NOT_FOUND) {
            return false;
        }
        throw RegistryException.forKey(code, path(), machineName);
    }

    @Override
    public boolean isAccessible() {
        return isAccessible(root.hKey(), machineName());
    }

    boolean isAccessible(HKEY rootHKey, String machineName) {
        HKEYByReference phkResult = new HKEYByReference();
        int code = api.RegOpenKeyEx(rootHKey, path, 0, WinNT.KEY_READ, phkResult);
        if (code == WinError.ERROR_SUCCESS) {
            closeKey(phkResult.getValue(), path(), machineName);
            return true;
        }
        if (code == WinError.ERROR_FILE_NOT_FOUND || code == WinError.ERROR_ACCESS_DENIED) {
            return false;
        }
        throw RegistryException.forKey(code, path(), machineName);
    }

    @Override
    public void create() {
        create(root.hKey(), machineName());
    }

    void create(HKEY rootHKey, String machineName) {
        if (createOrOpen(rootHKey, machineName) == WinNT.REG_OPENED_EXISTING_KEY) {
            throw new RegistryKeyAlreadyExistsException(path(), machineName);
        }
    }

    @Override
    public boolean createIfNotExists() {
        return createIfNotExists(root.hKey(), machineName());
    }

    boolean createIfNotExists(HKEY rootHKey, String machineName) {
        return createOrOpen(rootHKey, machineName) == WinNT.REG_CREATED_NEW_KEY;
    }

    private int createOrOpen(HKEY rootHKey, String machineName) {
        HKEYByReference phkResult = new HKEYByReference();
        IntByReference lpdwDisposition = new IntByReference();

        int code = api.RegCreateKeyEx(rootHKey, path, 0, null, WinNT.REG_OPTION_NON_VOLATILE, WinNT.KEY_READ, null, phkResult, lpdwDisposition);
        if (code == WinError.ERROR_SUCCESS) {
            closeKey(phkResult.getValue(), path(), machineName);
            return lpdwDisposition.getValue();
        }
        throw RegistryException.forKey(code, path(), machineName);
    }

    @Override
    public RegistryKey renameTo(String newName) {
        return renameTo(root.hKey(), newName, machineName());
    }

    SubKey renameTo(HKEY rootHKey, String newName, String machineName) {
        if (newName.contains(SEPARATOR)) {
            throw new IllegalArgumentException(Messages.RegistryKey.nameContainsBackslash(newName));
        }

        Deque<String> newPathParts = new ArrayDeque<>(pathParts);
        newPathParts.removeLast();
        newPathParts.addLast(newName);
        SubKey renamed = new SubKey(root, newPathParts);

        int code;
        try {
            code = api.RegRenameKey(rootHKey, path, newName);
        } catch (UnsatisfiedLinkError e) {
            // The RegRenameKey function does not exist; the current Windows version is too old
            throw new UnsupportedOperationException(e.getMessage(), e);
        }
        if (code == WinError.ERROR_SUCCESS) {
            return renamed;
        }
        if (code == WinError.ERROR_ACCESS_DENIED && renamed.exists(rootHKey, machineName)) {
            throw new RegistryKeyAlreadyExistsException(renamed.path(), machineName);
        }
        throw RegistryException.forKey(code, path(), machineName);
    }

    @Override
    public void delete() {
        delete(root.hKey(), machineName());
    }

    void delete(HKEY rootHKey, String machineName) {
        int code = api.RegDeleteKey(rootHKey, path);
        if (code != WinError.ERROR_SUCCESS) {
            throw RegistryException.forKey(code, path(), machineName);
        }
    }

    @Override
    public boolean deleteIfExists() {
        return deleteIfExists(root.hKey(), machineName());
    }

    boolean deleteIfExists(HKEY rootHKey, String machineName) {
        int code = api.RegDeleteKey(rootHKey, path);
        if (code == WinError.ERROR_SUCCESS) {
            return true;
        }
        if (code == WinError.ERROR_FILE_NOT_FOUND) {
            return false;
        }
        throw RegistryException.forKey(code, path(), machineName);
    }

    // handles

    @Override
    Handle handle(int samDesired, boolean create) {
        HKEY hKey = create
                ? createOrOpenKey(root.hKey(), samDesired, machineName())
                : openKey(root.hKey(), samDesired, machineName());
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

    HKEY openKey(HKEY rootHKey, int samDesired, String machineName) {
        HKEYByReference phkResult = new HKEYByReference();
        int code = api.RegOpenKeyEx(rootHKey, path, 0, samDesired, phkResult);
        if (code == WinError.ERROR_SUCCESS) {
            return phkResult.getValue();
        }
        throw RegistryException.forKey(code, path(), machineName);
    }

    HKEY createOrOpenKey(HKEY rootHKey, int samDesired, String machineName) {
        HKEYByReference phkResult = new HKEYByReference();

        int code = api.RegCreateKeyEx(rootHKey, path, 0, null, WinNT.REG_OPTION_NON_VOLATILE, samDesired, null, phkResult, null);
        if (code == WinError.ERROR_SUCCESS) {
            return phkResult.getValue();
        }
        throw RegistryException.forKey(code, path(), machineName);
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
