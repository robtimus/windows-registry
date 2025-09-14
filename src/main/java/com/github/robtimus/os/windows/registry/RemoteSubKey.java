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

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.lang.ref.Cleaner;
import java.util.Optional;
import java.util.function.IntPredicate;

final class RemoteSubKey extends RegistryKey {

    private final RemoteRootKey root;
    private final LocalSubKey local;

    RemoteSubKey(RemoteRootKey root, LocalSubKey local) {
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
                .map(p -> p.isRoot() ? root : new RemoteSubKey(root, (LocalSubKey) p));
    }

    @Override
    public RegistryKey resolve(String relativePath) {
        RegistryKey resolved = local.resolve(relativePath);
        return resolved.isRoot() ? root : new RemoteSubKey(root, (LocalSubKey) resolved);
    }

    @Override
    RegistryKey resolveChild(String name) {
        RegistryKey resolved = local.resolveChild(name);
        return new RemoteSubKey(root, (LocalSubKey) resolved);
    }

    // other

    @Override
    public boolean exists() {
        try (Arena allocator = Arena.ofConfined()) {
            return local.exists(root.hKey(), allocator, machineName());
        }
    }

    @Override
    public boolean isAccessible() {
        try (Arena allocator = Arena.ofConfined()) {
            return local.isAccessible(root.hKey(), allocator, machineName());
        }
    }

    @Override
    public void create() {
        try (Arena allocator = Arena.ofConfined()) {
            local.create(root.hKey(), allocator, machineName());
        }
    }

    @Override
    public boolean createIfNotExists() {
        try (Arena allocator = Arena.ofConfined()) {
            return local.createIfNotExists(root.hKey(), allocator, machineName());
        }
    }

    @Override
    public RegistryKey renameTo(String newName) {
        try (Arena allocator = Arena.ofConfined()) {
            LocalSubKey renamed = local.renameTo(root.hKey(), newName, allocator, machineName());
            return new RemoteSubKey(root, renamed);
        }
    }

    @Override
    public void delete() {
        try (Arena allocator = Arena.ofConfined()) {
            local.delete(root.hKey(), allocator, machineName());
        }
    }

    @Override
    public boolean deleteIfExists() {
        try (Arena allocator = Arena.ofConfined()) {
            return local.deleteIfExists(root.hKey(), allocator, machineName());
        }
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
        return local.hKey(root.hKey(), samDesired, create, allocator, machineName());
    }

    private MemorySegment hKey(int samDesired, IntPredicate ignoreError, SegmentAllocator allocator) {
        return local.hKey(root.hKey(), samDesired, ignoreError, allocator, machineName());
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
