/*
 * Service.java
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
 * A representation of a Windows service.
 *
 * @author Rob Spoor
 */
public final class Service {

    /**
     * The possible service start types.
     *
     * @author Rob Spoor
     */
    public enum StartType {
        /** Indicates a service is started automatically during system startup. */
        AUTOMATIC(WinNT.SERVICE_AUTO_START),

        /** Indicates a service needs to be started manually. */
        MANUAL(WinNT.SERVICE_DEMAND_START),

        /** Indicates a service is disabled. */
        DISABLED(WinNT.SERVICE_DISABLED),
        ;

        private int value;

        StartType(int type) {
            this.value = type;
        }

        private static StartType of(int value) {
            for (StartType startType : values()) {
                if (value == startType.value) {
                    return startType;
                }
            }
            //throw new IllegalArgumentException(Messages.Service.StartType.unsupported.get(value));
            return null;
        }
    }

    private final String name;
    private final String displayName;
    private final String description;
    private final String binaryPath;
    private final StartType startType;
    public final int rawStartType;

    Service(String name, String displayName, String description, String binaryPath, int startType) {
        this.name = name;
        this.displayName = displayName;
        this.description = description;
        this.binaryPath = binaryPath;
        this.startType = StartType.of(startType);
        rawStartType = startType;
    }

    public String name() {
        return name;
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }

    public String binaryPath() {
        return binaryPath;
    }

    public StartType startType() {
        return startType;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
