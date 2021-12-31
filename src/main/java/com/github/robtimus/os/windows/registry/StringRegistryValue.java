/*
 * StringRegistryValue.java
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
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.win32.W32APITypeMapper;

/**
 * A representation of string registry values.
 *
 * @author Rob Spoor
 */
public class StringRegistryValue extends SettableRegistryValue {

    private final String value;

    /**
     * Creates a new string registry value.
     *
     * @param name The name of the registry value.
     * @param value The registry value's string value.
     */
    public StringRegistryValue(String name, String value) {
        super(name, WinNT.REG_SZ);
        this.value = Objects.requireNonNull(value);
    }

    StringRegistryValue(String name, byte[] data, int dataLength) {
        super(name, WinNT.REG_SZ);
        value = toString(data, dataLength);
    }

    static String toString(byte[] data, int dataLength) {
        Memory memory = new Memory((long) dataLength + Native.WCHAR_SIZE);
        memory.clear();
        memory.write(0, data, 0, dataLength);
        return W32APITypeMapper.DEFAULT == W32APITypeMapper.UNICODE
                ? memory.getWideString(0)
                : memory.getString(0);
    }

    /**
     * Returns the registry value's string value.
     *
     * @return The registry value's string value.
     */
    public String value() {
        return value;
    }

    @Override
    byte[] rawData() {
        return fromString(value);
    }

    static byte[] fromString(String value) {
        Memory memory;
        if (W32APITypeMapper.DEFAULT == W32APITypeMapper.UNICODE) {
            memory = new Memory((value.length() + 1L) * Native.WCHAR_SIZE);
            memory.setWideString(0, value);
        } else {
            memory = new Memory(value.length() + 1L);
            memory.setString(0, value);
        }
        return memory.getByteArray(0, (int) memory.size());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!super.equals(o)) {
            return false;
        }
        StringRegistryValue other = (StringRegistryValue) o;
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
