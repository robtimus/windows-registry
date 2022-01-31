# windows-registry

Provides classes and interfaces for working with the Windows registry.

## Entry point

The entry point for accessing the Windows registry is one of the constants of class [RegistryKey](https://robtimus.github.io/windows-registry/apidocs/com.github.robtimus.os.windows.registry/com/github/robtimus/os/windows/registry/RegistryKey.html). From these, use the `resolve` method to get the registry key you need. Note that registry key names can contain forward slashes, so you need to use backslashes to separate keys. From there you can access the registry key's sub keys and values.

## Registry values

Unlike several other libraries for working with the Windows registry, registry values are not returned as strings. Instead, class [RegistryValue](https://robtimus.github.io/windows-registry/apidocs/com.github.robtimus.os.windows.registry/com/github/robtimus/os/windows/registry/RegistryValue.html) defines several sub classes, one for each of the known registry value types. For instance, [StringValue](https://robtimus.github.io/windows-registry/apidocs/com.github.robtimus.os.windows.registry/com/github/robtimus/os/windows/registry/StringValue.html) is used for string values, [DWordValue](https://robtimus.github.io/windows-registry/apidocs/com.github.robtimus.os.windows.registry/com/github/robtimus/os/windows/registry/DWordValue.html) for DWORD (int) values, etc. This allows you to retrieve and set values using proper types, instead of having to convert everything to and from strings.

In addition to retrieving registry values as instances of `RegistryValue` or a sub class of `RegistryValue`, class `RegistryKey` provides some utility methods to retrieve registry values as string, DWORD (int) or QWORD (int), as these are considered the most used types.

## Handles

Operations can be directly called on registry keys. However, for non-root keys this opens a connection to the Windows registry for every operation. If you need to perform several operations on a single registry key, you should consider calling one of its `handle` methods. The returned handle allows you to perform the same operations with the same method signatures using a single connection to the Windows registry.

## Remote registries

Using class [RemoteRegistryKey](https://robtimus.github.io/windows-registry/apidocs/com.github.robtimus.os.windows.registry/com/github/robtimus/os/windows/registry/RemoteRegistryKey.html) you can connect to the Windows registry on a remote machine, provided the user the JVM is running as has the rights to do so. Apart from the entry point, remote registry keys work exactly like regular registry keys. This includes using the `resolve` method.

## Implementation details

Interaction with the Windows registry is done through [JNA](https://github.com/java-native-access/jna). This has some benefits:

* Native interaction with the Windows registry instead of using commands like `REG QUERY` provides better performance.
* Native interaction with the Windows registry instead of piggybacking on Java internal classes like `java.util.Preferences` provides greater compatibility between Java versions.
* JNA is bundled with its libraries for interacting with the Windows platform. There is no need to install any libraries manually.
