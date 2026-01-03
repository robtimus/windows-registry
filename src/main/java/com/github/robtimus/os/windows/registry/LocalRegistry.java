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
@SuppressWarnings("squid:S6548")
public final class LocalRegistry extends Registry {

    static final LocalRegistry INSTANCE = new LocalRegistry();

    /** The HKEY_CLASSES_ROOT root key. */
    @SuppressWarnings({ "checkstyle:MemberName", "squid:S116", "squid:S1170" })
    public final RegistryKey HKEY_CLASSES_ROOT = LocalRootKey.HKEY_CLASSES_ROOT;

    /** The HKEY_CURRENT_USER root key. */
    @SuppressWarnings({ "checkstyle:MemberName", "squid:S116", "squid:S1170" })
    public final RegistryKey HKEY_CURRENT_USER = LocalRootKey.HKEY_CURRENT_USER;

    /** The HKEY_LOCAL_MACHINE root key. */
    @SuppressWarnings({ "checkstyle:MemberName", "squid:S116", "squid:S1170" })
    public final RegistryKey HKEY_LOCAL_MACHINE = LocalRootKey.HKEY_LOCAL_MACHINE;

    /** The HKEY_USERS root key. */
    @SuppressWarnings({ "checkstyle:MemberName", "squid:S116", "squid:S1170" })
    public final RegistryKey HKEY_USERS = LocalRootKey.HKEY_USERS;

    /** The HKEY_CURRENT_CONFIG root key. */
    @SuppressWarnings({ "checkstyle:MemberName", "squid:S116", "squid:S1170" })
    public final RegistryKey HKEY_CURRENT_CONFIG = LocalRootKey.HKEY_CURRENT_CONFIG;

    // CHECKSTYLE:ON: MemberName

    private LocalRegistry() {
    }
}
