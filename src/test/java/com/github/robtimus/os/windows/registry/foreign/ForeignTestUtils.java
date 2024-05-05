/*
 * ForeignTestUtils.java
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

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.argThat;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Map;
import java.util.WeakHashMap;
import org.mockito.ArgumentMatcher;
import org.mockito.internal.matchers.ContainsExtraTypeInfo;
import org.mockito.internal.matchers.text.ValuePrinter;
import com.github.robtimus.os.windows.registry.foreign.WinDef.HKEY;

@SuppressWarnings("javadoc")
public final class ForeignTestUtils {

    public static final Arena ALLOCATOR = ForeignUtils.ARENA;

    private static int hKeyValue = 0;

    private ForeignTestUtils() {
    }

    public static HKEY newHKEY() {
        MemorySegment segment = ALLOCATOR.allocateFrom(ValueLayout.JAVA_LONG, ++hKeyValue);
        HKEY hKey = new HKEY(segment);

        assertHKEYNotEquals(WinReg.HKEY_CLASSES_ROOT, hKey);
        assertHKEYNotEquals(WinReg.HKEY_CURRENT_USER, hKey);
        assertHKEYNotEquals(WinReg.HKEY_LOCAL_MACHINE, hKey);
        assertHKEYNotEquals(WinReg.HKEY_USERS, hKey);
        assertHKEYNotEquals(WinReg.HKEY_CURRENT_CONFIG, hKey);
        return hKey;
    }

    private static void assertHKEYNotEquals(HKEY unexpected, HKEY actual) {
        assertNotEquals(unexpected.segment().address(), actual.segment().address());
    }

    public static void setHKEY(HKEY.Reference reference, HKEY hKey) {
        reference.value(hKey);
    }

    public static void copyData(BytePointer source, BytePointer dest) {
        MemorySegment.copy(source.segment(), 0, dest.segment(), 0, source.size());
    }

    public static HKEY eqHKEY(HKEY value) {
        return argThat(new HKEYMatcher(value));
    }

    private static final class HKEYMatcher implements ArgumentMatcher<HKEY> {

        private final HKEY wanted;

        private HKEYMatcher(HKEY wanted) {
            this.wanted = wanted;
        }

        @Override
        public boolean matches(HKEY argument) {
            if (argument == null && wanted == null) {
                return true;
            }
            if (argument == null || wanted == null) {
                return false;
            }
            return argument.segment().equals(wanted.segment());
        }

        @Override
        public String toString() {
            return ValuePrinter.print(wanted);
        }
    }

    public static StringPointer eqPointer(String value) {
        return argThat(new StringPointerMatcher(value));
    }

    private static final class StringPointerMatcher implements ArgumentMatcher<StringPointer>, ContainsExtraTypeInfo {

        // Matchers used in verify steps are called with pointers that are no longer in scope
        // Therefore, cache their values
        private static final Map<StringPointer, String> VALUES = new WeakHashMap<>();

        private final String wanted;

        private StringPointerMatcher(String wanted) {
            this.wanted = wanted;
        }

        @Override
        public boolean matches(StringPointer argument) {
            if (argument == null) {
                return wanted == null;
            }
            if (argument.segment().scope().isAlive()) {
                String pointerValue = argument.value();
                VALUES.put(argument, pointerValue);
                return wanted.equals(pointerValue);
            }
            String pointerValue = VALUES.get(argument);
            return wanted.equals(pointerValue);
        }

        @Override
        @SuppressWarnings("nls")
        public String toString() {
            return "string pointer containing " + ValuePrinter.print(wanted);
        }

        @Override
        @SuppressWarnings("nls")
        public String toStringWithType(String className) {
            return "(%s) %s".formatted(className, ValuePrinter.print(wanted));
        }

        @Override
        public boolean typeMatches(Object target) {
            return target instanceof StringPointer;
        }

        @Override
        public Object getWanted() {
            return wanted;
        }
    }

    public static BytePointer eqPointer(BytePointer value) {
        return argThat(new BytePointerMatcher(value));
    }

    private static final class BytePointerMatcher implements ArgumentMatcher<BytePointer>, ContainsExtraTypeInfo {

        // Matchers used in verify steps are called with pointers that are no longer in scope
        // Therefore, cache their values
        private static final Map<BytePointer, byte[]> VALUES = new WeakHashMap<>();

        private final byte[] wanted;

        private BytePointerMatcher(BytePointer wanted) {
            this.wanted = wanted.toByteArray();
        }

        @Override
        public boolean matches(BytePointer argument) {
            if (argument == null) {
                return wanted == null;
            }
            if (argument.segment().scope().isAlive()) {
                byte[] pointerValue = argument.toByteArray();
                VALUES.put(argument, pointerValue);
                return Arrays.equals(wanted, pointerValue);
            }
            byte[] pointerValue = VALUES.get(argument);
            return Arrays.equals(wanted, pointerValue);
        }

        @Override
        @SuppressWarnings("nls")
        public String toString() {
            return "byte pointer containing " + ValuePrinter.print(wantedAsHex());
        }

        @Override
        @SuppressWarnings("nls")
        public String toStringWithType(String className) {
            return "(%s) %s".formatted(className, ValuePrinter.print(wantedAsHex()));
        }

        @SuppressWarnings("nls")
        private String wantedAsHex() {
            return "0x" + HexFormat.of().formatHex(wanted);
        }

        @Override
        public boolean typeMatches(Object target) {
            return target instanceof BytePointer;
        }

        @Override
        public Object getWanted() {
            return wanted;
        }
    }
}
