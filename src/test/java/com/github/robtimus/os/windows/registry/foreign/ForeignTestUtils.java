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

import static com.github.robtimus.os.windows.registry.foreign.CaptureState.LAST_ERROR_LAYOUT;
import static com.github.robtimus.os.windows.registry.foreign.CaptureState.LAST_ERROR_OFFSET;
import static com.github.robtimus.os.windows.registry.foreign.ForeignUtils.toByteArray;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.Map;
import org.mockito.ArgumentMatcher;
import org.mockito.internal.matchers.ContainsExtraTypeInfo;
import org.mockito.internal.matchers.text.ValuePrinter;

@SuppressWarnings("javadoc")
public final class ForeignTestUtils {

    public static final Arena ALLOCATOR = ForeignUtils.ARENA;

    private static int hKeyValue = 0;

    private ForeignTestUtils() {
    }

    public static MemorySegment newHKEY() {
        MemorySegment hKey = ALLOCATOR.allocateFrom(ValueLayout.JAVA_LONG, ++hKeyValue);

        assertHKEYNotEquals(WinReg.HKEY_CLASSES_ROOT, hKey);
        assertHKEYNotEquals(WinReg.HKEY_CURRENT_USER, hKey);
        assertHKEYNotEquals(WinReg.HKEY_LOCAL_MACHINE, hKey);
        assertHKEYNotEquals(WinReg.HKEY_USERS, hKey);
        assertHKEYNotEquals(WinReg.HKEY_CURRENT_CONFIG, hKey);
        return hKey;
    }

    private static void assertHKEYNotEquals(MemorySegment unexpected, MemorySegment actual) {
        assertNotEquals(unexpected.address(), actual.address());
    }

    public static void setHKEY(MemorySegment reference, MemorySegment hKey) {
        reference.set(ValueLayout.ADDRESS, 0, hKey);
    }

    public static void copyData(MemorySegment source, MemorySegment dest) {
        MemorySegment.copy(source, 0, dest, 0, source.byteSize());
    }

    public static MemorySegment notNULL() {
        return argThat(new NotNullMatcher());
    }

    private static final class NotNullMatcher implements ArgumentMatcher<MemorySegment> {

        @Override
        public boolean matches(MemorySegment argument) {
            return !MemorySegment.NULL.equals(argument);
        }

        @Override
        @SuppressWarnings("nls")
        public String toString() {
            return "not NULL";
        }
    }

    public static MemorySegment isNULL() {
        return eq(MemorySegment.NULL);
    }

    public static MemorySegment eqPointer(String value) {
        return argThat(new StringPointerMatcher(value));
    }

    private static final class StringPointerMatcher implements ArgumentMatcher<MemorySegment>, ContainsExtraTypeInfo {

        // Matchers used in verify steps are called with memory segments that are no longer in scope
        // Therefore, cache their values
        private static final Map<MemorySegment, String> VALUES = new IdentityHashMap<>();

        private final String wanted;

        private StringPointerMatcher(String wanted) {
            this.wanted = wanted;
        }

        @Override
        public boolean matches(MemorySegment argument) {
            if (argument == null) {
                return wanted == null;
            }
            if (wanted == null) {
                return MemorySegment.NULL.equals(argument);
            }
            if (argument.scope().isAlive()) {
                String pointerValue = WString.getString(argument);
                VALUES.put(argument, pointerValue);
                return wanted.equals(pointerValue);
            }
            String pointerValue = VALUES.get(argument);
            return wanted.equals(pointerValue);
        }

        @Override
        @SuppressWarnings("nls")
        public String toString() {
            return "memory segment containing " + ValuePrinter.print(wanted);
        }

        @Override
        @SuppressWarnings("nls")
        public String toStringWithType(String className) {
            return "(%s) %s".formatted(className, ValuePrinter.print(wanted));
        }

        @Override
        public boolean typeMatches(Object target) {
            return target instanceof MemorySegment;
        }

        @Override
        public Object getWanted() {
            return wanted;
        }
    }

    public static MemorySegment eqBytes(MemorySegment value) {
        return argThat(new BytesMatcher(value));
    }

    private static final class BytesMatcher implements ArgumentMatcher<MemorySegment>, ContainsExtraTypeInfo {

        // Matchers used in verify steps are called with memory segments that are no longer in scope
        // Therefore, cache their values
        private static final Map<MemorySegment, byte[]> VALUES = new IdentityHashMap<>();

        private final byte[] wanted;

        private BytesMatcher(MemorySegment wanted) {
            this.wanted = toByteArray(wanted);
        }

        @Override
        public boolean matches(MemorySegment argument) {
            if (argument == null) {
                return wanted == null;
            }
            if (argument.scope().isAlive()) {
                byte[] pointerValue = toByteArray(argument);
                VALUES.put(argument, pointerValue);
                return Arrays.equals(wanted, pointerValue);
            }
            byte[] pointerValue = VALUES.get(argument);
            return Arrays.equals(wanted, pointerValue);
        }

        @Override
        @SuppressWarnings("nls")
        public String toString() {
            return "memory segment containing bytes " + ValuePrinter.print(wantedAsHex());
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
            return target instanceof MemorySegment;
        }

        @Override
        public Object getWanted() {
            return wanted;
        }
    }

    public static int eqSize(MemorySegment segment) {
        return eq(Math.toIntExact(segment.byteSize()));
    }

    public static void setLastError(MemorySegment captureState, int errorCode) {
        captureState.set(LAST_ERROR_LAYOUT, LAST_ERROR_OFFSET, errorCode);
    }
}
