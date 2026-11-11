package io.github.jekitchen.mcfontcontrol.pack;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.resources.IoSupplier;
import org.jspecify.annotations.Nullable;

/**
 * The in-memory resource pack (PRD AP-1). It holds font definition JSON and serves the
 * selected font file straight from the player's disk at load time; font bytes are never
 * copied (AP-4). Nothing is written to {@code resourcepacks/}, so uninstalling the mod
 * leaves no trace.
 */
public final class RuntimeFontPack implements PackResources {
	private final PackLocationInfo location;
	private final Map<Identifier, IoSupplier<InputStream>> resources;

	private RuntimeFontPack(PackLocationInfo location, Map<Identifier, IoSupplier<InputStream>> resources) {
		this.location = location;
		this.resources = Map.copyOf(resources);
	}

	/** Collects the files the pack serves. */
	public static final class Builder {
		private final Map<Identifier, IoSupplier<InputStream>> resources = new LinkedHashMap<>();

		public Builder json(Identifier id, String json) {
			byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
			resources.put(id, () -> new ByteArrayInputStream(bytes));
			return this;
		}

		public Builder file(Identifier id, Path path) {
			resources.put(id, () -> Files.newInputStream(path));
			return this;
		}

		public RuntimeFontPack build(PackLocationInfo location) {
			return new RuntimeFontPack(location, resources);
		}
	}

	@Override
	public @Nullable IoSupplier<InputStream> getRootResource(String... path) {
		return null;
	}

	@Override
	public @Nullable IoSupplier<InputStream> getResource(PackType type, Identifier id) {
		return type == PackType.CLIENT_RESOURCES ? resources.get(id) : null;
	}

	@Override
	public void listResources(PackType type, String namespace, String directory, ResourceOutput output) {
		if (type != PackType.CLIENT_RESOURCES) {
			return;
		}
		String prefix = directory.endsWith("/") ? directory : directory + "/";
		resources.forEach((id, supplier) -> {
			if (id.getNamespace().equals(namespace) && id.getPath().startsWith(prefix)) {
				output.accept(id, supplier);
			}
		});
	}

	@Override
	public Set<String> getNamespaces(PackType type) {
		return type == PackType.CLIENT_RESOURCES ? resources.keySet().stream().map(Identifier::getNamespace).collect(Collectors.toUnmodifiableSet()) : Set.of();
	}

	@Override
	public <T> @Nullable T getMetadataSection(MetadataSectionType<T> type) {
		return null;
	}

	@Override
	public PackLocationInfo location() {
		return location;
	}

	@Override
	public void close() {
	}
}
