package sh.ondr.mcp4k.runtime.integration

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import sh.ondr.mcp4k.assertLinesMatch
import sh.ondr.mcp4k.client
import sh.ondr.mcp4k.logLines
import sh.ondr.mcp4k.runtime.Client
import sh.ondr.mcp4k.runtime.Server
import sh.ondr.mcp4k.runtime.annotation.McpPrompt
import sh.ondr.mcp4k.runtime.prompts.buildPrompt
import sh.ondr.mcp4k.runtime.serialization.deserializeResult
import sh.ondr.mcp4k.runtime.transport.ChannelTransport
import sh.ondr.mcp4k.schema.content.TextContent
import sh.ondr.mcp4k.schema.core.Role
import sh.ondr.mcp4k.schema.prompts.GetPromptRequest
import sh.ondr.mcp4k.schema.prompts.GetPromptRequest.GetPromptParams
import sh.ondr.mcp4k.schema.prompts.GetPromptResult
import sh.ondr.mcp4k.schema.prompts.ListPromptsRequest
import sh.ondr.mcp4k.schema.prompts.Prompt
import sh.ondr.mcp4k.server
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * This function prompts the user to review some code
 * @param code The code to review
 */
@McpPrompt
fun Server.codeReviewPrompt(code: String) =
	buildPrompt("The code review prompt") {
		user { "Please review the code: $code" }
	}

@McpPrompt
fun secondPrompt(code: String) =
	buildPrompt("The code review prompt") {
		user { "Please review the code: $code" }
	}

@McpPrompt
fun thirdPrompt(code: String) =
	buildPrompt("The code review prompt") {
		user { "Please review the code: $code" }
	}

@McpPrompt
fun fourthPrompt(code: String) =
	buildPrompt("The code review prompt") {
		user { "Please review the code: $code" }
	}

@McpPrompt
fun fifthPrompt(code: String) =
	buildPrompt("The code review prompt") {
		user { "Please review the code: $code" }
	}

class PromptsTest {
	@OptIn(ExperimentalCoroutinesApi::class)
	@Test
	fun testPromptsList() =
		runTest {
			val testDispatcher = StandardTestDispatcher(testScheduler)
			val log = mutableListOf<String>()

			val clientTransport = ChannelTransport()
			val serverTransport = clientTransport.flip()
			val server = Server.Builder()
				.withDispatcher(testDispatcher)
				.withPageSize(2)
				.withPrompts(
					Server::codeReviewPrompt,
					::secondPrompt,
					::thirdPrompt,
					::fourthPrompt,
					::fifthPrompt,
				)
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

			// Send the prompts/list request
			var cursor: String? = null
			val allPrompts = mutableListOf<Prompt>()
			var pageCount = 0

			client.fetchPagesAsFlow(ListPromptsRequest)
				.collect { prompts ->
					pageCount++
					allPrompts += prompts
				}

			advanceUntilIdle()

			// 6) We have 5 total prompts and a page size of 2 => 3 pages (2 + 2 + 1).
			assertEquals(3, pageCount, "Expected exactly 3 pages for 5 prompts with pageSize=2")
			assertEquals(5, allPrompts.size, "Should have exactly 5 prompts in total")

			val expected = logLines {
				// 1st page
				clientOutgoing("""{"method":"prompts/list","jsonrpc":"2.0","id":"2"}""")
				serverIncoming("""{"method":"prompts/list","jsonrpc":"2.0","id":"2"}""")
				serverOutgoing(
					"""{"jsonrpc":"2.0","id":"2","result":{"prompts":[{"name":"codeReviewPrompt","description":"This function prompts the user to review some code","arguments":[{"name":"code","description":"The code to review","required":true}]},{"name":"secondPrompt","arguments":[{"name":"code","required":true}]}],"nextCursor":"eyJwYWdlIjoxLCJwYWdlU2l6ZSI6Mn0="}}""",
				)
				clientIncoming(
					"""{"jsonrpc":"2.0","id":"2","result":{"prompts":[{"name":"codeReviewPrompt","description":"This function prompts the user to review some code","arguments":[{"name":"code","description":"The code to review","required":true}]},{"name":"secondPrompt","arguments":[{"name":"code","required":true}]}],"nextCursor":"eyJwYWdlIjoxLCJwYWdlU2l6ZSI6Mn0="}}""",
				)

				// 2nd page
				clientOutgoing("""{"method":"prompts/list","jsonrpc":"2.0","id":"3","params":{"cursor":"eyJwYWdlIjoxLCJwYWdlU2l6ZSI6Mn0="}}""")
				serverIncoming("""{"method":"prompts/list","jsonrpc":"2.0","id":"3","params":{"cursor":"eyJwYWdlIjoxLCJwYWdlU2l6ZSI6Mn0="}}""")
				serverOutgoing(
					"""{"jsonrpc":"2.0","id":"3","result":{"prompts":[{"name":"thirdPrompt","arguments":[{"name":"code","required":true}]},{"name":"fourthPrompt","arguments":[{"name":"code","required":true}]}],"nextCursor":"eyJwYWdlIjoyLCJwYWdlU2l6ZSI6Mn0="}}""",
				)
				clientIncoming(
					"""{"jsonrpc":"2.0","id":"3","result":{"prompts":[{"name":"thirdPrompt","arguments":[{"name":"code","required":true}]},{"name":"fourthPrompt","arguments":[{"name":"code","required":true}]}],"nextCursor":"eyJwYWdlIjoyLCJwYWdlU2l6ZSI6Mn0="}}""",
				)

				// 3rd page
				clientOutgoing("""{"method":"prompts/list","jsonrpc":"2.0","id":"4","params":{"cursor":"eyJwYWdlIjoyLCJwYWdlU2l6ZSI6Mn0="}}""")
				serverIncoming("""{"method":"prompts/list","jsonrpc":"2.0","id":"4","params":{"cursor":"eyJwYWdlIjoyLCJwYWdlU2l6ZSI6Mn0="}}""")
				serverOutgoing(
					"""{"jsonrpc":"2.0","id":"4","result":{"prompts":[{"name":"fifthPrompt","arguments":[{"name":"code","required":true}]}]}}""",
				)
				clientIncoming(
					"""{"jsonrpc":"2.0","id":"4","result":{"prompts":[{"name":"fifthPrompt","arguments":[{"name":"code","required":true}]}]}}""",
				)
			}

			assertLinesMatch(expected, log, "prompts list test")
		}

	@OptIn(ExperimentalCoroutinesApi::class)
	@Test
	fun testGetPrompt() =
		runTest {
			val testDispatcher = StandardTestDispatcher(testScheduler)
			val log = mutableListOf<String>()

			val clientTransport = ChannelTransport()
			val serverTransport = clientTransport.flip()
			val server = Server.Builder()
				.withDispatcher(testDispatcher)
				.withPrompt(Server::codeReviewPrompt)
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

			// Now send the prompts/get request
			val response = client.sendRequest { id ->
				// This matches the MCP spec for prompts/get
				GetPromptRequest(
					id = id,
					params = GetPromptParams(
						name = "codeReviewPrompt",
						arguments = mapOf("code" to "some code here"),
					),
				)
			}
			advanceUntilIdle()

			val expected = logLines {
				clientOutgoing(
					"""{"method":"prompts/get","jsonrpc":"2.0","id":"2","params":{"name":"codeReviewPrompt","arguments":{"code":"some code here"}}}""",
				)
				serverIncoming(
					"""{"method":"prompts/get","jsonrpc":"2.0","id":"2","params":{"name":"codeReviewPrompt","arguments":{"code":"some code here"}}}""",
				)
				serverOutgoing(
					"""{"jsonrpc":"2.0","id":"2","result":{"description":"The code review prompt","messages":[{"role":"user","content":{"type":"text","text":"Please review the code: some code here"}}]}}""",
				)
				clientIncoming(
					"""{"jsonrpc":"2.0","id":"2","result":{"description":"The code review prompt","messages":[{"role":"user","content":{"type":"text","text":"Please review the code: some code here"}}]}}""",
				)
			}

			assertLinesMatch(expected, log, "prompts get test")

			// Optionally, verify the actual response object
			val getPromptResult = response.result?.deserializeResult<GetPromptResult>()
			assertNotNull(getPromptResult)
			assertEquals("The code review prompt", getPromptResult.description)
			assertEquals(1, getPromptResult.messages.size)
			val msg = getPromptResult.messages.first()
			assertEquals(Role.USER, msg.role)
			val text = (msg.content as? TextContent)?.text
			assertEquals("Please review the code: some code here", text)
		}
}
