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

import static com.github.robtimus.os.windows.registry.foreign.ForeignUtils.ARENA;
import static com.github.robtimus.os.windows.registry.foreign.ForeignUtils.optionalFunctionMethodHandle;
import static com.github.robtimus.os.windows.registry.foreign.ForeignUtils.optionalSymbolLookup;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.Optional;

@SuppressWarnings({ "javadoc", "nls" })
public final class KtmW32 {

    private static final Optional<MethodHandle> CREATE_TRANSACTION;
    private static final Optional<MethodHandle> COMMIT_TRANSACTION;
    private static final Optional<MethodHandle> ROLLBACK_TRANSACTION;
    private static final Optional<MethodHandle> GET_TRANSACTION_INFORMATION;

    static {
        Linker linker = Linker.nativeLinker();
        Optional<SymbolLookup> ktmW32 = optionalSymbolLookup("KtmW32", ARENA);

        // CreateTransaction does not work before Windows Vista / Windows Server 2008
        CREATE_TRANSACTION = optionalFunctionMethodHandle(linker, ktmW32, "CreateTransaction", FunctionDescriptor.of(ValueLayout.ADDRESS,
                ValueLayout.ADDRESS, // lpSecurityAttributes
                ValueLayout.ADDRESS, // UOW
                ValueLayout.JAVA_INT, // CreateOptions
                ValueLayout.JAVA_INT, // IsolationLevel
                ValueLayout.JAVA_INT, // IsolationFlags
                ValueLayout.JAVA_INT, // Timeout
                ValueLayout.ADDRESS), // Description
                CaptureState.LINKER_OPTION);

        // CommitTransaction does not work before Windows Vista / Windows Server 2008
        COMMIT_TRANSACTION = optionalFunctionMethodHandle(linker, ktmW32, "CommitTransaction", FunctionDescriptor.of(ValueLayout.JAVA_BOOLEAN,
                ValueLayout.ADDRESS), // TransactionHandle
                CaptureState.LINKER_OPTION);

        // RollbackTransaction does not work before Windows Vista / Windows Server 2008
        ROLLBACK_TRANSACTION = optionalFunctionMethodHandle(linker, ktmW32, "RollbackTransaction", FunctionDescriptor.of(
                ValueLayout.JAVA_BOOLEAN, // return value
                ValueLayout.ADDRESS), // TransactionHandle
                CaptureState.LINKER_OPTION);

        // GetTransactionInformation does not work before Windows Vista / Windows Server 2008
        GET_TRANSACTION_INFORMATION = optionalFunctionMethodHandle(linker, ktmW32, "GetTransactionInformation", FunctionDescriptor.of(
                ValueLayout.JAVA_BOOLEAN, // return value
                ValueLayout.ADDRESS, // TransactionHandle
                ValueLayout.ADDRESS, // Outcome
                ValueLayout.ADDRESS, // IsolationLevel
                ValueLayout.ADDRESS, // IsolationFlags
                ValueLayout.ADDRESS, // Timeout
                ValueLayout.JAVA_INT, // BufferLength
                ValueLayout.ADDRESS), // Description
                CaptureState.LINKER_OPTION);
    }

    private KtmW32() {
    }

    // The following functions all require GetLastError() to be called to retrieve any error that occurred

    public static /* HANDLE */ MemorySegment CreateTransaction/* NOSONAR */(
            /* LPSECURITY_ATTRIBUTES */ MemorySegment lpSecurityAttributes,
            /* LPGUID */ MemorySegment UOW, // NOSONAR
            /* DWORD */ int CreateOptions, // NOSONAR
            /* DWORD */ int IsolationLevel, // NOSONAR
            /* DWORD */ int IsolationFlags, // NOSONAR
            /* DWORD */ int Timeout, // NOSONAR
            /* LPWSTR */ MemorySegment Description, // NOSONAR
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

    public static /* BOOL */ boolean CommitTransaction/* NOSONAR */(
            /* HANDLE */ MemorySegment TransactionHandle, // NOSONAR
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

    public static /* BOOL */ boolean RollbackTransaction/* NOSONAR */(
            /* HANDLE */ MemorySegment TransactionHandle, // NOSONAR
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

    public static /* BOOL */ boolean GetTransactionInformation/* NOSONAR */(
            /* HANDLE */ MemorySegment TransactionHandle, // NOSONAR
            /* PDWORD */ MemorySegment Outcome, // NOSONAR
            /* PDWORD */ MemorySegment IsolationLevel, // NOSONAR
            /* PDWORD */ MemorySegment IsolationFlags, // NOSONAR
            /* PDWORD */ MemorySegment Timeout, // NOSONAR
            /* DWORD */ int BufferLength, // NOSONAR
            /* LPWSTR */ MemorySegment Description, // NOSONAR
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
