package io.github.jekitchen.fontsys.platform;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import io.github.jekitchen.fontsys.Fontsys;
import io.github.jekitchen.fontsys.discovery.FaceInfo;
import io.github.jekitchen.fontsys.discovery.FontFamily;
import io.github.jekitchen.fontsys.discovery.SystemFontIndex;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import net.minecraft.network.chat.Component;

/**
 * Developer aid (PRD FD-8): {@code /fontsys fonts [filter]} lists discovered families
 * and their styles; {@code /fontsys rescan} rescans font folders.
 *
 * <p>Built for any command source type, because each loader has its own client command
 * source (Fabric's {@code FabricClientCommandSource}, NeoForge's {@code CommandSourceStack});
 * the loader passes how to show a line of feedback to the player (D51).
 */
public final class FontsysCommand {
	private static final int MAX_LINES = 40;

	private FontsysCommand() {
	}

	public static <S> LiteralArgumentBuilder<S> build(BiConsumer<S, Component> feedback) {
		return LiteralArgumentBuilder.<S>literal(Fontsys.MOD_ID)
			.then(LiteralArgumentBuilder.<S>literal("fonts")
				.executes(ctx -> list(ctx.getSource(), feedback, ""))
				.then(RequiredArgumentBuilder.<S, String>argument("filter", StringArgumentType.greedyString())
					.executes(ctx -> list(ctx.getSource(), feedback, StringArgumentType.getString(ctx, "filter")))))
			.then(LiteralArgumentBuilder.<S>literal("rescan").executes(ctx -> {
				Fontsys.fontIndex().scan();
				feedback.accept(ctx.getSource(), Component.translatable("fontsys.command.rescan"));
				return 1;
			}));
	}

	private static <S> int list(S source, BiConsumer<S, Component> feedback, String filter) {
		SystemFontIndex index = Fontsys.fontIndex();
		if (index.state() == SystemFontIndex.State.SCANNING && index.index().files().isEmpty()) {
			feedback.accept(source, Component.translatable("fontsys.status.scanning"));
			return 0;
		}
		String needle = filter.toLowerCase(Locale.ROOT);
		List<FontFamily> families = index.families(true).stream()
			.filter(f -> f.name().toLowerCase(Locale.ROOT).contains(needle))
			.toList();
		feedback.accept(source, Component.translatable("fontsys.command.fonts.header", families.size(), index.families(false).size()));
		families.stream().limit(MAX_LINES).forEach(family -> {
			String styles = family.styles().stream().map(FaceInfo::style).collect(Collectors.joining(", "));
			String hidden = family.visibility().isShown(false) ? "" : " [" + family.visibility().name().toLowerCase(Locale.ROOT) + "]";
			feedback.accept(source, Component.literal(family.name() + hidden + ": " + styles));
		});
		if (families.size() > MAX_LINES) {
			feedback.accept(source, Component.translatable("fontsys.command.fonts.more", families.size() - MAX_LINES));
		}
		return families.size();
	}
}
