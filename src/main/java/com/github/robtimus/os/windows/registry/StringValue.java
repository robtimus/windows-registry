/*
 * StringValue.java
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

import static com.github.robtimus.os.windows.registry.foreign.WindowsConstants.REG_EXPAND_SZ;
import static com.github.robtimus.os.windows.registry.foreign.WindowsConstants.REG_SZ;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.util.Objects;
import com.github.robtimus.os.windows.registry.foreign.Kernel32;
import com.github.robtimus.os.windows.registry.foreign.WString;

/**
 * A representation of string registry values.
 * Instances of this class are immutable.
 *
 * @author Rob Spoor
 */
public final class StringValue extends SettableRegistryValue {

    private final String value;

    StringValue(String name, int type, MemorySegment data, long dataLength) {
        super(name, type);
        // data may or may not include a terminating null character.
        // Therefore, don't pass a value to getString but let it calculate the length based on the segment size
        // and index of the first null character (if any).
        value = WString.getString(data.asSlice(0, dataLength));
    }

    private StringValue(String name, int type, String value) {
        super(name, type);
        this.value = Objects.requireNonNull(value);
    }

    /**
     * Creates a new string registry value.
     *
     * @param name The name of the registry value.
     * @param value The registry value's string value.
     * @return The created string registry value.
     * @throws NullPointerException If the given name or value is {@code null}.
     */
    public static StringValue of(String name, String value) {
        return new StringValue(name, REG_SZ, value);
    }

    /**
     * Creates a new string registry value.
     *
     * @param name The name of the registry value.
     * @param value The registry value's string value.
     * @return The created string registry value.
     * @throws NullPointerException If the given name or value is {@code null}.
     */
    public static StringValue expandableOf(String name, String value) {
        return new StringValue(name, REG_EXPAND_SZ, value);
    }

    /**
     * Returns the registry value's string value.
     * For <em>expandable</em> string registry values, this is the unexpanded value. Use {@link #expandedValue()} for the expanded value.
     *
     * @return The registry value's string value.
     */
    public String value() {
        return value;
    }

    /**
     * Returns whether or not this registry value is an <em>expandable</em> string registry value.
     * If so, its value can be expanded.
     *
     * @return {@code true} if this registry value is an expandable string registry value, or {@code false} otherwise.
     * @see #expandedValue()
     */
    public boolean isExpandable() {
        return type() == REG_EXPAND_SZ;
    }

    /**
     * Returns the registry value's expanded string value.
     *
     * @return The registry value's expanded string value.
     * @throws IllegalStateException If this registry value is not {@link #isExpandable() expandable}.
     */
    public String expandedValue() {
        if (isExpandable()) {
            return Kernel32.expandEnvironmentStrings(value);
        }
        throw new IllegalStateException(Messages.StringValue.notExpandable());
    }

    @Override
    MemorySegment rawData(SegmentAllocator allocator) {
        return WString.allocate(allocator, value);
    }

    @Override
    public StringValue withName(String name) {
        return new StringValue(name, type(), value);
    }

    /**
     * Returns a registry value with the same name as this registry value but a different value.
     * This registry value will not be {@link #isExpandable() expandable}.
     *
     * @param value The value of the registry value to return.
     * @return A registry value with the same name as this registry value and the given value.
     * @throws NullPointerException If the given value is {@code null}.
     */
    public StringValue withValue(String value) {
        return of(name(), value);
    }

    /**
     * Returns a registry value with the same name as this registry value but a different value.
     * This registry value will be {@link #isExpandable() expandable}.
     *
     * @param value The value of the registry value to return.
     * @return A registry value with the same name as this registry value and the given value.
     * @throws NullPointerException If the given value is {@code null}.
     */
    public StringValue withExpandableValue(String value) {
        return expandableOf(name(), value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!super.equals(o)) {
            return false;
        }
        StringValue other = (StringValue) o;
        return value.equals(other.value);
    }

    @Override
    public int hashCode() {
        int hash = super.hashCode();
        hash = 31 * hash + value.hashCode();
        return hash;
    }

    @Override
    @SuppressWarnings("nls")
    public String toString() {
        return name() + "=" + value;
    }
}
