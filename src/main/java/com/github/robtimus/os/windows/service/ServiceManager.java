/*
 * ServiceManager.java
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
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;
import com.github.robtimus.os.windows.AccessDeniedException;
import com.github.robtimus.os.windows.service.Advapi32Extended.QUERY_SERVICE_CONFIG;
import com.github.robtimus.os.windows.service.Advapi32Extended.SERVICE_DELAYED_AUTO_START_INFO;
import com.github.robtimus.os.windows.service.Advapi32Extended.SERVICE_DESCRIPTION;
import com.github.robtimus.os.windows.service.Service.Status;
import com.github.robtimus.os.windows.service.Service.StatusInfo;
import com.sun.jna.Memory;
import com.sun.jna.Structure;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.Win32Exception;
import com.sun.jna.platform.win32.WinError;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.platform.win32.Winsvc;
import com.sun.jna.platform.win32.Winsvc.ENUM_SERVICE_STATUS;
import com.sun.jna.platform.win32.Winsvc.ENUM_SERVICE_STATUS_PROCESS;
import com.sun.jna.platform.win32.Winsvc.SC_HANDLE;
import com.sun.jna.platform.win32.Winsvc.SC_STATUS_TYPE;
import com.sun.jna.platform.win32.Winsvc.SERVICE_STATUS;
import com.sun.jna.platform.win32.Winsvc.SERVICE_STATUS_PROCESS;
import com.sun.jna.ptr.IntByReference;

/**
 * A representation of a Windows service manager.
 *
 * @author Rob Spoor
 */
public final class ServiceManager implements AutoCloseable {

    // List taken from https://docs.microsoft.com/en-us/windows/win32/services/service-security-and-access-rights for Local authenticated users
    private static final int DEFAULT_SERVICE_MANAGER_ACCESS = Winsvc.SC_MANAGER_CONNECT
            | Winsvc.SC_MANAGER_ENUMERATE_SERVICE
            | Winsvc.SC_MANAGER_QUERY_LOCK_STATUS
            | WinNT.STANDARD_RIGHTS_READ;

    private static Advapi32Extended api = Advapi32Extended.INSTANCE;

    private static Kernel32 kernel32 = Kernel32.INSTANCE;

    private SC_HANDLE handle;

    private ServiceManager(SC_HANDLE handle) {
        this.handle = handle;
    }

    /**
     * Returns a service manager for the current machine.
     * <p>
     * By default services can only be queried.
     * The given options determine what additional operations can be performed using the returned service manager.
     *
     * @param options The options to use.
     * @return A service manager for the current machine.
     */
    public static ServiceManager local(ServiceOption... options) {
        return create(null, options);
    }

    /**
     * Returns a service manager for a remote machine.
     * <p>
     * By default services can only be queried.
     * The given options determine what additional operations can be performed using the returned service manager.
     *
     * @param machineName The name of the remote machine.
     * @param options The options to use.
     * @return A service manager for the current machine.
     * @throws AccessDeniedException If the current user does not have access to open a service manager on the remote machine.
     * @throws ServiceException If no service manager is available
     * @throws NullPointerException If the given machine name is {@code null}.
     */
    public static ServiceManager remote(String machineName, ServiceOption... options) {
        Objects.requireNonNull(machineName);
        return create(machineName, options);
    }

    private static ServiceManager create(String machineName, ServiceOption... options) {
        int dwDesiredAccess = dwDesiredAccess(DEFAULT_SERVICE_MANAGER_ACCESS, options);
        SC_HANDLE handle = api.OpenSCManager(machineName, null, dwDesiredAccess);
        if (handle == null) {
            throwLastError();
        }
        return new ServiceManager(handle);
    }

    private static int dwDesiredAccess(int defaultAccess, ServiceOption... options) {
        int dwDesiredAccess = defaultAccess;
        for (ServiceOption option : options) {
            dwDesiredAccess |= option.dwDesiredAccess;
        }
        return dwDesiredAccess;
    }

    @Override
    public void close() {
        if (handle != null) {
            if (!api.CloseServiceHandle(handle)) {
                throwLastError();
            }
            handle = null;
        }
    }

    private void checkClosed() {
        if (handle == null) {
            throw new IllegalStateException(Messages.ServiceManager.closed.get());
        }
    }

    /**
     * Returns all Windows services for this service manager.
     *
     * @return A stream with all Windows services, as a combination of a descriptor and the current status.
     * @throws IllegalStateException If this service manager is closed.
     */
    public Stream<Service.DescriptorAndStatusInfo> services() {
        return services(Service.DescriptorAndStatusInfo::new);
    }

    /**
     * Returns all Windows services for this service manager.
     *
     * @return A stream with all Windows services, as a descriptor only.
     * @throws IllegalStateException If this service manager is closed.
     */
    public Stream<Service.Descriptor> serviceDescriptors() {
        return services(Service.Descriptor::new);
    }

    private <T> Stream<T> services(Function<ENUM_SERVICE_STATUS_PROCESS, T> mapper) {
        checkClosed();

        final int infoLevel = Winsvc.SC_ENUM_PROCESS_INFO;
        final int dwServiceType = WinNT.SERVICE_WIN32;
        final int dwServiceState = Winsvc.SERVICE_STATE_ALL;

        IntByReference pcbBytesNeeded = new IntByReference();
        IntByReference lpServicesReturned = new IntByReference();
        IntByReference lpResumeHandle = new IntByReference(0);
        if (!api.EnumServicesStatusEx(handle, infoLevel, dwServiceType, dwServiceState, null, 0, pcbBytesNeeded, lpServicesReturned,
                lpResumeHandle, null)) {

            throwLastErrorUnless(WinError.ERROR_MORE_DATA);
        }

        if (pcbBytesNeeded.getValue() == 0) {
            return Stream.empty();
        }

        Memory lpServices = new Memory(pcbBytesNeeded.getValue());
        if (!api.EnumServicesStatusEx(handle, infoLevel, dwServiceType, dwServiceState, lpServices, pcbBytesNeeded.getValue(), pcbBytesNeeded,
                lpServicesReturned, lpResumeHandle, null)) {

            throwLastError();
        }

        if (lpServicesReturned.getValue() == 0) {
            return Stream.empty();
        }

        ENUM_SERVICE_STATUS_PROCESS status = Structure.newInstance(ENUM_SERVICE_STATUS_PROCESS.class, lpServices);
        status.read();
        ENUM_SERVICE_STATUS_PROCESS[] statuses = (ENUM_SERVICE_STATUS_PROCESS[]) status.toArray(lpServicesReturned.getValue());
        return Arrays.stream(statuses).map(mapper);
    }

    /**
     * Returns a specific Windows service if available.
     *
     * @param serviceName The name of the service to return. Note that this is case insensitive.
     * @return An optional describing a handle to the specified Windows service, as a combination of a descriptor and the current status,
     *         or {@link Optional#empty()} if no such service exists.
     * @throws IllegalStateException If this service manager is closed.
     */
    public Optional<Service.DescriptorAndStatusInfo> service(String serviceName) {
        return service(serviceName, 0, (serviceHandle, config) -> service(serviceName, serviceHandle, config));
    }

    /**
     * Returns a specific Windows service if available.
     *
     * @param serviceName The name of the service to return. Note that this is case insensitive.
     * @return An optional describing a handle to the specified Windows service, as a descriptor only,
     *         or {@link Optional#empty()} if no such service exists.
     * @throws IllegalStateException If this service manager is closed.
     */
    public Optional<Service.Descriptor> serviceDescriptor(String serviceName) {
        return service(serviceName, 0, (serviceHandle, config) -> new Service.Descriptor(serviceName, config));
    }

    private <T> Optional<T> service(String serviceName, int dwDesiredAccess, BiFunction<ServiceHandle, QUERY_SERVICE_CONFIG, T> mapper) {
        checkClosed();

        try (ServiceHandle serviceHandle = tryOpenService(serviceName, Winsvc.SERVICE_QUERY_CONFIG | dwDesiredAccess)) {
            if (serviceHandle == null) {
                return Optional.empty();
            }

            QUERY_SERVICE_CONFIG config = queryServiceConfig(serviceHandle);

            return Optional.of(mapper.apply(serviceHandle, config));
        }
    }

    private Service.DescriptorAndStatusInfo service(String serviceName, ServiceHandle serviceHandle, QUERY_SERVICE_CONFIG config) {
        SERVICE_STATUS_PROCESS status = queryStatus(serviceHandle);

        return new Service.DescriptorAndStatusInfo(serviceName, config, status);
    }

    /**
     * Returns the current status of a Windows service.
     *
     * @param service A descriptor for the service.
     * @return The current status of the service.
     * @throws IllegalStateException If this service manager is closed.
     * @throws NoSuchServiceException If the service does not exist in this service manager.
     * @throws ServiceException If the status could not be retrieved for another reason.
     */
    public Service.StatusInfo status(Service.Descriptor service) {
        checkClosed();

        try (ServiceHandle serviceHandle = openService(service, Winsvc.SERVICE_QUERY_STATUS)) {
            SERVICE_STATUS_PROCESS status = queryStatus(serviceHandle);

            return new Service.StatusInfo(status);
        }
    }

    /**
     * Returns information about a Windows service.
     *
     * @param service A descriptor for the service.
     * @return An object with information about the service.
     * @throws IllegalStateException If this service manager is closed.
     * @throws NoSuchServiceException If the service does not exist in this service manager.
     * @throws ServiceException If the status could not be retrieved for another reason.
     */
    public Service.Info info(Service.Descriptor service) {
        checkClosed();

        try (ServiceHandle serviceHandle = openService(service, Winsvc.SERVICE_QUERY_CONFIG)) {
            QUERY_SERVICE_CONFIG config = queryServiceConfig(serviceHandle);
            SERVICE_DESCRIPTION description = queryServiceConfig2(serviceHandle, Winsvc.SERVICE_CONFIG_DESCRIPTION, SERVICE_DESCRIPTION::new);
            SERVICE_DELAYED_AUTO_START_INFO delayedAutoStartInfo = queryServiceConfig2(serviceHandle, Winsvc.SERVICE_CONFIG_DELAYED_AUTO_START_INFO,
                    SERVICE_DELAYED_AUTO_START_INFO::new);

            return new Service.Info(config, description, delayedAutoStartInfo);
        }
    }

    /**
     * Returns all dependencies of a Windows service.
     * <p>
     * To get only the names of the dependencies, use {@link #info(Service.Descriptor)}.
     *
     * @param service A descriptor for the service.
     * @return A stream with all dependencies of the service, as a combination of a descriptor and the current status.
     * @throws IllegalStateException If this service manager is closed.
     * @throws NoSuchServiceException If the service does not exist in this service manager.
     * @throws ServiceException If the dependencies could not be retrieved for another reason.
     */
    public Stream<Service.DescriptorAndStatusInfo> dependencies(Service.Descriptor service) {
        return dependencies(service, this::service)
                .filter(Optional::isPresent)
                .map(Optional::get);
    }

    /**
     * Returns all dependencies of a Windows service.
     * <p>
     * To get only the names of the dependencies, use {@link #info(Service.Descriptor)}.
     *
     * @param service A descriptor for the service.
     * @return A stream with all dependencies of the service, as a descriptor only.
     * @throws IllegalStateException If this service manager is closed.
     * @throws NoSuchServiceException If the service does not exist in this service manager.
     * @throws ServiceException If the dependencies could not be retrieved for another reason.
     */
    public Stream<Service.Descriptor> dependencyDescriptors(Service.Descriptor service) {
        return dependencies(service, this::serviceDescriptor)
                .filter(Optional::isPresent)
                .map(Optional::get);
    }

    private <T> Stream<T> dependencies(Service.Descriptor service, Function<String, T> mapper) {
        checkClosed();

        try (ServiceHandle serviceHandle = openService(service, Winsvc.SERVICE_QUERY_CONFIG)) {
            QUERY_SERVICE_CONFIG config = queryServiceConfig(serviceHandle);

            return config.dependencies().stream().map(mapper);
        }
    }

    /**
     * Returns all dependents of a Windows service.
     *
     * @param service A descriptor for the service.
     * @return A stream with all services that have a dependency on the service, as a combination of a descriptor and the current status.
     * @throws IllegalStateException If this service manager is closed.
     * @throws NoSuchServiceException If the service does not exist in this service manager.
     * @throws ServiceException If the dependents could not be retrieved for another reason.
     */
    public Stream<Service.DescriptorAndStatusInfo> dependents(Service.Descriptor service) {
        return dependents(service, Service.DescriptorAndStatusInfo::new);
    }

    /**
     * Returns all dependents of a Windows service.
     *
     * @param service A descriptor for the service.
     * @return A stream with all services that have a dependency on the service, as a descriptor only.
     * @throws IllegalStateException If this service manager is closed.
     * @throws NoSuchServiceException If the service does not exist in this service manager.
     * @throws ServiceException If the dependents could not be retrieved for another reason.
     */
    public Stream<Service.Descriptor> dependentDescriptors(Service.Descriptor service) {
        return dependents(service, Service.Descriptor::new);
    }

    private <T> Stream<T> dependents(Service.Descriptor service, Function<ENUM_SERVICE_STATUS, T> mapper) {
        checkClosed();

        try (ServiceHandle serviceHandle = openService(service, Winsvc.SERVICE_ENUMERATE_DEPENDENTS)) {
            final int dwServiceState = Winsvc.SERVICE_STATE_ALL;

            IntByReference pcbBytesNeeded = new IntByReference();
            IntByReference lpServicesReturned = new IntByReference();
            if (!api.EnumDependentServices(serviceHandle.handle, dwServiceState, null, 0, pcbBytesNeeded, lpServicesReturned)) {
                throwLastErrorUnless(WinError.ERROR_MORE_DATA);
            }

            if (pcbBytesNeeded.getValue() == 0) {
                return Stream.empty();
            }

            Memory lpServices = new Memory(pcbBytesNeeded.getValue());
            if (!api.EnumDependentServices(serviceHandle.handle, dwServiceState, lpServices, pcbBytesNeeded.getValue(), pcbBytesNeeded,
                    lpServicesReturned)) {

                throwLastError();
            }

            if (lpServicesReturned.getValue() == 0) {
                return Stream.empty();
            }

            ENUM_SERVICE_STATUS status = Structure.newInstance(ENUM_SERVICE_STATUS.class, lpServices);
            status.read();
            ENUM_SERVICE_STATUS[] statuses = (ENUM_SERVICE_STATUS[]) status.toArray(lpServicesReturned.getValue());
            return Arrays.stream(statuses).map(mapper);
        }
    }

    /**
     * Starts a Windows service.
     * This method does not wait until the service has started but returns immediately.
     *
     * @param service A descriptor for the service.
     * @param args Additional arguments for the service.
     * @throws IllegalStateException If this service manager is closed.
     * @throws NoSuchServiceException If the service does not exist in this service manager.
     * @throws AccessDeniedException If the current user does not have sufficient rights to start services.
     * @throws ServiceException If the service could not be started for another reason.
     */
    public void start(Service.Descriptor service, String... args) {
        checkClosed();

        try (ServiceHandle serviceHandle = openService(service, Winsvc.SERVICE_START)) {
            int dwNumServiceArgs = args != null ? args.length : 0;
            if (!api.StartService(serviceHandle.handle, dwNumServiceArgs, args)) {
                throwLastError();
            }
        }
    }

    /**
     * Starts a Windows service.
     * This method waits until the service has entered a non-transitional state, or the given maximum wait time has passed.
     * <p>
     * If all goes well, the resulting status will be {@link Status#RUNNING}.
     * However, if the Windows service fails to start a different non-transitional status may be returned (usually {@link Status#STOPPED}.
     * If the maximum wait time passes before the service finishes starting, a {@link Status#isTransitionStatus() transition status} may be returned.
     *
     * @param service A descriptor for the service.
     * @param maxWaitTime The maximum time in milliseconds to wait for the service to start.
     * @param args Additional arguments for the service.
     * @return The status of the service after this method ends.
     * @throws IllegalStateException If this service manager is closed.
     * @throws IllegalArgumentException If the maximum wait time is negative.
     * @throws NoSuchServiceException If the service does not exist in this service manager.
     * @throws AccessDeniedException If the current user does not have sufficient rights to start services.
     * @throws ServiceException If the service could not be started for another reason.
     */
    public Service.StatusInfo startAndWait(Service.Descriptor service, long maxWaitTime, String... args) {
        checkClosed();
        checkWaitTime(maxWaitTime);

        try (ServiceHandle serviceHandle = openService(service, Winsvc.SERVICE_START | Winsvc.SERVICE_QUERY_STATUS)) {
            int dwNumServiceArgs = args != null ? args.length : 0;
            if (!api.StartService(serviceHandle.handle, dwNumServiceArgs, args)) {
                throwLastError();
            }
            return awaitStatusTransition(serviceHandle, maxWaitTime);
        }
    }

    /**
     * Stops a Windows service.
     * This method does not wait until the service has stopped but returns immediately.
     *
     * @param service A descriptor for the service.
     * @throws IllegalStateException If this service manager is closed.
     * @throws NoSuchServiceException If the service does not exist in this service manager.
     * @throws AccessDeniedException If the current user does not have sufficient rights to stop services.
     * @throws ServiceException If the service could not be stopped for another reason.
     */
    public void stop(Service.Descriptor service) {
        control(service, Winsvc.SERVICE_CONTROL_STOP, Winsvc.SERVICE_STOP);
    }

    /**
     * Stops a Windows service.
     * This method waits until the service has entered a non-transitional state, or the given maximum wait time has passed.
     * <p>
     * If all goes well, the resulting status will be {@link Status#STOPPED}.
     * However, if the Windows service fails to stop a different non-transitional status may be returned.
     * If the maximum wait time passes before the service finishes stopping, a {@link Status#isTransitionStatus() transition status} may be returned.
     *
     * @param service A descriptor for the service.
     * @param maxWaitTime The maximum time in milliseconds to wait for the service to stop.
     * @return The status of the service after this method ends.
     * @throws IllegalStateException If this service manager is closed.
     * @throws IllegalArgumentException If the maximum wait time is negative.
     * @throws NoSuchServiceException If the service does not exist in this service manager.
     * @throws AccessDeniedException If the current user does not have sufficient rights to stop services.
     * @throws ServiceException If the service could not be stopped for another reason.
     */
    public Service.StatusInfo stopAndWait(Service.Descriptor service, long maxWaitTime) {
        return control(service, Winsvc.SERVICE_CONTROL_STOP, Winsvc.SERVICE_STOP, maxWaitTime);
    }

    /**
     * Pauses a Windows service.
     * This method does not wait until the service has paused but returns immediately.
     *
     * @param service A descriptor for the service.
     * @throws IllegalStateException If this service manager is closed.
     * @throws NoSuchServiceException If the service does not exist in this service manager.
     * @throws AccessDeniedException If the current user does not have sufficient rights to pause services.
     * @throws ServiceException If the service could not be paused for another reason.
     */
    public void pause(Service.Descriptor service) {
        control(service, Winsvc.SERVICE_CONTROL_PAUSE, Winsvc.SERVICE_PAUSE_CONTINUE);
    }

    /**
     * Pauses a Windows service.
     * This method waits until the service has entered a non-transitional state, or the given maximum wait time has passed.
     * <p>
     * If all goes well, the resulting status will be {@link Status#PAUSED}.
     * However, if the Windows service fails to pause a different non-transitional status may be returned.
     * If the maximum wait time passes before the service finishes pausing, a {@link Status#isTransitionStatus() transition status} may be returned.
     *
     * @param service A descriptor for the service.
     * @param maxWaitTime The maximum time in milliseconds to wait for the service to pause.
     * @return The status of the service after this method ends.
     * @throws IllegalStateException If this service manager is closed.
     * @throws IllegalArgumentException If the maximum wait time is negative.
     * @throws NoSuchServiceException If the service does not exist in this service manager.
     * @throws AccessDeniedException If the current user does not have sufficient rights to pause services.
     * @throws ServiceException If the service could not be paused for another reason.
     */
    public Service.StatusInfo pauseAndWait(Service.Descriptor service, long maxWaitTime) {
        return control(service, Winsvc.SERVICE_CONTROL_PAUSE, Winsvc.SERVICE_PAUSE_CONTINUE, maxWaitTime);
    }

    /**
     * Resumes a Windows service.
     * This method does not wait until the service has stopped but returns immediately.
     *
     * @param service A descriptor for the service.
     * @throws IllegalStateException If this service manager is closed.
     * @throws NoSuchServiceException If the service does not exist in this service manager.
     * @throws AccessDeniedException If the current user does not have sufficient rights to resume services.
     * @throws ServiceException If the service could not be resumed for another reason.
     */
    public void resume(Service.Descriptor service) {
        control(service, Winsvc.SERVICE_CONTROL_CONTINUE, Winsvc.SERVICE_PAUSE_CONTINUE);
    }

    /**
     * Resumes a Windows service.
     * This method waits until the service has entered a non-transitional state, or the given maximum wait time has passed.
     * <p>
     * If all goes well, the resulting status will be {@link Status#RUNNING}.
     * However, if the Windows service fails to resume a different non-transitional status may be returned.
     * If the maximum wait time passes before the service finishes resuming, a {@link Status#isTransitionStatus() transition status} may be returned.
     *
     * @param service A descriptor for the service.
     * @param maxWaitTime The maximum time in milliseconds to wait for the service to resume.
     * @return The status of the service after this method ends.
     * @throws IllegalStateException If this service manager is closed.
     * @throws IllegalArgumentException If the maximum wait time is negative.
     * @throws NoSuchServiceException If the service does not exist in this service manager.
     * @throws AccessDeniedException If the current user does not have sufficient rights to resume services.
     * @throws ServiceException If the service could not be resumed for another reason.
     */
    public Service.StatusInfo resumeAndWait(Service.Descriptor service, long maxWaitTime) {
        return control(service, Winsvc.SERVICE_CONTROL_CONTINUE, Winsvc.SERVICE_PAUSE_CONTINUE, maxWaitTime);
    }

    private void control(Service.Descriptor service, int dwControl, int dwDesiredAccess) {
        checkClosed();

        try (ServiceHandle serviceHandle = openService(service, dwDesiredAccess)) {
            SERVICE_STATUS lpServiceStatus = new SERVICE_STATUS();
            if (!api.ControlService(serviceHandle.handle, dwControl, lpServiceStatus)) {
                throwLastError();
            }
        }
    }

    private Service.StatusInfo control(Service.Descriptor service, int dwControl, int dwDesiredAccess, long maxWaitTime) {
        checkClosed();
        checkWaitTime(maxWaitTime);

        try (ServiceHandle serviceHandle = openService(service, dwDesiredAccess | Winsvc.SERVICE_QUERY_STATUS)) {
            SERVICE_STATUS lpServiceStatus = new SERVICE_STATUS();
            if (!api.ControlService(serviceHandle.handle, dwControl, lpServiceStatus)) {
                throwLastError();
            }
            return awaitStatusTransition(serviceHandle, maxWaitTime);
        }
    }

    /**
     * Awaits until a Windows service has finished its latest status transition.
     * If the service is in a non-transition status this method will return immediately.
     * <p>
     * If the maximum wait time passes before the service finishes its latest status transition,
     * a {@link Status#isTransitionStatus() transition status} may be returned.
     *
     * @param service A descriptor for the service.
     * @param maxWaitTime The maximum time in milliseconds to wait for the service to finish its latest status transition.
     * @return The status of the service after this method ends.
     * @throws IllegalStateException If this service manager is closed.
     * @throws IllegalArgumentException If the maximum wait time is negative.
     * @throws NoSuchServiceException If the service does not exist in this service manager.
     * @throws AccessDeniedException If the current user does not have sufficient rights to resume services.
     * @throws ServiceException If the service could not be resumed for another reason.
     */
    public Service.StatusInfo awaitStatusTransition(Service.Descriptor service, long maxWaitTime) {
        checkClosed();
        checkWaitTime(maxWaitTime);

        try (ServiceHandle serviceHandle = openService(service, Winsvc.SERVICE_QUERY_STATUS)) {
            return awaitStatusTransition(serviceHandle, maxWaitTime);
        }
    }

    private void checkWaitTime(long maxWaitTime) {
        if (maxWaitTime < 0) {
            throw new IllegalArgumentException(maxWaitTime + " < 0"); //$NON-NLS-1$
        }
    }

    private Service.StatusInfo awaitStatusTransition(ServiceHandle serviceHandle, long maxWaitTime) {
        // Code based on https://docs.microsoft.com/en-us/windows/win32/services/starting-a-service

        final int infoLevel = SC_STATUS_TYPE.SC_STATUS_PROCESS_INFO;

        SERVICE_STATUS_PROCESS lpBuffer = new SERVICE_STATUS_PROCESS();
        IntByReference pcbBytesNeeded = new IntByReference();
        if (!api.QueryServiceStatusEx(serviceHandle.handle, infoLevel, lpBuffer, lpBuffer.size(), pcbBytesNeeded)) {
            throwLastError();
        }

        long pollStartTime = System.currentTimeMillis();
        long startTime = pollStartTime;
        int previousCheckPoint = lpBuffer.dwCheckPoint;

        while (Service.Status.of(lpBuffer.dwCurrentState).isTransitionStatus()) {
            // Do not wait longer than the wait hint.
            // A good interval is one-tenth of the wait hint but not less than 1 second and not more than the max wait time
            long waitTime = Math.min(maxWaitTime, Math.max(1000, lpBuffer.dwWaitHint / 10));

            try {
                Thread.sleep(waitTime);
            } catch (@SuppressWarnings("unused") InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }

            if (!api.QueryServiceStatusEx(serviceHandle.handle, infoLevel, lpBuffer, lpBuffer.size(), pcbBytesNeeded)) {
                throwLastError();
            }

            if (lpBuffer.dwCheckPoint > previousCheckPoint) {
                // Continue to wait and check
                startTime = System.currentTimeMillis();
                previousCheckPoint = lpBuffer.dwCheckPoint;
            } else {
                long currentTime = System.currentTimeMillis();
                if (currentTime - startTime > lpBuffer.dwWaitHint || currentTime - pollStartTime >= maxWaitTime) {
                    // No progress made within the wait hint, or the total wait time has passed
                    break;
                }
            }
        }

        return new Service.StatusInfo(lpBuffer);
    }

    private SERVICE_STATUS_PROCESS queryStatus(ServiceHandle serviceHandle) {
        final int infoLevel = SC_STATUS_TYPE.SC_STATUS_PROCESS_INFO;

        SERVICE_STATUS_PROCESS lpBuffer = new SERVICE_STATUS_PROCESS();
        IntByReference pcbBytesNeeded = new IntByReference();
        if (!api.QueryServiceStatusEx(serviceHandle.handle, infoLevel, lpBuffer, lpBuffer.size(), pcbBytesNeeded)) {
            throwLastError();
        }
        return lpBuffer;
    }

    private QUERY_SERVICE_CONFIG queryServiceConfig(ServiceHandle serviceHandle) {
        IntByReference pcbBytesNeeded = new IntByReference();
        if (!api.QueryServiceConfig(serviceHandle.handle, null, 0, pcbBytesNeeded)) {
            throwLastErrorUnless(WinError.ERROR_INSUFFICIENT_BUFFER);
        }
        QUERY_SERVICE_CONFIG lpServiceConfig = new QUERY_SERVICE_CONFIG(new Memory(pcbBytesNeeded.getValue()));
        if (!api.QueryServiceConfig(serviceHandle.handle, lpServiceConfig, pcbBytesNeeded.getValue(), pcbBytesNeeded)) {
            throwLastError();
        }
        return lpServiceConfig;
    }

    private <T extends Structure> T queryServiceConfig2(ServiceHandle serviceHandle, int dwInfoLevel, Function<Memory, T> bufferFactory) {
        IntByReference pcbBytesNeeded = new IntByReference();
        if (!api.QueryServiceConfig2(serviceHandle.handle, dwInfoLevel, null, 0, pcbBytesNeeded)) {
            throwLastErrorUnless(WinError.ERROR_INSUFFICIENT_BUFFER);
        }
        Memory lpBuffer = new Memory(pcbBytesNeeded.getValue());
        if (!api.QueryServiceConfig2(serviceHandle.handle, dwInfoLevel, lpBuffer, pcbBytesNeeded.getValue(), pcbBytesNeeded)) {
            throwLastError();
        }
        T lpConfig = bufferFactory.apply(lpBuffer);
        lpConfig.read();
        return lpConfig;
    }

    private ServiceHandle openService(Service.Descriptor service, int dwDesiredAccess) {
        return openService(service.serviceName(), dwDesiredAccess);
    }

    private ServiceHandle openService(String serviceName, int dwDesiredAccess) {
        SC_HANDLE scHandle = api.OpenService(handle, serviceName, dwDesiredAccess);
        if (scHandle == null) {
            throwLastError();
        }
        return new ServiceHandle(scHandle);
    }

    private ServiceHandle tryOpenService(String serviceName, int dwDesiredAccess) {
        SC_HANDLE scHandle = api.OpenService(handle, serviceName, dwDesiredAccess);
        if (scHandle == null) {
            throwLastErrorUnless(WinError.ERROR_SERVICE_DOES_NOT_EXIST);
            return null;
        }
        return new ServiceHandle(scHandle);
    }

    private static void throwLastError() {
        int lastError = kernel32.GetLastError();
        throw error(lastError);
    }

    private static void throwLastErrorUnless(int allowed) {
        int lastError = kernel32.GetLastError();
        if (lastError != allowed) {
            throw error(lastError);
        }
    }

    private static Win32Exception error(int code) {
        System.out.printf("Code: %d%n", code);
        switch (code) {
        case WinError.ERROR_ACCESS_DENIED:
            return new AccessDeniedException();
        case WinError.ERROR_SERVICE_NOT_FOUND:
            return new NoSuchServiceException();
        default:
            return new ServiceException(code);
        }
    }

    private static final class ServiceHandle implements AutoCloseable {

        private final SC_HANDLE handle;

        private ServiceHandle(SC_HANDLE handle) {
            this.handle = handle;
        }

        @Override
        public void close() {
            if (!api.CloseServiceHandle(handle)) {
                throwLastError();
            }
        }
    }

    @SuppressWarnings("nls")
    public static void main(String[] args) {
        try (ServiceManager serviceManager = local()) {
            testService(serviceManager, "Apache2.4");
            testService(serviceManager, "MariaDB");
            testService(serviceManager, "AxInstSV");
            testService(serviceManager, "RpcSs");
            testService(serviceManager, "UserManager");
            testService(serviceManager, "DoSvc");

            Service.Descriptor service = serviceManager.serviceDescriptor("Apache2.4").orElseThrow();
//            serviceManager.startAndWait(service, 5000);
//            System.out.printf("%s%n", serviceManager.status(service));
//            serviceManager.stopAndWait(service, 5000);
//            System.out.printf("%s%n", serviceManager.status(service));
//            serviceManager.start(service);
//            System.out.printf("%s%n", serviceManager.awaitStatusTransition(service, 5000));
//            serviceManager.stop(service);
//            System.out.printf("%s%n", serviceManager.awaitStatusTransition(service, 5000));

            serviceManager.services().limit(10).forEach(s -> System.out.printf("%s, %s, %s%n", s, s.descriptor(), s.statusInfo()));
        }
    }

    @SuppressWarnings("nls")
    private static void testService(ServiceManager serviceManager, String name) {
        Service.Descriptor service = serviceManager.serviceDescriptor(name).orElseThrow();
        Service.Info info = serviceManager.info(service);
        StatusInfo status = serviceManager.status(service);
        System.out.printf("%s (%s)%n", service, service.serviceName());
        System.out.printf("%s%n", info);
        System.out.printf("%s%n", status);
        System.out.printf("Dependencies:%n");
        serviceManager.dependencyDescriptors(service).forEach(d -> System.out.printf("- %s, %s%n", d.serviceName(), d.displayName()));
        System.out.printf("Dependent:%n");
        serviceManager.dependentDescriptors(service).limit(10).forEach(d -> System.out.printf("- %s, %s%n", d.serviceName(), d.displayName()));
        System.out.printf("Process:%n");
        status.process().map(ProcessHandle::info).ifPresent(System.out::println);
        System.out.printf("---%n");
    }
}
