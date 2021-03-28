/*
 * Advapi32Extended.java
 * Copyright 2021 Rob Spoor
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

package com.github.robtimus.os.windows.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.Structure.FieldOrder;
import com.sun.jna.platform.win32.Advapi32;
import com.sun.jna.platform.win32.Winsvc.ChangeServiceConfig2Info;
import com.sun.jna.platform.win32.Winsvc.SC_HANDLE;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.W32APIOptions;
import com.sun.jna.win32.W32APITypeMapper;

interface Advapi32Extended extends Advapi32 {
    Advapi32Extended INSTANCE = Native.load("Advapi32", Advapi32Extended.class, W32APIOptions.DEFAULT_OPTIONS); //$NON-NLS-1$

    boolean QueryServiceConfig(SC_HANDLE hService, QUERY_SERVICE_CONFIG lpServiceConfig, int cbBufSize, IntByReference pcbBytesNeeded);

    @FieldOrder({ "dwServiceType", "dwStartType", "dwErrorControl", "lpBinaryPathName", "lpLoadOrderGroup", "dwTagId", "lpDependencies",
            "lpServiceStartName", "lpDisplayName" })
    class QUERY_SERVICE_CONFIG extends Structure {
        public int dwServiceType;
        public int dwStartType;
        public int dwErrorControl;
        public String lpBinaryPathName;
        public String lpLoadOrderGroup;
        public int dwTagId;
        public Pointer lpDependencies;
        public String lpServiceStartName;
        public String lpDisplayName;

        public QUERY_SERVICE_CONFIG(Pointer p) {
            super(p, ALIGN_DEFAULT, W32APITypeMapper.DEFAULT);
        }

        public List<String> dependencies() {
            if (lpDependencies == null) {
                return Collections.emptyList();
            }

            List<String> result = new ArrayList<>();
            int offset = 0;
            while (true) {
                String s = W32APITypeMapper.DEFAULT == W32APITypeMapper.UNICODE
                        ? lpDependencies.getWideString(offset)
                        : lpDependencies.getString(offset);
                if (s == null || s.isEmpty()) {
                    break;
                }
                result.add(s);
                offset += W32APITypeMapper.DEFAULT == W32APITypeMapper.UNICODE
                        ? (s.length() + 1) * Native.WCHAR_SIZE
                        : s.length() + 1;
            }

            return Collections.unmodifiableList(result);
        }
    }

    @FieldOrder({"lpDescription"})
    public static class SERVICE_DESCRIPTION extends ChangeServiceConfig2Info {
        public String lpDescription;

        public SERVICE_DESCRIPTION(Pointer p) {
            super(p);
        }
    }

    @FieldOrder("fDelayedAutostart")
    class SERVICE_DELAYED_AUTO_START_INFO extends ChangeServiceConfig2Info {
        public boolean fDelayedAutostart;

        public SERVICE_DELAYED_AUTO_START_INFO(Pointer p) {
            super(p);
        }
    }
}
