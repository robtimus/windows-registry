/*
 * WinReg.java
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

import java.lang.foreign.MemorySegment;
import com.github.robtimus.os.windows.registry.foreign.WinDef.HKEY;

@SuppressWarnings("javadoc")
public final class WinReg {

    public static final HKEY HKEY_CLASSES_ROOT = hKey(0x80000000);
    public static final HKEY HKEY_CURRENT_USER = hKey(0x80000001);
    public static final HKEY HKEY_LOCAL_MACHINE = hKey(0x80000002);
    public static final HKEY HKEY_USERS = hKey(0x80000003);
    public static final HKEY HKEY_CURRENT_CONFIG = hKey(0x80000005);

    private WinReg() {
    }

    private static HKEY hKey(int address) {
        MemorySegment segment = MemorySegment.ofAddress(address);
        return new HKEY(segment);
    }
}
