/*
 * MultiStringValue.java
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

import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import com.github.robtimus.os.windows.registry.foreign.WString;
import com.github.robtimus.os.windows.registry.foreign.WinNT;

/**
 * A representation of multi-string registry values.
 * Instances of this class are immutable.
 *
 * @author Rob Spoor
 */
public final class MultiStringValue extends SettableRegistryValue {

    private final List<String> values;

    MultiStringValue(String name, MemorySegment data, long dataLength) {
        super(name, WinNT.REG_MULTI_SZ);
        values = WString.getStringList(data.asSlice(0, dataLength));
    }

    private MultiStringValue(String name, List<String> values) {
        super(name, WinNT.REG_MULTI_SZ);
        this.values = values;
    }

    /**
     * Creates a new multi-string registry value.
     *
     * @param name The name of the registry value.
     * @param values The registry value's string values.
     * @return The created multi-string registry value.
     * @throws NullPointerException If the name or any of the given values is {@code null}.
     * @throws IllegalArgumentException If any of the given values is empty.
     */
    public static MultiStringValue of(String name, String... values) {
        return of(name, Arrays.asList(values));
    }

    /**
     * Creates a new multi-string registry value.
     *
     * @param name The name of the registry value.
     * @param values The registry value's string values.
     * @return The created multi-string registry value.
     * @throws NullPointerException If the name or any of the given values is {@code null}.
     * @throws IllegalArgumentException If any of the given values is empty.
     */
    public static MultiStringValue of(String name, List<String> values) {
        return new MultiStringValue(name, copyOf(values));
    }

    private static List<String> copyOf(List<String> values) {
        List<String> result = new ArrayList<>(values.size());
        for (String value : values) {
            if (value.isEmpty()) {
                throw new IllegalArgumentException(Messages.MultiStringValue.emptyValue());
            }
            result.add(value);
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * Returns the registry value's string values.
     *
     * @return An unmodifiable list with the registry value's string values.
     */
    public List<String> values() {
        return values;
    }

    @Override
    MemorySegment rawData(SegmentAllocator allocator) {
        return WString.allocate(allocator, values);
    }

    @Override
    public MultiStringValue withName(String name) {
        // The values list is unmodifiable, so it's safe to share
        return new MultiStringValue(name, values);
    }

    /**
     * Returns a registry value with the same name as this registry value but a different value.
     *
     * @param values The values of the registry value to return.
     * @return A registry value with the same name as this registry value and the given values.
     * @throws NullPointerException If any of the given values is {@code null}.
     * @throws IllegalArgumentException If any of the given values is empty.
     */
    public MultiStringValue withValues(String... values) {
        return withValues(Arrays.asList(values));
    }

    /**
     * Returns a registry value with the same name as this registry value but a different value.
     *
     * @param values The values of the registry value to return.
     * @return A registry value with the same name as this registry value and the given values.
     * @throws NullPointerException If any of the given values is {@code null}.
     * @throws IllegalArgumentException If any of the given values is empty.
     */
    public MultiStringValue withValues(List<String> values) {
        return of(name(), values);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!super.equals(o)) {
            return false;
        }
        MultiStringValue other = (MultiStringValue) o;
        return values.equals(other.values);
    }

    @Override
    public int hashCode() {
        int hash = super.hashCode();
        hash = 31 * hash + values.hashCode();
        return hash;
    }

    @Override
    @SuppressWarnings("nls")
    public String toString() {
        return name() + "=" + values;
    }
}
