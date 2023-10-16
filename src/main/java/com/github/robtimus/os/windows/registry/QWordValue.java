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

import java.lang.foreign.SegmentAllocator;
import java.lang.foreign.ValueLayout;
import java.nio.ByteOrder;
import com.github.robtimus.os.windows.registry.foreign.BytePointer;
import com.github.robtimus.os.windows.registry.foreign.WinNT;

/**
 * A representation of QWORD registry values.
 * Instances of this class are immutable.
 *
 * @author Rob Spoor
 */
public final class QWordValue extends SettableRegistryValue {

    private static final ValueLayout.OfLong LAYOUT = ValueLayout.JAVA_LONG.withOrder(ByteOrder.LITTLE_ENDIAN);

    private final long value;

    QWordValue(String name, BytePointer data) {
        super(name, WinNT.REG_QWORD_LITTLE_ENDIAN);

        this.value = data.toLong(LAYOUT);
    }

    private QWordValue(String name, long value) {
        super(name, WinNT.REG_QWORD_LITTLE_ENDIAN);
        this.value = value;
    }

    /**
     * Creates a new QWORD registry value.
     *
     * @param name The name of the registry value.
     * @param value The registry value's QWORD value.
     * @return The created QWORD registry value.
     * @throws NullPointerException If the given name is {@code null}.
     */
    public static QWordValue of(String name, long value) {
        return new QWordValue(name, value);
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
    BytePointer rawData(SegmentAllocator allocator) {
        return BytePointer.withLong(value, LAYOUT, allocator);
    }

    @Override
    public QWordValue withName(String name) {
        return new QWordValue(name, value);
    }

    /**
     * Returns a registry value with the same name as this registry value but a different value.
     *
     * @param value The value of the registry value to return.
     * @return A registry value with the same name as this registry value and the given value.
     */
    public QWordValue withValue(long value) {
        return of(name(), value);
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
