/*
 * StringUtils.java
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

package com.github.robtimus.os.windows.registry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.win32.W32APITypeMapper;

final class StringUtils {

    private StringUtils() {
    }

    static String toHexString(byte[] array) {
        StringBuilder sb = new StringBuilder(2 + array.length * 2);
        sb.append("0x"); //$NON-NLS-1$
        for (byte b : array) {
            int high = (b >> 4) & 0xF;
            int low = b & 0xF;
            sb.append(Character.forDigit(high, 16));
            sb.append(Character.forDigit(low, 16));
        }
        return sb.toString();
    }

    static String toString(byte[] data, int dataLength) {
        try (Memory memory = new Memory((long) dataLength + Native.WCHAR_SIZE)) {
            memory.clear();
            memory.write(0, data, 0, dataLength);
            return W32APITypeMapper.DEFAULT == W32APITypeMapper.UNICODE
                    ? memory.getWideString(0)
                    : memory.getString(0);
        }
    }

    static byte[] fromString(String value) {
        if (W32APITypeMapper.DEFAULT == W32APITypeMapper.UNICODE) {
            try (Memory memory = new Memory((value.length() + 1L) * Native.WCHAR_SIZE)) {
                memory.setWideString(0, value);
                return memory.getByteArray(0, (int) memory.size());
            }
        }
        try (Memory memory = new Memory(value.length() + 1L)) {
            memory.setString(0, value);
            return memory.getByteArray(0, (int) memory.size());
        }
    }

    static List<String> toStringList(byte[] data, int dataLength) {
        List<String> result = new ArrayList<>();
        int offset = 0;
        try (Memory memory = new Memory(dataLength + 2L * Native.WCHAR_SIZE)) {
            memory.clear();
            memory.write(0, data, 0, dataLength);

            while (offset < memory.size()) {
                String value;
                if (W32APITypeMapper.DEFAULT == W32APITypeMapper.UNICODE) {
                    value = memory.getWideString(offset);
                    offset += value.length() * Native.WCHAR_SIZE;
                    offset += Native.WCHAR_SIZE;
                } else {
                    value = memory.getString(offset);
                    offset += value.length();
                    offset += 1;
                }

                if (value.length() == 0) {
                    // A sequence of null-terminated strings, terminated by an empty string (\0).
                    // => The first empty string terminates the string list
                    break;
                }

                result.add(value);
            }
            return Collections.unmodifiableList(result);
        }
    }

    static byte[] fromStringList(List<String> values) {
        int charwidth = W32APITypeMapper.DEFAULT == W32APITypeMapper.UNICODE ? Native.WCHAR_SIZE : 1;

        int size = 0;
        for (String s : values) {
            size += s.length() * charwidth;
            size += charwidth;
        }
        size += charwidth;

        int offset = 0;
        try (Memory memory = new Memory(size)) {
            memory.clear();
            for (String s : values) {
                if (W32APITypeMapper.DEFAULT == W32APITypeMapper.UNICODE) {
                    memory.setWideString(offset, s);
                } else {
                    memory.setString(offset, s);
                }
                offset += s.length() * charwidth;
                offset += charwidth;
            }
            return memory.getByteArray(0, size);
        }
    }
}
