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

import com.github.robtimus.os.windows.registry.foreign.WinDef.HANDLE;

@SuppressWarnings("javadoc")
public interface KtmW32 {

    KtmW32 INSTANCE = new KtmW32Impl();

    HANDLE CreateTransaction/* NOSONAR */(
            NullPointer /* SECURITY_ATTRIBUTES */ lpSecurityAttributes, // no need to implement SECURITY_ATTRIBUTES as they are never used
            NullPointer /* LPGUID */ UOW, // NOSONAR
            int CreateOptions, // NOSONAR
            int IsolationLevel, // NOSONAR
            int IsolationFlags, // NOSONAR
            int Timeout, // NOSONAR
            StringPointer Description); // NOSONAR

    boolean isCreateTransactionEnabled();

    boolean CommitTransaction/* NOSONAR */(
            HANDLE TransactionHandle); // NOSONAR

    boolean isCommitTransactionEnabled();

    boolean RollbackTransaction/* NOSONAR */(
            HANDLE TransactionHandle); // NOSONAR

    boolean isRollbackTransactionEnabled();

    boolean GetTransactionInformation/* NOSONAR */(
            HANDLE TransactionHandle, // NOSONAR
            IntPointer Outcome, // NOSONAR
            IntPointer IsolationLevel, // NOSONAR
            IntPointer IsolationFlags, // NOSONAR
            IntPointer Timeout, // NOSONAR
            int BufferLength, // NOSONAR
            StringPointer Description); // NOSONAR

    boolean isGetTransactionInformationEnabled();
}
