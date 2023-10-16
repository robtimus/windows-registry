/*
 * NoSuchRegistryKeyException.java
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
 * Thrown when an attempt is made to access a registry key that does not exist.
 *
 * @author Rob Spoor
 */
@SuppressWarnings("serial")
public class NoSuchRegistryKeyException extends RegistryException {

    /**
     * Creates a new exception.
     *
     * @param path The path that was used to access the non-existing registry key.
     */
    public NoSuchRegistryKeyException(String path) {
        this(path, null);
    }

    /**
     * Creates a new exception.
     *
     * @param path The path that was used to access the non-existing registry key.
     * @param machineName The remote machine on which the non-existing registry key was attempted to be accessed,
     *                        or {@code null} for the local machine.
     * @since 1.1
     */
    public NoSuchRegistryKeyException(String path, String machineName) {
        this(WinError.ERROR_FILE_NOT_FOUND, path, machineName);
    }

    NoSuchRegistryKeyException(int errorCode, String path, String machineName) {
        super(errorCode, path, machineName);
    }
}
