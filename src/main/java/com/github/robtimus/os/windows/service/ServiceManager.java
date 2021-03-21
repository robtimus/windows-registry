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
import java.util.Comparator;
import java.util.Objects;
import java.util.stream.Stream;
import com.github.robtimus.os.windows.AccessDeniedException;
import com.github.robtimus.os.windows.service.Advapi32Extended.QUERY_SERVICE_CONFIG;
import com.sun.jna.Memory;
import com.sun.jna.Structure;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.Win32Exception;
import com.sun.jna.platform.win32.WinError;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.platform.win32.Winsvc;
import com.sun.jna.platform.win32.Winsvc.ENUM_SERVICE_STATUS_PROCESS;
import com.sun.jna.platform.win32.Winsvc.SC_HANDLE;
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

    // List taken from https://docs.microsoft.com/en-us/windows/win32/services/service-security-and-access-rights for Local authenticated users
    private static final int DEFAULT_SERVICE_ACCESS = WinNT.READ_CONTROL
            | Winsvc.SERVICE_ENUMERATE_DEPENDENTS
            | Winsvc.SERVICE_INTERROGATE
            | Winsvc.SERVICE_QUERY_CONFIG
            | Winsvc.SERVICE_QUERY_STATUS
            | Winsvc.SERVICE_USER_DEFINED_CONTROL;

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
     * @throws ServiceException If no service manager is available
     */
    public static ServiceManager forMachine(String machineName, ServiceOption... options) {
        Objects.requireNonNull(machineName);
        return create(machineName, options);
    }

    private static ServiceManager create(String machineName, ServiceOption... options) {
        int dwDesiredAccess = dwDesiredAccess(options);
        SC_HANDLE handle = api.OpenSCManager(machineName, null, dwDesiredAccess);
        if (handle == null) {
            throw lastError();
        }
        return new ServiceManager(handle);
    }

    private static int dwDesiredAccess(ServiceOption... options) {
        int dwDesiredAccess = DEFAULT_SERVICE_MANAGER_ACCESS;
        for (ServiceOption option : options) {
            dwDesiredAccess |= option.dwDesiredAccess;
        }
        return dwDesiredAccess;
    }

    @Override
    public void close() {
        if (handle != null) {
            if (!api.CloseServiceHandle(handle)) {
                throw lastError();
            }
            handle = null;
        }
    }

    private void checkClosed() {
        if (handle == null) {
            throw new IllegalStateException(Messages.ServiceManager.closed.get());
        }
    }

    public Stream<Service> services() {
        return services(null);
    }

    public Stream<Service> services(String groupName) {
        checkClosed();

        final int infoLevel = Winsvc.SC_ENUM_PROCESS_INFO;
        final int serviceType = WinNT.SERVICE_WIN32;
        final int serviceState = Winsvc.SERVICE_STATE_ALL;

        IntByReference pcbBytesNeeded = new IntByReference();
        IntByReference lpServicesReturned = new IntByReference();
        IntByReference lpResumeHandle = new IntByReference(0);
        api.EnumServicesStatusEx(handle, infoLevel, serviceType, serviceState, null, 0, pcbBytesNeeded, lpServicesReturned, lpResumeHandle,
                groupName);

        int lastError = kernel32.GetLastError();
        if (lastError != WinError.ERROR_MORE_DATA) {
            throw error(lastError);
        }

        Memory lpServices = new Memory(pcbBytesNeeded.getValue());
        boolean result = api.EnumServicesStatusEx(handle, infoLevel, serviceType, serviceState, lpServices, pcbBytesNeeded.getValue(), pcbBytesNeeded,
                lpServicesReturned, lpResumeHandle, groupName);
        if (!result) {
            throw error(kernel32.GetLastError());
        }

        if (lpServicesReturned.getValue() == 0) {
            return Stream.empty();
        }

        ENUM_SERVICE_STATUS_PROCESS status = Structure.newInstance(ENUM_SERVICE_STATUS_PROCESS.class, lpServices);
        status.read();
        ENUM_SERVICE_STATUS_PROCESS[] statuses = (ENUM_SERVICE_STATUS_PROCESS[]) status.toArray(lpServicesReturned.getValue());
        return Arrays.stream(statuses)
                .map(this::createService);
    }

    private Service createService(ENUM_SERVICE_STATUS_PROCESS status) {
        SC_HANDLE serviceHandle = openService(status, DEFAULT_SERVICE_ACCESS);
        try {
            QUERY_SERVICE_CONFIG lpServiceConfig = queryServiceConfig(serviceHandle);
            System.out.printf("%s, %s, %d%n", status.lpDisplayName, status.lpServiceName, lpServiceConfig.dwStartType);

            return new Service(
                    status.lpServiceName,
                    status.lpDisplayName,
                    null,
                    lpServiceConfig.lpBinaryPathName,
                    lpServiceConfig.dwStartType);
        } finally {
            closeService(serviceHandle);
        }
    }

    private QUERY_SERVICE_CONFIG queryServiceConfig(SC_HANDLE serviceHandle) {
        IntByReference pcbBytesNeeded = new IntByReference();
        if (!api.QueryServiceConfig(serviceHandle, null, 0, pcbBytesNeeded)) {
            int lastError = kernel32.GetLastError();
            if (lastError != WinError.ERROR_INSUFFICIENT_BUFFER) {
                throw error(lastError);
            }
        }
        Memory memory = new Memory(pcbBytesNeeded.getValue());
        QUERY_SERVICE_CONFIG lpServiceConfig = new QUERY_SERVICE_CONFIG(memory);
        if (!api.QueryServiceConfig(serviceHandle, lpServiceConfig, pcbBytesNeeded.getValue(), pcbBytesNeeded)) {
            System.out.printf("%d, %d%n", pcbBytesNeeded.getValue(), lpServiceConfig.size());
            throw lastError();
        }
        return lpServiceConfig;
    }

    private SC_HANDLE openService(ENUM_SERVICE_STATUS_PROCESS status, int dwDesiredAccess) {
        SC_HANDLE serviceHandle = api.OpenService(handle, status.lpServiceName, dwDesiredAccess);
        if (serviceHandle == null) {
            throw lastError();
        }
        return serviceHandle;
    }

    private void closeService(SC_HANDLE serviceHandle) {
        if (!api.CloseServiceHandle(serviceHandle)) {
            throw lastError();
        }
    }

    static Win32Exception lastError() {
        int lastError = kernel32.GetLastError();
        return error(lastError);
    }

    static Win32Exception error(int code) {
        switch (code) {
        case WinError.ERROR_ACCESS_DENIED:
            return new AccessDeniedException();
        default:
            return new ServiceException(code);
        }
    }

    public static void main(String[] args) {
        try (ServiceManager serviceManager = local()) {
            // Do nothing
            try (Stream<Service> services = serviceManager.services()) {
                services.sorted(Comparator.comparing(Service::displayName))
                        .forEach(s -> System.out.printf("%s (%s): %s, %d%n", s.displayName(), s.name(), s.startType(), s.rawStartType));
            }
        }
    }
}
