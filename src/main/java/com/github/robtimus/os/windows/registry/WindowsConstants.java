/*
 * WindowsConstants.java
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

package com.github.robtimus.os.windows.registry;

import java.lang.foreign.MemorySegment;

final class WindowsConstants {

    static final int ERROR_SUCCESS = 0;
    static final int ERROR_FILE_NOT_FOUND = 2;
    static final int ERROR_ACCESS_DENIED = 5;
    static final int ERROR_INVALID_HANDLE = 6;
    static final int ERROR_BAD_NETPATH = 53;
    static final int ERROR_ALREADY_EXISTS = 183;
    static final int ERROR_MORE_DATA = 234;
    static final int ERROR_NO_MORE_ITEMS = 259;
    static final int ERROR_KEY_DELETED = 1018;

    static final int READ_CONTROL = 0x00020000;
    static final int SYNCHRONIZE = 0x00100000;

    static final int STANDARD_RIGHTS_READ = READ_CONTROL;
    static final int STANDARD_RIGHTS_WRITE = READ_CONTROL;
    static final int STANDARD_RIGHTS_ALL = 0x001F0000;

    static final int KEY_QUERY_VALUE = 0x0001;
    static final int KEY_SET_VALUE = 0x0002;
    static final int KEY_CREATE_SUB_KEY = 0x0004;
    static final int KEY_ENUMERATE_SUB_KEYS = 0x0008;
    static final int KEY_NOTIFY = 0x0010;
    static final int KEY_CREATE_LINK = 0x0020;

    static final int KEY_READ = (STANDARD_RIGHTS_READ | KEY_QUERY_VALUE | KEY_ENUMERATE_SUB_KEYS | KEY_NOTIFY) & ~SYNCHRONIZE;

    static final int REG_OPTION_NON_VOLATILE = 0x00000000;
    static final int REG_CREATED_NEW_KEY = 0x00000001;
    static final int REG_OPENED_EXISTING_KEY = 0x00000002;

    static final int REG_NONE = 0;
    static final int REG_SZ = 1;
    static final int REG_EXPAND_SZ = 2;
    static final int REG_BINARY = 3;
    static final int REG_DWORD = 4;
    static final int REG_DWORD_LITTLE_ENDIAN = 4;
    static final int REG_DWORD_BIG_ENDIAN = 5;
    static final int REG_LINK = 6;
    static final int REG_MULTI_SZ = 7;
    static final int REG_RESOURCE_LIST = 8;
    static final int REG_FULL_RESOURCE_DESCRIPTOR = 9;
    static final int REG_RESOURCE_REQUIREMENTS_LIST = 10;
    static final int REG_QWORD = 11;
    static final int REG_QWORD_LITTLE_ENDIAN = 11;

    static final MemorySegment HKEY_CLASSES_ROOT = hKey(0x80000000);
    static final MemorySegment HKEY_CURRENT_USER = hKey(0x80000001);
    static final MemorySegment HKEY_LOCAL_MACHINE = hKey(0x80000002);
    static final MemorySegment HKEY_USERS = hKey(0x80000003);
    static final MemorySegment HKEY_CURRENT_CONFIG = hKey(0x80000005);

    static final int TRANSACTION_DO_NOT_PROMOTE = 0x00000001;

    private WindowsConstants() {
    }

    private static MemorySegment hKey(int address) {
        return MemorySegment.ofAddress(address);
    }
}
