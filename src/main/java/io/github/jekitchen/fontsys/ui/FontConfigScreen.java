package io.github.jekitchen.fontsys.ui;

import com.mojang.blaze3d.Blaze3D;
import io.github.jekitchen.fontsys.Fontsys;
import io.github.jekitchen.fontsys.apply.FontController;
import io.github.jekitchen.fontsys.config.FontSettings;
import io.github.jekitchen.fontsys.discovery.FaceInfo;
import io.github.jekitchen.fontsys.discovery.FontFamily;
import io.github.jekitchen.fontsys.discovery.InstanceFonts;
import io.github.jekitchen.fontsys.discovery.OsFamily;
import io.github.jekitchen.fontsys.discovery.PixelGrid;
import io.github.jekitchen.fontsys.discovery.StyledFaces;
import io.github.jekitchen.fontsys.discovery.SystemFontIndex;
import io.github.jekitchen.fontsys.discovery.Visibility;
import io.github.jekitchen.fontsys.pack.FontStyleSlot;
import io.github.jekitchen.fontsys.pack.PackContents;
import io.github.jekitchen.fontsys.pack.RuntimeFontPackSource;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;
import java.util.OptionalInt;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * The config screen (PRD 5.4): searchable family list, style picker, sliders, show-hidden
 * toggle, live preview, and Apply / Reset to vanilla / Rescan fonts.
 */
public final class FontConfigScreen extends Screen {
	private static final int MARGIN = 8;
	private static final int TOP = 22;
	private static final int PREVIEW_DEBOUNCE_TICKS = 3;

	private final @Nullable Screen parent;
	private final FontController controller = Fontsys.controller();
	private final SystemFontIndex index = Fontsys.fontIndex();

	/** What Apply will apply. Starts as the applied settings. */
	private FontSettings draft;
	/** The draft when the screen opened or Apply / Reset was last pressed: Done asks about anything since (UI-13). */
	private FontSettings baseline;
	private String search = "";
	private int seenGeneration = -1;

	private FamilyList list;
	private Button styleButton;
	private Button prevStyle;
	private Button nextStyle;
	private Button applyButton;
	private Button resetButton;
	private ValueSlider oversampleSlider;
	private int previewX0;
	private int previewY0;
	private int previewX1;
	private int previewY1;

	private @Nullable PreviewFont preview;
	private @Nullable Component previewError;
	private boolean previewDirty = true;
	private int previewWait;

	public FontConfigScreen(@Nullable Screen parent) {
		super(Component.translatable("fontsys.screen.title"));
		this.parent = parent;
		this.draft = controller.appliedSettings();
		this.baseline = draft;
		// Pick up fonts added since the last scan, e.g. to the instance's fonts folder (FD-14).
		index.scan();
	}

	@Override
	protected void init() {
		int listWidth = Math.max(120, width * 2 / 5 - MARGIN);
		int rightX = MARGIN + listWidth + MARGIN;
		int rightWidth = width - rightX - MARGIN;
		int half = (rightWidth - 4) / 2;
		int bottom = height - 28;

		EditBox searchBox = new EditBox(font, MARGIN, TOP, listWidth, 16, Component.translatable("fontsys.screen.search"));
		searchBox.setHint(Component.translatable("fontsys.screen.search").withStyle(EditBox.SEARCH_HINT_STYLE));
		searchBox.setValue(search);
		searchBox.setResponder(value -> {
			search = value;
			refreshList();
		});
		addRenderableWidget(searchBox);

		list = new FamilyList(minecraft, listWidth, bottom - 26 - (TOP + 20), TOP + 20);
		list.updateSizeAndPosition(listWidth, bottom - 26 - (TOP + 20), MARGIN, TOP + 20);
		addRenderableWidget(list);

		addRenderableWidget(Checkbox.builder(Component.translatable("fontsys.screen.show_hidden"), font)
			.pos(MARGIN, bottom - 22)
			.selected(controller.config().showHiddenFonts)
			.onValueChange((box, value) -> {
				controller.setShowHiddenFonts(value);
				refreshList();
			})
			.build());

		prevStyle = addRenderableWidget(Button.builder(Component.literal("<"), b -> cycleStyle(-1)).bounds(rightX, TOP, 20, 20).build());
		styleButton = addRenderableWidget(Button.builder(Component.empty(), b -> cycleStyle(1)).bounds(rightX + 22, TOP, rightWidth - 44, 20).build());
		nextStyle = addRenderableWidget(Button.builder(Component.literal(">"), b -> cycleStyle(1)).bounds(rightX + rightWidth - 20, TOP, 20, 20).build());

		addRenderableWidget(new ValueSlider(rightX, TOP + 24, half, FontSettings.MIN_SIZE, FontSettings.MAX_SIZE, 0.5, draft.size(),
			v -> Component.translatable("fontsys.screen.size", format(v)),
			v -> updateLayout(v, draft.oversample(), draft.shiftX(), draft.shiftY())));
		oversampleSlider = addRenderableWidget(new ValueSlider(rightX + half + 4, TOP + 24, half, 0, FontSettings.MAX_OVERSAMPLE, 1, draft.oversample(),
			v -> draft.crispEdges()
				? Component.translatable("fontsys.screen.oversample.crisp", RuntimeFontPackSource.currentGuiScale())
				: v <= 0
				? Component.translatable("fontsys.screen.oversample.auto", RuntimeFontPackSource.currentGuiScale())
				: Component.translatable("fontsys.screen.oversample", format(v)),
			v -> updateLayout(draft.size(), (float) v, draft.shiftX(), draft.shiftY())));
		addRenderableWidget(new ValueSlider(rightX, TOP + 48, half, -FontSettings.MAX_SHIFT, FontSettings.MAX_SHIFT, 0.5, draft.shiftX(),
			v -> Component.translatable("fontsys.screen.shift_x", format(v)),
			v -> updateLayout(draft.size(), draft.oversample(), (float) v, draft.shiftY())));
		addRenderableWidget(new ValueSlider(rightX + half + 4, TOP + 48, half, -FontSettings.MAX_SHIFT, FontSettings.MAX_SHIFT, 0.5, draft.shiftY(),
			v -> Component.translatable("fontsys.screen.shift_y", format(v)),
			v -> updateLayout(draft.size(), draft.oversample(), draft.shiftX(), (float) v)));
		Checkbox uniform = addRenderableWidget(Checkbox.builder(Component.translatable("fontsys.screen.apply_to_uniform"), font)
			.pos(rightX, TOP + 72)
			.selected(draft.applyToUniform())
			.tooltip(Tooltip.create(Component.translatable("fontsys.screen.apply_to_uniform.tooltip")))
			.onValueChange((box, value) -> {
				draft = draft.withApplyToUniform(value);
				updateButtons();
			})
			.build());
		Checkbox crisp = addRenderableWidget(Checkbox.builder(Component.translatable("fontsys.screen.crisp"), font)
			.selected(draft.crispEdges())
			.tooltip(Tooltip.create(Component.translatable("fontsys.screen.crisp.tooltip")))
			.onValueChange((box, value) -> setCrispEdges(value))
			.build());
		// Under the sliders (UI-9): beside the Unicode checkbox when both fit, otherwise below it.
		int crispX = rightX + rightWidth - crisp.getWidth();
		boolean sameRow = crispX >= uniform.getRight() + 8;
		crisp.setPosition(sameRow ? crispX : rightX, sameRow ? TOP + 72 : TOP + 92);
		updateOversampleSlider();

		previewX0 = rightX;
		previewY0 = sameRow ? TOP + 96 : TOP + 116;
		previewX1 = rightX + rightWidth;
		previewY1 = bottom - 16;

		int buttonWidth = (width - 2 * MARGIN - 4 * 4) / 5;
		applyButton = addRenderableWidget(Button.builder(Component.translatable("fontsys.screen.apply"), b -> apply())
			.bounds(MARGIN, bottom + 4, buttonWidth, 20).build());
		resetButton = addRenderableWidget(Button.builder(Component.translatable("fontsys.screen.reset"), b -> resetToVanilla())
			.bounds(MARGIN + (buttonWidth + 4), bottom + 4, buttonWidth, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("fontsys.screen.rescan"), b -> index.scan())
			.bounds(MARGIN + 2 * (buttonWidth + 4), bottom + 4, buttonWidth, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("fontsys.screen.open_folder"), b -> openInstanceFolder())
			.bounds(MARGIN + 3 * (buttonWidth + 4), bottom + 4, buttonWidth, 20)
			.tooltip(Tooltip.create(Component.translatable("fontsys.screen.open_folder.tooltip")))
			.build());
		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
			.bounds(MARGIN + 4 * (buttonWidth + 4), bottom + 4, buttonWidth, 20).build());

		seenGeneration = -1;
		refreshList();
		updateButtons();
		setInitialFocus(searchBox);
		previewDirty = true;
	}

	private static String format(double v) {
		return String.format(Locale.ROOT, v == Math.rint(v) ? "%.0f" : "%.1f", v);
	}

	// ---- Font list ----------------------------------------------------------------------

	private void refreshList() {
		if (list == null) {
			return;
		}
		seenGeneration = index.generation();
		String needle = search.toLowerCase(Locale.ROOT).strip();
		List<FontFamily> families = index.families(controller.config().showHiddenFonts).stream()
			.filter(f -> needle.isEmpty() || f.name().toLowerCase(Locale.ROOT).contains(needle))
			.toList();
		list.show(families, draft.familyName(), draft.fontPath());
		updateButtons();
	}

	private @Nullable FontFamily selectedFamily() {
		FamilyList.Entry entry = list == null ? null : list.getSelected();
		if (entry != null) {
			return entry.family;
		}
		if (draft.familyName() == null) {
			return null;
		}
		return index.families(true).stream().filter(f -> f.holds(draft.familyName(), draft.fontPath())).findFirst().orElse(null);
	}

	/**
	 * Creates the instance's {@code fonts/} folder if needed and opens it in the file manager,
	 * as the resource-pack screen does for its folder (FD-14). The list rescans when the screen
	 * is opened again or Rescan is pressed.
	 */
	private void openInstanceFolder() {
		InstanceFonts.dir().ifPresent(dir -> {
			try {
				Files.createDirectories(dir);
				Blaze3D.openPath(dir);
			} catch (IOException | RuntimeException e) {
				Fontsys.LOGGER.warn("[{}] Could not open the fonts folder {}: {}", Fontsys.DISPLAY_NAME, dir.getFileName(), e.toString());
			}
		});
	}

	/** The family highlighted in the list, if any; read by the game tests. */
	public @Nullable FontFamily listSelection() {
		FamilyList.Entry entry = list == null ? null : list.getSelected();
		return entry == null ? null : entry.family;
	}

	public void selectFamily(FontFamily family) {
		FaceInfo face = draft.styleName() == null ? family.defaultStyle() : family.findStyle(draft.styleName()).orElse(family.defaultStyle());
		if (!family.holds(draft.familyName(), draft.fontPath())) {
			face = family.defaultStyle();
		}
		selectFace(face);
	}

	private void selectFace(FaceInfo face) {
		draft = draft.withFont(face.path(), face.faceIndex(), face.family(), face.style());
		previewDirty = true;
		updateButtons();
	}

	private void cycleStyle(int direction) {
		FontFamily family = selectedFamily();
		if (family == null || family.styles().size() < 2) {
			return;
		}
		List<FaceInfo> styles = family.styles();
		int current = 0;
		for (int i = 0; i < styles.size(); i++) {
			if (styles.get(i).faceIndex() == draft.faceIndex() && styles.get(i).path().equals(draft.fontPath())) {
				current = i;
			}
		}
		selectFace(styles.get(Math.floorMod(current + direction, styles.size())));
	}

	/** Crisp mode (AP-10, D34): sharpness follows the GUI scale, so the slider steps aside. */
	private void setCrispEdges(boolean value) {
		draft = draft.withCrispEdges(value);
		updateOversampleSlider();
		previewDirty = true;
		updateButtons();
	}

	private void updateOversampleSlider() {
		oversampleSlider.active = !draft.crispEdges();
		oversampleSlider.refreshMessage();
	}

	private void updateLayout(double size, float oversample, float shiftX, float shiftY) {
		draft = draft.withLayout((float) size, oversample, shiftX, shiftY).sanitized();
		previewDirty = true;
		updateButtons();
	}

	// ---- Apply / reset --------------------------------------------------------------------

	private void apply() {
		if (draft.fontPath() == null) {
			return;
		}
		closePreview();
		baseline = draft;
		controller.applyWithConfirmation(draft.withEnabled(true), this);
	}

	private void resetToVanilla() {
		closePreview();
		draft = draft.withEnabled(false);
		baseline = draft;
		controller.applyWithConfirmation(controller.appliedSettings().withEnabled(false), this);
	}

	/** Whether Apply would change the game's font. */
	private boolean canApply() {
		return draft.fontPath() != null && !controller.stepAside() && !controller.appliedSettings().sameRendering(draft.withEnabled(true));
	}

	/**
	 * Whether closing the screen should ask first (UI-13): the settings changed since the screen
	 * opened or Apply was last pressed, and Apply would change the font.
	 */
	public boolean hasUnappliedChanges() {
		return !draft.equals(baseline) && canApply();
	}

	/** "Apply" in {@link UnappliedChangesScreen}: applies, and the keep-or-revert dialog then leaves the settings. */
	void applyAndClose() {
		closePreview();
		baseline = draft;
		controller.applyWithConfirmation(draft.withEnabled(true), parent);
	}

	/** "Discard changes" in {@link UnappliedChangesScreen}: leaves; the applied font is untouched. */
	void discardAndClose() {
		minecraft.gui.setScreen(parent);
	}

	private void updateButtons() {
		if (applyButton == null) {
			return;
		}
		applyButton.active = canApply();
		resetButton.active = controller.appliedSettings().isActive();
		FontFamily family = selectedFamily();
		boolean manyStyles = family != null && family.styles().size() > 1;
		prevStyle.active = manyStyles;
		nextStyle.active = manyStyles;
		styleButton.active = manyStyles;
		styleButton.setMessage(draft.styleName() == null
			? Component.translatable("fontsys.screen.style.none")
			: Component.translatable("fontsys.screen.style", draft.styleName()));
	}

	// ---- Preview -----------------------------------------------------------------------------

	@Override
	public void tick() {
		if (index.generation() != seenGeneration) {
			refreshList();
		}
		// A resource reload closes the vanilla providers the preview shares; drop the preview
		// while the loading overlay is up and rebuild it afterwards.
		if (minecraft.gui.overlay() != null) {
			closePreview();
			previewDirty = true;
			return;
		}
		if (previewDirty && ++previewWait >= PREVIEW_DEBOUNCE_TICKS) {
			previewWait = 0;
			previewDirty = false;
			rebuildPreview();
		}
	}

	private void rebuildPreview() {
		closePreview();
		previewError = null;
		if (draft.fontPath() == null) {
			return;
		}
		try {
			preview = PreviewFont.create(draft.sanitized(), RuntimeFontPackSource.currentGuiScale());
		} catch (IOException | RuntimeException | LinkageError e) {
			previewError = Component.translatable("fontsys.preview.error");
			Fontsys.LOGGER.warn("[{}] Preview of '{} {}' failed: {}", Fontsys.DISPLAY_NAME, draft.familyName(), draft.styleName(), e.toString());
		}
	}

	private void closePreview() {
		if (preview != null) {
			preview.close();
			preview = null;
		}
	}

	@Override
	public void removed() {
		closePreview();
		previewDirty = true;
	}

	/** Done and Esc: asks first if there are changes that haven't been applied (UI-13). */
	@Override
	public void onClose() {
		minecraft.gui.setScreen(hasUnappliedChanges() ? new UnappliedChangesScreen(this) : parent);
	}

	// ---- Drawing -------------------------------------------------------------------------------

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		graphics.centeredText(font, title, width / 2, 8, 0xFFFFFFFF);
		extractPreview(graphics);
		if (list.children().isEmpty()) {
			// Empty list: explain why, wrapped inside the list area (FD-7, FD-10).
			Component why = search.isBlank() ? status() : Component.translatable("fontsys.screen.no_match", search);
			graphics.textWithWordWrap(font, why, list.getX() + 6, list.getY() + 6, list.getWidth() - 12, 0xFFA0A0A0);
		} else {
			graphics.text(font, status(), MARGIN + list.getWidth() + MARGIN, height - 40, 0xFFA0A0A0, false);
		}
	}

	private void extractPreview(GuiGraphicsExtractor graphics) {
		graphics.fill(previewX0, previewY0, previewX1, previewY1, 0xA0000000);
		graphics.outline(previewX0, previewY0, previewX1 - previewX0, previewY1 - previewY0, 0xFF555555);
		PreviewFont current = preview;
		if (current == null || minecraft.gui.overlay() != null) {
			Component message = previewError != null ? previewError
				: draft.fontPath() == null ? Component.translatable("fontsys.preview.empty")
				: Component.translatable("fontsys.preview.loading");
			graphics.textWithWordWrap(font, message, previewX0 + 6, previewY0 + 6, previewX1 - previewX0 - 12, 0xFFA0A0A0);
			return;
		}
		Font pf = current.font();
		int x = previewX0 + 6;
		int w = previewX1 - previewX0 - 12;
		int line = Math.max(10, (int) Math.ceil(draft.size()) + 3);
		int y = previewY0 + 5;
		graphics.enableScissor(previewX0 + 1, previewY0 + 1, previewX1 - 1, previewY1 - 1);
		Component hint = pixelFontHint(current);
		if (hint != null) {
			// First, so it stays visible however little room the samples leave.
			graphics.text(font, hint, x, y, 0xFFFFE080, false);
			y += font.lineHeight + 3;
		}
		graphics.text(pf, Component.translatable("fontsys.preview.pangram"), x, y, 0xFFFFFFFF, true);
		y += line;
		graphics.text(pf, Component.translatable("fontsys.preview.digits"), x, y, 0xFFD0D0D0, true);
		y += line;
		graphics.text(pf, styleSample(), x, y, 0xFFFFFFFF, true);
		y += line + 3;
		// Chat line
		graphics.fill(x - 2, y - 2, x + w, y + line - 2, 0x80000000);
		graphics.text(pf, Component.translatable("fontsys.preview.chat"), x, y, 0xFFFFFFFF, true);
		y += line + 4;
		// Tooltip and sign samples side by side
		Component tooltip = Component.translatable("fontsys.preview.tooltip");
		int tw = Math.min(w / 2 - 4, pf.width(tooltip) + 8);
		graphics.fill(x - 2, y - 3, x - 2 + tw, y + line - 1, 0xF0100010);
		graphics.outline(x - 2, y - 3, tw, line + 2, 0xFF5000FF);
		graphics.text(pf, tooltip, x + 2, y, 0xFFFFFF55, true);
		Component sign = Component.translatable("fontsys.preview.sign");
		int sx = x + tw + 6;
		int sw = Math.min(x + w - sx, pf.width(sign) + 12);
		graphics.fill(sx, y - 3, sx + sw, y + line - 1, 0xFFA2824E);
		graphics.text(pf, sign, sx + 6, y, 0xFF000000, false);
		// Which faces bold and italic use, when the panel has room below the samples.
		int labelY = y + line + 3;
		if (labelY + font.lineHeight <= previewY1 - 2) {
			current.styles().ifPresent(styles -> graphics.text(font, facesLabel(current, styles), x, labelY, 0xFFA0A0A0, false));
		}
		graphics.disableScissor();
	}

	/**
	 * For a pixel-style font (PRD 5.5): suggests crisp mode while it's off, then a size on the
	 * font's pixel grid if the current one isn't. Never changes anything by itself.
	 */
	private @Nullable Component pixelFontHint(PreviewFont current) {
		if (current.pixelGrid().isEmpty()) {
			return null;
		}
		int cells = current.pixelGrid().getAsInt();
		OptionalInt size = PixelGrid.suggestedSize(cells, draft.size(), FontSettings.MIN_SIZE, FontSettings.MAX_SIZE);
		boolean onGrid = size.isPresent() && size.getAsInt() == draft.size();
		if (!draft.crispEdges()) {
			return size.isPresent() && !onGrid
				? Component.translatable("fontsys.preview.pixel_font.crisp_size", size.getAsInt())
				: Component.translatable("fontsys.preview.pixel_font.crisp");
		}
		return size.isPresent() && !onGrid ? Component.translatable("fontsys.preview.pixel_font.size", size.getAsInt()) : null;
	}

	/** "Bold text  Italic text  Bold italic", each in its style (AP-8). */
	private static Component styleSample() {
		return Component.empty()
			.append(Component.translatable("fontsys.preview.bold").withStyle(ChatFormatting.BOLD))
			.append(Component.translatable("fontsys.preview.separator"))
			.append(Component.translatable("fontsys.preview.italic").withStyle(ChatFormatting.ITALIC))
			.append(Component.translatable("fontsys.preview.separator"))
			.append(Component.translatable("fontsys.preview.bold_italic").withStyle(ChatFormatting.BOLD, ChatFormatting.ITALIC));
	}

	/** Which faces bold and italic text use, e.g. "Bold: Bold · Italic: Italic" (AP-8, D24). */
	private static Component facesLabel(PreviewFont preview, StyledFaces styles) {
		Component bold = preview.hasStyledFace(FontStyleSlot.BOLD) ? faceName(styles.bold()) : Component.translatable("fontsys.preview.face.simulated");
		Component italic = preview.hasStyledFace(FontStyleSlot.ITALIC) && styles.italic().isPresent()
			? faceName(styles.italic().get())
			: Component.translatable("fontsys.preview.face.slanted");
		return Component.translatable("fontsys.preview.faces", bold, italic);
	}

	private static Component faceName(StyledFaces.Variant variant) {
		Component style = Component.literal(variant.face().style());
		return variant.syntheticBold() ? Component.translatable("fontsys.preview.face.emboldened", style) : style;
	}

	private Component status() {
		if (controller.conflict().isPresent()) {
			return Component.translatable("fontsys.status.conflict", controller.conflict().get().name());
		}
		if (index.os() == OsFamily.OTHER) {
			return Component.translatable("fontsys.status.unsupported_os");
		}
		if (index.state() == SystemFontIndex.State.SCANNING) {
			return Component.translatable("fontsys.status.scanning");
		}
		if (index.families(true).isEmpty() && io.github.jekitchen.fontsys.discovery.FontDirectories.flatpakWithoutHostFonts()) {
			return Component.translatable("fontsys.status.flatpak");
		}
		if (index.state() == SystemFontIndex.State.FAILED || index.families(true).isEmpty()) {
			return Component.translatable("fontsys.status.no_fonts");
		}
		FontSettings applied = controller.appliedSettings();
		if (applied.isActive() && !PackContents.providerLoaded() && Minecraft.getInstance().gui.overlay() == null) {
			return Component.translatable("fontsys.status.load_failed", applied.familyName());
		}
		return applied.isActive()
			? Component.translatable("fontsys.status.current", applied.familyName() + " " + applied.styleName())
			: Component.translatable("fontsys.status.vanilla");
	}

	// ---- Family list widget ----------------------------------------------------------------------

	private final class FamilyList extends ObjectSelectionList<FamilyList.Entry> {
		FamilyList(Minecraft minecraft, int width, int height, int y) {
			super(minecraft, width, height, y, 14);
		}

		void show(List<FontFamily> families, @Nullable String selectedName, @Nullable String selectedPath) {
			Entry selected = null;
			List<Entry> entries = families.stream().map(Entry::new).toList();
			for (Entry e : entries) {
				if (e.family.holds(selectedName, selectedPath)) {
					selected = e;
				}
			}
			replaceEntries(entries);
			setSelected(selected);
			if (selected != null) {
				centerScrollOn(selected);
			} else {
				setScrollAmount(0);
			}
		}

		@Override
		public int getRowWidth() {
			return width - 14;
		}

		@Override
		protected int scrollBarX() {
			return getRight() - 6;
		}

		final class Entry extends ObjectSelectionList.Entry<Entry> {
			final FontFamily family;

			Entry(FontFamily family) {
				this.family = family;
			}

			@Override
			public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
				int y = getContentYMiddle() - 4;
				int right = getContentX() + getContentWidth() - 2;
				Visibility v = family.visibility();
				if (v != Visibility.VISIBLE) {
					Component tag = Component.translatable("fontsys.hidden." + v.name().toLowerCase(Locale.ROOT));
					right -= font.width(tag);
					graphics.text(font, tag, right, y, 0xFF909090, false);
					right -= 6;
				}
				if (family.instance()) {
					Component tag = Component.translatable("fontsys.screen.instance_tag");
					right -= font.width(tag);
					graphics.text(font, tag, right, y, 0xFF78ECBA, false);
					right -= 6;
				}
				// Long names (or a wide applied font) are cut short rather than run under the tags.
				int room = right - (getContentX() + 2);
				String name = font.width(family.name()) <= room ? family.name()
					: font.plainSubstrByWidth(family.name(), Math.max(0, room - font.width("…"))) + "…";
				graphics.text(font, name, getContentX() + 2, y, 0xFFFFFFFF, false);
			}

			@Override
			public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
				FamilyList.this.setSelected(this);
				selectFamily(family);
				return true;
			}

			@Override
			public Component getNarration() {
				Component name = family.instance()
					? Component.translatable("fontsys.screen.instance_narration", family.name())
					: Component.literal(family.name());
				return Component.translatable("narrator.select", name);
			}
		}
	}
}
