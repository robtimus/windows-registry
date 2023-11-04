/*
 * RemoteRegistryKey.java
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
import com.github.robtimus.os.windows.registry.foreign.StringPointer;
import com.github.robtimus.os.windows.registry.foreign.WinDef.HKEY;
import com.github.robtimus.os.windows.registry.foreign.WinError;

/**
 * A representation of registry keys on a remote machine.
 *
 * @author Rob Spoor
 */
public abstract class RemoteRegistryKey extends RegistryKey implements AutoCloseable {

    /** A connector for connecting to the HKEY_LOCAL_MACHINE root key on a remote machine. */
    public static final Connector HKEY_LOCAL_MACHINE = new Connector(RootKey.HKEY_LOCAL_MACHINE);

    /** A connector for connecting to the HKEY_USERS root key on a remote machine. */
    public static final Connector HKEY_USERS = new Connector(RootKey.HKEY_USERS);

    /**
     * A class for connecting to a registry key on a remote machine.
     *
     * @author Rob Spoor
     */
    public static final class Connector {

        private final RootKey rootKey;

        private Connector(RootKey rootKey) {
            this.rootKey = rootKey;
        }

        /**
         * Connects to the registry key on a remote machine.
         * The returned registry key needs to be closed when it is no longer needed. There is no need to close any registry keys retrieved from it,
         * e.g. using {@link RegistryKey#subKeys()} or {@link RegistryKey#resolve(String)}.
         *
         * @param machineName The machine name. This cannot be an IP address but must be a resolvable host name.
         * @return A reference to the registry on the given remote machine.
         * @throws RegistryException If the connection failed.
         */
        public RemoteRegistryKey at(String machineName) {
            try (Arena allocator = Arena.ofConfined()) {
                StringPointer lpMachineName = StringPointer.withValue(machineName, allocator);
                HKEY.Reference phkResult = HKEY.uninitializedReference(allocator);

                int code = api.RegConnectRegistry(lpMachineName, rootKey.hKey(), phkResult);
                if (code != WinError.ERROR_SUCCESS) {
                    throw RegistryException.forKey(code, rootKey.path(), machineName);
                }
                return new RemoteRootKey(machineName, rootKey, phkResult.value());
            }
        }
    }

    RemoteRegistryKey() {
    }

    /**
     * Closes the connection to the remote registry key.
     *
     * @throws RegistryException If the remote registry key could not be closed.
     */
    @Override
    public abstract void close();
}
