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

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import com.github.robtimus.os.windows.service.Advapi32Extended.QUERY_SERVICE_CONFIG;
import com.github.robtimus.os.windows.service.Advapi32Extended.SERVICE_DELAYED_AUTO_START_INFO;
import com.github.robtimus.os.windows.service.Advapi32Extended.SERVICE_DESCRIPTION;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.platform.win32.Winsvc;
import com.sun.jna.platform.win32.Winsvc.ENUM_SERVICE_STATUS;
import com.sun.jna.platform.win32.Winsvc.ENUM_SERVICE_STATUS_PROCESS;
import com.sun.jna.platform.win32.Winsvc.SERVICE_STATUS;
import com.sun.jna.platform.win32.Winsvc.SERVICE_STATUS_PROCESS;

/**
 * Container for classes with information about Windows services.
 *
 * @author Rob Spoor
 */
public final class Service {

    private Service() {
        throw new IllegalStateException("cannot create instances of " + getClass().getName()); //$NON-NLS-1$
    }

    /**
     * A descriptor of a Windows service.
     *
     * @author Rob Spoor
     */
    public static final class Descriptor {

        private final String serviceName;
        private final String displayName;
        private final TypeInfo typeInfo;

        Descriptor(String serviceName, QUERY_SERVICE_CONFIG config) {
            this.serviceName = serviceName;
            this.displayName = config.lpDisplayName;
            this.typeInfo = new TypeInfo(config.dwServiceType);
        }

        Descriptor(ENUM_SERVICE_STATUS_PROCESS status) {
            this.serviceName = status.lpServiceName;
            this.displayName = status.lpDisplayName;
            this.typeInfo = new TypeInfo(status.ServiceStatusProcess.dwServiceType);
        }

        Descriptor(ENUM_SERVICE_STATUS status) {
            this.serviceName = status.lpServiceName;
            this.displayName = status.lpDisplayName;
            this.typeInfo = new TypeInfo(status.ServiceStatus.dwServiceType);
        }

        /**
         * Returns the service name.
         *
         * @return The service name.
         */
        public String serviceName() {
            return serviceName;
        }

        /**
         * Returns the service display name.
         *
         * @return The service display name.
         */
        public String displayName() {
            return displayName;
        }

        /**
         * Returns information about the service type.
         *
         * @return An object containing information about the service type.
         */
        public TypeInfo typeInfo() {
            return typeInfo;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || o.getClass() != getClass()) {
                return false;
            }
            Service.Descriptor other = (Service.Descriptor) o;
            return serviceName.equals(other.serviceName);
        }

        @Override
        public int hashCode() {
            return serviceName.hashCode();
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    /**
     * Information about a Windows service's type. This information is fixed as long as the service is not updated externally.
     *
     * @author Rob Spoor
     */
    public static final class TypeInfo {

        private final int serviceType;

        TypeInfo(int serviceType) {
            this.serviceType = serviceType;
        }

        /**
         * Returns whether or not the service is a driver service.
         *
         * @return {@code true} if the service is a driver service, or {@code false} otherwise.
         */
        public boolean isDriver() {
            return isSet(serviceType, WinNT.SERVICE_KERNEL_DRIVER)
                    || isSet(serviceType, WinNT.SERVICE_FILE_SYSTEM_DRIVER)
                    || isSet(serviceType, WinNT.SERVICE_ADAPTER)
                    || isSet(serviceType, WinNT.SERVICE_RECOGNIZER_DRIVER);
        }

        /**
         * Returns whether or not the service shares a process with one or more other services.
         *
         * @return {@code true} if the service shares a process with one or more other services, or {@code false} if runs in its own process.
         */
        public boolean isShared() {
            return isSet(serviceType, WinNT.SERVICE_WIN32_SHARE_PROCESS)
                    || isSet(serviceType, 0x00000060); // SERVICE_USER_SHARE_PROCESS
        }

        /**
         * Returns whether or not the service runs under the logged-on user account.
         *
         * @return {@code true} if the service under the logged-on user account, or {@code false} otherwise.
         */
        public boolean isUserProcess() {
            return isSet(serviceType, 0x00000050) // WinNT.SERVICE_USER_OWN_PROCESS
                    || isSet(serviceType, 0x00000060); // SERVICE_USER_SHARE_PROCESS
        }

        /**
         * Returns whether or not the service can interact with the desktop.
         *
         * @return {@code true} if the service can interact with the desktop, or {@code false} otherwise.
         */
        public boolean isInteractiveProcess() {
            return isSet(serviceType, WinNT.SERVICE_INTERACTIVE_PROCESS);
        }

        @Override
        @SuppressWarnings("nls")
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append('[');
            appendField(sb, "driver", isDriver());
            appendField(sb, "shared", isShared());
            appendField(sb, "userProcess", isUserProcess());
            appendField(sb, "interactiveProcess", isInteractiveProcess());
            sb.append(']');
            return sb.toString();
        }
    }

    /**
     * Information about a Windows service. This information is fixed as long as the service is not updated explicitly.
     *
     * @author Rob Spoor
     */
    public static final class Info {

        private final String description;
        private final String executable;
        private final StartInfo startInfo;
        private final String logonAccount;
        private final List<String> dependencies;

        Info(QUERY_SERVICE_CONFIG config, SERVICE_DESCRIPTION description, SERVICE_DELAYED_AUTO_START_INFO delayedAutoStartInfo) {
            this.description = description.lpDescription;
            this.executable = config.lpBinaryPathName;
            this.startInfo = new StartInfo(config, delayedAutoStartInfo);
            this.logonAccount = config.lpServiceStartName;
            this.dependencies = config.dependencies();
        }

        /**
         * Returns the service description.
         *
         * @return An {@link Optional} describing the service description, or {@link Optional#empty()} if the service has no description.
         */
        public Optional<String> description() {
            return Optional.ofNullable(description);
        }

        /**
         * Returns the service executable.
         *
         * @return The service executable.
         */
        public String executable() {
            return executable;
        }

        /**
         * Returns information about how the service starts.
         *
         * @return An object containing information about how the service starts.
         */
        public StartInfo startInfo() {
            return startInfo;
        }

        /**
         * Returns the service logon account.
         *
         * @return The service logon account.
         */
        public String logonAccount() {
            return logonAccount;
        }

        /**
         * Returns the names of the dependencies of the service.
         *
         * @return A list with the names of the dependencies of the service; empty if there are no dependencies.
         */
        public List<String> dependencies() {
            return dependencies;
        }

        @Override
        @SuppressWarnings("nls")
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append('[');
            appendField(sb, "description", description);
            appendField(sb, "executable", executable);
            appendField(sb, "startInfo", startInfo);
            appendField(sb, "logonAccount", logonAccount);
            appendField(sb, "dependencies", dependencies);
            sb.append(']');
            return sb.toString();
        }
    }

    /**
     * Information about how a Windows service starts. This information is fixed as long as the service is not updated explicitly.
     *
     * @author Rob Spoor
     */
    public static final class StartInfo {

        private final StartType startType;
        private final Boolean delayedStart;

        StartInfo(QUERY_SERVICE_CONFIG config, SERVICE_DELAYED_AUTO_START_INFO delayedAutoStartInfo) {
            this.startType = StartType.of(config.dwStartType);
            this.delayedStart = startType == StartType.AUTOMATIC ? delayedAutoStartInfo.fDelayedAutostart : null;
        }

        /**
         * Returns the service start type.
         *
         * @return The service start type.
         */
        public StartType startType() {
            return startType;
        }

        /**
         * Returns whether the service is started after other auto-start services are started plus a short delay, or during system boot.
         *
         * @return An {@link Optional} with value {@code true} if the service is started after other auto-start services are started plus a short
         *         delay, an {@link Optional} with value {@code false} if the service is started during system boot,
         *         or {@link Optional#empty()} if the {@link #startType()} is not {@link StartType#AUTOMATIC}.
         */
        public Optional<Boolean> delayedStart() {
            return Optional.ofNullable(delayedStart);
        }

        @Override
        @SuppressWarnings("nls")
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append('[');
            appendField(sb, "startType", startType);
            appendField(sb, "delayedStart", delayedStart);
            sb.append(']');
            return sb.toString();
        }
    }

    /**
     * The possible Windows service start types.
     *
     * @author Rob Spoor
     */
    public enum StartType {
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

        StartType(int type) {
            this.value = type;
        }

        static StartType of(int value) {
            for (StartType startType : values()) {
                if (value == startType.value) {
                    return startType;
                }
            }
            throw new IllegalArgumentException(Messages.Service.StartType.unsupported.get(value));
        }
    }

    /**
     * Information snapshot about a Windows service's status.
     *
     * @author Rob Spoor
     */
    public static final class StatusInfo {

        private final Status status;
        private final int controlsAccepted;
        private final ProcessHandle process;

        StatusInfo(SERVICE_STATUS_PROCESS status) {
            this.status = Status.of(status.dwCurrentState);
            controlsAccepted = status.dwControlsAccepted;
            process = ProcessHandle.of(status.dwProcessId).orElse(null);
        }

        StatusInfo(SERVICE_STATUS status) {
            this.status = Status.of(status.dwCurrentState);
            controlsAccepted = status.dwControlsAccepted;
            process = null;
        }

        /**
         * Returns the current service status.
         *
         * @return The current service status.
         */
        public Status status() {
            return status;
        }

        /**
         * Returns whether or not the service can start.
         *
         * @return {@code true} if the service can start, or {@code false} otherwise.
         */
        public boolean canStart() {
            // The controlsAccepted flag doesn't mention starting, but services can only be started when they are actually stopped
            return status == Status.STOPPED;
        }

        /**
         * Returns whether or not the service can stop.
         *
         * @return {@code true} if the service can stop, or {@code false} otherwise.
         */
        public boolean canStop() {
            // Stopping while transitioning (e.g. starting, stopping) is not allowed
            return isSet(controlsAccepted, Winsvc.SERVICE_ACCEPT_STOP) && !status.isTransitionStatus();
        }

        /**
         * Returns whether or not the service can pause.
         *
         * @return {@code true} if the service can pause, or {@code false} otherwise.
         */
        public boolean canPause() {
            // Pausing while transitioning (e.g. starting, stopping) is not allowed
            return isSet(controlsAccepted, Winsvc.SERVICE_ACCEPT_PAUSE_CONTINUE) && !status.isTransitionStatus();
        }

        /**
         * Returns whether or not the service can resume.
         *
         * @return {@code true} if the service can resume, or {@code false} otherwise.
         */
        public boolean canResume() {
            // Resuming while transitioning (e.g. starting, stopping) is not allowed
            return isSet(controlsAccepted, Winsvc.SERVICE_ACCEPT_PAUSE_CONTINUE) && !status.isTransitionStatus();
        }

        /**
         * Returns a handle to the service process.
         *
         * @return An {@link Optional} describing the service process handle, or {@link Optional#empty()} if the service process is not available.
         */
        public Optional<ProcessHandle> process() {
            return Optional.ofNullable(process);
        }

        @Override
        @SuppressWarnings("nls")
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append('[');
            appendField(sb, "status", status);
            appendField(sb, "canStart", canStart());
            appendField(sb, "canStop", canStop());
            appendField(sb, "canPause", canPause());
            appendField(sb, "canResume", canResume());
            appendField(sb, "process", process);
            sb.append(']');
            return sb.toString();
        }
    }

    /**
     * The possible Windows service statuses.
     *
     * @author Rob Spoor
     */
    public enum Status {
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

        Status(int type, boolean transitionStatus) {
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

        static Status of(int value) {
            for (Status startType : values()) {
                if (value == startType.value) {
                    return startType;
                }
            }
            throw new IllegalArgumentException(Messages.Service.Status.unsupported.get(value));
        }
    }

    /**
     * A class encapsulating both a {@link Descriptor} and a {@link StatusInfo} for a Windows service.
     *
     * @author Rob Spoor
     */
    public static final class DescriptorAndStatusInfo {

        private final Descriptor descriptor;
        private final StatusInfo statusInfo;

        DescriptorAndStatusInfo(ENUM_SERVICE_STATUS_PROCESS status) {
            this.descriptor = new Descriptor(status);
            this.statusInfo = new StatusInfo(status.ServiceStatusProcess);
        }

        DescriptorAndStatusInfo(ENUM_SERVICE_STATUS status) {
            this.descriptor = new Descriptor(status);
            this.statusInfo = new StatusInfo(status.ServiceStatus);
        }

        DescriptorAndStatusInfo(String serviceName, QUERY_SERVICE_CONFIG config, SERVICE_STATUS_PROCESS status) {
            this.descriptor = new Descriptor(serviceName, config);
            this.statusInfo = new StatusInfo(status);
        }

        /**
         * Returns the service descriptor.
         *
         * @return The service descriptor.
         */
        public Descriptor descriptor() {
            return descriptor;
        }

        /**
         * The service status information.
         *
         * @return The service status information.
         */
        public StatusInfo statusInfo() {
            return statusInfo;
        }

        @Override
        @SuppressWarnings("nls")
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append('[');
            appendField(sb, "descriptor", descriptor);
            appendField(sb, "statusInfo", statusInfo);
            sb.append(']');
            return sb.toString();
        }
    }

    private static boolean isSet(int value, int flag) {
        return (value & flag) == flag;
    }

    @SuppressWarnings("nls")
    private static void appendField(StringBuilder sb, String name, Object value) {
        if (value != null) {
            if (sb.length() != 1) {
                sb.append(", ");
            }
            sb.append(name).append(": ").append(value);
        }
    }

    @SuppressWarnings("nls")
    private static void appendField(StringBuilder sb, String name, Collection<?> value) {
        if (value != null && !value.isEmpty()) {
            if (sb.length() != 1) {
                sb.append(", ");
            }
            sb.append(name).append(": ").append(value);
        }
    }
}
