/*
 * QWordValue.java
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
import com.sun.jna.platform.win32.WinNT;

/**
 * A representation of QWORD registry values.
 *
 * @author Rob Spoor
 */
public final class QWordValue extends SettableRegistryValue {

    private final long value;

    /**
     * Creates a new QWORD registry value.
     *
     * @param name The name of the registry value.
     * @param value The registry value's QWORD value.
     */
    public QWordValue(String name, long value) {
        super(name, WinNT.REG_QWORD_LITTLE_ENDIAN);
        this.value = value;
    }

    QWordValue(String name, byte[] data) {
        super(name, WinNT.REG_QWORD_LITTLE_ENDIAN);

        ByteBuffer buffer = ByteBuffer.wrap(data);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        this.value = buffer.getLong();
    }

    /**
     * Returns the registry value's QWORD value.
     *
     * @return The registry value's QWORD value.
     */
    public long value() {
        return value;
    }

    @Override
    byte[] rawData() {
        byte[] data = new byte[Long.SIZE / Byte.SIZE];
        ByteBuffer buffer = ByteBuffer.wrap(data);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.putLong(value);
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
        QWordValue other = (QWordValue) o;
        return value == other.value;
    }

    @Override
    public int hashCode() {
        int hash = super.hashCode();
        hash = 31 * hash + Long.hashCode(value);
        return hash;
    }

    @Override
    @SuppressWarnings("nls")
    public String toString() {
        return name() + "=" + value;
    }
}
