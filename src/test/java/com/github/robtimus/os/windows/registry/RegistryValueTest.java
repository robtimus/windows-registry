/*
 * RegistryValueTest.java
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

import static com.github.robtimus.os.windows.registry.foreign.ForeignTestUtils.ALLOCATOR;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import com.github.robtimus.os.windows.registry.foreign.BytePointer;
import com.github.robtimus.os.windows.registry.foreign.WinNT;
import com.sun.jna.win32.W32APITypeMapper;

@SuppressWarnings("nls")
final class RegistryValueTest {

    static final String TEXT = "Lorem ipsum dolor sit amet, consectetuer adipiscing elit. Aenean commodo ligula eget dolor. Aenean massa.";

    private RegistryValueTest() {
    }

    @BeforeAll
    static void verifyUnicode() {
        assertEquals(W32APITypeMapper.UNICODE, W32APITypeMapper.DEFAULT);
    }

    @Nested
    @DisplayName("of")
    class Of {

        @Test
        @DisplayName("REG_NONE")
        void testNone() {
            BytePointer data = randomDataBytePointer();

            RegistryValue value = RegistryValue.of("test", WinNT.REG_NONE, data, data.size());
            assertInstanceOf(NoneValue.class, value);
            assertEquals("test", value.name());
            assertEquals(WinNT.REG_NONE, value.type());
        }

        @Test
        @DisplayName("REG_SZ")
        void testString() {
            BytePointer data = textAsBytePointer();

            RegistryValue value = RegistryValue.of("test", WinNT.REG_SZ, data, data.size());
            StringValue stringValue = assertInstanceOf(StringValue.class, value);
            assertEquals("test", value.name());
            assertEquals(WinNT.REG_SZ, value.type());
            assertBytePointerEquals(data, stringValue.rawData(ALLOCATOR));
        }

        @Test
        @DisplayName("REG_EXPAND_SZ")
        void testExpandableString() {
            BytePointer data = textAsBytePointer();

            RegistryValue value = RegistryValue.of("test", WinNT.REG_EXPAND_SZ, data, data.size());
            StringValue expandableStringValue = assertInstanceOf(StringValue.class, value);
            assertEquals("test", value.name());
            assertEquals(WinNT.REG_EXPAND_SZ, value.type());
            assertBytePointerEquals(data, expandableStringValue.rawData(ALLOCATOR));
        }

        @Test
        @DisplayName("REG_BINARY")
        void testBinary() {
            BytePointer data = randomDataBytePointer();

            RegistryValue value = RegistryValue.of("test", WinNT.REG_BINARY, data, data.size());
            BinaryValue binaryValue = assertInstanceOf(BinaryValue.class, value);
            assertEquals("test", value.name());
            assertEquals(WinNT.REG_BINARY, value.type());
            assertBytePointerEquals(data, binaryValue.rawData(ALLOCATOR));
        }

        @Test
        @DisplayName("REG_DWORD")
        void testDWord() {
            BytePointer data = bytePointer(1, 2, 3, 4);

            RegistryValue value = RegistryValue.of("test", WinNT.REG_DWORD, data, data.size());
            DWordValue dWordValue = assertInstanceOf(DWordValue.class, value);
            assertEquals("test", value.name());
            assertEquals(WinNT.REG_DWORD, value.type());
            assertBytePointerEquals(data, dWordValue.rawData(ALLOCATOR));
        }

        @Test
        @DisplayName("REG_DWORD_LITTLE_ENDIAN")
        void testLittleEndianDWord() {
            BytePointer data = bytePointer(1, 2, 3, 4);

            RegistryValue value = RegistryValue.of("test", WinNT.REG_DWORD_LITTLE_ENDIAN, data, data.size());
            DWordValue dWordValue = assertInstanceOf(DWordValue.class, value);
            assertEquals("test", value.name());
            assertEquals(WinNT.REG_DWORD_LITTLE_ENDIAN, value.type());
            assertBytePointerEquals(data, dWordValue.rawData(ALLOCATOR));
        }

        @Test
        @DisplayName("REG_DWORD_BIG_ENDIAN")
        void testBigEndianDWord() {
            BytePointer data = bytePointer(1, 2, 3, 4);

            RegistryValue value = RegistryValue.of("test", WinNT.REG_DWORD_BIG_ENDIAN, data, data.size());
            DWordValue dWordValue = assertInstanceOf(DWordValue.class, value);
            assertEquals("test", value.name());
            assertEquals(WinNT.REG_DWORD_BIG_ENDIAN, value.type());
            assertBytePointerEquals(data, dWordValue.rawData(ALLOCATOR));
        }

        @Test
        @DisplayName("REG_LINK")
        void testLink() {
            BytePointer data = randomDataBytePointer();

            RegistryValue value = RegistryValue.of("test", WinNT.REG_LINK, data, data.size());
            assertInstanceOf(LinkValue.class, value);
            assertEquals("test", value.name());
            assertEquals(WinNT.REG_LINK, value.type());
        }

        @Test
        @DisplayName("REG_MULTI_SZ")
        void testMultiString() {
            BytePointer data = textAsBytePointer("value1", "value2", "value3");

            RegistryValue value = RegistryValue.of("test", WinNT.REG_MULTI_SZ, data, data.size());
            MultiStringValue multiStringValue = assertInstanceOf(MultiStringValue.class, value);
            assertEquals("test", value.name());
            assertEquals(WinNT.REG_MULTI_SZ, value.type());
            assertBytePointerEquals(data, multiStringValue.rawData(ALLOCATOR));
        }

        @Test
        @DisplayName("REG_RESOURCE_LIST")
        void testResourceList() {
            BytePointer data = randomDataBytePointer();

            RegistryValue value = RegistryValue.of("test", WinNT.REG_RESOURCE_LIST, data, data.size());
            assertInstanceOf(ResourceListValue.class, value);
            assertEquals("test", value.name());
            assertEquals(WinNT.REG_RESOURCE_LIST, value.type());
        }

        @Test
        @DisplayName("REG_FULL_RESOURCE_DESCRIPTOR")
        void testFullResourceDescriptor() {
            BytePointer data = randomDataBytePointer();

            RegistryValue value = RegistryValue.of("test", WinNT.REG_FULL_RESOURCE_DESCRIPTOR, data, data.size());
            assertInstanceOf(FullResourceDescriptorValue.class, value);
            assertEquals("test", value.name());
            assertEquals(WinNT.REG_FULL_RESOURCE_DESCRIPTOR, value.type());
        }

        @Test
        @DisplayName("REG_RESOURCE_REQUIREMENTS_LIST")
        void testResourceRequirementsList() {
            BytePointer data = randomDataBytePointer();

            RegistryValue value = RegistryValue.of("test", WinNT.REG_RESOURCE_REQUIREMENTS_LIST, data, data.size());
            assertInstanceOf(ResourceRequirementsListValue.class, value);
            assertEquals("test", value.name());
            assertEquals(WinNT.REG_RESOURCE_REQUIREMENTS_LIST, value.type());
        }

        @Test
        @DisplayName("REG_QWORD")
        void testQWord() {
            BytePointer data = bytePointer(1, 2, 3, 4, 5, 6, 7, 8);

            RegistryValue value = RegistryValue.of("test", WinNT.REG_QWORD, data, data.size());
            QWordValue qWordValue = assertInstanceOf(QWordValue.class, value);
            assertEquals("test", value.name());
            assertEquals(WinNT.REG_QWORD, value.type());
            assertBytePointerEquals(data, qWordValue.rawData(ALLOCATOR));
        }

        @Test
        @DisplayName("REG_QWORD_LITTLE_ENDIAN")
        void testLittleEndianQWord() {
            BytePointer data = bytePointer(1, 2, 3, 4, 5, 6, 7, 8);

            RegistryValue value = RegistryValue.of("test", WinNT.REG_QWORD_LITTLE_ENDIAN, data, data.size());
            QWordValue qWordValue = assertInstanceOf(QWordValue.class, value);
            assertEquals("test", value.name());
            assertEquals(WinNT.REG_QWORD_LITTLE_ENDIAN, value.type());
            assertBytePointerEquals(data, qWordValue.rawData(ALLOCATOR));
        }

        @Test
        @DisplayName("unsupported type")
        void testUnsupportedType() {
            BytePointer data = randomDataBytePointer();
            int dataLength = data.size();

            assertThrows(IllegalStateException.class, () -> RegistryValue.of("test", -1, data, dataLength));
        }
    }

    @Nested
    @DisplayName("Filter")
    class Filter {

        @Test
        @DisplayName("unfiltered")
        void testUnfiltered() {
            RegistryValue.Filter filter = RegistryValue.filter();

            String name = UUID.randomUUID().toString();
            for (int type = WinNT.REG_NONE; type <= WinNT.REG_QWORD_LITTLE_ENDIAN; type++) {
                assertTrue(filter.matches(name, type));
            }
        }

        @Nested
        @DisplayName("name")
        class Name {

            @Test
            @DisplayName("matching filter")
            void testMatchingFilter() {
                RegistryValue.Filter filter = RegistryValue.filter().name(s -> s.startsWith("v"));

                String name = "value";
                for (int type = WinNT.REG_NONE; type <= WinNT.REG_QWORD_LITTLE_ENDIAN; type++) {
                    assertTrue(filter.matches(name, type));
                }
            }

            @Test
            @DisplayName("not matching filter")
            void testNotMatchingFilter() {
                RegistryValue.Filter filter = RegistryValue.filter().name(s -> !s.startsWith("v"));

                String name = "value";
                for (int type = WinNT.REG_NONE; type <= WinNT.REG_QWORD_LITTLE_ENDIAN; type++) {
                    assertFalse(filter.matches(name, type));
                }
            }
        }

        @Nested
        @DisplayName("strings")
        class Strings {

            @Nested
            @DisplayName("matching filter")
            class MatchingFilter {

                @Test
                @DisplayName("REG_SZ")
                void testRegSZ() {
                    RegistryValue.Filter filter = RegistryValue.filter().strings();

                    String name = UUID.randomUUID().toString();
                    assertTrue(filter.matches(name, WinNT.REG_SZ));
                }

                @Test
                @DisplayName("REG_EXPAND_SZ")
                void testRegExpandSZ() {
                    RegistryValue.Filter filter = RegistryValue.filter().strings();

                    String name = UUID.randomUUID().toString();
                    assertTrue(filter.matches(name, WinNT.REG_EXPAND_SZ));
                }

                @Test
                @DisplayName("REG_MULTI_SZ")
                void testRegMultiSZ() {
                    RegistryValue.Filter filter = RegistryValue.filter().strings();

                    String name = UUID.randomUUID().toString();
                    assertTrue(filter.matches(name, WinNT.REG_MULTI_SZ));
                }
            }

            @Nested
            @DisplayName("not matching filter")
            class NotMatchingFilter {

                @Test
                @DisplayName("REG_NONE")
                void testRegNone() {
                    RegistryValue.Filter filter = RegistryValue.filter().strings();

                    String name = UUID.randomUUID().toString();
                    assertFalse(filter.matches(name, WinNT.REG_NONE));
                }

                @Test
                @DisplayName("REG_BINARY")
                void testRegBinary() {
                    RegistryValue.Filter filter = RegistryValue.filter().strings();

                    String name = UUID.randomUUID().toString();
                    assertFalse(filter.matches(name, WinNT.REG_BINARY));
                }

                @Test
                @DisplayName("REG_DWORD")
                void testRegDWord() {
                    RegistryValue.Filter filter = RegistryValue.filter().strings();

                    String name = UUID.randomUUID().toString();
                    assertFalse(filter.matches(name, WinNT.REG_DWORD));
                }

                @Test
                @DisplayName("REG_DWORD_LITTLE_ENDIAN")
                void testRegLittleEndianDWord() {
                    RegistryValue.Filter filter = RegistryValue.filter().strings();

                    String name = UUID.randomUUID().toString();
                    assertFalse(filter.matches(name, WinNT.REG_DWORD_LITTLE_ENDIAN));
                }

                @Test
                @DisplayName("REG_DWORD_BIG_ENDIAN")
                void testRegBigEndianDWord() {
                    RegistryValue.Filter filter = RegistryValue.filter().strings();

                    String name = UUID.randomUUID().toString();
                    assertFalse(filter.matches(name, WinNT.REG_DWORD_BIG_ENDIAN));
                }

                @Test
                @DisplayName("REG_LINK")
                void testRegLink() {
                    RegistryValue.Filter filter = RegistryValue.filter().strings();

                    String name = UUID.randomUUID().toString();
                    assertFalse(filter.matches(name, WinNT.REG_LINK));
                }

                @Test
                @DisplayName("REG_RESOURCE_LIST")
                void testRegResourceList() {
                    RegistryValue.Filter filter = RegistryValue.filter().strings();

                    String name = UUID.randomUUID().toString();
                    assertFalse(filter.matches(name, WinNT.REG_RESOURCE_LIST));
                }

                @Test
                @DisplayName("REG_FULL_RESOURCE_DESCRIPTOR")
                void testRegFullResourceDescriptor() {
                    RegistryValue.Filter filter = RegistryValue.filter().strings();

                    String name = UUID.randomUUID().toString();
                    assertFalse(filter.matches(name, WinNT.REG_FULL_RESOURCE_DESCRIPTOR));
                }

                @Test
                @DisplayName("REG_RESOURCE_REQUIREMENTS_LIST")
                void testRegResourceRequirementsList() {
                    RegistryValue.Filter filter = RegistryValue.filter().strings();

                    String name = UUID.randomUUID().toString();
                    assertFalse(filter.matches(name, WinNT.REG_RESOURCE_REQUIREMENTS_LIST));
                }

                @Test
                @DisplayName("REG_QWORD")
                void testRegQWord() {
                    RegistryValue.Filter filter = RegistryValue.filter().strings();

                    String name = UUID.randomUUID().toString();
                    assertFalse(filter.matches(name, WinNT.REG_QWORD));
                }

                @Test
                @DisplayName("REG_QWORD_LITTLE_ENDIAN")
                void testRegLittleEndianQWord() {
                    RegistryValue.Filter filter = RegistryValue.filter().strings();

                    String name = UUID.randomUUID().toString();
                    assertFalse(filter.matches(name, WinNT.REG_QWORD_LITTLE_ENDIAN));
                }
            }
        }

        @Nested
        @DisplayName("binaries")
        class Binaries {

            @Nested
            @DisplayName("matching filter")
            class MatchingFilter {

                @Test
                @DisplayName("REG_BINARY")
                void testRegBinary() {
                    RegistryValue.Filter filter = RegistryValue.filter().binaries();

                    String name = UUID.randomUUID().toString();
                    assertTrue(filter.matches(name, WinNT.REG_BINARY));
                }
            }

            @Nested
            @DisplayName("not matching filter")
            class NotMatchingFilter {

                @Test
                @DisplayName("REG_NONE")
                void testRegNone() {
                    RegistryValue.Filter filter = RegistryValue.filter().binaries();

                    String name = UUID.randomUUID().toString();
                    assertFalse(filter.matches(name, WinNT.REG_NONE));
                }

                @Test
                @DisplayName("REG_SZ")
                void testRegSZ() {
                    RegistryValue.Filter filter = RegistryValue.filter().binaries();

                    String name = UUID.randomUUID().toString();
                    assertFalse(filter.matches(name, WinNT.REG_SZ));
                }

                @Test
                @DisplayName("REG_EXPAND_SZ")
                void testRegExpandSZ() {
                    RegistryValue.Filter filter = RegistryValue.filter().binaries();

                    String name = UUID.randomUUID().toString();
                    assertFalse(filter.matches(name, WinNT.REG_EXPAND_SZ));
                }

                @Test
                @DisplayName("REG_DWORD")
                void testRegDWord() {
                    RegistryValue.Filter filter = RegistryValue.filter().binaries();

                    String name = UUID.randomUUID().toString();
                    assertFalse(filter.matches(name, WinNT.REG_DWORD));
                }

                @Test
                @DisplayName("REG_DWORD_LITTLE_ENDIAN")
                void testRegLittleEndianDWord() {
                    RegistryValue.Filter filter = RegistryValue.filter().binaries();

                    String name = UUID.randomUUID().toString();
                    assertFalse(filter.matches(name, WinNT.REG_DWORD_LITTLE_ENDIAN));
                }

                @Test
                @DisplayName("REG_DWORD_BIG_ENDIAN")
                void testRegBigEndianDWord() {
                    RegistryValue.Filter filter = RegistryValue.filter().binaries();

                    String name = UUID.randomUUID().toString();
                    assertFalse(filter.matches(name, WinNT.REG_DWORD_BIG_ENDIAN));
                }

                @Test
                @DisplayName("REG_LINK")
                void testRegLink() {
                    RegistryValue.Filter filter = RegistryValue.filter().binaries();

                    String name = UUID.randomUUID().toString();
                    assertFalse(filter.matches(name, WinNT.REG_LINK));
                }

                @Test
                @DisplayName("REG_MULTI_SZ")
                void testRegMultiSZ() {
                    RegistryValue.Filter filter = RegistryValue.filter().binaries();

                    String name = UUID.randomUUID().toString();
                    assertFalse(filter.matches(name, WinNT.REG_MULTI_SZ));
                }

                @Test
                @DisplayName("REG_RESOURCE_LIST")
                void testRegResourceList() {
                    RegistryValue.Filter filter = RegistryValue.filter().binaries();

                    String name = UUID.randomUUID().toString();
                    assertFalse(filter.matches(name, WinNT.REG_RESOURCE_LIST));
                }

                @Test
                @DisplayName("REG_FULL_RESOURCE_DESCRIPTOR")
                void testRegFullResourceDescriptor() {
                    RegistryValue.Filter filter = RegistryValue.filter().binaries();

                    String name = UUID.randomUUID().toString();
                    assertFalse(filter.matches(name, WinNT.REG_FULL_RESOURCE_DESCRIPTOR));
                }

                @Test
                @DisplayName("REG_RESOURCE_REQUIREMENTS_LIST")
                void testRegResourceRequirementsList() {
                    RegistryValue.Filter filter = RegistryValue.filter().binaries();

                    String name = UUID.randomUUID().toString();
                    assertFalse(filter.matches(name, WinNT.REG_RESOURCE_REQUIREMENTS_LIST));
                }

                @Test
                @DisplayName("REG_QWORD")
                void testRegQWord() {
                    RegistryValue.Filter filter = RegistryValue.filter().binaries();

                    String name = UUID.randomUUID().toString();
                    assertFalse(filter.matches(name, WinNT.REG_QWORD));
                }

                @Test
                @DisplayName("REG_QWORD_LITTLE_ENDIAN")
                void testRegLittleEndianQWord() {
                    RegistryValue.Filter filter = RegistryValue.filter().binaries();

                    String name = UUID.randomUUID().toString();
                    assertFalse(filter.matches(name, WinNT.REG_QWORD_LITTLE_ENDIAN));
                }
            }
        }

        @Nested
        @DisplayName("words")
        class Words {

            @Nested
            @DisplayName("matching filter")
            class MatchingFilter {

                @Test
                @DisplayName("REG_DWORD")
                void testRegDWord() {
                    RegistryValue.Filter filter = RegistryValue.filter().words();

                    String name = UUID.randomUUID().toString();
                    assertTrue(filter.matches(name, WinNT.REG_DWORD));
                }

                @Test
                @DisplayName("REG_DWORD_LITTLE_ENDIAN")
                void testRegLittleEndianDWord() {
                    RegistryValue.Filter filter = RegistryValue.filter().words();

                    String name = UUID.randomUUID().toString();
                    assertTrue(filter.matches(name, WinNT.REG_DWORD_LITTLE_ENDIAN));
                }

                @Test
                @DisplayName("REG_DWORD_BIG_ENDIAN")
                void testRegBigEndianDWord() {
                    RegistryValue.Filter filter = RegistryValue.filter().words();

                    String name = UUID.randomUUID().toString();
                    assertTrue(filter.matches(name, WinNT.REG_DWORD_BIG_ENDIAN));
                }

                @Test
                @DisplayName("REG_QWORD")
                void testRegQWord() {
                    RegistryValue.Filter filter = RegistryValue.filter().words();

                    String name = UUID.randomUUID().toString();
                    assertTrue(filter.matches(name, WinNT.REG_QWORD));
                }

                @Test
                @DisplayName("REG_QWORD_LITTLE_ENDIAN")
                void testRegLittleEndianQWord() {
                    RegistryValue.Filter filter = RegistryValue.filter().words();

                    String name = UUID.randomUUID().toString();
                    assertTrue(filter.matches(name, WinNT.REG_QWORD_LITTLE_ENDIAN));
                }
            }

            @Nested
            @DisplayName("not matching filter")
            class NotMatchingFilter {

                @Test
                @DisplayName("REG_NONE")
                void testRegNone() {
                    RegistryValue.Filter filter = RegistryValue.filter().words();

                    String name = UUID.randomUUID().toString();
                    assertFalse(filter.matches(name, WinNT.REG_NONE));
                }

                @Test
                @DisplayName("REG_SZ")
                void testRegSZ() {
                    RegistryValue.Filter filter = RegistryValue.filter().words();

                    String name = UUID.randomUUID().toString();
                    assertFalse(filter.matches(name, WinNT.REG_SZ));
                }

                @Test
                @DisplayName("REG_EXPAND_SZ")
                void testRegExpandSZ() {
                    RegistryValue.Filter filter = RegistryValue.filter().words();

                    String name = UUID.randomUUID().toString();
                    assertFalse(filter.matches(name, WinNT.REG_EXPAND_SZ));
                }

                @Test
                @DisplayName("REG_BINARY")
                void testRegBinary() {
                    RegistryValue.Filter filter = RegistryValue.filter().words();

                    String name = UUID.randomUUID().toString();
                    assertFalse(filter.matches(name, WinNT.REG_BINARY));
                }

                @Test
                @DisplayName("REG_LINK")
                void testRegLink() {
                    RegistryValue.Filter filter = RegistryValue.filter().words();

                    String name = UUID.randomUUID().toString();
                    assertFalse(filter.matches(name, WinNT.REG_LINK));
                }

                @Test
                @DisplayName("REG_MULTI_SZ")
                void testRegMultiSZ() {
                    RegistryValue.Filter filter = RegistryValue.filter().words();

                    String name = UUID.randomUUID().toString();
                    assertFalse(filter.matches(name, WinNT.REG_MULTI_SZ));
                }

                @Test
                @DisplayName("REG_RESOURCE_LIST")
                void testRegResourceList() {
                    RegistryValue.Filter filter = RegistryValue.filter().words();

                    String name = UUID.randomUUID().toString();
                    assertFalse(filter.matches(name, WinNT.REG_RESOURCE_LIST));
                }

                @Test
                @DisplayName("REG_FULL_RESOURCE_DESCRIPTOR")
                void testRegFullResourceDescriptor() {
                    RegistryValue.Filter filter = RegistryValue.filter().words();

                    String name = UUID.randomUUID().toString();
                    assertFalse(filter.matches(name, WinNT.REG_FULL_RESOURCE_DESCRIPTOR));
                }

                @Test
                @DisplayName("REG_RESOURCE_REQUIREMENTS_LIST")
                void testRegResourceRequirementsList() {
                    RegistryValue.Filter filter = RegistryValue.filter().words();

                    String name = UUID.randomUUID().toString();
                    assertFalse(filter.matches(name, WinNT.REG_RESOURCE_REQUIREMENTS_LIST));
                }
            }
        }

        @Nested
        @DisplayName("settable")
        class Settable {

            @Nested
            @DisplayName("matching filter")
            class MatchingFilter {

                @Test
                @DisplayName("REG_SZ")
                void testRegSZ() {
                    RegistryValue.Filter filter = RegistryValue.filter().settable();

                    String name = UUID.randomUUID().toString();
                    assertTrue(filter.matches(name, WinNT.REG_SZ));
                }

                @Test
                @DisplayName("REG_EXPAND_SZ")
                void testRegExpandSZ() {
                    RegistryValue.Filter filter = RegistryValue.filter().settable();

                    String name = UUID.randomUUID().toString();
                    assertTrue(filter.matches(name, WinNT.REG_EXPAND_SZ));
                }

                @Test
                @DisplayName("REG_BINARY")
                void testRegBinary() {
                    RegistryValue.Filter filter = RegistryValue.filter().settable();

                    String name = UUID.randomUUID().toString();
                    assertTrue(filter.matches(name, WinNT.REG_BINARY));
                }

                @Test
                @DisplayName("REG_DWORD")
                void testRegDWord() {
                    RegistryValue.Filter filter = RegistryValue.filter().settable();

                    String name = UUID.randomUUID().toString();
                    assertTrue(filter.matches(name, WinNT.REG_DWORD));
                }

                @Test
                @DisplayName("REG_DWORD_LITTLE_ENDIAN")
                void testRegLittleEndianDWord() {
                    RegistryValue.Filter filter = RegistryValue.filter().settable();

                    String name = UUID.randomUUID().toString();
                    assertTrue(filter.matches(name, WinNT.REG_DWORD_LITTLE_ENDIAN));
                }

                @Test
                @DisplayName("REG_DWORD_BIG_ENDIAN")
                void testRegBigEndianDWord() {
                    RegistryValue.Filter filter = RegistryValue.filter().settable();

                    String name = UUID.randomUUID().toString();
                    assertTrue(filter.matches(name, WinNT.REG_DWORD_BIG_ENDIAN));
                }

                @Test
                @DisplayName("REG_MULTI_SZ")
                void testRegMultiSZ() {
                    RegistryValue.Filter filter = RegistryValue.filter().settable();

                    String name = UUID.randomUUID().toString();
                    assertTrue(filter.matches(name, WinNT.REG_MULTI_SZ));
                }

                @Test
                @DisplayName("REG_QWORD")
                void testRegQWord() {
                    RegistryValue.Filter filter = RegistryValue.filter().settable();

                    String name = UUID.randomUUID().toString();
                    assertTrue(filter.matches(name, WinNT.REG_QWORD));
                }

                @Test
                @DisplayName("REG_QWORD_LITTLE_ENDIAN")
                void testRegLittleEndianQWord() {
                    RegistryValue.Filter filter = RegistryValue.filter().settable();

                    String name = UUID.randomUUID().toString();
                    assertTrue(filter.matches(name, WinNT.REG_QWORD_LITTLE_ENDIAN));
                }
            }

            @Nested
            @DisplayName("not matching filter")
            class NotMatchingFilter {

                @Test
                @DisplayName("REG_NONE")
                void testRegNone() {
                    RegistryValue.Filter filter = RegistryValue.filter().settable();

                    String name = UUID.randomUUID().toString();
                    assertFalse(filter.matches(name, WinNT.REG_NONE));
                }

                @Test
                @DisplayName("REG_LINK")
                void testRegLink() {
                    RegistryValue.Filter filter = RegistryValue.filter().settable();

                    String name = UUID.randomUUID().toString();
                    assertFalse(filter.matches(name, WinNT.REG_LINK));
                }

                @Test
                @DisplayName("REG_RESOURCE_LIST")
                void testRegResourceList() {
                    RegistryValue.Filter filter = RegistryValue.filter().settable();

                    String name = UUID.randomUUID().toString();
                    assertFalse(filter.matches(name, WinNT.REG_RESOURCE_LIST));
                }

                @Test
                @DisplayName("REG_FULL_RESOURCE_DESCRIPTOR")
                void testRegFullResourceDescriptor() {
                    RegistryValue.Filter filter = RegistryValue.filter().settable();

                    String name = UUID.randomUUID().toString();
                    assertFalse(filter.matches(name, WinNT.REG_FULL_RESOURCE_DESCRIPTOR));
                }

                @Test
                @DisplayName("REG_RESOURCE_REQUIREMENTS_LIST")
                void testRegResourceRequirementsList() {
                    RegistryValue.Filter filter = RegistryValue.filter().settable();

                    String name = UUID.randomUUID().toString();
                    assertFalse(filter.matches(name, WinNT.REG_RESOURCE_REQUIREMENTS_LIST));
                }
            }
        }

        @Nested
        @DisplayName("classes")
        class Classes {

            @Nested
            @DisplayName("RegistryValue")
            class RegistryValueClass {

                @Nested
                @DisplayName("matching filter")
                class MatchingFilter {

                    @Test
                    @DisplayName("REG_NONE")
                    void testRegNone() {
                        RegistryValue.Filter filter = RegistryValue.filter().classes(RegistryValue.class);

                        String name = UUID.randomUUID().toString();
                        assertTrue(filter.matches(name, WinNT.REG_NONE));
                    }

                    @Test
                    @DisplayName("REG_SZ")
                    void testRegSZ() {
                        RegistryValue.Filter filter = RegistryValue.filter().classes(RegistryValue.class);

                        String name = UUID.randomUUID().toString();
                        assertTrue(filter.matches(name, WinNT.REG_SZ));
                    }

                    @Test
                    @DisplayName("REG_EXPAND_SZ")
                    void testRegExpandSZ() {
                        RegistryValue.Filter filter = RegistryValue.filter().classes(RegistryValue.class);

                        String name = UUID.randomUUID().toString();
                        assertTrue(filter.matches(name, WinNT.REG_EXPAND_SZ));
                    }

                    @Test
                    @DisplayName("REG_BINARY")
                    void testRegBinary() {
                        RegistryValue.Filter filter = RegistryValue.filter().classes(RegistryValue.class);

                        String name = UUID.randomUUID().toString();
                        assertTrue(filter.matches(name, WinNT.REG_BINARY));
                    }

                    @Test
                    @DisplayName("REG_DWORD")
                    void testRegDWord() {
                        RegistryValue.Filter filter = RegistryValue.filter().classes(RegistryValue.class);

                        String name = UUID.randomUUID().toString();
                        assertTrue(filter.matches(name, WinNT.REG_DWORD));
                    }

                    @Test
                    @DisplayName("REG_DWORD_LITTLE_ENDIAN")
                    void testRegLittleEndianDWord() {
                        RegistryValue.Filter filter = RegistryValue.filter().classes(RegistryValue.class);

                        String name = UUID.randomUUID().toString();
                        assertTrue(filter.matches(name, WinNT.REG_DWORD_LITTLE_ENDIAN));
                    }

                    @Test
                    @DisplayName("REG_DWORD_BIG_ENDIAN")
                    void testRegBigEndianDWord() {
                        RegistryValue.Filter filter = RegistryValue.filter().classes(RegistryValue.class);

                        String name = UUID.randomUUID().toString();
                        assertTrue(filter.matches(name, WinNT.REG_DWORD_BIG_ENDIAN));
                    }

                    @Test
                    @DisplayName("REG_LINK")
                    void testRegLink() {
                        RegistryValue.Filter filter = RegistryValue.filter().classes(RegistryValue.class);

                        String name = UUID.randomUUID().toString();
                        assertTrue(filter.matches(name, WinNT.REG_LINK));
                    }

                    @Test
                    @DisplayName("REG_MULTI_SZ")
                    void testRegMultiSZ() {
                        RegistryValue.Filter filter = RegistryValue.filter().classes(RegistryValue.class);

                        String name = UUID.randomUUID().toString();
                        assertTrue(filter.matches(name, WinNT.REG_MULTI_SZ));
                    }

                    @Test
                    @DisplayName("REG_RESOURCE_LIST")
                    void testRegResourceList() {
                        RegistryValue.Filter filter = RegistryValue.filter().classes(RegistryValue.class);

                        String name = UUID.randomUUID().toString();
                        assertTrue(filter.matches(name, WinNT.REG_RESOURCE_LIST));
                    }

                    @Test
                    @DisplayName("REG_FULL_RESOURCE_DESCRIPTOR")
                    void testRegFullResourceDescriptor() {
                        RegistryValue.Filter filter = RegistryValue.filter().classes(RegistryValue.class);

                        String name = UUID.randomUUID().toString();
                        assertTrue(filter.matches(name, WinNT.REG_FULL_RESOURCE_DESCRIPTOR));
                    }

                    @Test
                    @DisplayName("REG_RESOURCE_REQUIREMENTS_LIST")
                    void testRegResourceRequirementsList() {
                        RegistryValue.Filter filter = RegistryValue.filter().classes(RegistryValue.class);

                        String name = UUID.randomUUID().toString();
                        assertTrue(filter.matches(name, WinNT.REG_RESOURCE_REQUIREMENTS_LIST));
                    }

                    @Test
                    @DisplayName("REG_QWORD")
                    void testRegQWord() {
                        RegistryValue.Filter filter = RegistryValue.filter().classes(RegistryValue.class);

                        String name = UUID.randomUUID().toString();
                        assertTrue(filter.matches(name, WinNT.REG_QWORD));
                    }

                    @Test
                    @DisplayName("REG_QWORD_LITTLE_ENDIAN")
                    void testRegLittleEndianQWord() {
                        RegistryValue.Filter filter = RegistryValue.filter().classes(RegistryValue.class);

                        String name = UUID.randomUUID().toString();
                        assertTrue(filter.matches(name, WinNT.REG_QWORD_LITTLE_ENDIAN));
                    }
                }
            }

            @Nested
            @DisplayName("SettableRegistryValue")
            class SettableRegistryValueClass {

                @Nested
                @DisplayName("matching filter")
                class MatchingFilter {

                    @Test
                    @DisplayName("REG_SZ")
                    void testRegSZ() {
                        RegistryValue.Filter filter = RegistryValue.filter().classes(SettableRegistryValue.class);

                        String name = UUID.randomUUID().toString();
                        assertTrue(filter.matches(name, WinNT.REG_SZ));
                    }

                    @Test
                    @DisplayName("REG_EXPAND_SZ")
                    void testRegExpandSZ() {
                        RegistryValue.Filter filter = RegistryValue.filter().classes(SettableRegistryValue.class);

                        String name = UUID.randomUUID().toString();
                        assertTrue(filter.matches(name, WinNT.REG_EXPAND_SZ));
                    }

                    @Test
                    @DisplayName("REG_BINARY")
                    void testRegBinary() {
                        RegistryValue.Filter filter = RegistryValue.filter().classes(SettableRegistryValue.class);

                        String name = UUID.randomUUID().toString();
                        assertTrue(filter.matches(name, WinNT.REG_BINARY));
                    }

                    @Test
                    @DisplayName("REG_DWORD")
                    void testRegDWord() {
                        RegistryValue.Filter filter = RegistryValue.filter().classes(SettableRegistryValue.class);

                        String name = UUID.randomUUID().toString();
                        assertTrue(filter.matches(name, WinNT.REG_DWORD));
                    }

                    @Test
                    @DisplayName("REG_DWORD_LITTLE_ENDIAN")
                    void testRegLittleEndianDWord() {
                        RegistryValue.Filter filter = RegistryValue.filter().classes(SettableRegistryValue.class);

                        String name = UUID.randomUUID().toString();
                        assertTrue(filter.matches(name, WinNT.REG_DWORD_LITTLE_ENDIAN));
                    }

                    @Test
                    @DisplayName("REG_DWORD_BIG_ENDIAN")
                    void testRegBigEndianDWord() {
                        RegistryValue.Filter filter = RegistryValue.filter().classes(SettableRegistryValue.class);

                        String name = UUID.randomUUID().toString();
                        assertTrue(filter.matches(name, WinNT.REG_DWORD_BIG_ENDIAN));
                    }

                    @Test
                    @DisplayName("REG_MULTI_SZ")
                    void testRegMultiSZ() {
                        RegistryValue.Filter filter = RegistryValue.filter().classes(SettableRegistryValue.class);

                        String name = UUID.randomUUID().toString();
                        assertTrue(filter.matches(name, WinNT.REG_MULTI_SZ));
                    }

                    @Test
                    @DisplayName("REG_QWORD")
                    void testRegQWord() {
                        RegistryValue.Filter filter = RegistryValue.filter().classes(SettableRegistryValue.class);

                        String name = UUID.randomUUID().toString();
                        assertTrue(filter.matches(name, WinNT.REG_QWORD));
                    }

                    @Test
                    @DisplayName("REG_QWORD_LITTLE_ENDIAN")
                    void testRegLittleEndianQWord() {
                        RegistryValue.Filter filter = RegistryValue.filter().classes(SettableRegistryValue.class);

                        String name = UUID.randomUUID().toString();
                        assertTrue(filter.matches(name, WinNT.REG_QWORD_LITTLE_ENDIAN));
                    }
                }

                @Nested
                @DisplayName("not matching filter")
                class NotMatchingFilter {

                    @Test
                    @DisplayName("REG_NONE")
                    void testRegNone() {
                        RegistryValue.Filter filter = RegistryValue.filter().classes(SettableRegistryValue.class);

                        String name = UUID.randomUUID().toString();
                        assertFalse(filter.matches(name, WinNT.REG_NONE));
                    }

                    @Test
                    @DisplayName("REG_LINK")
                    void testRegLink() {
                        RegistryValue.Filter filter = RegistryValue.filter().classes(SettableRegistryValue.class);

                        String name = UUID.randomUUID().toString();
                        assertFalse(filter.matches(name, WinNT.REG_LINK));
                    }

                    @Test
                    @DisplayName("REG_RESOURCE_LIST")
                    void testRegResourceList() {
                        RegistryValue.Filter filter = RegistryValue.filter().classes(SettableRegistryValue.class);

                        String name = UUID.randomUUID().toString();
                        assertFalse(filter.matches(name, WinNT.REG_RESOURCE_LIST));
                    }

                    @Test
                    @DisplayName("REG_FULL_RESOURCE_DESCRIPTOR")
                    void testRegFullResourceDescriptor() {
                        RegistryValue.Filter filter = RegistryValue.filter().classes(SettableRegistryValue.class);

                        String name = UUID.randomUUID().toString();
                        assertFalse(filter.matches(name, WinNT.REG_FULL_RESOURCE_DESCRIPTOR));
                    }

                    @Test
                    @DisplayName("REG_RESOURCE_REQUIREMENTS_LIST")
                    void testRegResourceRequirementsList() {
                        RegistryValue.Filter filter = RegistryValue.filter().classes(SettableRegistryValue.class);

                        String name = UUID.randomUUID().toString();
                        assertFalse(filter.matches(name, WinNT.REG_RESOURCE_REQUIREMENTS_LIST));
                    }
                }
            }
        }
    }

    static byte[] randomData() {
        Random random = new Random();
        byte[] data = new byte[128];
        random.nextBytes(data);
        return data;
    }

    static BytePointer randomDataBytePointer() {
        byte[] bytes = randomData();
        return BytePointer.withBytes(bytes, ALLOCATOR);
    }

    static BytePointer bytePointer(int... values) {
        byte[] data = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            data[i] = (byte) values[i];
        }
        return BytePointer.withBytes(data, ALLOCATOR);
    }

    static byte[] textAsBytes() {
        return textAsBytes(TEXT);
    }

    static BytePointer textAsBytePointer() {
        byte[] bytes = textAsBytes();
        return BytePointer.withBytes(bytes, ALLOCATOR);
    }

    static byte[] textAsBytes(String... texts) {
        byte[] result = {};
        for (String text : texts) {
            byte[] textResult = textAsBytes(text);
            byte[] newResult = Arrays.copyOf(result, result.length + textResult.length);
            System.arraycopy(textResult, 0, newResult, result.length, textResult.length);
            result = newResult;
        }
        return Arrays.copyOf(result, result.length + 2);
    }

    static BytePointer textAsBytePointer(String... texts) {
        byte[] bytes = textAsBytes(texts);
        return BytePointer.withBytes(bytes, ALLOCATOR);
    }

    private static byte[] textAsBytes(String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[bytes.length * 2 + 2];
        for (int i = 0; i < bytes.length; i++) {
            result[i * 2] = bytes[i];
        }
        return result;
    }

    static BytePointer resized(BytePointer data, int newSize) {
        byte[] bytes = Arrays.copyOf(data.toByteArray(), newSize);
        return BytePointer.withBytes(bytes, ALLOCATOR);
    }

    static void assertBytePointerEquals(BytePointer expected, BytePointer actual) {
        assertBytePointerEquals(expected, actual, expected.size());
    }

    static void assertBytePointerEquals(BytePointer expected, BytePointer actual, int dataLength) {
        assertArrayEquals(expected.toByteArray(dataLength), actual.toByteArray(dataLength));
    }
}
