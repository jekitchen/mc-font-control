package io.github.jekitchen.fontsys.gametest;

import io.github.jekitchen.fontsys.Fontsys;
import io.github.jekitchen.fontsys.config.FontSettings;
import io.github.jekitchen.fontsys.discovery.FaceInfo;
import io.github.jekitchen.fontsys.discovery.FontFamily;
import io.github.jekitchen.fontsys.discovery.StyledFaces;
import io.github.jekitchen.fontsys.mixin.FontManagerAccessor;
import io.github.jekitchen.fontsys.mixin.MinecraftAccessor;
import io.github.jekitchen.fontsys.pack.FontStyleSlot;
import io.github.jekitchen.fontsys.pack.PackContents;
import io.github.jekitchen.fontsys.pack.StyleRouting;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;

/**
 * AP-8 / D24: bold and italic text uses the family's real bold and italic faces, or FreeType
 * emboldening when the family has no heavier face, never vanilla's double draw over a
 * system font. Screenshots compare the styles side by side.
 */
@SuppressWarnings("UnstableApiUsage")
public class StyledTextGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		context.waitForScreen(TitleScreen.class);
		GameTestFonts.pick(context);
		List<FontFamily> families = Fontsys.fontIndex().families(false);

		// A family with real Bold and Italic faces.
		Optional<FontFamily> fourStyle = families.stream()
			.filter(f -> List.of("segoe ui", "arial", "helvetica neue", "dejavu sans", "liberation sans").contains(f.name().toLowerCase()))
			.filter(f -> f.bold().isPresent() && f.italic().isPresent())
			.findFirst()
			.or(() -> families.stream().filter(f -> f.bold().isPresent() && f.italic().isPresent()).findFirst());
		fourStyle.ifPresent(family -> {
			FaceInfo regular = family.defaultStyle();
			GameTestFonts.apply(context, GameTestFonts.settingsFor(regular));
			StyledFaces.Variant bold = PackContents.loaded().styles().get(FontStyleSlot.BOLD);
			require(bold != null && !bold.syntheticBold(), family.name() + ": bold should use a real face, got " + bold);
			require(PackContents.loaded().styles().containsKey(FontStyleSlot.ITALIC), family.name() + ": italic face expected");
			for (FontStyleSlot slot : PackContents.loaded().styles().keySet()) {
				FontStyleSlot served = PackContents.loaded().servedBy().get(slot);
				require(PackContents.providerLoaded(served.file()), slot + " face should load");
				require(route(context, slot) == served.font(), slot + " text should use its own font");
			}
			require(context.computeOnClient(mc -> glyph(mc, FontStyleSlot.BOLD, 'A').info().getBoldOffset()) == 0.0F,
				"bold glyphs must not be drawn twice");
			showSamples(context, "ap8-styles-real-" + slug(family.name()));
		});

		// A family with a single upright face: bold is FreeType-emboldened, italic is vanilla's slant.
		Optional<FontFamily> single = families.stream()
			.filter(f -> f.styles().size() == 1 && !f.styles().getFirst().italic() && f.styles().getFirst().weight() <= 500)
			.findFirst();
		single.ifPresent(family -> {
			GameTestFonts.apply(context, GameTestFonts.settingsFor(family.defaultStyle()));
			StyledFaces.Variant bold = PackContents.loaded().styles().get(FontStyleSlot.BOLD);
			require(bold != null && bold.syntheticBold(), family.name() + ": bold should be emboldened, got " + bold);
			require(!PackContents.loaded().styles().containsKey(FontStyleSlot.ITALIC), "no italic face, so vanilla slants");
			require(PackContents.providerLoaded(FontStyleSlot.BOLD.file()), "emboldened face should load");
			require(route(context, FontStyleSlot.BOLD_ITALIC) == FontStyleSlot.BOLD.font(), "bold italic shares the emboldened face");
			require(!PackContents.loaded().files().containsKey(FontStyleSlot.BOLD_ITALIC.file()), "the same face is loaded once");
			float regular = context.computeOnClient(mc -> mc.font.width("Wwm"));
			float emboldened = context.computeOnClient(mc -> mc.font.width(Component.literal("Wwm").withStyle(ChatFormatting.BOLD)));
			require(emboldened > regular, "emboldened text should be wider than regular (" + emboldened + " vs " + regular + ")");
			showSamples(context, "ap8-styles-synthetic-" + slug(family.name()));
		});

		GameTestFonts.apply(context, FontSettings.DEFAULTS);
		require(route(context, FontStyleSlot.BOLD) == FontDescription.DEFAULT, "vanilla font: bold text keeps vanilla's styling");
		context.waitForScreen(TitleScreen.class);
	}

	/** Where the game's main font sends text in this slot's style. */
	private static FontDescription route(ClientGameTestContext context, FontStyleSlot slot) {
		Style style = Style.EMPTY.withBold(slot.bold()).withItalic(slot.italic());
		return context.computeOnClient(mc -> StyleRouting.route(new Object(), FontDescription.DEFAULT, style));
	}

	private static BakedGlyph glyph(Minecraft mc, FontStyleSlot slot, int codepoint) {
		FontSet set = ((FontManagerAccessor) ((MinecraftAccessor) mc).fontsys$fontManager()).fontsys$fontSets().get(slot.font().id());
		return set.source(false).getGlyph(codepoint);
	}

	private static void showSamples(ClientGameTestContext context, String screenshot) {
		context.setScreen(SampleScreen::new);
		context.waitTicks(10);
		context.takeScreenshot(screenshot);
		context.setScreen(TitleScreen::new);
	}

	private static String slug(String name) {
		return name.toLowerCase().replaceAll("[^a-z0-9]+", "-");
	}

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	/** Regular, bold, italic and bold-italic lines, plus a server-style chat line with a bold rank. */
	private static final class SampleScreen extends Screen {
		SampleScreen() {
			super(Component.literal("Styles"));
		}

		@Override
		public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
			super.extractRenderState(graphics, mouseX, mouseY, a);
			String sample = "The quick brown fox jumps over the lazy dog 0123456789";
			int x = 12;
			int y = 12;
			graphics.text(font, Component.literal("Regular: " + sample), x, y, 0xFFFFFFFF, true);
			graphics.text(font, Component.literal("Bold: " + sample).withStyle(ChatFormatting.BOLD), x, y += 14, 0xFFFFFFFF, true);
			graphics.text(font, Component.literal("Italic: " + sample).withStyle(ChatFormatting.ITALIC), x, y += 14, 0xFFFFFFFF, true);
			graphics.text(font, Component.literal("Bold italic: " + sample).withStyle(ChatFormatting.BOLD, ChatFormatting.ITALIC), x, y += 14, 0xFFFFFFFF, true);
			Component chat = Component.empty()
				.append(Component.literal("DONATOR").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
				.append(Component.literal(" ImSensey ").withStyle(ChatFormatting.WHITE))
				.append(Component.literal("AFK").withStyle(ChatFormatting.RED, ChatFormatting.BOLD))
				.append(Component.literal("  ONLINE PLAYERS").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD, ChatFormatting.UNDERLINE));
			graphics.text(font, chat, x, y += 18, 0xFFFFFFFF, true);
		}
	}
}
