package io.github.jekitchen.fontsys.fabric;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.github.jekitchen.fontsys.Fontsys;
import io.github.jekitchen.fontsys.discovery.FaceInfo;
import io.github.jekitchen.fontsys.discovery.FontFamily;
import io.github.jekitchen.fontsys.discovery.SystemFontIndex;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

/**
 * Developer aid (PRD FD-8): {@code /fontsys fonts [filter]} lists discovered families
 * and their styles; {@code /fontsys rescan} rescans font folders.
 */
final class FontsCommand {
	private static final int MAX_LINES = 40;

	private FontsCommand() {
	}

	static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
		dispatcher.register(literal(Fontsys.MOD_ID)
			.then(literal("fonts")
				.executes(ctx -> list(ctx.getSource(), ""))
				.then(argument("filter", StringArgumentType.greedyString())
					.executes(ctx -> list(ctx.getSource(), StringArgumentType.getString(ctx, "filter")))))
			.then(literal("rescan").executes(ctx -> {
				Fontsys.fontIndex().scan();
				ctx.getSource().sendFeedback(Component.translatable("fontsys.command.rescan"));
				return 1;
			})));
	}

	private static int list(FabricClientCommandSource source, String filter) {
		SystemFontIndex index = Fontsys.fontIndex();
		if (index.state() == SystemFontIndex.State.SCANNING && index.index().files().isEmpty()) {
			source.sendFeedback(Component.translatable("fontsys.status.scanning"));
			return 0;
		}
		String needle = filter.toLowerCase(Locale.ROOT);
		List<FontFamily> families = index.families(true).stream()
			.filter(f -> f.name().toLowerCase(Locale.ROOT).contains(needle))
			.toList();
		source.sendFeedback(Component.translatable("fontsys.command.fonts.header", families.size(), index.families(false).size()));
		families.stream().limit(MAX_LINES).forEach(family -> {
			String styles = family.styles().stream().map(FaceInfo::style).collect(Collectors.joining(", "));
			String hidden = family.visibility().isShown(false) ? "" : " [" + family.visibility().name().toLowerCase(Locale.ROOT) + "]";
			source.sendFeedback(Component.literal(family.name() + hidden + ": " + styles));
		});
		if (families.size() > MAX_LINES) {
			source.sendFeedback(Component.translatable("fontsys.command.fonts.more", families.size() - MAX_LINES));
		}
		return families.size();
	}
}
