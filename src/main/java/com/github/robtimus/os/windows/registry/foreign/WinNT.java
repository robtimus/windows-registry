/*
 * WinNT.java
 * Copyright 2023 Rob Spoor
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

@SuppressWarnings("javadoc")
public final class WinNT {

    public static final int READ_CONTROL = 0x00020000;
    public static final int SYNCHRONIZE = 0x00100000;

    public static final int STANDARD_RIGHTS_READ = READ_CONTROL;
    public static final int STANDARD_RIGHTS_WRITE = READ_CONTROL;
    public static final int STANDARD_RIGHTS_ALL = 0x001F0000;

    public static final int KEY_QUERY_VALUE = 0x0001;
    public static final int KEY_SET_VALUE = 0x0002;
    public static final int KEY_CREATE_SUB_KEY = 0x0004;
    public static final int KEY_ENUMERATE_SUB_KEYS = 0x0008;
    public static final int KEY_NOTIFY = 0x0010;
    public static final int KEY_CREATE_LINK = 0x0020;

    public static final int KEY_READ = (STANDARD_RIGHTS_READ | KEY_QUERY_VALUE | KEY_ENUMERATE_SUB_KEYS | KEY_NOTIFY) & ~SYNCHRONIZE;

    public static final int REG_OPTION_NON_VOLATILE = 0x00000000;
    public static final int REG_CREATED_NEW_KEY = 0x00000001;
    public static final int REG_OPENED_EXISTING_KEY = 0x00000002;

    public static final int REG_NONE = 0;
    public static final int REG_SZ = 1;
    public static final int REG_EXPAND_SZ = 2;
    public static final int REG_BINARY = 3;
    public static final int REG_DWORD = 4;
    public static final int REG_DWORD_LITTLE_ENDIAN = 4;
    public static final int REG_DWORD_BIG_ENDIAN = 5;
    public static final int REG_LINK = 6;
    public static final int REG_MULTI_SZ = 7;
    public static final int REG_RESOURCE_LIST = 8;
    public static final int REG_FULL_RESOURCE_DESCRIPTOR = 9;
    public static final int REG_RESOURCE_REQUIREMENTS_LIST = 10;
    public static final int REG_QWORD = 11;
    public static final int REG_QWORD_LITTLE_ENDIAN = 11;

    private WinNT() {
    }

    /*
     * typedef enum _TRANSACTION_OUTCOME:
     *   TransactionOutcomeUndetermined = 1,
     *   TransactionOutcomeCommitted,
     *   TransactionOutcomeAborted
     */
    public static final class TRANSACTION_OUTCOME { // NOSONAR

        public static final int TransactionOutcomeUndetermined = 1; // NOSONAR
        public static final int TransactionOutcomeCommitted = 2; // NOSONAR
        public static final int TransactionOutcomeAborted = 3; // NOSONAR

        private TRANSACTION_OUTCOME() {
        }
    }
}
