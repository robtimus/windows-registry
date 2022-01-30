/*
 * DWordValueTest.java
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import com.sun.jna.platform.win32.WinNT;

@SuppressWarnings("nls")
class DWordValueTest {

    @Nested
    @DisplayName("value")
    class Value {

        @Test
        @DisplayName("from value without byte order")
        void testFromValueWithoutByteOrder() {
            DWordValue value = DWordValue.of("test", 16909060);

            assertEquals(16909060, value.value());
        }

        @Test
        @DisplayName("from value with little-endian byte order")
        void testFromValueWithLittleEndianByteOrder() {
            DWordValue value = DWordValue.littleEndianOf("test", 16909060);

            assertEquals(16909060, value.value());
        }

        @Test
        @DisplayName("from value with big-endian byte order")
        void testFromValueWithBigEndianByteOrder() {
            DWordValue value = DWordValue.bigEndianOf("test", 16909060);

            assertEquals(16909060, value.value());
        }

        @Nested
        @DisplayName("from bytes")
        class FromBytes {

            @Test
            @DisplayName("REG_DWORD_LITTLE_ENDIAN")
            void testLittleEndian() {
                byte[] bytes = { 1, 2, 3, 4 };
                DWordValue value = new DWordValue("test", WinNT.REG_DWORD_LITTLE_ENDIAN, bytes);

                assertEquals(67305985, value.value());
            }

            @Test
            @DisplayName("REG_DWORD_BIG_ENDIAN")
            void testBigEndian() {
                byte[] bytes = { 1, 2, 3, 4 };
                DWordValue value = new DWordValue("test", WinNT.REG_DWORD_BIG_ENDIAN, bytes);

                assertEquals(16909060, value.value());
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
            @DisplayName("default endian")
            void testDefaultEndian() {
                byte[] bytes = { 1, 2, 3, 4 };
                DWordValue value = DWordValue.of("test", 67305985);

                assertArrayEquals(bytes, value.rawData());
            }

            @Test
            @DisplayName("little-endian")
            void testLittleEndian() {
                byte[] bytes = { 1, 2, 3, 4 };
                DWordValue value = DWordValue.littleEndianOf("test", 67305985);

                assertArrayEquals(bytes, value.rawData());
            }

            @Test
            @DisplayName("big-endian")
            void testBigEndian() {
                byte[] bytes = { 1, 2, 3, 4 };
                DWordValue value = DWordValue.bigEndianOf("test", 16909060);

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
                DWordValue value = new DWordValue("test", WinNT.REG_DWORD_BIG_ENDIAN, bytes);

                assertArrayEquals(bytes, value.rawData());
            }

            @Test
            @DisplayName("REG_DWORD_LITTLE_ENDIAN")
            void testLittleEndian() {
                byte[] bytes = { 1, 2, 3, 4 };
                DWordValue value = new DWordValue("test", WinNT.REG_DWORD_LITTLE_ENDIAN, bytes);

                assertArrayEquals(bytes, value.rawData());
            }
        }
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("equalsArguments")
    @DisplayName("equals")
    void testEquals(DWordValue value, Object other, boolean expected) {
        assertEquals(expected, value.equals(other));
    }

    static Arguments[] equalsArguments() {
        byte[] data = { 1, 2, 3, 4, };
        DWordValue value = new DWordValue("test", WinNT.REG_DWORD_LITTLE_ENDIAN, data);

        return new Arguments[] {
                arguments(value, value, true),
                arguments(value, new DWordValue("test", WinNT.REG_DWORD_LITTLE_ENDIAN, data), true),
                arguments(value, DWordValue.of("test", 67305985), true),
                arguments(value, new DWordValue("test", WinNT.REG_DWORD_BIG_ENDIAN, data), false),
                arguments(value, new DWordValue("test2", WinNT.REG_DWORD_LITTLE_ENDIAN, data), false),
                arguments(value, DWordValue.of("test", 67305984), false),
                arguments(value, "foo", false),
                arguments(value, null, false),
        };
    }

    @Test
    @DisplayName("hashCode")
    void testHashCode() {
        DWordValue value = DWordValue.of("test", 123456);

        assertEquals(value.hashCode(), value.hashCode());
        assertEquals(value.hashCode(), DWordValue.of("test", 123456).hashCode());
    }
}
