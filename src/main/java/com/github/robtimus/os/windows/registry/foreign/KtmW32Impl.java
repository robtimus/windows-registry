/*
 * KtmW32Impl.java
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

final class KtmW32Impl implements KtmW32 {

    private final Optional<MethodHandle> createTransaction;
    private final Optional<MethodHandle> commitTransaction;
    private final Optional<MethodHandle> rollbackTransaction;
    private final Optional<MethodHandle> getTransactionInformation;

    @SuppressWarnings("nls")
    KtmW32Impl() {
        Linker linker = Linker.nativeLinker();
        Optional<SymbolLookup> symbolLookup = optionalSymbolLookup("KtmW32", ARENA);

        // CreateTransaction does not work before Windows Vista / Windows Server 2008
        createTransaction = optionalFunctionMethodHandle(linker, symbolLookup, "CreateTransaction", FunctionDescriptor.of(ValueLayout.ADDRESS,
                ValueLayout.ADDRESS, // lpSecurityAttributes
                ValueLayout.ADDRESS, // UOW
                ValueLayout.JAVA_INT, // CreateOptions
                ValueLayout.JAVA_INT, // IsolationLevel
                ValueLayout.JAVA_INT, // IsolationFlags
                ValueLayout.JAVA_INT, // Timeout
                ValueLayout.ADDRESS), // Description
                CaptureState.LINKER_OPTION);

        // CommitTransaction does not work before Windows Vista / Windows Server 2008
        commitTransaction = optionalFunctionMethodHandle(linker, symbolLookup, "CommitTransaction", FunctionDescriptor.of(ValueLayout.JAVA_BOOLEAN,
                ValueLayout.ADDRESS), // TransactionHandle
                CaptureState.LINKER_OPTION);

        // RollbackTransaction does not work before Windows Vista / Windows Server 2008
        rollbackTransaction = optionalFunctionMethodHandle(linker, symbolLookup, "RollbackTransaction", FunctionDescriptor.of(
                ValueLayout.JAVA_BOOLEAN, // return value
                ValueLayout.ADDRESS), // TransactionHandle
                CaptureState.LINKER_OPTION);

        // GetTransactionInformation does not work before Windows Vista / Windows Server 2008
        getTransactionInformation = optionalFunctionMethodHandle(linker, symbolLookup, "GetTransactionInformation", FunctionDescriptor.of(
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

    @Override
    public MemorySegment CreateTransaction(
            MemorySegment lpSecurityAttributes,
            MemorySegment UOW, // NOSONAR
            int CreateOptions, // NOSONAR
            int IsolationLevel, // NOSONAR
            int IsolationFlags, // NOSONAR
            int Timeout, // NOSONAR
            MemorySegment Description, // NOSONAR
            MemorySegment captureState) {

        MethodHandle createTransactionHandle = createTransaction.orElseThrow(UnsupportedOperationException::new);
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

    @Override
    public boolean isCreateTransactionEnabled() {
        return createTransaction.isPresent();
    }

    @Override
    public boolean CommitTransaction(
            MemorySegment TransactionHandle, // NOSONAR
            MemorySegment captureState) {

        MethodHandle commitTransactionHandle = commitTransaction.orElseThrow(UnsupportedOperationException::new);
        try {
            return (boolean) commitTransactionHandle.invokeExact(
                    captureState,
                    TransactionHandle);
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public boolean isCommitTransactionEnabled() {
        return commitTransaction.isPresent();
    }

    @Override
    public boolean RollbackTransaction(
            MemorySegment TransactionHandle, // NOSONAR
            MemorySegment captureState) {

        MethodHandle rollbackTransactionHandle = rollbackTransaction.orElseThrow(UnsupportedOperationException::new);
        try {
            return (boolean) rollbackTransactionHandle.invokeExact(
                    captureState,
                    TransactionHandle);
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public boolean isRollbackTransactionEnabled() {
        return rollbackTransaction.isPresent();
    }

    @Override
    public boolean GetTransactionInformation(
            MemorySegment TransactionHandle, // NOSONAR
            MemorySegment Outcome, // NOSONAR
            MemorySegment IsolationLevel, // NOSONAR
            MemorySegment IsolationFlags, // NOSONAR
            MemorySegment Timeout, // NOSONAR
            int BufferLength, // NOSONAR
            MemorySegment Description, // NOSONAR
            MemorySegment captureState) {

        MethodHandle getTransactionInformationHandle = getTransactionInformation.orElseThrow(UnsupportedOperationException::new);
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

    @Override
    public boolean isGetTransactionInformationEnabled() {
        return getTransactionInformation.isPresent();
    }
}
