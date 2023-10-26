/*
 * RegistryValue.java
 * Copyright 2020 Rob Spoor
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

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import com.github.robtimus.os.windows.registry.foreign.BytePointer;
import com.github.robtimus.os.windows.registry.foreign.WinNT;

/**
 * A representation of registry values.
 *
 * @author Rob Spoor
 */
public abstract sealed class RegistryValue
        permits SettableRegistryValue, FullResourceDescriptorValue, LinkValue, NoneValue, ResourceListValue, ResourceRequirementsListValue {

    /** The name of the default value of registry keys. */
    public static final String DEFAULT = ""; //$NON-NLS-1$

    private final String name;
    private final int type;

    RegistryValue(String name, int type) {
        this.name = Objects.requireNonNull(name);
        this.type = type;
    }

    /**
     * Returns the name of the registry value.
     *
     * @return The name of the registry value.
     */
    public String name() {
        return name;
    }

    int type() {
        return type;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || o.getClass() != getClass()) {
            return false;
        }
        RegistryValue other = (RegistryValue) o;
        return name.equals(other.name)
                && type == other.type;
    }

    @Override
    public int hashCode() {
        int hash = name.hashCode();
        hash = 31 * hash + type;
        return hash;
    }

    static RegistryValue of(String name, int type, BytePointer data, int dataLength) {
        switch (type) {
            case WinNT.REG_NONE:
                return new NoneValue(name, data, dataLength);
            case WinNT.REG_SZ, WinNT.REG_EXPAND_SZ:
                return new StringValue(name, type, data, dataLength);
            case WinNT.REG_BINARY:
                return new BinaryValue(name, data, dataLength);
            case WinNT.REG_DWORD_LITTLE_ENDIAN, WinNT.REG_DWORD_BIG_ENDIAN:
                return new DWordValue(name, type, data);
            case WinNT.REG_LINK:
                return new LinkValue(name, data, dataLength);
            case WinNT.REG_MULTI_SZ:
                return new MultiStringValue(name, data, dataLength);
            case WinNT.REG_RESOURCE_LIST:
                return new ResourceListValue(name, data, dataLength);
            case WinNT.REG_FULL_RESOURCE_DESCRIPTOR:
                return new FullResourceDescriptorValue(name, data, dataLength);
            case WinNT.REG_RESOURCE_REQUIREMENTS_LIST:
                return new ResourceRequirementsListValue(name, data, dataLength);
            case WinNT.REG_QWORD_LITTLE_ENDIAN:
                return new QWordValue(name, data);
            default:
                throw new IllegalStateException(Messages.RegistryValue.unsupportedType(type));
        }
    }

    /**
     * Returns a new filter for registry values.
     *
     * @return A new filter for registry values.
     */
    public static Filter filter() {
        return new Filter();
    }

    /**
     * A filter for registry values.
     *
     * @author Rob Spoor
     */
    public static final class Filter {

        private static final Class<? extends RegistryValue>[] REGISTRY_CLASSES = registryClasses();

        private Predicate<? super String> namePredicate;
        private Set<Class<? extends RegistryValue>> valueClasses;

        private Filter() {
        }

        private static Class<? extends RegistryValue>[] registryClasses() {
            @SuppressWarnings("unchecked")
            Class<? extends RegistryValue>[] classes = new Class[WinNT.REG_QWORD_LITTLE_ENDIAN + 1];
            classes[WinNT.REG_NONE] = NoneValue.class;
            classes[WinNT.REG_SZ] = StringValue.class;
            classes[WinNT.REG_EXPAND_SZ] = StringValue.class;
            classes[WinNT.REG_BINARY] = BinaryValue.class;
            classes[WinNT.REG_DWORD_LITTLE_ENDIAN] = DWordValue.class;
            classes[WinNT.REG_DWORD_BIG_ENDIAN] = DWordValue.class;
            classes[WinNT.REG_LINK] = LinkValue.class;
            classes[WinNT.REG_MULTI_SZ] = MultiStringValue.class;
            classes[WinNT.REG_RESOURCE_LIST] = ResourceListValue.class;
            classes[WinNT.REG_FULL_RESOURCE_DESCRIPTOR] = FullResourceDescriptorValue.class;
            classes[WinNT.REG_RESOURCE_REQUIREMENTS_LIST] = ResourceRequirementsListValue.class;
            classes[WinNT.REG_QWORD_LITTLE_ENDIAN] = QWordValue.class;
            return classes;
        }

        /**
         * Specifies a predicate for the name. Only registry values that match the predicate will be returned.
         *
         * @param namePredicate The predicate for the name, or {@code null} to not use a predicate.
         * @return This filter.
         */
        public Filter name(Predicate<? super String> namePredicate) {
            this.namePredicate = namePredicate;
            return this;
        }

        /**
         * Specifies registry value classes for which to include instances.
         * <p>
         * This method is additive; calling it multiple times will add classes for which to include instances.
         * However, if this method is never called, instances of all registry value classes will be returned.
         *
         * @param classes The classes for which to include instances.
         * @return This filter.
         */
        @SafeVarargs
        public final Filter classes(Class<? extends RegistryValue>... classes) {
            if (valueClasses == null) {
                valueClasses = new HashSet<>();
            }
            Collections.addAll(valueClasses, classes);
            return this;
        }

        /**
         * Specifies that all string registry values should be included.
         * This method is shorthand for calling {@link #classes(Class...)} with class literals for {@link StringValue} and {@link MultiStringValue}.
         *
         * @return This filter.
         */
        public Filter strings() {
            return classes(StringValue.class, MultiStringValue.class);
        }

        /**
         * Specifies that all binary registry values should be included.
         * This method is shorthand for calling {@link #classes(Class...)} with a class literal for {@link BinaryValue}.
         *
         * @return This filter.
         */
        public Filter binaries() {
            return classes(BinaryValue.class);
        }

        /**
         * Specifies that all WORD (or numeric) registry values should be included.
         * This method is shorthand for calling {@link #classes(Class...)} with class literals for {@link DWordValue} and {@link QWordValue}.
         *
         * @return This filter.
         */
        public Filter words() {
            return classes(DWordValue.class, QWordValue.class);
        }

        /**
         * Specifies that all settable registry values should be included.
         * This method is shorthand for calling {@link #classes(Class...)} with class literals for all the {@link SettableRegistryValue} sub classes.
         *
         * @return This filter.
         */
        public Filter settable() {
            return strings().binaries().words();
        }

        boolean matches(String name, int type) {
            return matches(name) && matches(type);
        }

        private boolean matches(String name) {
            return namePredicate == null || namePredicate.test(name);
        }

        private boolean matches(int type) {
            return valueClasses == null || hasMatchingClass(type) || valueClasses.contains(RegistryValue.class);
        }

        private boolean hasMatchingClass(int type) {
            Class<? extends RegistryValue> valueClass = REGISTRY_CLASSES[type];
            return valueClasses.contains(valueClass)
                    || SettableRegistryValue.class.isAssignableFrom(valueClass) && valueClasses.contains(SettableRegistryValue.class);
        }
    }
}
