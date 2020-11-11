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

import com.sun.jna.platform.win32.WinError;

/**
 * Thrown when an attempt is made to access a registry key that does not exist.
 *
 * @author Rob Spoor
 */
@SuppressWarnings("serial")
public class NoSuchRegistryKeyException extends RegistryException {

    private final String path;

    /**
     * Creates a new exception.
     *
     * @param path The path that was used to access the non-existing registry key.
     */
    public NoSuchRegistryKeyException(String path) {
        super(WinError.ERROR_FILE_NOT_FOUND);
        this.path = path;
    }

    /**
     * Returns the path that was used to access the non-existing registry key.
     *
     * @return The path that was used to access the non-existing registry key.
     */
    public String path() {
        return path;
    }
}
