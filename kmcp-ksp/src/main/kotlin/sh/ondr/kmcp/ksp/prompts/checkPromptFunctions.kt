package sh.ondr.kmcp.ksp.prompts

import com.google.devtools.ksp.getVisibility
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.symbol.Visibility
import sh.ondr.kmcp.ksp.KmcpProcessor

internal fun KmcpProcessor.checkPromptFunctions(prompts: List<PromptMeta>): Boolean {
	var errorsFound = false

	// 1. Unique function names (already handled by the aggregator in processPrompts, but let's re-check)
	prompts.groupBy { it.functionName }
		.filterValues { it.size > 1 }
		.forEach { (name, promptList) ->
			val fqNames = promptList.joinToString(", ") { it.fqName }
			logger.error(
				"KMCP error: Multiple @McpPrompt functions share the name '$name'. " +
					"Prompt names must be unique. Conflicts: $fqNames",
			)
			errorsFound = true
		}

	// 2. Return type must be GetPromptResult
	prompts.filter { it.returnTypeFqn != "sh.ondr.kmcp.schema.prompts.GetPromptResult" }
		.forEach { prompt ->
			logger.error(
				"KMCP error: @McpPrompt function '${prompt.functionName}' must return GetPromptResult. " +
					"Currently returns: ${prompt.returnTypeReadable}. ",
				symbol = prompt.ksFunction,
			)
			errorsFound = true
		}

	// 3. Parameter types must be String or String?
	for (prompt in prompts) {
		prompt.params
			.filterNot { param ->
				param.fqnType == "kotlin.String" || param.fqnType == "kotlin.String?"
			}
			.forEach { param ->
				logger.error(
					"KMCP error: @McpPrompt function '${prompt.functionName}' has a parameter '${param.name}' " +
						"with unsupported type '${param.readableType}'. " +
						"Only String (or String?) is allowed for prompt parameters.",
					symbol = prompt.ksFunction,
				)
				errorsFound = true
			}
	}

	// 4. Limit non-required parameters
	val nonRequiredLimit = 7
	prompts.filter { it.params.count { p -> !p.isRequired } > nonRequiredLimit }
		.forEach { prompt ->
			logger.error(
				"KMCP error: @McpPrompt function '${prompt.functionName}' has more than $nonRequiredLimit non-required parameters. " +
					"Please reduce optional/nullable/default parameters to $nonRequiredLimit or fewer.",
				symbol = prompt.ksFunction,
			)
			errorsFound = true
		}

	// 5. Must be top-level function
	prompts.filter { it.ksFunction.parentDeclaration != null }
		.forEach { prompt ->
			val parent = prompt.ksFunction.parentDeclaration?.qualifiedName?.asString() ?: "unknown parent"
			logger.error(
				"KMCP error: @McpPrompt function '${prompt.functionName}' is defined inside a class or object ($parent). " +
					"@McpPrompt functions must be top-level. Move '${prompt.functionName}' to file scope.",
				symbol = prompt.ksFunction,
			)
			errorsFound = true
		}

	// 6. Must have no disallowed modifiers and be public
	val disallowedModifiers = setOf(
		Modifier.INLINE,
		Modifier.PRIVATE,
		Modifier.PROTECTED,
		Modifier.INTERNAL,
		Modifier.ABSTRACT,
		Modifier.OPEN,
	)

	prompts.forEach { prompt ->
		val visibility = prompt.ksFunction.getVisibility()
		if (visibility != Visibility.PUBLIC) {
			logger.error(
				"KMCP error: @McpPrompt function '${prompt.functionName}' must be public. " +
					"Current visibility: $visibility. " +
					"Please ensure it's a top-level public function with no additional modifiers.",
				symbol = prompt.ksFunction,
			)
			errorsFound = true
		}

		val foundDisallowed = prompt.ksFunction.modifiers.filter { it in disallowedModifiers }
		if (foundDisallowed.isNotEmpty()) {
			logger.error(
				"KMCP error: @McpPrompt function '${prompt.functionName}' has disallowed modifiers: $foundDisallowed. " +
					"Only public, top-level, non-inline, non-abstract, non-internal functions are allowed.",
				symbol = prompt.ksFunction,
			)
			errorsFound = true
		}
	}

	return errorsFound
}
