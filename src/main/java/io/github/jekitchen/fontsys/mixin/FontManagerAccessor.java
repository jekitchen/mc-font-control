package io.github.jekitchen.fontsys.mixin;

import java.util.Map;
import net.minecraft.client.gui.font.FontManager;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read access to {@code FontManager.fontSets}, to find {@code fontsys:vanilla} for the preview (PRD D4). */
@Mixin(FontManager.class)
public interface FontManagerAccessor {
	@Accessor("fontSets")
	Map<Identifier, FontSet> fontsys$fontSets();
}
