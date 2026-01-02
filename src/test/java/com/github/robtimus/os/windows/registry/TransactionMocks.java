/*
 * TransactionMocks.java
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

import static com.github.robtimus.os.windows.registry.ForeignTestUtils.eqPointer;
import static com.github.robtimus.os.windows.registry.ForeignTestUtils.isNULL;
import static com.github.robtimus.os.windows.registry.RegistryTestBase.arena;
import static com.github.robtimus.os.windows.registry.RegistryTestBase.kernel32;
import static com.github.robtimus.os.windows.registry.RegistryTestBase.ktmW32;
import static com.github.robtimus.os.windows.registry.foreign.Kernel32.CloseHandle;
import static com.github.robtimus.os.windows.registry.foreign.KtmW32.CommitTransaction;
import static com.github.robtimus.os.windows.registry.foreign.KtmW32.CreateTransaction;
import static com.github.robtimus.os.windows.registry.foreign.KtmW32.GetTransactionInformation;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.notNull;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.time.Duration;
import java.util.List;
import java.util.stream.IntStream;
import com.github.robtimus.os.windows.registry.foreign.KtmTypes;

final class TransactionMocks {

    private TransactionMocks() {
    }

    static Transaction createTransaction() {
        return createTransaction(null);
    }

    static Transaction createTransaction(String description) {
        return createTransaction(Duration.ofMillis(100), description);
    }

    static Transaction createTransaction(Duration timeout, String description) {
        mockCreateTransaction(timeout, description);

        return Transaction.create(timeout, description);
    }

    static MemorySegment mockCreateTransaction(Duration timeout, String description) {
        MemorySegment handle = arena.allocate(0);

        mockCreateTransaction(handle, timeout, description);

        return handle;
    }

    static void mockCreateTransaction(MemorySegment handle, Duration timeout, String description) {

        int timeoutInMillis = Math.toIntExact(timeout.toMillis());

        ktmW32.when(() -> CreateTransaction(isNULL(), isNULL(), eq(KtmTypes.TRANSACTION_DO_NOT_PROMOTE), eq(0), eq(0), eq(timeoutInMillis),
                eqPointer(description), notNull()))
                .thenReturn(handle);
    }

    static List<MemorySegment> mockCreateTransactions(Duration timeout, String description, int count) {
        List<MemorySegment> handles = IntStream.range(0, count)
                .mapToObj(_ -> arena.allocate(0))
                .toList();

        mockCreateTransactions(handles, timeout, description);

        return handles;
    }

    static void mockCreateTransactions(List<MemorySegment> handles, Duration timeout, String description) {

        int timeoutInMillis = Math.toIntExact(timeout.toMillis());

        ktmW32.when(() -> CreateTransaction(isNULL(), isNULL(), eq(KtmTypes.TRANSACTION_DO_NOT_PROMOTE), eq(0), eq(0), eq(timeoutInMillis),
                eqPointer(description), notNull()))
                .thenReturn(handles.getFirst(), (Object[]) handles.subList(0, handles.size()).toArray(MemorySegment[]::new));
    }

    static void mockCloseHandle(MemorySegment handle) {
        kernel32.when(() -> CloseHandle(eq(handle), notNull())).thenReturn(true);
    }

    static void mockCommitTransaction(MemorySegment handle) {
        ktmW32.when(() -> CommitTransaction(eq(handle), notNull())).thenReturn(true);
    }

    static void mockGetTransactionStatus(MemorySegment handle, int outcome) {
        ktmW32.when(() -> GetTransactionInformation(eq(handle), notNull(), isNULL(), isNULL(), isNULL(), eq(0), isNULL(), notNull()))
                .thenAnswer(i -> {
                    MemorySegment outcomeSegment = i.getArgument(1);
                    outcomeSegment.set(ValueLayout.JAVA_INT, 0, outcome);

                    return true;
                });
    }
}
