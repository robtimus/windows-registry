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

import java.lang.foreign.MemorySegment;

@SuppressWarnings("javadoc")
public interface KtmW32 {

    KtmW32 INSTANCE = new KtmW32Impl();

    // The following functions all require GetLastError() to be called to retrieve any error that occurred

    /* HANDLE */ MemorySegment CreateTransaction/* NOSONAR */(
            /* LPSECURITY_ATTRIBUTES */ MemorySegment lpSecurityAttributes,
            /* LPGUID */ MemorySegment UOW, // NOSONAR
            /* DWORD */ int CreateOptions, // NOSONAR
            /* DWORD */ int IsolationLevel, // NOSONAR
            /* DWORD */ int IsolationFlags, // NOSONAR
            /* DWORD */ int Timeout, // NOSONAR
            /* LPWSTR */ MemorySegment Description, // NOSONAR
            MemorySegment captureState);

    boolean isCreateTransactionEnabled();

    /* BOOL */ boolean CommitTransaction/* NOSONAR */(
            /* HANDLE */ MemorySegment TransactionHandle, // NOSONAR
            MemorySegment captureState);

    boolean isCommitTransactionEnabled();

    /* BOOL */ boolean RollbackTransaction/* NOSONAR */(
            /* HANDLE */ MemorySegment TransactionHandle, // NOSONAR
            MemorySegment captureState);

    boolean isRollbackTransactionEnabled();

    /* BOOL */ boolean GetTransactionInformation/* NOSONAR */(
            /* HANDLE */ MemorySegment TransactionHandle, // NOSONAR
            /* PDWORD */ MemorySegment Outcome, // NOSONAR
            /* PDWORD */ MemorySegment IsolationLevel, // NOSONAR
            /* PDWORD */ MemorySegment IsolationFlags, // NOSONAR
            /* PDWORD */ MemorySegment Timeout, // NOSONAR
            /* DWORD */ int BufferLength, // NOSONAR
            /* LPWSTR */ MemorySegment Description, // NOSONAR
            MemorySegment captureState);

    boolean isGetTransactionInformationEnabled();
}
