/*
 * MultiStringRegistryValue.java
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.win32.W32APITypeMapper;

/**
 * A representation of multi-string registry values.
 *
 * @author Rob Spoor
 */
public class MultiStringRegistryValue extends RegistryValue {

    private final String[] values;

    /**
     * Creates a new multi-string registry value.
     *
     * @param name The name of the registry value.
     * @param values The registry value's string values.
     */
    public MultiStringRegistryValue(String name, String[] values) {
        super(name, WinNT.REG_SZ);
        for (String value : values) {
            Objects.requireNonNull(value);
        }
        this.values = values.clone();
    }

    MultiStringRegistryValue(String name, byte[] data, int dataLength) {
        super(name, WinNT.REG_SZ);
        values = toStringArray(data, dataLength);
    }

    static String[] toStringArray(byte[] data, int dataLength) {
        Memory memory = new Memory(dataLength + 2L * Native.WCHAR_SIZE);
        memory.clear();
        memory.write(0, data, 0, dataLength);

        List<String> result = new ArrayList<>();
        int offset = 0;
        while (offset < memory.size()) {
            String value;
            if (W32APITypeMapper.DEFAULT == W32APITypeMapper.UNICODE) {
                value = memory.getWideString(offset);
                offset += value.length() * Native.WCHAR_SIZE;
                offset += Native.WCHAR_SIZE;
            } else {
                value = memory.getString(offset);
                offset += value.length();
                offset += 1;
            }

            if (value.length() == 0) {
                // A sequence of null-terminated strings,
                // terminated by an empty string (\0).
                // => The first empty string terminates the
                break;
            }

            result.add(value);
        }
        return result.toArray(new String[0]);
    }

    /**
     * Returns the registry value's string values.
     *
     * @return A copy of the registry value's string values.
     */
    public String[] values() {
        return values.clone();
    }

    @Override
    byte[] rawData() {
        int charwidth = W32APITypeMapper.DEFAULT == W32APITypeMapper.UNICODE ? Native.WCHAR_SIZE : 1;

        int size = 0;
        for (String s : values) {
            size += s.length() * charwidth;
            size += charwidth;
        }
        size += charwidth;

        int offset = 0;
        Memory memory = new Memory(size);
        memory.clear();
        for (String s : values) {
            if (W32APITypeMapper.DEFAULT == W32APITypeMapper.UNICODE) {
                memory.setWideString(offset, s);
            } else {
                memory.setString(offset, s);
            }
            offset += s.length() * charwidth;
            offset += charwidth;
        }
        return memory.getByteArray(0, size);
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
        MultiStringRegistryValue other = (MultiStringRegistryValue) o;
        return Arrays.equals(values, other.values);
    }

    @Override
    public int hashCode() {
        int hash = super.hashCode();
        hash = 31 * hash + Arrays.hashCode(values);
        return hash;
    }

    @Override
    @SuppressWarnings("nls")
    public String toString() {
        return name() + "=" + Arrays.toString(values);
    }
}
