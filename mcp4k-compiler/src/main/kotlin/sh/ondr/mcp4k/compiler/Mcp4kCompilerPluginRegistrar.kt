package sh.ondr.mcp4k.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment

@OptIn(ExperimentalCompilerApi::class)
class Mcp4kCompilerPluginRegistrar : CompilerPluginRegistrar() {
	override val pluginId = "sh.ondr.mcp4k"
	override val supportsK2 = true

	override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
		val messageCollector = configuration[CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.NONE]
		val isTest = configuration.get(Mcp4kConfigurationKeys.IS_TEST_SET, false)

		IrGenerationExtension.registerExtension(
			object : IrGenerationExtension {
				override fun generate(
					moduleFragment: IrModuleFragment,
					pluginContext: IrPluginContext,
				) {
					moduleFragment.transform(
						Mcp4kIrTransformer(
							messageCollector = messageCollector,
							pluginContext = pluginContext,
							isTest = isTest,
						),
						data = null,
					)
				}
			},
		)
	}
}
