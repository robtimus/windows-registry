/*
 * QWordRegistryTest.java
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

@SuppressWarnings("nls")
class QWordRegistryTest {

    @Nested
    @DisplayName("value")
    class Value {

        @Test
        @DisplayName("from value")
        void testFromValue() {
            QWordValue value = new QWordValue("test", 578437695752307201L);

            assertEquals(578437695752307201L, value.value());
        }

        @Test
        @DisplayName("from bytes")
        void testFromBytes() {
            byte[] bytes = { 1, 2, 3, 4, 5, 6, 7, 8, };
            QWordValue value = new QWordValue("test", bytes);

            assertEquals(578437695752307201L, value.value());
        }
    }

    @Nested
    @DisplayName("rawData")
    class RawData {

        @Test
        @DisplayName("from long")
        void testFromLong() {
            byte[] bytes = { 1, 2, 3, 4, 5, 6, 7, 8, };
            QWordValue value = new QWordValue("test", 578437695752307201L);

            assertArrayEquals(bytes, value.rawData());
        }

        @Test
        @DisplayName("from bytes")
        void testFromBytes() {
            byte[] bytes = { 1, 2, 3, 4, 5, 6, 7, 8, };
            QWordValue value = new QWordValue("test", bytes);

            assertArrayEquals(bytes, value.rawData());
        }
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("equalsArguments")
    @DisplayName("equals")
    void testEquals(QWordValue value, Object other, boolean expected) {
        assertEquals(expected, value.equals(other));
    }

    static Arguments[] equalsArguments() {
        byte[] data = { 1, 2, 3, 4, 5, 6, 7, 8, };
        byte[] otherData = { 1, 2, 3, 4, 5, 6, 7, 0, };
        QWordValue value = new QWordValue("test", data);

        return new Arguments[] {
                arguments(value, value, true),
                arguments(value, new QWordValue("test", data), true),
                arguments(value, new QWordValue("test", 578437695752307201L), true),
                arguments(value, new QWordValue("test", otherData), false),
                arguments(value, new QWordValue("test2", data), false),
                arguments(value, new QWordValue("test", 578437695752307200L), false),
                arguments(value, "foo", false),
                arguments(value, null, false),
        };
    }

    @Test
    @DisplayName("hashCode")
    void testHashCode() {
        QWordValue value = new QWordValue("test", 123456);

        assertEquals(value.hashCode(), value.hashCode());
        assertEquals(value.hashCode(), new QWordValue("test", 123456).hashCode());
    }
}
