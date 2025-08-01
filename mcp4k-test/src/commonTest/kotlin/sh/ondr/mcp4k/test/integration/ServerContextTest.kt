package sh.ondr.mcp4k.test.integration

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import sh.ondr.mcp4k.runtime.Client
import sh.ondr.mcp4k.runtime.Server
import sh.ondr.mcp4k.runtime.serialization.deserializeResult
import sh.ondr.mcp4k.runtime.transport.ChannelTransport
import sh.ondr.mcp4k.schema.content.TextContent
import sh.ondr.mcp4k.schema.core.JsonRpcResponse
import sh.ondr.mcp4k.schema.tools.CallToolRequest
import sh.ondr.mcp4k.schema.tools.CallToolRequest.CallToolParams
import sh.ondr.mcp4k.schema.tools.CallToolResult
import sh.ondr.mcp4k.test.assertLinesMatch
import sh.ondr.mcp4k.test.buildLog
import sh.ondr.mcp4k.test.clientIncoming
import sh.ondr.mcp4k.test.clientOutgoing
import sh.ondr.mcp4k.test.serverIncoming
import sh.ondr.mcp4k.test.serverOutgoing
import sh.ondr.mcp4k.test.tools.RemoteServiceImpl
import sh.ondr.mcp4k.test.tools.storeValueInContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ServerContextTest {
	@OptIn(ExperimentalCoroutinesApi::class)
	@Test
	fun testContextToolFunction() =
		runTest {
			val testDispatcher = StandardTestDispatcher(testScheduler)
			val log = mutableListOf<String>()

			// 1) Create our context object
			val remoteService = RemoteServiceImpl()

			// 2) Create test transports
			val clientTransport = ChannelTransport()
			val serverTransport = clientTransport.flip()

			// 3) Build the server with context + our tool
			val server = Server.Builder()
				.withDispatcher(testDispatcher)
				.withContext(remoteService) // <--- Pass in the context
				.withTool(Server::storeValueInContext) // <--- Register our tool that uses the context
				.withTransport(serverTransport)
				.withTransportLogger(
					logIncoming = { msg -> log.add(serverIncoming(msg)) },
					logOutgoing = { msg -> log.add(serverOutgoing(msg)) },
				)
				.build()
			server.start()

			// 4) Build the client
			val client = Client.Builder()
				.withDispatcher(testDispatcher)
				.withTransport(clientTransport)
				.withTransportLogger(
					logIncoming = { msg -> log.add(clientIncoming(msg)) },
					logOutgoing = { msg -> log.add(clientOutgoing(msg)) },
				)
				.withClientInfo("TestClient", "1.0.0")
				.build()
			client.start()

			// 5) Perform MCP initialization
			client.initialize()
			advanceUntilIdle()
			log.clear()

			// 6) Call our tool "storeValueInContext"
			val response: JsonRpcResponse = client.sendRequest { requestId ->
				CallToolRequest(
					id = requestId,
					params = CallToolParams(
						name = "storeValueInContext",
						arguments = mapOf("newValue" to JsonPrimitive("Hello, context!")),
					),
				)
			}
			advanceUntilIdle()

			// 7) Verify the server side was updated
			val storedValue = remoteService.value
			assertNotNull(storedValue, "Expected the server context to store a value.")
			assertEquals("Hello, context!", storedValue)

			// 8) Verify the returned response content
			val callToolResult = response.result?.deserializeResult<CallToolResult>()
			assertNotNull(callToolResult, "Expected non-null tool result.")
			assertEquals(1, callToolResult.content.size)
			val text = (callToolResult.content.first() as? TextContent)?.text
			assertEquals("Value updated from 'Initial value' to 'Hello, context!'", text)

			// 9) Optionally verify logs
			val expectedLogs = buildLog {
				addClientOutgoing(
					"""{"method":"tools/call","jsonrpc":"2.0","id":"2","params":{"name":"storeValueInContext","arguments":{"newValue":"Hello, context!"}}}""",
				)
				addServerIncoming(
					"""{"method":"tools/call","jsonrpc":"2.0","id":"2","params":{"name":"storeValueInContext","arguments":{"newValue":"Hello, context!"}}}""",
				)
				addServerOutgoing(
					"""{"jsonrpc":"2.0","id":"2","result":{"content":[{"type":"text","text":"Value updated from 'Initial value' to 'Hello, context!'"}]}}""",
				)
				addClientIncoming(
					"""{"jsonrpc":"2.0","id":"2","result":{"content":[{"type":"text","text":"Value updated from 'Initial value' to 'Hello, context!'"}]}}""",
				)
			}
			assertLinesMatch(expectedLogs, log, "Check logs for context-based tool usage")
		}
}
