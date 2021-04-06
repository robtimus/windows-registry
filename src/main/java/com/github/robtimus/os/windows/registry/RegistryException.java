/*
 * RegistryException.java
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

import com.github.robtimus.os.windows.WindowsException;
import com.sun.jna.platform.win32.WinError;

/**
 * Thrown when an error occurred while trying to access or modify the Windows registry.
 *
 * @author Rob Spoor
 */
@SuppressWarnings("serial")
public class RegistryException extends WindowsException {

    /**
     * Creates a new exception.
     *
     * @param errorCode The error code that was returned from the Windows API.
     */
    public RegistryException(int errorCode) {
        super(errorCode);
    }

    static RegistryException of(int errorCode, String path) {
        if (errorCode == WinError.ERROR_FILE_NOT_FOUND) {
            return new NoSuchRegistryKeyException(path);
        }
        throw new RegistryException(errorCode);
    }

    static RegistryException of(int errorCode, String path, String name) {
        if (errorCode == WinError.ERROR_FILE_NOT_FOUND) {
            return new NoSuchRegistryValueException(path, name);
        }
        throw new RegistryException(errorCode);
    }
}
