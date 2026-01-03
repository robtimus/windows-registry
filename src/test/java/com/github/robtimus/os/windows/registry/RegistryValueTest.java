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

import static com.github.robtimus.os.windows.registry.foreign.WindowsConstants.REG_BINARY;
import static com.github.robtimus.os.windows.registry.foreign.WindowsConstants.REG_DWORD;
import static com.github.robtimus.os.windows.registry.foreign.WindowsConstants.REG_DWORD_BIG_ENDIAN;
import static com.github.robtimus.os.windows.registry.foreign.WindowsConstants.REG_DWORD_LITTLE_ENDIAN;
import static com.github.robtimus.os.windows.registry.foreign.WindowsConstants.REG_EXPAND_SZ;
import static com.github.robtimus.os.windows.registry.foreign.WindowsConstants.REG_FULL_RESOURCE_DESCRIPTOR;
import static com.github.robtimus.os.windows.registry.foreign.WindowsConstants.REG_LINK;
import static com.github.robtimus.os.windows.registry.foreign.WindowsConstants.REG_MULTI_SZ;
import static com.github.robtimus.os.windows.registry.foreign.WindowsConstants.REG_NONE;
import static com.github.robtimus.os.windows.registry.foreign.WindowsConstants.REG_QWORD;
import static com.github.robtimus.os.windows.registry.foreign.WindowsConstants.REG_QWORD_LITTLE_ENDIAN;
import static com.github.robtimus.os.windows.registry.foreign.WindowsConstants.REG_RESOURCE_LIST;
import static com.github.robtimus.os.windows.registry.foreign.WindowsConstants.REG_RESOURCE_REQUIREMENTS_LIST;
import static com.github.robtimus.os.windows.registry.foreign.WindowsConstants.REG_SZ;
import static java.lang.Math.toIntExact;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
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
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment data = randomDataBytePointer(arena);

                RegistryValue value = RegistryValue.of("test", REG_NONE, data, data.byteSize());
                assertInstanceOf(NoneValue.class, value);
                assertEquals("test", value.name());
                assertEquals(REG_NONE, value.type());
            }
        }

        @Test
        @DisplayName("REG_SZ")
        void testString() {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment data = textAsSegment(arena);

                RegistryValue value = RegistryValue.of("test", REG_SZ, data, data.byteSize());
                StringValue stringValue = assertInstanceOf(StringValue.class, value);
                assertEquals("test", value.name());
                assertEquals(REG_SZ, value.type());
                assertContentEquals(data, stringValue.rawData(arena));
            }
        }

        @Test
        @DisplayName("REG_EXPAND_SZ")
        void testExpandableString() {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment data = textAsSegment(arena);

                RegistryValue value = RegistryValue.of("test", REG_EXPAND_SZ, data, data.byteSize());
                StringValue expandableStringValue = assertInstanceOf(StringValue.class, value);
                assertEquals("test", value.name());
                assertEquals(REG_EXPAND_SZ, value.type());
                assertContentEquals(data, expandableStringValue.rawData(arena));
            }
        }

        @Test
        @DisplayName("REG_BINARY")
        void testBinary() {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment data = randomDataBytePointer(arena);

                RegistryValue value = RegistryValue.of("test", REG_BINARY, data, data.byteSize());
                BinaryValue binaryValue = assertInstanceOf(BinaryValue.class, value);
                assertEquals("test", value.name());
                assertEquals(REG_BINARY, value.type());
                assertContentEquals(data, binaryValue.rawData(arena));
            }
        }

        @Test
        @DisplayName("REG_DWORD")
        void testDWord() {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment data = bytesSegment(arena, 1, 2, 3, 4);

                RegistryValue value = RegistryValue.of("test", REG_DWORD, data, data.byteSize());
                DWordValue dWordValue = assertInstanceOf(DWordValue.class, value);
                assertEquals("test", value.name());
                assertEquals(REG_DWORD, value.type());
                assertContentEquals(data, dWordValue.rawData(arena));
            }
        }

        @Test
        @DisplayName("REG_DWORD_LITTLE_ENDIAN")
        void testLittleEndianDWord() {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment data = bytesSegment(arena, 1, 2, 3, 4);

                RegistryValue value = RegistryValue.of("test", REG_DWORD_LITTLE_ENDIAN, data, data.byteSize());
                DWordValue dWordValue = assertInstanceOf(DWordValue.class, value);
                assertEquals("test", value.name());
                assertEquals(REG_DWORD_LITTLE_ENDIAN, value.type());
                assertContentEquals(data, dWordValue.rawData(arena));
            }
        }

        @Test
        @DisplayName("REG_DWORD_BIG_ENDIAN")
        void testBigEndianDWord() {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment data = bytesSegment(arena, 1, 2, 3, 4);

                RegistryValue value = RegistryValue.of("test", REG_DWORD_BIG_ENDIAN, data, data.byteSize());
                DWordValue dWordValue = assertInstanceOf(DWordValue.class, value);
                assertEquals("test", value.name());
                assertEquals(REG_DWORD_BIG_ENDIAN, value.type());
                assertContentEquals(data, dWordValue.rawData(arena));
            }
        }

        @Test
        @DisplayName("REG_LINK")
        void testLink() {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment data = randomDataBytePointer(arena);

                RegistryValue value = RegistryValue.of("test", REG_LINK, data, data.byteSize());
                assertInstanceOf(LinkValue.class, value);
                assertEquals("test", value.name());
                assertEquals(REG_LINK, value.type());
            }
        }

        @Test
        @DisplayName("REG_MULTI_SZ")
        void testMultiString() {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment data = textAsSegment(arena, "value1", "value2", "value3");

                RegistryValue value = RegistryValue.of("test", REG_MULTI_SZ, data, data.byteSize());
                MultiStringValue multiStringValue = assertInstanceOf(MultiStringValue.class, value);
                assertEquals("test", value.name());
                assertEquals(REG_MULTI_SZ, value.type());
                assertContentEquals(data, multiStringValue.rawData(arena));
            }
        }

        @Test
        @DisplayName("REG_RESOURCE_LIST")
        void testResourceList() {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment data = randomDataBytePointer(arena);

                RegistryValue value = RegistryValue.of("test", REG_RESOURCE_LIST, data, data.byteSize());
                assertInstanceOf(ResourceListValue.class, value);
                assertEquals("test", value.name());
                assertEquals(REG_RESOURCE_LIST, value.type());
            }
        }

        @Test
        @DisplayName("REG_FULL_RESOURCE_DESCRIPTOR")
        void testFullResourceDescriptor() {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment data = randomDataBytePointer(arena);

                RegistryValue value = RegistryValue.of("test", REG_FULL_RESOURCE_DESCRIPTOR, data, data.byteSize());
                assertInstanceOf(FullResourceDescriptorValue.class, value);
                assertEquals("test", value.name());
                assertEquals(REG_FULL_RESOURCE_DESCRIPTOR, value.type());
            }
        }

        @Test
        @DisplayName("REG_RESOURCE_REQUIREMENTS_LIST")
        void testResourceRequirementsList() {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment data = randomDataBytePointer(arena);

                RegistryValue value = RegistryValue.of("test", REG_RESOURCE_REQUIREMENTS_LIST, data, data.byteSize());
                assertInstanceOf(ResourceRequirementsListValue.class, value);
                assertEquals("test", value.name());
                assertEquals(REG_RESOURCE_REQUIREMENTS_LIST, value.type());
            }
        }

        @Test
        @DisplayName("REG_QWORD")
        void testQWord() {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment data = bytesSegment(arena, 1, 2, 3, 4, 5, 6, 7, 8);

                RegistryValue value = RegistryValue.of("test", REG_QWORD, data, data.byteSize());
                QWordValue qWordValue = assertInstanceOf(QWordValue.class, value);
                assertEquals("test", value.name());
                assertEquals(REG_QWORD, value.type());
                assertContentEquals(data, qWordValue.rawData(arena));
            }
        }

        @Test
        @DisplayName("REG_QWORD_LITTLE_ENDIAN")
        void testLittleEndianQWord() {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment data = bytesSegment(arena, 1, 2, 3, 4, 5, 6, 7, 8);

                RegistryValue value = RegistryValue.of("test", REG_QWORD_LITTLE_ENDIAN, data, data.byteSize());
                QWordValue qWordValue = assertInstanceOf(QWordValue.class, value);
                assertEquals("test", value.name());
                assertEquals(REG_QWORD_LITTLE_ENDIAN, value.type());
                assertContentEquals(data, qWordValue.rawData(arena));
            }
        }

        @Test
        @DisplayName("unsupported type")
        void testUnsupportedType() {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment data = randomDataBytePointer(arena);
                long dataLength = data.byteSize();

                assertThrows(IllegalStateException.class, () -> RegistryValue.of("test", -1, data, dataLength));
            }
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
            for (int type = REG_NONE; type <= REG_QWORD_LITTLE_ENDIAN; type++) {
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
                for (int type = REG_NONE; type <= REG_QWORD_LITTLE_ENDIAN; type++) {
                    assertTrue(filter.matches(name, type));
                }
            }

            @Test
            @DisplayName("not matching filter")
            void testNotMatchingFilter() {
                RegistryValue.Filter filter = RegistryValue.filter().name(s -> !s.startsWith("v"));

                String name = "value";
                for (int type = REG_NONE; type <= REG_QWORD_LITTLE_ENDIAN; type++) {
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
                    assertTrue(filter.matches(name, REG_SZ));
                }

                @Test
                @DisplayName("REG_EXPAND_SZ")
                void testRegExpandSZ() {
                    RegistryValue.Filter filter = RegistryValue.filter().strings();

                    String name = UUID.randomUUID().toString();
                    assertTrue(filter.matches(name, REG_EXPAND_SZ));
                }

                @Test
                @DisplayName("REG_MULTI_SZ")
                void testRegMultiSZ() {
                    RegistryValue.Filter filter = RegistryValue.filter().strings();

                    String name = UUID.randomUUID().toString();
                    assertTrue(filter.matches(name, REG_MULTI_SZ));
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
                    assertFalse(filter.matches(name, REG_NONE));
                }

                @Test
                @DisplayName("REG_BINARY")
                void testRegBinary() {
                    RegistryValue.Filter filter = RegistryValue.filter().strings();

                    String name = UUID.randomUUID().toString();
                    assertFalse(filter.matches(name, REG_BINARY));
                }

                @Test
                @DisplayName("REG_DWORD")
                void testRegDWord() {
                    RegistryValue.Filter filter = RegistryValue.filter().strings();

                    String name = UUID.randomUUID().toString();
                    assertFalse(filter.matches(name, REG_DWORD));
                }

                @Test
                @DisplayName("REG_DWORD_LITTLE_ENDIAN")
                void testRegLittleEndianDWord() {
                    RegistryValue.Filter filter = RegistryValue.filter().strings();

                    String name = UUID.randomUUID().toString();
                    assertFalse(filter.matches(name, REG_DWORD_LITTLE_ENDIAN));
                }

                @Test
                @DisplayName("REG_DWORD_BIG_ENDIAN")
                void testRegBigEndianDWord() {
                    RegistryValue.Filter filter = RegistryValue.filter().strings();

                    String name = UUID.randomUUID().toString();
                    assertFalse(filter.matches(name, REG_DWORD_BIG_ENDIAN));
                }

                @Test
                @DisplayName("REG_LINK")
                void testRegLink() {
                    RegistryValue.Filter filter = RegistryValue.filter().strings();

                    String name = UUID.randomUUID().toString();
                    assertFalse(filter.matches(name, REG_LINK));
                }

                @Test
                @DisplayName("REG_RESOURCE_LIST")
                void testRegResourceList() {
                    RegistryValue.Filter filter = RegistryValue.filter().strings();

                    String name = UUID.randomUUID().toString();
                    assertFalse(filter.matches(name, REG_RESOURCE_LIST));
                }

                @Test
                @DisplayName("REG_FULL_RESOURCE_DESCRIPTOR")
                void testRegFullResourceDescriptor() {
                    RegistryValue.Filter filter = RegistryValue.filter().strings();

                    String name = UUID.randomUUID().toString();
                    assertFalse(filter.matches(name, REG_FULL_RESOURCE_DESCRIPTOR));
                }

                @Test
                @DisplayName("REG_RESOURCE_REQUIREMENTS_LIST")
                void testRegResourceRequirementsList() {
                    RegistryValue.Filter filter = RegistryValue.filter().strings();

                    String name = UUID.randomUUID().toString();
                    assertFalse(filter.matches(name, REG_RESOURCE_REQUIREMENTS_LIST));
                }

                @Test
                @DisplayName("REG_QWORD")
                void testRegQWord() {
                    RegistryValue.Filter filter = RegistryValue.filter().strings();

                    String name = UUID.randomUUID().toString();
                    assertFalse(filter.matches(name, REG_QWORD));
                }

                @Test
                @DisplayName("REG_QWORD_LITTLE_ENDIAN")
                void testRegLittleEndianQWord() {
                    RegistryValue.Filter filter = RegistryValue.filter().strings();

                    String name = UUID.randomUUID().toString();
                    assertFalse(filter.matches(name, REG_QWORD_LITTLE_ENDIAN));
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
                    assertTrue(filter.matches(name, REG_BINARY));
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
                    assertFalse(filter.matches(name, REG_NONE));
                }

                @Test
                @DisplayName("REG_SZ")
                void testRegSZ() {
                    RegistryValue.Filter filter = RegistryValue.filter().binaries();

                    String name = UUID.randomUUID().toString();
                    assertFalse(filter.matches(name, REG_SZ));
                }

                @Test
                @DisplayName("REG_EXPAND_SZ")
                void testRegExpandSZ() {
                    RegistryValue.Filter filter = RegistryValue.filter().binaries();

                    String name = UUID.randomUUID().toString();
                    assertFalse(filter.matches(name, REG_EXPAND_SZ));
                }

                @Test
                @DisplayName("REG_DWORD")
                void testRegDWord() {
                    RegistryValue.Filter filter = RegistryValue.filter().binaries();

                    String name = UUID.randomUUID().toString();
                    assertFalse(filter.matches(name, REG_DWORD));
                }

                @Test
                @DisplayName("REG_DWORD_LITTLE_ENDIAN")
                void testRegLittleEndianDWord() {
                    RegistryValue.Filter filter = RegistryValue.filter().binaries();

                    String name = UUID.randomUUID().toString();
                    assertFalse(filter.matches(name, REG_DWORD_LITTLE_ENDIAN));
                }

                @Test
                @DisplayName("REG_DWORD_BIG_ENDIAN")
                void testRegBigEndianDWord() {
                    RegistryValue.Filter filter = RegistryValue.filter().binaries();

                    String name = UUID.randomUUID().toString();
                    assertFalse(filter.matches(name, REG_DWORD_BIG_ENDIAN));
                }

                @Test
                @DisplayName("REG_LINK")
                void testRegLink() {
                    RegistryValue.Filter filter = RegistryValue.filter().binaries();

                    String name = UUID.randomUUID().toString();
                    assertFalse(filter.matches(name, REG_LINK));
                }

                @Test
                @DisplayName("REG_MULTI_SZ")
                void testRegMultiSZ() {
                    RegistryValue.Filter filter = RegistryValue.filter().binaries();

                    String name = UUID.randomUUID().toString();
                    assertFalse(filter.matches(name, REG_MULTI_SZ));
                }

                @Test
                @DisplayName("REG_RESOURCE_LIST")
                void testRegResourceList() {
                    RegistryValue.Filter filter = RegistryValue.filter().binaries();

                    String name = UUID.randomUUID().toString();
                    assertFalse(filter.matches(name, REG_RESOURCE_LIST));
                }

                @Test
                @DisplayName("REG_FULL_RESOURCE_DESCRIPTOR")
                void testRegFullResourceDescriptor() {
                    RegistryValue.Filter filter = RegistryValue.filter().binaries();

                    String name = UUID.randomUUID().toString();
                    assertFalse(filter.matches(name, REG_FULL_RESOURCE_DESCRIPTOR));
                }

                @Test
                @DisplayName("REG_RESOURCE_REQUIREMENTS_LIST")
                void testRegResourceRequirementsList() {
                    RegistryValue.Filter filter = RegistryValue.filter().binaries();

                    String name = UUID.randomUUID().toString();
                    assertFalse(filter.matches(name, REG_RESOURCE_REQUIREMENTS_LIST));
                }

                @Test
                @DisplayName("REG_QWORD")
                void testRegQWord() {
                    RegistryValue.Filter filter = RegistryValue.filter().binaries();

                    String name = UUID.randomUUID().toString();
                    assertFalse(filter.matches(name, REG_QWORD));
                }

                @Test
                @DisplayName("REG_QWORD_LITTLE_ENDIAN")
                void testRegLittleEndianQWord() {
                    RegistryValue.Filter filter = RegistryValue.filter().binaries();

                    String name = UUID.randomUUID().toString();
                    assertFalse(filter.matches(name, REG_QWORD_LITTLE_ENDIAN));
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
                    assertTrue(filter.matches(name, REG_DWORD));
                }

                @Test
                @DisplayName("REG_DWORD_LITTLE_ENDIAN")
                void testRegLittleEndianDWord() {
                    RegistryValue.Filter filter = RegistryValue.filter().words();

                    String name = UUID.randomUUID().toString();
                    assertTrue(filter.matches(name, REG_DWORD_LITTLE_ENDIAN));
                }

                @Test
                @DisplayName("REG_DWORD_BIG_ENDIAN")
                void testRegBigEndianDWord() {
                    RegistryValue.Filter filter = RegistryValue.filter().words();

                    String name = UUID.randomUUID().toString();
                    assertTrue(filter.matches(name, REG_DWORD_BIG_ENDIAN));
                }

                @Test
                @DisplayName("REG_QWORD")
                void testRegQWord() {
                    RegistryValue.Filter filter = RegistryValue.filter().words();

                    String name = UUID.randomUUID().toString();
                    assertTrue(filter.matches(name, REG_QWORD));
                }

                @Test
                @DisplayName("REG_QWORD_LITTLE_ENDIAN")
                void testRegLittleEndianQWord() {
                    RegistryValue.Filter filter = RegistryValue.filter().words();

                    String name = UUID.randomUUID().toString();
                    assertTrue(filter.matches(name, REG_QWORD_LITTLE_ENDIAN));
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
                    assertFalse(filter.matches(name, REG_NONE));
                }

                @Test
                @DisplayName("REG_SZ")
                void testRegSZ() {
                    RegistryValue.Filter filter = RegistryValue.filter().words();

                    String name = UUID.randomUUID().toString();
                    assertFalse(filter.matches(name, REG_SZ));
                }

                @Test
                @DisplayName("REG_EXPAND_SZ")
                void testRegExpandSZ() {
                    RegistryValue.Filter filter = RegistryValue.filter().words();

                    String name = UUID.randomUUID().toString();
                    assertFalse(filter.matches(name, REG_EXPAND_SZ));
                }

                @Test
                @DisplayName("REG_BINARY")
                void testRegBinary() {
                    RegistryValue.Filter filter = RegistryValue.filter().words();

                    String name = UUID.randomUUID().toString();
                    assertFalse(filter.matches(name, REG_BINARY));
                }

                @Test
                @DisplayName("REG_LINK")
                void testRegLink() {
                    RegistryValue.Filter filter = RegistryValue.filter().words();

                    String name = UUID.randomUUID().toString();
                    assertFalse(filter.matches(name, REG_LINK));
                }

                @Test
                @DisplayName("REG_MULTI_SZ")
                void testRegMultiSZ() {
                    RegistryValue.Filter filter = RegistryValue.filter().words();

                    String name = UUID.randomUUID().toString();
                    assertFalse(filter.matches(name, REG_MULTI_SZ));
                }

                @Test
                @DisplayName("REG_RESOURCE_LIST")
                void testRegResourceList() {
                    RegistryValue.Filter filter = RegistryValue.filter().words();

                    String name = UUID.randomUUID().toString();
                    assertFalse(filter.matches(name, REG_RESOURCE_LIST));
                }

                @Test
                @DisplayName("REG_FULL_RESOURCE_DESCRIPTOR")
                void testRegFullResourceDescriptor() {
                    RegistryValue.Filter filter = RegistryValue.filter().words();

                    String name = UUID.randomUUID().toString();
                    assertFalse(filter.matches(name, REG_FULL_RESOURCE_DESCRIPTOR));
                }

                @Test
                @DisplayName("REG_RESOURCE_REQUIREMENTS_LIST")
                void testRegResourceRequirementsList() {
                    RegistryValue.Filter filter = RegistryValue.filter().words();

                    String name = UUID.randomUUID().toString();
                    assertFalse(filter.matches(name, REG_RESOURCE_REQUIREMENTS_LIST));
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
                    assertTrue(filter.matches(name, REG_SZ));
                }

                @Test
                @DisplayName("REG_EXPAND_SZ")
                void testRegExpandSZ() {
                    RegistryValue.Filter filter = RegistryValue.filter().settable();

                    String name = UUID.randomUUID().toString();
                    assertTrue(filter.matches(name, REG_EXPAND_SZ));
                }

                @Test
                @DisplayName("REG_BINARY")
                void testRegBinary() {
                    RegistryValue.Filter filter = RegistryValue.filter().settable();

                    String name = UUID.randomUUID().toString();
                    assertTrue(filter.matches(name, REG_BINARY));
                }

                @Test
                @DisplayName("REG_DWORD")
                void testRegDWord() {
                    RegistryValue.Filter filter = RegistryValue.filter().settable();

                    String name = UUID.randomUUID().toString();
                    assertTrue(filter.matches(name, REG_DWORD));
                }

                @Test
                @DisplayName("REG_DWORD_LITTLE_ENDIAN")
                void testRegLittleEndianDWord() {
                    RegistryValue.Filter filter = RegistryValue.filter().settable();

                    String name = UUID.randomUUID().toString();
                    assertTrue(filter.matches(name, REG_DWORD_LITTLE_ENDIAN));
                }

                @Test
                @DisplayName("REG_DWORD_BIG_ENDIAN")
                void testRegBigEndianDWord() {
                    RegistryValue.Filter filter = RegistryValue.filter().settable();

                    String name = UUID.randomUUID().toString();
                    assertTrue(filter.matches(name, REG_DWORD_BIG_ENDIAN));
                }

                @Test
                @DisplayName("REG_MULTI_SZ")
                void testRegMultiSZ() {
                    RegistryValue.Filter filter = RegistryValue.filter().settable();

                    String name = UUID.randomUUID().toString();
                    assertTrue(filter.matches(name, REG_MULTI_SZ));
                }

                @Test
                @DisplayName("REG_QWORD")
                void testRegQWord() {
                    RegistryValue.Filter filter = RegistryValue.filter().settable();

                    String name = UUID.randomUUID().toString();
                    assertTrue(filter.matches(name, REG_QWORD));
                }

                @Test
                @DisplayName("REG_QWORD_LITTLE_ENDIAN")
                void testRegLittleEndianQWord() {
                    RegistryValue.Filter filter = RegistryValue.filter().settable();

                    String name = UUID.randomUUID().toString();
                    assertTrue(filter.matches(name, REG_QWORD_LITTLE_ENDIAN));
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
                    assertFalse(filter.matches(name, REG_NONE));
                }

                @Test
                @DisplayName("REG_LINK")
                void testRegLink() {
                    RegistryValue.Filter filter = RegistryValue.filter().settable();

                    String name = UUID.randomUUID().toString();
                    assertFalse(filter.matches(name, REG_LINK));
                }

                @Test
                @DisplayName("REG_RESOURCE_LIST")
                void testRegResourceList() {
                    RegistryValue.Filter filter = RegistryValue.filter().settable();

                    String name = UUID.randomUUID().toString();
                    assertFalse(filter.matches(name, REG_RESOURCE_LIST));
                }

                @Test
                @DisplayName("REG_FULL_RESOURCE_DESCRIPTOR")
                void testRegFullResourceDescriptor() {
                    RegistryValue.Filter filter = RegistryValue.filter().settable();

                    String name = UUID.randomUUID().toString();
                    assertFalse(filter.matches(name, REG_FULL_RESOURCE_DESCRIPTOR));
                }

                @Test
                @DisplayName("REG_RESOURCE_REQUIREMENTS_LIST")
                void testRegResourceRequirementsList() {
                    RegistryValue.Filter filter = RegistryValue.filter().settable();

                    String name = UUID.randomUUID().toString();
                    assertFalse(filter.matches(name, REG_RESOURCE_REQUIREMENTS_LIST));
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
                        assertTrue(filter.matches(name, REG_NONE));
                    }

                    @Test
                    @DisplayName("REG_SZ")
                    void testRegSZ() {
                        RegistryValue.Filter filter = RegistryValue.filter().classes(RegistryValue.class);

                        String name = UUID.randomUUID().toString();
                        assertTrue(filter.matches(name, REG_SZ));
                    }

                    @Test
                    @DisplayName("REG_EXPAND_SZ")
                    void testRegExpandSZ() {
                        RegistryValue.Filter filter = RegistryValue.filter().classes(RegistryValue.class);

                        String name = UUID.randomUUID().toString();
                        assertTrue(filter.matches(name, REG_EXPAND_SZ));
                    }

                    @Test
                    @DisplayName("REG_BINARY")
                    void testRegBinary() {
                        RegistryValue.Filter filter = RegistryValue.filter().classes(RegistryValue.class);

                        String name = UUID.randomUUID().toString();
                        assertTrue(filter.matches(name, REG_BINARY));
                    }

                    @Test
                    @DisplayName("REG_DWORD")
                    void testRegDWord() {
                        RegistryValue.Filter filter = RegistryValue.filter().classes(RegistryValue.class);

                        String name = UUID.randomUUID().toString();
                        assertTrue(filter.matches(name, REG_DWORD));
                    }

                    @Test
                    @DisplayName("REG_DWORD_LITTLE_ENDIAN")
                    void testRegLittleEndianDWord() {
                        RegistryValue.Filter filter = RegistryValue.filter().classes(RegistryValue.class);

                        String name = UUID.randomUUID().toString();
                        assertTrue(filter.matches(name, REG_DWORD_LITTLE_ENDIAN));
                    }

                    @Test
                    @DisplayName("REG_DWORD_BIG_ENDIAN")
                    void testRegBigEndianDWord() {
                        RegistryValue.Filter filter = RegistryValue.filter().classes(RegistryValue.class);

                        String name = UUID.randomUUID().toString();
                        assertTrue(filter.matches(name, REG_DWORD_BIG_ENDIAN));
                    }

                    @Test
                    @DisplayName("REG_LINK")
                    void testRegLink() {
                        RegistryValue.Filter filter = RegistryValue.filter().classes(RegistryValue.class);

                        String name = UUID.randomUUID().toString();
                        assertTrue(filter.matches(name, REG_LINK));
                    }

                    @Test
                    @DisplayName("REG_MULTI_SZ")
                    void testRegMultiSZ() {
                        RegistryValue.Filter filter = RegistryValue.filter().classes(RegistryValue.class);

                        String name = UUID.randomUUID().toString();
                        assertTrue(filter.matches(name, REG_MULTI_SZ));
                    }

                    @Test
                    @DisplayName("REG_RESOURCE_LIST")
                    void testRegResourceList() {
                        RegistryValue.Filter filter = RegistryValue.filter().classes(RegistryValue.class);

                        String name = UUID.randomUUID().toString();
                        assertTrue(filter.matches(name, REG_RESOURCE_LIST));
                    }

                    @Test
                    @DisplayName("REG_FULL_RESOURCE_DESCRIPTOR")
                    void testRegFullResourceDescriptor() {
                        RegistryValue.Filter filter = RegistryValue.filter().classes(RegistryValue.class);

                        String name = UUID.randomUUID().toString();
                        assertTrue(filter.matches(name, REG_FULL_RESOURCE_DESCRIPTOR));
                    }

                    @Test
                    @DisplayName("REG_RESOURCE_REQUIREMENTS_LIST")
                    void testRegResourceRequirementsList() {
                        RegistryValue.Filter filter = RegistryValue.filter().classes(RegistryValue.class);

                        String name = UUID.randomUUID().toString();
                        assertTrue(filter.matches(name, REG_RESOURCE_REQUIREMENTS_LIST));
                    }

                    @Test
                    @DisplayName("REG_QWORD")
                    void testRegQWord() {
                        RegistryValue.Filter filter = RegistryValue.filter().classes(RegistryValue.class);

                        String name = UUID.randomUUID().toString();
                        assertTrue(filter.matches(name, REG_QWORD));
                    }

                    @Test
                    @DisplayName("REG_QWORD_LITTLE_ENDIAN")
                    void testRegLittleEndianQWord() {
                        RegistryValue.Filter filter = RegistryValue.filter().classes(RegistryValue.class);

                        String name = UUID.randomUUID().toString();
                        assertTrue(filter.matches(name, REG_QWORD_LITTLE_ENDIAN));
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
                        assertTrue(filter.matches(name, REG_SZ));
                    }

                    @Test
                    @DisplayName("REG_EXPAND_SZ")
                    void testRegExpandSZ() {
                        RegistryValue.Filter filter = RegistryValue.filter().classes(SettableRegistryValue.class);

                        String name = UUID.randomUUID().toString();
                        assertTrue(filter.matches(name, REG_EXPAND_SZ));
                    }

                    @Test
                    @DisplayName("REG_BINARY")
                    void testRegBinary() {
                        RegistryValue.Filter filter = RegistryValue.filter().classes(SettableRegistryValue.class);

                        String name = UUID.randomUUID().toString();
                        assertTrue(filter.matches(name, REG_BINARY));
                    }

                    @Test
                    @DisplayName("REG_DWORD")
                    void testRegDWord() {
                        RegistryValue.Filter filter = RegistryValue.filter().classes(SettableRegistryValue.class);

                        String name = UUID.randomUUID().toString();
                        assertTrue(filter.matches(name, REG_DWORD));
                    }

                    @Test
                    @DisplayName("REG_DWORD_LITTLE_ENDIAN")
                    void testRegLittleEndianDWord() {
                        RegistryValue.Filter filter = RegistryValue.filter().classes(SettableRegistryValue.class);

                        String name = UUID.randomUUID().toString();
                        assertTrue(filter.matches(name, REG_DWORD_LITTLE_ENDIAN));
                    }

                    @Test
                    @DisplayName("REG_DWORD_BIG_ENDIAN")
                    void testRegBigEndianDWord() {
                        RegistryValue.Filter filter = RegistryValue.filter().classes(SettableRegistryValue.class);

                        String name = UUID.randomUUID().toString();
                        assertTrue(filter.matches(name, REG_DWORD_BIG_ENDIAN));
                    }

                    @Test
                    @DisplayName("REG_MULTI_SZ")
                    void testRegMultiSZ() {
                        RegistryValue.Filter filter = RegistryValue.filter().classes(SettableRegistryValue.class);

                        String name = UUID.randomUUID().toString();
                        assertTrue(filter.matches(name, REG_MULTI_SZ));
                    }

                    @Test
                    @DisplayName("REG_QWORD")
                    void testRegQWord() {
                        RegistryValue.Filter filter = RegistryValue.filter().classes(SettableRegistryValue.class);

                        String name = UUID.randomUUID().toString();
                        assertTrue(filter.matches(name, REG_QWORD));
                    }

                    @Test
                    @DisplayName("REG_QWORD_LITTLE_ENDIAN")
                    void testRegLittleEndianQWord() {
                        RegistryValue.Filter filter = RegistryValue.filter().classes(SettableRegistryValue.class);

                        String name = UUID.randomUUID().toString();
                        assertTrue(filter.matches(name, REG_QWORD_LITTLE_ENDIAN));
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
                        assertFalse(filter.matches(name, REG_NONE));
                    }

                    @Test
                    @DisplayName("REG_LINK")
                    void testRegLink() {
                        RegistryValue.Filter filter = RegistryValue.filter().classes(SettableRegistryValue.class);

                        String name = UUID.randomUUID().toString();
                        assertFalse(filter.matches(name, REG_LINK));
                    }

                    @Test
                    @DisplayName("REG_RESOURCE_LIST")
                    void testRegResourceList() {
                        RegistryValue.Filter filter = RegistryValue.filter().classes(SettableRegistryValue.class);

                        String name = UUID.randomUUID().toString();
                        assertFalse(filter.matches(name, REG_RESOURCE_LIST));
                    }

                    @Test
                    @DisplayName("REG_FULL_RESOURCE_DESCRIPTOR")
                    void testRegFullResourceDescriptor() {
                        RegistryValue.Filter filter = RegistryValue.filter().classes(SettableRegistryValue.class);

                        String name = UUID.randomUUID().toString();
                        assertFalse(filter.matches(name, REG_FULL_RESOURCE_DESCRIPTOR));
                    }

                    @Test
                    @DisplayName("REG_RESOURCE_REQUIREMENTS_LIST")
                    void testRegResourceRequirementsList() {
                        RegistryValue.Filter filter = RegistryValue.filter().classes(SettableRegistryValue.class);

                        String name = UUID.randomUUID().toString();
                        assertFalse(filter.matches(name, REG_RESOURCE_REQUIREMENTS_LIST));
                    }
                }
            }
        }
    }

    @Nested
    @DisplayName("exhaustiveness")
    class Exhaustiveness {

        // The tests in this nested class don't test any behaviour, they test the API for exhaustiveness using the compiler

        @Test
        void testSeparateClasses() {
            RegistryValue value = DWordValue.of("name", 0);
            switch (value) {
                case BinaryValue v -> assertInstanceOf(BinaryValue.class, v);
                case DWordValue v -> assertInstanceOf(DWordValue.class, v);
                case FullResourceDescriptorValue v -> assertInstanceOf(FullResourceDescriptorValue.class, v);
                case LinkValue v -> assertInstanceOf(LinkValue.class, v);
                case MultiStringValue v -> assertInstanceOf(MultiStringValue.class, v);
                case NoneValue v -> assertInstanceOf(NoneValue.class, v);
                case QWordValue v -> assertInstanceOf(QWordValue.class, v);
                case ResourceListValue v -> assertInstanceOf(ResourceListValue.class, v);
                case ResourceRequirementsListValue v -> assertInstanceOf(ResourceRequirementsListValue.class, v);
                case StringValue v -> assertInstanceOf(StringValue.class, v);
            }
        }

        @Test
        void testWithSettableRegistryValue() {
            RegistryValue value = DWordValue.of("name", 0);
            switch (value) {
                case FullResourceDescriptorValue v -> assertInstanceOf(FullResourceDescriptorValue.class, v);
                case LinkValue v -> assertInstanceOf(LinkValue.class, v);
                case NoneValue v -> assertInstanceOf(NoneValue.class, v);
                case ResourceListValue v -> assertInstanceOf(ResourceListValue.class, v);
                case ResourceRequirementsListValue v -> assertInstanceOf(ResourceRequirementsListValue.class, v);
                case SettableRegistryValue v -> testSettableRegistryValue(v);
            }
        }

        private void testSettableRegistryValue(SettableRegistryValue value) {
            switch (value) {
                case BinaryValue v -> assertInstanceOf(BinaryValue.class, v);
                case DWordValue v -> assertInstanceOf(DWordValue.class, v);
                case MultiStringValue v -> assertInstanceOf(MultiStringValue.class, v);
                case QWordValue v -> assertInstanceOf(QWordValue.class, v);
                case StringValue v -> assertInstanceOf(StringValue.class, v);
            }
        }
    }

    static byte[] randomData() {
        Random random = new Random();
        byte[] data = new byte[128];
        random.nextBytes(data);
        return data;
    }

    static MemorySegment randomDataBytePointer(Arena arena) {
        byte[] bytes = randomData();
        return arena.allocateFrom(ValueLayout.JAVA_BYTE, bytes);
    }

    static MemorySegment bytesSegment(Arena arena, int... values) {
        byte[] data = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            data[i] = (byte) values[i];
        }
        return arena.allocateFrom(ValueLayout.JAVA_BYTE, data);
    }

    static byte[] textAsBytes() {
        return textAsBytes(TEXT);
    }

    static MemorySegment textAsSegment(Arena arena) {
        byte[] bytes = textAsBytes();
        return arena.allocateFrom(ValueLayout.JAVA_BYTE, bytes);
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

    static MemorySegment textAsSegment(Arena arena, String... texts) {
        byte[] bytes = textAsBytes(texts);
        return arena.allocateFrom(ValueLayout.JAVA_BYTE, bytes);
    }

    private static byte[] textAsBytes(String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[bytes.length * 2 + 2];
        for (int i = 0; i < bytes.length; i++) {
            result[i * 2] = bytes[i];
        }
        return result;
    }

    static MemorySegment resized(Arena arena, MemorySegment data, long newSize) {
        byte[] bytes = Arrays.copyOf(data.toArray(ValueLayout.JAVA_BYTE), toIntExact(newSize));
        return arena.allocateFrom(ValueLayout.JAVA_BYTE, bytes);
    }

    static void assertContentEquals(MemorySegment expected, MemorySegment actual) {
        assertContentEquals(expected, actual, expected.byteSize());
    }

    static void assertContentEquals(MemorySegment expected, MemorySegment actual, long dataLength) {
        assertArrayEquals(expected.asSlice(0, dataLength).toArray(ValueLayout.JAVA_BYTE),
                actual.asSlice(0, dataLength).toArray(ValueLayout.JAVA_BYTE));
    }
}
