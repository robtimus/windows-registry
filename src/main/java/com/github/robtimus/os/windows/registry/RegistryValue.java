/*
 * RegistryValue.java
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
import com.sun.jna.platform.win32.WinNT;

/**
 * A representation of registry values.
 *
 * @author Rob Spoor
 */
public abstract class RegistryValue {

    private final String name;
    private final int type;

    RegistryValue(String name, int type) {
        this.name = Objects.requireNonNull(name);
        this.type = type;
    }

    /**
     * Returns the name of the registry value.
     *
     * @return The name of the registry value.
     */
    public String name() {
        return name;
    }

    int type() {
        return type;
    }

    abstract byte[] rawData();

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || o.getClass() != getClass()) {
            return false;
        }
        RegistryValue other = (RegistryValue) o;
        return name.equals(other.name)
                && type == other.type;
    }

    @Override
    public int hashCode() {
        int hash = name.hashCode();
        hash = 31 * hash + type;
        return hash;
    }

    static RegistryValue of(String name, int type, byte[] data, int dataLength) {
        switch (type) {
            case WinNT.REG_NONE:
                return new NoneRegistryValue(name, data, dataLength);
            case WinNT.REG_SZ:
                return new StringRegistryValue(name, data, dataLength);
            case WinNT.REG_EXPAND_SZ:
                return new ExpandableStringRegistryValue(name, data, dataLength);
            case WinNT.REG_BINARY:
                return new BinaryRegistryValue(name, data, dataLength);
            case WinNT.REG_DWORD_LITTLE_ENDIAN:
            case WinNT.REG_DWORD_BIG_ENDIAN:
                return new DWordRegistryValue(name, type, data);
            case WinNT.REG_LINK:
                return new LinkRegistryValue(name, data, dataLength);
            case WinNT.REG_MULTI_SZ:
                return new MultiStringRegistryValue(name, data, dataLength);
            case WinNT.REG_RESOURCE_LIST:
                return new ResourceListRegistryValue(name, data, dataLength);
            case WinNT.REG_FULL_RESOURCE_DESCRIPTOR:
                return new FullResourceDescriptorRegistryValue(name, data, dataLength);
            case WinNT.REG_RESOURCE_REQUIREMENTS_LIST:
                return new ResourceRequirementsListRegistryValue(name, data, dataLength);
            case WinNT.REG_QWORD_LITTLE_ENDIAN:
                return new QWordRegistryValue(name, data);
            default:
                throw new IllegalStateException(Messages.RegistryValue.unsupportedType.get(type));
        }
    }
}
