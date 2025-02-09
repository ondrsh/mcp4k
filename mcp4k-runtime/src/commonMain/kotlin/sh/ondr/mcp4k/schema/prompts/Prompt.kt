package sh.ondr.mcp4k.schema.prompts

import kotlinx.serialization.Serializable

@Serializable
data class Prompt(
	val name: String,
	val description: String? = null,
	val arguments: List<PromptArgument>? = null,
)
