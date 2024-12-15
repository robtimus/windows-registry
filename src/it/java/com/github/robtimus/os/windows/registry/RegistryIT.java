/*
 * RegistryIT.java
 * Copyright 2022 Rob Spoor
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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.matchesRegex;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import com.github.robtimus.os.windows.registry.RegistryKey.HandleOption;
import com.github.robtimus.os.windows.registry.RegistryKey.TraverseOption;
import com.github.robtimus.os.windows.registry.foreign.WinNT;

@SuppressWarnings("nls")
class RegistryIT {

    @BeforeEach
    void assureDocker() {
        if (!Boolean.getBoolean("allow-non-dockerized-registry-tests")) {
            // By only running tests in Docker, any bugs will not mess up the host system
            String userName = System.getProperty("user.name");
            assumeTrue("ContainerUser".equalsIgnoreCase(userName), "Must run in Docker; current user name: " + userName);
        }
    }

    @Nested
    @DisplayName("HKEY_LOCAL_MACHINE")
    class HKLM {

        @Test
        @DisplayName("Windows build")
        void testWindowsBuild() {
            RegistryKey registryKey = RegistryKey.HKEY_LOCAL_MACHINE.resolve("SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion");
            RegistryValue registryValue = registryKey.getValue("CurrentBuild", RegistryValue.class);
            String buildNumber = assertInstanceOf(StringValue.class, registryValue).value();
            String expected = readBuildNumber();
            assertEquals(expected, buildNumber);
        }

        @Nested
        @DisplayName("remote")
        class Remote {

            @Test
            @DisplayName("Windows build")
            void testWindowsBuild() {
                String hostName = readHostName();
                try (RemoteRegistryKey remoteRegistryKey = RemoteRegistryKey.HKEY_LOCAL_MACHINE.at(hostName)) {
                    RegistryKey registryKey = remoteRegistryKey.resolve("SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion");
                    RegistryValue registryValue = registryKey.getValue("CurrentBuild", RegistryValue.class);
                    String buildNumber = assertInstanceOf(StringValue.class, registryValue).value();
                    String expected = readBuildNumber();
                    assertEquals(expected, buildNumber);
                }
            }

            private String readHostName() {
                String output = executeProcess("cmd", "/C", "ipconfig", "/all");

                return Pattern.compile("\\r?\\n").splitAsStream(output)
                        .filter(line -> line.contains("Host Name"))
                        .map(line -> line.substring(line.indexOf(':') + 1).trim())
                        .findAny()
                        .orElseThrow(() -> new IllegalStateException("Could not read host name from output: " + output));
            }
        }

        private String readBuildNumber() {
            String output = executeProcess("cmd", "/C", "ver");

            // The build number is the 3rd part, e.g. 19042 in "Microsoft Windows [Version 10.0.19042.1469]"
            Pattern versionPattern = Pattern.compile("Microsoft Windows \\[Version \\d+\\.\\d+\\.(\\d+)\\.\\d+\\]");
            Matcher matcher = versionPattern.matcher(output);
            assertTrue(matcher.matches(), "Version does not match pattern: " + output);
            return matcher.group(1);
        }
    }

    @Nested
    @DisplayName("HKEY_CURRENT_USER")
    class HKCU {

        @Test
        @DisplayName("registry")
        void testRegistry() {
            RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER.resolve("Software\\JavaSoft\\windows-registry\\registry");

            // Assert that it doesn't exist; queryKey will throw an exception
            assertFalse(registryKey.exists());
            assertThrows(ProcessExecutionException.class, () -> queryKey(registryKey));

            // Create and verify that it now exists
            assertTrue(registryKey.createIfNotExists());
            assertTrue(registryKey.exists());

            // Verify that there are no values or sub keys
            KeyInfo keyInfo = queryKey(registryKey);
            keyInfo.assertValues();
            keyInfo.assertSubKeys();

            // Test values
            testValues(registryKey);

            // First sub key, using createIfNotExists
            RegistryKey subKey1 = registryKey.resolve("subKey");

            assertFalse(subKey1.exists());
            assertTrue(subKey1.createIfNotExists());
            assertFalse(subKey1.createIfNotExists());
            assertTrue(subKey1.exists());

            // Second sub key, using create
            // Also use a name with spaces
            RegistryKey subKey2 = registryKey.resolve("sub key");

            assertFalse(subKey2.exists());
            subKey2.create();
            assertThrows(RegistryKeyAlreadyExistsException.class, subKey2::create);

            // Third sub key, using handle
            // Also use a name with /
            RegistryKey subKey3 = registryKey.resolve("sub/key");

            assertFalse(subKey3.exists());
            assertThrows(NoSuchRegistryKeyException.class, subKey3::handle);
            try (RegistryKey.Handle handle = subKey3.handle(HandleOption.CREATE, HandleOption.MANAGE_VALUES)) {
                assertTrue(subKey3.exists());

                handle.setValue(StringValue.expandableOf("path", "%PATH%"));
                StringValue value = handle.getValue("path", StringValue.class);
                assertEquals(System.getenv("PATH"), value.expandedValue());
            }

            queryKey(registryKey).assertSubKeys("subKey", "sub key", "sub/key");
            assertEquals(Set.of(subKey1, subKey2, subKey3), registryKey.subKeys().collect(Collectors.toSet()));

            // Delete the third key
            subKey3.delete();
            assertFalse(subKey3.exists());
            assertFalse(subKey3.deleteIfExists());
            assertThrows(NoSuchRegistryKeyException.class, subKey3::delete);

            queryKey(registryKey).assertSubKeys("subKey", "sub key");

            // Traverse with sub keys last
            List<RegistryKey> allKeys = registryKey.traverse().toList();
            assertThat(allKeys, containsInAnyOrder(registryKey, subKey1, subKey2));
            assertEquals(registryKey, allKeys.get(0));

            // Assert that deleting the registry key fails if it has children
            assertThrows(RegistryAccessDeniedException.class, registryKey::delete);
            assertThrows(RegistryAccessDeniedException.class, registryKey::deleteIfExists);

            // Traverse with sub keys first, and delete the tree
            // First assert that the tree still exists
            assertTrue(registryKey.exists());
            assertTrue(subKey1.exists());
            assertTrue(subKey2.exists());
            queryKey(registryKey).assertSubKeys("subKey", "sub key");

            registryKey.traverse(TraverseOption.SUB_KEYS_FIRST)
                    // don't delete immediately, as that has unspecified effects on the stream, as documented
                    .toList()
                    .stream()
                    .forEach(RegistryKey::delete);

            // Now assert that the tree no longer exists
            assertFalse(registryKey.exists());
            assertFalse(subKey1.exists());
            assertFalse(subKey2.exists());
            assertThrows(ProcessExecutionException.class, () -> queryKey(registryKey));
        }

        private void testValues(RegistryKey registryKey) {
            assertEquals(Collections.emptySet(), registryKey.values().collect(Collectors.toSet()));

            assertEquals(Optional.empty(), registryKey.findValue(UUID.randomUUID().toString(), RegistryValue.class));

            StringValue stringValue = StringValue.of("string-value", "Lorem ipsum");
            MultiStringValue multiStringValue = MultiStringValue.of("multi-string-value", "value1", "value2", "value3");
            StringValue expandableStringValue = StringValue.expandableOf("expandable-string-value", "%PATH%");
            DWordValue dwordValue = DWordValue.of("dword-value", 13);
            DWordValue beDWordValue = DWordValue.bigEndianOf("be-dword-value", 26);
            DWordValue leDWordValue = DWordValue.littleEndianOf("le-dword-value", 26);
            QWordValue qwordValue = QWordValue.of("qword-value", 481);
            BinaryValue binaryValue = BinaryValue.of("binary-value", "Hello World".getBytes());
            StringValue defaultValue = StringValue.of(RegistryValue.DEFAULT, "default");

            registryKey.setValue(stringValue);
            registryKey.setValue(multiStringValue);
            registryKey.setValue(expandableStringValue);
            registryKey.setValue(dwordValue);
            registryKey.setValue(beDWordValue);
            registryKey.setValue(leDWordValue);
            registryKey.setValue(qwordValue);
            registryKey.setValue(binaryValue);
            registryKey.setValue(defaultValue);

            assertEquals(stringValue, registryKey.getValue("string-value", RegistryValue.class));
            assertEquals(multiStringValue, registryKey.getValue("multi-string-value", RegistryValue.class));
            assertEquals(expandableStringValue, registryKey.getValue("expandable-string-value", RegistryValue.class));
            assertEquals(dwordValue, registryKey.getValue("dword-value", RegistryValue.class));
            assertEquals(beDWordValue, registryKey.getValue("be-dword-value", RegistryValue.class));
            assertEquals(leDWordValue, registryKey.getValue("le-dword-value", RegistryValue.class));
            assertEquals(qwordValue, registryKey.getValue("qword-value", RegistryValue.class));
            assertEquals(binaryValue, registryKey.getValue("binary-value", RegistryValue.class));
            assertEquals(defaultValue, registryKey.getValue(RegistryValue.DEFAULT, RegistryValue.class));

            assertEquals(Optional.of(stringValue), registryKey.findValue("string-value", RegistryValue.class));
            assertEquals(Optional.of(multiStringValue), registryKey.findValue("multi-string-value", RegistryValue.class));
            assertEquals(Optional.of(expandableStringValue), registryKey.findValue("expandable-string-value", RegistryValue.class));
            assertEquals(Optional.of(dwordValue), registryKey.findValue("dword-value", RegistryValue.class));
            assertEquals(Optional.of(beDWordValue), registryKey.findValue("be-dword-value", RegistryValue.class));
            assertEquals(Optional.of(leDWordValue), registryKey.findValue("le-dword-value", RegistryValue.class));
            assertEquals(Optional.of(qwordValue), registryKey.findValue("qword-value", RegistryValue.class));
            assertEquals(Optional.of(binaryValue), registryKey.findValue("binary-value", RegistryValue.class));
            assertEquals(Optional.of(defaultValue), registryKey.findValue(RegistryValue.DEFAULT, RegistryValue.class));

            assertEquals(Optional.of(stringValue), registryKey.values().filter(v -> "string-value".equals(v.name())).findAny());
            assertEquals(Optional.of(multiStringValue), registryKey.values().filter(v -> "multi-string-value".equals(v.name())).findAny());
            assertEquals(Optional.of(expandableStringValue), registryKey.values().filter(v -> "expandable-string-value".equals(v.name())).findAny());
            assertEquals(Optional.of(dwordValue), registryKey.values().filter(v -> "dword-value".equals(v.name())).findAny());
            assertEquals(Optional.of(beDWordValue), registryKey.values().filter(v -> "be-dword-value".equals(v.name())).findAny());
            assertEquals(Optional.of(leDWordValue), registryKey.values().filter(v -> "le-dword-value".equals(v.name())).findAny());
            assertEquals(Optional.of(qwordValue), registryKey.values().filter(v -> "qword-value".equals(v.name())).findAny());
            assertEquals(Optional.of(binaryValue), registryKey.values().filter(v -> "binary-value".equals(v.name())).findAny());
            assertEquals(Optional.of(defaultValue), registryKey.values().filter(v -> RegistryValue.DEFAULT.equals(v.name())).findAny());

            assertEquals(Set.of(stringValue, multiStringValue, expandableStringValue, defaultValue),
                    registryKey.values(RegistryValue.filter().strings()).collect(Collectors.toSet()));
            assertEquals(Set.of(dwordValue, beDWordValue, leDWordValue, qwordValue),
                    registryKey.values(RegistryValue.filter().words()).collect(Collectors.toSet()));

            queryKey(registryKey).assertValues(stringValue, multiStringValue, expandableStringValue, dwordValue, beDWordValue, leDWordValue,
                    qwordValue, binaryValue, defaultValue);

            registryKey.deleteValue("binary-value");
            queryKey(registryKey).assertValues(stringValue, multiStringValue, expandableStringValue, dwordValue, beDWordValue, leDWordValue,
                    qwordValue, defaultValue);
            assertFalse(registryKey.deleteValueIfExists("binary-value"));
            assertThrows(NoSuchRegistryValueException.class, () -> registryKey.deleteValue("binary-value"));

            try (RegistryKey.Handle handle = registryKey.handle()) {
                assertThrows(RegistryAccessDeniedException.class, () -> handle.setValue(binaryValue));
            }
        }

        @Test
        @DisplayName("rename")
        void testRename() {
            // Prepare the registry key and some content
            RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER.resolve("Software\\JavaSoft\\windows-registry\\rename");
            registryKey.createIfNotExists();

            List<String> subKeys = List.of("subKey1", "subKey2", "subKey3");
            subKeys.forEach(s -> registryKey.resolve(s).create());

            registryKey.setValue(StringValue.of("string", "test"));

            // Rename
            RegistryKey renamedRegistryKey = registryKey.renameTo("renamed");
            assertEquals(registryKey.resolve("..\\renamed"), renamedRegistryKey);

            // Assert that the content is available under the renamed key, and not under the old key
            assertTrue(renamedRegistryKey.exists());
            for (String subKey : subKeys) {
                assertTrue(renamedRegistryKey.resolve(subKey).exists());
            }
            assertEquals(Optional.of("test"), renamedRegistryKey.findStringValue("string"));

            assertFalse(registryKey.exists());
            for (String subKey : subKeys) {
                assertFalse(registryKey.resolve(subKey).exists());
            }
            assertThrows(NoSuchRegistryKeyException.class, () -> registryKey.findStringValue("string"));

            // Assert that renaming a non-existing key fails
            RegistryException exception = assertThrows(NoSuchRegistryKeyException.class, () -> registryKey.renameTo("renamed"));
            assertEquals(registryKey.path(), exception.path());

            // Assert that renaming to an existing key fails
            registryKey.create();
            exception = assertThrows(RegistryKeyAlreadyExistsException.class, () -> renamedRegistryKey.renameTo("rename"));
            assertEquals(registryKey.path(), exception.path());
        }

        @Nested
        @DisplayName("invalid handle states")
        class InvalidHandleStates {

            @Test
            @DisplayName("use after closing")
            @SuppressWarnings("resource")
            void testUseAfterClosing() {
                RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER.resolve("Software\\JavaSoft\\windows-registry\\invalid-handle-states");
                registryKey.createIfNotExists();

                RegistryKey.Handle handle;
                try (RegistryKey.Handle h = registryKey.handle()) {
                    handle = h;
                }

                assertThrows(InvalidRegistryHandleException.class, () -> handle.findValue("non-existing", RegistryValue.class));
            }

            @Test
            @DisplayName("delete key during use")
            void testDeleteKeyDuringUse() {
                RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER.resolve("Software\\JavaSoft\\windows-registry\\invalid-handle-states");
                registryKey.createIfNotExists();

                try (RegistryKey.Handle handle = registryKey.handle(HandleOption.MANAGE_VALUES)) {
                    handle.setValue(StringValue.of("string", "test"));

                    registryKey.delete();

                    assertThrows(NoSuchRegistryKeyException.class, () -> handle.findStringValue("string"));
                }
            }
        }
    }

    private String executeProcess(String... command) {
        return executeProcess(new ProcessBuilder(command));
    }

    private String executeProcess(ProcessBuilder processBuilder) {
        try {
            Process process = processBuilder
                    .redirectErrorStream(true)
                    .start();

            StringWriter output = new StringWriter();
            try (Reader reader = new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)) {
                reader.transferTo(output);
            }

            int exitValue = process.exitValue();
            if (exitValue != 0) {
                throw new ProcessExecutionException(exitValue);
            }

            return output.toString().trim();

        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private KeyInfo queryKey(RegistryKey registryKey) {
        return queryKey(registryKey.path());
    }

    private KeyInfo queryKey(String path) {
        String output = executeProcess("REG", "QUERY", path);

        // Output format:
        // - If there are values:
        //   <full path>
        //       <name>  <type>  <value> (for each value)
        // - If there are sub keys:
        //   <full path> (for each sub key)

        List<String> lines = Pattern.compile("\r?\n").splitAsStream(output)
                .filter(s -> !s.trim().isEmpty())
                .toList();

        List<ValueInfo> values = lines.stream()
                .filter(s -> Character.isWhitespace(s.charAt(0)))
                .map(ValueInfo::new)
                .toList();

        List<String> subKeys = lines.stream()
                .filter(s -> s.startsWith(path + "\\"))
                .map(s -> s.substring(path.length() + 1))
                .toList();

        return new KeyInfo(values, subKeys);
    }

    private static final class KeyInfo {

        private final Map<String, ValueInfo> values;
        private final Set<String> subKeys;

        private KeyInfo(List<ValueInfo> values, List<String> subKeys) {
            this.values = values.stream().collect(Collectors.toMap(v -> v.name, Function.identity()));
            this.subKeys = new HashSet<>(subKeys);
        }

        private void assertValues(RegistryValue... registryValues) {
            Map<String, RegistryValue> valueMap = Arrays.stream(registryValues)
                    .collect(Collectors.toMap(RegistryValue::name, Function.identity()));

            assertEquals(valueMap.keySet(), values.keySet());

            for (Map.Entry<String, RegistryValue> entry : valueMap.entrySet()) {
                ValueInfo valueInfo = values.get(entry.getKey());
                valueInfo.type.assertValue(entry.getValue(), valueInfo.value);
            }
        }

        private void assertSubKeys(String... subKeyNames) {
            Set<String> subKeySet = Arrays.stream(subKeyNames).collect(Collectors.toSet());

            assertEquals(subKeySet, subKeys);
        }
    }

    private static final class ValueInfo {

        private static final Pattern VALUE_PATTERN = Pattern.compile("\\s+(.*?)\\s+(REG_[_A-Z]+)\\s+(.*)");

        private final String name;
        private final ValueType type;
        private final String value;

        private ValueInfo(String line) {
            Matcher matcher = VALUE_PATTERN.matcher(line);
            assertTrue(matcher.matches());
            name = fixName(matcher.group(1));
            type = ValueType.valueOf(matcher.group(2));
            value = matcher.group(3);
        }

        private String fixName(String matchedName) {
            return "(Default)".equals(matchedName) ? RegistryValue.DEFAULT : matchedName;
        }
    }

    private enum ValueType {
        REG_NONE(WinNT.REG_NONE),
        REG_SZ(WinNT.REG_SZ),
        REG_EXPAND_SZ(WinNT.REG_EXPAND_SZ),
        // Binary: values are in upper case and are missing leading 0x
        REG_BINARY(WinNT.REG_BINARY, s -> "0x" + s.toLowerCase()),
        // DWORD: values are hex-encoded
        REG_DWORD(WinNT.REG_DWORD, s -> Integer.decode(s).toString()),
        REG_DWORD_LITTLE_ENDIAN(WinNT.REG_DWORD_LITTLE_ENDIAN, s -> Integer.decode(s).toString()),
        REG_DWORD_BIG_ENDIAN(WinNT.REG_DWORD_BIG_ENDIAN, ValueType::convertBigEndian),
        REG_LINK(WinNT.REG_LINK),
        // Multi string: values are separated with \0
        REG_MULTI_SZ(WinNT.REG_MULTI_SZ, s -> "[" + s.replace("\\0", ", ") + "]"),
        REG_RESOURCE_LIST(WinNT.REG_RESOURCE_LIST),
        REG_FULL_RESOURCE_DESCRIPTOR(WinNT.REG_FULL_RESOURCE_DESCRIPTOR),
        REG_RESOURCE_REQUIREMENTS_LIST(WinNT.REG_RESOURCE_REQUIREMENTS_LIST),
        // QWORD: values are hex-encoded
        REG_QWORD(WinNT.REG_QWORD, s -> Long.decode(s).toString()),
        REG_QWORD_LITTLE_ENDIAN(WinNT.REG_QWORD_LITTLE_ENDIAN, s -> Long.decode(s).toString()),
        ;

        private final int value;
        private final UnaryOperator<String> valueMapper;

        ValueType(int value) {
            this.value = value;
            this.valueMapper = UnaryOperator.identity();
        }

        ValueType(int value, UnaryOperator<String> valueMapper) {
            this.value = value;
            this.valueMapper = valueMapper;
        }

        private void assertValue(RegistryValue registryValue, String valueString) {
            assertEquals(registryValue.type(), value);

            String expected = registryValue.name() + "=" + valueMapper.apply(valueString);
            assertEquals(expected, registryValue.toString());
        }

        private static String convertBigEndian(String value) {
            Pattern pattern = Pattern.compile("0x([0-9a-z]{2})([0-9a-z]{2})([0-9a-z]{2})([0-9a-z]{2})", Pattern.CASE_INSENSITIVE);
            assertThat(value, matchesRegex(pattern));
            Matcher matcher = pattern.matcher(value);
            assertTrue(matcher.matches());
            String convertedValue = "0x" + matcher.group(4) + matcher.group(3) + matcher.group(2) + matcher.group(1);
            return Integer.decode(convertedValue).toString();
        }
    }

    @SuppressWarnings("serial")
    private static final class ProcessExecutionException extends RuntimeException {

        private ProcessExecutionException(int exitValue) {
            super("Command exited with value " + exitValue);
        }
    }
}
