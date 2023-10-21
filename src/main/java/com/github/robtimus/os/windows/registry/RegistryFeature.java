/*
 * RegistryFeature.java
 * Copyright 2023 Rob Spoor
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

import com.github.robtimus.os.windows.registry.foreign.Advapi32;

/**
 * An enumeration over optional registry features.
 *
 * @author Rob Spoor
 * @since 2.0
 */
public enum RegistryFeature {

    /** {@link RegistryKey#renameTo(String) renaming} registry keys. */
    RENAME_KEY(Advapi32.INSTANCE.isRegRenameKeyEnabled()),
    ;

    private final boolean enabled;

    RegistryFeature(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Returns whether or not the registry feature is enabled.
     *
     * @return {@code true} if the registry feature is enabled, or {@code false} otherwise.
     */
    public boolean isEnabled() {
        return enabled;
    }
}
