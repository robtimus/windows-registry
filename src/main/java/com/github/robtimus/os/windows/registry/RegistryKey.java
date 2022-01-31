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

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import com.sun.jna.Native;
import com.sun.jna.platform.win32.WinError;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.platform.win32.WinReg.HKEY;
import com.sun.jna.ptr.IntByReference;

/**
 * A representation of registry keys.
 *
 * @author Rob Spoor
 */
public abstract class RegistryKey implements Comparable<RegistryKey> {

    /** The HKEY_CLASSES_ROOT root key. */
    public static final RegistryKey HKEY_CLASSES_ROOT = RootKey.HKEY_CLASSES_ROOT;

    /** The HKEY_CURRENT_USER root key. */
    public static final RegistryKey HKEY_CURRENT_USER = RootKey.HKEY_CURRENT_USER;

    /** The HKEY_LOCAL_MACHINE root key. */
    public static final RegistryKey HKEY_LOCAL_MACHINE = RootKey.HKEY_LOCAL_MACHINE;

    /** The HKEY_USERS root key. */
    public static final RegistryKey HKEY_USERS = RootKey.HKEY_USERS;

    /** The HKEY_CURRENT_CONFIG root key. */
    public static final RegistryKey HKEY_CURRENT_CONFIG = RootKey.HKEY_CURRENT_CONFIG;

    static final String SEPARATOR = "\\"; //$NON-NLS-1$

    // Non-private non-final to allow replacing for testing
    static Advapi32Extended api = Advapi32Extended.INSTANCE;

    RegistryKey() {
    }

    // structural

    /**
     * Returns the name of the registry key.
     *
     * @return The name of the registry key.
     */
    public abstract String name();

    /**
     * Returns the full path to the registry key.
     *
     * @return The full path to the registry key.
     */
    public abstract String path();

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
    public abstract boolean isRoot();

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
    public abstract RegistryKey root();

    /**
     * Returns the parent registry key.
     *
     * @return An {@link Optional} with the parent registry key, or {@link Optional#empty()} if this registry key is a root key.
     */
    public abstract Optional<RegistryKey> parent();

    /**
     * Returns a registry key relative to this registry key.
     * If the relative path is empty or a single {@code .}, this registry key is returned.
     * <p>
     * Note that this method will never leave the root key.
     *
     * @param relativePath The path for the new registry key, relative to this registry key.
     *                         Since registry keys can contain forward slashes, registry keys must be separated using backslashes ({@code \}).
     * @return The resulting registry key.
     * @throws NullPointerException If the given relative path is {@code null}.
     */
    public abstract RegistryKey resolve(String relativePath);

    abstract RegistryKey resolveChild(String name);

    /**
     * Returns all direct sub keys of this registry key. This stream should be closed afterwards.
     * <p>
     * Note that nothing can be said about the order of sub keys in the stream. It's also unspecified what happens if sub keys are removed while
     * consuming the stream.
     *
     * @return A stream with all direct sub keys of this registry key.
     * @throws NoSuchRegistryKeyException If this registry key does not {@link #exists() exist}.
     * @throws RegistryException If the sub keys cannot be queried for another reason.
     */
    @SuppressWarnings("resource")
    public Stream<RegistryKey> subKeys() {
        Handle handle = handle(WinNT.KEY_READ);
        try {
            return handle.subKeys()
                    .onClose(handle::close);
        } catch (RuntimeException e) {
            handle.close(e);
            throw e;
        }
    }

    /**
     * Returns a {@link Stream} that traverses through this registry keys and all of its nested keys. This stream should be closed afterwards.
     * <p>
     * Note that nothing can be said about the order of registry keys in the stream. It's also unspecified what happens if registry keys are removed
     * while consuming the stream.
     *
     * @param options The options to configure the traversal.
     * @return A {@link Stream} that traverses through this registry keys and all of its nested keys
     */
    public Stream<RegistryKey> traverse(TraverseOption... options) {
        return traverse(Integer.MAX_VALUE, options);
    }

    /**
     * Returns a {@link Stream} that traverses through this registry keys and all of its nested keys. This stream should be closed afterwards.
     * <p>
     * Note that nothing can be said about the order of registry keys in the stream. It's also unspecified what happens if registry keys are removed
     * while consuming the stream.
     *
     * @param maxDepth The maximum number of registry key levels to visit. A value of 0 indicates that only this registry key should be returned;
     *                     a value of 1 indicates that only this registry key and its direct {@link #subKeys() sub keys} should be returned.
     * @param options The options to configure the traversal.
     * @return A {@link Stream} that traverses through this registry keys and all of its nested keys
     * @throws IllegalArgumentException If the given maximum depth is negative.
     */
    public Stream<RegistryKey> traverse(int maxDepth, TraverseOption... options) {
        if (maxDepth < 0) {
            throw new IllegalArgumentException(maxDepth + " < 0"); //$NON-NLS-1$
        }

        Set<TraverseOption> optionSet = EnumSet.noneOf(TraverseOption.class);
        Collections.addAll(optionSet, options);

        return optionSet.contains(TraverseOption.SUB_KEYS_FIRST)
                ? traverseWithSubKeysFirst(maxDepth)
                : traverseWithSubKeysLast(maxDepth);
    }

    private Stream<RegistryKey> traverseWithSubKeysFirst(int maxDepth) {
        return maxDepth == 0
                ? Stream.of(this)
                : Stream.concat(subKeys().flatMap(k -> k.traverseWithSubKeysFirst(maxDepth - 1)), Stream.of(this));
    }

    private Stream<RegistryKey> traverseWithSubKeysLast(int maxDepth) {
        return maxDepth == 0
                ? Stream.of(this)
                : Stream.concat(Stream.of(this), subKeys().flatMap(k -> k.traverseWithSubKeysLast(maxDepth - 1)));
    }

    /**
     * An enumeration over the possible options for traversing a registry key.
     *
     * @author Rob Spoor
     */
    public enum TraverseOption {
        /** Indicates that sub keys come before their parents. */
        SUB_KEYS_FIRST,
    }

    // values

    /**
     * Returns all values of this registry key. This stream should be closed afterwards.
     * <p>
     * Note that nothing can be said about the order of values in the stream. It's also unspecified what happens if values are removed while consuming
     * the stream.
     *
     * @return A stream with all values of this registry key.
     * @throws NoSuchRegistryKeyException If this registry key does not {@link #exists() exist}.
     * @throws RegistryException If the values cannot be queried for another reason.
     */
    @SuppressWarnings("resource")
    public Stream<RegistryValue> values() {
        Handle handle = handle(WinNT.KEY_READ);
        try {
            return handle.values()
                    .onClose(handle::close);
        } catch (RuntimeException e) {
            handle.close(e);
            throw e;
        }
    }

    /**
     * Returns all values of this registry key. This stream should be closed afterwards.
     * <p>
     * While filtering can be done on a stream returned by {@link #values()}, this method allows limited filtering before any objects are even created
     * for registry values. This offers a small performance gain.
     * <p>
     * Note that nothing can be said about the order of values in the stream. It's also unspecified what happens if values are removed while consuming
     * the stream.
     *
     * @param filter A filter that can be used to limit which registry values are returned.
     * @return A stream with all values of this registry key.
     * @throws NoSuchRegistryKeyException If this registry key does not {@link #exists() exist}.
     * @throws RegistryException If the values cannot be queried for another reason.
     */
    @SuppressWarnings("resource")
    public Stream<RegistryValue> values(RegistryValue.Filter filter) {
        Handle handle = handle(WinNT.KEY_READ);
        return handle.values(filter)
                .onClose(handle::close);
    }

    /**
     * Returns a registry value.
     *
     * @param <V> The type of registry value to return.
     * @param name The name of the registry value to return.
     * @param valueType The type of registry value to return.
     * @return The registry value with the given name.
     * @throws NullPointerException If the given name or value type is {@code null}.
     * @throws NoSuchRegistryKeyException If this registry key does not {@link #exists() exist}.
     * @throws NoSuchRegistryValueException If there is no such registry value.
     * @throws RegistryException If the value cannot be returned for another reason.
     * @throws ClassCastException If the registry value with the given name cannot be cast to the given value type.
     */
    public <V extends RegistryValue> V getValue(String name, Class<V> valueType) {
        Objects.requireNonNull(name);
        Objects.requireNonNull(valueType);

        try (Handle handle = handle(WinNT.KEY_READ)) {
            return handle.getValue(name, valueType);
        }
    }

    /**
     * Tries to return a registry value.
     *
     * @param <V> The type of registry value to return.
     * @param name The name of the registry value to return.
     * @param valueType The type of registry value to return.
     * @return An {@link Optional} with the registry value with the given name, or {@link Optional#empty()} if there is no such registry value.
     * @throws NullPointerException If the given name or value type is {@code null}.
     * @throws NoSuchRegistryKeyException If this registry key does not {@link #exists() exist}.
     * @throws RegistryException If the value cannot be returned for another reason.
     * @throws ClassCastException If the registry value with the given name cannot be cast to the given value type.
     */
    public <V extends RegistryValue> Optional<V> findValue(String name, Class<V> valueType) {
        Objects.requireNonNull(name);
        Objects.requireNonNull(valueType);

        try (Handle handle = handle(WinNT.KEY_READ)) {
            return handle.findValue(name, valueType);
        }
    }

    /**
     * Returns a registry value as a string.
     * This method is shorthand for calling {@code getValue(name, StringValue.class).value()}.
     *
     * @param name The name of the registry value to return.
     * @return The registry value with the given name as a string.
     * @throws NullPointerException If the given name is {@code null}.
     * @throws NoSuchRegistryKeyException If this registry key does not {@link #exists() exist}.
     * @throws NoSuchRegistryValueException If there is no such registry value.
     * @throws RegistryException If the value cannot be returned for another reason.
     * @throws ClassCastException If the registry value with the given name is not a string.
     * @see #getValue(String, Class)
     * @see StringValue#value()
     */
    public String getStringValue(String name) {
        return getValue(name, StringValue.class).value();
    }

    /**
     * Tries to return a registry value as a string.
     * This method is shorthand for calling {@code getValue(name, StringValue.class).map(StringValue::value)}.
     *
     * @param name The name of the registry value to return.
     * @return An {@link Optional} with the registry value with the given name as a string,
     *         or {@link Optional#empty()} if there is no such registry value.
     * @throws NullPointerException If the given name is {@code null}.
     * @throws NoSuchRegistryKeyException If this registry key does not {@link #exists() exist}.
     * @throws RegistryException If the value cannot be returned for another reason.
     * @throws ClassCastException If the registry value with the given name is not a string.
     * @see #findValue(String, Class)
     * @see StringValue#value()
     */
    public Optional<String> findStringValue(String name) {
        return findValue(name, StringValue.class).map(StringValue::value);
    }

    /**
     * Returns a registry value as a DWORD.
     * This method is shorthand for calling {@code getValue(name, DWordValue.class).value()}.
     *
     * @param name The name of the registry value to return.
     * @return The registry value with the given name as a DWORD.
     * @throws NullPointerException If the given name is {@code null}.
     * @throws NoSuchRegistryKeyException If this registry key does not {@link #exists() exist}.
     * @throws NoSuchRegistryValueException If there is no such registry value.
     * @throws RegistryException If the value cannot be returned for another reason.
     * @throws ClassCastException If the registry value with the given name is not a DWORD.
     * @see #getValue(String, Class)
     * @see DWordValue#value()
     */
    public int getDWordValue(String name) {
        return getValue(name, DWordValue.class).value();
    }

    /**
     * Tries to return a registry value as a DWORD.
     * This method is shorthand for calling {@code findValue(name, DWordValue.class).mapToInt(DWordValue::value)}, if {@link Optional} had a method
     * {@code mapToInt}.
     *
     * @param name The name of the registry value to return.
     * @return An {@link Optional} with the registry value with the given name as a DWORD,
     *         or {@link Optional#empty()} if there is no such registry value.
     * @throws NullPointerException If the given name is {@code null}.
     * @throws NoSuchRegistryKeyException If this registry key does not {@link #exists() exist}.
     * @throws RegistryException If the value cannot be returned for another reason.
     * @throws ClassCastException If the registry value with the given name is not a DWORD.
     * @see #findValue(String, Class)
     * @see DWordValue#value()
     */
    public OptionalInt findDWordValue(String name) {
        Optional<DWordValue> value = findValue(name, DWordValue.class);
        return value.isPresent() ? OptionalInt.of(value.get().value()) : OptionalInt.empty();
    }

    /**
     * Returns a registry value as a QWORD.
     * This method is shorthand for calling {@code getValue(name, QWordValue.class).value()}.
     *
     * @param name The name of the registry value to return.
     * @return The registry value with the given name as a QWORD.
     * @throws NullPointerException If the given name is {@code null}.
     * @throws NoSuchRegistryKeyException If this registry key does not {@link #exists() exist}.
     * @throws NoSuchRegistryValueException If there is no such registry value.
     * @throws RegistryException If the value cannot be returned for another reason.
     * @throws ClassCastException If the registry value with the given name is not a QWORD.
     * @see #getValue(String, Class)
     * @see QWordValue#value()
     */
    public long getQWordValue(String name) {
        return getValue(name, QWordValue.class).value();
    }

    /**
     * Tries to return a registry value as a QWORD.
     * This method is shorthand for calling {@code findValue(name, QWordValue.class).mapToLong(QWordValue::value)}, if {@link Optional} had a method
     * {@code mapToLong}.
     *
     * @param name The name of the registry value to return.
     * @return An {@link Optional} with the registry value with the given name as a QWORD,
     *         or {@link Optional#empty()} if there is no such registry value.
     * @throws NullPointerException If the given name is {@code null}.
     * @throws NoSuchRegistryKeyException If this registry key does not {@link #exists() exist}.
     * @throws RegistryException If the value cannot be returned for another reason.
     * @throws ClassCastException If the registry value with the given name is not a QWORD.
     * @see #findValue(String, Class)
     * @see QWordValue#value()
     */
    public OptionalLong findQWordValue(String name) {
        Optional<QWordValue> value = findValue(name, QWordValue.class);
        return value.isPresent() ? OptionalLong.of(value.get().value()) : OptionalLong.empty();
    }

    // Purposefully omitted:
    // - getExpandedStringValue / findExpandedStringValue
    // - getBinaryValue / findBinaryValue
    // - getBinaryValueAsStream / findBinaryValueAsStream
    // - getMultiStringValue / findMultiStringValue

    /**
     * Sets a registry value.
     *
     * @param value The registry value to set.
     * @throws NullPointerException If the given registry value is {@code null}.
     * @throws NoSuchRegistryKeyException If this registry key does not {@link #exists() exist}.
     * @throws RegistryException If the value cannot be set for another reason.
     */
    public void setValue(SettableRegistryValue value) {
        Objects.requireNonNull(value);

        try (Handle handle = handle(WinNT.KEY_READ | WinNT.KEY_SET_VALUE)) {
            handle.setValue(value);
        }
    }

    /**
     * Deletes a registry value.
     *
     * @param name The name of the registry value to exist.
     * @throws NullPointerException If the given name is {@code null}.
     * @throws NoSuchRegistryKeyException If this registry key does not {@link #exists() exist}.
     * @throws NoSuchRegistryValueException If the registry value does not exist.
     * @throws RegistryException If the value cannot be deleted for another reason.
     */
    public void deleteValue(String name) {
        Objects.requireNonNull(name);

        try (Handle handle = handle(WinNT.KEY_READ | WinNT.KEY_SET_VALUE)) {
            handle.deleteValue(name);
        }
    }

    /**
     * Deletes a registry value if it exists.
     *
     * @param name The name of the registry value to exist.
     * @return {@code true} if the registry value existed and was deleted, or {@code false} if it didn't exist.
     * @throws NullPointerException If the given name is {@code null}.
     * @throws NoSuchRegistryKeyException If this registry key does not {@link #exists() exist}.
     * @throws RegistryException If the value cannot be deleted for another reason.
     */
    public boolean deleteValueIfExists(String name) {
        Objects.requireNonNull(name);

        try (Handle handle = handle(WinNT.KEY_READ | WinNT.KEY_SET_VALUE)) {
            return handle.deleteValueIfExists(name);
        }
    }

    // other

    /**
     * Tests whether or not this registry key exists.
     *
     * @return {@code true} if this registry key exists, or {@code false} otherwise.
     * @throws RegistryException If the existence of this registry cannot be determined.
     */
    public abstract boolean exists();

    /**
     * Creates this registry key if it does not exist already.
     *
     * @throws RegistryKeyAlreadyExistsException If this registry key already {@link #exists() exists}.
     * @throws RegistryException If this registry key cannot be created for another reason.
     */
    public abstract void create();

    /**
     * Creates this registry key if it does not exist already. It will also create any missing parent registry keys that are missing.
     *
     * @return {@code true} if the registry key was created, or {@code false} if it already {@link #exists() existed}.
     * @throws RegistryException If this registry key cannot be created.
     */
    public abstract boolean createIfNotExists();

    /**
     * Renames this registry key.
     * <p>
     * Note: this method requires Windows Vista or later, or Windows Server 2008 or later.
     *
     * @param newName The new registry key name.
     * @return A new registry key object representing the renamed registry key.
     * @throws UnsupportedOperationException If trying to rename one of the root keys.
     * @throws NullPointerException If the given name is {@code null}.
     * @throws IllegalArgumentException If the given name contains a backslash ({@code \}).
     * @throws NoSuchRegistryKeyException If this registry key does not {@link #exists() exist}.
     * @throws RegistryKeyAlreadyExistsException If this registry key's parent key already contains a sub key with the given name.
     * @throws RegistryException If this registry key could not be renamed for another reason.
     */
    public abstract RegistryKey renameTo(String newName);

    /**
     * Deletes this registry key and all of its values.
     *
     * @throws UnsupportedOperationException If trying to delete one of the root keys.
     * @throws NoSuchRegistryKeyException If this registry key does not {@link #exists() exist}.
     * @throws RegistryException If the registry key cannot be deleted for another reason.
     */
    public abstract void delete();

    /**
     * Deletes this registry key and all of its values if it exists.
     *
     * @return {@code true} if this registry key existed and has been removed, or {@code false} if it didn't {@link #exists() exist}.
     * @throws UnsupportedOperationException If trying to delete one of the root keys.
     * @throws RegistryException If the registry key cannot be deleted for another reason.
     */
    public abstract boolean deleteIfExists();

    // handles

    /**
     * Creates a handle to this registry key. This allows multiple operations on this registry key to be performed without creating a new link to the
     * Windows registry for each operation. The returned handle should be closed when it is no longer needed.
     * <p>
     * This method will be like calling {@link #handle(HandleOption...)} without any options. As a result, it will not be possible to set or delete
     * registry values using the returned handle.
     *
     * @return The created handle.
     * @throws NoSuchRegistryKeyException If this registry key does not {@link #exists() exist}.
     * @throws RegistryException If the handle could not be created for another reason.
     */
    public Handle handle() {
        return handle(WinNT.KEY_READ);
    }

    /**
     * Creates a handle to this registry key. This allows multiple operations on this registry key to be performed without creating a new link to the
     * Windows registry for each operation. The returned handle should be closed when it is no longer needed.
     *
     * @param options The options that define how the handle is created.
     * @return The created handle.
     * @throws NoSuchRegistryKeyException If this registry key does not {@link #exists() exist},
     *                                        and {@link HandleOption#CREATE} is not one of the given options.
     * @throws RegistryException If the handle could not be created for another reason.
     */
    public Handle handle(HandleOption... options) {
        Set<HandleOption> optionSet = EnumSet.noneOf(HandleOption.class);
        Collections.addAll(optionSet, options);

        int samDesired = samDesired(optionSet);
        boolean create = optionSet.contains(HandleOption.CREATE);

        return handle(samDesired, create);
    }

    private Handle handle(int samDesired) {
        return handle(samDesired, false);
    }

    abstract Handle handle(int samDesired, boolean create);

    private int samDesired(Set<HandleOption> options) {
        int samDesired = WinNT.KEY_READ;
        for (HandleOption option : options) {
            samDesired |= option.samDesired;
        }
        return samDesired;
    }

    // Comparable / Object

    @Override
    public int compareTo(RegistryKey key) {
        return toString().compareTo(key.toString());
    }

    @Override
    public abstract boolean equals(Object o);

    @Override
    public abstract int hashCode();

    @Override
    public String toString() {
        return path();
    }

    // utility

    void closeKey(HKEY hKey) {
        int code = api.RegCloseKey(hKey);
        if (code != WinError.ERROR_SUCCESS) {
            throw RegistryException.of(code, path());
        }
    }

    /**
     * A handle to a registry key. This offers mostly the same functionality as {@link RegistryKey} itself. However, it reuses the same link to the
     * Windows registry instead of creating a new one every time. That makes it more efficient if multiple operations on the same registry key are
     * needed. Handle instances should be closed when they are no longer needed to release the link to the Windows registry.
     * <p>
     * Note that the way the handle is created may limit the available operations. For instance, if {@link HandleOption#MANAGE_VALUES} isn't given,
     * trying to set or delete registry values will lead to {@link RegistryAccessDeniedException}s.
     *
     * @author Rob Spoor
     */
    public abstract class Handle implements AutoCloseable {

        final HKEY hKey;

        Handle(HKEY hKey) {
            this.hKey = hKey;
        }

        // traversal

        /**
         * Returns all direct sub keys of the registry key from which this handle was retrieved.
         * This stream is valid until this handle is closed, and does not need to be closed afterwards.
         * <p>
         * Note that nothing can be said about the order of sub keys in the stream. It's also unspecified what happens if sub keys are removed while
         * consuming the stream.
         *
         * @return A stream with all direct sub keys of the registry key from which this handle was retrieved.
         * @throws InvalidRegistryHandleException If this handle is no longer valid.
         * @throws NoSuchRegistryKeyException If the registry key from which this handle was retrieved no longer {@link RegistryKey#exists() exists}.
         * @throws RegistryException If the sub keys cannot be queried for another reason.
         */
        public Stream<RegistryKey> subKeys() {
            Iterator<String> iterator = subKeyIterator();
            Spliterator<String> spliterator = Spliterators.spliteratorUnknownSize(iterator, Spliterator.NONNULL);
            return StreamSupport.stream(spliterator, false)
                    .map(RegistryKey.this::resolveChild);
        }

        private Iterator<String> subKeyIterator() {
            IntByReference lpcMaxSubKeyLen = new IntByReference();
            int code = api.RegQueryInfoKey(hKey, null, null, null, null, lpcMaxSubKeyLen, null, null, null, null, null, null);
            if (code != WinError.ERROR_SUCCESS) {
                throw RegistryException.of(code, path());
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
                    throw RegistryException.of(code, path());
                }
            };
        }

        // values

        /**
         * Returns all values of the registry key from which this handle was retrieved.
         * This stream is valid until this handle is closed, and does not need to be closed afterwards.
         * <p>
         * Note that nothing can be said about the order of values in the stream. It's also unspecified what happens if values are removed while
         * consuming the stream.
         *
         * @return A stream with all values of the registry key from which this handle was retrieved.
         * @throws InvalidRegistryHandleException If this handle is no longer valid.
         * @throws NoSuchRegistryKeyException If the registry key from which this handle was retrieved no longer {@link RegistryKey#exists() exists}.
         * @throws RegistryException If the values cannot be queried for another reason.
         */
        public Stream<RegistryValue> values() {
            return values(null);
        }

        /**
         * Returns all values of the registry key from which this handle was retrieved.
         * This stream is valid until this handle is closed, and does not need to be closed afterwards.
         * <p>
         * While filtering can be done on a stream returned by {@link #values()}, this method allows limited filtering before any objects are even
         * created for registry values. This offers a small performance gain.
         * <p>
         * Note that nothing can be said about the order of values in the stream. It's also unspecified what happens if values are removed while
         * consuming the stream.
         *
         * @param filter A filter that can be used to limit which registry values are returned.
         * @return A stream with all values of the registry key from which this handle was retrieved.
         * @throws InvalidRegistryHandleException If this handle is no longer valid.
         * @throws NoSuchRegistryKeyException If the registry key from which this handle was retrieved no longer {@link RegistryKey#exists() exists}.
         * @throws RegistryException If the values cannot be queried for another reason.
         */
        public Stream<RegistryValue> values(RegistryValue.Filter filter) {
            Iterator<RegistryValue> iterator = valueIterator(filter);
            Spliterator<RegistryValue> spliterator = Spliterators.spliteratorUnknownSize(iterator, Spliterator.NONNULL);
            return StreamSupport.stream(spliterator, false);
        }

        private Iterator<RegistryValue> valueIterator(RegistryValue.Filter filter) {
            IntByReference lpcMaxValueNameLen = new IntByReference();
            IntByReference lpcMaxValueLen = new IntByReference();
            int code = api.RegQueryInfoKey(hKey, null, null, null, null, null, null, null, lpcMaxValueNameLen, lpcMaxValueLen, null, null);
            if (code != WinError.ERROR_SUCCESS) {
                throw RegistryException.of(code, path());
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
                    while (true) {
                        lpcchValueName.setValue(lpValueName.length);
                        lpType.setValue(0);
                        Arrays.fill(byteData, (byte) 0);
                        lpcbData.setValue(lpcMaxValueLen.getValue());

                        int code = api.RegEnumValue(hKey, index, lpValueName, lpcchValueName, null, lpType, byteData, lpcbData);
                        if (code == WinError.ERROR_SUCCESS) {
                            index++;
                            String valueName = Native.toString(lpValueName);
                            int valueType = lpType.getValue();
                            if (filter == null || filter.matches(valueName, valueType)) {
                                return RegistryValue.of(valueName, valueType, byteData, lpcbData.getValue());
                            }
                            continue;
                        }
                        if (code == WinError.ERROR_NO_MORE_ITEMS) {
                            return null;
                        }
                        throw RegistryException.of(code, path());
                    }
                }
            };
        }

        /**
         * Returns a registry value.
         *
         * @param <V> The type of registry value to return.
         * @param name The name of the registry value to return.
         * @param valueType The type of registry value to return.
         * @return The registry value with the given name.
         * @throws NullPointerException If the given name or value type is {@code null}.
         * @throws InvalidRegistryHandleException If this handle is no longer valid.
         * @throws NoSuchRegistryKeyException If the registry key from which this handle was retrieved no longer {@link RegistryKey#exists() exists}.
         * @throws NoSuchRegistryValueException If there is no such registry value.
         * @throws RegistryException If the value cannot be returned for another reason.
         * @throws ClassCastException If the registry value with the given name cannot be cast to the given value type.
         */
        public <V extends RegistryValue> V getValue(String name, Class<V> valueType) {
            Objects.requireNonNull(name);
            Objects.requireNonNull(valueType);

            IntByReference lpType = new IntByReference();
            IntByReference lpcbData = new IntByReference();
            int code = api.RegQueryValueEx(hKey, name, 0, lpType, (byte[]) null, lpcbData);
            if (code == WinError.ERROR_SUCCESS || code == WinError.ERROR_MORE_DATA) {
                byte[] byteData = new byte[lpcbData.getValue() + Native.WCHAR_SIZE];
                Arrays.fill(byteData, (byte) 0);
                lpcbData.setValue(byteData.length);

                code = api.RegQueryValueEx(hKey, name, 0, null, byteData, lpcbData);
                if (code == WinError.ERROR_SUCCESS) {
                    return valueType.cast(RegistryValue.of(name, lpType.getValue(), byteData, lpcbData.getValue()));
                }
            }
            throw RegistryException.of(code, path(), name);
        }

        /**
         * Tries to return a registry value.
         *
         * @param <V> The type of registry value to return.
         * @param name The name of the registry value to return.
         * @param valueType The type of registry value to return.
         * @return An {@link Optional} with the registry value with the given name, or {@link Optional#empty()} if there is no such registry value.
         * @throws NullPointerException If the given name or value type is {@code null}.
         * @throws InvalidRegistryHandleException If this handle is no longer valid.
         * @throws NoSuchRegistryKeyException If the registry key from which this handle was retrieved no longer {@link RegistryKey#exists() exists}.
         * @throws RegistryException If the value cannot be returned for another reason.
         * @throws ClassCastException If the registry value with the given name cannot be cast to the given value type.
         */
        public <V extends RegistryValue> Optional<V> findValue(String name, Class<V> valueType) {
            Objects.requireNonNull(name);
            Objects.requireNonNull(valueType);

            IntByReference lpType = new IntByReference();
            IntByReference lpcbData = new IntByReference();
            int code = api.RegQueryValueEx(hKey, name, 0, lpType, (byte[]) null, lpcbData);
            if (code == WinError.ERROR_FILE_NOT_FOUND) {
                return Optional.empty();
            }
            if (code == WinError.ERROR_SUCCESS || code == WinError.ERROR_MORE_DATA) {
                byte[] byteData = new byte[lpcbData.getValue() + Native.WCHAR_SIZE];
                Arrays.fill(byteData, (byte) 0);
                lpcbData.setValue(byteData.length);

                code = api.RegQueryValueEx(hKey, name, 0, null, byteData, lpcbData);
                if (code == WinError.ERROR_SUCCESS) {
                    RegistryValue value = RegistryValue.of(name, lpType.getValue(), byteData, lpcbData.getValue());
                    return Optional.of(valueType.cast(value));
                }
            }
            throw RegistryException.of(code, path(), name);
        }

        /**
         * Returns a registry value as a string.
         * This method is shorthand for calling {@code getValue(name, StringValue.class).value()}.
         *
         * @param name The name of the registry value to return.
         * @return The registry value with the given name as a string.
         * @throws NullPointerException If the given name is {@code null}.
         * @throws NoSuchRegistryKeyException If this registry key does not {@link #exists() exist}.
         * @throws NoSuchRegistryValueException If there is no such registry value.
         * @throws RegistryException If the value cannot be returned for another reason.
         * @throws ClassCastException If the registry value with the given name is not a string.
         * @see #getValue(String, Class)
         * @see StringValue#value()
         */
        public String getStringValue(String name) {
            return getValue(name, StringValue.class).value();
        }

        /**
         * Tries to return a registry value as a string.
         * This method is shorthand for calling {@code getValue(name, StringValue.class).map(StringValue::value)}.
         *
         * @param name The name of the registry value to return.
         * @return An {@link Optional} with the registry value with the given name as a string,
         *         or {@link Optional#empty()} if there is no such registry value.
         * @throws NullPointerException If the given name is {@code null}.
         * @throws NoSuchRegistryKeyException If this registry key does not {@link #exists() exist}.
         * @throws RegistryException If the value cannot be returned for another reason.
         * @throws ClassCastException If the registry value with the given name is not a string.
         * @see #findValue(String, Class)
         * @see StringValue#value()
         */
        public Optional<String> findStringValue(String name) {
            return findValue(name, StringValue.class).map(StringValue::value);
        }

        /**
         * Returns a registry value as a DWORD.
         * This method is shorthand for calling {@code getValue(name, DWordValue.class).value()}.
         *
         * @param name The name of the registry value to return.
         * @return The registry value with the given name as a DWORD.
         * @throws NullPointerException If the given name is {@code null}.
         * @throws NoSuchRegistryKeyException If this registry key does not {@link #exists() exist}.
         * @throws NoSuchRegistryValueException If there is no such registry value.
         * @throws RegistryException If the value cannot be returned for another reason.
         * @throws ClassCastException If the registry value with the given name is not a DWORD.
         * @see #getValue(String, Class)
         * @see DWordValue#value()
         */
        public int getDWordValue(String name) {
            return getValue(name, DWordValue.class).value();
        }

        /**
         * Tries to return a registry value as a DWORD.
         * This method is shorthand for calling {@code findValue(name, DWordValue.class).mapToInt(DWordValue::value)}, if {@link Optional} had a
         * method {@code mapToInt}.
         *
         * @param name The name of the registry value to return.
         * @return An {@link Optional} with the registry value with the given name as a DWORD,
         *         or {@link Optional#empty()} if there is no such registry value.
         * @throws NullPointerException If the given name is {@code null}.
         * @throws NoSuchRegistryKeyException If this registry key does not {@link #exists() exist}.
         * @throws RegistryException If the value cannot be returned for another reason.
         * @throws ClassCastException If the registry value with the given name is not a DWORD.
         * @see #findValue(String, Class)
         * @see DWordValue#value()
         */
        public OptionalInt findDWordValue(String name) {
            Optional<DWordValue> value = findValue(name, DWordValue.class);
            return value.isPresent() ? OptionalInt.of(value.get().value()) : OptionalInt.empty();
        }

        /**
         * Returns a registry value as a QWORD.
         * This method is shorthand for calling {@code getValue(name, QWordValue.class).value()}.
         *
         * @param name The name of the registry value to return.
         * @return The registry value with the given name as a QWORD.
         * @throws NullPointerException If the given name is {@code null}.
         * @throws NoSuchRegistryKeyException If this registry key does not {@link #exists() exist}.
         * @throws NoSuchRegistryValueException If there is no such registry value.
         * @throws RegistryException If the value cannot be returned for another reason.
         * @throws ClassCastException If the registry value with the given name is not a QWORD.
         * @see #getValue(String, Class)
         * @see QWordValue#value()
         */
        public long getQWordValue(String name) {
            return getValue(name, QWordValue.class).value();
        }

        /**
         * Tries to return a registry value as a QWORD.
         * This method is shorthand for calling {@code findValue(name, QWordValue.class).mapToLong(QWordValue::value)}, if {@link Optional} had a
         * method {@code mapToLong}.
         *
         * @param name The name of the registry value to return.
         * @return An {@link Optional} with the registry value with the given name as a QWORD,
         *         or {@link Optional#empty()} if there is no such registry value.
         * @throws NullPointerException If the given name is {@code null}.
         * @throws NoSuchRegistryKeyException If this registry key does not {@link #exists() exist}.
         * @throws RegistryException If the value cannot be returned for another reason.
         * @throws ClassCastException If the registry value with the given name is not a QWORD.
         * @see #findValue(String, Class)
         * @see QWordValue#value()
         */
        public OptionalLong findQWordValue(String name) {
            Optional<QWordValue> value = findValue(name, QWordValue.class);
            return value.isPresent() ? OptionalLong.of(value.get().value()) : OptionalLong.empty();
        }

        // Purposefully omitted:
        // - getExpandedStringValue / findExpandedStringValue
        // - getBinaryValue / findBinaryValue
        // - getBinaryValueAsStream / findBinaryValueAsStream
        // - getMultiStringValue / findMultiStringValue

        /**
         * Sets a registry value.
         *
         * @param value The registry value to set.
         * @throws NullPointerException If the given registry value is {@code null}.
         * @throws InvalidRegistryHandleException If this handle is no longer valid.
         * @throws NoSuchRegistryKeyException If the registry key from which this handle was retrieved no longer {@link RegistryKey#exists() exists}.
         * @throws RegistryException If the value cannot be set for another reason.
         */
        public void setValue(SettableRegistryValue value) {
            Objects.requireNonNull(value);

            byte[] data = value.rawData();
            int code = api.RegSetValueEx(hKey, value.name(), 0, value.type(), data, data.length);
            if (code != WinError.ERROR_SUCCESS) {
                throw RegistryException.of(code, path(), value.name());
            }
        }

        /**
         * Deletes a registry value.
         *
         * @param name The name of the registry value to exist.
         * @throws NullPointerException If the given name is {@code null}.
         * @throws InvalidRegistryHandleException If this handle is no longer valid.
         * @throws NoSuchRegistryKeyException If the registry key from which this handle was retrieved no longer {@link RegistryKey#exists() exists}.
         * @throws NoSuchRegistryValueException If the registry value does not exist.
         * @throws RegistryException If the value cannot be deleted for another reason.
         */
        public void deleteValue(String name) {
            Objects.requireNonNull(name);

            int code = api.RegDeleteValue(hKey, name);
            if (code != WinError.ERROR_SUCCESS) {
                throw RegistryException.of(code, path(), name);
            }
        }

        /**
         * Deletes a registry value if it exists.
         *
         * @param name The name of the registry value to exist.
         * @return {@code true} if the registry value existed and was deleted, or {@code false} if it didn't exist.
         * @throws NullPointerException If the given name is {@code null}.
         * @throws InvalidRegistryHandleException If this handle is no longer valid.
         * @throws NoSuchRegistryKeyException If the registry key from which this handle was retrieved no longer {@link RegistryKey#exists() exists}.
         * @throws RegistryException If the value cannot be deleted for another reason.
         */
        public boolean deleteValueIfExists(String name) {
            Objects.requireNonNull(name);

            int code = api.RegDeleteValue(hKey, name);
            if (code == WinError.ERROR_SUCCESS) {
                return true;
            }
            if (code == WinError.ERROR_FILE_NOT_FOUND) {
                return false;
            }
            throw RegistryException.of(code, path(), name);
        }

        // other

        @Override
        public abstract void close();

        abstract void close(RuntimeException exception);
    }

    /**
     * An enumeration over the possible options for opening Windows registry handles.
     *
     * @author Rob Spoor
     */
    public enum HandleOption {
        /** Indicates that the registry should be created if it does not exist yet. */
        CREATE(0),

        /** Indicates that setting and deleting registry values should be allowed. */
        MANAGE_VALUES(WinNT.KEY_SET_VALUE),
        ;

        private final int samDesired;

        HandleOption(int samDesired) {
            this.samDesired = samDesired;
        }
    }
}
