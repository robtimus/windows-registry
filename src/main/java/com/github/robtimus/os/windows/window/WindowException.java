/*
 * WindowException.java
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

package com.github.robtimus.os.windows.window;

import com.sun.jna.platform.win32.Win32Exception;

/**
 * Thrown when an error occurred while trying to access native windows.
 *
 * @author Rob Spoor
 */
@SuppressWarnings("serial")
public class WindowException extends Win32Exception {

    /**
     * Creates a new exception.
     *
     * @param code The error code that was returned from the Windows API.
     */
    public WindowException(int code) {
        super(code);
    }
}
