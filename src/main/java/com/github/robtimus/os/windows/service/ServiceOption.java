/*
 * ServiceOption.java
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

package com.github.robtimus.os.windows.service;

import com.sun.jna.platform.win32.Winsvc;

/**
 * An object that configures how to open a service manager.
 *
 * @author Rob Spoor
 */
public enum ServiceOption {
    /** Indicates services can be created. */
    CREATE(Winsvc.SC_MANAGER_CREATE_SERVICE),

    /** Indicates services can be changed. */
    CHANGE(Winsvc.SERVICE_CHANGE_CONFIG),

    /** Indicates services can be deleted. */
    DELETE(0),
    ;

    final int dwDesiredAccess;

    ServiceOption(int dwDesiredAccess) {
        this.dwDesiredAccess = dwDesiredAccess;
    }
}
