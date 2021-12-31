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

import java.io.Closeable;
import com.sun.jna.platform.win32.WinError;
import com.sun.jna.platform.win32.WinReg.HKEYByReference;

/**
 * A representation of registry keys on a remote machine.
 *
 * @author Rob Spoor
 */
public abstract class RemoteRegistryKey extends RegistryKey implements Closeable {

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
         * @param machineName The machine name.
         * @return A reference to the registry on the given remote machine.
         * @throws RegistryException If the connection failed.
         */
        public RemoteRegistryKey at(String machineName) {
            HKEYByReference phkResult = new HKEYByReference();
            int code = api.RegConnectRegistry(machineName, rootKey.hKey, phkResult);
            if (code != WinError.ERROR_SUCCESS) {
                throw RegistryException.of(code, rootKey.path());
            }
            return new RemoteRootKey(machineName, rootKey, phkResult.getValue());
        }
    }

    @Override
    public abstract void close();
}
