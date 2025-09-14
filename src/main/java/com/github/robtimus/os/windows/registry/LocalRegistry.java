/*
 * LocalRegistry.java
 * Copyright 2025 Rob Spoor
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

/**
 * A representation of the local Windows registry.
 *
 * @author Rob Spoor
 * @since 2.0
 */
public final class LocalRegistry extends Registry {

    static final LocalRegistry INSTANCE = new LocalRegistry();

    // CHECKSTYLE:OFF: MemberName

    /** The HKEY_CLASSES_ROOT root key. */
    public final RegistryKey HKEY_CLASSES_ROOT = LocalRootKey.HKEY_CLASSES_ROOT; // NOSONAR

    /** The HKEY_CURRENT_USER root key. */
    public final RegistryKey HKEY_CURRENT_USER = LocalRootKey.HKEY_CURRENT_USER; // NOSONAR

    /** The HKEY_LOCAL_MACHINE root key. */
    public final RegistryKey HKEY_LOCAL_MACHINE = LocalRootKey.HKEY_LOCAL_MACHINE; // NOSONAR

    /** The HKEY_USERS root key. */
    public final RegistryKey HKEY_USERS = LocalRootKey.HKEY_USERS; // NOSONAR

    /** The HKEY_CURRENT_CONFIG root key. */
    public final RegistryKey HKEY_CURRENT_CONFIG = LocalRootKey.HKEY_CURRENT_CONFIG; // NOSONAR

    // CHECKSTYLE:ON: MemberName

    private LocalRegistry() {
    }
}
