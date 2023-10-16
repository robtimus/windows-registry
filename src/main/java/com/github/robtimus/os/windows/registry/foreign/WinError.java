/*
 * WinError.java
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
public final class WinError {

    public static final int ERROR_SUCCESS = 0;
    public static final int ERROR_FILE_NOT_FOUND = 2;
    public static final int ERROR_ACCESS_DENIED = 5;
    public static final int ERROR_INVALID_HANDLE = 6;
    public static final int ERROR_BAD_NETPATH = 53;
    public static final int ERROR_ALREADY_EXISTS = 183;
    public static final int ERROR_MORE_DATA = 234;
    public static final int ERROR_NO_MORE_ITEMS = 259;
    public static final int ERROR_KEY_DELETED = 1018;

    private WinError() {
    }
}
