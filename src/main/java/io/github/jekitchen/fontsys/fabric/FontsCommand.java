package io.github.jekitchen.fontsys.fabric;

import com.mojang.brigadier.CommandDispatcher;
import io.github.jekitchen.fontsys.platform.FontsysCommand;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

/** Registers {@code /fontsys} as a Fabric client command (PRD FD-8); the command itself is {@link FontsysCommand}. */
final class FontsCommand {
	private FontsCommand() {
	}

	static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
		dispatcher.register(FontsysCommand.<FabricClientCommandSource>build(FabricClientCommandSource::sendFeedback));
	}
}
