/*
 * ExpandableStringValue.java
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

import java.util.Objects;
import com.sun.jna.platform.win32.Kernel32Util;
import com.sun.jna.platform.win32.WinNT;

/**
 * A representation of string registry values.
 *
 * @author Rob Spoor
 */
public final class ExpandableStringValue extends SettableRegistryValue {

    private final String value;

    ExpandableStringValue(String name, byte[] data, int dataLength) {
        super(name, WinNT.REG_EXPAND_SZ);
        value = StringUtils.toString(data, dataLength);
    }

    private ExpandableStringValue(String name, String value) {
        super(name, WinNT.REG_EXPAND_SZ);
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
    public static ExpandableStringValue of(String name, String value) {
        return new ExpandableStringValue(name, value);
    }

    /**
     * Returns the registry value's string value.
     *
     * @return The registry value's string value.
     */
    public String value() {
        return value;
    }

    /**
     * Returns the registry value's expanded string value.
     *
     * @return The registry value's expanded string value.
     */
    public String expandedValue() {
        return Kernel32Util.expandEnvironmentStrings(value);
    }

    @Override
    byte[] rawData() {
        return StringUtils.fromString(value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!super.equals(o)) {
            return false;
        }
        ExpandableStringValue other = (ExpandableStringValue) o;
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
