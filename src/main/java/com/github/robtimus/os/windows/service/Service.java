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

import java.util.Arrays;
import java.util.Collection;
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
import com.github.robtimus.os.windows.service.ServiceManager.OpenOption;
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
         * Returns information about the current status of the service.
         *
         * @return Information about the current status of the service.
         * @throws IllegalStateException If the service manager from which this service handle originated is closed.
         * @throws NoSuchServiceException If the service no longer exists in the service manager from which this service handle originated.
         * @throws ServiceException If the status information could not be retrieved for another reason.
         */
        public StatusInfo statusInfo() {
            return serviceManager.statusInfo(this);
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
        private final String loadOrderGroup;
        private final TypeInfo typeInfo;
        private final StartInfo startInfo;
        private final String logOnAccount;
        private final List<String> dependencies;

        Info(QUERY_SERVICE_CONFIG config, SERVICE_DESCRIPTION description, SERVICE_DELAYED_AUTO_START_INFO delayedAutoStartInfo) {
            this.displayName = config.lpDisplayName;
            this.description = description != null ? nullIfBlank(description.lpDescription) : null;
            this.executable = config.lpBinaryPathName;
            this.loadOrderGroup = nullIfBlank(config.lpLoadOrderGroup);
            this.typeInfo = new TypeInfo(config.dwServiceType);
            this.startInfo = new StartInfo(config, delayedAutoStartInfo);
            this.logOnAccount = nullIfBlank(config.lpServiceStartName);
            this.dependencies = config.dependencies();
        }

        private String nullIfBlank(String value) {
            return value == null || value.isBlank() ? null : value;
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
         * @return An {@link Optional} describing the service description,
         *         or {@link Optional#empty()} if the service has no description or the description is not available.
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
         * Returns the name of the load ordering group of which the service is a member.
         *
         * @return An {@link Optional} describing the load ordering group of which the service is a member,
         *         or {@link Optional#empty()} if the service is not a member of any load ordering group.
         */
        public Optional<String> loadOrderGroup() {
            return Optional.ofNullable(loadOrderGroup);
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
         * Returns the service log-on account.
         *
         * @return An {@link Optional} describing the service log-on account, or {@link Optional#empty()} if the service has no log-on account.
         *         This should usually only occur for drivers.
         */
        public Optional<String> logOnAccount() {
            return Optional.ofNullable(logOnAccount);
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
            appendField(sb, "loadOrderGroup", loadOrderGroup);
            appendField(sb, "typeInfo", typeInfo);
            appendField(sb, "startInfo", startInfo);
            appendField(sb, "logOnAccount", logOnAccount);
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

        private static final int SERVICE_USER_SERVICE = 0x00000040;
        private static final int SERVICE_USERSERVICE_INSTANCE = 0x00000080;
        // Possible other useful constants:
        // SERVICE_USER_OWN_PROCESS = SERVICE_USER_SERVICE | WinNT.SERVICE_WIN32_OWN_PROCESS; // 0x00000050
        // SERVICE_USER_SHARE_PROCESS = SERVICE_USER_SERVICE | WinNT.SERVICE_WIN32_SHARE_PROCESS; // 0x00000060
        // SERVICE_PKG_SERVICE 0x00000200

        private final Type type;
        private final boolean sharedProcess;
        private final boolean userProcess;
        private final boolean template;
        private final boolean interactiveProcess;

        private TypeInfo(int serviceType) {
            type = Type.of(serviceType);
            // SERVICE_USER_SHARED_PROCESS (0x60) includes SERVICE_WIN32_SHARE_PROCESS (0x20)
            sharedProcess = isSet(serviceType, WinNT.SERVICE_WIN32_SHARE_PROCESS);
            userProcess = isSet(serviceType, SERVICE_USER_SERVICE);
            template = isUserProcessTemplate(serviceType);
            interactiveProcess = isSet(serviceType, WinNT.SERVICE_INTERACTIVE_PROCESS);
        }

        private static boolean isUserProcessTemplate(int serviceType) {
            return isSet(serviceType, SERVICE_USER_SERVICE) && !isSet(serviceType, SERVICE_USERSERVICE_INSTANCE);
        }

        /**
         * Returns the service type.
         *
         * @return The service type.
         */
        public Type type() {
            return type;
        }

        /**
         * Returns whether or not the service shares a process with one or more other services.
         *
         * @return {@code true} if the service shares a process with one or more other services,
         *         or {@code false} if the service runs in its own process.
         * @throws IllegalStateException If the {@link #type()} is not {@link Type#PROCESS}.
         */
        public boolean sharedProcess() {
            if (type == Type.PROCESS) {
                return sharedProcess;
            }
            throw new IllegalStateException(Messages.Service.TypeInfo.noProcessType.get());
        }

        /**
         * Returns whether or not the service runs in a process under the logged-on user account.
         *
         * @return {@code true} if the service runs in a process under the logged-on user account, or {@code false} otherwise.
         * @throws IllegalStateException If the {@link #type()} is not {@link Type#PROCESS}.
         */
        public boolean userProcess() {
            if (type == Type.PROCESS) {
                return userProcess;
            }
            throw new IllegalStateException(Messages.Service.TypeInfo.noProcessType.get());
        }

        /**
         * Returns whether or not the service is a template for user processes.
         *
         * @return {@code true} if the service is a template for user process services, or {@code false} otherwise.
         * @throws IllegalStateException If the service does not run in a process under the logged-on user account.
         * @see #userProcess()
         */
        public boolean template() {
            if (userProcess()) {
                return template;
            }
            throw new IllegalStateException(Messages.Service.TypeInfo.noUserProcessType.get());
        }

        /**
         * Returns whether or not the service can interact with the desktop.
         *
         * @return {@code true} if the service can interact with the desktop, or {@code false} otherwise.
         * @throws IllegalStateException If the {@link #type()} is not {@link Type#PROCESS}.
         */
        public boolean interactiveProcess() {
            if (type == Type.PROCESS) {
                return interactiveProcess;
            }
            throw new IllegalStateException(Messages.Service.TypeInfo.noProcessType.get());
        }

        @Override
        @SuppressWarnings("nls")
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append('[');
            appendField(sb, "type", type());
            if (type == Type.PROCESS) {
                appendField(sb, "sharedProcess", sharedProcess);
                appendField(sb, "userProcess", userProcess);
                appendField(sb, "template", template && userProcess);
                appendField(sb, "interactiveProcess", interactiveProcess);
            }
            sb.append(']');
            return sb.toString();
        }
    }

    /**
     * The possible Windows service types.
     *
     * @author Rob Spoor
     */
    public enum Type {
        /** Indicates a service is a driver service. */
        KERNEL_DRIVER(true),

        /** Indicates a service is a file system driver service. */
        FILE_SYSTEM_DRIVER(true),

        /** Indicates a service is an adapter. */
        ADAPTER(false),

        /** Indicates a service is a recognizer driver. */
        RECOGNIZER_DRIVER(true),

        /** Indicates a service runs in a process. */
        PROCESS(false),
        ;

        private final boolean driver;

        Type(boolean driver) {
            this.driver = driver;
        }

        /**
         * Returns whether or not this type is a driver type.
         *
         * @return {@code true} if this type is a driver type, or {@code false} otherwise.
         */
        public boolean isDriver() {
            return driver;
        }

        static Type of(int value) {
            switch (value) {
            case WinNT.SERVICE_KERNEL_DRIVER:
                return KERNEL_DRIVER;
            case WinNT.SERVICE_FILE_SYSTEM_DRIVER:
                return FILE_SYSTEM_DRIVER;
            case WinNT.SERVICE_ADAPTER:
                return ADAPTER;
            case WinNT.SERVICE_RECOGNIZER_DRIVER:
                return RECOGNIZER_DRIVER;
            default:
                return ofProcess(value);
            }
        }

        private static Type ofProcess(int value) {
            if (isSet(value, WinNT.SERVICE_WIN32_OWN_PROCESS) || isSet(value, WinNT.SERVICE_WIN32_SHARE_PROCESS)) {
                return PROCESS;
            }
            throw new IllegalArgumentException(Messages.Service.StartType.unsupported.get(value));
        }
    }

    /**
     * Information about how a Windows service starts. This information is fixed as long as the service is not updated.
     *
     * @author Rob Spoor
     */
    public static final class StartInfo {

        private final StartType startType;
        private final boolean delayedStart;

        private StartInfo(QUERY_SERVICE_CONFIG config, SERVICE_DELAYED_AUTO_START_INFO delayedAutoStartInfo) {
            this.startType = StartType.of(config.dwStartType);
            this.delayedStart = delayedAutoStartInfo != null && delayedAutoStartInfo.fDelayedAutostart;
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
         * @return {@code true} if the service is started after other auto-start services are started plus a short delay,
         *         or {@code false} if the service is started during system boot.
         * @throws IllegalStateException If the {@link #startType()} is not {@link StartType#AUTOMATIC}.
         */
        public boolean delayedStart() {
            if (startType == StartType.AUTOMATIC) {
                return delayedStart;
            }
            throw new IllegalStateException(Messages.Service.StartInfo.noAutomaticStartType.get());
        }

        @Override
        @SuppressWarnings("nls")
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append('[');
            appendField(sb, "startType", startType);
            if (startType == StartType.AUTOMATIC) {
                appendField(sb, "delayedStart", delayedStart);
            }
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
        private final int serviceType;
        private final ProcessHandle process;

        StatusInfo(SERVICE_STATUS_PROCESS status) {
            this.status = Status.of(status.dwCurrentState);
            controlsAccepted = status.dwControlsAccepted;
            serviceType = status.dwServiceType;
            process = ProcessHandle.of(status.dwProcessId).orElse(null);
        }

        StatusInfo(SERVICE_STATUS status) {
            this.status = Status.of(status.dwCurrentState);
            controlsAccepted = status.dwControlsAccepted;
            serviceType = status.dwServiceType;
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
            return status == Status.STOPPED && !TypeInfo.isUserProcessTemplate(serviceType);
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
            return status == Status.RUNNING && isSet(controlsAccepted, Winsvc.SERVICE_ACCEPT_PAUSE_CONTINUE);
        }

        /**
         * Returns whether or not the service can resume.
         *
         * @return {@code true} if the service can resume, or {@code false} otherwise.
         */
        public boolean canResume() {
            // Resuming while transitioning (e.g. starting, stopping) is not allowed
            return status == Status.PAUSED && isSet(controlsAccepted, Winsvc.SERVICE_ACCEPT_PAUSE_CONTINUE);
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
                (manager, name, handle) -> manager.statusInfo(handle),
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
                (manager, name, handle) -> new HandleAndStatusInfo(new Handle(manager, name), manager.statusInfo(handle)),
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

        @FunctionalInterface
        interface ServicesQuery<T> {

            T queryFrom(ServiceManager serviceManager, ENUM_SERVICE_STATUS_PROCESS status);
        }

        @FunctionalInterface
        interface ServiceQuery<T> {

            T queryFrom(ServiceManager serviceManager, String serviceName, ServiceManager.Handle serviceHandle);
        }

        @FunctionalInterface
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

        /**
         * Specifies the load order group to filter on.
         *
         * @param loadOrderGroup The load order group to filter on; {@code null} to not filter on the load order group.
         * @return This filter.
         */
        public Filter loadOrderGroup(String loadOrderGroup) {
            this.pszGroupName = loadOrderGroup;
            return this;
        }
    }

    /**
     * An object that will help create new Windows services.
     * <p>
     * By default, if no modifying methods are called, services created will have the following characteristics:
     * <ul>
     * <li>The display name is equal to the service name.</li>
     * <li>There is no description.</li>
     * <li>The type is {@link Type#PROCESS}, and calling {@link TypeInfo#sharedProcess()}, {@link TypeInfo#userProcess()} and
     *     {@link TypeInfo#interactiveProcess()} would all return {@code false}.</li>
     * <li>The start type is {@link StartType#MANUAL}.</li>
     * <li>The log-on account is the <a href="https://docs.microsoft.com/en-us/windows/desktop/Services/localsystem-account">LocalSystem</a> account.
     *     </li>
     * <li>There are no dependencies.</li>
     * </ul>
     * The following code snippets will all create the same service:
     * <pre><code>
     * serviceManager.newService(serviceName, executable).create();
     *
     * serviceManager.newService(serviceName, executable)
     *         .process()
     *             .endProcess()
     *         .create();
     *
     * serviceManager.newService(serviceName, executable)
     *         .displayName(serviceName)
     *         .description(null)
     *         .loadOrderGroup(null)
     *         .process()
     *             .shared(false)
     *             .user(false)
     *             .logOnAccount()
     *                 .localSystem(false)
     *                 .endLogOnAccount()
     *             .startType()
     *                 .manual()
     *                 .endStartType()
     *             .endProcess()
     *         .dependencies()
     *         .create();
     * </code></pre>
     *
     * @author Rob Spoor
     */
    public interface Creator {

        /**
         * Sets the display name.
         *
         * @param displayName The display name to set.
         * @return This object.
         * @throws NullPointerException If the given display name is {@code null}.
         * @throws IllegalArgumentException If the given display name is blank or larger than {@code 256} characters.
         */
        Creator displayName(String displayName);

        /**
         * Sets the description.
         *
         * @param description The description name to set. If {@code null} or blank, the description is cleared.
         * @return This object.
         * @throws IllegalArgumentException If the given description is larger than {@code 2048} characters.
         */
        Creator description(String description);

        /**
         * Sets the load order group.
         *
         * @param loadOrderGroup The load order group to set. If {@code null} or blank, the load order group is cleared.
         * @return This object.
         * @throws IllegalArgumentException If the given load order group is larger than {@code 256} characters.
         */
        Creator loadOrderGroup(String loadOrderGroup);

        /**
         * Sets the dependencies.
         *
         * @param dependencies Handles to the dependencies to set; possibly none.
         * @return This object.
         * @throws NullPointerException If any {@code null} dependencies are given.
         */
        default Creator dependencies(Handle... dependencies) {
            return dependencies(Arrays.asList(dependencies));
        }

        /**
         * Sets the dependencies.
         *
         * @param dependencies A collection with handles to the dependencies to set; possibly empty.
         * @return This object.
         * @throws NullPointerException If the collection or any of its elements is {@code null}.
         */
        Creator dependencies(Collection<Handle> dependencies);

        /**
         * Sets the type to {@link Type#PROCESS}.
         *
         * @return An object that can be used to further configure the process.
         */
        Process process();

        /**
         * An object that can be used to configure a Windows service of type {@link Type#PROCESS}.
         *
         * @author Rob Spoor
         */
        interface Process {

            /**
             * Sets whether the service should share a process with one or more other services, or run in its own process.
             *
             * @param shared {@code true} if the service should share a process with one or more other services,
             *                   or {@code false} if the service should run in its own process.
             * @return This object.
             */
            Process shared(boolean shared);

            /**
             * Sets whether or not the service should run in a process under the logged-on user account.
             *
             * @param user {@code true} if the service should run in a process under the logged-on user account, or {@code false} otherwise.
             * @return This object.
             */
            Process user(boolean user);

            /**
             * Returns an object that can be used to specify the log-on account.
             *
             * @return An object that can be used to specify the log-on account.
             */
            LogOnAccount logOnAccount();

            /**
             * An object that can be used to configure the log-on account for a Windows service of type {@link Type#PROCESS}.
             *
             * @author Rob Spoor
             */
            interface LogOnAccount {

                /**
                 * Specifies that the log-on account should be the
                 * <a href="https://docs.microsoft.com/en-us/windows/desktop/Services/localsystem-account">LocalSystem</a> account.
                 * The service will not be able to interact with the desktop.
                 *
                 * @return This object.
                 */
                default LogOnAccount localSystem() {
                    return localSystem(false);
                }

                /**
                 * Specifies that the log-on account should be the
                 * <a href="https://docs.microsoft.com/en-us/windows/desktop/Services/localsystem-account">LocalSystem</a> account.
                 *
                 * @param interactive {@code true} if the service should be able to interact with the desktop, or {@code false} otherwise.
                 * @return This object.
                 */
                LogOnAccount localSystem(boolean interactive);

                /**
                 * Specifies that the log-on account should be the
                 * <a href="https://docs.microsoft.com/en-us/windows/desktop/Services/localservice-account">LocalService</a> account.
                 *
                 * @return This object.
                 */
                LogOnAccount localService();

                /**
                 * Specifies that the log-on account should be the
                 * <a href="https://docs.microsoft.com/en-us/windows/desktop/Services/networkservice-account">NetworkService</a> account.
                 *
                 * @return This object.
                 */
                LogOnAccount networkService();

                /**
                 * Specifies the log-on account that should be used.
                 *
                 * @param userName The name of the account. It should be in the form <i>DomainName\UserName</i>.
                 * @param password The password for the account; an empty string if the account has no password.
                 * @return This object.
                 * @throws NullPointerException If the user name or password is {@code null}.
                 */
                LogOnAccount account(String userName, String password);

                /**
                 * Specifies the log-on account that should be used.
                 *
                 * @param userName The name of the account, within the given domain.
                 * @param password The password for the account; an empty string if the account has no password.
                 * @param domain The domain of the account; {@code .} for the built-in domain.
                 * @return This object.
                 * @throws NullPointerException If the user name, password or domain is {@code null}.
                 */
                LogOnAccount account(String userName, String password, String domain);

                /**
                 * Ends configuring the log-on account.
                 *
                 * @return The {@link Process} instance from which this object originated.
                 */
                Process endLogOnAccount();
            }

            /**
             * Returns an object that can be used to specify the start type.
             *
             * @return An object that can be used to specify the start type.
             */
            StartType startType();

            interface StartType {

                /**
                 * Specifies that the service should start automatically during system startup.
                 *
                 * @return This object.
                 */
                default StartType automatic() {
                    return automatic(false);
                }

                /**
                 * Specifies that the service should start automatically during system startup.
                 *
                 * @param delayedStart {@code true} if the service should be started after other auto-start services are started plus a short delay,
                 *                         or {@code false} if the service should be started during system boot.
                 * @return This object.
                 */
                StartType automatic(boolean delayedStart);

                /**
                 * Specifies that the service should be started manually.
                 *
                 * @return This object.
                 */
                StartType manual();

                /**
                 * Specifies that the service should be disabled.
                 *
                 * @return This object.
                 */
                StartType disabled();

                /**
                 * Ends configuring the start type.
                 *
                 * @return The {@link Process} instance from which this object originated.
                 */
                Process endStartType();
            }

            /**
             * Ends configuring the service type.
             *
             * @return The {@link Creator} instance from which this object originated.
             */
            Creator endProcess();
        }

        /**
         * Creates a service with the current settings of this object.
         *
         * @return A handle to the created service.
         * @throws IllegalStateException If the service manager from which this object originated is closed.
         * @throws AccessDeniedException If the current user does not have sufficient rights to create services,
         *                                   or if the {@link OpenOption#CREATE} option is not given when opening the service manager from which this
         *                                   object originated.
         * @throws ServiceAlreadyExistsException If there already is a service with the service name and/or display name of this object.
         * @throws ServiceException If a service could not be created for another reason.
         */
        Handle create();
    }

    static final class CreatorImpl implements Creator, Creator.Process, Creator.Process.LogOnAccount, Creator.Process.StartType {

        private final ServiceManager serviceManager;

        final String serviceName;
        final String executable;

        String displayName;
        int serviceType = WinNT.SERVICE_WIN32_OWN_PROCESS;
        int startType = WinNT.SERVICE_DEMAND_START;
        int errorControl = WinNT.SERVICE_ERROR_NORMAL;
        String loadOrderGroup = null;
        List<String> dependencies = Collections.emptyList();
        String logOnAccount = null;
        String logOnAccountPassword = null;

        String description = null;
        boolean delayedStart = false;

        CreatorImpl(ServiceManager serviceManager, String serviceName, String executable) {
            this.serviceManager = serviceManager;
            this.serviceName = validateServiceName(serviceName);
            this.executable = validateExecutable(executable);

            displayName = serviceName;
        }

        // Creator

        @Override
        public Creator displayName(String displayName) {
            this.displayName = validateDisplayName(displayName);
            return this;
        }

        @Override
        public Creator description(String description) {
            this.description = validateDescription(description, null);
            return this;
        }

        @Override
        public Creator loadOrderGroup(String loadOrderGroup) {
            this.loadOrderGroup = validateLoadOrderGroup(loadOrderGroup, null);
            return this;
        }

        @Override
        public Creator dependencies(Collection<Handle> dependencies) {
            this.dependencies = dependencies.stream()
                    .map(Objects::requireNonNull)
                    .map(Handle::serviceName)
                    .collect(Collectors.toUnmodifiableList());
            return this;
        }

        // Creator.Process

        @Override
        public Creator.Process process() {
            serviceType = WinNT.SERVICE_WIN32_OWN_PROCESS;
            startType = WinNT.SERVICE_DEMAND_START;
            logOnAccount = null;
            logOnAccountPassword = null;
            return this;
        }

        @Override
        public Creator.Process shared(boolean shared) {
            serviceType = set(serviceType, WinNT.SERVICE_WIN32_OWN_PROCESS, !shared);
            serviceType = set(serviceType, WinNT.SERVICE_WIN32_SHARE_PROCESS, shared);
            return this;
        }

        @Override
        public Creator.Process user(boolean user) {
            serviceType = set(serviceType, TypeInfo.SERVICE_USER_SERVICE, user);
            return this;
        }

        // Creator.Process.LogonAccount

        @Override
        public Creator.Process.LogOnAccount logOnAccount() {
            // No need to reset anything
            return this;
        }

        @Override
        public Creator.Process.LogOnAccount localSystem(boolean interactive) {
            serviceType = set(serviceType, WinNT.SERVICE_INTERACTIVE_PROCESS, interactive);
            logOnAccount = null;
            logOnAccountPassword = null;
            return this;
        }

        @Override
        public Creator.Process.LogOnAccount localService() {
            serviceType = set(serviceType, WinNT.SERVICE_INTERACTIVE_PROCESS, false);
            logOnAccount = "NT AUTHORITY\\LocalService"; //$NON-NLS-1$
            logOnAccountPassword = null;
            return this;
        }

        @Override
        public Creator.Process.LogOnAccount networkService() {
            serviceType = set(serviceType, WinNT.SERVICE_INTERACTIVE_PROCESS, false);
            logOnAccount = "NT AUTHORITY\\NetworkService"; //$NON-NLS-1$
            logOnAccountPassword = null;
            return this;
        }

        @Override
        public Creator.Process.LogOnAccount account(String userName, String password) {
            Objects.requireNonNull(userName);
            Objects.requireNonNull(password);

            serviceType = set(serviceType, WinNT.SERVICE_INTERACTIVE_PROCESS, false);
            logOnAccount = userName;
            logOnAccountPassword = password;
            return this;
        }

        @Override
        public Creator.Process.LogOnAccount account(String userName, String password, String domain) {
            Objects.requireNonNull(userName);
            Objects.requireNonNull(password);
            Objects.requireNonNull(domain);

            return account(domain + "\\" + userName, password); //$NON-NLS-1$
        }

        @Override
        public Creator.Process endLogOnAccount() {
            return this;
        }

        // Creator.Process.StartType

        @Override
        public Creator.Process.StartType startType() {
            // No need to reset anything
            return this;
        }

        @Override
        public Creator.Process.StartType automatic(boolean delayedStart) {
            startType = WinNT.SERVICE_AUTO_START;
            this.delayedStart = delayedStart;
            return this;
        }

        @Override
        public Creator.Process.StartType manual() {
            startType = WinNT.SERVICE_DEMAND_START;
            delayedStart = false;
            return this;
        }

        @Override
        public Creator.Process.StartType disabled() {
            startType = WinNT.SERVICE_DISABLED;
            delayedStart = false;
            return null;
        }

        @Override
        public Creator.Process endStartType() {
            return this;
        }

        @Override
        public Creator endProcess() {
            return this;
        }

        @Override
        public Handle create() {
            return serviceManager.create(this);
        }
    }

    /**
     * An object that will help update new Windows services.
     * <p>
     * By default, if no modifying methods are called, nothing will be updated.
     *
     * @author Rob Spoor
     */
    public interface Updater {

        /**
         * Sets the display name.
         *
         * @param displayName The display name to set.
         * @return This object.
         * @throws NullPointerException If the given display name is {@code null}.
         * @throws IllegalArgumentException If the given display name is blank or larger than {@code 256} characters.
         */
        Updater displayName(String displayName);

        /**
         * Sets the description.
         *
         * @param description The description name to set. If {@code null} or blank, the description is cleared.
         * @return This object.
         * @throws IllegalArgumentException If the given description is larger than {@code 2048} characters.
         */
        Updater description(String description);

        /**
         * Sets the executable.
         *
         * @param executable The executable to set.
         * @return This object.
         * @throws NullPointerException If the given executable is {@code null}.
         * @throws IllegalArgumentException If the given executable is larger than {@code 2048} characters.
         */
        Updater executable(String executable);

        /**
         * Sets the load order group.
         *
         * @param loadOrderGroup The load order group to set. If {@code null} or blank, the load order group is cleared.
         * @return This object.
         * @throws IllegalArgumentException If the given load order group is larger than {@code 256} characters.
         */
        Updater loadOrderGroup(String loadOrderGroup);

        /**
         * Sets the dependencies.
         *
         * @param dependencies Handles to the dependencies to set; possibly none.
         * @return This object.
         * @throws NullPointerException If any {@code null} dependencies are given.
         */
        default Updater dependencies(Handle... dependencies) {
            return dependencies(Arrays.asList(dependencies));
        }

        /**
         * Sets the dependencies.
         *
         * @param dependencies A collection with handles to the dependencies to set; possibly empty.
         * @return This object.
         * @throws NullPointerException If the collection or any of its elements is {@code null}.
         */
        Updater dependencies(Collection<Handle> dependencies);

        /**
         * Returns an object that can be used to further configure services of type {@link Type#PROCESS}.
         * <p>
         * Note that this method will not cause the service type to be changed; that is not supported.
         *
         * @return An object that can be used to further configure the process.
         * @throws UnsupportedOperationException If the service to update has a type other than {@link Type#PROCESS}.
         */
        Process process();

        /**
         * An object that can be used to configure a Windows service of type {@link Type#PROCESS}.
         *
         * @author Rob Spoor
         */
        interface Process {

            /**
             * Sets whether the service should share a process with one or more other services, or run in its own process.
             *
             * @param shared {@code true} if the service should share a process with one or more other services,
             *                   or {@code false} if the service should run in its own process.
             * @return This object.
             */
            Process shared(boolean shared);

            /**
             * Sets whether or not the service should run in a process under the logged-on user account.
             *
             * @param user {@code true} if the service should run in a process under the logged-on user account, or {@code false} otherwise.
             * @return This object.
             */
            Process user(boolean user);

            /**
             * Returns an object that can be used to specify the log-on account.
             *
             * @return An object that can be used to specify the log-on account.
             */
            LogOnAccount logOnAccount();

            /**
             * An object that can be used to configure the log-on account for a Windows service of type {@link Type#PROCESS}.
             *
             * @author Rob Spoor
             */
            interface LogOnAccount {

                /**
                 * Specifies that the log-on account should be the
                 * <a href="https://docs.microsoft.com/en-us/windows/desktop/Services/localsystem-account">LocalSystem</a> account.
                 * The service will not be able to interact with the desktop.
                 *
                 * @return This object.
                 */
                default LogOnAccount localSystem() {
                    return localSystem(false);
                }

                /**
                 * Specifies that the log-on account should be the
                 * <a href="https://docs.microsoft.com/en-us/windows/desktop/Services/localsystem-account">LocalSystem</a> account.
                 *
                 * @param interactive {@code true} if the service should be able to interact with the desktop, or {@code false} otherwise.
                 * @return This object.
                 */
                LogOnAccount localSystem(boolean interactive);

                /**
                 * Specifies that the log-on account should be the
                 * <a href="https://docs.microsoft.com/en-us/windows/desktop/Services/localservice-account">LocalService</a> account.
                 *
                 * @return This object.
                 */
                LogOnAccount localService();

                /**
                 * Specifies that the log-on account should be the
                 * <a href="https://docs.microsoft.com/en-us/windows/desktop/Services/networkservice-account">NetworkService</a> account.
                 *
                 * @return This object.
                 */
                LogOnAccount networkService();

                /**
                 * Specifies the log-on account that should be used.
                 *
                 * @param userName The name of the account. It should be in the form <i>DomainName\UserName</i>.
                 * @param password The password for the account; an empty string if the account has no password.
                 * @return This object.
                 * @throws NullPointerException If the user name or password is {@code null}.
                 */
                LogOnAccount account(String userName, String password);

                /**
                 * Specifies the log-on account that should be used.
                 *
                 * @param userName The name of the account, within the given domain.
                 * @param password The password for the account; an empty string if the account has no password.
                 * @param domain The domain of the account; {@code .} for the built-in domain.
                 * @return This object.
                 * @throws NullPointerException If the user name, password or domain is {@code null}.
                 */
                LogOnAccount account(String userName, String password, String domain);

                /**
                 * Ends configuring the log-on account.
                 *
                 * @return The {@link Process} instance from which this object originated.
                 */
                Process endLogOnAccount();
            }

            /**
             * Returns an object that can be used to specify the start type.
             *
             * @return An object that can be used to specify the start type.
             */
            StartType startType();

            interface StartType {

                /**
                 * Specifies that the service should start automatically during system startup.
                 *
                 * @return This object.
                 */
                default StartType automatic() {
                    return automatic(false);
                }

                /**
                 * Specifies that the service should start automatically during system startup.
                 *
                 * @param delayedStart {@code true} if the service should be started after other auto-start services are started plus a short delay,
                 *                         or {@code false} if the service should be started during system boot.
                 * @return This object.
                 */
                StartType automatic(boolean delayedStart);

                /**
                 * Specifies that the service should be started manually.
                 *
                 * @return This object.
                 */
                StartType manual();

                /**
                 * Specifies that the service should be disabled.
                 *
                 * @return This object.
                 */
                StartType disabled();

                /**
                 * Ends configuring the start type.
                 *
                 * @return The {@link Process} instance from which this object originated.
                 */
                Process endStartType();
            }

            /**
             * Ends configuring the service type.
             *
             * @return The {@link Updater} instance from which this object originated.
             */
            Updater endProcess();
        }

        /**
         * Updates the service with the current settings of this object.
         *
         * @throws IllegalStateException If the service manager from which this object originated is closed.
         * @throws AccessDeniedException If the current user does not have sufficient rights to create services,
         *                                   or if the {@link OpenOption#CHANGE} option is not given when opening the service manager from which this
         *                                   object originated.
         * @throws ServiceAlreadyExistsException If there is a service with the display name of this object.
         * @throws ServiceException If a service could not be created for another reason.
         */
        void update();
    }

    static final class UpdaterImpl implements Updater, Updater.Process, Updater.Process.LogOnAccount, Updater.Process.StartType {

        private static final int SERVICE_NO_CHANGE = 0xFFFF_FFFF;

        // To change values to empty strings, use an empty string, since null means no change
        private static final String NO_VALUE = ""; //$NON-NLS-1$

        private final ServiceManager serviceManager;

        final String serviceName;
        private final int originalServiceType;

        String displayName = null;
        String executable = null;
        int serviceType = SERVICE_NO_CHANGE;
        int startType = SERVICE_NO_CHANGE;
        int errorControl = SERVICE_NO_CHANGE;
        String loadOrderGroup = null;
        List<String> dependencies = null;
        String logOnAccount = null;
        String logOnAccountPassword = null;

        String description = null;
        boolean delayedStart = false;

        UpdaterImpl(ServiceManager serviceManager, String serviceName, int originalServiceType) {
            this.serviceManager = serviceManager;
            this.serviceName = validateServiceName(serviceName);
            this.originalServiceType = originalServiceType;
        }

        // Updater

        @Override
        public Updater displayName(String displayName) {
            this.displayName = validateDisplayName(displayName);
            return this;
        }

        @Override
        public Updater description(String description) {
            this.description = validateDescription(description, NO_VALUE);
            return this;
        }

        @Override
        public Updater executable(String executable) {
            this.executable = validateExecutable(executable);
            return this;
        }

        @Override
        public Updater loadOrderGroup(String loadOrderGroup) {
            this.loadOrderGroup = validateLoadOrderGroup(loadOrderGroup, NO_VALUE);
            return this;
        }

        @Override
        public Updater dependencies(Collection<Handle> dependencies) {
            this.dependencies = dependencies.stream()
                    .map(Objects::requireNonNull)
                    .map(Handle::serviceName)
                    .collect(Collectors.toUnmodifiableList());
            return this;
        }

        // Updater.Process

        @Override
        public Updater.Process process() {
            // Don't update anything, just validate the type
            validateServiceType(Type.PROCESS);
            return this;
        }

        @Override
        public Updater.Process shared(boolean shared) {
            changeServiceType(WinNT.SERVICE_WIN32_OWN_PROCESS, !shared);
            changeServiceType(WinNT.SERVICE_WIN32_SHARE_PROCESS, shared);
            return this;
        }

        @Override
        public Updater.Process user(boolean user) {
            changeServiceType(TypeInfo.SERVICE_USER_SERVICE, user);
            return this;
        }

        // Updater.Process.LogonAccount

        @Override
        public Updater.Process.LogOnAccount logOnAccount() {
            // No need to update anything yet
            return this;
        }

        @Override
        public Updater.Process.LogOnAccount localSystem(boolean interactive) {
            changeServiceType(WinNT.SERVICE_INTERACTIVE_PROCESS, interactive);
            logOnAccount = "LocalSystem"; //$NON-NLS-1$
            logOnAccountPassword = NO_VALUE;
            return this;
        }

        @Override
        public Updater.Process.LogOnAccount localService() {
            changeServiceType(WinNT.SERVICE_INTERACTIVE_PROCESS, false);
            logOnAccount = "NT AUTHORITY\\LocalService"; //$NON-NLS-1$
            logOnAccountPassword = NO_VALUE;
            return this;
        }

        @Override
        public Updater.Process.LogOnAccount networkService() {
            changeServiceType(WinNT.SERVICE_INTERACTIVE_PROCESS, false);
            logOnAccount = "NT AUTHORITY\\NetworkService"; //$NON-NLS-1$
            logOnAccountPassword = NO_VALUE;
            return this;
        }

        @Override
        public Updater.Process.LogOnAccount account(String userName, String password) {
            Objects.requireNonNull(userName);
            Objects.requireNonNull(password);

            changeServiceType(WinNT.SERVICE_INTERACTIVE_PROCESS, false);
            logOnAccount = userName;
            logOnAccountPassword = password;
            return this;
        }

        @Override
        public Updater.Process.LogOnAccount account(String userName, String password, String domain) {
            Objects.requireNonNull(userName);
            Objects.requireNonNull(password);
            Objects.requireNonNull(domain);

            return account(domain + "\\" + userName, password); //$NON-NLS-1$
        }

        @Override
        public Updater.Process endLogOnAccount() {
            return this;
        }

        // Updater.Process.StartType

        @Override
        public Updater.Process.StartType startType() {
            // No need to update anything yet
            return this;
        }

        @Override
        public Updater.Process.StartType automatic(boolean delayedStart) {
            startType = WinNT.SERVICE_AUTO_START;
            this.delayedStart = delayedStart;
            return this;
        }

        @Override
        public Updater.Process.StartType manual() {
            startType = WinNT.SERVICE_DEMAND_START;
            delayedStart = false;
            return this;
        }

        @Override
        public Updater.Process.StartType disabled() {
            startType = WinNT.SERVICE_DISABLED;
            delayedStart = false;
            return null;
        }

        @Override
        public Updater.Process endStartType() {
            return this;
        }

        @Override
        public Updater endProcess() {
            return this;
        }

        @Override
        public void update() {
            serviceManager.update(this);
        }

        private void validateServiceType(Type expected) {
            if (Type.of(originalServiceType) != expected) {
                throw new UnsupportedOperationException(Messages.Service.Updater.unsupportedProcess.get(originalServiceType));
            }
        }

        private void changeServiceType(int flag, boolean set) {
            int newServiceType = set(originalServiceType, flag, set);
            serviceType = newServiceType == originalServiceType ? SERVICE_NO_CHANGE : newServiceType;
        }

        boolean needsConfigChange() {
            return displayName != null
                    || executable != null
                    || serviceType != SERVICE_NO_CHANGE
                    || startType != SERVICE_NO_CHANGE
                    || errorControl != SERVICE_NO_CHANGE
                    || loadOrderGroup != null
                    || dependencies != null
                    || logOnAccount != null
                    || logOnAccountPassword != null;
        }

        boolean needsDelayedStartChange() {
            // delayedStart is always changed with startType
            return startType != SERVICE_NO_CHANGE;
        }
    }

    private static boolean isSet(int value, int flag) {
        return (value & flag) == flag;
    }

    private static int set(int value, int flag, boolean set) {
        return set ? (value | flag) : (value & ~flag);
    }

    @SuppressWarnings("nls")
    private static void appendField(StringBuilder sb, String name, Object value) {
        if (value != null) {
            addSeparatorIfNeeded(sb);
            sb.append(name).append(": ").append(value);
        }
    }

    private static void appendField(StringBuilder sb, String name, boolean value) {
        if (value) {
            addSeparatorIfNeeded(sb);
            sb.append(name);
        }
    }

    @SuppressWarnings("nls")
    private static void appendField(StringBuilder sb, String name, Collection<?> value) {
        if (value != null && !value.isEmpty()) {
            addSeparatorIfNeeded(sb);
            sb.append(name).append(": ").append(value);
        }
    }

    @SuppressWarnings("nls")
    private static void addSeparatorIfNeeded(StringBuilder sb) {
        if (sb.length() != 1) {
            sb.append(", ");
        }
    }

    private static String validateServiceName(String serviceName) {
        return validateString(serviceName, "serviceName", 256); //$NON-NLS-1$
    }

    private static String validateExecutable(String executable) {
        // The maximum length for the executable has not been specified.
        // The actual executable length may be larger, but let's use a sensible length
        return validateString(executable, "executable", 2048); //$NON-NLS-1$
    }

    private static String validateDisplayName(String displayName) {
        return validateString(displayName, "displayName", 256); //$NON-NLS-1$
    }

    private static String validateDescription(String description, String defaultValue) {
        if (description == null || description.isBlank()) {
            return defaultValue;
        }
        // The maximum length for the description has not been specified.
        // The actual description length may be larger, but let's use a sensible length
        return validateString(description, "description", 2048); //$NON-NLS-1$
    }

    private static String validateLoadOrderGroup(String loadOrderGroup, String defaultValue) {
        if (loadOrderGroup == null || loadOrderGroup.isBlank()) {
            return defaultValue;
        }
        // The maximum length for the load order group has not been specified.
        // The actual load order group length may be larger, but let's use a sensible length
        return validateString(loadOrderGroup, "loadOrderGroup", 256); //$NON-NLS-1$
    }

    private static String validateString(String value, String name, int maxLength) {
        String result = value.trim();
        if (result.isEmpty()) {
            throw new IllegalArgumentException(Messages.Service.validation.stringBlank.get(name));
        }
        if (result.length() > maxLength) {
            throw new IllegalArgumentException(Messages.Service.validation.stringTooLong.get(name, maxLength));
        }
        return result;
    }
}
