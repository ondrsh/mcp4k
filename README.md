
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


mcp4k is a compiler-driven framework that handles all the [Model Context Protocol](https://modelcontextprotocol.io) details behind the scenes. 

You just annotate your functions and mcp4k takes care of JSON-RPC messages, schema generation and protocol lifecycle:

```kotlin
/**
 * Reverses an input string
 *
 * @param input The string to be reversed
 */
@McpTool
fun reverseString(input: String) =
  "Reversed: ${input.reversed()}".toTextContent()

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

That’s it! To use the above tool in Claude Desktop (or any other MCP-compatible client), add the compiled binary to your configuration file.

mcp4k will do the following for you:
- Generate the required JSON schemas (including tool and parameter descriptions)
- Handle incoming tool requests
- Convert thrown Kotlin exceptions to MCP error codes 

For instance, if this tool received an invalid parameter (like passing a number when a string is expected), it would respond with a `-32602 (INVALID_PARAMS)` code. The same is true in case an `IllegalArgumentException` (or any other exception) is thrown from inside a tool function — mcp4k will catch it and map exceptions according to the protocol.
<br>



# Installation

Add the mcp4k plugin to your multiplatform (or jvm) project:

```kotlin
plugins {
  kotlin("multiplatform") version "2.1.0" // or kotlin("jvm")
  kotlin("plugin.serialization") version "2.1.0"
  
  id("sh.ondr.mcp4k") version "0.3.5" // <-- Add this
}
```


# Examples

## Tools

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
KDoc parameter descriptions are type-safe and will throw a compile-time error if you specify a non-existing property.

Clients can now send a `tools/call` request with a JSON object describing the above schema. Invocation and type-safe deserialization will be handled by mcp4k.

<br>

## Prompts

Annotate functions with `@McpPrompt` and return a `GetPromptResult`. Arguments must be Strings:

```kotlin
@McpPrompt
fun codeReviewPrompt(code: String) = buildPrompt {
  user("Please review the following code:")
  user("'''\n$code\n'''")
}
```

<br>

## Server Context

In some cases, you want multiple tools or prompts to share state.

mcp4k allows you to attach a custom **context object** that tools and prompts can reference. For example:

```kotlin
// 1) Implement your custom context
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

<br>

## Resources

Resources are provided by a `ResourceProvider`. You can either create your own `ResourceProvider` or use one of the 2 default implementations:
- `DiscreteFileProvider`
  - Let's you add/remove a discrete set of files that will be exposed to the client.
  - Handles `resources/list` requests.
  - Handles `resources/read` requests by reading contents from disk via `okio`.
  - Sends `notifications/resources/list_changed` when files are added or removed.
  - Supports subscriptions (but changes to files have to be marked manually for now).

- `TemplateFileProvider`
  - Exposes a given `rootDir` through a URI template.
  - Handles `resources/templates/list`.
  - Handles `resources/read` requests by reading contents from disk via `okio`.
  - Supports subscriptions (but changes to files have to be marked manually for now).

**Use those providers only in a sand-boxed environment. They are NOT production-ready.**

### DiscreteFileProvider

Let's say you want to expose 2 files:
- /app/resources/cpp/my_program.h
- /app/resources/cpp/my_program.cpp

You would first create the following provider:
```kotlin
val fileProvider = DiscreteFileProvider(
  fileSystem = FileSystem.SYSTEM,
  rootDir = "/app/resources".toPath(),
  initialFiles = listOf(
    File(
      relativePath = "cpp/my_program.h",
      mimeType = "text/x-c++",
    ),
    File(
      relativePath = "cpp/my_program.cpp",
      mimeType = "text/x-c++",
    ),
  )
)
```

And add it when building the server:
```kotlin
val server = Server.Builder()
  .withResourceProvider(fileProvider)
  .withTransport(StdioTransport())
  .build()
```

A client calling `resources/list` will then receive:
```json
{
  "resources": [
    {
      "uri": "file://cpp/my_program.h",
      "name": "my_program.h",
      "description": "File at cpp/my_program.h",
      "mimeType": "text/x-c++"
    },
    {
      "uri": "file://cpp/my_program.cpp",
      "name": "my_program.cpp",
      "description": "File at cpp/my_program.cpp",
      "mimeType": "text/x-c++"
    }
  ]
}
```

A client sending a `resources/read` request to fetch the contents of the source file would receive:
```json
{
  "contents": [
    {
      "uri": "file://cpp/my_program.cpp",
      "mimeType": "text/x-c++",
      "text": "int main(){}"
    }
  ]
}
```

You can also add or remove files at runtime via
```kotlin
fileProvider.addFile(
  File(
    relativePath = "cpp/README.txt",
    mimeType = "text/plain",
  )
)

fileProvider.removeFile("cpp/my_program.h")
```

Both `addFile` and `removeFile` will send a `notifications/resources/list_changed` notification.

<br>

When making changes to a file, always call
```kotlin
fileProvider.onResourceChange("cpp/my_program.h")
```

If (and only if) the client subscribed to this resource, this will send a `notifications/resources/updated` notification to the client.

<br>

### TemplateFileProvider

If you want to expose a whole directory, you can do:
```kotlin
val templateFileProvider = TemplateFileProvider(
  fileSystem = FileSystem.SYSTEM,
  rootDir = "/app/resources".toPath(),
)
```

A client calling `resources/templates/list` will receive:
```json
{
  "resourceTemplates": [
    {
      "uriTemplate": "file:///{path}",
      "name": "Arbitrary local file access",
      "description": "Allows reading any file by specifying {path}"
    }
  ]
}
```

The client can then issue a `resources/read` request by providing the `path`:
```json
{
  "method": "resources/read",
  "params": {
    "uri": "file:///cpp/my_program.cpp"
  }
}
```

This will read from `/app/resources/cpp/my_program.cpp` and return the result:
```json
{
  "contents": [
    {
      "uri": "file:///cpp/my_program.cpp",
      "mimeType": "text/plain",
      "text": "int main(){}"
    }
  ]
}
```

Note the incorrect `text/plain` here - proper MIME detection will be added at some point.

Similarly to `DiscreteFileProvider`, when modifying a resource, call
```kotlin
templateFileProvider.onResourceChange("cpp/my_program.h")
```
to trigger the notification in case a client is subscribed to this resource.

<br>

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

<br>

## Request Cancellations

mcp4k uses Kotlin coroutines for cooperative cancellation. For example, suppose you have a long-running tool operation on the server:

```kotlin
@McpTool
suspend fun slowToolOperation(iterations: Int = 10): ToolContent {
  for (i in 1..iterations) {
    delay(1000)
  }
  return "Operation completed successfully after $iterations iterations".toTextContent()
}
```

From the client side, after you invoked the tool, you can simply cancel the coroutine job:

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

// Let the server do partial work
delay(600)

// Now cancel
requestJob.cancel("User doesn't want to wait anymore")
```

Under the hood, mcp4k automatically sends a JSON-RPC notifications/cancelled message to the server, and your suspended tool operation will be aborted:
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

This gives you straightforward cancellations across the entire client-server flow.

## TODO
```
✅ Add resource capability
✅ Suspendable logger, @McpTool and @McpPrompt functions
✅ Request cancellations
✅ Pagination
✅ Sampling (client-side)
✅ Roots
⬜ Completions
⬜ Support logging levels
⬜ Proper version negotiation
⬜ Emit progress notifications from @McpTool functions
⬜ Proper MIME detection
⬜ Add FileWatcher to automate resources/updated nofications
⬜ HTTP-SSE transport
⬜ Add references, property descriptions and validation keywords to the JSON schemas
```

<br>

## How mcp4k Works

- Annotated `@McpTool` and `@McpPrompt` functions are processed at compile time.
- mcp4k generates schemas, handlers, and registrations automatically.
- Generated code is injected during the IR phase.
- If you mess something up, you (hopefully) get a compile-time error.


<br>

## Contributing
Issues and pull requests are welcome.

## License
Licensed under the [Apache License 2.0](./LICENSE).
