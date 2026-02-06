# mcp4k-file-provider

File-based resource provider implementations for mcp4k. This module provides ready-to-use implementations of the `ResourceProvider` interface for exposing files through the Model Context Protocol.

## Installation

First, make sure you have the main mcp4k plugin applied:

```kotlin
plugins {
  kotlin("multiplatform") version "2.3.10" // or kotlin("jvm")
  kotlin("plugin.serialization") version "2.3.10"
  
  id("sh.ondr.mcp4k") version "0.4.8" // <-- Required
}
```

Then add the file-provider dependency:

```kotlin
dependencies {
  implementation("sh.ondr.mcp4k:mcp4k-file-provider:0.4.8")
}
```

**Note**: The mcp4k plugin automatically includes `mcp4k-runtime`, so you don't need to add it explicitly.

## Overview

This module provides two file-based implementations of the `ResourceProvider` interface:

- **`DiscreteFileProvider`** - Exposes a specific set of files with discrete URIs
- **`TemplateFileProvider`** - Exposes an entire directory using URI templates

**⚠️ WARNING**: These implementations are experimental and NOT production-ready. Use only in sandboxed or trusted environments.

## Usage

### DiscreteFileProvider

Exposes a specific set of files with discrete URIs. Use this when you want to expose only certain files from a directory.

```kotlin
val fileProvider = DiscreteFileProvider(
  fileSystem = FileSystem.SYSTEM,
  rootDir = "/app/resources".toPath(),
  initialFiles = listOf(
    File(
      relativePath = "config/app.yaml",
      mimeType = "application/yaml",
    ),
    File(
      relativePath = "data/users.json",
      mimeType = "application/json",
    ),
  )
)

val server = Server.Builder()
  .withResourceProvider(fileProvider)
  .withTransport(StdioTransport())
  .build()
```

#### Dynamic File Management

You can add or remove files at runtime:

```kotlin
// Add a new file
fileProvider.addFile(
  File(
    relativePath = "logs/app.log",
    mimeType = "text/plain",
  )
)

// Remove a file
fileProvider.removeFile("config/app.yaml")
```

Both operations automatically send `notifications/resources/list_changed` to connected clients.

#### File Change Notifications

When a file's contents change, notify subscribed clients:

```kotlin
fileProvider.onResourceChange("data/users.json")
```

This sends `notifications/resources/updated` to clients that have subscribed to the resource.

### TemplateFileProvider

Exposes an entire directory using URI templates. Use this when you want to provide access to all files in a directory structure.

```kotlin
val templateProvider = TemplateFileProvider(
  fileSystem = FileSystem.SYSTEM,
  rootDir = "/app/documents".toPath(),
)

val server = Server.Builder()
  .withResourceProvider(templateProvider)
  .withTransport(StdioTransport())
  .build()
```

Clients can read any file within the root directory by providing the relative path:

```json
{
  "method": "resources/read",
  "params": {
    "uri": "file:///reports/2024/summary.pdf"
  }
}
```

## Client Usage

From the client side, resources work the same regardless of the provider:

```kotlin
// List available resources
val resources = client.listResources()
resources.forEach { resource ->
  println("${resource.name}: ${resource.uri}")
}

// Read a resource
val contents = client.readResource("file://config/app.yaml")
println(contents.text)

// Subscribe to changes
client.subscribeResource("file://data/users.json")
// Handle notifications via onResourceUpdated callback
```

## Security Considerations

These file providers have several security limitations:
- No access control or authentication
- Basic path traversal protection only
- No file size limits
- No rate limiting
- No sandboxing of file access

For production use, implement your own `ResourceProvider` with appropriate security measures.

## Custom File Systems

Both providers support Okio's `FileSystem` abstraction, allowing you to use:
- `FileSystem.SYSTEM` - Real file system (only available in platform sets, not in `commonMain`)
- `FakeFileSystem` - In-memory file system for testing
- Custom implementations - Cloud storage, encrypted files, etc.

To use FakeFileSystem for testing, add the following dependency to your test set:

```kts
dependencies {
    implementation("com.squareup.okio:okio-fakefilesystem:3.16.0")
}
```

Here is some example code:

```kotlin
val fakeFs = FakeFileSystem()
fakeFs.createDirectories("/test/data".toPath())
fakeFs.write("/test/data/sample.txt".toPath()) {
  writeUtf8("Hello, MCP!")
}

val provider = DiscreteFileProvider(
  fileSystem = fakeFs,
  rootDir = "/test".toPath(),
  initialFiles = listOf(
    File(relativePath = "data/sample.txt", mimeType = "text/plain")
  )
)
```

## MIME Type Detection

Currently, MIME types must be specified manually when creating files. Automatic MIME type detection is planned for future releases.

## License

Apache License 2.0
