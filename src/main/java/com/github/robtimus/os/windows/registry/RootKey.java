/*
 * RootKey.java
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

import java.lang.foreign.MemorySegment;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.Optional;
import java.util.function.IntPredicate;
import java.util.regex.Pattern;
import com.github.robtimus.os.windows.registry.foreign.WinReg;

final class RootKey extends RegistryKey {

    static final RootKey HKEY_CLASSES_ROOT = new RootKey(WinReg.HKEY_CLASSES_ROOT, "HKEY_CLASSES_ROOT"); //$NON-NLS-1$

    static final RootKey HKEY_CURRENT_USER = new RootKey(WinReg.HKEY_CURRENT_USER, "HKEY_CURRENT_USER"); //$NON-NLS-1$

    static final RootKey HKEY_LOCAL_MACHINE = new RootKey(WinReg.HKEY_LOCAL_MACHINE, "HKEY_LOCAL_MACHINE"); //$NON-NLS-1$

    static final RootKey HKEY_USERS = new RootKey(WinReg.HKEY_USERS, "HKEY_USERS"); //$NON-NLS-1$

    static final RootKey HKEY_CURRENT_CONFIG = new RootKey(WinReg.HKEY_CURRENT_CONFIG, "HKEY_CURRENT_CONFIG"); //$NON-NLS-1$

    private static final Pattern PATH_SPLIT_PATTERN = Pattern.compile(Pattern.quote(SEPARATOR));

    private final MemorySegment hKey;
    private final String name;
    private final Handle handle;

    RootKey(MemorySegment hKey, String name) {
        this.hKey = hKey;
        this.name = name;
        this.handle = new Handle();
    }

    MemorySegment hKey() {
        return hKey;
    }

    // structural

    @Override
    public String name() {
        return name;
    }

    @Override
    public String path() {
        return name();
    }

    @Override
    String machineName() {
        return null;
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
        if (relativePath.isEmpty() || ".".equals(relativePath)) { //$NON-NLS-1$
            return this;
        }
        return resolve(relativePath, Collections.emptyList());
    }

    RegistryKey resolve(String relativePath, Collection<String> pathParts) {
        Deque<String> result = new ArrayDeque<>(pathParts);
        String[] relativePathParts = PATH_SPLIT_PATTERN.split(relativePath);
        for (String relativePathPart : relativePathParts) {
            if ("..".equals(relativePathPart)) { //$NON-NLS-1$
                if (!result.isEmpty()) {
                    result.removeLast();
                }
            } else if (!(relativePathPart.isEmpty() || ".".equals(relativePathPart))) { //$NON-NLS-1$
                result.addLast(relativePathPart);
            }
        }

        return result.isEmpty() ? this : new SubKey(this, result);
    }

    @Override
    RegistryKey resolveChild(String name) {
        return resolveChild(name, Collections.emptyList());
    }

    RegistryKey resolveChild(String name, Collection<String> pathParts) {
        Deque<String> newPathParts = new ArrayDeque<>(pathParts.size() + 1);
        newPathParts.addAll(pathParts);
        newPathParts.add(name);
        return new SubKey(this, newPathParts);
    }

    // other

    @Override
    public boolean exists() {
        return true;
    }

    @Override
    public boolean isAccessible() {
        // The pre-defined root keys are always accessible
        return true;
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
        return handle;
    }

    @Override
    Optional<RegistryKey.Handle> handle(int samDesired, IntPredicate ignoreError) {
        return Optional.of(handle);
    }

    // Comparable / Object

    @Override
    public boolean equals(Object o) {
        return this == o;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(this);
    }

    private final class Handle extends RegistryKey.Handle {

        private Handle() {
            super(RootKey.this.hKey);
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
