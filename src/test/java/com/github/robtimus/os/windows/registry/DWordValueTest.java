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

import static com.github.robtimus.os.windows.registry.RegistryValueTest.assertBytePointerEquals;
import static com.github.robtimus.os.windows.registry.RegistryValueTest.bytePointer;
import static com.github.robtimus.os.windows.registry.foreign.ForeignTestUtils.ALLOCATOR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import com.github.robtimus.os.windows.registry.foreign.BytePointer;
import com.github.robtimus.os.windows.registry.foreign.WinNT;

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
        @DisplayName("from byte pointer")
        class FromBytes {

            @Test
            @DisplayName("REG_DWORD_LITTLE_ENDIAN")
            void testLittleEndian() {
                BytePointer data = bytePointer(1, 2, 3, 4);
                DWordValue value = new DWordValue("test", WinNT.REG_DWORD_LITTLE_ENDIAN, data);

                assertEquals(67305985, value.value());
            }

            @Test
            @DisplayName("REG_DWORD_BIG_ENDIAN")
            void testBigEndian() {
                BytePointer data = bytePointer(1, 2, 3, 4);
                DWordValue value = new DWordValue("test", WinNT.REG_DWORD_BIG_ENDIAN, data);

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
                BytePointer data = bytePointer(1, 2, 3, 4);
                DWordValue value = DWordValue.of("test", 67305985);

                assertBytePointerEquals(data, value.rawData(ALLOCATOR));
            }

            @Test
            @DisplayName("little-endian")
            void testLittleEndian() {
                BytePointer data = bytePointer(1, 2, 3, 4);
                DWordValue value = DWordValue.littleEndianOf("test", 67305985);

                assertBytePointerEquals(data, value.rawData(ALLOCATOR));
            }

            @Test
            @DisplayName("big-endian")
            void testBigEndian() {
                BytePointer data = bytePointer(1, 2, 3, 4);
                DWordValue value = DWordValue.bigEndianOf("test", 16909060);

                assertBytePointerEquals(data, value.rawData(ALLOCATOR));
            }
        }

        @Nested
        @DisplayName("from byte pointer")
        class FromBytePointer {

            @Test
            @DisplayName("REG_DWORD_BIG_ENDIAN")
            void testBigEndian() {
                BytePointer data = bytePointer(1, 2, 3, 4);
                DWordValue value = new DWordValue("test", WinNT.REG_DWORD_BIG_ENDIAN, data);

                assertBytePointerEquals(data, value.rawData(ALLOCATOR));
            }

            @Test
            @DisplayName("REG_DWORD_LITTLE_ENDIAN")
            void testLittleEndian() {
                BytePointer data = bytePointer(1, 2, 3, 4);
                DWordValue value = new DWordValue("test", WinNT.REG_DWORD_LITTLE_ENDIAN, data);

                assertBytePointerEquals(data, value.rawData(ALLOCATOR));
            }
        }
    }

    @Nested
    @DisplayName("withName")
    class WithName {

        @Test
        @DisplayName("same name")
        void testSameName() {
            DWordValue value = DWordValue.of("test", 16909060);

            DWordValue otherValue = value.withName("test");

            assertEquals(value, otherValue);
        }

        @Test
        @DisplayName("different name")
        void testDifferentName() {
            DWordValue value = DWordValue.of("test", 16909060);

            DWordValue otherValue = value.withName("test2");

            assertNotEquals(value, otherValue);
            assertEquals("test2", otherValue.name());
            assertEquals(value.value(), otherValue.value());
        }
    }

    @Nested
    @DisplayName("withValue")
    class WithValue {

        @Nested
        @DisplayName("default endian")
        class DefaultEndian {

            @Test
            @DisplayName("same value")
            void testSameValue() {
                DWordValue value = DWordValue.of("test", 16909060);

                DWordValue otherValue = value.withValue(16909060);

                assertEquals(value, otherValue);
            }

            @Test
            @DisplayName("different value")
            void testDifferentValue() {
                DWordValue value = DWordValue.of("test", 16909060);

                DWordValue otherValue = value.withValue(67305985);

                assertNotEquals(value, otherValue);
                assertEquals(value.name(), otherValue.name());
                assertEquals(67305985, otherValue.value());
            }
        }

        @Nested
        @DisplayName("little-endian")
        class LittleEndian {

            @Test
            @DisplayName("same value")
            void testSameValue() {
                DWordValue value = DWordValue.littleEndianOf("test", 16909060);

                DWordValue otherValue = value.withValue(16909060);

                assertEquals(value, otherValue);
            }

            @Test
            @DisplayName("different value")
            void testDifferentValue() {
                DWordValue value = DWordValue.littleEndianOf("test", 16909060);

                DWordValue otherValue = value.withValue(67305985);

                assertNotEquals(value, otherValue);
                assertEquals(value.name(), otherValue.name());
                assertEquals(67305985, otherValue.value());
            }
        }

        @Nested
        @DisplayName("big-endian")
        class BigEndian {

            @Test
            @DisplayName("same value")
            void testSameValue() {
                DWordValue value = DWordValue.bigEndianOf("test", 16909060);

                DWordValue otherValue = value.withValue(16909060);

                assertEquals(value, otherValue);
            }

            @Test
            @DisplayName("different value")
            void testDifferentValue() {
                DWordValue value = DWordValue.bigEndianOf("test", 16909060);

                DWordValue otherValue = value.withValue(67305985);

                assertNotEquals(value, otherValue);
                assertEquals(value.name(), otherValue.name());
                assertEquals(67305985, otherValue.value());
            }
        }
    }

    @Nested
    @DisplayName("withLittleEndianValue")
    class WithLittleEndianValue {

        @Nested
        @DisplayName("default endian")
        class DefaultEndian {

            @Test
            @DisplayName("same value")
            void testSameValue() {
                DWordValue value = DWordValue.of("test", 16909060);

                DWordValue otherValue = value.withLittleEndianValue(16909060);

                assertEquals(value, otherValue);
            }

            @Test
            @DisplayName("different value")
            void testDifferentValue() {
                DWordValue value = DWordValue.of("test", 16909060);

                DWordValue otherValue = value.withLittleEndianValue(67305985);

                assertNotEquals(value, otherValue);
                assertEquals(value.name(), otherValue.name());
                assertEquals(67305985, otherValue.value());
            }

            @Test
            @DisplayName("byte-order only")
            void testByteOrderOnly() {
                DWordValue value = DWordValue.of("test", 16909060);

                DWordValue otherValue = value.withLittleEndianValue();

                assertEquals(value, otherValue);
            }
        }

        @Nested
        @DisplayName("little-endian")
        class LittleEndian {

            @Test
            @DisplayName("same value")
            void testSameValue() {
                DWordValue value = DWordValue.littleEndianOf("test", 16909060);

                DWordValue otherValue = value.withLittleEndianValue(16909060);

                assertEquals(value, otherValue);
            }

            @Test
            @DisplayName("different value")
            void testDifferentValue() {
                DWordValue value = DWordValue.littleEndianOf("test", 16909060);

                DWordValue otherValue = value.withLittleEndianValue(67305985);

                assertNotEquals(value, otherValue);
                assertEquals(value.name(), otherValue.name());
                assertEquals(67305985, otherValue.value());
            }

            @Test
            @DisplayName("byte-order only")
            void testByteOrderOnly() {
                DWordValue value = DWordValue.littleEndianOf("test", 16909060);

                DWordValue otherValue = value.withLittleEndianValue();

                assertEquals(value, otherValue);
            }
        }

        @Nested
        @DisplayName("big-endian")
        class BigEndian {

            @Test
            @DisplayName("same value")
            void testSameValue() {
                DWordValue value = DWordValue.bigEndianOf("test", 16909060);

                DWordValue otherValue = value.withLittleEndianValue(16909060);

                assertNotEquals(value, otherValue);
                assertEquals(value.name(), otherValue.name());
                assertEquals(value.value(), otherValue.value());
            }

            @Test
            @DisplayName("different value")
            void testDifferentValue() {
                DWordValue value = DWordValue.bigEndianOf("test", 16909060);

                DWordValue otherValue = value.withLittleEndianValue(67305985);

                assertNotEquals(value, otherValue);
                assertEquals(value.name(), otherValue.name());
                assertEquals(67305985, otherValue.value());
            }

            @Test
            @DisplayName("byte-order only")
            void testByteOrderOnly() {
                DWordValue value = DWordValue.bigEndianOf("test", 16909060);

                DWordValue otherValue = value.withLittleEndianValue();

                assertNotEquals(value, otherValue);
                assertEquals(value.name(), otherValue.name());
                assertEquals(value.value(), otherValue.value());
            }
        }
    }

    @Nested
    @DisplayName("withBigEndianValue")
    class WithBigEndianValue {

        @Nested
        @DisplayName("default endian")
        class DefaultEndian {

            @Test
            @DisplayName("same value")
            void testSameValue() {
                DWordValue value = DWordValue.of("test", 16909060);

                DWordValue otherValue = value.withBigEndianValue(16909060);

                assertNotEquals(value, otherValue);
                assertEquals(value.name(), otherValue.name());
                assertEquals(value.value(), otherValue.value());
            }

            @Test
            @DisplayName("different value")
            void testDifferentValue() {
                DWordValue value = DWordValue.of("test", 16909060);

                DWordValue otherValue = value.withBigEndianValue(67305985);

                assertNotEquals(value, otherValue);
                assertEquals(value.name(), otherValue.name());
                assertEquals(67305985, otherValue.value());
            }

            @Test
            @DisplayName("byte-order only")
            void testByteOrderOnly() {
                DWordValue value = DWordValue.of("test", 16909060);

                DWordValue otherValue = value.withBigEndianValue();

                assertNotEquals(value, otherValue);
                assertEquals(value.name(), otherValue.name());
                assertEquals(value.value(), otherValue.value());
            }
        }

        @Nested
        @DisplayName("little-endian")
        class LittleEndian {

            @Test
            @DisplayName("same value")
            void testSameValue() {
                DWordValue value = DWordValue.littleEndianOf("test", 16909060);

                DWordValue otherValue = value.withBigEndianValue(16909060);

                assertNotEquals(value, otherValue);
                assertEquals(value.name(), otherValue.name());
                assertEquals(value.value(), otherValue.value());
            }

            @Test
            @DisplayName("different value")
            void testDifferentValue() {
                DWordValue value = DWordValue.littleEndianOf("test", 16909060);

                DWordValue otherValue = value.withBigEndianValue(67305985);

                assertNotEquals(value, otherValue);
                assertEquals(value.name(), otherValue.name());
                assertEquals(67305985, otherValue.value());
            }

            @Test
            @DisplayName("byte-order only")
            void testByteOrderOnly() {
                DWordValue value = DWordValue.littleEndianOf("test", 16909060);

                DWordValue otherValue = value.withBigEndianValue();

                assertNotEquals(value, otherValue);
                assertEquals(value.name(), otherValue.name());
                assertEquals(value.value(), otherValue.value());
            }
        }

        @Nested
        @DisplayName("big-endian")
        class BigEndian {

            @Test
            @DisplayName("same value")
            void testSameValue() {
                DWordValue value = DWordValue.bigEndianOf("test", 16909060);

                DWordValue otherValue = value.withBigEndianValue(16909060);

                assertEquals(value, otherValue);
            }

            @Test
            @DisplayName("different value")
            void testDifferentValue() {
                DWordValue value = DWordValue.bigEndianOf("test", 16909060);

                DWordValue otherValue = value.withBigEndianValue(67305985);

                assertNotEquals(value, otherValue);
                assertEquals(value.name(), otherValue.name());
                assertEquals(67305985, otherValue.value());
            }

            @Test
            @DisplayName("byte-order only")
            void testByteOrderOnly() {
                DWordValue value = DWordValue.bigEndianOf("test", 16909060);

                DWordValue otherValue = value.withBigEndianValue();

                assertEquals(value, otherValue);
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
        BytePointer data = bytePointer(1, 2, 3, 4);
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
