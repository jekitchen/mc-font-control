package io.github.jekitchen.mcfontcontrol.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.font.FontManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read access to {@code Minecraft.fontManager}, for the live preview (PRD D4). */
@Mixin(Minecraft.class)
public interface MinecraftAccessor {
	@Accessor("fontManager")
	FontManager mcfontcontrol$fontManager();
}
