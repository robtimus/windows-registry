/*
 * DWordRegistryValue.java
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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;
import com.sun.jna.platform.win32.WinNT;

/**
 * A representation of DWORD registry values.
 *
 * @author Rob Spoor
 */
public final class DWordRegistryValue extends SettableRegistryValue {

    private final int value;
    private final ByteOrder byteOrder;

    /**
     * Creates a new DWORD registry value.
     *
     * @param name The name of the registry value.
     * @param value The registry value's DWORD value.
     */
    public DWordRegistryValue(String name, int value) {
        super(name, WinNT.REG_DWORD);
        this.value = value;
        this.byteOrder = getByteOrder(WinNT.REG_DWORD);
    }

    /**
     * Creates a new DWORD registry value.
     *
     * @param name The name of the registry value.
     * @param value The registry value's DWORD value.
     * @param byteOrder The byte order for the registry value; either {@link ByteOrder#BIG_ENDIAN} or {@link ByteOrder#LITTLE_ENDIAN}.
     */
    public DWordRegistryValue(String name, int value, ByteOrder byteOrder) {
        super(name, getType(byteOrder));
        this.value = value;
        this.byteOrder = Objects.requireNonNull(byteOrder);
    }

    DWordRegistryValue(String name, int type, byte[] data) {
        super(name, type);

        byteOrder = getByteOrder(type);
        ByteBuffer buffer = ByteBuffer.wrap(data);
        buffer.order(byteOrder);
        this.value = buffer.getInt();
    }

    private static int getType(ByteOrder byteOrder) {
        if (byteOrder == ByteOrder.BIG_ENDIAN) {
            return WinNT.REG_DWORD_BIG_ENDIAN;
        }
        if (byteOrder == ByteOrder.LITTLE_ENDIAN) {
            return WinNT.REG_DWORD_LITTLE_ENDIAN;
        }
        return WinNT.REG_DWORD;
    }

    private static ByteOrder getByteOrder(int type) {
        switch (type) {
            case WinNT.REG_DWORD_BIG_ENDIAN:
                return ByteOrder.BIG_ENDIAN;
            case WinNT.REG_DWORD_LITTLE_ENDIAN:
                return ByteOrder.LITTLE_ENDIAN;
            default:
                throw new IllegalArgumentException(Messages.RegistryValue.unsupportedType.get(type));
        }
    }

    /**
     * Returns the registry value's DWORD value.
     *
     * @return The registry value's DWORD value.
     */
    public int value() {
        return value;
    }

    @Override
    byte[] rawData() {
        byte[] data = new byte[Integer.SIZE / Byte.SIZE];
        ByteBuffer buffer = ByteBuffer.wrap(data);
        buffer.order(byteOrder);
        buffer.putInt(value);
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
        DWordRegistryValue other = (DWordRegistryValue) o;
        return value == other.value;
    }

    @Override
    public int hashCode() {
        int hash = super.hashCode();
        hash = 31 * hash + value;
        return hash;
    }

    @Override
    @SuppressWarnings("nls")
    public String toString() {
        return name() + "=" + value;
    }
}
