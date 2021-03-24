/*
 * ServiceStatus.java
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
 * The possible service statuses.
 *
 * @author Rob Spoor
 */
public enum ServiceStatus {
    /** Indicates a service is stopped. */
    STOPPED(Winsvc.SERVICE_STOPPED, false),

    /** Indicates a service is starting. */
    STARTING(Winsvc.SERVICE_START_PENDING, true),

    /** Indicates a service is stopping. */
    STOPPING(Winsvc.SERVICE_STOP_PENDING, true),

    /** Indicates a service is running (started). */
    RUNNING(Winsvc.SERVICE_RUNNING, false),

    /** Indicates a service is continuing. */
    CONTINUING(Winsvc.SERVICE_CONTINUE_PENDING, true),

    /** Indicates a service is pausing. */
    PAUSING(Winsvc.SERVICE_PAUSE_PENDING, true),

    /** Indicates a service is paused. */
    PAUSED(Winsvc.SERVICE_PAUSED, false),
    ;

    private final int value;
    private final boolean transitionStatus;

    ServiceStatus(int type, boolean transitionStatus) {
        this.value = type;
        this.transitionStatus = transitionStatus;
    }

    /**
     * Returns whether or not this status is a transition status, e.g. the service is moving from one status to another.
     *
     * @return {@code true} if this status is a transition status, or {@code false} otherwise.
     */
    public boolean isTransitionStatus() {
        return transitionStatus;
    }

    static ServiceStatus of(int value) {
        for (ServiceStatus startType : values()) {
            if (value == startType.value) {
                return startType;
            }
        }
        throw new IllegalArgumentException(Messages.ServiceStatus.unsupported.get(value));
    }
}
