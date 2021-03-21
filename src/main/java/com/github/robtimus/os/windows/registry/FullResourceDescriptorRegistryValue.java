/*
 * FullResourceDescriptorRegistryValue.java
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

import java.util.Arrays;
import com.sun.jna.platform.win32.WinNT;

/**
 * A representation of full resource descriptor registry values.
 *
 * @author Rob Spoor
 */
public class FullResourceDescriptorRegistryValue extends RegistryValue {

    private final byte[] data;

    FullResourceDescriptorRegistryValue(String name, byte[] data, int dataLength) {
        super(name, WinNT.REG_FULL_RESOURCE_DESCRIPTOR);
        this.data = Arrays.copyOfRange(data, 0, dataLength);
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
        FullResourceDescriptorRegistryValue other = (FullResourceDescriptorRegistryValue) o;
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
        return name() + "=" + BinaryRegistryValue.toString(data);
    }
}
