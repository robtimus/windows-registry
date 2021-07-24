/*
 * WindowsException.java
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

package com.github.robtimus.os.windows;

import com.sun.jna.platform.win32.Kernel32Util;

/**
 * Thrown when an error occurred while trying to call the Windows API.
 *
 * @author Rob Spoor
 */
@SuppressWarnings("serial")
public class WindowsException extends RuntimeException {

    private final int errorCode;

    /**
     * Creates a new exception.
     *
     * @param errorCode The error code that was returned from the Windows API.
     */
    public WindowsException(int errorCode) {
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
}