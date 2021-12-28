/*
 * DWordRegistryValueTest.java
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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import java.nio.ByteOrder;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.junit.jupiter.params.provider.MethodSource;
import com.sun.jna.platform.win32.WinNT;

@SuppressWarnings("nls")
class DWordRegistryValueTest {

    @Nested
    @DisplayName("value")
    class Value {

        @Test
        @DisplayName("from value without byte order")
        void testFromValueWithoutByteOrder() {
            DWordRegistryValue value = new DWordRegistryValue("test", 16909060);

            assertEquals(16909060, value.value());
        }

        @ParameterizedTest
        @ArgumentsSource(ByteOrderProvider.class)
        @DisplayName("from value with byte order")
        void testFromValueWithoutByteOrder(ByteOrder byteOrder) {
            DWordRegistryValue value = new DWordRegistryValue("test", 16909060, byteOrder);

            assertEquals(16909060, value.value());
        }

        @Nested
        @DisplayName("from bytes")
        class FromBytes {

            @Test
            @DisplayName("REG_DWORD_BIG_ENDIAN")
            void testBigEndian() {
                byte[] bytes = { 1, 2, 3, 4 };
                DWordRegistryValue value = new DWordRegistryValue("test", WinNT.REG_DWORD_BIG_ENDIAN, bytes);

                assertEquals(16909060, value.value());
            }

            @Test
            @DisplayName("REG_DWORD_LITTLE_ENDIAN")
            void testLittleEndian() {
                byte[] bytes = { 1, 2, 3, 4 };
                DWordRegistryValue value = new DWordRegistryValue("test", WinNT.REG_DWORD_LITTLE_ENDIAN, bytes);

                assertEquals(67305985, value.value());
            }
        }
    }

    @Nested
    @DisplayName("rawData")
    class RawData {

        @Nested
        @DisplayName("from int")
        class FromInt {

            @Test
            @DisplayName("BigEndian")
            void testBigEndian() {
                byte[] bytes = { 1, 2, 3, 4 };
                DWordRegistryValue value = new DWordRegistryValue("test", 16909060, ByteOrder.BIG_ENDIAN);

                assertArrayEquals(bytes, value.rawData());
            }

            @Test
            @DisplayName("LittleEndian")
            void testLittleEndian() {
                byte[] bytes = { 1, 2, 3, 4 };
                DWordRegistryValue value = new DWordRegistryValue("test", 67305985, ByteOrder.LITTLE_ENDIAN);

                assertArrayEquals(bytes, value.rawData());
            }
        }

        @Nested
        @DisplayName("from bytes")
        class FromBytes {

            @Test
            @DisplayName("REG_DWORD_BIG_ENDIAN")
            void testBigEndian() {
                byte[] bytes = { 1, 2, 3, 4 };
                DWordRegistryValue value = new DWordRegistryValue("test", WinNT.REG_DWORD_BIG_ENDIAN, bytes);

                assertArrayEquals(bytes, value.rawData());
            }

            @Test
            @DisplayName("REG_DWORD_LITTLE_ENDIAN")
            void testLittleEndian() {
                byte[] bytes = { 1, 2, 3, 4 };
                DWordRegistryValue value = new DWordRegistryValue("test", WinNT.REG_DWORD_LITTLE_ENDIAN, bytes);

                assertArrayEquals(bytes, value.rawData());
            }
        }
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("equalsArguments")
    @DisplayName("equals")
    void testEquals(DWordRegistryValue value, Object other, boolean expected) {
        assertEquals(expected, value.equals(other));
    }

    static Arguments[] equalsArguments() {
        byte[] data = { 1, 2, 3, 4, };
        DWordRegistryValue value = new DWordRegistryValue("test", WinNT.REG_DWORD_LITTLE_ENDIAN, data);

        return new Arguments[] {
                arguments(value, value, true),
                arguments(value, new DWordRegistryValue("test", WinNT.REG_DWORD_LITTLE_ENDIAN, data), true),
                arguments(value, new DWordRegistryValue("test", 67305985), true),
                arguments(value, new DWordRegistryValue("test", WinNT.REG_DWORD_BIG_ENDIAN, data), false),
                arguments(value, new DWordRegistryValue("test2", WinNT.REG_DWORD_LITTLE_ENDIAN, data), false),
                arguments(value, new DWordRegistryValue("test", 67305984), false),
                arguments(value, "foo", false),
                arguments(value, null, false),
        };
    }

    @Test
    @DisplayName("hashCode")
    void testHashCode() {
        DWordRegistryValue value = new DWordRegistryValue("test", 123456);

        assertEquals(value.hashCode(), value.hashCode());
        assertEquals(value.hashCode(), new DWordRegistryValue("test", 123456).hashCode());
    }

    private static final class ByteOrderProvider implements ArgumentsProvider {

        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext context) throws Exception {
            return Stream.of(ByteOrder.BIG_ENDIAN, ByteOrder.LITTLE_ENDIAN)
                    .map(Arguments::arguments);
        }
    }
}
