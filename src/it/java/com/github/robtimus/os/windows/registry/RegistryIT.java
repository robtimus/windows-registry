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
import com.sun.jna.platform.win32.WinNT;

@SuppressWarnings("nls")
class RegistryIT {

    @BeforeEach
    void assureDocker() {
        // By only running tests in Docker, any bugs will not mess up the host system
        String userName = System.getProperty("user.name");
        assumeTrue("ContainerUser".equalsIgnoreCase(userName), "Must run in Docker; current user name: " + userName);
    }

    @Nested
    @DisplayName("HKEY_LOCAL_MACHINE")
    class HKLM {

        @Test
        @DisplayName("Windows build")
        void testWindowsBuild() {
            RegistryKey registryKey = RegistryKey.HKEY_LOCAL_MACHINE.resolve("SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion");
            RegistryValue registryValue = registryKey.getValue("CurrentBuild").orElseThrow();
            String buildNumber = assertInstanceOf(StringRegistryValue.class, registryValue).value();
            String expected = readBuildNumber();
            assertEquals(expected, buildNumber);
        }

        private String readBuildNumber() {
            String output = executeProcess(new ProcessBuilder("cmd", "/C", "ver"));

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
            RegistryKey registryKey = RegistryKey.HKEY_CURRENT_USER.resolve("Software\\JavaSoft\\windows-registry");

            // Assert that it doesn't exist; queryKey will throw an exception
            assertFalse(registryKey.exists());
            assertThrows(ProcessExecutionException.class, () -> queryKey(registryKey));

            // Create and verify that it now exists
            assertTrue(registryKey.createAll());
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

                handle.setValue(new ExpandableStringRegistryValue("path", "%PATH%"));
                ExpandableStringRegistryValue value = handle.getValue("path")
                        .map(ExpandableStringRegistryValue.class::cast)
                        .orElseThrow();
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

            List<RegistryKey> allKeys = registryKey.traverse().collect(Collectors.toList());
            assertThat(allKeys, containsInAnyOrder(registryKey, subKey1, subKey2));
            assertEquals(registryKey, allKeys.get(0));

            assertThrows(RegistryAccessDeniedException.class, registryKey::delete);
            assertThrows(RegistryAccessDeniedException.class, registryKey::deleteIfExists);

            assertTrue(registryKey.exists());
            assertTrue(subKey1.exists());
            assertTrue(subKey2.exists());
            queryKey(registryKey).assertSubKeys("subKey", "sub key");

            registryKey.traverse(TraverseOption.SUB_KEYS_FIRST)
                    // don't delete immediately, as that has unspecified effects on the stream, as documented
                    .collect(Collectors.toList())
                    .stream()
                    .forEach(RegistryKey::delete);

            assertFalse(registryKey.exists());
            assertFalse(subKey1.exists());
            assertFalse(subKey2.exists());
            assertThrows(ProcessExecutionException.class, () -> queryKey(registryKey));
        }

        private void testValues(RegistryKey registryKey) {
            assertEquals(Collections.emptySet(), registryKey.values().collect(Collectors.toSet()));

            assertEquals(Optional.empty(), registryKey.getValue(UUID.randomUUID().toString()));

            StringRegistryValue stringValue = new StringRegistryValue("string-value", "Lorem ipsum");
            MultiStringRegistryValue multiStringValue = new MultiStringRegistryValue("multi-string-value", "value1", "value2", "value3");
            DWordRegistryValue dwordValue = new DWordRegistryValue("dword-value", 13);
            QWordRegistryValue qwordValue = new QWordRegistryValue("qword-value", 481);
            BinaryRegistryValue binaryValue = new BinaryRegistryValue("binary-value", "Hello World".getBytes());

            registryKey.setValue(stringValue);
            registryKey.setValue(multiStringValue);
            registryKey.setValue(dwordValue);
            registryKey.setValue(qwordValue);
            registryKey.setValue(binaryValue);

            assertEquals(Optional.of(stringValue), registryKey.getValue("string-value"));
            assertEquals(Optional.of(multiStringValue), registryKey.getValue("multi-string-value"));
            assertEquals(Optional.of(dwordValue), registryKey.getValue("dword-value"));
            assertEquals(Optional.of(qwordValue), registryKey.getValue("qword-value"));
            assertEquals(Optional.of(binaryValue), registryKey.getValue("binary-value"));

            assertEquals(Optional.of(stringValue), registryKey.values().filter(v -> "string-value".equals(v.name())).findAny());
            assertEquals(Optional.of(multiStringValue), registryKey.values().filter(v -> "multi-string-value".equals(v.name())).findAny());
            assertEquals(Optional.of(dwordValue), registryKey.values().filter(v -> "dword-value".equals(v.name())).findAny());
            assertEquals(Optional.of(qwordValue), registryKey.values().filter(v -> "qword-value".equals(v.name())).findAny());
            assertEquals(Optional.of(binaryValue), registryKey.values().filter(v -> "binary-value".equals(v.name())).findAny());

            assertEquals(Set.of(stringValue, multiStringValue), registryKey.values(RegistryValue.filter().strings()).collect(Collectors.toSet()));

            queryKey(registryKey).assertValues(stringValue, multiStringValue, dwordValue, qwordValue, binaryValue);

            registryKey.deleteValue("binary-value");
            queryKey(registryKey).assertValues(stringValue, multiStringValue, dwordValue, qwordValue);
            assertFalse(registryKey.deleteValueIfExists("binary-value"));
            assertThrows(NoSuchRegistryValueException.class, () -> registryKey.deleteValue("binary-value"));

            try (RegistryKey.Handle handle = registryKey.handle()) {
                assertThrows(RegistryAccessDeniedException.class, () -> handle.setValue(binaryValue));
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
                .collect(Collectors.toList());

        List<ValueInfo> values = lines.stream()
                .filter(s -> Character.isWhitespace(s.charAt(0)))
                .map(ValueInfo::new)
                .collect(Collectors.toList());

        List<String> subKeys = lines.stream()
                .filter(s -> s.startsWith(path + "\\"))
                .map(s -> s.substring(path.length() + 1))
                .collect(Collectors.toList());

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
            name = matcher.group(1);
            type = ValueType.valueOf(matcher.group(2));
            value = matcher.group(3);
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
        REG_DWORD_BIG_ENDIAN(WinNT.REG_DWORD_BIG_ENDIAN, s -> Integer.decode(s).toString()),
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
    }

    @SuppressWarnings("serial")
    private static final class ProcessExecutionException extends RuntimeException {

        private ProcessExecutionException(int exitValue) {
            super("Command exited with value " + exitValue);
        }
    }
}
