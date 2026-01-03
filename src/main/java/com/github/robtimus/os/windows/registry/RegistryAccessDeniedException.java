/*
 * RegistryAccessDeniedException.java
 * Copyright 2021 Rob Spoor
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

import static com.github.robtimus.os.windows.registry.WindowsConstants.ERROR_ACCESS_DENIED;

/**
 * Thrown when access is denied for a requested operation.
 *
 * @author Rob Spoor
 */
@SuppressWarnings("serial")
public class RegistryAccessDeniedException extends RegistryException {

    /**
     * Creates a new exception.
     *
     * @param path The path of the registry key for which access was denied.
     */
    public RegistryAccessDeniedException(String path) {
        this(path, null);
    }

    /**
     * Creates a new exception.
     *
     * @param path The path of the registry key for which access was denied.
     * @param machineName The remote machine of the registry key for which access was denied, or {@code null} for the local machine.
     * @since 1.1
     */
    public RegistryAccessDeniedException(String path, String machineName) {
        super(ERROR_ACCESS_DENIED, path, machineName);
    }
}
