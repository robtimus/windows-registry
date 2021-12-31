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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.win32.W32APITypeMapper;

@SuppressWarnings("nls")
final class RegistryValueTest {

    static final String TEXT = "Lorem ipsum dolor sit amet, consectetuer adipiscing elit. Aenean commodo ligula eget dolor. Aenean massa.";

    private RegistryValueTest() {
    }

    @Nested
    @DisplayName("of")
    class Of {

        @Test
        @DisplayName("REG_NONE")
        void testNone() {
            byte[] data = randomData();

            RegistryValue value = RegistryValue.of("test", WinNT.REG_NONE, data, data.length);
            assertInstanceOf(NoneRegistryValue.class, value);
            assertEquals("test", value.name());
            assertEquals(WinNT.REG_NONE, value.type());
        }

        @Test
        @DisplayName("REG_SZ")
        void testString() {
            byte[] data = textAsBytes();

            RegistryValue value = RegistryValue.of("test", WinNT.REG_SZ, data, data.length);
            StringRegistryValue stringValue = assertInstanceOf(StringRegistryValue.class, value);
            assertEquals("test", value.name());
            assertEquals(WinNT.REG_SZ, value.type());
            assertArrayEquals(data, stringValue.rawData());
        }

        @Test
        @DisplayName("REG_EXPAND_SZ")
        void testExpandableString() {
            byte[] data = textAsBytes();

            RegistryValue value = RegistryValue.of("test", WinNT.REG_EXPAND_SZ, data, data.length);
            ExpandableStringRegistryValue expandableStringValue = assertInstanceOf(ExpandableStringRegistryValue.class, value);
            assertEquals("test", value.name());
            assertEquals(WinNT.REG_EXPAND_SZ, value.type());
            assertArrayEquals(data, expandableStringValue.rawData());
        }

        @Test
        @DisplayName("REG_BINARY")
        void testBinary() {
            byte[] data = randomData();

            RegistryValue value = RegistryValue.of("test", WinNT.REG_BINARY, data, data.length);
            BinaryRegistryValue binaryValue = assertInstanceOf(BinaryRegistryValue.class, value);
            assertEquals("test", value.name());
            assertEquals(WinNT.REG_BINARY, value.type());
            assertArrayEquals(data, binaryValue.rawData());
        }

        @Test
        @DisplayName("REG_DWORD")
        void testDWord() {
            byte[] data = { 1, 2, 3, 4, };

            RegistryValue value = RegistryValue.of("test", WinNT.REG_DWORD, data, data.length);
            DWordRegistryValue dWordValue = assertInstanceOf(DWordRegistryValue.class, value);
            assertEquals("test", value.name());
            assertEquals(WinNT.REG_DWORD, value.type());
            assertArrayEquals(data, dWordValue.rawData());
        }

        @Test
        @DisplayName("REG_DWORD_LITTLE_ENDIAN")
        void testLittleEndianDWord() {
            byte[] data = { 1, 2, 3, 4, };

            RegistryValue value = RegistryValue.of("test", WinNT.REG_DWORD_LITTLE_ENDIAN, data, data.length);
            DWordRegistryValue dWordValue = assertInstanceOf(DWordRegistryValue.class, value);
            assertEquals("test", value.name());
            assertEquals(WinNT.REG_DWORD_LITTLE_ENDIAN, value.type());
            assertArrayEquals(data, dWordValue.rawData());
        }

        @Test
        @DisplayName("REG_DWORD_BIG_ENDIAN")
        void testBigEndianDWord() {
            byte[] data = { 1, 2, 3, 4, };

            RegistryValue value = RegistryValue.of("test", WinNT.REG_DWORD_BIG_ENDIAN, data, data.length);
            DWordRegistryValue dWordValue = assertInstanceOf(DWordRegistryValue.class, value);
            assertEquals("test", value.name());
            assertEquals(WinNT.REG_DWORD_BIG_ENDIAN, value.type());
            assertArrayEquals(data, dWordValue.rawData());
        }

        @Test
        @DisplayName("REG_LINK")
        void testLink() {
            byte[] data = randomData();

            RegistryValue value = RegistryValue.of("test", WinNT.REG_LINK, data, data.length);
            assertInstanceOf(LinkRegistryValue.class, value);
            assertEquals("test", value.name());
            assertEquals(WinNT.REG_LINK, value.type());
        }

        @Test
        @DisplayName("REG_MULTI_SZ")
        void testMultiString() {
            byte[] data = textAsBytes("value1", "value2", "value3");

            RegistryValue value = RegistryValue.of("test", WinNT.REG_MULTI_SZ, data, data.length);
            MultiStringRegistryValue multiStringValue = assertInstanceOf(MultiStringRegistryValue.class, value);
            assertEquals("test", value.name());
            assertEquals(WinNT.REG_MULTI_SZ, value.type());
            assertArrayEquals(data, multiStringValue.rawData());
        }

        @Test
        @DisplayName("REG_RESOURCE_LIST")
        void testResourceList() {
            byte[] data = randomData();

            RegistryValue value = RegistryValue.of("test", WinNT.REG_RESOURCE_LIST, data, data.length);
            assertInstanceOf(ResourceListRegistryValue.class, value);
            assertEquals("test", value.name());
            assertEquals(WinNT.REG_RESOURCE_LIST, value.type());
        }

        @Test
        @DisplayName("REG_FULL_RESOURCE_DESCRIPTOR")
        void testFullResourceDescriptor() {
            byte[] data = randomData();

            RegistryValue value = RegistryValue.of("test", WinNT.REG_FULL_RESOURCE_DESCRIPTOR, data, data.length);
            assertInstanceOf(FullResourceDescriptorRegistryValue.class, value);
            assertEquals("test", value.name());
            assertEquals(WinNT.REG_FULL_RESOURCE_DESCRIPTOR, value.type());
        }

        @Test
        @DisplayName("REG_RESOURCE_REQUIREMENTS_LIST")
        void testResourceRequirementsList() {
            byte[] data = randomData();

            RegistryValue value = RegistryValue.of("test", WinNT.REG_RESOURCE_REQUIREMENTS_LIST, data, data.length);
            assertInstanceOf(ResourceRequirementsListRegistryValue.class, value);
            assertEquals("test", value.name());
            assertEquals(WinNT.REG_RESOURCE_REQUIREMENTS_LIST, value.type());
        }

        @Test
        @DisplayName("REG_QWORD")
        void testQWord() {
            byte[] data = { 1, 2, 3, 4, 5, 6, 7, 8, };

            RegistryValue value = RegistryValue.of("test", WinNT.REG_QWORD, data, data.length);
            QWordRegistryValue qWordValue = assertInstanceOf(QWordRegistryValue.class, value);
            assertEquals("test", value.name());
            assertEquals(WinNT.REG_QWORD, value.type());
            assertArrayEquals(data, qWordValue.rawData());
        }

        @Test
        @DisplayName("REG_QWORD_LITTLE_ENDIAN")
        void testLittleEndianQWord() {
            byte[] data = { 1, 2, 3, 4, 5, 6, 7, 8, };

            RegistryValue value = RegistryValue.of("test", WinNT.REG_QWORD_LITTLE_ENDIAN, data, data.length);
            QWordRegistryValue qWordValue = assertInstanceOf(QWordRegistryValue.class, value);
            assertEquals("test", value.name());
            assertEquals(WinNT.REG_QWORD_LITTLE_ENDIAN, value.type());
            assertArrayEquals(data, qWordValue.rawData());
        }

        @Test
        @DisplayName("unsupported type")
        void testUnsupportedType() {
            byte[] data = randomData();

            assertThrows(IllegalStateException.class, () -> RegistryValue.of("test", -1, data, data.length));
        }
    }

    static byte[] randomData() {
        Random random = new Random();
        byte[] data = new byte[128];
        random.nextBytes(data);
        return data;
    }

    static byte[] textAsBytes() {
        return textAsBytes(TEXT);
    }

    static byte[] textAsBytes(String... texts) {
        byte[] result = {};
        for (String text : texts) {
            byte[] textResult = textAsBytes(text);
            byte[] newResult = Arrays.copyOf(result, result.length + textResult.length);
            System.arraycopy(textResult, 0, newResult, result.length, textResult.length);
            result = newResult;
        }
        return W32APITypeMapper.DEFAULT == W32APITypeMapper.UNICODE
                ? Arrays.copyOf(result, result.length + 2)
                : Arrays.copyOf(result, result.length + 1);
    }

    static byte[] textAsBytes(String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (W32APITypeMapper.DEFAULT == W32APITypeMapper.UNICODE) {
            byte[] result = new byte[bytes.length * 2 + 2];
            for (int i = 0; i < bytes.length; i++) {
                result[i * 2] = bytes[i];
            }
            return result;
        }
        return Arrays.copyOf(bytes, bytes.length + 1);
    }
}
