/*
 * DWordValue.java
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
 * Instances of this class are immutable.
 *
 * @author Rob Spoor
 */
public final class DWordValue extends SettableRegistryValue {

    private final int value;
    private final ByteOrder byteOrder;

    DWordValue(String name, int type, byte[] data) {
        super(name, type);

        byteOrder = getByteOrder(type);
        ByteBuffer buffer = ByteBuffer.wrap(data);
        buffer.order(byteOrder);
        this.value = buffer.getInt();
    }

    private DWordValue(String name, int type, int value, ByteOrder byteOrder) {
        super(name, type);
        this.value = value;
        this.byteOrder = Objects.requireNonNull(byteOrder);
    }

    /**
     * Creates a new DWORD registry value.
     *
     * @param name The name of the registry value.
     * @param value The registry value's DWORD value.
     * @return The created DWORD registry value.
     * @throws NullPointerException If the given name is {@code null}.
     */
    public static DWordValue of(String name, int value) {
        // WinNT.REG_DWORD is the same as WinNT.REG_DWORD_LITTLE_ENDIAN
        return littleEndianOf(name, value);
    }

    /**
     * Creates a new little-endian DWORD registry value. This is actually the same as {@link #of(String, int)}, but makes the byte order explicit.
     *
     * @param name The name of the registry value.
     * @param value The registry value's DWORD value.
     * @return The created DWORD registry value.
     * @throws NullPointerException If the given name is {@code null}.
     */
    public static DWordValue littleEndianOf(String name, int value) {
        return new DWordValue(name, WinNT.REG_DWORD_LITTLE_ENDIAN, value, ByteOrder.LITTLE_ENDIAN);
    }

    /**
     * Creates a new big-endian DWORD registry value.
     *
     * @param name The name of the registry value.
     * @param value The registry value's DWORD value.
     * @return The created DWORD registry value.
     * @throws NullPointerException If the given name is {@code null}.
     */
    public static DWordValue bigEndianOf(String name, int value) {
        return new DWordValue(name, WinNT.REG_DWORD_BIG_ENDIAN, value, ByteOrder.BIG_ENDIAN);
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
    public DWordValue withName(String name) {
        return new DWordValue(name, type(), value, byteOrder);
    }

    /**
     * Returns a registry value with the same name as this registry value but a different value. This method does not change the byte order.
     *
     * @param value The value of the registry value to return.
     * @return A registry value with the same name as this registry value and the given value.
     */
    public DWordValue withValue(int value) {
        return new DWordValue(name(), type(), value, byteOrder);
    }

    /**
     * Returns a registry value with the same name as this registry value but a different little-endian value.
     *
     * @param value The value of the registry value to return.
     * @return A registry value with the same name as this registry value and the given value.
     */
    public DWordValue withLittleEndianValue(int value) {
        return littleEndianOf(name(), value);
    }

    /**
     * Returns a registry value with the same name as this registry value but a different big-endian value.
     *
     * @param value The value of the registry value to return.
     * @return A registry value with the same name as this registry value and the given value.
     */
    public DWordValue withBigEndianValue(int value) {
        return bigEndianOf(name(), value);
    }

    /**
     * Returns a registry value with the same name and value as this registry value but little-endian byte order.
     *
     * @return A registry value with the same name and value as this registry value but little-endian byte order.
     */
    public DWordValue withLittleEndianValue() {
        return littleEndianOf(name(), value);
    }

    /**
     * Returns a registry value with the same name and value as this registry value but big-endian byte order.
     *
     * @return A registry value with the same name and value as this registry value but big-endian byte order.
     */
    public DWordValue withBigEndianValue() {
        return bigEndianOf(name(), value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!super.equals(o)) {
            return false;
        }
        DWordValue other = (DWordValue) o;
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
