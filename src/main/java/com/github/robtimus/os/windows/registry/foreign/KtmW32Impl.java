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
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.Optional;
import com.github.robtimus.os.windows.registry.foreign.WinDef.HANDLE;

final class KtmW32Impl extends ApiImpl implements KtmW32 {

    private final Optional<MethodHandle> createTransaction;
    private final Optional<MethodHandle> commitTransaction;
    private final Optional<MethodHandle> rollbackTransaction;
    private final Optional<MethodHandle> getTransactionInformation;

    @SuppressWarnings("nls")
    KtmW32Impl() {
        Linker linker = Linker.nativeLinker();
        Optional<SymbolLookup> symbolLookup = optionalSymbolLookup("KtmW32", ARENA);

        // CreateTransaction does not work before Windows Vista / Windows Server 2008
        createTransaction = optionalFunctionMethodHandle(linker, symbolLookup, "CreateTransaction", ValueLayout.ADDRESS,
                ValueLayout.ADDRESS, // lpSecurityAttributes
                ValueLayout.ADDRESS, // UOW
                ValueLayout.JAVA_INT, // CreateOptions
                ValueLayout.JAVA_INT, // IsolationLevel
                ValueLayout.JAVA_INT, // IsolationFlags
                ValueLayout.JAVA_INT, // Timeout
                ValueLayout.ADDRESS); // Description

        // CommitTransaction does not work before Windows Vista / Windows Server 2008
        commitTransaction = optionalFunctionMethodHandle(linker, symbolLookup, "CommitTransaction", ValueLayout.JAVA_BOOLEAN,
                ValueLayout.ADDRESS); // TransactionHandle

        // RollbackTransaction does not work before Windows Vista / Windows Server 2008
        rollbackTransaction = optionalFunctionMethodHandle(linker, symbolLookup, "RollbackTransaction", ValueLayout.JAVA_BOOLEAN,
                ValueLayout.ADDRESS); // TransactionHandle

        // GetTransactionInformation does not work before Windows Vista / Windows Server 2008
        getTransactionInformation = optionalFunctionMethodHandle(linker, symbolLookup, "GetTransactionInformation", ValueLayout.JAVA_BOOLEAN,
                ValueLayout.ADDRESS, // TransactionHandle
                ValueLayout.ADDRESS, // Outcome
                ValueLayout.ADDRESS, // IsolationLevel
                ValueLayout.ADDRESS, // IsolationFlags
                ValueLayout.ADDRESS, // Timeout
                ValueLayout.JAVA_INT, // BufferLength
                ValueLayout.ADDRESS); // Description
    }

    @Override
    public HANDLE CreateTransaction(
            NullPointer lpSecurityAttributes,
            NullPointer UOW, // NOSONAR
            int CreateOptions, // NOSONAR
            int IsolationLevel, // NOSONAR
            int IsolationFlags, // NOSONAR
            int Timeout, // NOSONAR
            StringPointer Description) { // NOSONAR

        MethodHandle createTransactionHandle = createTransaction.orElseThrow(UnsupportedOperationException::new);
        try {
            MemorySegment result = (MemorySegment) createTransactionHandle.invokeExact(
                    segment(lpSecurityAttributes),
                    segment(lpSecurityAttributes),
                    CreateOptions,
                    IsolationLevel,
                    IsolationFlags,
                    Timeout,
                    segment(Description));
            return new HANDLE(result);
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
            HANDLE TransactionHandle) { // NOSONAR

        MethodHandle commitTransactionHandle = commitTransaction.orElseThrow(UnsupportedOperationException::new);
        try {
            return (boolean) commitTransactionHandle.invokeExact(
                    TransactionHandle.segment());
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
            HANDLE TransactionHandle) { // NOSONAR

        MethodHandle rollbackTransactionHandle = rollbackTransaction.orElseThrow(UnsupportedOperationException::new);
        try {
            return (boolean) rollbackTransactionHandle.invokeExact(
                    TransactionHandle.segment());
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
            HANDLE TransactionHandle, // NOSONAR
            IntPointer Outcome, // NOSONAR
            IntPointer IsolationLevel, // NOSONAR
            IntPointer IsolationFlags, // NOSONAR
            IntPointer Timeout, // NOSONAR
            int BufferLength, // NOSONAR
            StringPointer Description) { // NOSONAR

        MethodHandle getTransactionInformationHandle = getTransactionInformation.orElseThrow(UnsupportedOperationException::new);
        try {
            return (boolean) getTransactionInformationHandle.invokeExact(
                    TransactionHandle.segment(),
                    segment(Outcome),
                    segment(IsolationLevel),
                    segment(IsolationFlags),
                    segment(Timeout),
                    BufferLength,
                    segment(Description));
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public boolean isGetTransactionInformationEnabled() {
        return getTransactionInformation.isPresent();
    }
}
