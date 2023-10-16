/*
 * NoSuchRegistryValueException.java
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

import com.github.robtimus.os.windows.registry.foreign.WinError;

/**
 * Thrown when an attempt is made to access a registry value that does not exist.
 *
 * @author Rob Spoor
 */
@SuppressWarnings("serial")
public class NoSuchRegistryValueException extends RegistryException {

    private final String name;

    /**
     * Creates a new exception.
     *
     * @param path The path to the registry key for which the non-existing registry value was attempted to be accessed.
     * @param name The name of the non-existing registry value.
     */
    public NoSuchRegistryValueException(String path, String name) {
        this(path, null, name);
    }

    /**
     * Creates a new exception.
     *
     * @param path The path to the registry key for which the non-existing registry value was attempted to be accessed.
     * @param machineName The remote machine of the registry key for which the non-existing registry value was attempted to be accessed,
     *                        or {@code null} for the local machine.
     * @param name The name of the non-existing registry value.
     * @since 1.1
     */
    public NoSuchRegistryValueException(String path, String machineName, String name) {
        super(WinError.ERROR_FILE_NOT_FOUND, path, machineName);
        this.name = name;
    }

    /**
     * Returns the name of the non-existing registry value.
     *
     * @return The name of the non-existing registry value.
     */
    public String name() {
        return name;
    }
}
