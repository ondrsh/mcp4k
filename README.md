<p align="center">
  <img src="./mcp4k.svg" alt="mcp4k banner">
</p>

<p align="center">
  <a href="https://central.sonatype.com/search?q=sh.ondr.mcp4k">
    <img src="https://img.shields.io/maven-central/v/sh.ondr.mcp4k/mcp4k-gradle.svg?label=Maven%20Central&color=blue" alt="Maven Central"/>
  </a>
  <a href="https://www.apache.org/licenses/LICENSE-2.0">
    <img src="https://img.shields.io/badge/License-Apache%202.0-blue.svg?color=blue" alt="License"/>
  </a>
</p>

<b>mcp4k</b> is a compiler-driven framework for building both <b>clients and servers</b> using the
<a href="https://modelcontextprotocol.io">Model Context Protocol</a> (MCP) in Kotlin.
It implements the vast majority of the MCP specification, including resources, prompts, tools, sampling, and more.

mcp4k automatically generates JSON-RPC handlers, schema metadata, and manages the complete lifecycle for you.

---

## Overview

- **Client**: Connects to any MCP server to request prompts, read resources, or invoke tools.
- **Server**: Exposes resources, prompts, and tools to MCP-compatible clients, handling standard JSON-RPC messages and protocol events.
- **Transports**: Supports `stdio`, with HTTP-Streaming and other transports on the roadmap.
- **Lifecycle**: Manages initialization, cancellation, sampling, progress tracking, and more.

mcp4k also enforces correct parameter typing at compile time.
If you describe a tool parameter incorrectly, you get a compile-time error instead of a runtime mismatch.

---

## Installation

Add mcp4k to your build:

```kotlin
plugins {
  kotlin("multiplatform") version "2.3.10" // or kotlin("jvm")
  kotlin("plugin.serialization") version "2.3.10"

  id("sh.ondr.mcp4k") version "0.4.8" // <-- Add this
}
```

### Version Compatibility

mcp4k includes a compiler plugin that requires exact Kotlin version matching. Each mcp4k version is hard-linked to a specific Kotlin version:

| mcp4k Version | Required Kotlin Version |
|---------------|-------------------------|
| 0.4.8         | 2.3.10                  |
| 0.4.7         | 2.3.0                   |
| 0.4.6         | 2.2.21                  |
| 0.4.5         | 2.2.20                  |
| 0.4.4         | 2.2.10                  |
| 0.4.2 - 0.4.3 | 2.2.0                   |

---

## Quick Start

### Create a Simple Server

```kotlin
/**
 * Reverses an input string
 *
 * @param input The string to be reversed
 */
@McpTool
fun reverseString(input: String): ToolContent {
  return "Reversed: ${input.reversed()}".toTextContent()
}

fun main() = runBlocking {
  val server = Server.Builder()
    .withTool(::reverseString)
    .withTransport(StdioTransport())
    .build()
    
  server.start()
  
  // Keep server running 
  while (true) { 
    delay(1000)
  }
}
```

In this example, your new ```@McpTool``` is exposed via JSON-RPC as ```reverseString```.
Clients can call it by sending ```tools/call``` messages.

---

### Create a Simple Client

```kotlin
fun main() = runBlocking {
  val client = Client.Builder()
    .withClientInfo("MyClient", "1.0.0")
    .withTransport(StdioTransport())
    .build()
  
  client.start()
  client.initialize()
}
```
All boilerplate (capability negotiation, JSON-RPC ID handling, etc.) is handled by mcp4k.

Once connected, the client can discover prompts/tools/resources and make calls according to the MCP spec:

```kotlin
val allTools = client.getAllTools()
println("Server tools = $allTools")

val response = client.callTool(
  name = "reverseString",
  arguments = buildMap {
    put("input", JsonPrimitive("Some string we want reversed"))
  },
)

val result = response.result?.deserializeResult<CallToolResult>()
println(result) // --> "desrever tnaw ew gnirts emoS"
```

If you want to get notified when the server changes its tools, you can provide a callback:
```kotlin
val client = Client.Builder()
  // ...
  .withOnToolsChanged { updatedTools: List<Tool> ->
    println("Updated tools: $updatedTools")
  }
  .build()
```

---

## Transport Logging

You can observe raw incoming/outgoing messages by providing ```withTransportLogger``` lambdas:

```kotlin
val server = Server.Builder()
  .withTransport(StdioTransport())
  .withTransportLogger(
    logIncoming = { msg -> println("SERVER INCOMING: $msg") },
    logOutgoing = { msg -> println("SERVER OUTGOING: $msg") },
  )
  .build()
```

Both ```Server``` and ```Client``` accept this configuration. Super useful for debugging and tests.

---

## Tools

Let's look at a more advanced tool example:

```kotlin
@JsonSchema @Serializable
enum class Priority {
  LOW, NORMAL, HIGH
}

/**
 * @property title The email's title
 * @property body The email's body
 * @property priority The email's priority
 */
@JsonSchema @Serializable
data class Email(
  val title: String,
  val body: String?,
  val priority: Priority = Priority.NORMAL,
)

/**
 * Sends an email
 * @param recipients The email addresses of the recipients
 * @param email The email to send
 */
@McpTool
fun sendEmail(
  recipients: List<String>,
  email: Email,
) = buildString {
  append("Email sent to ${recipients.joinToString()} with ")
  append("title '${email.title}' and ")
  append("body '${email.body}' and ")
  append("priority ${email.priority}")
}.toTextContent()
```

When clients call `tools/list`, they see a JSON schema describing the tool's input:

```json
{
  "type": "object",
  "description": "Sends an email",
  "properties": {
    "recipients": {
      "type": "array",
      "description": "The email addresses of the recipients",
      "items": {
        "type": "string"
      }
    },
    "email": {
      "type": "object",
      "description": "The email to send",
      "properties": {
        "title": {
          "type": "string",
          "description": "The email's title"
        },
        "body": {
          "type": "string",
          "description": "The email's body"
        },
        "priority": {
          "type": "string",
          "description": "The email's priority",
          "enum": [
            "LOW",
            "NORMAL",
            "HIGH"
          ]
        }
      },
      "required": [
        "title"
      ]
    }
  },
  "required": [
    "recipients",
    "email"
  ]
}
```
KDoc parameter descriptions are type-safe and will throw a compile-time error if you specify a non-existing property. Tool call invocation and type-safe deserialization will be handled by mcp4k.

Server can also add or remove tools at runtime:
```kotlin
server.addTool(::sendEmail)
// ...
server.removeTool(::sendEmail)
```

Both calls will automatically send `ToolListChanged` notifications to the client.

Tools can also be added or removed from inside tool functions if they are implemented as `Server` extension functions:
```kotlin
@McpTool
fun Server.toolThatAddsSecondTool(): ToolContent {
  addTool(::secondTool)
  return "Second tool added!".toTextContent()
}
```

---

## Prompts

Annotate functions with ```@McpPrompt``` to define parameterized conversation templates:

```kotlin
@McpPrompt
fun codeReviewPrompt(code: String) = buildPrompt {
  user("Please review the following code:")
  user("'''\n$code\n'''")
}
```

Clients can call ```prompts/get``` to retrieve the underlying messages.

---

## Server Context

In some cases, you want multiple tools or prompts to share state.  mcp4k allows you to attach a custom **context object** that tools and prompts can reference.

1) Create a `ServerContext` object
2) Pass it in with ```.withContext(...)```
3) Each tool or prompt can access it by calling ```getContextAs()```

For example:

```kotlin
// 1) Create your context
class MyServerContext : ServerContext {
  var userName: String = ""
}

// 2) A tool function that writes into the context
@McpTool
fun Server.setUserName(name: String): ToolContent {
  getContextAs<MyServerContext>().userName = name
  return "Username set to: $name".toTextContent()
}

// 3) Another tool that reads from the context
@McpTool
fun Server.greetUser(): ToolContent {
  val name = getContextAs<MyServerContext>().userName
  if (name.isEmpty()) return "No user set yet!".toTextContent()
  return "Hello, $name!".toTextContent()
}

fun main() = runBlocking {
  val context = MyServerContext()
  val server = Server.Builder()
    .withContext(context) // <-- Provide the context
    .withTool(Server::setUserName)
    .withTool(Server::greetUser)
    .withTransport(StdioTransport())
    .build()
  
  server.start()
  while(true) {
    delay(1000)
  }
}
```

But looking at the above code, it doesn't make sense that the `greetUser` function is callable before `setUserName` has been called.

Thus, we can improve the code by doing:
```kotlin
fun main() {
  val server = Server.Builder()
  // ...
  .withTool(Server::setUserName) // only add this
  // ...
}

@McpTool
fun Server.setUserName(name: String): ToolContent {
  getContextAs<MyServerContext>().userName = name
  addTool(Server::greetUser) // Now, add greetUser
  return "Username set to: $name".toTextContent()
}
```

---

## Resources

Resources in MCP allow servers to expose data that clients can read. The `ResourceProvider` interface is the core abstraction for implementing resource support:

```kotlin
interface ResourceProvider {
  suspend fun listResources(): List<Resource>
  suspend fun readResource(uri: String): ResourceContents
  suspend fun listResourceTemplates(): List<ResourceTemplate>
  suspend fun subscribe(uri: String)
  suspend fun unsubscribe(uri: String)
  fun onResourceListChanged(callback: () -> Unit)
  fun onResourceUpdated(callback: (uri: String) -> Unit)
}
```

You can implement this interface to expose any type of data as resources - databases, APIs, files, or any other data source.

### File-Based Resource Providers

For common file-based use cases, mcp4k provides ready-to-use implementations in the optional `mcp4k-file-provider` module. See the [file provider documentation](mcp4k-file-provider/README.md) for details on:
- `DiscreteFileProvider` - Expose specific files with discrete URIs
- `TemplateFileProvider` - Expose entire directories with URI templates

### Creating Custom Resource Providers

Here's a simple example of a custom resource provider:

```kotlin
class DatabaseResourceProvider : ResourceProvider {
  override suspend fun listResources(): List<Resource> {
    return listOf(
      Resource(
        uri = "db://users",
        name = "Users Table",
        description = "Access to user data",
        mimeType = "application/json"
      )
    )
  }
  
  override suspend fun readResource(uri: String): ResourceContents {
    return when (uri) {
      "db://users" -> ResourceContents(
        uri = uri,
        mimeType = "application/json",
        text = fetchUsersAsJson()
      )
      else -> throw ResourceNotFoundException(uri)
    }
  }
  
  // Implement other methods as needed...
}
```

Then add it to your server:
```kotlin
val server = Server.Builder()
  .withResourceProvider(DatabaseResourceProvider())
  .withTransport(StdioTransport())
  .build()
```

---

## Sampling

Clients can fulfill server-initiated LLM requests by providing a `SamplingProvider`.

In a real application, you would call your favorite LLM API (e.g., OpenAI, Anthropic) inside the provider. Here’s a simplified example that always returns a dummy completion:

```kotlin
// 1) Define a sampling provider
val samplingProvider = SamplingProvider { params: CreateMessageParams ->
  CreateMessageResult(
    model = "dummy-model",
    role = Role.ASSISTANT,
    content = TextContent("Dummy completion result"),
    stopReason = "endTurn",
  )
}

// 2) Build the client with sampling support
val client = Client.Builder()
  .withTransport(StdioTransport())
  .withPermissionCallback { userApprovable -> 
    // Prompt the user for confirmation here
    true 
  }
  .withSamplingProvider(samplingProvider) // Register the provider
  .build()

runBlocking {
  client.start()
  client.initialize()

  // Now, if a server sends a "sampling/createMessage" request, 
  // the samplingProvider will be invoked to generate a response.
}
```

---

## Request Cancellations

mcp4k uses Kotlin coroutines for cooperative cancellation. For example, a long-running server tool:

```kotlin
@McpTool
suspend fun slowToolOperation(iterations: Int = 10): ToolContent {
  for (i in 1..iterations) {
    delay(1000)
  }
  return "Operation completed after $iterations".toTextContent()
}
```

The client can cancel mid-operation:

```kotlin
val requestJob = launch {
  client.sendRequest { id ->
    CallToolRequest(
      id = id,
      params = CallToolRequest.CallToolParams(
        name = "slowToolOperation",
        arguments = mapOf("iterations" to 20),
      ),
    )
  }
}
delay(600)
requestJob.cancel("User doesn't want to wait anymore")
```

Under the hood, mcp4k sends a notification to the server:
```json
{
  "method": "notifications/cancelled",
  "jsonrpc": "2.0",
  "params": {
    "requestId": "2",
    "reason": "Client doesn't want to wait anymore"
  }
}
```
and the server will abort the suspended tool operation.

---

## Roadmap

```
✅ Add resource capability
✅ @McpTool and @McpPrompt functions
✅ Request cancellations
✅ Pagination
✅ Sampling (client-side)
✅ Roots
✅ Transport logging
✅ onToolsChanged callback in Client
⬜ Support other Kotlin versions
⬜ Completions
⬜ Support logging levels
⬜ Proper version negotiation
⬜ Emit progress notifications from @McpTool functions
⬜ Proper MIME detection
⬜ Add FileWatcher to automate resources/updated notifications
⬜ HTTP-Streaming transport
⬜ Add references, property descriptions and validation keywords to the JSON schemas
```

---

## How mcp4k Works

- Annotated ```@McpTool``` and ```@McpPrompt``` functions are processed at compile time.
- mcp4k generates JSON schemas, request handlers, and registration code automatically.
- Generated code is injected during Kotlin's IR compilation phase, guaranteeing type-safe usage.
- If your KDoc references unknown parameters, the build fails, forcing you to keep docs in sync with code.

---

## Contributing

Issues and pull requests are welcome!
Feel free to open a discussion or contribute improvements.

**License**: mcp4k is available under the [Apache License 2.0](./LICENSE).
