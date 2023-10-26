/*
 * SettableRegistryValue.java
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
import com.github.robtimus.os.windows.registry.foreign.BytePointer;

/**
 * A representation of registry values that can be set.
 * This includes string values, numeric values and binary values, but excludes values like links or resource lists.
 *
 * @author Rob Spoor
 */
public abstract sealed class SettableRegistryValue extends RegistryValue permits BinaryValue, DWordValue, MultiStringValue, QWordValue, StringValue {

    SettableRegistryValue(String name, int type) {
        super(name, type);
    }

    abstract BytePointer rawData(SegmentAllocator allocator);

    /**
     * Returns a registry value with the same value as this registry value but a different name.
     *
     * @param name The name of the registry value to return.
     * @return A registry value with the same value as this registry value and the given name.
     * @throws NullPointerException If the given name is {@code null}.
     */
    public abstract SettableRegistryValue withName(String name);
}
