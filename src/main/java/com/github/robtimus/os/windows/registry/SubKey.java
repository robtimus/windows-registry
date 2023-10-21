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

import java.lang.foreign.Arena;
import java.lang.foreign.SegmentAllocator;
import java.lang.ref.Cleaner;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.Optional;
import java.util.function.IntPredicate;
import com.github.robtimus.os.windows.registry.foreign.IntPointer;
import com.github.robtimus.os.windows.registry.foreign.StringPointer;
import com.github.robtimus.os.windows.registry.foreign.WinDef.HKEY;
import com.github.robtimus.os.windows.registry.foreign.WinError;
import com.github.robtimus.os.windows.registry.foreign.WinNT;

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
        try (Arena allocator = Arena.ofConfined()) {
            return exists(root.hKey(), allocator, machineName());
        }
    }

    boolean exists(HKEY rootHKey, SegmentAllocator allocator, String machineName) {
        int code = checkSubKey(rootHKey, allocator, machineName);
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
        try (Arena allocator = Arena.ofConfined()) {
            return isAccessible(root.hKey(), allocator, machineName());
        }
    }

    boolean isAccessible(HKEY rootHKey, SegmentAllocator allocator, String machineName) {
        int code = checkSubKey(rootHKey, allocator, machineName);
        if (code == WinError.ERROR_SUCCESS) {
            return true;
        }
        if (code == WinError.ERROR_FILE_NOT_FOUND || code == WinError.ERROR_ACCESS_DENIED) {
            return false;
        }
        throw RegistryException.forKey(code, path(), machineName);
    }

    private int checkSubKey(HKEY rootHKey, SegmentAllocator allocator, String machineName) {
        StringPointer lpSubKey = StringPointer.withValue(path, allocator);
        HKEY.Reference phkResult = HKEY.uninitializedReference(allocator);

        int code = api.RegOpenKeyEx(rootHKey, lpSubKey, 0, WinNT.KEY_READ, phkResult);
        if (code == WinError.ERROR_SUCCESS) {
            closeKey(phkResult.value(), path(), machineName);
        }
        return code;
    }

    @Override
    public void create() {
        try (Arena allocator = Arena.ofConfined()) {
            create(root.hKey(), allocator, machineName());
        }
    }

    void create(HKEY rootHKey, SegmentAllocator allocator, String machineName) {
        if (createOrOpen(rootHKey, allocator, machineName) == WinNT.REG_OPENED_EXISTING_KEY) {
            throw new RegistryKeyAlreadyExistsException(path(), machineName);
        }
    }

    @Override
    public boolean createIfNotExists() {
        try (Arena allocator = Arena.ofConfined()) {
            return createIfNotExists(root.hKey(), allocator, machineName());
        }
    }

    boolean createIfNotExists(HKEY rootHKey, SegmentAllocator allocator, String machineName) {
        return createOrOpen(rootHKey, allocator, machineName) == WinNT.REG_CREATED_NEW_KEY;
    }

    private int createOrOpen(HKEY rootHKey, SegmentAllocator allocator, String machineName) {
        StringPointer lpSubKey = StringPointer.withValue(path, allocator);
        HKEY.Reference phkResult = HKEY.uninitializedReference(allocator);
        IntPointer lpdwDisposition = IntPointer.uninitialized(allocator);

        int code = api.RegCreateKeyEx(rootHKey, lpSubKey, 0, null, WinNT.REG_OPTION_NON_VOLATILE, WinNT.KEY_READ, null, phkResult, lpdwDisposition);
        if (code == WinError.ERROR_SUCCESS) {
            closeKey(phkResult.value(), path(), machineName);
            return lpdwDisposition.value();
        }
        throw RegistryException.forKey(code, path(), machineName);
    }

    @Override
    public RegistryKey renameTo(String newName) {
        try (Arena allocator = Arena.ofConfined()) {
            return renameTo(root.hKey(), newName, allocator, machineName());
        }
    }

    SubKey renameTo(HKEY rootHKey, String newName, SegmentAllocator allocator, String machineName) {
        if (newName.contains(SEPARATOR)) {
            throw new IllegalArgumentException(Messages.RegistryKey.nameContainsBackslash(newName));
        }

        Deque<String> newPathParts = new ArrayDeque<>(pathParts);
        newPathParts.removeLast();
        newPathParts.addLast(newName);
        SubKey renamed = new SubKey(root, newPathParts);

        StringPointer lpSubKeyName = StringPointer.withValue(path, allocator);
        StringPointer lpNewKeyName = StringPointer.withValue(newName, allocator);

        int code = api.RegRenameKey(rootHKey, lpSubKeyName, lpNewKeyName);
        if (code == WinError.ERROR_SUCCESS) {
            return renamed;
        }
        if (code == WinError.ERROR_ACCESS_DENIED && renamed.exists(rootHKey, allocator, machineName)) {
            throw new RegistryKeyAlreadyExistsException(renamed.path(), machineName);
        }
        throw RegistryException.forKey(code, path(), machineName);
    }

    @Override
    public void delete() {
        try (Arena allocator = Arena.ofConfined()) {
            delete(root.hKey(), allocator, machineName());
        }
    }

    void delete(HKEY rootHKey, SegmentAllocator allocator, String machineName) {
        StringPointer lpSubKey = StringPointer.withValue(path, allocator);

        int code = api.RegDeleteKey(rootHKey, lpSubKey);
        if (code != WinError.ERROR_SUCCESS) {
            throw RegistryException.forKey(code, path(), machineName);
        }
    }

    @Override
    public boolean deleteIfExists() {
        try (Arena allocator = Arena.ofConfined()) {
            return deleteIfExists(root.hKey(), allocator, machineName());
        }
    }

    boolean deleteIfExists(HKEY rootHKey, SegmentAllocator allocator, String machineName) {
        StringPointer lpSubKey = StringPointer.withValue(path, allocator);

        int code = api.RegDeleteKey(rootHKey, lpSubKey);
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
    RegistryKey.Handle handle(int samDesired, boolean create) {
        @SuppressWarnings("resource")
        Arena hKeyAllocator = Arena.ofShared();
        try (Arena allocator = Arena.ofConfined()) {
            HKEY hKey = hKey(samDesired, create, allocator, hKeyAllocator);
            return new Handle(hKey, hKeyAllocator);
        } catch (RuntimeException e) {
            hKeyAllocator.close();
            throw e;
        }
    }

    @Override
    Optional<RegistryKey.Handle> handle(int samDesired, IntPredicate ignoreError) {
        @SuppressWarnings("resource")
        Arena hKeyAllocator = Arena.ofShared();
        try (Arena allocator = Arena.ofConfined()) {
            HKEY hKey = hKey(samDesired, ignoreError, allocator, hKeyAllocator);
            return Optional.ofNullable(hKey)
                    .map(hk -> new Handle(hk, hKeyAllocator));
        } catch (RuntimeException e) {
            hKeyAllocator.close();
            throw e;
        }
    }

    private HKEY hKey(int samDesired, boolean create, SegmentAllocator allocator, SegmentAllocator hKeyAllocator) {
        return hKey(root.hKey(), samDesired, create, allocator, hKeyAllocator, machineName());
    }

    private HKEY hKey(int samDesired, IntPredicate ignoreError, SegmentAllocator allocator, SegmentAllocator hKeyAllocator) {
        return hKey(root.hKey(), samDesired, ignoreError, allocator, hKeyAllocator, machineName());
    }

    HKEY hKey(HKEY rootHKEY, int samDesired, boolean create, SegmentAllocator allocator, SegmentAllocator hKeyAllocator, String machineName) {
        return create
                ? createOrOpenKey(rootHKEY, samDesired, allocator, hKeyAllocator, machineName)
                : openKey(rootHKEY, samDesired, error -> false, allocator, hKeyAllocator, machineName);
    }

    HKEY hKey(HKEY rootHKEY, int samDesired, IntPredicate ignoreError, SegmentAllocator allocator, SegmentAllocator hKeyAllocator,
            String machineName) {

        return openKey(rootHKEY, samDesired, ignoreError, allocator, hKeyAllocator, machineName);
    }

    private HKEY createOrOpenKey(HKEY rootHKey, int samDesired, SegmentAllocator allocator, SegmentAllocator hKeyAllocator, String machineName) {
        StringPointer lpSubKey = StringPointer.withValue(path, allocator);
        HKEY.Reference phkResult = HKEY.uninitializedReference(hKeyAllocator);

        int code = api.RegCreateKeyEx(rootHKey, lpSubKey, 0, null, WinNT.REG_OPTION_NON_VOLATILE, samDesired, null, phkResult, null);
        if (code == WinError.ERROR_SUCCESS) {
            return phkResult.value();
        }
        throw RegistryException.forKey(code, path(), machineName);
    }

    private HKEY openKey(HKEY rootHKey, int samDesired, IntPredicate ignoreError, SegmentAllocator allocator, SegmentAllocator hKeyAllocator,
            String machineName) {

        StringPointer lpSubKey = StringPointer.withValue(path, allocator);
        HKEY.Reference phkResult = HKEY.uninitializedReference(hKeyAllocator);

        int code = api.RegOpenKeyEx(rootHKey, lpSubKey, 0, samDesired, phkResult);
        if (code == WinError.ERROR_SUCCESS) {
            return phkResult.value();
        }
        if (ignoreError.test(code)) {
            return null;
        }
        throw RegistryException.forKey(code, path(), machineName);
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

    private final class Handle extends RegistryKey.Handle {

        private final Cleaner.Cleanable cleanable;

        private Handle(HKEY hKey, Arena allocator) {
            super(hKey);
            this.cleanable = closeOnClean(this, hKey, allocator, path(), machineName());
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
