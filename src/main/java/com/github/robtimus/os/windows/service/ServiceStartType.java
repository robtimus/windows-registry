/*
 * ServiceStartType.java
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

import com.sun.jna.platform.win32.WinNT;

/**
 * The possible service start types.
 *
 * @author Rob Spoor
 */
public enum ServiceStartType {
    /** Indicates a device driver is started by the system loader. */
    BOOT(WinNT.SERVICE_BOOT_START),

    /** Indicates a device driver is started by the IoInitSystem function. */
    SYSTEM(WinNT.SERVICE_SYSTEM_START),

    /** Indicates a service is started automatically during system startup. */
    AUTOMATIC(WinNT.SERVICE_AUTO_START),

    /** Indicates a service needs to be started manually. */
    MANUAL(WinNT.SERVICE_DEMAND_START),

    /** Indicates a service is disabled. */
    DISABLED(WinNT.SERVICE_DISABLED),
    ;

    private final int value;

    ServiceStartType(int type) {
        this.value = type;
    }

    static ServiceStartType of(int value) {
        for (ServiceStartType startType : values()) {
            if (value == startType.value) {
                return startType;
            }
        }
        throw new IllegalArgumentException(Messages.ServiceStartType.unsupported.get(value));
    }
}
