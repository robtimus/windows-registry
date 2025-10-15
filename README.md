# windows-registry
[![Maven Central](https://img.shields.io/maven-central/v/com.github.robtimus/windows-registry)](https://search.maven.org/artifact/com.github.robtimus/windows-registry)
[![Build Status](https://github.com/robtimus/windows-registry/actions/workflows/build.yml/badge.svg)](https://github.com/robtimus/windows-registry/actions/workflows/build.yml)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=com.github.robtimus%3Awindows-registry&metric=alert_status)](https://sonarcloud.io/summary/overall?id=com.github.robtimus%3Awindows-registry)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=com.github.robtimus%3Awindows-registry&metric=coverage)](https://sonarcloud.io/summary/overall?id=com.github.robtimus%3Awindows-registry)
[![Known Vulnerabilities](https://snyk.io/test/github/robtimus/windows-registry/badge.svg)](https://snyk.io/test/github/robtimus/windows-registry)

Provides classes and interfaces for working with the Windows registry.

## Entry point

The entry point for accessing the Windows registry is method [Registry.local()](https://robtimus.github.io/windows-registry/apidocs/com.github.robtimus.os.windows.registry/com/github/robtimus/os/windows/registry/Registry.html#local\(\)). It returns a reference to the local Windows registry. This reference contains constants for each of the available root registry keys. From these, use the `resolve` method to get the registry key you need. Note that registry key names can contain forward slashes, so you need to use backslashes to separate keys. From there you can access the registry key's sub keys and values.

## Registry values

Unlike several other libraries for working with the Windows registry, registry values are not returned as strings. Instead, class [RegistryValue](https://robtimus.github.io/windows-registry/apidocs/com.github.robtimus.os.windows.registry/com/github/robtimus/os/windows/registry/RegistryValue.html) defines several sub classes, one for each of the known registry value types. For instance, [StringValue](https://robtimus.github.io/windows-registry/apidocs/com.github.robtimus.os.windows.registry/com/github/robtimus/os/windows/registry/StringValue.html) is used for string values, [DWordValue](https://robtimus.github.io/windows-registry/apidocs/com.github.robtimus.os.windows.registry/com/github/robtimus/os/windows/registry/DWordValue.html) for DWORD (int) values, etc. This allows you to retrieve and set values using proper types, instead of having to convert everything to and from strings.

In addition to retrieving registry values as instances of `RegistryValue` or a sub class of `RegistryValue`, class `RegistryKey` provides some utility methods to retrieve registry values as string, DWORD (int) or QWORD (long), as these are considered the most used types.

## Handles

Operations can be directly called on registry keys. However, for non-root keys this opens a connection to the Windows registry for every operation. If you need to perform several operations on a single registry key, you should consider calling one of its `handle` methods. The returned handle allows you to perform the same operations with the same method signatures using a single connection to the Windows registry.

## Transactions

By default, any interaction with the Windows registry does not use transactions. Using class [TransactionalState](https://robtimus.github.io/windows-registry/apidocs/com.github.robtimus.os.windows.registry/com/github/robtimus/os/windows/registry/TransactionalState.html) it's possible to run code within one of the following transactional states, based on Jakarta EE and Spring transactional states:

* `TransactionalState mandatory` will reuse an existing transaction if present, or throw an exception otherwise.
* `TransactionalState.required` will reuse an existing transaction if present, otherwise create a new one.
* `TransactionalState.requiresNew` will create a new transaction.
* `TransactionalState.supports` will use an existing transaction if present, otherwise work without a transaction.
* `TransactionalState.notSupported` will ignore any existing transaction.
* `TransactionalState.never` will throw an exception if an existing transaction is present.

While nested transactions are not supported by Windows, nesting `TransactionalState` calls is supported. The innermost call will be leading for any interaction with the Windows registry. However, mixing different transactional states for the same registry keys may cause Windows to automatically rollback one or more transactions.

### Committing and rolling back

By default, a transaction will be committed automatically when the `call` or `run` method that created it ends. By calling [Transaction.current()](https://robtimus.github.io/windows-registry/apidocs/com.github.robtimus.os.windows.registry/com/github/robtimus/os/windows/registry/Transaction.html#current\(\)) you can get access to the current transaction (if any). This allows you to query the transaction's status, turn auto-commit on or off, and explicitly commit or rollback the transaction.

## Remote registries

Using method [Registry.at](https://robtimus.github.io/windows-registry/apidocs/com.github.robtimus.os.windows.registry/com/github/robtimus/os/windows/registry/Registry.html#at\(java.lang.String\)) you can connect to the Windows registry on a remote machine, provided the user the JVM is running as has the rights to do so. Apart from the entry point, remote registry keys work exactly like regular registry keys. This includes using the `resolve` method.

### Using transactions with remote registries

Remote registries will make use of the current transaction just like local registries, as long as Windows allows it. If needed, use `TransactionalState.notSupported` to ignore any existing transaction.

## Implementation details

Interaction with the Windows registry is done through the [Foreign Function and Memory (FFM) API](https://docs.oracle.com/en/java/javase/25/core/foreign-function-and-memory-api.html). This has some benefits:

* Native interaction with the Windows registry instead of using commands like `REG QUERY` provides better performance.
* Native interaction with the Windows registry instead of piggybacking on Java internal classes like `java.util.Preferences` provides greater compatibility between Java versions.
* FFM is part of the Java core libraries. There is no need to install any libraries manually. There is not even need to install any runtime dependencies.

### Enabling native access

Applications will need to add JVM flag `--enable-native-access=com.github.robtimus.os.windows.registry`, or add `com.github.robtimus.os.windows.registry` to any already present `--enable-native-access` module list. If this flag is not present the current JVM versions will display a warning. That may change into a runtime error in later JVMs.
