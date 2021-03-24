/*
 * ServiceHandle.java
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

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import com.github.robtimus.os.windows.AccessDeniedException;
import com.github.robtimus.os.windows.service.Advapi32Extended.QUERY_SERVICE_CONFIG;
import com.github.robtimus.os.windows.service.Advapi32Extended.SERVICE_DELAYED_AUTO_START_INFO;
import com.github.robtimus.os.windows.service.Advapi32Extended.SERVICE_DESCRIPTION;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.platform.win32.Winsvc;
import com.sun.jna.platform.win32.Winsvc.SERVICE_STATUS_PROCESS;

/**
 * Identifies and provides control of Windows services.
 * <p>
 * Each service handle is valid as long as the {@link ServiceManager} from which it originated is not closed and the Windows service is not deleted.
 *
 * @author Rob Spoor
 */
public final class ServiceHandle {

    private final ServiceManager serviceManager;
    private final String serviceName;

    ServiceHandle(ServiceManager serviceManager, String serviceName) {
        this.serviceManager = Objects.requireNonNull(serviceManager);
        this.serviceName = Objects.requireNonNull(serviceName);
    }

    /**
     * Returns the name of the Windows service.
     *
     * @return The name of the Windows service.
     */
    public String serviceName() {
        return serviceName;
    }

    /**
     * Returns the current status of the Windows service.
     *
     * @return The current status of the Windows service.
     * @throws IllegalStateException If the service manager from which this service handle originated is closed.
     */
    public StatusInfo status() {
        return serviceManager.status(this);
    }

    /**
     * Returns a snapshot of information about the Windows service.
     *
     * @return A snapshot of information about the Windows service.
     * @throws IllegalStateException If the service manager from which this service handle originated is closed.
     */
    public Info info() {
        return serviceManager.info(this);
    }

    /**
     * Returns all Windows services that have a dependency on this Windows service.
     *
     * @return A stream with all Windows services that have a dependency on this Windows service.
     * @throws IllegalStateException If the service manager from which this service handle originated is closed.
     */
    public Stream<ServiceHandle> dependents() {
        return serviceManager.dependents(this);
    }

    /**
     * Starts the Windows service.
     * This method does not wait until the Windows service has started but returns immediately.
     *
     * @param args Additional arguments for the service.
     * @throws IllegalStateException If the service manager from which this service handle originated is closed.
     * @throws AccessDeniedException If the current user does not have sufficient rights to start Windows services.
     */
    public void start(String... args) {
        serviceManager.start(this, args);
    }

    /**
     * Starts the Windows service.
     * This method waits until the Windows service has entered a non-transitional state, or the given maximum wait time has passed.
     * <p>
     * If all goes well, the resulting status will be {@link ServiceStatus#RUNNING}.
     * However, if the Windows service fails to start a different non-transitional status may be returned (usually {@link ServiceStatus#STOPPED}.
     * If the maximum wait time passes before the Windows service finishes starting, a {@link ServiceStatus#isTransitionStatus() transition status}
     * may be returned.
     *
     * @param maxWaitTime The maximum time in milliseconds to wait for the Windows service to start.
     * @param args Additional arguments for the service.
     * @return The status of the Windows service.
     * @throws IllegalStateException If the service manager from which this service handle originated is closed.
     * @throws AccessDeniedException If the current user does not have sufficient rights to start Windows services.
     */
    public StatusInfo startAndWait(long maxWaitTime, String... args) {
        return serviceManager.start(this, args, maxWaitTime);
    }

    /**
     * Stops the Windows service.
     * This method does not wait until the Windows service has stopped but returns immediately.
     *
     * @throws IllegalStateException If the service manager from which this service handle originated is closed.
     * @throws AccessDeniedException If the current user does not have sufficient rights to stop Windows services.
     */
    public void stop() {
        serviceManager.stop(this);
    }

    /**
     * Stops the Windows service.
     * This method waits until the Windows service has entered a non-transitional state, or the given maximum wait time has passed.
     * <p>
     * If all goes well, the resulting status will be {@link ServiceStatus#STOPPED}.
     * However, if the Windows service fails to stop a different non-transitional status may be returned.
     * If the maximum wait time passes before the Windows service finishes stopping, a {@link ServiceStatus#isTransitionStatus() transition status}
     * may be returned.
     *
     * @param maxWaitTime The maximum time in milliseconds to wait for the Windows service to stop.
     * @return The status of the Windows service.
     * @throws IllegalStateException If the service manager from which this service handle originated is closed.
     * @throws AccessDeniedException If the current user does not have sufficient rights to stop Windows services.
     */
    public StatusInfo stopAndWait(long maxWaitTime) {
        return serviceManager.stop(this, maxWaitTime);
    }

    /**
     * Pauses the Windows service.
     * This method does not wait until the Windows service has paused but returns immediately.
     *
     * @throws IllegalStateException If the service manager from which this service handle originated is closed.
     * @throws AccessDeniedException If the current user does not have sufficient rights to pause Windows services.
     */
    public void pause() {
        serviceManager.pause(this);
    }

    /**
     * Pauses the Windows service.
     * This method waits until the Windows service has entered a non-transitional state, or the given maximum wait time has passed.
     * <p>
     * If all goes well, the resulting status will be {@link ServiceStatus#PAUSED}.
     * However, if the Windows service fails to pause a different non-transitional status may be returned.
     * If the maximum wait time passes before the Windows service finishes pausing, a {@link ServiceStatus#isTransitionStatus() transition status}
     * may be returned.
     *
     * @param maxWaitTime The maximum time in milliseconds to wait for the Windows service to pause.
     * @return The status of the Windows service.
     * @throws IllegalStateException If the service manager from which this service handle originated is closed.
     * @throws AccessDeniedException If the current user does not have sufficient rights to pause Windows services.
     */
    public StatusInfo pauseAndWait(long maxWaitTime) {
        return serviceManager.pause(this, maxWaitTime);
    }

    /**
     * Resumes the Windows service.
     * This method does not wait until the Windows service has resumed but returns immediately.
     *
     * @throws IllegalStateException If the service manager from which this service handle originated is closed.
     * @throws AccessDeniedException If the current user does not have sufficient rights to resume Windows services.
     */
    public void resume() {
        serviceManager.resume(this);
    }

    /**
     * Resumes the Windows service.
     * This method waits until the Windows service has entered a non-transitional state, or the given maximum wait time has passed.
     * <p>
     * If all goes well, the resulting status will be {@link ServiceStatus#RUNNING}.
     * However, if the Windows service fails to resume a different non-transitional status may be returned (usually {@link ServiceStatus#PAUSED}.
     * If the maximum wait time passes before the Windows service finishes pausing, a {@link ServiceStatus#isTransitionStatus() transition status}
     * may be returned.
     *
     * @param maxWaitTime The maximum time in milliseconds to wait for the Windows service to resume.
     * @return The status of the Windows service.
     * @throws IllegalStateException If the service manager from which this service handle originated is closed.
     * @throws AccessDeniedException If the current user does not have sufficient rights to resume Windows services.
     */
    public StatusInfo resumeAndWait(long maxWaitTime) {
        return serviceManager.resume(this, maxWaitTime);
    }

    /**
     * Awaits until the Windows service has finished its latest status transition.
     * If the Windows service is in a non-transition status this method will return immediately.
     * <p>
     * If the maximum wait time passes before the Windows service finishes its latest status transition,
     * a {@link ServiceStatus#isTransitionStatus() transition status} may be returned.
     *
     * @param maxWaitTime The maximum time in milliseconds to wait for the Windows service to resume.
     * @return The status of the Windows service.
     */
    public StatusInfo awaitStatusTransition(long maxWaitTime) {
        return serviceManager.awaitStatusTransition(this, maxWaitTime);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || o.getClass() != getClass()) {
            return false;
        }
        ServiceHandle other = (ServiceHandle) o;
        return serviceManager.equals(other.serviceManager)
                && serviceName.equals(other.serviceName);
    }

    @Override
    public int hashCode() {
        return serviceManager.hashCode() ^ serviceName.hashCode();
    }

    @Override
    public String toString() {
        return serviceName;
    }

    /**
     * Information snapshot about the Windows service.
     *
     * @author Rob Spoor
     */
    public static final class Info {

        private final String displayName;
        private final String description;
        private final String executable;
        private final ServiceStartType startType;
        private final Boolean delayedStart;
        private final TypeInfo typeInfo;
        private final StatusInfo statusInfo;
        private final ProcessHandle process;
        private final String logonAccount;
        private final List<ServiceHandle> dependencies;

        Info(ServiceManager serviceManager, QUERY_SERVICE_CONFIG config, SERVICE_STATUS_PROCESS status, SERVICE_DESCRIPTION description,
                SERVICE_DELAYED_AUTO_START_INFO delayedAutoStartInfo) {

            this.displayName = config != null ? config.lpDisplayName : null;
            this.description = description != null ? description.lpDescription : null;
            this.executable = config != null ? config.lpBinaryPathName : null;
            this.startType = config != null ? ServiceStartType.of(config.dwStartType) : null;
            this.delayedStart = delayedAutoStartInfo != null ? delayedAutoStartInfo.fDelayedAutostart : null;
            this.typeInfo = calculateTypeInfo(config, status);
            this.statusInfo = status != null ? new StatusInfo(status) : null;
            this.process = status != null ? ProcessHandle.of(status.dwProcessId).orElse(null) : null;
            this.logonAccount = config != null ? config.lpServiceStartName : null;
            this.dependencies = config != null
                    ? config.dependencies().stream()
                            .map(d -> new ServiceHandle(serviceManager, d))
                            .collect(Collectors.toList())
                    : Collections.emptyList();
        }

        private TypeInfo calculateTypeInfo(QUERY_SERVICE_CONFIG config, SERVICE_STATUS_PROCESS status) {
            if (config != null) {
                return new TypeInfo(config.dwServiceType);
            }
            if (status != null) {
                return new TypeInfo(status.dwServiceType);
            }
            return null;
        }

        /**
         * Returns the display name.
         *
         * @return An {@link Optional} describing the display name, or {@link Optional#empty()} if the display name is not available.
         */
        public Optional<String> displayName() {
            return Optional.ofNullable(displayName);
        }

        /**
         * Returns the description.
         *
         * @return An {@link Optional} describing the description, or {@link Optional#empty()} if the description is not available.
         */
        public Optional<String> description() {
            return Optional.ofNullable(description);
        }

        /**
         * Returns the executable.
         *
         * @return An {@link Optional} describing the executable, or {@link Optional#empty()} if the executable is not available.
         */
        public Optional<String> executable() {
            return Optional.ofNullable(executable);
        }

        /**
         * Returns the start type.
         *
         * @return An {@link Optional} describing the start type, or {@link Optional#empty()} if the start type is not available.
         */
        public Optional<ServiceStartType> startType() {
            return Optional.ofNullable(startType);
        }

        /**
         * Returns whether the service is started after other auto-start services are started plus a short delay, or during system boot.
         *
         * @return An {@link Optional} with value {@code true} if the service is started after other auto-start services are started plus a short
         *         delay, an {@link Optional} with value {@code false} if the service is started during system boot,
         *         or {@link Optional#empty()} if the value is not available.
         */
        public Optional<Boolean> delayedStart() {
            return Optional.ofNullable(delayedStart);
        }

        /**
         * Returns information about the service type.
         *
         * @return An {@link Optional} describing the type information, or {@link Optional#empty()} if the type information is not available.
         */
        public Optional<TypeInfo> typeInfo() {
            return Optional.ofNullable(typeInfo);
        }

        /**
         * Returns information about the status.
         *
         * @return An {@link Optional} describing the status information, or {@link Optional#empty()} if the status information is not available.
         */
        public Optional<StatusInfo> statusInfo() {
            return Optional.ofNullable(statusInfo);
        }

        /**
         * Returns the process.
         *
         * @return An {@link Optional} describing the process, or {@link Optional#empty()} if the process is not available.
         */
        public Optional<ProcessHandle> process() {
            return Optional.ofNullable(process);
        }

        /**
         * Returns the logon account.
         *
         * @return An {@link Optional} describing the logon account, or {@link Optional#empty()} if the logon account is not available.
         */
        public Optional<String> logonAccount() {
            return Optional.ofNullable(logonAccount);
        }

        /**
         * Returns the dependencies.
         *
         * @return A list with the dependencies; empty if there are no dependencies or if the dependencies are not available.
         */
        public List<ServiceHandle> dependencies() {
            return dependencies;
        }

        @Override
        @SuppressWarnings("nls")
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append('[');
            appendField(sb, "displayName", displayName);
            appendField(sb, "description", description);
            appendField(sb, "executable", executable);
            appendField(sb, "startType", startType);
            appendField(sb, "delayedStart", delayedStart);
            appendField(sb, "typeInfo", typeInfo);
            appendField(sb, "statusInfo", statusInfo);
            appendField(sb, "process", process);
            appendField(sb, "logonAccount", logonAccount);
            appendField(sb, "dependencies", dependencies);
            sb.append(']');
            return sb.toString();
        }
    }

    /**
     * Information snapshot about the Windows service's status.
     *
     * @author Rob Spoor
     */
    public static final class StatusInfo {

        private final ServiceStatus status;
        private final int controlsAccepted;

        StatusInfo(SERVICE_STATUS_PROCESS status) {
            this.status = ServiceStatus.of(status.dwCurrentState);
            controlsAccepted = status.dwControlsAccepted;
        }

        /**
         * Returns the current status of the Windows service.
         *
         * @return The current status of the Windows service.
         */
        public ServiceStatus status() {
            return status;
        }

        /**
         * Returns whether or not the Windows service can start.
         *
         * @return {@code true} if the Windows service can start, or {@code false} otherwise.
         */
        public boolean canStart() {
            // The controlsAccepted flag doesn't mention starting, but services can only be started when they are actually stopped
            return status == ServiceStatus.STOPPED;
        }

        /**
         * Returns whether or not the Windows service can stop.
         *
         * @return {@code true} if the Windows service can stop, or {@code false} otherwise.
         */
        public boolean canStop() {
            // Stopping while transitioning (e.g. starting, stopping) is not allowed
            return isSet(controlsAccepted, Winsvc.SERVICE_ACCEPT_STOP) && !status.isTransitionStatus();
        }

        /**
         * Returns whether or not the Windows service can pause.
         *
         * @return {@code true} if the Windows service can pause, or {@code false} otherwise.
         */
        public boolean canPause() {
            // Pausing while transitioning (e.g. starting, stopping) is not allowed
            return isSet(controlsAccepted, Winsvc.SERVICE_ACCEPT_PAUSE_CONTINUE) && !status.isTransitionStatus();
        }

        /**
         * Returns whether or not the Windows service can resume.
         *
         * @return {@code true} if the Windows service can resume, or {@code false} otherwise.
         */
        public boolean canResume() {
            // Resuming while transitioning (e.g. starting, stopping) is not allowed
            return isSet(controlsAccepted, Winsvc.SERVICE_ACCEPT_PAUSE_CONTINUE) && !status.isTransitionStatus();
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
            sb.append(']');
            return sb.toString();
        }
    }

    /**
     * Information snapshot about the Windows service type.
     *
     * @author Rob Spoor
     */
    public static final class TypeInfo {

        private final int serviceType;

        TypeInfo(int serviceType) {
            this.serviceType = serviceType;
        }

        /**
         * Returns whether or not the Windows service is a driver service.
         *
         * @return {@code true} if the Windows service is a driver service, or {@code false} otherwise.
         */
        public boolean isDriver() {
            return isSet(serviceType, WinNT.SERVICE_KERNEL_DRIVER)
                    || isSet(serviceType, WinNT.SERVICE_FILE_SYSTEM_DRIVER)
                    || isSet(serviceType, WinNT.SERVICE_ADAPTER)
                    || isSet(serviceType, WinNT.SERVICE_RECOGNIZER_DRIVER);
        }

        /**
         * Returns whether or not the Windows service shares a process with one or more other services.
         *
         * @return {@code true} if the Windows service shares a process with one or more other services, or {@code false} if runs in its own process.
         */
        public boolean isShared() {
            return isSet(serviceType, WinNT.SERVICE_WIN32_SHARE_PROCESS)
                    || isSet(serviceType, 0x00000060); // SERVICE_USER_SHARE_PROCESS
        }

        /**
         * Returns whether or not the Windows service runs under the logged-on user account.
         *
         * @return {@code true} if the Windows service under the logged-on user account, or {@code false} otherwise.
         */
        public boolean isUserProcess() {
            return isSet(serviceType, 0x00000050) // WinNT.SERVICE_USER_OWN_PROCESS
                    || isSet(serviceType, 0x00000060); // SERVICE_USER_SHARE_PROCESS
        }

        /**
         * Returns whether or not the Windows service can interact with the desktop.
         *
         * @return {@code true} if the Windows service can interact with the desktop, or {@code false} otherwise.
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
}
