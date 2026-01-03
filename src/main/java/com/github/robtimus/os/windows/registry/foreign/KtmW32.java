/*
 * KtmW32.java
 * Copyright 2024 Rob Spoor
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

package com.github.robtimus.os.windows.registry.foreign;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.Optional;

@SuppressWarnings({ "javadoc", "nls" })
public final class KtmW32 extends WindowsApi {

    private static final Optional<MethodHandle> CREATE_TRANSACTION;
    private static final Optional<MethodHandle> COMMIT_TRANSACTION;
    private static final Optional<MethodHandle> ROLLBACK_TRANSACTION;
    private static final Optional<MethodHandle> GET_TRANSACTION_INFORMATION;

    static {
        Linker linker = Linker.nativeLinker();
        Optional<SymbolLookup> ktmW32 = optionalSymbolLookup("KtmW32", ARENA);

        // CreateTransaction does not work before Windows Vista / Windows Server 2008
        CREATE_TRANSACTION = ktmW32.flatMap(l -> l.find("CreateTransaction"))
                .map(address -> linker.downcallHandle(address, FunctionDescriptor.of(
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS, // lpSecurityAttributes
                        ValueLayout.ADDRESS, // UOW
                        ValueLayout.JAVA_INT, // CreateOptions
                        ValueLayout.JAVA_INT, // IsolationLevel
                        ValueLayout.JAVA_INT, // IsolationFlags
                        ValueLayout.JAVA_INT, // Timeout
                        ValueLayout.ADDRESS), // Description
                        CaptureState.LINKER_OPTION));

        // CommitTransaction does not work before Windows Vista / Windows Server 2008
        COMMIT_TRANSACTION = ktmW32.flatMap(l -> l.find("CommitTransaction"))
                .map(address -> linker.downcallHandle(address, FunctionDescriptor.of(
                        ValueLayout.JAVA_BOOLEAN,
                        ValueLayout.ADDRESS), // TransactionHandle
                        CaptureState.LINKER_OPTION));

        // RollbackTransaction does not work before Windows Vista / Windows Server 2008
        ROLLBACK_TRANSACTION = ktmW32.flatMap(l -> l.find("RollbackTransaction"))
                .map(address -> linker.downcallHandle(address, FunctionDescriptor.of(
                        ValueLayout.JAVA_BOOLEAN, // return value
                        ValueLayout.ADDRESS), // TransactionHandle
                        CaptureState.LINKER_OPTION));

        // GetTransactionInformation does not work before Windows Vista / Windows Server 2008
        GET_TRANSACTION_INFORMATION = ktmW32.flatMap(l -> l.find("GetTransactionInformation"))
                .map(address -> linker.downcallHandle(address, FunctionDescriptor.of(
                        ValueLayout.JAVA_BOOLEAN, // return value
                        ValueLayout.ADDRESS, // TransactionHandle
                        ValueLayout.ADDRESS, // Outcome
                        ValueLayout.ADDRESS, // IsolationLevel
                        ValueLayout.ADDRESS, // IsolationFlags
                        ValueLayout.ADDRESS, // Timeout
                        ValueLayout.JAVA_INT, // BufferLength
                        ValueLayout.ADDRESS), // Description
                        CaptureState.LINKER_OPTION));
    }

    private KtmW32() {
    }

    // The following functions all require GetLastError() to be called to retrieve any error that occurred

    /*
     * HANDLE CreateTransaction(
     *   [in, optional] LPSECURITY_ATTRIBUTES lpTransactionAttributes,
     *   [in, optional] LPGUID                UOW,
     *   [in, optional] DWORD                 CreateOptions,
     *   [in, optional] DWORD                 IsolationLevel,
     *   [in, optional] DWORD                 IsolationFlags,
     *   [in, optional] DWORD                 Timeout,
     *   [in, optional] LPWSTR                Description
     * )
     */
    @SuppressWarnings({ "checkstyle:MethodName", "squid:S100", "squid:S107" })
    public static MemorySegment CreateTransaction(
            MemorySegment lpSecurityAttributes,
            @SuppressWarnings({ "checkstyle:ParameterName", "squid:S117" })
            MemorySegment UOW,
            @SuppressWarnings({ "checkstyle:ParameterName", "squid:S117" })
            int CreateOptions,
            @SuppressWarnings({ "checkstyle:ParameterName", "squid:S117" })
            int IsolationLevel,
            @SuppressWarnings({ "checkstyle:ParameterName", "squid:S117" })
            int IsolationFlags,
            @SuppressWarnings({ "checkstyle:ParameterName", "squid:S117" })
            int Timeout,
            @SuppressWarnings({ "checkstyle:ParameterName", "squid:S117" })
            MemorySegment Description,
            MemorySegment captureState) {

        MethodHandle createTransactionHandle = CREATE_TRANSACTION.orElseThrow(UnsupportedOperationException::new);
        try {
            return (MemorySegment) createTransactionHandle.invokeExact(
                    captureState,
                    lpSecurityAttributes,
                    UOW,
                    CreateOptions,
                    IsolationLevel,
                    IsolationFlags,
                    Timeout,
                    Description);
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        }
    }

    public static boolean isCreateTransactionEnabled() {
        return CREATE_TRANSACTION.isPresent();
    }

    /*
     * BOOL CommitTransaction(
     *   [in] HANDLE TransactionHandle
     * )
     */
    @SuppressWarnings({ "checkstyle:MethodName", "squid:S100" })
    public static boolean CommitTransaction(
            @SuppressWarnings({ "checkstyle:ParameterName", "squid:S117" })
            MemorySegment TransactionHandle,
            MemorySegment captureState) {

        MethodHandle commitTransactionHandle = COMMIT_TRANSACTION.orElseThrow(UnsupportedOperationException::new);
        try {
            return (boolean) commitTransactionHandle.invokeExact(
                    captureState,
                    TransactionHandle);
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        }
    }

    public static boolean isCommitTransactionEnabled() {
        return COMMIT_TRANSACTION.isPresent();
    }

    /*
     * BOOL RollbackTransaction(
     *   [in] HANDLE TransactionHandle
     * )
     */
    @SuppressWarnings({ "checkstyle:MethodName", "squid:S100" })
    public static boolean RollbackTransaction(
            @SuppressWarnings({ "checkstyle:ParameterName", "squid:S117" })
            MemorySegment TransactionHandle,
            MemorySegment captureState) {

        MethodHandle rollbackTransactionHandle = ROLLBACK_TRANSACTION.orElseThrow(UnsupportedOperationException::new);
        try {
            return (boolean) rollbackTransactionHandle.invokeExact(
                    captureState,
                    TransactionHandle);
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        }
    }

    public static boolean isRollbackTransactionEnabled() {
        return ROLLBACK_TRANSACTION.isPresent();
    }

    /*
     * BOOL GetTransactionInformation(
     *   [in]            HANDLE TransactionHandle,
     *   [out, optional] PDWORD Outcome,
     *   [out, optional] PDWORD IsolationLevel,
     *   [out, optional] PDWORD IsolationFlags,
     *   [out, optional] PDWORD Timeout,
     *   [in]            DWORD  BufferLength,
     *   [out, optional] LPWSTR Description
     * )
     */
    @SuppressWarnings({ "checkstyle:MethodName", "squid:S100", "squid:S107" })
    public static boolean GetTransactionInformation(
            @SuppressWarnings({ "checkstyle:ParameterName", "squid:S117" })
            MemorySegment TransactionHandle,
            @SuppressWarnings({ "checkstyle:ParameterName", "squid:S117" })
            MemorySegment Outcome,
            @SuppressWarnings({ "checkstyle:ParameterName", "squid:S117" })
            MemorySegment IsolationLevel,
            @SuppressWarnings({ "checkstyle:ParameterName", "squid:S117" })
            MemorySegment IsolationFlags,
            @SuppressWarnings({ "checkstyle:ParameterName", "squid:S117" })
            MemorySegment Timeout,
            @SuppressWarnings({ "checkstyle:ParameterName", "squid:S117" })
            int BufferLength,
            @SuppressWarnings({ "checkstyle:ParameterName", "squid:S117" })
            MemorySegment Description,
            MemorySegment captureState) {

        MethodHandle getTransactionInformationHandle = GET_TRANSACTION_INFORMATION.orElseThrow(UnsupportedOperationException::new);
        try {
            return (boolean) getTransactionInformationHandle.invokeExact(
                    captureState,
                    TransactionHandle,
                    Outcome,
                    IsolationLevel,
                    IsolationFlags,
                    Timeout,
                    BufferLength,
                    Description);
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        }
    }

    public static boolean isGetTransactionInformationEnabled() {
        return GET_TRANSACTION_INFORMATION.isPresent();
    }
}
