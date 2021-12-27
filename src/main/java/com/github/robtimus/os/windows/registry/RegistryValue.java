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
import com.sun.jna.platform.win32.WinNT;

/**
 * A representation of registry values.
 *
 * @author Rob Spoor
 */
public abstract class RegistryValue {

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

    abstract byte[] rawData();

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
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

    static RegistryValue of(String name, int type, byte[] data, int dataLength) {
        switch (type) {
            case WinNT.REG_NONE:
                return new NoneRegistryValue(name, data, dataLength);
            case WinNT.REG_SZ:
                return new StringRegistryValue(name, data, dataLength);
            case WinNT.REG_EXPAND_SZ:
                return new ExpandableStringRegistryValue(name, data, dataLength);
            case WinNT.REG_BINARY:
                return new BinaryRegistryValue(name, data, dataLength);
            case WinNT.REG_DWORD_LITTLE_ENDIAN:
            case WinNT.REG_DWORD_BIG_ENDIAN:
                return new DWordRegistryValue(name, type, data);
            case WinNT.REG_LINK:
                return new LinkRegistryValue(name, data, dataLength);
            case WinNT.REG_MULTI_SZ:
                return new MultiStringRegistryValue(name, data, dataLength);
            case WinNT.REG_RESOURCE_LIST:
                return new ResourceListRegistryValue(name, data, dataLength);
            case WinNT.REG_FULL_RESOURCE_DESCRIPTOR:
                return new FullResourceDescriptorRegistryValue(name, data, dataLength);
            case WinNT.REG_RESOURCE_REQUIREMENTS_LIST:
                return new ResourceRequirementsListRegistryValue(name, data, dataLength);
            case WinNT.REG_QWORD_LITTLE_ENDIAN:
                return new QWordRegistryValue(name, data);
            default:
                throw new IllegalStateException(Messages.RegistryValue.unsupportedType.get(type));
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

        private static Class<? extends RegistryValue>[] registryClasses() {
            @SuppressWarnings("unchecked")
            Class<? extends RegistryValue>[] classes = new Class[WinNT.REG_QWORD_LITTLE_ENDIAN + 1];
            classes[WinNT.REG_NONE] = NoneRegistryValue.class;
            classes[WinNT.REG_SZ] = StringRegistryValue.class;
            classes[WinNT.REG_EXPAND_SZ] = ExpandableStringRegistryValue.class;
            classes[WinNT.REG_BINARY] = BinaryRegistryValue.class;
            classes[WinNT.REG_DWORD_LITTLE_ENDIAN] = DWordRegistryValue.class;
            classes[WinNT.REG_DWORD_BIG_ENDIAN] = DWordRegistryValue.class;
            classes[WinNT.REG_LINK] = LinkRegistryValue.class;
            classes[WinNT.REG_MULTI_SZ] = MultiStringRegistryValue.class;
            classes[WinNT.REG_RESOURCE_LIST] = ResourceListRegistryValue.class;
            classes[WinNT.REG_FULL_RESOURCE_DESCRIPTOR] = FullResourceDescriptorRegistryValue.class;
            classes[WinNT.REG_RESOURCE_REQUIREMENTS_LIST] = ResourceRequirementsListRegistryValue.class;
            classes[WinNT.REG_QWORD_LITTLE_ENDIAN] = QWordRegistryValue.class;
            return classes;
        }

        private Filter() {
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
         * This method is shorthand for calling {@link #classes(Class...)} with class literals for {@link StringRegistryValue},
         * {@link ExpandableStringRegistryValue} and {@link MultiStringRegistryValue}.
         *
         * @return This filter.
         */
        public Filter strings() {
            return classes(StringRegistryValue.class, ExpandableStringRegistryValue.class, MultiStringRegistryValue.class);
        }

        /**
         * Specifies that all binary registry values should be included.
         * This method is shorthand for calling {@link #classes(Class...)} with a class literal for {@link BinaryRegistryValue}.
         *
         * @return This filter.
         */
        public Filter binaries() {
            return classes(BinaryRegistryValue.class);
        }

        /**
         * Specifies that all WORD (or numeric) registry values should be included.
         * This method is shorthand for calling {@link #classes(Class...)} with class literals for {@link DWordRegistryValue} and
         * {@link QWordRegistryValue}.
         *
         * @return This filter.
         */
        public Filter words() {
            return classes(DWordRegistryValue.class, QWordRegistryValue.class);
        }

        boolean matches(String name, int type) {
            return matches(name) && matches(type);
        }

        private boolean matches(String name) {
            return namePredicate == null || namePredicate.test(name);
        }

        private boolean matches(int type) {
            return valueClasses == null || valueClasses.contains(REGISTRY_CLASSES[type]) || valueClasses.contains(RegistryValue.class);
        }
    }
}
