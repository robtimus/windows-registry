/*
 * InvalidRegistryHandleException.java
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

import com.sun.jna.platform.win32.WinError;

/**
 * Thrown when an attempt is made to access a registry key using a handle that is no longer valid.
 *
 * @author Rob Spoor
 */
@SuppressWarnings("serial")
public class InvalidRegistryHandleException extends RegistryException {

    /**
     * Creates a new exception.
     *
     * @param path The path of the registry key the invalid handle is for.
     */
    public InvalidRegistryHandleException(String path) {
        this(path, null);
    }

    /**
     * Creates a new exception.
     *
     * @param path The path of the registry key the invalid handle is for.
     * @param machineName The remote machine of the registry key the invalid handle is for, or {@code null} for the local machine.
     * @since 1.1
     */
    public InvalidRegistryHandleException(String path, String machineName) {
        super(WinError.ERROR_INVALID_HANDLE, path, machineName);
    }
}
