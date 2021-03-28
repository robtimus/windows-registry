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
import java.util.function.Function;
import java.util.stream.Stream;
import com.github.robtimus.os.windows.AccessDeniedException;
import com.github.robtimus.os.windows.service.Advapi32Extended.QUERY_SERVICE_CONFIG;
import com.github.robtimus.os.windows.service.Advapi32Extended.SERVICE_DELAYED_AUTO_START_INFO;
import com.github.robtimus.os.windows.service.Advapi32Extended.SERVICE_DESCRIPTION;
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

    private SC_HANDLE scmHandle;

    private ServiceManager(SC_HANDLE scmHandle) {
        this.scmHandle = scmHandle;
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
        SC_HANDLE scmHandle = api.OpenSCManager(machineName, null, dwDesiredAccess);
        if (scmHandle == null) {
            throwLastError();
        }
        return new ServiceManager(scmHandle);
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
        if (scmHandle != null) {
            if (!api.CloseServiceHandle(scmHandle)) {
                throwLastError();
            }
            scmHandle = null;
        }
    }

    private void checkClosed() {
        if (scmHandle == null) {
            throw new IllegalStateException(Messages.ServiceManager.closed.get());
        }
    }

    /**
     * Returns all non-driver Windows services for this service manager.
     *
     * @return A stream with handles to all Windows services.
     * @throws IllegalStateException If this service manager is closed.
     * @throws ServiceException If the services could not be retrieved for another reason.
     */
    public Stream<Service.Handle> services() {
        return services(Service.Query.HANDLE);
    }

    /**
     * Returns all non-driver Windows services for this service manager.
     *
     * @param <T> The type of objects to return.
     * @param query The query defining the type of objects to return.
     * @return A stream with all Windows services, as instances of the type defined by the query.
     * @throws NullPointerException If the query is {@code null}.
     * @throws IllegalStateException If this service manager is closed.
     * @throws ServiceException If the services could not be retrieved for another reason.
     */
    public <T> Stream<T> services(Service.Query<T> query) {
        return services(query, Service.Filter.DEFAULT);
    }

    /**
     * Returns all Windows services for this service manager.
     *
     * @param filter The filter that determines which services are returned.
     * @return A stream with handles to all Windows services.
     * @throws NullPointerException If the filter is {@code null}.
     * @throws IllegalStateException If this service manager is closed.
     * @throws ServiceException If the services could not be retrieved for another reason.
     */
    public Stream<Service.Handle> services(Service.Filter filter) {
        return services(Service.Query.HANDLE, filter);
    }

    /**
     * Returns all Windows services for this service manager.
     *
     * @param <T> The type of objects to return.
     * @param query The query defining the type of objects to return.
     * @param filter The filter that determines which services are returned.
     * @return A stream with all Windows services, as instances of the type defined by the query.
     * @throws NullPointerException If the query or filter is {@code null}.
     * @throws IllegalStateException If this service manager is closed.
     * @throws ServiceException If the services could not be retrieved for another reason.
     */
    public <T> Stream<T> services(Service.Query<T> query, Service.Filter filter) {
        Objects.requireNonNull(query);
        checkClosed();

        final int infoLevel = Winsvc.SC_ENUM_PROCESS_INFO;
        final int dwServiceType = filter != null ? filter.dwServiceType : WinNT.SERVICE_WIN32;
        final int dwServiceState = filter != null ? filter.dwServiceState : Winsvc.SERVICE_STATE_ALL;
        final String pszGroupName = filter != null ? filter.pszGroupName : null;

        IntByReference pcbBytesNeeded = new IntByReference();
        IntByReference lpServicesReturned = new IntByReference();
        IntByReference lpResumeHandle = new IntByReference(0);
        if (!api.EnumServicesStatusEx(scmHandle, infoLevel, dwServiceType, dwServiceState, null, 0, pcbBytesNeeded, lpServicesReturned,
                lpResumeHandle, pszGroupName)) {

            throwLastErrorUnless(WinError.ERROR_MORE_DATA);
        }

        if (pcbBytesNeeded.getValue() == 0) {
            return Stream.empty();
        }

        Memory lpServices = new Memory(pcbBytesNeeded.getValue());
        if (!api.EnumServicesStatusEx(scmHandle, infoLevel, dwServiceType, dwServiceState, lpServices, pcbBytesNeeded.getValue(), pcbBytesNeeded,
                lpServicesReturned, lpResumeHandle, pszGroupName)) {

            throwLastError();
        }

        if (lpServicesReturned.getValue() == 0) {
            return Stream.empty();
        }

        ENUM_SERVICE_STATUS_PROCESS status = Structure.newInstance(ENUM_SERVICE_STATUS_PROCESS.class, lpServices);
        status.read();
        ENUM_SERVICE_STATUS_PROCESS[] statuses = (ENUM_SERVICE_STATUS_PROCESS[]) status.toArray(lpServicesReturned.getValue());
        return Arrays.stream(statuses).map(s -> query.servicesQuery.queryFrom(this, s));
    }

    /**
     * Returns a specific Windows service if available.
     *
     * @param serviceName The name of the service to return. Note that this is case insensitive.
     * @return An {@link Optional} describing a handle to the specified Windows service, or {@link Optional#empty()} if no such service exists.
     * @throws NullPointerException If the service name is {@code null}.
     * @throws IllegalStateException If this service manager is closed.
     * @throws ServiceException If the services could not be retrieved for another reason.
     */
    public Optional<Service.Handle> service(String serviceName) {
        return service(serviceName, Service.Query.HANDLE);
    }

    /**
     * Returns a specific Windows service if available.
     *
     * @param <T> The type of objects to return.
     * @param serviceName The name of the service to return. Note that this is case insensitive.
     * @param query The query defining the type of object to return.
     * @return An {@link Optional} describing the specified Windows service as an instance of the type defined by the query,
     *         or {@link Optional#empty()} if no such service exists.
     * @throws NullPointerException If the service name or query is {@code null}.
     * @throws IllegalStateException If this service manager is closed.
     * @throws ServiceException If the services could not be retrieved for another reason.
     */
    public <T> Optional<T> service(String serviceName, Service.Query<T> query) {
        Objects.requireNonNull(serviceName);
        Objects.requireNonNull(query);
        checkClosed();

        try (Handle serviceHandle = tryOpenService(serviceName, Winsvc.SERVICE_QUERY_CONFIG | query.dwDesiredServiceAccess)) {
            if (serviceHandle == null) {
                return Optional.empty();
            }

            return Optional.of(query.serviceQuery.queryFrom(this, serviceName, serviceHandle));
        }
    }

    Service.StatusInfo status(Service.Handle service) {
        Objects.requireNonNull(service);
        checkClosed();

        try (Handle serviceHandle = openService(service, Winsvc.SERVICE_QUERY_STATUS)) {
            return status(serviceHandle);
        }
    }

    Service.StatusInfo status(Handle serviceHandle) {
        SERVICE_STATUS_PROCESS status = queryStatus(serviceHandle);

        return new Service.StatusInfo(status);
    }

    Service.Info info(Service.Handle service) {
        Objects.requireNonNull(service);
        checkClosed();

        return info(service.serviceName());
    }

    Service.Info info(String serviceName) {
        try (Handle serviceHandle = openService(serviceName, Winsvc.SERVICE_QUERY_CONFIG)) {
            return info(serviceHandle);
        }
    }

    Service.Info info(Handle serviceHandle) {
        QUERY_SERVICE_CONFIG config = queryServiceConfig(serviceHandle);
        SERVICE_DESCRIPTION description = queryOptionalServiceConfig2(serviceHandle, Winsvc.SERVICE_CONFIG_DESCRIPTION, SERVICE_DESCRIPTION::new);
        SERVICE_DELAYED_AUTO_START_INFO delayedAutoStartInfo = config.dwStartType == WinNT.SERVICE_AUTO_START
                ? queryServiceConfig2(serviceHandle, Winsvc.SERVICE_CONFIG_DELAYED_AUTO_START_INFO, SERVICE_DELAYED_AUTO_START_INFO::new)
                : null;

        return new Service.Info(config, description, delayedAutoStartInfo);
    }

    <T> Stream<T> dependencies(Service.Handle service, Service.Query<T> query) {
        Objects.requireNonNull(service);
        Objects.requireNonNull(query);
        checkClosed();

        try (Handle serviceHandle = openService(service, Winsvc.SERVICE_QUERY_CONFIG)) {
            QUERY_SERVICE_CONFIG config = queryServiceConfig(serviceHandle);

            return config.dependencies().stream()
                    .map(d -> service(d, query))
                    .filter(Optional::isPresent)
                    .map(Optional::get);
        }
    }

    <T> Stream<T> dependents(Service.Handle service, Service.Query<T> query) {
        Objects.requireNonNull(service);
        Objects.requireNonNull(query);
        checkClosed();

        try (Handle serviceHandle = openService(service, Winsvc.SERVICE_ENUMERATE_DEPENDENTS)) {
            final int dwServiceState = Winsvc.SERVICE_STATE_ALL;

            IntByReference pcbBytesNeeded = new IntByReference();
            IntByReference lpServicesReturned = new IntByReference();
            if (!api.EnumDependentServices(serviceHandle.scHandle, dwServiceState, null, 0, pcbBytesNeeded, lpServicesReturned)) {
                throwLastErrorUnless(WinError.ERROR_MORE_DATA);
            }

            if (pcbBytesNeeded.getValue() == 0) {
                return Stream.empty();
            }

            Memory lpServices = new Memory(pcbBytesNeeded.getValue());
            if (!api.EnumDependentServices(serviceHandle.scHandle, dwServiceState, lpServices, pcbBytesNeeded.getValue(), pcbBytesNeeded,
                    lpServicesReturned)) {

                throwLastError();
            }

            if (lpServicesReturned.getValue() == 0) {
                return Stream.empty();
            }

            ENUM_SERVICE_STATUS status = Structure.newInstance(ENUM_SERVICE_STATUS.class, lpServices);
            status.read();
            ENUM_SERVICE_STATUS[] statuses = (ENUM_SERVICE_STATUS[]) status.toArray(lpServicesReturned.getValue());
            return Arrays.stream(statuses).map(s -> query.dependentQuery.queryFrom(this, s));
        }
    }

    Service.AllInfo allInfo(String serviceName, Handle serviceHandle) {
        Service.Handle handle = new Service.Handle(this, serviceName);

        QUERY_SERVICE_CONFIG config = queryServiceConfig(serviceHandle);
        SERVICE_STATUS_PROCESS status = queryStatus(serviceHandle);
        SERVICE_DESCRIPTION description = queryOptionalServiceConfig2(serviceHandle, Winsvc.SERVICE_CONFIG_DESCRIPTION, SERVICE_DESCRIPTION::new);
        SERVICE_DELAYED_AUTO_START_INFO delayedAutoStartInfo = config.dwStartType == WinNT.SERVICE_AUTO_START
                ? queryServiceConfig2(serviceHandle, Winsvc.SERVICE_CONFIG_DELAYED_AUTO_START_INFO, SERVICE_DELAYED_AUTO_START_INFO::new)
                : null;

        return new Service.AllInfo(handle, status, config, description, delayedAutoStartInfo);
    }

    void start(Service.Handle service, String... args) {
        Objects.requireNonNull(service);
        checkClosed();

        try (Handle serviceHandle = openService(service, Winsvc.SERVICE_START)) {
            int dwNumServiceArgs = args != null ? args.length : 0;
            if (!api.StartService(serviceHandle.scHandle, dwNumServiceArgs, args)) {
                throwLastError();
            }
        }
    }

    Service.StatusInfo startAndWait(Service.Handle service, long maxWaitTime, String... args) {
        Objects.requireNonNull(service);
        checkWaitTime(maxWaitTime);
        checkClosed();

        try (Handle serviceHandle = openService(service, Winsvc.SERVICE_START | Winsvc.SERVICE_QUERY_STATUS)) {
            int dwNumServiceArgs = args != null ? args.length : 0;
            if (!api.StartService(serviceHandle.scHandle, dwNumServiceArgs, args)) {
                throwLastError();
            }
            return awaitStatusTransition(serviceHandle, maxWaitTime);
        }
    }

    void stop(Service.Handle service) {
        control(service, Winsvc.SERVICE_CONTROL_STOP, Winsvc.SERVICE_STOP);
    }

    Service.StatusInfo stopAndWait(Service.Handle service, long maxWaitTime) {
        return controlAndWait(service, Winsvc.SERVICE_CONTROL_STOP, Winsvc.SERVICE_STOP, maxWaitTime);
    }

    void pause(Service.Handle service) {
        control(service, Winsvc.SERVICE_CONTROL_PAUSE, Winsvc.SERVICE_PAUSE_CONTINUE);
    }

    Service.StatusInfo pauseAndWait(Service.Handle service, long maxWaitTime) {
        return controlAndWait(service, Winsvc.SERVICE_CONTROL_PAUSE, Winsvc.SERVICE_PAUSE_CONTINUE, maxWaitTime);
    }

    void resume(Service.Handle service) {
        control(service, Winsvc.SERVICE_CONTROL_CONTINUE, Winsvc.SERVICE_PAUSE_CONTINUE);
    }

    Service.StatusInfo resumeAndWait(Service.Handle service, long maxWaitTime) {
        return controlAndWait(service, Winsvc.SERVICE_CONTROL_CONTINUE, Winsvc.SERVICE_PAUSE_CONTINUE, maxWaitTime);
    }

    private void control(Service.Handle service, int dwControl, int dwDesiredAccess) {
        Objects.requireNonNull(service);
        checkClosed();

        try (Handle serviceHandle = openService(service, dwDesiredAccess)) {
            SERVICE_STATUS lpServiceStatus = new SERVICE_STATUS();
            if (!api.ControlService(serviceHandle.scHandle, dwControl, lpServiceStatus)) {
                throwLastError();
            }
        }
    }

    Service.StatusInfo controlAndWait(Service.Handle service, int dwControl, int dwDesiredAccess, long maxWaitTime) {
        Objects.requireNonNull(service);
        checkWaitTime(maxWaitTime);
        checkClosed();

        try (Handle serviceHandle = openService(service, dwDesiredAccess | Winsvc.SERVICE_QUERY_STATUS)) {
            SERVICE_STATUS lpServiceStatus = new SERVICE_STATUS();
            if (!api.ControlService(serviceHandle.scHandle, dwControl, lpServiceStatus)) {
                throwLastError();
            }
            return awaitStatusTransition(serviceHandle, maxWaitTime);
        }
    }

    Service.StatusInfo awaitStatusTransition(Service.Handle service, long maxWaitTime) {
        Objects.requireNonNull(service);
        checkWaitTime(maxWaitTime);
        checkClosed();

        try (Handle serviceHandle = openService(service, Winsvc.SERVICE_QUERY_STATUS)) {
            return awaitStatusTransition(serviceHandle, maxWaitTime);
        }
    }

    private void checkWaitTime(long maxWaitTime) {
        if (maxWaitTime < 0) {
            throw new IllegalArgumentException(maxWaitTime + " < 0"); //$NON-NLS-1$
        }
    }

    private Service.StatusInfo awaitStatusTransition(Handle serviceHandle, long maxWaitTime) {
        // Code based on https://docs.microsoft.com/en-us/windows/win32/services/starting-a-service

        final int infoLevel = SC_STATUS_TYPE.SC_STATUS_PROCESS_INFO;

        SERVICE_STATUS_PROCESS lpBuffer = new SERVICE_STATUS_PROCESS();
        IntByReference pcbBytesNeeded = new IntByReference();
        if (!api.QueryServiceStatusEx(serviceHandle.scHandle, infoLevel, lpBuffer, lpBuffer.size(), pcbBytesNeeded)) {
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

            if (!api.QueryServiceStatusEx(serviceHandle.scHandle, infoLevel, lpBuffer, lpBuffer.size(), pcbBytesNeeded)) {
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

    private SERVICE_STATUS_PROCESS queryStatus(Handle serviceHandle) {
        final int infoLevel = SC_STATUS_TYPE.SC_STATUS_PROCESS_INFO;

        SERVICE_STATUS_PROCESS lpBuffer = new SERVICE_STATUS_PROCESS();
        IntByReference pcbBytesNeeded = new IntByReference();
        if (!api.QueryServiceStatusEx(serviceHandle.scHandle, infoLevel, lpBuffer, lpBuffer.size(), pcbBytesNeeded)) {
            throwLastError();
        }
        return lpBuffer;
    }

    private QUERY_SERVICE_CONFIG queryServiceConfig(Handle serviceHandle) {
        IntByReference pcbBytesNeeded = new IntByReference();
        if (!api.QueryServiceConfig(serviceHandle.scHandle, null, 0, pcbBytesNeeded)) {
            throwLastErrorUnless(WinError.ERROR_INSUFFICIENT_BUFFER);
        }
        QUERY_SERVICE_CONFIG lpServiceConfig = new QUERY_SERVICE_CONFIG(new Memory(pcbBytesNeeded.getValue()));
        if (!api.QueryServiceConfig(serviceHandle.scHandle, lpServiceConfig, pcbBytesNeeded.getValue(), pcbBytesNeeded)) {
            throwLastError();
        }
        return lpServiceConfig;
    }

    private <T extends Structure> T queryServiceConfig2(Handle serviceHandle, int dwInfoLevel, Function<Memory, T> bufferFactory) {
        T config = queryOptionalServiceConfig2(serviceHandle, dwInfoLevel, bufferFactory);
        if (config == null) {
            throwLastError();
        }
        return config;
    }

    private <T extends Structure> T queryOptionalServiceConfig2(Handle serviceHandle, int dwInfoLevel, Function<Memory, T> bufferFactory) {
        IntByReference pcbBytesNeeded = new IntByReference();
        if (!api.QueryServiceConfig2(serviceHandle.scHandle, dwInfoLevel, null, 0, pcbBytesNeeded)
                && kernel32.GetLastError() != WinError.ERROR_INSUFFICIENT_BUFFER) {

            return null;
        }
        Memory lpBuffer = new Memory(pcbBytesNeeded.getValue());
        if (!api.QueryServiceConfig2(serviceHandle.scHandle, dwInfoLevel, lpBuffer, pcbBytesNeeded.getValue(), pcbBytesNeeded)) {
            return null;
        }
        T lpConfig = bufferFactory.apply(lpBuffer);
        lpConfig.read();
        return lpConfig;
    }

    private Handle openService(Service.Handle service, int dwDesiredAccess) {
        return openService(service.serviceName(), dwDesiredAccess);
    }

    private Handle openService(String serviceName, int dwDesiredAccess) {
        SC_HANDLE scHandle = api.OpenService(scmHandle, serviceName, dwDesiredAccess);
        if (scHandle == null) {
            throwLastError();
        }
        return new Handle(scHandle);
    }

    private Handle tryOpenService(String serviceName, int dwDesiredAccess) {
        SC_HANDLE scHandle = api.OpenService(scmHandle, serviceName, dwDesiredAccess);
        if (scHandle == null) {
            throwLastErrorUnless(WinError.ERROR_SERVICE_DOES_NOT_EXIST);
            return null;
        }
        return new Handle(scHandle);
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

    static final class Handle implements AutoCloseable {

        private final SC_HANDLE scHandle;

        private Handle(SC_HANDLE handle) {
            this.scHandle = handle;
        }

        @Override
        public void close() {
            if (!api.CloseServiceHandle(scHandle)) {
                throwLastError();
            }
        }
    }
}
