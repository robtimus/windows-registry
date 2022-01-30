/*
 * BinaryValue.java
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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import com.sun.jna.platform.win32.WinNT;

/**
 * A representation of binary registry values.
 *
 * @author Rob Spoor
 */
public final class BinaryValue extends SettableRegistryValue {

    private final byte[] data;

    BinaryValue(String name, byte[] data, int dataLength) {
        super(name, WinNT.REG_BINARY);
        this.data = Arrays.copyOf(data, dataLength);
    }

    private BinaryValue(String name, byte[] data) {
        super(name, WinNT.REG_BINARY);
        this.data = data;
    }

    /**
     * Creates a new binary registry value.
     *
     * @param name The name of the registry value.
     * @param data The registry value's binary data.
     * @return The created binary registry value.
     * @throws NullPointerException If the given name or byte array is {@code null}.
     */
    public static BinaryValue of(String name, byte[] data) {
        return new BinaryValue(name, data.clone());
    }

    /**
     * Creates a new binary registry value.
     *
     * @param name The name of the registry value.
     * @param data An input stream with the registry value's binary data.
     * @return The created binary registry value.
     * @throws NullPointerException If the given name or input stream is {@code null}.
     * @throws IOException If an I/O error occurs while reading from the given input stream.
     */
    public static BinaryValue of(String name, InputStream data) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        data.transferTo(outputStream);
        return new BinaryValue(name, outputStream.toByteArray());
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
    public BinaryValue withName(String name) {
        // The data is not exposed directly, so it's safe to share
        return new BinaryValue(name, data);
    }

    /**
     * Returns a registry value with the same name as this registry value but different binary data.
     *
     * @param data The binary data of the registry value to return.
     * @return A registry value with the same name as this registry value and the given binary data.
     * @throws NullPointerException If the given array is {@code null}.
     */
    public BinaryValue withData(byte[] data) {
        return of(name(), data);
    }

    /**
     * Returns a registry value with the same name as this registry value but different binary data.
     *
     * @param data An input stream with the binary data of the registry value to return.
     * @return A registry value with the same name as this registry value and the binary data from the given input stream.
     * @throws IOException If an I/O error occurs while reading from the given input stream.
     */
    public BinaryValue withData(InputStream data) throws IOException {
        return of(name(), data);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!super.equals(o)) {
            return false;
        }
        BinaryValue other = (BinaryValue) o;
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
        return name() + "=" + StringUtils.toHexString(data);
    }
}
