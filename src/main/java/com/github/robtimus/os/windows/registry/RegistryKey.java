/*
 * RegistryKey.java
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import com.sun.jna.Native;
import com.sun.jna.platform.win32.Advapi32;
import com.sun.jna.platform.win32.WinError;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.platform.win32.WinReg;
import com.sun.jna.platform.win32.WinReg.HKEY;
import com.sun.jna.platform.win32.WinReg.HKEYByReference;
import com.sun.jna.ptr.IntByReference;

/**
 * A representation of registry keys.
 *
 * @author Rob Spoor
 */
public final class RegistryKey implements Comparable<RegistryKey> {

    /** The HKEY_CLASSES_ROOT root key. */
    public static final RegistryKey HKEY_CLASSES_ROOT = new RegistryKey(WinReg.HKEY_CLASSES_ROOT, "HKEY_CLASSES_ROOT"); //$NON-NLS-1$

    /** The HKEY_CURRENT_USER root key. */
    public static final RegistryKey HKEY_CURRENT_USER = new RegistryKey(WinReg.HKEY_CURRENT_USER, "HKEY_CURRENT_USER"); //$NON-NLS-1$

    /** The HKEY_LOCAL_MACHINE root key. */
    public static final RegistryKey HKEY_LOCAL_MACHINE = new RegistryKey(WinReg.HKEY_LOCAL_MACHINE, "HKEY_LOCAL_MACHINE"); //$NON-NLS-1$

    /** The HKEY_USERS root key. */
    public static final RegistryKey HKEY_USERS = new RegistryKey(WinReg.HKEY_USERS, "HKEY_USERS"); //$NON-NLS-1$

    /** The HKEY_CURRENT_CONFIG root key. */
    public static final RegistryKey HKEY_CURRENT_CONFIG = new RegistryKey(WinReg.HKEY_CURRENT_CONFIG, "HKEY_CURRENT_CONFIG"); //$NON-NLS-1$

    private static final String SEPARATOR = "\\"; //$NON-NLS-1$

    private static final Pattern PATH_SPLIT_PATTERN = Pattern.compile(Pattern.quote(SEPARATOR));

    private static Advapi32 api = Advapi32.INSTANCE;

    private final RegistryKey root;
    private final HKEY rootHKEY;

    private final String name;
    private final String path;
    private final String pathFromRoot;
    private final String[] pathParts;

    private RegistryKey(HKEY rootHKEY, String name) {
        this.root = this;
        this.rootHKEY = rootHKEY;

        this.name = name;
        this.path = name;
        this.pathFromRoot = ""; //$NON-NLS-1$
        this.pathParts = new String[] { path };
    }

    private RegistryKey(RegistryKey root, String[] pathParts) {
        this.root = root;
        this.rootHKEY = root.rootHKEY;

        this.name = pathParts[pathParts.length - 1];
        this.path = String.join(SEPARATOR, pathParts);
        this.pathFromRoot = path.substring(root.path.length() + 1);
        this.pathParts = pathParts;
    }

    private RegistryKey(RegistryKey parent, String name) {
        this.root = parent.root;
        this.rootHKEY = parent.rootHKEY;

        this.name = name;
        this.path = parent.path + SEPARATOR + name;
        this.pathFromRoot = path.substring(root.path.length() + 1);
        this.pathParts = Arrays.copyOfRange(parent.pathParts, 0, parent.pathParts.length + 1);
        this.pathParts[pathParts.length - 1] = name;
    }

    // structural

    /**
     * Returns the name of the registry key.
     *
     * @return The name of the registry key.
     */
    public String name() {
        return name;
    }

    /**
     * Returns the full path to the registry key.
     *
     * @return The full path to the registry key.
     */
    public String path() {
        return path;
    }

    // traversal

    /**
     * Returns the root of the registry key.
     *
     * @return The root of the registry key.
     */
    public RegistryKey root() {
        return root;
    }

    /**
     * Returns the parent registry key.
     *
     * @return An {@link Optional} with the parent registry key, or {@link Optional#empty()} if this is a root key.
     */
    public Optional<RegistryKey> parent() {
        if (pathParts.length == 1) {
            return Optional.empty();
        }
        if (pathParts.length == 2) {
            return Optional.of(root);
        }
        String[] parentPathParts = Arrays.copyOfRange(pathParts, 0, pathParts.length - 1);
        RegistryKey parent = new RegistryKey(root, parentPathParts);
        return Optional.of(parent);
    }

    /**
     * Returns a registry key relative to this registry key.
     * If the relative path is empty or a single {@code .}, this registry key is returned.
     * <p>
     * Note that this method will never leave the root key.
     *
     * @param relativePath The path for the new registry key, relative to this registry key.
     * @return The resulting registry key.
     * @throws NullPointerException If the given relative path is {@code null}.
     */
    public RegistryKey resolve(String relativePath) {
        if (relativePath.isEmpty() || ".".equals(relativePath)) { //$NON-NLS-1$
            return this;
        }

        Deque<String> result = new ArrayDeque<>(Arrays.asList(pathParts));
        String[] relativePathParts = PATH_SPLIT_PATTERN.split(relativePath);
        for (String relativePathPart : relativePathParts) {
            if ("..".equals(relativePathPart)) { //$NON-NLS-1$
                // the first entry of result is the root key, which can never be removed
                if (result.size() > 1) {
                    result.removeLast();
                }
            } else if (!(relativePath.isEmpty() || ".".equals(relativePathPart))) { //$NON-NLS-1$
                result.addLast(relativePathPart);
            }
        }

        if (result.size() == 1) {
            return root;
        }

        return new RegistryKey(root, result.toArray(new String[0]));
    }

    /**
     * Returns all direct sub keys of this registry key. This stream should be closed afterwards.
     *
     * @return A stream with all direct sub keys of this registry key.
     * @throws NoSuchRegistryKeyException If this registry key does not {@link #exists() exist}.
     * @throws RegistryException If the sub keys cannot be queried for another reason.
     */
    public Stream<RegistryKey> subKeys() {
        HKEY hKey = openKey(WinNT.KEY_READ);

        Iterator<String> iterator = subKeys(hKey);
        Spliterator<String> spliterator = Spliterators.spliteratorUnknownSize(iterator, Spliterator.NONNULL);
        return StreamSupport.stream(spliterator, false)
                .onClose(() -> closeKey(hKey))
                .map(n -> new RegistryKey(this, n));
    }

    private Iterator<String> subKeys(HKEY hKey) {
        IntByReference lpcMaxSubKeyLen = new IntByReference();
        int code = api.RegQueryInfoKey(hKey, null, null, null, null, lpcMaxSubKeyLen, null, null, null, null, null, null);
        if (code != WinError.ERROR_SUCCESS) {
            throw RegistryException.of(code, path);
        }

        char[] lpName = new char[lpcMaxSubKeyLen.getValue() + 1];
        IntByReference lpcName = new IntByReference(lpName.length);

        return new LookaheadIterator<>() {

            private int index = 0;

            @Override
            protected String nextElement() {
                lpcName.setValue(lpName.length);

                int code = api.RegEnumKeyEx(hKey, index, lpName, lpcName, null, null, null, null);
                if (code == WinError.ERROR_SUCCESS) {
                    index++;
                    return Native.toString(lpName);
                }
                if (code == WinError.ERROR_NO_MORE_ITEMS) {
                    return null;
                }
                throw RegistryException.of(code, path);
            }
        };
    }

    /**
     * Deletes all direct sub keys of this registry keys whose names match a specific predicate.
     *
     * @param namePredicate The predicate to use.
     * @throws NoSuchRegistryKeyException If this registry key does not {@link #exists() exist}.
     * @throws RegistryException If the sub keys cannot be deleted for another reason.
     */
    public void deleteSubKeys(Predicate<? super String> namePredicate) {
        try (Key key = new Key(openKey(WinNT.KEY_READ))) {
            List<String> subKeys = new ArrayList<>();
            for (Iterator<String> i = subKeys(key.hKey); i.hasNext(); ) {
                String subKey = i.next();
                if (namePredicate.test(subKey)) {
                    subKeys.add(subKey);
                }
            }
            for (String subKey : subKeys) {
                deleteSubKey(key.hKey, subKey);
            }
        }
    }

    private void deleteSubKey(HKEY hKey, String subKey) {
        int code = api.RegDeleteKey(hKey, subKey);
        if (code != WinError.ERROR_SUCCESS && code != WinError.ERROR_FILE_NOT_FOUND) {
            throw RegistryException.of(code, path);
        }
    }

    // values

    /**
     * Returns a registry value.
     *
     * @param name The name of the registry value to return.
     * @return An {@link Optional} with the registry value with the given name, or {@link Optional#empty()} if there is no such registry value.
     * @throws NullPointerException If the given name is {@code null}.
     * @throws NoSuchRegistryKeyException If this registry key does not {@link #exists() exist}.
     * @throws RegistryException If the value cannot be returned for another reason.
     */
    public Optional<RegistryValue> getValue(String name) {
        Objects.requireNonNull(name);

        try (Key key = new Key(openKey(WinNT.KEY_READ | WinNT.KEY_QUERY_VALUE))) {
            IntByReference lpType = new IntByReference();
            IntByReference lpcbData = new IntByReference();
            int code = api.RegQueryValueEx(key.hKey, name, 0, lpType, (byte[]) null, lpcbData);
            if (code == WinError.ERROR_FILE_NOT_FOUND) {
                return Optional.empty();
            }
            if (code == WinError.ERROR_SUCCESS || code == WinError.ERROR_MORE_DATA) {
                byte[] byteData = new byte[lpcbData.getValue() + Native.WCHAR_SIZE];
                Arrays.fill(byteData, (byte) 0);
                lpcbData.setValue(0);

                code = api.RegQueryValueEx(key.hKey, name, 0, null, byteData, lpcbData);
                if (code == WinError.ERROR_SUCCESS) {
                    RegistryValue value = RegistryValue.of(name, lpType.getValue(), byteData, lpcbData.getValue());
                    return Optional.of(value);
                }
            }
            throw RegistryException.of(code, path, name);
        }
    }

    /**
     * Sets a registry value.
     *
     * @param value The registry value to set.
     * @throws NullPointerException If the given name is {@code null}.
     * @throws NoSuchRegistryKeyException If this registry key does not {@link #exists() exist}.
     * @throws RegistryException If the value cannot be set for another reason.
     */
    public void setValue(RegistryValue value) {
        Objects.requireNonNull(value);

        try (Key key = new Key(openKey(WinNT.KEY_READ | WinNT.KEY_SET_VALUE))) {
            byte[] data = value.rawData();
            int code = api.RegSetValueEx(key.hKey, value.name(), 0, value.type(), data, data.length);
            if (code != WinError.ERROR_SUCCESS) {
                throw RegistryException.of(code, path, name);
            }
        }
    }

    /**
     * Deletes a registry value.
     *
     * @param name The name of the registry value to exist.
     * @throws NoSuchRegistryKeyException If this registry key does not {@link #exists() exist}.
     * @throws NoSuchRegistryValueException If the registry value does not exist.
     * @throws RegistryException If the value cannot be deleted for another reason.
     */
    public void deleteValue(String name) {
        Objects.requireNonNull(name);

        try (Key key = new Key(openKey(WinNT.KEY_READ | WinNT.KEY_SET_VALUE))) {
            int code = api.RegDeleteValue(key.hKey, name);
            if (code != WinError.ERROR_SUCCESS) {
                throw RegistryException.of(code, path);
            }
        }
    }

    /**
     * Deletes a registry value if it exists.
     *
     * @param name The name of the registry value to exist.
     * @return {@code true} if the registry value existed and was deleted, or {@code false} if it didn't exist.
     * @throws NoSuchRegistryKeyException If this registry key does not {@link #exists() exist}.
     * @throws RegistryException If the value cannot be deleted for another reason.
     */
    public boolean deleteValueIfExists(String name) {
        Objects.requireNonNull(name);

        try (Key key = new Key(openKey(WinNT.KEY_READ | WinNT.KEY_SET_VALUE))) {
            int code = api.RegDeleteValue(key.hKey, name);
            if (code == WinError.ERROR_FILE_NOT_FOUND) {
                return false;
            }
            if (code == WinError.ERROR_SUCCESS) {
                return true;
            }
            throw RegistryException.of(code, path);
        }
    }

    /**
     * Returns all values of this registry key. This stream should be closed afterwards.
     *
     * @return A stream with all values of this registry key.
     * @throws NoSuchRegistryKeyException If this registry key does not {@link #exists() exist}.
     * @throws RegistryException If the values cannot be queried for another reason.
     */
    public Stream<RegistryValue> values() {
        HKEY hKey = openKey(WinNT.KEY_READ);

        Iterator<RegistryValue> iterator = values(hKey);
        Spliterator<RegistryValue> spliterator = Spliterators.spliteratorUnknownSize(iterator, Spliterator.NONNULL);
        return StreamSupport.stream(spliterator, false).onClose(() -> closeKey(hKey));
    }

    private Iterator<RegistryValue> values(HKEY hKey) {
        IntByReference lpcMaxValueNameLen = new IntByReference();
        IntByReference lpcMaxValueLen = new IntByReference();
        int code = api.RegQueryInfoKey(hKey, null, null, null, null, null, null, null, lpcMaxValueNameLen, lpcMaxValueLen, null, null);
        if (code != WinError.ERROR_SUCCESS) {
            throw RegistryException.of(code, path);
        }

        char[] lpValueName = new char[lpcMaxValueNameLen.getValue() + 1];
        IntByReference lpcchValueName = new IntByReference(lpValueName.length);

        IntByReference lpType = new IntByReference();

        byte[] byteData = new byte[lpcMaxValueLen.getValue() + 2 * Native.WCHAR_SIZE];
        IntByReference lpcbData = new IntByReference(byteData.length);

        return new LookaheadIterator<>() {

            private int index = 0;

            @Override
            protected RegistryValue nextElement() {
                lpcchValueName.setValue(lpValueName.length);
                lpType.setValue(0);
                Arrays.fill(byteData, (byte) 0);
                lpcbData.setValue(lpcMaxValueLen.getValue());

                int code = api.RegEnumValue(hKey, index, lpValueName, lpcchValueName, null, lpType, byteData, lpcbData);
                if (code == WinError.ERROR_SUCCESS) {
                    index++;
                    return RegistryValue.of(Native.toString(lpValueName), lpType.getValue(), byteData, lpcbData.getValue());
                }
                if (code == WinError.ERROR_NO_MORE_ITEMS) {
                    return null;
                }
                throw RegistryException.of(code, path);
            }
        };
    }

    /**
     * Deletes all values of this registry keys whose names match a specific predicate.
     *
     * @param valuePredicate The predicate to use.
     * @throws NoSuchRegistryKeyException If this registry key does not {@link #exists() exist}.
     * @throws RegistryException If the values cannot be deleted for another reason.
     */
    public void deleteValues(Predicate<? super RegistryValue> valuePredicate) {
        try (Key key = new Key(openKey(WinNT.KEY_READ | WinNT.KEY_SET_VALUE))) {
            List<String> values = new ArrayList<>();
            for (Iterator<RegistryValue> i = values(key.hKey); i.hasNext(); ) {
                RegistryValue value = i.next();
                if (valuePredicate.test(value)) {
                    values.add(value.name());
                }
            }
            for (String value : values) {
                deleteValue(key.hKey, value);
            }
        }
    }

    private void deleteValue(HKEY hKey, String name) {
        int code = api.RegDeleteValue(hKey, name);
        if (code != WinError.ERROR_SUCCESS && code != WinError.ERROR_FILE_NOT_FOUND) {
            throw RegistryException.of(code, path);
        }
    }

    // other

    /**
     * Tests whether or not this registry key exists.
     *
     * @return {@code true} if this registry key exists, or {@code false} otherwise.
     */
    public boolean exists() {
        HKEYByReference phkResult = new HKEYByReference();
        if (openKey(WinNT.KEY_READ, phkResult)) {
            closeKey(phkResult.getValue());
            return true;
        }
        return false;
    }

    /**
     * Creates this registry key if it does not exist already.
     *
     * @throws RegistryKeyAlreadyExistsException If this registry key already {@link #exists() exists}.
     * @throws RegistryException If this registry key cannot be created for another reason.
     */
    public void create() {
        if (createOrOpen() == WinNT.REG_OPENED_EXISTING_KEY) {
            throw new RegistryKeyAlreadyExistsException(path);
        }
    }

    /**
     * Creates this registry key if it does not exist already.
     *
     * @return {@code true} if the registry key was created, or {@code false} if it already {@link #exists() existed}.
     * @throws RegistryException If this registry key cannot be created.
     */
    public boolean createIfNotExists() {
        return createOrOpen() == WinNT.REG_CREATED_NEW_KEY;
    }

    private int createOrOpen() {
        HKEYByReference phkResult = new HKEYByReference();
        IntByReference lpdwDisposition = new IntByReference();

        int code = api.RegCreateKeyEx(rootHKEY, pathFromRoot, 0, null, WinNT.REG_OPTION_NON_VOLATILE, WinNT.KEY_READ, null, phkResult,
                lpdwDisposition);
        if (code == WinError.ERROR_SUCCESS) {
            if (this != root) {
                closeKey(phkResult.getValue());
            }
            return lpdwDisposition.getValue();
        }
        throw RegistryException.of(code, path);
    }

    /**
     * Deletes this registry key and all of its values.
     *
     * @throws UnsupportedOperationException If trying to delete on of the root keys.
     * @throws RegistryException If the registry key cannot be deleted.
     */
    public void delete() {
        if (this == root) {
            throw new UnsupportedOperationException(Messages.RegistryKey.cannotDeleteRoot.get(path));
        }

        int code = api.RegDeleteKey(rootHKEY, pathFromRoot);
        if (code != WinError.ERROR_SUCCESS) {
            throw RegistryException.of(code, path);
        }
    }

    /**
     * Deletes this registry key and all of its values if it exists.
     *
     * @return {@code true} if this registry key existed and has been removed, or {@code false} if it didn't {@link #exists() exist}.
     * @throws UnsupportedOperationException If trying to delete on of the root keys.
     * @throws RegistryException If the registry key cannot be deleted.
     */
    public boolean deleteIfExists() {
        if (this == root) {
            throw new UnsupportedOperationException(Messages.RegistryKey.cannotDeleteRoot.get(path));
        }

        int code = api.RegDeleteKey(rootHKEY, pathFromRoot);
        if (code == WinError.ERROR_SUCCESS) {
            return true;
        }
        if (code == WinError.ERROR_FILE_NOT_FOUND) {
            return false;
        }
        throw RegistryException.of(code, path);
    }

    // Comparable / Object

    @Override
    public int compareTo(RegistryKey key) {
        return path.compareTo(key.path);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || o.getClass() != getClass()) {
            return false;
        }
        RegistryKey other = (RegistryKey) o;
        return path.equals(other.path);
    }

    @Override
    public int hashCode() {
        return path.hashCode();
    }

    @Override
    public String toString() {
        return path;
    }

    // util

    private boolean openKey(int samDesired, HKEYByReference phkResult) {
        int code = api.RegOpenKeyEx(rootHKEY, pathFromRoot, 0, samDesired, phkResult);
        if (code == WinError.ERROR_SUCCESS) {
            return true;
        }
        if (code == WinError.ERROR_FILE_NOT_FOUND) {
            return false;
        }
        throw RegistryException.of(code, path);
    }

    private HKEY openKey(int samDesired) {
        HKEYByReference phkResult = new HKEYByReference();
        int code = api.RegOpenKeyEx(rootHKEY, pathFromRoot, 0, samDesired, phkResult);
        if (code == WinError.ERROR_SUCCESS) {
            return phkResult.getValue();
        }
        throw RegistryException.of(code, path);
    }

    private void closeKey(HKEY hKey) {
        int code = api.RegCloseKey(hKey);
        if (code != WinError.ERROR_SUCCESS) {
            throw RegistryException.of(code, path);
        }
    }

    final class Key implements AutoCloseable {

        private final HKEY hKey;

        private Key(HKEY hKey) {
            this.hKey = hKey;
        }

        @Override
        public void close() {
            closeKey(hKey);
        }
    }
}
