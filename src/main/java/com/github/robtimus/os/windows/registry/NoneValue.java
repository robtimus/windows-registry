/*
 * NoneValue.java
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

import static com.github.robtimus.os.windows.registry.BinaryValue.HEX_FORMAT;
import static com.github.robtimus.os.windows.registry.foreign.WindowsConstants.REG_NONE;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Arrays;

/**
 * Represents registry values with no defined value types.
 *
 * @author Rob Spoor
 */
public final class NoneValue extends RegistryValue {

    private final byte[] data;

    NoneValue(String name, MemorySegment data, long dataLength) {
        super(name, REG_NONE);
        this.data = data.asSlice(0, dataLength).toArray(ValueLayout.JAVA_BYTE);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!super.equals(o)) {
            return false;
        }
        NoneValue other = (NoneValue) o;
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
        return name() + "=0x" + HEX_FORMAT.formatHex(data);
    }
}
