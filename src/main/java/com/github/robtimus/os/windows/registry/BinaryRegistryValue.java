/*
 * BinaryRegistryValue.java
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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Arrays;
import com.sun.jna.platform.win32.WinNT;

/**
 * A representation of binary registry values.
 *
 * @author Rob Spoor
 */
public final class BinaryRegistryValue extends SettableRegistryValue {

    private final byte[] data;

    /**
     * Creates a new binary registry value.
     *
     * @param name The name of the registry value.
     * @param data The registry value's binary data.
     */
    public BinaryRegistryValue(String name, byte[] data) {
        super(name, WinNT.REG_BINARY);
        this.data = data.clone();
    }

    BinaryRegistryValue(String name, byte[] data, int dataLength) {
        super(name, WinNT.REG_BINARY);
        this.data = Arrays.copyOf(data, dataLength);
    }

    /**
     * Returns the registry value's binary data.
     *
     * @return A copy of the registry value's binary data.
     */
    public byte[] data() {
        return data.clone();
    }

    /**
     * Returns an input stream over the registry value's binary data.
     *
     * @return An input stream over the registry value's binary data.
     */
    public InputStream inputStream() {
        return new ByteArrayInputStream(data);
    }

    @Override
    byte[] rawData() {
        return data;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!super.equals(o)) {
            return false;
        }
        BinaryRegistryValue other = (BinaryRegistryValue) o;
        return Arrays.equals(data, other.data);
    }

    @Override
    public int hashCode() {
        int hash = super.hashCode();
        hash = 31 * hash + Arrays.hashCode(data);
        return hash;
    }

    @Override
    @SuppressWarnings("nls")
    public String toString() {
        return name() + "=" + toString(data);
    }

    static String toString(byte[] array) {
        StringBuilder sb = new StringBuilder(2 + array.length * 2);
        sb.append("0x"); //$NON-NLS-1$
        for (byte b : array) {
            int high = (b >> 4) & 0xF;
            int low = b & 0xF;
            sb.append(Character.forDigit(high, 16));
            sb.append(Character.forDigit(low, 16));
        }
        return sb.toString();
    }
}
