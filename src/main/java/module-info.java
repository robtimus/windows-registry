/*
 * module-info.java
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

/**
 * Provides classes and interfaces for working with Windows specific features.
 *
 * @author Rob Spoor
 */
module com.github.robtimus.os.windows {
    requires static transitive java.desktop;
    requires com.sun.jna;
    requires com.sun.jna.platform;

    exports com.github.robtimus.os.windows;
    exports com.github.robtimus.os.windows.registry;
    exports com.github.robtimus.os.windows.service;
    exports com.github.robtimus.os.windows.window;
}
