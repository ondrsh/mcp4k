package sh.ondr.kmcp.runtime.integration

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import sh.ondr.kmcp.assertLinesMatch
import sh.ondr.kmcp.client
import sh.ondr.kmcp.logLines
import sh.ondr.kmcp.runtime.Client
import sh.ondr.kmcp.runtime.Server
import sh.ondr.kmcp.runtime.annotation.McpTool
import sh.ondr.kmcp.runtime.core.toTextContent
import sh.ondr.kmcp.runtime.serialization.deserializeResult
import sh.ondr.kmcp.runtime.transport.TestTransport
import sh.ondr.kmcp.schema.content.TextContent
import sh.ondr.kmcp.schema.content.ToolContent
import sh.ondr.kmcp.schema.tools.CallToolRequest
import sh.ondr.kmcp.schema.tools.CallToolResult
import sh.ondr.kmcp.schema.tools.ListToolsRequest
import sh.ondr.kmcp.schema.tools.Tool
import sh.ondr.kmcp.server
import sh.ondr.koja.JsonSchema
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * @param title The email's title
 * @param body The email's body
 */
@Serializable @JsonSchema
data class Email(
	val title: String,
	val body: String?,
)

/**
 * Sends an email to [recipients] with the given [email]
 */
@McpTool
fun sendEmail(
	recipients: List<String>,
	email: Email,
) = "Sending email to $recipients with title '${email.title}' and body '${email.body}'".toTextContent()

/**
 * Greets a user by [name], optionally specifying an [age].
 */
@McpTool
suspend fun greet(
	name: String,
	age: Int = 25,
): ToolContent {
	return TextContent("Hello, $name, you are $age years old!")
}

/**
 * Reverses a given string [s].
 */
@McpTool
fun reverseString(s: String) = "Reversed: ${s.reversed()}".toTextContent()

class ToolsTest {
	@OptIn(ExperimentalCoroutinesApi::class)
	@Test
	fun testToolsList() =
		runTest {
			val testDispatcher = StandardTestDispatcher(testScheduler)
			val log = mutableListOf<String>()

			val (clientTransport, serverTransport) = TestTransport.createClientAndServerTransport()

			val server = Server.Builder()
				.withDispatcher(testDispatcher)
				.withPageSize(2)
				.withTool(::greet)
				.withTool(::sendEmail)
				.withTool(::reverseString)
				.withTransport(serverTransport)
				.withLogger { line -> log.server(line) }
				.build()
			server.start()

			val client = Client.Builder()
				.withTransport(clientTransport)
				.withDispatcher(testDispatcher)
				.withLogger { line -> log.client(line) }
				.withClientInfo("TestClient", "1.0.0")
				.build()
			client.start()

			// Perform initialization
			client.initialize()
			advanceUntilIdle()
			log.clear()

			// Use fetchPagesAsFlow to retrieve tools across multiple pages
			val allTools = mutableListOf<Tool>()
			var pageCount = 0

			client.fetchPagesAsFlow(ListToolsRequest).collect { pageOfTools ->
				pageCount++
				allTools += pageOfTools
			}
			advanceUntilIdle()

			// With 3 total tools and pageSize=2 => 2 pages
			assertEquals(2, pageCount, "Expected 2 pages of tools")
			assertEquals(3, allTools.size, "Expected a total of 3 tools")

			val expected = logLines {
				// 1st page
				clientOutgoing("""{"method":"tools/list","jsonrpc":"2.0","id":"2"}""")
				serverIncoming("""{"method":"tools/list","jsonrpc":"2.0","id":"2"}""")
				serverOutgoing(
					"""{"jsonrpc":"2.0","id":"2","result":{"tools":[{"name":"greet","description":"Greets a user by [name], optionally specifying an [age].","inputSchema":{"type":"object","properties":{"name":{"type":"string"},"age":{"type":"number"}},"required":["name"]}},{"name":"sendEmail","description":"Sends an email to [recipients] with the given [email]","inputSchema":{"type":"object","properties":{"recipients":{"type":"array","items":{"type":"string"}},"email":{"type":"object","description":"null","properties":{"title":{"type":"string","description":"The email's title"},"body":{"type":"string","description":"The email's body"}},"required":["title"]}},"required":["recipients","email"]}}],"nextCursor":"eyJwYWdlIjoxLCJwYWdlU2l6ZSI6Mn0="}}""",
				)
				clientIncoming(
					"""{"jsonrpc":"2.0","id":"2","result":{"tools":[{"name":"greet","description":"Greets a user by [name], optionally specifying an [age].","inputSchema":{"type":"object","properties":{"name":{"type":"string"},"age":{"type":"number"}},"required":["name"]}},{"name":"sendEmail","description":"Sends an email to [recipients] with the given [email]","inputSchema":{"type":"object","properties":{"recipients":{"type":"array","items":{"type":"string"}},"email":{"type":"object","description":"null","properties":{"title":{"type":"string","description":"The email's title"},"body":{"type":"string","description":"The email's body"}},"required":["title"]}},"required":["recipients","email"]}}],"nextCursor":"eyJwYWdlIjoxLCJwYWdlU2l6ZSI6Mn0="}}""",
				)

				// 2nd page
				clientOutgoing("""{"method":"tools/list","jsonrpc":"2.0","id":"3","params":{"cursor":"eyJwYWdlIjoxLCJwYWdlU2l6ZSI6Mn0="}}""")
				serverIncoming("""{"method":"tools/list","jsonrpc":"2.0","id":"3","params":{"cursor":"eyJwYWdlIjoxLCJwYWdlU2l6ZSI6Mn0="}}""")
				serverOutgoing(
					"""{"jsonrpc":"2.0","id":"3","result":{"tools":[{"name":"reverseString","description":"Reverses a given string [s].","inputSchema":{"type":"object","properties":{"s":{"type":"string"}},"required":["s"]}}]}}""",
				)
				clientIncoming(
					"""{"jsonrpc":"2.0","id":"3","result":{"tools":[{"name":"reverseString","description":"Reverses a given string [s].","inputSchema":{"type":"object","properties":{"s":{"type":"string"}},"required":["s"]}}]}}""",
				)
			}
			assertLinesMatch(expected, log, "tools list paginated test")
		}

	@OptIn(ExperimentalCoroutinesApi::class)
	@Test
	fun testCallToolGreet() =
		runTest {
			val testDispatcher = StandardTestDispatcher(testScheduler)
			val log = mutableListOf<String>()

			val (clientTransport, serverTransport) = TestTransport.createClientAndServerTransport()
			val server = Server.Builder()
				.withDispatcher(testDispatcher)
				.withTool(::greet)
				.withTool(::sendEmail)
				.withTransport(serverTransport)
				.withLogger { line -> log.server(line) }
				.build()
			server.start()

			val client = Client.Builder()
				.withTransport(clientTransport)
				.withDispatcher(testDispatcher)
				.withLogger { line -> log.client(line) }
				.withClientInfo("TestClient", "1.0.0")
				.build()
			client.start()

			// Perform initialization
			client.initialize()
			advanceUntilIdle()
			log.clear()

			// Call the 'greet' tool
			val response = client.sendRequest { id ->
				CallToolRequest(
					id = id,
					params = CallToolRequest.CallToolParams(
						name = "greet",
						arguments = mapOf("name" to JsonPrimitive("Alice")),
					),
				)
			}
			advanceUntilIdle()

			val expected = logLines {
				clientOutgoing("""{"method":"tools/call","jsonrpc":"2.0","id":"2","params":{"name":"greet","arguments":{"name":"Alice"}}}""")
				serverIncoming("""{"method":"tools/call","jsonrpc":"2.0","id":"2","params":{"name":"greet","arguments":{"name":"Alice"}}}""")
				serverOutgoing(
					"""{"jsonrpc":"2.0","id":"2","result":{"content":[{"type":"text","text":"Hello, Alice, you are 25 years old!"}]}}""",
				)
				clientIncoming(
					"""{"jsonrpc":"2.0","id":"2","result":{"content":[{"type":"text","text":"Hello, Alice, you are 25 years old!"}]}}""",
				)
			}
			assertLinesMatch(expected, log, "tools call greet test")

			// Optionally, verify the actual response
			val callToolResult = response.result?.deserializeResult<CallToolResult>()
			assertNotNull(callToolResult)
			assertEquals(1, callToolResult.content.size)
			val text = (callToolResult.content.first() as? TextContent)?.text
			assertEquals("Hello, Alice, you are 25 years old!", text)
		}
}
