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
import java.util.Collection;
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

    /**
     * Returns the name of the registry key.
     *
     * @return The name of the registry key.
     */
    @Override
    public String name() {
        return pathParts.getLast();
    }

    /**
     * Returns the full path to the registry key.
     *
     * @return The full path to the registry key.
     */
    @Override
    public String path() {
        return root.name() + SEPARATOR + path;
    }

    // traversal

    /**
     * Returns whether or not this registry key is a root registry key.
     *
     * @return {@code true} if this registry key is a root registry key, or {@code false} otherwise.
     * @see #root()
     * @see #HKEY_CLASSES_ROOT
     * @see #HKEY_CURRENT_USER
     * @see #HKEY_LOCAL_MACHINE
     * @see #HKEY_USERS
     * @see #HKEY_CURRENT_CONFIG
     */
    @Override
    public boolean isRoot() {
        return false;
    }

    /**
     * Returns the root of the registry key.
     *
     * @return The root of the registry key.
     * @see #isRoot()
     * @see #HKEY_CLASSES_ROOT
     * @see #HKEY_CURRENT_USER
     * @see #HKEY_LOCAL_MACHINE
     * @see #HKEY_USERS
     * @see #HKEY_CURRENT_CONFIG
     */
    @Override
    public RegistryKey root() {
        return root;
    }

    /**
     * Returns the parent registry key.
     *
     * @return An {@link Optional} with the parent registry key, or {@link Optional#empty()} if this registry key is a root key.
     */
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
    Collection<String> pathParts() {
        return pathParts;
    }

    @Override
    RegistryKey fromPathParts(Deque<String> pathParts) {
        return new SubKey(root, pathParts);
    }

    // other

    /**
     * Tests whether or not this registry key exists.
     *
     * @return {@code true} if this registry key exists, or {@code false} otherwise.
     * @throws RegistryException If the existence of this registry cannot be determined.
     */
    @Override
    public boolean exists() {
        HKEYByReference phkResult = new HKEYByReference();
        int code = api.RegOpenKeyEx(root.hKey, path, 0, WinNT.KEY_READ, phkResult);
        if (code == WinError.ERROR_SUCCESS) {
            closeKey(phkResult.getValue());
            return true;
        }
        if (code == WinError.ERROR_FILE_NOT_FOUND) {
            return false;
        }
        throw RegistryException.of(code, path());
    }

    /**
     * Creates this registry key if it does not exist already.
     *
     * @throws RegistryKeyAlreadyExistsException If this registry key already {@link #exists() exists}.
     * @throws RegistryException If this registry key cannot be created for another reason.
     */
    @Override
    public void create() {
        if (createOrOpen() == WinNT.REG_OPENED_EXISTING_KEY) {
            throw new RegistryKeyAlreadyExistsException(path());
        }
    }

    /**
     * Creates this registry key if it does not exist already.
     *
     * @return {@code true} if the registry key was created, or {@code false} if it already {@link #exists() existed}.
     * @throws RegistryException If this registry key cannot be created.
     */
    @Override
    public boolean createIfNotExists() {
        return createOrOpen() == WinNT.REG_CREATED_NEW_KEY;
    }

    private int createOrOpen() {
        HKEYByReference phkResult = new HKEYByReference();
        IntByReference lpdwDisposition = new IntByReference();

        int code = api.RegCreateKeyEx(root.hKey, path, 0, null, WinNT.REG_OPTION_NON_VOLATILE, WinNT.KEY_READ, null, phkResult, lpdwDisposition);
        if (code == WinError.ERROR_SUCCESS) {
            closeKey(phkResult.getValue());
            return lpdwDisposition.getValue();
        }
        throw RegistryException.of(code, path());
    }

    /**
     * Deletes this registry key and all of its values.
     *
     * @throws UnsupportedOperationException If trying to delete on of the root keys.
     * @throws NoSuchRegistryKeyException If this registry key does not {@link #exists() exist}.
     * @throws RegistryException If the registry key cannot be deleted for another reason.
     */
    @Override
    public void delete() {
        int code = api.RegDeleteKey(root.hKey, path);
        if (code != WinError.ERROR_SUCCESS) {
            throw RegistryException.of(code, path());
        }
    }

    /**
     * Deletes this registry key and all of its values if it exists.
     *
     * @return {@code true} if this registry key existed and has been removed, or {@code false} if it didn't {@link #exists() exist}.
     * @throws UnsupportedOperationException If trying to delete on of the root keys.
     * @throws RegistryException If the registry key cannot be deleted for another reason.
     */
    @Override
    public boolean deleteIfExists() {
        int code = api.RegDeleteKey(root.hKey, path);
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

    @Override
    public String toString() {
        return path();
    }

    private HKEY openKey(int samDesired) {
        HKEYByReference phkResult = new HKEYByReference();
        int code = api.RegOpenKeyEx(root.hKey, path, 0, samDesired, phkResult);
        if (code == WinError.ERROR_SUCCESS) {
            return phkResult.getValue();
        }
        throw RegistryException.of(code, path());
    }

    private HKEY createOrOpenKey(int samDesired) {
        HKEYByReference phkResult = new HKEYByReference();

        int code = api.RegCreateKeyEx(root.hKey, path, 0, null, WinNT.REG_OPTION_NON_VOLATILE, samDesired, null, phkResult, null);
        if (code == WinError.ERROR_SUCCESS) {
            return phkResult.getValue();
        }
        throw RegistryException.of(code, path());
    }

    private void closeKey(HKEY hKey) {
        int code = api.RegCloseKey(hKey);
        if (code != WinError.ERROR_SUCCESS) {
            throw RegistryException.of(code, path());
        }
    }

    final class Handle extends RegistryKey.Handle {

        private Handle(int samDesired, boolean create) {
            super(create ? createOrOpenKey(samDesired) : openKey(samDesired));
        }

        @Override
        public void close() {
            SubKey.this.closeKey(hKey);
        }

        @Override
        void close(RuntimeException exception) {
            int code = api.RegCloseKey(hKey);
            if (code != WinError.ERROR_SUCCESS) {
                exception.addSuppressed(RegistryException.of(code, path()));
            }
        }
    }
}
