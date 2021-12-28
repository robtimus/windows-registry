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

import com.sun.jna.platform.win32.Kernel32Util;
import com.sun.jna.platform.win32.WinError;

/**
 * Thrown when an error occurred while trying to access or modify the Windows registry.
 *
 * @author Rob Spoor
 */
@SuppressWarnings("serial")
public class RegistryException extends RuntimeException {

    private final int errorCode;

    /**
     * Creates a new exception.
     *
     * @param errorCode The error code that was returned from the Windows API.
     */
    public RegistryException(int errorCode) {
        super(Kernel32Util.formatMessage(errorCode));
        this.errorCode = errorCode;
    }

    /**
     * Returns the error code that was returned from the Windows API.
     *
     * @return The error code that was returned from the Windows API.
     */
    public int errorCode() {
        return errorCode;
    }

    static RegistryException of(int errorCode, String path) {
        switch (errorCode) {
            case WinError.ERROR_KEY_DELETED:
            case WinError.ERROR_FILE_NOT_FOUND:
                return new NoSuchRegistryKeyException(errorCode, path);
            case WinError.ERROR_ACCESS_DENIED:
                return new RegistryAccessDeniedException();
            case WinError.ERROR_INVALID_HANDLE:
                return new InvalidRegistryHandleException();
            default:
                return new RegistryException(errorCode);
        }
    }

    static RegistryException of(int errorCode, String path, String name) {
        switch (errorCode) {
            case WinError.ERROR_KEY_DELETED:
                return new NoSuchRegistryKeyException(errorCode, path);
            case WinError.ERROR_FILE_NOT_FOUND:
                return new NoSuchRegistryValueException(path, name);
            case WinError.ERROR_ACCESS_DENIED:
                return new RegistryAccessDeniedException();
            case WinError.ERROR_INVALID_HANDLE:
                return new InvalidRegistryHandleException();
            default:
                return new RegistryException(errorCode);
        }
    }
}
