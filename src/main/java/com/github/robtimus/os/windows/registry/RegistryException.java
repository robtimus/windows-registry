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

import static com.github.robtimus.os.windows.registry.WindowsConstants.ERROR_ACCESS_DENIED;
import static com.github.robtimus.os.windows.registry.WindowsConstants.ERROR_FILE_NOT_FOUND;
import static com.github.robtimus.os.windows.registry.WindowsConstants.ERROR_INVALID_HANDLE;
import static com.github.robtimus.os.windows.registry.WindowsConstants.ERROR_KEY_DELETED;

/**
 * Thrown when an error occurred while trying to access or modify the Windows registry.
 *
 * @author Rob Spoor
 */
@SuppressWarnings("serial")
public class RegistryException extends RuntimeException {

    private final int errorCode;
    private final String path;
    private final String machineName;

    /**
     * Creates a new exception.
     *
     * @param errorCode The error code that was returned from the Windows API.
     * @param path The path of the registry key for which this exception was thrown.
     */
    public RegistryException(int errorCode, String path) {
        this(errorCode, path, null);
    }

    /**
     * Creates a new exception.
     *
     * @param errorCode The error code that was returned from the Windows API.
     * @param path The path of the registry key for which this exception was thrown.
     * @param machineName The remote machine of the registry key for which this exception was thrown, or {@code null} for the local machine.
     * @since 1.1
     */
    public RegistryException(int errorCode, String path, String machineName) {
        super(createMessage(errorCode, path, machineName));
        this.errorCode = errorCode;
        this.path = path;
        this.machineName = machineName;
    }

    @SuppressWarnings("nls")
    private static String createMessage(int errorCode, String path, String machineName) {
        StringBuilder sb = new StringBuilder();
        sb.append(path);
        if (machineName != null) {
            sb.append('@').append(machineName);
        }
        sb.append(": ");
        sb.append(Kernel32.formatMessage(errorCode));
        return sb.toString();
    }

    /**
     * Returns the error code that was returned from the Windows API.
     *
     * @return The error code that was returned from the Windows API.
     */
    public int errorCode() {
        return errorCode;
    }

    /**
     * Returns the path of the registry key for which this exception was thrown.
     *
     * @return The path of the registry key for which this exception was thrown.
     */
    public String path() {
        return path;
    }

    /**
     * Returns the remote machine of the registry key for which this exception was thrown.
     *
     * @return The remote machine of the registry key for which this exception was thrown, or {@code null} for the local machine.
     * @since 1.1
     */
    public String machineName() {
        return machineName;
    }

    static RegistryException forKey(int errorCode, String path, String machineName) {
        switch (errorCode) {
            case ERROR_KEY_DELETED, ERROR_FILE_NOT_FOUND:
                return new NoSuchRegistryKeyException(errorCode, path, machineName);
            case ERROR_ACCESS_DENIED:
                return new RegistryAccessDeniedException(path, machineName);
            case ERROR_INVALID_HANDLE:
                return new InvalidRegistryHandleException(path, machineName);
            default:
                return new RegistryException(errorCode, path, machineName);
        }
    }

    static RegistryException forValue(int errorCode, String path, String machineName, String name) {
        switch (errorCode) {
            case ERROR_KEY_DELETED:
                return new NoSuchRegistryKeyException(errorCode, path, machineName);
            case ERROR_FILE_NOT_FOUND:
                return new NoSuchRegistryValueException(path, machineName, name);
            case ERROR_ACCESS_DENIED:
                return new RegistryAccessDeniedException(path, machineName);
            case ERROR_INVALID_HANDLE:
                return new InvalidRegistryHandleException(path, machineName);
            default:
                return new RegistryException(errorCode, path, machineName);
        }
    }
}
