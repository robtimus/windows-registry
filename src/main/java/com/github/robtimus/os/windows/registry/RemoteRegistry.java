/*
 * RemoteRegistry.java
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

package com.github.robtimus.os.windows.registry;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.ref.Cleaner;
import java.util.Objects;
import com.github.robtimus.os.windows.registry.foreign.WString;
import com.github.robtimus.os.windows.registry.foreign.WinDef.HKEY;
import com.github.robtimus.os.windows.registry.foreign.WinError;

/**
 * A representation of a Windows registry on a remote machine.
 *
 * @author Rob Spoor
 * @since 2.0
 */
public final class RemoteRegistry extends Registry implements AutoCloseable {

    // CHECKSTYLE:OFF: MemberName

    /** The HKEY_LOCAL_MACHINE root key on the remote machine. */
    public final RegistryKey HKEY_LOCAL_MACHINE; // NOSONAR

    /** The HKEY_USERS root key on the remote machine. */
    public final RegistryKey HKEY_USERS; // NOSONAR

    // CHECKSTYLE:ON: MemberName

    private final Cleaner.Cleanable cleanable;

    private RemoteRegistry(RemoteRootKey hklm, RemoteRootKey hku) {
        this.HKEY_LOCAL_MACHINE = hklm;
        this.HKEY_USERS = hku;

        this.cleanable = RegistryKey.runOnClean(this, () -> {
            // Close in reversed order, as they are closed from last to first
            try (var _ = RemoteRootKeyCloseable.fromRemoteRootKey(hku);
                    var _ = RemoteRootKeyCloseable.fromRemoteRootKey(hklm)) {
                // does nothing
            }
        });
    }

    static RemoteRegistry connect(String machineName) {
        RemoteRootKey hklm = null;
        RemoteRootKey hku = null;

        try (Arena allocator = Arena.ofConfined()) {
            MemorySegment lpMachineName = WString.allocate(allocator, machineName);
            MemorySegment phkResult = HKEY.allocateRef(allocator);

            hklm = connect(LocalRootKey.HKEY_LOCAL_MACHINE, machineName, lpMachineName, phkResult);
            hku = connect(LocalRootKey.HKEY_USERS, machineName, lpMachineName, phkResult);
            return new RemoteRegistry(hklm, hku);
        } catch (RegistryException e) {
            // Close in reversed order, as they are closed from last to first
            try (var _ = RemoteRootKeyCloseable.fromRemoteRootKey(hku);
                    var _ = RemoteRootKeyCloseable.fromRemoteRootKey(hklm)) {

                throw e;
            }
        }
    }

    private static RemoteRootKey connect(LocalRootKey rootKey, String machineName, MemorySegment lpMachineName, MemorySegment phkResult) {
        int code = RegistryKey.api.RegConnectRegistry(lpMachineName, rootKey.hKey(), phkResult);
        if (code != WinError.ERROR_SUCCESS) {
            throw RegistryException.forKey(code, rootKey.path(), machineName);
        }
        return new RemoteRootKey(machineName, rootKey, HKEY.target(phkResult));
    }

    /**
     * Closes the connection to the remote registry.
     *
     * @throws RegistryException If the remote registry could not be closed.
     */
    @Override
    public void close() {
        cleanable.clean();
    }

    /**
     * An object that can be used to connect to the registry on a remote machine.
     *
     * @author Rob Spoor
     */
    public static final class Connector {

        private final String machineName;

        Connector(String machineName) {
            this.machineName = Objects.requireNonNull(machineName);
        }

        /**
         * Connects to the registry on the remote machine.
         * The returned registry needs to be closed when it is no longer needed.
         *
         * @return A representation of the registry on the given remote machine.
         * @throws RegistryException If the connection failed.
         */
        public RemoteRegistry connect() {
            return RemoteRegistry.connect(machineName);
        }
    }

    private interface RemoteRootKeyCloseable extends AutoCloseable {

        @Override
        void close();

        static RemoteRootKeyCloseable fromRemoteRootKey(RemoteRootKey rootKey) {
            return rootKey == null ? null : rootKey::close;
        }
    }
}
