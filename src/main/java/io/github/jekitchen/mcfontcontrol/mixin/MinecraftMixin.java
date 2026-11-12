package io.github.jekitchen.mcfontcontrol.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.jekitchen.mcfontcontrol.McFontControl;
import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.RepositorySource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Adds the runtime font pack to the client pack list (PRD AP-1, D3).
 *
 * <p>Target: {@code Minecraft.<init>(GameConfig)}, the
 * {@code new PackRepository(clientPackSource, downloadedPackSource, directoryPacks)} call.
 * Fabric API 0.166 for 26.3 has no hook for adding a dynamic pack source (its resource
 * loader only registers packs stored in a mod's jar), so this mixin appends ours.
 */
@Mixin(Minecraft.class)
abstract class MinecraftMixin {
	@WrapOperation(method = "<init>", at = @At(value = "NEW", target = "([Lnet/minecraft/server/packs/repository/RepositorySource;)Lnet/minecraft/server/packs/repository/PackRepository;"))
	private PackRepository mcfontcontrol$addRuntimeFontPack(RepositorySource[] sources, Operation<PackRepository> original) {
		RepositorySource[] withOurs = Arrays.copyOf(sources, sources.length + 1);
		withOurs[sources.length] = McFontControl.packSource();
		return original.call((Object) withOurs);
	}
}
