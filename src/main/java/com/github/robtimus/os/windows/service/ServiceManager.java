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

import java.io.IOException;
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
     * @return A stream with handles to all Windows services.
     * @throws IllegalStateException If this service manager is closed.
     */
    public Stream<ServiceHandle> services() {
        checkClosed();

        final int infoLevel = Winsvc.SC_ENUM_PROCESS_INFO;
        final int dwServiceType = WinNT.SERVICE_WIN32;
        final int dwServiceState = Winsvc.SERVICE_STATE_ALL;

        IntByReference pcbBytesNeeded = new IntByReference();
        IntByReference lpServicesReturned = new IntByReference();
        IntByReference lpResumeHandle = new IntByReference(0);
        boolean result = api.EnumServicesStatusEx(handle, infoLevel, dwServiceType, dwServiceState, null, 0, pcbBytesNeeded, lpServicesReturned,
                lpResumeHandle, null);
        if (!result) {
            throwLastErrorUnless(WinError.ERROR_MORE_DATA);
        }

        if (pcbBytesNeeded.getValue() == 0) {
            return Stream.empty();
        }

        Memory lpServices = new Memory(pcbBytesNeeded.getValue());
        result = api.EnumServicesStatusEx(handle, infoLevel, dwServiceType, dwServiceState, lpServices, pcbBytesNeeded.getValue(), pcbBytesNeeded,
                lpServicesReturned, lpResumeHandle, null);
        if (!result) {
            throwLastError();
        }

        if (lpServicesReturned.getValue() == 0) {
            return Stream.empty();
        }

        ENUM_SERVICE_STATUS_PROCESS status = Structure.newInstance(ENUM_SERVICE_STATUS_PROCESS.class, lpServices);
        status.read();
        ENUM_SERVICE_STATUS_PROCESS[] statuses = (ENUM_SERVICE_STATUS_PROCESS[]) status.toArray(lpServicesReturned.getValue());
        return Arrays.stream(statuses).map(s -> new ServiceHandle(this, s.lpServiceName));
    }

    /**
     * Returns a specific Windows service if available.
     *
     * @param serviceName The name of the service to return. Note that this is case insensitive.
     * @return An optional describing a handle to the specified Windows service, or {@link Optional#empty()} if no such service exists.
     */
    public Optional<ServiceHandle> service(String serviceName) {
        checkClosed();

        SC_HANDLE scHandle = api.OpenService(handle, serviceName, Winsvc.SERVICE_QUERY_CONFIG);
        if (scHandle == null) {
            throwLastErrorUnless(WinError.ERROR_SERVICE_DOES_NOT_EXIST);
            return Optional.empty();
        }
        try {
            return Optional.of(new ServiceHandle(this, serviceName));
        } finally {
            closeService(scHandle);
        }
    }

    ServiceHandle.StatusInfo status(ServiceHandle serviceHandle) {
        checkClosed();

        SC_HANDLE scHandle = openService(serviceHandle, Winsvc.SERVICE_QUERY_STATUS);
        try {
            SERVICE_STATUS_PROCESS status = queryStatus(scHandle);
            if (status == null) {
                throwLastError();
                return null;
            }

            return new ServiceHandle.StatusInfo(status);
        } finally {
            closeService(scHandle);
        }
    }

    void start(ServiceHandle serviceHandle, String... args) {
        checkClosed();

        SC_HANDLE scHandle = openService(serviceHandle, Winsvc.SERVICE_START);
        try {
            int dwNumServiceArgs = args != null ? args.length : 0;
            if (!api.StartService(scHandle, dwNumServiceArgs, args)) {
                throwLastError();
            }
        } finally {
            closeService(scHandle);
        }
    }

    ServiceHandle.StatusInfo start(ServiceHandle serviceHandle, String[] args, long maxWaitTime) throws InterruptedException {
        checkClosed();

        SC_HANDLE scHandle = openService(serviceHandle, Winsvc.SERVICE_START | Winsvc.SERVICE_QUERY_STATUS);
        try {
            int dwNumServiceArgs = args != null ? args.length : 0;
            if (!api.StartService(scHandle, dwNumServiceArgs, args)) {
                throwLastError();
            }
            return awaitStatusTransition(scHandle, maxWaitTime);
        } finally {
            closeService(scHandle);
        }
    }

    void stop(ServiceHandle serviceHandle) {
        control(serviceHandle, Winsvc.SERVICE_CONTROL_STOP, Winsvc.SERVICE_STOP);
    }

    void pause(ServiceHandle serviceHandle) {
        control(serviceHandle, Winsvc.SERVICE_CONTROL_PAUSE, Winsvc.SERVICE_PAUSE_CONTINUE);
    }

    void resume(ServiceHandle serviceHandle) {
        control(serviceHandle, Winsvc.SERVICE_CONTROL_CONTINUE, Winsvc.SERVICE_PAUSE_CONTINUE);
    }

    ServiceHandle.StatusInfo stop(ServiceHandle serviceHandle, long maxWaitTime) throws InterruptedException {
        return control(serviceHandle, Winsvc.SERVICE_CONTROL_STOP, Winsvc.SERVICE_STOP, maxWaitTime);
    }

    ServiceHandle.StatusInfo pause(ServiceHandle serviceHandle, long maxWaitTime) throws InterruptedException {
        return control(serviceHandle, Winsvc.SERVICE_CONTROL_PAUSE, Winsvc.SERVICE_PAUSE_CONTINUE, maxWaitTime);
    }

    ServiceHandle.StatusInfo resume(ServiceHandle serviceHandle, long maxWaitTime) throws InterruptedException {
        return control(serviceHandle, Winsvc.SERVICE_CONTROL_CONTINUE, Winsvc.SERVICE_PAUSE_CONTINUE, maxWaitTime);
    }

    private void control(ServiceHandle serviceHandle, int dwControl, int dwDesiredAccess) {
        checkClosed();

        SC_HANDLE scHandle = openService(serviceHandle, dwDesiredAccess);
        try {
            SERVICE_STATUS lpServiceStatus = new SERVICE_STATUS();
            if (!api.ControlService(scHandle, dwControl, lpServiceStatus)) {
                throwLastError();
            }
        } finally {
            closeService(scHandle);
        }
    }

    private ServiceHandle.StatusInfo control(ServiceHandle serviceHandle, int dwControl, int dwDesiredAccess, long maxWaitTime)
            throws InterruptedException {

        checkClosed();

        SC_HANDLE scHandle = openService(serviceHandle, dwDesiredAccess | Winsvc.SERVICE_QUERY_STATUS);
        try {
            SERVICE_STATUS lpServiceStatus = new SERVICE_STATUS();
            if (!api.ControlService(scHandle, dwControl, lpServiceStatus)) {
                throwLastError();
            }
            return awaitStatusTransition(scHandle, maxWaitTime);
        } finally {
            closeService(scHandle);
        }
    }

    private ServiceHandle.StatusInfo awaitStatusTransition(SC_HANDLE scHandle, long maxWaitTime) throws InterruptedException {
        final int infoLevel = SC_STATUS_TYPE.SC_STATUS_PROCESS_INFO;

        SERVICE_STATUS_PROCESS lpBuffer = new SERVICE_STATUS_PROCESS();
        IntByReference pcbBytesNeeded = new IntByReference();
        if (!api.QueryServiceStatusEx(scHandle, infoLevel, lpBuffer, lpBuffer.size(), pcbBytesNeeded)) {
            throwLastError();
        }

        long startTime = System.currentTimeMillis();
        int previousCheckPoint = lpBuffer.dwCheckPoint;

        while (ServiceStatus.of(lpBuffer.dwCurrentState).isTransitionStatus()) {
            // Do not wait longer than the wait hint.
            // A good interval is one-tenth of the wait hint but not less than 1 second and not more than the max wait time
            long waitTime = Math.min(maxWaitTime, Math.max(1000, lpBuffer.dwWaitHint / 10));
            Thread.sleep(waitTime);

            if (!api.QueryServiceStatusEx(scHandle, infoLevel, lpBuffer, lpBuffer.size(), pcbBytesNeeded)) {
                throwLastError();
            }

            if (lpBuffer.dwCheckPoint > previousCheckPoint) {
                // Continue to wait and check
                startTime = System.currentTimeMillis();
                previousCheckPoint = lpBuffer.dwCheckPoint;
            } else if (System.currentTimeMillis() - startTime > lpBuffer.dwWaitHint) {
                // No progress made within the wait hint
                break;
            }
        }

        return new ServiceHandle.StatusInfo(lpBuffer);
    }

    ServiceHandle.Info info(ServiceHandle serviceHandle) {
        checkClosed();

        SC_HANDLE scHandle = openService(serviceHandle, Winsvc.SERVICE_QUERY_CONFIG | Winsvc.SERVICE_QUERY_STATUS);
        try {
            QUERY_SERVICE_CONFIG config = queryServiceConfig(scHandle);
            SERVICE_STATUS_PROCESS status = queryStatus(scHandle);
            SERVICE_DESCRIPTION description = queryServiceConfig2(scHandle, Winsvc.SERVICE_CONFIG_DESCRIPTION, SERVICE_DESCRIPTION::new);
            SERVICE_DELAYED_AUTO_START_INFO delayedAutoStartInfo = queryServiceConfig2(scHandle, Winsvc.SERVICE_CONFIG_DELAYED_AUTO_START_INFO,
                    SERVICE_DELAYED_AUTO_START_INFO::new);

            return new ServiceHandle.Info(this, config, status, description, delayedAutoStartInfo);
        } finally {
            closeService(scHandle);
        }
    }

    Stream<ServiceHandle> dependents(ServiceHandle serviceHandle) {
        checkClosed();

        SC_HANDLE scHandle = openService(serviceHandle, Winsvc.SERVICE_ENUMERATE_DEPENDENTS);
        try {
            final int dwServiceState = Winsvc.SERVICE_STATE_ALL;

            IntByReference pcbBytesNeeded = new IntByReference();
            IntByReference lpServicesReturned = new IntByReference();
            if (!api.EnumDependentServices(scHandle, dwServiceState, null, 0, pcbBytesNeeded, lpServicesReturned)) {
                throwLastErrorUnless(WinError.ERROR_MORE_DATA);
            }

            if (pcbBytesNeeded.getValue() == 0) {
                return Stream.empty();
            }

            Memory lpServices = new Memory(pcbBytesNeeded.getValue());
            if (!api.EnumDependentServices(scHandle, dwServiceState, lpServices, pcbBytesNeeded.getValue(), pcbBytesNeeded, lpServicesReturned)) {
                throwLastError();
            }

            if (lpServicesReturned.getValue() == 0) {
                return Stream.empty();
            }

            ENUM_SERVICE_STATUS status = Structure.newInstance(ENUM_SERVICE_STATUS.class, lpServices);
            status.read();
            ENUM_SERVICE_STATUS[] statuses = (ENUM_SERVICE_STATUS[]) status.toArray(lpServicesReturned.getValue());
            return Arrays.stream(statuses).map(s -> new ServiceHandle(this, s.lpServiceName));
        } finally {
            closeService(scHandle);
        }
    }

    private SERVICE_STATUS_PROCESS queryStatus(SC_HANDLE scHandle) {
        final int infoLevel = SC_STATUS_TYPE.SC_STATUS_PROCESS_INFO;

        SERVICE_STATUS_PROCESS lpBuffer = new SERVICE_STATUS_PROCESS();
        IntByReference pcbBytesNeeded = new IntByReference();
        if (api.QueryServiceStatusEx(scHandle, infoLevel, lpBuffer, lpBuffer.size(), pcbBytesNeeded)) {
            return lpBuffer;
        }
        return null;
    }

    private QUERY_SERVICE_CONFIG queryServiceConfig(SC_HANDLE scHandle) {
        IntByReference pcbBytesNeeded = new IntByReference();
        if (!api.QueryServiceConfig(scHandle, null, 0, pcbBytesNeeded) && !hasLastError(WinError.ERROR_INSUFFICIENT_BUFFER)) {
            return null;
        }
        QUERY_SERVICE_CONFIG lpServiceConfig = new QUERY_SERVICE_CONFIG(new Memory(pcbBytesNeeded.getValue()));
        if (api.QueryServiceConfig(scHandle, lpServiceConfig, pcbBytesNeeded.getValue(), pcbBytesNeeded)) {
            return lpServiceConfig;
        }
        return null;
    }

    private <T extends Structure> T queryServiceConfig2(SC_HANDLE scHandle, int dwInfoLevel, Function<Memory, T> bufferFactory) {
        IntByReference pcbBytesNeeded = new IntByReference();
        if (!api.QueryServiceConfig2(scHandle, dwInfoLevel, null, 0, pcbBytesNeeded) && !hasLastError(WinError.ERROR_INSUFFICIENT_BUFFER)) {
            return null;
        }
        Memory lpBuffer = new Memory(pcbBytesNeeded.getValue());
        if (api.QueryServiceConfig2(scHandle, dwInfoLevel, lpBuffer, pcbBytesNeeded.getValue(), pcbBytesNeeded)) {
            T lpConfig = bufferFactory.apply(lpBuffer);
            lpConfig.read();
            return lpConfig;
        }
        return null;
    }

    private SC_HANDLE openService(ServiceHandle serviceHandle, int dwDesiredAccess) {
        return openService(serviceHandle.serviceName(), dwDesiredAccess);
    }

    private SC_HANDLE openService(String serviceName, int dwDesiredAccess) {
        SC_HANDLE scHandle = api.OpenService(handle, serviceName, dwDesiredAccess);
        if (scHandle == null) {
            throwLastError();
        }
        return scHandle;
    }

    private void closeService(SC_HANDLE scHandle) {
        if (!api.CloseServiceHandle(scHandle)) {
            throwLastError();
        }
    }

    private static boolean hasLastError(int code) {
        int lastError = kernel32.GetLastError();
        return lastError == code;
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
        default:
            return new ServiceException(code);
        }
    }

    @SuppressWarnings("nls")
    public static void main(String[] args) throws IOException, InterruptedException {
        try (ServiceManager serviceManager = local()) {
            testService(serviceManager.service("Apache2.4").get());
            testService(serviceManager.service("MariaDB").get());
            testService(serviceManager.service("AxInstSV").get());
            testService(serviceManager.service("RpcSs").get());
            testService(serviceManager.service("UserManager").get());
            testService(serviceManager.service("DoSvc").get());

            ServiceHandle sh = serviceManager.service("Apache2.4").get();
            //System.out.printf("%s%n", serviceManager.start(sh, null, 1000));
            //System.out.printf("%s%n", serviceManager.stop(sh, 1000));
            System.out.printf("%s%n", sh.status());
            sh.start();
            System.out.printf("%s%n", sh.status());
            Thread.sleep(1000);
            System.out.printf("%s%n", sh.status());

            System.out.printf("---%n");
            sh = serviceManager.service("MariaDB").get();
            System.out.printf("%s%n", sh.status());
            //sh.stop();
            //sh.pause();
            System.out.printf("%s%n", sh.status());

            serviceManager.services().forEach(s -> {});
        }
    }

    @SuppressWarnings("nls")
    private static void testService(ServiceHandle sh) {
        System.out.printf("%s%n", sh);
        System.out.printf("%s%n", sh.info());
        System.out.printf("%s%n", sh.status());
        System.out.printf("Dependencies:%n");
        sh.info().dependencies().forEach(d -> System.out.printf("- %s, %s%n", d, d.info().displayName()));
        System.out.printf("Dependent:%n");
        sh.dependents().limit(10).forEach(d -> System.out.printf("- %s, %s%n", d, d.info().displayName()));
        System.out.printf("Process:%n");
        sh.info().process().map(ProcessHandle::info).ifPresent(System.out::println);
        System.out.printf("---%n");
    }
}
