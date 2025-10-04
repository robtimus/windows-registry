/*
 * Registry.java
 * Copyright 2025 Rob Spoor
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

import java.lang.foreign.MemorySegment;
import com.github.robtimus.os.windows.registry.foreign.Advapi32;

/**
 * A representation of a local or remote Windows registry.
 *
 * @author Rob Spoor
 * @since 2.0
 */
public abstract sealed class Registry permits LocalRegistry, RemoteRegistry {

    private static final ScopedValue<Context> CONTEXT = ScopedValue.newInstance();

    private static final Context.NonTransactional NO_TRANSACTION = new Context.NonTransactional();

    Registry() {
    }

    /**
     * Returns a representation of the local Windows registry.
     *
     * @return A representation of the local Windows registry.
     */
    public static LocalRegistry local() {
        return LocalRegistry.INSTANCE;
    }

    /**
     * Returns an object that can be used to connect to the registry on a remote machine.
     *
     * @param machineName The machine name. This cannot be an IP address but must be a resolvable host name.
     * @return An object that can be used to connect to the registry on the given remote machine.
     * @throws NullPointerException If the given machine name is {@code null}.
     */
    public static RemoteRegistry.Connector at(String machineName) {
        return new RemoteRegistry.Connector(machineName);
    }

    // transactional support

    static Context currentContext() {
        return CONTEXT.orElse(NO_TRANSACTION);
    }

    static <R, X extends Throwable> R callWithTransaction(Transaction transaction, TransactionalState.Callable<R, X> action) throws X {
        return callWithContext(new Context.Transactional(transaction), action);
    }

    static <R, X extends Throwable> R callWithoutTransaction(TransactionalState.Callable<R, X> action) throws X {
        return callWithContext(NO_TRANSACTION, action);
    }

    private static <R, X extends Throwable> R callWithContext(Context context, TransactionalState.Callable<R, X> action) throws X {
        return ScopedValue.where(CONTEXT, context).call(action::call);
    }

    abstract static sealed class Context {

        abstract int createKey(
                Advapi32 api,
                MemorySegment hKey,
                MemorySegment lpSubKey,
                int dwOptions,
                int samDesired,
                MemorySegment phkResult,
                MemorySegment lpdwDisposition);

        abstract int deleteKey(
                Advapi32 api,
                MemorySegment hKey,
                MemorySegment lpSubKey,
                int samDesired);

        abstract int openKey(
                Advapi32 api,
                MemorySegment hKey,
                MemorySegment lpSubKey,
                int ulOptions,
                int samDesired,
                MemorySegment phkResult);

        static final class Transactional extends Context {

            private final Transaction transaction;

            private Transactional(Transaction transaction) {
                this.transaction = transaction;
            }

            Transaction transaction() {
                return transaction;
            }

            @Override
            int createKey(
                    Advapi32 api,
                    MemorySegment hKey,
                    MemorySegment lpSubKey,
                    int dwOptions,
                    int samDesired,
                    MemorySegment phkResult,
                    MemorySegment lpdwDisposition) {

                return api.RegCreateKeyTransacted(
                        hKey,
                        lpSubKey,
                        0,
                        MemorySegment.NULL,
                        dwOptions,
                        samDesired,
                        MemorySegment.NULL,
                        phkResult,
                        lpdwDisposition,
                        transaction.handle(),
                        MemorySegment.NULL);
            }

            @Override
            int deleteKey(
                    Advapi32 api,
                    MemorySegment hKey,
                    MemorySegment lpSubKey,
                    int samDesired) {

                return api.RegDeleteKeyTransacted(
                        hKey,
                        lpSubKey,
                        samDesired,
                        0,
                        transaction.handle(),
                        MemorySegment.NULL);
            }

            @Override
            int openKey(
                    Advapi32 api,
                    MemorySegment hKey,
                    MemorySegment lpSubKey,
                    int ulOptions,
                    int samDesired,
                    MemorySegment phkResult) {

                return api.RegOpenKeyTransacted(
                        hKey,
                        lpSubKey,
                        ulOptions,
                        samDesired,
                        phkResult,
                        transaction.handle(),
                        MemorySegment.NULL);
            }
        }

        static final class NonTransactional extends Context {

            private NonTransactional() {
            }

            @Override
            int createKey(
                    Advapi32 api,
                    MemorySegment hKey,
                    MemorySegment lpSubKey,
                    int dwOptions,
                    int samDesired,
                    MemorySegment phkResult,
                    MemorySegment lpdwDisposition) {

                return api.RegCreateKeyEx(
                        hKey,
                        lpSubKey,
                        0,
                        MemorySegment.NULL,
                        dwOptions,
                        samDesired,
                        MemorySegment.NULL,
                        phkResult,
                        lpdwDisposition);
            }

            @Override
            int deleteKey(
                    Advapi32 api,
                    MemorySegment hKey,
                    MemorySegment lpSubKey,
                    int samDesired) {

                return api.RegDeleteKeyEx(
                        hKey,
                        lpSubKey,
                        samDesired,
                        0);
            }

            @Override
            int openKey(
                    Advapi32 api,
                    MemorySegment hKey,
                    MemorySegment lpSubKey,
                    int ulOptions,
                    int samDesired,
                    MemorySegment phkResult) {

                return api.RegOpenKeyEx(hKey, lpSubKey, ulOptions, samDesired, phkResult);
            }
        }
    }
}
