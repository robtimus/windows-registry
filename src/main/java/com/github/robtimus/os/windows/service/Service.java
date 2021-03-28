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
import java.util.stream.Stream;
import com.github.robtimus.os.windows.AccessDeniedException;
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
     * Identifies and provides control of Windows services.
     *
     * @author Rob Spoor
     */
    public static final class Handle {

        private final ServiceManager serviceManager;
        private final String serviceName;

        Handle(ServiceManager serviceManager, String serviceName) {
            this.serviceManager = serviceManager;
            this.serviceName = serviceName;
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
         * Returns the current status of the service.
         *
         * @return The current status of the service.
         * @throws IllegalStateException If the service manager from which this service handle originated is closed.
         * @throws NoSuchServiceException If the service no longer exists in the service manager from which this service handle originated.
         * @throws ServiceException If the status could not be retrieved for another reason.
         */
        public StatusInfo status() {
            return serviceManager.status(this);
        }

        /**
         * Returns information about the service.
         *
         * @return An object with information about the service.
         * @throws IllegalStateException If the service manager from which this service handle originated is closed.
         * @throws NoSuchServiceException If the service no longer exists in the service manager from which this service handle originated.
         * @throws ServiceException If the status could not be retrieved for another reason.
         */
        public Info info() {
            return serviceManager.info(this);
        }

        /**
         * Returns all dependencies of the service.
         * <p>
         * To get only the names of the dependencies, use {@link #info()}.
         *
         * @return A stream with handles to all dependencies of the service.
         * @throws IllegalStateException If the service manager from which this service handle originated is closed.
         * @throws NoSuchServiceException If the service no longer exists in the service manager from which this service handle originated.
         * @throws ServiceException If the dependencies could not be retrieved for another reason.
         */
        public Stream<Handle> dependencies() {
            return dependencies(Query.HANDLE);
        }

        /**
         * Returns all dependencies of the service.
         * <p>
         * To get only the names of the dependencies, use {@link #info()}.
         *
         * @param <T> The type of objects to return.
         * @param query The query defining the type of objects to return.
         * @return A stream with all dependencies of the service, as instances of the type defined by the query.
         * @throws NullPointerException If the query is {@code null}.
         * @throws IllegalStateException If the service manager from which this service handle originated is closed.
         * @throws NoSuchServiceException If the service no longer exists in the service manager from which this service handle originated.
         * @throws ServiceException If the dependencies could not be retrieved for another reason.
         */
        public <T> Stream<T> dependencies(Query<T> query) {
            return serviceManager.dependencies(this, query);
        }

        /**
         * Returns all dependents of the service.
         *
         * @return A stream with handles to all services that have a dependency on the service.
         * @throws IllegalStateException If the service manager from which this service handle originated is closed.
         * @throws NoSuchServiceException If the service no longer exists in the service manager from which this service handle originated.
         * @throws ServiceException If the dependents could not be retrieved for another reason.
         */
        public Stream<Handle> dependents() {
            return dependents(Query.HANDLE);
        }

        /**
         * Returns all dependents of the service.
         *
         * @param <T> The type of objects to return.
         * @param query The query defining the type of objects to return.
         * @return A stream with all services that have a dependency on the service, as instances of the type defined by the query.
         * @throws NullPointerException If the query is {@code null}.
         * @throws IllegalStateException If the service manager from which this service handle originated is closed.
         * @throws NoSuchServiceException If the service no longer exists in the service manager from which this service handle originated.
         * @throws ServiceException If the dependencies could not be retrieved for another reason.
         */
        public <T> Stream<T> dependents(Query<T> query) {
            return serviceManager.dependents(this, query);
        }

        /**
         * Starts the service.
         * This method does not wait until the service has started but returns immediately.
         *
         * @param args Additional arguments for the service.
         * @throws IllegalStateException If the service manager from which this service handle originated is closed.
         * @throws NoSuchServiceException If the service no longer exists in the service manager from which this service handle originated.
         * @throws AccessDeniedException If the current user does not have sufficient rights to start services.
         * @throws ServiceException If the service could not be started for another reason.
         */
        public void start(String... args) {
            serviceManager.start(this, args);
        }

        /**
         * Starts a Windows service.
         * This method waits until the service has entered a non-transitional state, or the given maximum wait time has passed.
         * <p>
         * If all goes well, the resulting status will be {@link Status#RUNNING}.
         * However, if the Windows service fails to start a different non-transitional status may be returned (usually {@link Status#STOPPED}.
         * If the maximum wait time passes before the service finishes starting, a {@link Status#isTransitionStatus() transition status} may be
         * returned.
         *
         * @param maxWaitTime The maximum time in milliseconds to wait for the service to start.
         * @param args Additional arguments for the service.
         * @return The status of the service after this method ends.
         * @throws NullPointerException If the service descriptor is {@code null}.
         * @throws IllegalArgumentException If the maximum wait time is negative.
         * @throws IllegalStateException If the service manager from which this service handle originated is closed.
         * @throws NoSuchServiceException If the service does not exist in this service manager.
         * @throws AccessDeniedException If the current user does not have sufficient rights to start services.
         * @throws ServiceException If the service could not be started for another reason.
         */
        public StatusInfo startAndWait(long maxWaitTime, String... args) {
            return serviceManager.startAndWait(this, maxWaitTime, args);
        }

        /**
         * Stops the service.
         * This method does not wait until the service has stopped but returns immediately.
         *
         * @throws IllegalStateException If the service manager from which this service handle originated is closed.
         * @throws NoSuchServiceException If the service no longer exists in the service manager from which this service handle originated.
         * @throws AccessDeniedException If the current user does not have sufficient rights to stop services.
         * @throws ServiceException If the service could not be stopped for another reason.
         */
        public void stop() {
            serviceManager.stop(this);
        }

        /**
         * Stops the service.
         * This method waits until the service has entered a non-transitional state, or the given maximum wait time has passed.
         * <p>
         * If all goes well, the resulting status will be {@link Status#STOPPED}.
         * However, if the Windows service fails to stop a different non-transitional status may be returned.
         * If the maximum wait time passes before the service finishes stopping, a {@link Status#isTransitionStatus() transition status} may be
         * returned.
         *
         * @param maxWaitTime The maximum time in milliseconds to wait for the service to stop.
         * @return The status of the service after this method ends.
         * @throws IllegalArgumentException If the maximum wait time is negative.
         * @throws IllegalStateException If the service manager from which this service handle originated is closed.
         * @throws NoSuchServiceException If the service no longer exists in the service manager from which this service handle originated.
         * @throws AccessDeniedException If the current user does not have sufficient rights to stop services.
         * @throws ServiceException If the service could not be stopped for another reason.
         */
        public StatusInfo stopAndWait(long maxWaitTime) {
            return serviceManager.stopAndWait(this, maxWaitTime);
        }

        /**
         * Pauses the service.
         * This method does not wait until the service has paused but returns immediately.
         *
         * @throws IllegalStateException If the service manager from which this service handle originated is closed.
         * @throws NoSuchServiceException If the service no longer exists in the service manager from which this service handle originated.
         * @throws AccessDeniedException If the current user does not have sufficient rights to pause services.
         * @throws ServiceException If the service could not be paused for another reason.
         */
        public void pause() {
            serviceManager.pause(this);
        }

        /**
         * Pauses the service.
         * This method waits until the service has entered a non-transitional state, or the given maximum wait time has passed.
         * <p>
         * If all goes well, the resulting status will be {@link Status#PAUSED}.
         * However, if the Windows service fails to pause a different non-transitional status may be returned.
         * If the maximum wait time passes before the service finishes pausing, a {@link Status#isTransitionStatus() transition status} may be
         * returned.
         *
         * @param maxWaitTime The maximum time in milliseconds to wait for the service to pause.
         * @return The status of the service after this method ends.
         * @throws IllegalArgumentException If the maximum wait time is negative.
         * @throws IllegalStateException If the service manager from which this service handle originated is closed.
         * @throws NoSuchServiceException If the service no longer exists in the service manager from which this service handle originated.
         * @throws AccessDeniedException If the current user does not have sufficient rights to pause services.
         * @throws ServiceException If the service could not be paused for another reason.
         */
        public StatusInfo pauseAndWait(long maxWaitTime) {
            return serviceManager.pauseAndWait(this, maxWaitTime);
        }

        /**
         * Resumes the service.
         * This method does not wait until the service has stopped but returns immediately.
         *
         * @throws IllegalStateException If the service manager from which this service handle originated is closed.
         * @throws NoSuchServiceException If the service no longer exists in the service manager from which this service handle originated.
         * @throws AccessDeniedException If the current user does not have sufficient rights to resume services.
         * @throws ServiceException If the service could not be resumed for another reason.
         */
        public void resume() {
            serviceManager.resume(this);
        }

        /**
         * Resumes the service.
         * This method waits until the service has entered a non-transitional state, or the given maximum wait time has passed.
         * <p>
         * If all goes well, the resulting status will be {@link Status#RUNNING}.
         * However, if the Windows service fails to resume a different non-transitional status may be returned.
         * If the maximum wait time passes before the service finishes resuming, a {@link Status#isTransitionStatus() transition status} may be
         * returned.
         *
         * @param maxWaitTime The maximum time in milliseconds to wait for the service to resume.
         * @return The status of the service after this method ends.
         * @throws IllegalArgumentException If the maximum wait time is negative.
         * @throws IllegalStateException If the service manager from which this service handle originated is closed.
         * @throws NoSuchServiceException If the service no longer exists in the service manager from which this service handle originated.
         * @throws AccessDeniedException If the current user does not have sufficient rights to resume services.
         * @throws ServiceException If the service could not be resumed for another reason.
         */
        public StatusInfo resumeAndWait(long maxWaitTime) {
            return serviceManager.resumeAndWait(this, maxWaitTime);
        }

        /**
         * Awaits until the service has finished its latest status transition.
         * If the service is in a non-transition status this method will return immediately.
         * <p>
         * If the maximum wait time passes before the service finishes its latest status transition,
         * a {@link Status#isTransitionStatus() transition status} may be returned.
         *
         * @param maxWaitTime The maximum time in milliseconds to wait for the service to finish its latest status transition.
         * @return The status of the service after this method ends.
         * @throws IllegalArgumentException If the maximum wait time is negative.
         * @throws IllegalStateException If the service manager from which this service handle originated is closed.
         * @throws NoSuchServiceException If the service no longer exists in the service manager from which this service handle originated.
         * @throws AccessDeniedException If the current user does not have sufficient rights to resume services.
         * @throws ServiceException If the service could not be resumed for another reason.
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
            Handle other = (Handle) o;
            return serviceManager.equals(other.serviceManager) && serviceName.equals(other.serviceName);
        }

        @Override
        public int hashCode() {
            return serviceManager.hashCode() ^ serviceName.hashCode();
        }

        @Override
        public String toString() {
            return serviceName;
        }
    }

    /**
     * Information about a Windows service. This information is fixed as long as the service is not updated.
     *
     * @author Rob Spoor
     */
    public static final class Info {

        private final String displayName;
        private final String description;
        private final String executable;
        private final TypeInfo typeInfo;
        private final StartInfo startInfo;
        private final String logonAccount;
        private final List<String> dependencies;

        Info(QUERY_SERVICE_CONFIG config, SERVICE_DESCRIPTION description, SERVICE_DELAYED_AUTO_START_INFO delayedAutoStartInfo) {
            this.displayName = config.lpDisplayName;
            this.description = description.lpDescription;
            this.executable = config.lpBinaryPathName;
            this.typeInfo = new TypeInfo(config.dwServiceType);
            this.startInfo = new StartInfo(config, delayedAutoStartInfo);
            this.logonAccount = config.lpServiceStartName;
            this.dependencies = config.dependencies();
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
         * Returns information about the service type.
         *
         * @return An object containing information about the service type.
         */
        public TypeInfo typeInfo() {
            return typeInfo;
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
            appendField(sb, "displayName", displayName);
            appendField(sb, "description", description);
            appendField(sb, "executable", executable);
            appendField(sb, "typeInfo", typeInfo);
            appendField(sb, "startInfo", startInfo);
            appendField(sb, "logonAccount", logonAccount);
            appendField(sb, "dependencies", dependencies);
            sb.append(']');
            return sb.toString();
        }
    }

    /**
     * Information about a Windows service's type. This information is fixed as long as the service is not updated.
     *
     * @author Rob Spoor
     */
    public static final class TypeInfo {

        private final int serviceType;

        private TypeInfo(int serviceType) {
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
     * Information about how a Windows service starts. This information is fixed as long as the service is not updated.
     *
     * @author Rob Spoor
     */
    public static final class StartInfo {

        private final StartType startType;
        private final Boolean delayedStart;

        private StartInfo(QUERY_SERVICE_CONFIG config, SERVICE_DELAYED_AUTO_START_INFO delayedAutoStartInfo) {
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
     * A class encapsulating both a {@link Handle} and an {@link Info} for a Windows service.
     *
     * @author Rob Spoor
     */
    public static final class HandleAndInfo {

        private final Handle handle;
        private final Info info;

        private HandleAndInfo(Handle handle, Info info) {
            this.handle = handle;
            this.info = info;
        }

        /**
         * Returns the service handle.
         *
         * @return The service handle.
         */
        public Handle handle() {
            return handle;
        }

        /**
         * The service information.
         *
         * @return The service information.
         */
        public Info info() {
            return info;
        }

        @Override
        @SuppressWarnings("nls")
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append('[');
            appendField(sb, "handle", handle);
            appendField(sb, "info", info);
            sb.append(']');
            return sb.toString();
        }
    }

    /**
     * A class encapsulating both a {@link Handle} and a {@link StatusInfo} for a Windows service.
     *
     * @author Rob Spoor
     */
    public static final class HandleAndStatusInfo {

        private final Handle handle;
        private final StatusInfo statusInfo;

        private HandleAndStatusInfo(Handle handle, StatusInfo statusInfo) {
            this.handle = handle;
            this.statusInfo = statusInfo;
        }

        /**
         * Returns the service handle.
         *
         * @return The service handle.
         */
        public Handle handle() {
            return handle;
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
            appendField(sb, "handle", handle);
            appendField(sb, "statusInfo", statusInfo);
            sb.append(']');
            return sb.toString();
        }
    }

    /**
     * A class encapsulating all information for a Windows service.
     *
     * @author Rob Spoor
     */
    public static final class AllInfo {

        private final Handle handle;
        private final Info info;
        private final StatusInfo statusInfo;

        AllInfo(ServiceManager serviceManager, ENUM_SERVICE_STATUS_PROCESS status, Info info) {
            this.handle = new Handle(serviceManager, status.lpServiceName);
            this.info = info;
            this.statusInfo = new StatusInfo(status.ServiceStatusProcess);
        }

        AllInfo(Handle handle, SERVICE_STATUS_PROCESS status, QUERY_SERVICE_CONFIG config, SERVICE_DESCRIPTION description,
                SERVICE_DELAYED_AUTO_START_INFO delayedAutoStartInfo) {

            this.handle = handle;
            this.info = new Info(config, description, delayedAutoStartInfo);
            this.statusInfo = new StatusInfo(status);
        }

        AllInfo(ServiceManager serviceManager, ENUM_SERVICE_STATUS status, Info info) {
            this.handle = new Handle(serviceManager, status.lpServiceName);
            this.info = info;
            this.statusInfo = new StatusInfo(status.ServiceStatus);
        }

        /**
         * Returns the service handle.
         *
         * @return The service handle.
         */
        public Handle handle() {
            return handle;
        }

        /**
         * Returns the service information.
         *
         * @return The service information.
         */
        public Info info() {
            return info();
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
            appendField(sb, "handle", handle);
            appendField(sb, "info", info);
            appendField(sb, "statusInfo", statusInfo);
            sb.append(']');
            return sb.toString();
        }
    }

    /**
     * A strategy for querying Windows services. This can be used to determine what to return from the following methods:
     * <ul>
     * <li>{@link ServiceManager#services(Query)}</li>
     * <li>{@link ServiceManager#service(String, Query)}</li>
     * <li>{@link Handle#dependencies(Query)}</li>
     * <li>{@link Handle#dependents(Query)}</li>
     * </ul>
     *
     * @author Rob Spoor
     * @param <T> The type returned from the query.
     */
    public static final class Query<T> {

        /** A query for {@code Handle} instances. */
        public static final Query<Handle> HANDLE = new Query<>(
                (manager, status) -> new Handle(manager, status.lpServiceName),
                (manager, name, handle) -> new Handle(manager, name),
                (manager, status) -> new Handle(manager, status.lpServiceName));

        /** A query for {@code Info} instances. */
        public static final Query<Info> INFO = new Query<>(
                (manager, status) -> manager.info(status.lpServiceName),
                (manager, name, handle) -> manager.info(handle),
                (manager, status) -> manager.info(status.lpServiceName));

        /** A query for {@code StatusInfo} instances. */
        public static final Query<StatusInfo> STATUS_INFO = new Query<>(
                (manager, status) -> new StatusInfo(status.ServiceStatusProcess),
                (manager, name, handle) -> manager.status(handle),
                (manager, status) -> new StatusInfo(status.ServiceStatus),
                Winsvc.SERVICE_QUERY_STATUS);

        /** A query for {@code HandleAndInfo} instances. */
        public static final Query<HandleAndInfo> HANDLE_AND_INFO = new Query<>(
                (manager, status) -> new HandleAndInfo(new Handle(manager, status.lpServiceName), manager.info(status.lpServiceName)),
                (manager, name, handle) -> new HandleAndInfo(new Handle(manager, name), manager.info(handle)),
                (manager, status) -> new HandleAndInfo(new Handle(manager, status.lpServiceName), manager.info(status.lpServiceName)),
                Winsvc.SERVICE_QUERY_STATUS);

        /** A query for {@code HandleAndStatusInfo} instances. */
        public static final Query<HandleAndStatusInfo> HANDLE_AND_STATUS_INFO = new Query<>(
                (manager, status) -> new HandleAndStatusInfo(new Handle(manager, status.lpServiceName), new StatusInfo(status.ServiceStatusProcess)),
                (manager, name, handle) -> new HandleAndStatusInfo(new Handle(manager, name), manager.status(handle)),
                (manager, status) -> new HandleAndStatusInfo(new Handle(manager, status.lpServiceName), new StatusInfo(status.ServiceStatus)),
                Winsvc.SERVICE_QUERY_STATUS);

        /** A query for {@code AllInfo} instances. */
        public static final Query<AllInfo> ALL_INFO = new Query<>(
                (manager, status) -> new AllInfo(manager, status, manager.info(status.lpServiceName)),
                (manager, name, handle) -> manager.allInfo(name, handle),
                (manager, status) -> new AllInfo(manager, status, manager.info(status.lpServiceName)),
                Winsvc.SERVICE_QUERY_STATUS);

        final ServicesQuery<T> servicesQuery;
        final ServiceQuery<T> serviceQuery;
        final DependentQuery<T> dependentQuery;

        final int dwDesiredServiceAccess;

        private Query(ServicesQuery<T> servicesQuery, ServiceQuery<T> serviceQuery, DependentQuery<T> dependentQuery) {
            this(servicesQuery, serviceQuery, dependentQuery, 0);
        }

        private Query(ServicesQuery<T> servicesQuery, ServiceQuery<T> serviceQuery, DependentQuery<T> dependentQuery, int dwDesiredServiceAccess) {
            this.servicesQuery = servicesQuery;
            this.serviceQuery = serviceQuery;
            this.dependentQuery = dependentQuery;

            this.dwDesiredServiceAccess = dwDesiredServiceAccess;
        }

        interface ServicesQuery<T> {

            T queryFrom(ServiceManager serviceManager, ENUM_SERVICE_STATUS_PROCESS status);
        }

        interface ServiceQuery<T> {

            T queryFrom(ServiceManager serviceManager, String serviceName, ServiceManager.Handle serviceHandle);
        }

        interface DependentQuery<T> {

            T queryFrom(ServiceManager serviceManager, ENUM_SERVICE_STATUS status);
        }
    }

    /**
     * A strategy for filtering Windows services. This can be used to determine what to return from the following methods:
     * <ul>
     * <li>{@link ServiceManager#services(Filter)}</li>
     * <li>{@link ServiceManager#services(Query, Filter)}</li>
     * </ul>
     *
     * @author Rob Spoor
     */
    public static final class Filter {

        static final Filter DEFAULT = new Filter();

        int dwServiceType;
        int dwServiceState;
        String pszGroupName;

        /**
         * Creates a new filter. Using this filter will by default return both active and inactive non-driver services.
         */
        public Filter() {
            dwServiceType = WinNT.SERVICE_WIN32;
            dwServiceState = Winsvc.SERVICE_STATE_ALL;
            pszGroupName = null;
        }

        /**
         * Specifies that using this filter will return only drivers.
         *
         * @return This filter.
         */
        public Filter driversOnly() {
            dwServiceType = WinNT.SERVICE_DRIVER;
            return this;
        }

        /**
         * Specifies that using this filter will return all services, both drivers and non-drivers.
         *
         * @return This filter.
         */
        public Filter allTypes() {
            dwServiceType = WinNT.SERVICE_TYPE_ALL;
            return this;
        }

        /**
         * Specifies that using this filter will return only active services.
         *
         * @return This filter.
         */
        public Filter activeOnly() {
            dwServiceState = Winsvc.SERVICE_ACTIVE;
            return this;
        }

        /**
         * Specifies that using this filter will return only inactive services.
         *
         * @return This filter.
         */
        public Filter inactiveOnly() {
            dwServiceState = Winsvc.SERVICE_INACTIVE;
            return this;
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
