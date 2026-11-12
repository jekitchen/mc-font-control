package io.github.jekitchen.mcfontcontrol.pack;

import io.github.jekitchen.mcfontcontrol.McFontControl;
import io.github.jekitchen.mcfontcontrol.config.FontSettings;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackCompatibility;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.RepositorySource;
import net.minecraft.world.flag.FeatureFlagSet;

/**
 * Offers the runtime pack to the game's client pack list (PRD AP-1, D3).
 *
 * <p>Ordering (D11): the pack is <em>required</em> with default position TOP but not fixed.
 * {@code Pack.Position.insert} places a non-fixed TOP pack above the player's packs but
 * below fixed TOP packs, which is how server-provided packs are configured
 * ({@code DownloadedPackSource}). Server UI glyphs therefore always win.
 */
public final class RuntimeFontPackSource implements RepositorySource {
	public static final String PACK_ID = McFontControl.MOD_ID;
	private static final PackSelectionConfig SELECTION = new PackSelectionConfig(true, Pack.Position.TOP, false);

	private final Supplier<FontSettings> settings;
	private final Supplier<Boolean> stepAside;

	public RuntimeFontPackSource(Supplier<FontSettings> settings, Supplier<Boolean> stepAside) {
		this.settings = settings;
		this.stepAside = stepAside;
	}

	@Override
	public void loadPacks(Consumer<Pack> result) {
		PackLocationInfo location = new PackLocationInfo(PACK_ID, Component.translatable("mcfontcontrol.pack.title"), PackSource.BUILT_IN, Optional.empty());
		Pack.Metadata metadata = new Pack.Metadata(Component.translatable("mcfontcontrol.pack.description"), PackCompatibility.COMPATIBLE, FeatureFlagSet.of(), List.of());
		result.accept(new Pack(location, new Pack.ResourcesSupplier() {
			@Override
			public net.minecraft.server.packs.PackMetadataResources openMetadata(PackLocationInfo loc) {
				return new RuntimeFontPack.Builder().build(loc);
			}

			@Override
			public Stream<PackResources> openResources(PackLocationInfo loc, Pack.Metadata meta) {
				RuntimeFontPack.Builder builder = new RuntimeFontPack.Builder();
				PackContents.build(builder, settings.get(), stepAside.get(), currentGuiScale(),
					() -> McFontControl.fontIndex() == null ? io.github.jekitchen.mcfontcontrol.discovery.FontIndex.empty() : McFontControl.fontIndex().bestAvailable());
				return Stream.of(builder.build(loc));
			}
		}, metadata, SELECTION));
	}

	/** The GUI scale now, computing it from options if the window has not set one yet. */
	public static int currentGuiScale() {
		Minecraft mc = Minecraft.getInstance();
		if (mc == null || mc.getWindow() == null) {
			return 2;
		}
		int scale = mc.getWindow().getGuiScale();
		if (scale <= 0 && mc.options != null) {
			scale = mc.getWindow().calculateScale(mc.options.guiScale().get(), mc.options.forceUnicodeFont().get());
		}
		return Math.max(1, scale);
	}
}
