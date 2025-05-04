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

import static com.github.robtimus.os.windows.registry.foreign.ForeignUtils.allocateBytes;
import static com.github.robtimus.os.windows.registry.foreign.ForeignUtils.allocateInt;
import static com.github.robtimus.os.windows.registry.foreign.ForeignUtils.clear;
import static com.github.robtimus.os.windows.registry.foreign.ForeignUtils.getInt;
import static com.github.robtimus.os.windows.registry.foreign.ForeignUtils.setInt;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.lang.ref.Cleaner;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
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
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntPredicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import com.github.robtimus.os.windows.registry.foreign.Advapi32;
import com.github.robtimus.os.windows.registry.foreign.ForeignUtils;
import com.github.robtimus.os.windows.registry.foreign.WString;
import com.github.robtimus.os.windows.registry.foreign.WinDef.FILETIME;
import com.github.robtimus.os.windows.registry.foreign.WinError;
import com.github.robtimus.os.windows.registry.foreign.WinNT;

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
    static Advapi32 api = Advapi32.INSTANCE;

    private static final ScopedValue<Context> CONTEXT = ScopedValue.newInstance();

    private static final Context.NonTransactional NO_TRANSACTION = new Context.NonTransactional();

    private static final Cleaner CLEANER = Cleaner.create();

    static final Instant FILETIME_BASE = ZonedDateTime.of(1601, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC).toInstant();

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

    abstract String machineName();

    // informational

    /**
     * Returns the instant when this registry key was last written to.
     *
     * @return The instant when this registry key was last written to.
     * @since 1.1
     */
    public Instant lastWriteTime() {
        try (Handle handle = handle(WinNT.KEY_READ)) {
            return handle.lastWriteTime();
        }
    }

    /**
     * Returns attributes for this registry key.
     *
     * @return Attributes for this registry key.
     * @since 1.1
     */
    public Attributes attributes() {
        try (Handle handle = handle(WinNT.KEY_READ)) {
            return handle.attributes();
        }
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
        try {
            return handle.values(filter)
                    .onClose(handle::close);
        } catch (RuntimeException e) {
            handle.close(e);
            throw e;
        }
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
     * Runs an action on a {@link Handle} if this registry key exists. The handle will be open only during the execution of the action.
     *
     * @param action The action to run.
     * @param options The options that define how the handle is created. If {@link HandleOption#CREATE} is given it will be ignored.
     * @throws NullPointerException If the given action is {@code null}.
     * @see #exists()
     * @see #handle(HandleOption...)
     * @since 1.1
     */
    public void ifExists(Consumer<? super Handle> action, HandleOption... options) {
        Objects.requireNonNull(action);

        Set<HandleOption> optionSet = EnumSet.noneOf(HandleOption.class);
        Collections.addAll(optionSet, options);

        int samDesired = samDesired(optionSet);

        handle(samDesired, error -> error == WinError.ERROR_FILE_NOT_FOUND)
                .ifPresent(handle -> runAction(handle, action));
    }

    /**
     * Runs an action on a {@link Handle} if this registry key exists. The handle will be open only during the execution of the action.
     *
     * @param <R> The type of result of the action.
     * @param action The action to run.
     * @param options The options that define how the handle is created. If {@link HandleOption#CREATE} is given it will be ignored.
     * @return An {@link Optional} with the return of calling the action on the handle,
     *         or {@link Optional#empty()} if this registry key does not exist.
     * @throws NullPointerException If the given action is {@code null}.
     * @see #exists()
     * @see #handle(HandleOption...)
     * @since 1.1
     */
    public <R> Optional<R> ifExists(Function<? super Handle, ? extends R> action, HandleOption... options) {
        Objects.requireNonNull(action);

        Set<HandleOption> optionSet = EnumSet.noneOf(HandleOption.class);
        Collections.addAll(optionSet, options);

        int samDesired = samDesired(optionSet);

        return handle(samDesired, error -> error == WinError.ERROR_FILE_NOT_FOUND)
                .map(handle -> runAction(handle, action));
    }

    /**
     * Tests whether or not this registry key is accessible.
     * Accessible means that accessing it will not throw a {@link RegistryAccessDeniedException}.
     * <p>
     * The following are the relations between this method and {@link #exists()}:
     * <ul>
     *   <li>If {@code isAccessible()} returns {@code true} then {@link #exists()} will return {@code true}.</li>
     *   <li>If {@link #exists()} returns {@code false} then {@code isAccessible()} will return {@code false}.</li>
     *   <li>If {@code isAccessible()} returns {@code false} then {@link #exists()} will not necessarily return {@code false};
     *       existing keys may not be accessible.</li>
     *   <li>If {@link #exists()} returns {@code true} then {@code isAccessible()} will not necessarily return {@code true};
     *       existing keys may not be accessible.</li>
     * </ul>
     *
     * @return {@code true} if this registry key is accessible, or {@code false} otherwise.
     * @throws RegistryException If the accessibility of this registry cannot be determined.
     * @since 1.1
     */
    public abstract boolean isAccessible();

    /**
     * Runs an action on a {@link Handle} if this registry key is accessible. The handle will be open only during the execution of the action.
     *
     * @param action The action to run.
     * @param options The options that define how the handle is created. If {@link HandleOption#CREATE} is given it will be ignored.
     * @throws NullPointerException If the given action is {@code null}.
     * @see #isAccessible()
     * @see #handle(HandleOption...)
     * @since 1.1
     */
    public void ifAccessible(Consumer<? super Handle> action, HandleOption... options) {
        Objects.requireNonNull(action);

        Set<HandleOption> optionSet = EnumSet.noneOf(HandleOption.class);
        Collections.addAll(optionSet, options);

        int samDesired = samDesired(optionSet);

        handle(samDesired, error -> error == WinError.ERROR_FILE_NOT_FOUND || error == WinError.ERROR_ACCESS_DENIED)
                .ifPresent(handle -> runAction(handle, action));
    }

    /**
     * Runs an action on a {@link Handle} if this registry key is accessible. The handle will be open only during the execution of the action.
     *
     * @param <R> The type of result of the action.
     * @param action The action to run.
     * @param options The options that define how the handle is created. If {@link HandleOption#CREATE} is given it will be ignored.
     * @return An {@link Optional} with the return of calling the action on the handle,
     *         or {@link Optional#empty()} if this registry key is not accessible.
     * @throws NullPointerException If the given action is {@code null}.
     * @see #isAccessible()
     * @see #handle(HandleOption...)
     * @since 1.1
     */
    public <R> Optional<R> ifAccessible(Function<? super Handle, ? extends R> action, HandleOption... options) {
        Objects.requireNonNull(action);

        Set<HandleOption> optionSet = EnumSet.noneOf(HandleOption.class);
        Collections.addAll(optionSet, options);

        int samDesired = samDesired(optionSet);

        return handle(samDesired, error -> error == WinError.ERROR_FILE_NOT_FOUND || error == WinError.ERROR_ACCESS_DENIED)
                .map(handle -> runAction(handle, action));
    }

    private void runAction(Handle handle, Consumer<? super Handle> action) {
        try (handle) {
            action.accept(handle);
        }
    }

    private <R> R runAction(Handle handle, Function<? super Handle, ? extends R> action) {
        try (handle) {
            return action.apply(handle);
        }
    }

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
     * @throws UnsupportedOperationException If trying to rename one of the root keys,
     *                                           or if the current Windows version does not support renaming registry keys.
     * @throws NullPointerException If the given name is {@code null}.
     * @throws IllegalArgumentException If the given name contains a backslash ({@code \}).
     * @throws NoSuchRegistryKeyException If this registry key does not {@link #exists() exist}.
     * @throws RegistryKeyAlreadyExistsException If this registry key's parent key already contains a sub key with the given name.
     * @throws RegistryException If this registry key could not be renamed for another reason.
     * @see RegistryFeature#RENAME_KEY
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

    /**
     * Creates a handle, unless an error occurs that can be ignored according to the given predicate.
     * In that case, {@link Optional#empty()} should be returned.
     * If an error occurs that cannot be ignored, a {@link RegistryException} should be thrown.
     */
    abstract Optional<Handle> handle(int samDesired, IntPredicate ignoreError);

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

    // transactional support

    static Context currentContext() {
        return CONTEXT.orElse(NO_TRANSACTION);
    }

    static <R, X extends Throwable> R callWithTransaction(Transaction transaction, TransactionalState.Callable<R, X> action) throws X {
        return callWithContext(new Context.Transactional(transaction), action);
    }

    static <R, X extends Throwable> R callWithoutTransaction(TransactionalState.Callable<R, X> action) throws X {
        return callWithContext(NO_TRANSACTION, action);
    }

    private static <R, X extends Throwable> R callWithContext(Context context, TransactionalState.Callable<R, X> action) throws X {
        return ScopedValue.where(CONTEXT, context).call(action::call);
    }

    // utility

    static void closeKey(MemorySegment hKey, String path, String machineName) {
        int code = api.RegCloseKey(hKey);
        if (code != WinError.ERROR_SUCCESS) {
            throw RegistryException.forKey(code, path, machineName);
        }
    }

    static Cleaner.Cleanable closeOnClean(Object object, MemorySegment hKey, String path, String machineName) {
        // Since this method is static, using a lambda does not capture any state except what's used inside it,
        // and therefore it's safe to use as action
        return CLEANER.register(object, () -> closeKey(hKey, path, machineName));
    }

    // nested classes

    /**
     * Attributes associated with a registry key.
     *
     * @author Rob Spoor
     * @since 1.1
     */
    public static final class Attributes {

        private final int subKeyCount;
        private final int valueCount;
        private final Instant lastWriteTime;

        private Attributes(int subKeyCount, int valueCount, Instant lastWriteTime) {
            this.subKeyCount = subKeyCount;
            this.valueCount = valueCount;
            this.lastWriteTime = lastWriteTime;
        }

        /**
         * Returns the number of sub keys of the registry key.
         *
         * @return The number of sub keys of the registry key.
         */
        public int subKeyCount() {
            return subKeyCount;
        }

        /**
         * Returns the number of values of the registry key.
         *
         * @return The number of values of the registry key.
         */
        public int valueCount() {
            return valueCount;
        }

        /**
         * Returns the instant when the registry key was last written to.
         *
         * @return The instant when the registry key was last written to.
         */
        public Instant lastWriteTime() {
            return lastWriteTime;
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

        final MemorySegment hKey;

        Handle(MemorySegment hKey) {
            this.hKey = hKey;
        }

        // informational

        /**
         * Returns the instant when the registry key from which this handle was retrieved was last written to.
         *
         * @return The instant when the registry key from which this handle was retrieved was last written to.
         * @since 1.1
         */
        public Instant lastWriteTime() {
            try (Arena allocator = Arena.ofConfined()) {
                MemorySegment lpftLastWriteTime = FILETIME.allocate(allocator);
                int code = api.RegQueryInfoKey(
                        hKey,
                        MemorySegment.NULL,
                        MemorySegment.NULL,
                        MemorySegment.NULL,
                        MemorySegment.NULL,
                        MemorySegment.NULL,
                        MemorySegment.NULL,
                        MemorySegment.NULL,
                        MemorySegment.NULL,
                        MemorySegment.NULL,
                        MemorySegment.NULL,
                        lpftLastWriteTime);
                if (code != WinError.ERROR_SUCCESS) {
                    throw RegistryException.forKey(code, path(), machineName());
                }
                return toInstant(lpftLastWriteTime);
            }
        }

        /**
         * Returns attributes for the registry key from which this handle was retrieved.
         *
         * @return Attributes for the registry key from which this handle was retrieved.
         * @since 1.1
         */
        public Attributes attributes() {
            try (Arena allocator = Arena.ofConfined()) {
                MemorySegment lpcSubKeys = allocateInt(allocator);
                MemorySegment lpcValues = allocateInt(allocator);
                MemorySegment lpftLastWriteTime = FILETIME.allocate(allocator);
                int code = api.RegQueryInfoKey(
                        hKey,
                        MemorySegment.NULL,
                        MemorySegment.NULL,
                        MemorySegment.NULL,
                        lpcSubKeys,
                        MemorySegment.NULL,
                        MemorySegment.NULL,
                        lpcValues,
                        MemorySegment.NULL,
                        MemorySegment.NULL,
                        MemorySegment.NULL,
                        lpftLastWriteTime);
                if (code != WinError.ERROR_SUCCESS) {
                    throw RegistryException.forKey(code, path(), machineName());
                }
                return new Attributes(ForeignUtils.getInt(lpcSubKeys), getInt(lpcValues), toInstant(lpftLastWriteTime));
            }
        }

        private Instant toInstant(MemorySegment lpFileTime) {
            // FILETIME "Contains a 64-bit value representing the number of 100-nanosecond intervals since January 1, 1601 (UTC)."
            long intervals = ((long) FILETIME.dwHighDateTime(lpFileTime) << 32L) | (FILETIME.dwLowDateTime(lpFileTime) & 0xFFFF_FFFFL);
            if (intervals <= 0) {
                // Conversion using Kernel32's FileTimeToSystemTime does not support this, which means we cannot test against this
                // This is very unlikely though, as the time would be before the year 1601
                return FILETIME_BASE;
            }
            // Ideally we would convert intervals to nanos by multiplying by 100, but that can easily overflow
            long millis = intervals / 10_000L;
            long nanos = (intervals % 10_000L) * 100;
            return FILETIME_BASE.plusMillis(millis).plusNanos(nanos);
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
        @SuppressWarnings("resource")
        public Stream<RegistryKey> subKeys() {
            Arena allocator = Arena.ofShared();
            try {
                Iterator<String> iterator = subKeyIterator(allocator);
                Spliterator<String> spliterator = Spliterators.spliteratorUnknownSize(iterator, Spliterator.NONNULL);
                return StreamSupport.stream(spliterator, false)
                        .onClose(allocator::close)
                        .map(RegistryKey.this::resolveChild);
            } catch (RuntimeException e) {
                try (allocator) {
                    throw e;
                }
            }
        }

        private Iterator<String> subKeyIterator(SegmentAllocator allocator) {
            MemorySegment lpcMaxSubKeyLen = allocateInt(allocator);

            int code = api.RegQueryInfoKey(
                    hKey,
                    MemorySegment.NULL,
                    MemorySegment.NULL,
                    MemorySegment.NULL,
                    MemorySegment.NULL,
                    lpcMaxSubKeyLen,
                    MemorySegment.NULL,
                    MemorySegment.NULL,
                    MemorySegment.NULL,
                    MemorySegment.NULL,
                    MemorySegment.NULL,
                    MemorySegment.NULL);
            if (code != WinError.ERROR_SUCCESS) {
                throw RegistryException.forKey(code, path(), machineName());
            }

            MemorySegment lpName = WString.allocate(allocator, getInt(lpcMaxSubKeyLen));
            MemorySegment lpcName = allocateInt(allocator, lpName.byteSize());

            return new LookaheadIterator<>() {

                private int index = 0;

                @Override
                protected String nextElement() {
                    setInt(lpcName, lpName.byteSize());

                    int code = api.RegEnumKeyEx(
                            hKey,
                            index,
                            lpName,
                            lpcName,
                            MemorySegment.NULL,
                            MemorySegment.NULL,
                            MemorySegment.NULL,
                            MemorySegment.NULL);
                    if (code == WinError.ERROR_SUCCESS) {
                        index++;
                        return WString.getString(lpName);
                    }
                    if (code == WinError.ERROR_NO_MORE_ITEMS) {
                        return null;
                    }
                    throw RegistryException.forKey(code, path(), machineName());
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
        @SuppressWarnings("resource")
        public Stream<RegistryValue> values(RegistryValue.Filter filter) {
            Arena allocator = Arena.ofShared();
            try {
                Iterator<RegistryValue> iterator = valueIterator(filter, allocator);
                Spliterator<RegistryValue> spliterator = Spliterators.spliteratorUnknownSize(iterator, Spliterator.NONNULL);
                return StreamSupport.stream(spliterator, false)
                        .onClose(allocator::close);
            } catch (RuntimeException e) {
                try (allocator) {
                    throw e;
                }
            }
        }

        private Iterator<RegistryValue> valueIterator(RegistryValue.Filter filter, SegmentAllocator allocator) {
            MemorySegment lpcMaxValueNameLen = allocateInt(allocator);
            MemorySegment lpcMaxValueLen = allocateInt(allocator);

            int code = api.RegQueryInfoKey(
                    hKey,
                    MemorySegment.NULL,
                    MemorySegment.NULL,
                    MemorySegment.NULL,
                    MemorySegment.NULL,
                    MemorySegment.NULL,
                    MemorySegment.NULL,
                    MemorySegment.NULL,
                    lpcMaxValueNameLen,
                    lpcMaxValueLen,
                    MemorySegment.NULL,
                    MemorySegment.NULL);
            if (code != WinError.ERROR_SUCCESS) {
                throw RegistryException.forKey(code, path(), machineName());
            }

            MemorySegment lpValueName = WString.allocate(allocator, getInt(lpcMaxValueNameLen));
            MemorySegment lpcchValueName = allocateInt(allocator, lpValueName.byteSize());

            MemorySegment lpType = allocateInt(allocator);

            // lpcMaxValueLen does not include the terminating null character so add one extra
            MemorySegment lpData = allocateBytes(allocator, getInt(lpcMaxValueLen) + WString.CHAR_SIZE);
            MemorySegment lpcbData = allocateInt(allocator, lpData.byteSize());

            return new LookaheadIterator<>() {

                private int index = 0;

                @Override
                protected RegistryValue nextElement() {
                    while (true) {
                        clear(lpValueName);
                        setInt(lpcchValueName, lpValueName.byteSize());
                        setInt(lpType, 0);
                        clear(lpData);
                        setInt(lpcbData, getInt(lpcMaxValueLen));

                        int code = api.RegEnumValue(
                                hKey,
                                index,
                                lpValueName,
                                lpcchValueName,
                                MemorySegment.NULL,
                                lpType,
                                lpData,
                                lpcbData);
                        if (code == WinError.ERROR_SUCCESS) {
                            index++;
                            String valueName = WString.getString(lpValueName);
                            int valueType = getInt(lpType);
                            if (filter == null || filter.matches(valueName, valueType)) {
                                return RegistryValue.of(valueName, valueType, lpData, getInt(lpcbData));
                            }
                            continue;
                        }
                        if (code == WinError.ERROR_NO_MORE_ITEMS) {
                            return null;
                        }
                        throw RegistryException.forKey(code, path(), machineName());
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

            try (Arena allocator = Arena.ofConfined()) {
                MemorySegment lpValueName = WString.allocate(allocator, name);
                MemorySegment lpType = allocateInt(allocator);
                MemorySegment lpcbData = allocateInt(allocator);

                int code = api.RegQueryValueEx(
                        hKey,
                        lpValueName,
                        MemorySegment.NULL,
                        lpType,
                        MemorySegment.NULL,
                        lpcbData);
                if (code == WinError.ERROR_SUCCESS || code == WinError.ERROR_MORE_DATA) {
                    // lpcbData includes the terminating null characters unless the data was stored without them
                    // Add not one but two chars, so for REG_MULTI_SZ both terminating null characters will be added
                    MemorySegment lpData = allocateBytes(allocator, getInt(lpcbData) + 2 * WString.CHAR_SIZE);
                    clear(lpData);
                    setInt(lpcbData, lpData.byteSize());

                    code = api.RegQueryValueEx(
                            hKey,
                            lpValueName,
                            MemorySegment.NULL,
                            MemorySegment.NULL,
                            lpData,
                            lpcbData);
                    if (code == WinError.ERROR_SUCCESS) {
                        return valueType.cast(RegistryValue.of(name, getInt(lpType), lpData, getInt(lpcbData)));
                    }
                }
                throw RegistryException.forValue(code, path(), machineName(), name);
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
         * @throws InvalidRegistryHandleException If this handle is no longer valid.
         * @throws NoSuchRegistryKeyException If the registry key from which this handle was retrieved no longer {@link RegistryKey#exists() exists}.
         * @throws RegistryException If the value cannot be returned for another reason.
         * @throws ClassCastException If the registry value with the given name cannot be cast to the given value type.
         */
        public <V extends RegistryValue> Optional<V> findValue(String name, Class<V> valueType) {
            Objects.requireNonNull(name);
            Objects.requireNonNull(valueType);

            try (Arena allocator = Arena.ofConfined()) {
                MemorySegment lpValueName = WString.allocate(allocator, name);
                MemorySegment lpType = allocateInt(allocator);
                MemorySegment lpcbData = allocateInt(allocator);

                int code = api.RegQueryValueEx(
                        hKey,
                        lpValueName,
                        MemorySegment.NULL,
                        lpType,
                        MemorySegment.NULL,
                        lpcbData);
                if (code == WinError.ERROR_FILE_NOT_FOUND) {
                    return Optional.empty();
                }
                if (code == WinError.ERROR_SUCCESS || code == WinError.ERROR_MORE_DATA) {
                    // lpcbData includes the terminating null characters unless the data was stored without them
                    // Add not one but two chars, so for REG_MULTI_SZ both terminating null characters will be added
                    MemorySegment lpData = allocateBytes(allocator, getInt(lpcbData) + 2 * WString.CHAR_SIZE);
                    clear(lpData);
                    setInt(lpcbData, lpData.byteSize());

                    code = api.RegQueryValueEx(
                            hKey,
                            lpValueName,
                            MemorySegment.NULL,
                            MemorySegment.NULL,
                            lpData,
                            lpcbData);
                    if (code == WinError.ERROR_SUCCESS) {
                        RegistryValue value = RegistryValue.of(name, getInt(lpType), lpData, getInt(lpcbData));
                        return Optional.of(valueType.cast(value));
                    }
                }
                throw RegistryException.forValue(code, path(), machineName(), name);
            }
        }

        /**
         * Returns a registry value as a string.
         * This method is shorthand for calling {@code getValue(name, StringValue.class).value()}.
         *
         * @param name The name of the registry value to return.
         * @return The registry value with the given name as a string.
         * @throws NullPointerException If the given name is {@code null}.
         * @throws InvalidRegistryHandleException If this handle is no longer valid.
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
         * @throws InvalidRegistryHandleException If this handle is no longer valid.
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
         * @throws InvalidRegistryHandleException If this handle is no longer valid.
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
         * @throws InvalidRegistryHandleException If this handle is no longer valid.
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
         * @throws InvalidRegistryHandleException If this handle is no longer valid.
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
         * @throws InvalidRegistryHandleException If this handle is no longer valid.
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

            try (Arena allocator = Arena.ofConfined()) {
                MemorySegment lpValueName = WString.allocate(allocator, value.name());
                MemorySegment lpData = value.rawData(allocator);

                int code = api.RegSetValueEx(
                        hKey,
                        lpValueName,
                        0,
                        value.type(),
                        lpData,
                        Math.toIntExact(lpData.byteSize()));
                if (code != WinError.ERROR_SUCCESS) {
                    throw RegistryException.forValue(code, path(), machineName(), value.name());
                }
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

            try (Arena allocator = Arena.ofConfined()) {
                MemorySegment lpValueName = WString.allocate(allocator, name);

                int code = api.RegDeleteValue(hKey, lpValueName);
                if (code != WinError.ERROR_SUCCESS) {
                    throw RegistryException.forValue(code, path(), machineName(), name);
                }
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

            try (Arena allocator = Arena.ofConfined()) {
                MemorySegment lpValueName = WString.allocate(allocator, name);

                int code = api.RegDeleteValue(hKey, lpValueName);
                if (code == WinError.ERROR_SUCCESS) {
                    return true;
                }
                if (code == WinError.ERROR_FILE_NOT_FOUND) {
                    return false;
                }
                throw RegistryException.forValue(code, path(), machineName(), name);
            }
        }

        // other

        /**
         * Closes this registry handle.
         *
         * @throws RegistryException If the registry handle could not be closed.
         */
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

    abstract static sealed class Context {

        abstract int createKey(
                MemorySegment hKey,
                MemorySegment lpSubKey,
                int dwOptions,
                int samDesired,
                MemorySegment phkResult,
                MemorySegment lpdwDisposition);

        abstract int deleteKey(
                MemorySegment hKey,
                MemorySegment lpSubKey);

        abstract int openKey(
                MemorySegment hKey,
                MemorySegment lpSubKey,
                int ulOptions,
                int samDesired,
                MemorySegment phkResult);

        static final class Transactional extends Context {

            private final Transaction transaction;

            private Transactional(Transaction transaction) {
                this.transaction = transaction;
            }

            Transaction transaction() {
                return transaction;
            }

            @Override
            int createKey(
                    MemorySegment hKey,
                    MemorySegment lpSubKey,
                    int dwOptions,
                    int samDesired,
                    MemorySegment phkResult,
                    MemorySegment lpdwDisposition) {

                return api.RegCreateKeyTransacted(
                        hKey,
                        lpSubKey,
                        0,
                        MemorySegment.NULL,
                        dwOptions,
                        samDesired,
                        MemorySegment.NULL,
                        phkResult,
                        lpdwDisposition,
                        transaction.handle(),
                        MemorySegment.NULL);
            }

            @Override
            int deleteKey(
                    MemorySegment hKey,
                    MemorySegment lpSubKey) {

                // TODO: check samDesired
                return api.RegDeleteKeyTransacted(
                        hKey,
                        lpSubKey,
                        0,
                        0,
                        transaction.handle(),
                        MemorySegment.NULL);
            }

            @Override
            int openKey(
                    MemorySegment hKey,
                    MemorySegment lpSubKey,
                    int ulOptions,
                    int samDesired,
                    MemorySegment phkResult) {

                return api.RegOpenKeyTransacted(
                        hKey,
                        lpSubKey,
                        ulOptions,
                        samDesired,
                        phkResult,
                        transaction.handle(),
                        MemorySegment.NULL);
            }
        }

        static final class NonTransactional extends Context {

            private NonTransactional() {
            }

            @Override
            int createKey(
                    MemorySegment hKey,
                    MemorySegment lpSubKey,
                    int dwOptions,
                    int samDesired,
                    MemorySegment phkResult,
                    MemorySegment lpdwDisposition) {

                return api.RegCreateKeyEx(
                        hKey,
                        lpSubKey,
                        0,
                        MemorySegment.NULL,
                        dwOptions,
                        samDesired,
                        MemorySegment.NULL,
                        phkResult,
                        lpdwDisposition);
            }

            @Override
            int deleteKey(
                    MemorySegment hKey,
                    MemorySegment lpSubKey) {

                return api.RegDeleteKey(hKey, lpSubKey);
            }

            @Override
            int openKey(
                    MemorySegment hKey,
                    MemorySegment lpSubKey,
                    int ulOptions,
                    int samDesired,
                    MemorySegment phkResult) {

                return api.RegOpenKeyEx(hKey, lpSubKey, ulOptions, samDesired, phkResult);
            }
        }
    }
}
