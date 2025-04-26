/*
 * MultiStringValueTest.java
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

import static com.github.robtimus.os.windows.registry.RegistryValueTest.assertContentEquals;
import static com.github.robtimus.os.windows.registry.RegistryValueTest.resized;
import static com.github.robtimus.os.windows.registry.RegistryValueTest.textAsSegment;
import static com.github.robtimus.os.windows.registry.foreign.ForeignTestUtils.ALLOCATOR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import java.lang.foreign.MemorySegment;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@SuppressWarnings("nls")
class MultiStringValueTest {

    private static final String VALUE1 = "value1";
    private static final String VALUE2 = "value2";
    private static final String VALUE3 = "value3";

    @Nested
    @DisplayName("value")
    class Value {

        @Test
        @DisplayName("from Strings")
        void testFromString() {
            MultiStringValue value = MultiStringValue.of("test", VALUE1, VALUE2, VALUE3);

            assertEquals(List.of(VALUE1, VALUE2, VALUE3), value.values());
        }

        @Test
        @DisplayName("from memory segment")
        void testFromBytePointer() {
            MemorySegment data = textAsSegment(VALUE1, VALUE2, VALUE3);
            MultiStringValue value = new MultiStringValue("test", data, data.byteSize());

            assertEquals(List.of(VALUE1, VALUE2, VALUE3), value.values());
        }
    }

    @Nested
    @DisplayName("rawData")
    class RawData {

        @Test
        @DisplayName("from Strings")
        void testFromString() {
            MultiStringValue value = MultiStringValue.of("test", VALUE1, VALUE2, VALUE3);

            assertContentEquals(textAsSegment(VALUE1, VALUE2, VALUE3), value.rawData(ALLOCATOR));
        }

        @Test
        @DisplayName("from memory segment")
        void testFromBytes() {
            MemorySegment data = textAsSegment(VALUE1, VALUE2, VALUE3);
            MultiStringValue value = new MultiStringValue("test", data, data.byteSize());

            assertContentEquals(data, value.rawData(ALLOCATOR));
        }
    }

    @Nested
    @DisplayName("withName")
    class WithName {

        @Test
        @DisplayName("same name")
        void testSameName() {
            MultiStringValue value = MultiStringValue.of("test", VALUE1, VALUE2, VALUE3);

            MultiStringValue otherValue = value.withName("test");

            assertEquals(value, otherValue);
        }

        @Test
        @DisplayName("different name")
        void testDifferentName() {
            MultiStringValue value = MultiStringValue.of("test", VALUE1, VALUE2, VALUE3);

            MultiStringValue otherValue = value.withName("test2");

            assertNotEquals(value, otherValue);
            assertEquals("test2", otherValue.name());
            assertEquals(value.values(), otherValue.values());
        }
    }

    @Nested
    @DisplayName("withValues")
    class WithValues {

        @Test
        @DisplayName("same values")
        void testSameValues() {
            MultiStringValue value = MultiStringValue.of("test", VALUE1, VALUE2, VALUE3);

            MultiStringValue otherValue = value.withValues(VALUE1, VALUE2, VALUE3);

            assertEquals(value, otherValue);
        }

        @Test
        @DisplayName("different values")
        void testDifferentValues() {
            MultiStringValue value = MultiStringValue.of("test", VALUE1, VALUE2, VALUE3);

            MultiStringValue otherValue = value.withValues(VALUE3, VALUE2, VALUE1);

            assertNotEquals(value, otherValue);
            assertEquals(value.name(), otherValue.name());
            assertEquals(List.of(VALUE3, VALUE2, VALUE1), otherValue.values());
        }

        @Test
        @DisplayName("empty value")
        void testEmptyValue() {
            MultiStringValue value = MultiStringValue.of("test", VALUE1, VALUE2, VALUE3);

            assertThrows(IllegalArgumentException.class, () -> value.withValues(VALUE3, VALUE2, VALUE1, ""));
        }
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("equalsArguments")
    @DisplayName("equals")
    void testEquals(MultiStringValue value, Object other, boolean expected) {
        assertEquals(expected, value.equals(other));
    }

    static Arguments[] equalsArguments() {
        MemorySegment data = textAsSegment(VALUE1, VALUE2, VALUE3);
        MultiStringValue value = MultiStringValue.of("test", VALUE1, VALUE2, VALUE3);

        return new Arguments[] {
                arguments(value, value, true),
                arguments(value, MultiStringValue.of("test", VALUE1, VALUE2, VALUE3), true),
                arguments(value, new MultiStringValue("test", data, data.byteSize()), true),
                arguments(value, new MultiStringValue("test", resized(data, data.byteSize() + 10), data.byteSize()), true),
                arguments(value, MultiStringValue.of("test2", VALUE1, VALUE2, VALUE3), false),
                arguments(value, MultiStringValue.of("test", VALUE1, VALUE2), false),
                arguments(value, "foo", false),
                arguments(value, null, false),
        };
    }

    @Test
    @DisplayName("hashCode")
    void testHashCode() {
        MultiStringValue value = MultiStringValue.of("test", VALUE1, VALUE2, VALUE3);

        assertEquals(value.hashCode(), value.hashCode());
        assertEquals(value.hashCode(), MultiStringValue.of("test", VALUE1, VALUE2, VALUE3).hashCode());
    }
}
