package io.github.jekitchen.mcfontcontrol.mixin;

import com.mojang.blaze3d.font.GlyphProvider;
import java.util.List;
import net.minecraft.client.gui.font.FontSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read access to {@code FontSet.allProviders}: vanilla's providers become the preview's fallback (PRD D4). */
@Mixin(FontSet.class)
public interface FontSetAccessor {
	@Accessor("allProviders")
	List<GlyphProvider.Conditional> mcfontcontrol$allProviders();
}
