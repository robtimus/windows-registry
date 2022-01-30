/*
 * StringValueTest.java
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

import static com.github.robtimus.os.windows.registry.RegistryValueTest.TEXT;
import static com.github.robtimus.os.windows.registry.RegistryValueTest.textAsBytes;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import com.sun.jna.platform.win32.WinNT;

@SuppressWarnings("nls")
class StringValueTest {

    @Nested
    @DisplayName("value")
    class Value {

        @Test
        @DisplayName("from String")
        void testFromString() {
            StringValue value = StringValue.of("test", TEXT);

            assertEquals(TEXT, value.value());
        }

        @Test
        @DisplayName("from bytes")
        void testFromBytes() {
            byte[] bytes = textAsBytes();
            StringValue value = new StringValue("test", WinNT.REG_SZ, bytes, bytes.length);

            assertEquals(TEXT, value.value());
        }
    }

    @Nested
    @DisplayName("isExpandable")
    class IsExpandable {

        @Test
        @DisplayName("not expandable")
        void testFromString() {
            StringValue value = StringValue.of("test", TEXT);

            assertFalse(value.isExpandable());
        }

        @Test
        @DisplayName("expandable")
        void testFromBytes() {
            StringValue value = StringValue.expandableOf("test", TEXT);

            assertTrue(value.isExpandable());
        }
    }

    @Nested
    @DisplayName("expandedValue")
    class ExpandedValue {

        @Test
        @DisplayName("not expandable")
        void testNotExpandable() {
            Map<String, String> getenv = System.getenv();

            String text = getenv.keySet().stream()
                    .limit(3)
                    .collect(Collectors.joining("%, %", "Three values: %", "%"));

            StringValue value = StringValue.of("test", text);
            assertThrows(IllegalStateException.class, value::expandedValue);
        }

        @Nested
        @DisplayName("expandable")
        class Expandable {

            @Test
            @DisplayName("with environment variables")
            // No need to mock anything, just make sure we run on Windows
            @EnabledOnOs(OS.WINDOWS)
            void testWithEnvironmentVairables() {
                Map<String, String> getenv = System.getenv();

                String text = getenv.keySet().stream()
                        .limit(3)
                        .collect(Collectors.joining("%, %", "Three values: %", "%"));
                String expectedValue = getenv.values().stream()
                        .limit(3)
                        .collect(Collectors.joining(", ", "Three values: ", ""));

                StringValue value = StringValue.expandableOf("test", text);
                assertEquals(expectedValue, value.expandedValue());
            }

            @Test
            @DisplayName("without environment variables")
            // No need to mock anything, just make sure we run on Windows
            @EnabledOnOs(OS.WINDOWS)
            void testWithoutEnvironmentVairables() {
                StringValue value = StringValue.expandableOf("test", TEXT);
                assertEquals(TEXT, value.expandedValue());
            }
        }
    }

    @Nested
    @DisplayName("rawData")
    class RawData {

        @Test
        @DisplayName("from String")
        void testFromString() {
            StringValue value = StringValue.of("test", TEXT);

            assertArrayEquals(textAsBytes(), value.rawData());
        }

        @Test
        @DisplayName("from bytes")
        void testFromBytes() {
            byte[] bytes = textAsBytes();
            StringValue value = new StringValue("test", WinNT.REG_SZ, bytes, bytes.length);

            assertArrayEquals(bytes, value.rawData());
        }
    }

    @Nested
    @DisplayName("withName")
    class WithName {

        @Test
        @DisplayName("same name")
        void testSameName() {
            StringValue value = StringValue.of("test", TEXT);

            StringValue otherValue = value.withName("test");

            assertEquals(value, otherValue);
        }

        @Test
        @DisplayName("different name")
        void testDifferentName() {
            StringValue value = StringValue.of("test", TEXT);

            StringValue otherValue = value.withName("test2");

            assertNotEquals(value, otherValue);
            assertEquals("test2", otherValue.name());
            assertEquals(value.value(), otherValue.value());
        }
    }

    @Nested
    @DisplayName("withValue")
    class WithValue {

        @Nested
        @DisplayName("not expandable")
        class NotExpandable {

            @Test
            @DisplayName("same value")
            void testSameValue() {
                StringValue value = StringValue.of("test", TEXT);

                StringValue otherValue = value.withValue(TEXT);

                assertEquals(value, otherValue);
            }

            @Test
            @DisplayName("different value")
            void testDifferentValue() {
                StringValue value = StringValue.of("test", TEXT);

                StringValue otherValue = value.withValue(TEXT + TEXT);

                assertNotEquals(value, otherValue);
                assertEquals(value.name(), otherValue.name());
                assertEquals(TEXT + TEXT, otherValue.value());
            }
        }

        @Nested
        @DisplayName("expandable")
        class Expandable {

            @Test
            @DisplayName("same value")
            void testSameValue() {
                StringValue value = StringValue.expandableOf("test", TEXT);

                StringValue otherValue = value.withValue(TEXT);

                assertNotEquals(value, otherValue);
                assertEquals(value.name(), otherValue.name());
                assertEquals(value.value(), otherValue.value());
            }

            @Test
            @DisplayName("different value")
            void testDifferentValue() {
                StringValue value = StringValue.expandableOf("test", TEXT);

                StringValue otherValue = value.withValue(TEXT + TEXT);

                assertNotEquals(value, otherValue);
                assertEquals(value.name(), otherValue.name());
                assertEquals(TEXT + TEXT, otherValue.value());
            }
        }
    }

    @Nested
    @DisplayName("withExpandableValue")
    class WithExpandableValue {

        @Nested
        @DisplayName("not expandable")
        class NotExpandable {

            @Test
            @DisplayName("same value")
            void testSameValue() {
                StringValue value = StringValue.of("test", TEXT);

                StringValue otherValue = value.withExpandableValue(TEXT);

                assertNotEquals(value, otherValue);
                assertEquals(value.name(), otherValue.name());
                assertEquals(value.value(), otherValue.value());
            }

            @Test
            @DisplayName("different value")
            void testDifferentValue() {
                StringValue value = StringValue.of("test", TEXT);

                StringValue otherValue = value.withExpandableValue(TEXT + TEXT);

                assertNotEquals(value, otherValue);
                assertEquals(value.name(), otherValue.name());
                assertEquals(TEXT + TEXT, otherValue.value());
            }
        }

        @Nested
        @DisplayName("expandable")
        class Expandable {

            @Test
            @DisplayName("same value")
            void testSameValue() {
                StringValue value = StringValue.expandableOf("test", TEXT);

                StringValue otherValue = value.withExpandableValue(TEXT);

                assertEquals(value, otherValue);
            }

            @Test
            @DisplayName("different value")
            void testDifferentValue() {
                StringValue value = StringValue.expandableOf("test", TEXT);

                StringValue otherValue = value.withExpandableValue(TEXT + TEXT);

                assertNotEquals(value, otherValue);
                assertEquals(value.name(), otherValue.name());
                assertEquals(TEXT + TEXT, otherValue.value());
            }
        }
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("equalsArguments")
    @DisplayName("equals")
    void testEquals(StringValue value, Object other, boolean expected) {
        assertEquals(expected, value.equals(other));
    }

    static Arguments[] equalsArguments() {
        byte[] data = textAsBytes();
        StringValue value = StringValue.of("test", TEXT);

        return new Arguments[] {
                arguments(value, value, true),
                arguments(value, StringValue.of("test", TEXT), true),
                arguments(value, new StringValue("test", WinNT.REG_SZ, data, data.length), true),
                arguments(value, new StringValue("test", WinNT.REG_EXPAND_SZ, data, data.length), false),
                arguments(value, new StringValue("test", WinNT.REG_SZ, Arrays.copyOf(data, data.length + 10), data.length), true),
                arguments(value, StringValue.of("test2", TEXT), false),
                arguments(value, StringValue.of("test", TEXT.substring(0, TEXT.length() - 1)), false),
                arguments(value, new StringValue("test", WinNT.REG_SZ, data, data.length - 4), false),
                arguments(value, "foo", false),
                arguments(value, null, false),
        };
    }

    @Test
    @DisplayName("hashCode")
    void testHashCode() {
        StringValue value = StringValue.of("test", TEXT);

        assertEquals(value.hashCode(), value.hashCode());
        assertEquals(value.hashCode(), StringValue.of("test", TEXT).hashCode());
    }
}
