/*
 * LocalSubKey.java
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

import static com.github.robtimus.os.windows.registry.foreign.Advapi32.RegRenameKey;
import static com.github.robtimus.os.windows.registry.foreign.WindowsConstants.ERROR_ACCESS_DENIED;
import static com.github.robtimus.os.windows.registry.foreign.WindowsConstants.ERROR_FILE_NOT_FOUND;
import static com.github.robtimus.os.windows.registry.foreign.WindowsConstants.ERROR_SUCCESS;
import static com.github.robtimus.os.windows.registry.foreign.WindowsConstants.KEY_READ;
import static com.github.robtimus.os.windows.registry.foreign.WindowsConstants.REG_CREATED_NEW_KEY;
import static com.github.robtimus.os.windows.registry.foreign.WindowsConstants.REG_OPENED_EXISTING_KEY;
import static com.github.robtimus.os.windows.registry.foreign.WindowsConstants.REG_OPTION_NON_VOLATILE;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.lang.foreign.ValueLayout;
import java.lang.ref.Cleaner;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.Optional;
import java.util.function.IntPredicate;
import com.github.robtimus.os.windows.registry.foreign.WString;
import com.github.robtimus.os.windows.registry.foreign.WindowsTypes.HKEY;

final class LocalSubKey extends RegistryKey {

    /*
     * Possible values:
     * - KEY_WOW64_32KEY (0x0200) for the 32-bit registry view
     * - KEY_WOW64_64KEY (0x0100) for the 64-bit registry view
     */
    private static final int SAM_DESIRED_REGISTRY_VIEW = 0;

    private final LocalRootKey root;

    private final String path;
    private final Deque<String> pathParts;

    LocalSubKey(LocalRootKey root, Deque<String> pathParts) {
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
        LocalSubKey parent = new LocalSubKey(root, parentPathParts);
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

    boolean exists(MemorySegment rootHKey, SegmentAllocator allocator, String machineName) {
        int code = checkSubKey(rootHKey, allocator, machineName);
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
        try (Arena allocator = Arena.ofConfined()) {
            return isAccessible(root.hKey(), allocator, machineName());
        }
    }

    boolean isAccessible(MemorySegment rootHKey, SegmentAllocator allocator, String machineName) {
        int code = checkSubKey(rootHKey, allocator, machineName);
        if (code == ERROR_SUCCESS) {
            return true;
        }
        if (code == ERROR_FILE_NOT_FOUND || code == ERROR_ACCESS_DENIED) {
            return false;
        }
        throw RegistryException.forKey(code, path(), machineName);
    }

    private int checkSubKey(MemorySegment rootHKey, SegmentAllocator allocator, String machineName) {
        MemorySegment lpSubKey = WString.allocate(allocator, path);
        MemorySegment phkResult = HKEY.allocateRef(allocator);

        int code = Registry.currentContext().openKey(
                rootHKey,
                lpSubKey,
                0,
                KEY_READ | SAM_DESIRED_REGISTRY_VIEW,
                phkResult);
        if (code == ERROR_SUCCESS) {
            closeKey(HKEY.target(phkResult), path(), machineName);
        }
        return code;
    }

    @Override
    public void create() {
        try (Arena allocator = Arena.ofConfined()) {
            create(root.hKey(), allocator, machineName());
        }
    }

    void create(MemorySegment rootHKey, SegmentAllocator allocator, String machineName) {
        if (createOrOpen(rootHKey, allocator, machineName) == REG_OPENED_EXISTING_KEY) {
            throw new RegistryKeyAlreadyExistsException(path(), machineName);
        }
    }

    @Override
    public boolean createIfNotExists() {
        try (Arena allocator = Arena.ofConfined()) {
            return createIfNotExists(root.hKey(), allocator, machineName());
        }
    }

    boolean createIfNotExists(MemorySegment rootHKey, SegmentAllocator allocator, String machineName) {
        return createOrOpen(rootHKey, allocator, machineName) == REG_CREATED_NEW_KEY;
    }

    private int createOrOpen(MemorySegment rootHKey, SegmentAllocator allocator, String machineName) {
        MemorySegment lpSubKey = WString.allocate(allocator, path);
        MemorySegment phkResult = HKEY.allocateRef(allocator);
        MemorySegment lpdwDisposition = allocator.allocate(ValueLayout.JAVA_INT);

        int code = Registry.currentContext().createKey(
                rootHKey,
                lpSubKey,
                REG_OPTION_NON_VOLATILE,
                KEY_READ | SAM_DESIRED_REGISTRY_VIEW,
                phkResult,
                lpdwDisposition);
        if (code == ERROR_SUCCESS) {
            closeKey(HKEY.target(phkResult), path(), machineName);
            return lpdwDisposition.get(ValueLayout.JAVA_INT, 0);
        }
        throw RegistryException.forKey(code, path(), machineName);
    }

    @Override
    public RegistryKey renameTo(String newName) {
        try (Arena allocator = Arena.ofConfined()) {
            return renameTo(root.hKey(), newName, allocator, machineName());
        }
    }

    LocalSubKey renameTo(MemorySegment rootHKey, String newName, SegmentAllocator allocator, String machineName) {
        if (newName.contains(SEPARATOR)) {
            throw new IllegalArgumentException(Messages.RegistryKey.nameContainsBackslash(newName));
        }

        Deque<String> newPathParts = new ArrayDeque<>(pathParts);
        newPathParts.removeLast();
        newPathParts.addLast(newName);
        LocalSubKey renamed = new LocalSubKey(root, newPathParts);

        MemorySegment lpSubKeyName = WString.allocate(allocator, path);
        MemorySegment lpNewKeyName = WString.allocate(allocator, newName);

        int code = RegRenameKey(rootHKey, lpSubKeyName, lpNewKeyName);
        if (code == ERROR_SUCCESS) {
            return renamed;
        }
        if (code == ERROR_ACCESS_DENIED && renamed.exists(rootHKey, allocator, machineName)) {
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

    void delete(MemorySegment rootHKey, SegmentAllocator allocator, String machineName) {
        MemorySegment lpSubKey = WString.allocate(allocator, path);

        int code = Registry.currentContext().deleteKey(
                rootHKey,
                lpSubKey,
                SAM_DESIRED_REGISTRY_VIEW);
        if (code != ERROR_SUCCESS) {
            throw RegistryException.forKey(code, path(), machineName);
        }
    }

    @Override
    public boolean deleteIfExists() {
        try (Arena allocator = Arena.ofConfined()) {
            return deleteIfExists(root.hKey(), allocator, machineName());
        }
    }

    boolean deleteIfExists(MemorySegment rootHKey, SegmentAllocator allocator, String machineName) {
        MemorySegment lpSubKey = WString.allocate(allocator, path);

        int code = Registry.currentContext().deleteKey(
                rootHKey,
                lpSubKey,
                SAM_DESIRED_REGISTRY_VIEW);
        if (code == ERROR_SUCCESS) {
            return true;
        }
        if (code == ERROR_FILE_NOT_FOUND) {
            return false;
        }
        throw RegistryException.forKey(code, path(), machineName);
    }

    // handles

    @Override
    RegistryKey.Handle handle(int samDesired, boolean create) {
        try (Arena allocator = Arena.ofConfined()) {
            MemorySegment hKey = hKey(samDesired, create, allocator);
            return new Handle(hKey);
        }
    }

    @Override
    Optional<RegistryKey.Handle> handle(int samDesired, IntPredicate ignoreError) {
        try (Arena allocator = Arena.ofConfined()) {
            MemorySegment hKey = hKey(samDesired, ignoreError, allocator);
            return Optional.ofNullable(hKey)
                    .map(Handle::new);
        }
    }

    private MemorySegment hKey(int samDesired, boolean create, SegmentAllocator allocator) {
        return hKey(root.hKey(), samDesired, create, allocator, machineName());
    }

    private MemorySegment hKey(int samDesired, IntPredicate ignoreError, SegmentAllocator allocator) {
        return hKey(root.hKey(), samDesired, ignoreError, allocator, machineName());
    }

    MemorySegment hKey(MemorySegment rootHKEY, int samDesired, boolean create, SegmentAllocator allocator, String machineName) {
        return create
                ? createOrOpenKey(rootHKEY, samDesired, allocator, machineName)
                : openKey(rootHKEY, samDesired, _ -> false, allocator, machineName);
    }

    MemorySegment hKey(MemorySegment rootHKEY, int samDesired, IntPredicate ignoreError, SegmentAllocator allocator, String machineName) {
        return openKey(rootHKEY, samDesired, ignoreError, allocator, machineName);
    }

    private MemorySegment createOrOpenKey(MemorySegment rootHKey, int samDesired, SegmentAllocator allocator, String machineName) {
        MemorySegment lpSubKey = WString.allocate(allocator, path);
        MemorySegment phkResult = HKEY.allocateRef(allocator);

        int code = Registry.currentContext().createKey(
                rootHKey,
                lpSubKey,
                REG_OPTION_NON_VOLATILE,
                samDesired | SAM_DESIRED_REGISTRY_VIEW,
                phkResult,
                MemorySegment.NULL);
        if (code == ERROR_SUCCESS) {
            return HKEY.target(phkResult);
        }
        throw RegistryException.forKey(code, path(), machineName);
    }

    private MemorySegment openKey(MemorySegment rootHKey, int samDesired, IntPredicate ignoreError, SegmentAllocator allocator, String machineName) {
        MemorySegment lpSubKey = WString.allocate(allocator, path);
        MemorySegment phkResult = HKEY.allocateRef(allocator);

        int code = Registry.currentContext().openKey(
                rootHKey,
                lpSubKey,
                0,
                samDesired | SAM_DESIRED_REGISTRY_VIEW,
                phkResult);
        if (code == ERROR_SUCCESS) {
            return HKEY.target(phkResult);
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
        LocalSubKey other = (LocalSubKey) o;
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

        private Handle(MemorySegment hKey) {
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
