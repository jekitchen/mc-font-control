package io.github.jekitchen.mcfontcontrol.ui;

import io.github.jekitchen.mcfontcontrol.McFontControl;
import io.github.jekitchen.mcfontcontrol.apply.FontController;
import io.github.jekitchen.mcfontcontrol.config.FontSettings;
import io.github.jekitchen.mcfontcontrol.discovery.FaceInfo;
import io.github.jekitchen.mcfontcontrol.discovery.FontFamily;
import io.github.jekitchen.mcfontcontrol.discovery.OsFamily;
import io.github.jekitchen.mcfontcontrol.discovery.SystemFontIndex;
import io.github.jekitchen.mcfontcontrol.discovery.Visibility;
import io.github.jekitchen.mcfontcontrol.pack.PackContents;
import io.github.jekitchen.mcfontcontrol.pack.RuntimeFontPackSource;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
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
	private final FontController controller = McFontControl.controller();
	private final SystemFontIndex index = McFontControl.fontIndex();

	/** What Apply will apply. Starts as the applied settings. */
	private FontSettings draft;
	private String search = "";
	private int seenGeneration = -1;

	private FamilyList list;
	private Button styleButton;
	private Button prevStyle;
	private Button nextStyle;
	private Button applyButton;
	private Button resetButton;
	private int previewX0;
	private int previewY0;
	private int previewX1;
	private int previewY1;

	private @Nullable PreviewFont preview;
	private @Nullable Component previewError;
	private boolean previewDirty = true;
	private int previewWait;

	public FontConfigScreen(@Nullable Screen parent) {
		super(Component.translatable("mcfontcontrol.screen.title"));
		this.parent = parent;
		this.draft = controller.appliedSettings();
	}

	@Override
	protected void init() {
		int listWidth = Math.max(120, width * 2 / 5 - MARGIN);
		int rightX = MARGIN + listWidth + MARGIN;
		int rightWidth = width - rightX - MARGIN;
		int half = (rightWidth - 4) / 2;
		int bottom = height - 28;

		EditBox searchBox = new EditBox(font, MARGIN, TOP, listWidth, 16, Component.translatable("mcfontcontrol.screen.search"));
		searchBox.setHint(Component.translatable("mcfontcontrol.screen.search").withStyle(EditBox.SEARCH_HINT_STYLE));
		searchBox.setValue(search);
		searchBox.setResponder(value -> {
			search = value;
			refreshList();
		});
		addRenderableWidget(searchBox);

		list = new FamilyList(minecraft, listWidth, bottom - 26 - (TOP + 20), TOP + 20);
		list.updateSizeAndPosition(listWidth, bottom - 26 - (TOP + 20), MARGIN, TOP + 20);
		addRenderableWidget(list);

		addRenderableWidget(Checkbox.builder(Component.translatable("mcfontcontrol.screen.show_hidden"), font)
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
			v -> Component.translatable("mcfontcontrol.screen.size", format(v)),
			v -> updateLayout(v, draft.oversample(), draft.shiftX(), draft.shiftY())));
		addRenderableWidget(new ValueSlider(rightX + half + 4, TOP + 24, half, 0, FontSettings.MAX_OVERSAMPLE, 1, draft.oversample(),
			v -> v <= 0
				? Component.translatable("mcfontcontrol.screen.oversample.auto", RuntimeFontPackSource.currentGuiScale())
				: Component.translatable("mcfontcontrol.screen.oversample", format(v)),
			v -> updateLayout(draft.size(), (float) v, draft.shiftX(), draft.shiftY())));
		addRenderableWidget(new ValueSlider(rightX, TOP + 48, half, -FontSettings.MAX_SHIFT, FontSettings.MAX_SHIFT, 0.5, draft.shiftX(),
			v -> Component.translatable("mcfontcontrol.screen.shift_x", format(v)),
			v -> updateLayout(draft.size(), draft.oversample(), (float) v, draft.shiftY())));
		addRenderableWidget(new ValueSlider(rightX + half + 4, TOP + 48, half, -FontSettings.MAX_SHIFT, FontSettings.MAX_SHIFT, 0.5, draft.shiftY(),
			v -> Component.translatable("mcfontcontrol.screen.shift_y", format(v)),
			v -> updateLayout(draft.size(), draft.oversample(), draft.shiftX(), (float) v)));
		addRenderableWidget(Checkbox.builder(Component.translatable("mcfontcontrol.screen.apply_to_uniform"), font)
			.pos(rightX, TOP + 72)
			.selected(draft.applyToUniform())
			.onValueChange((box, value) -> draft = draft.withApplyToUniform(value))
			.build());

		previewX0 = rightX;
		previewY0 = TOP + 96;
		previewX1 = rightX + rightWidth;
		previewY1 = bottom - 16;

		int buttonWidth = (width - 2 * MARGIN - 3 * 4) / 4;
		applyButton = addRenderableWidget(Button.builder(Component.translatable("mcfontcontrol.screen.apply"), b -> apply())
			.bounds(MARGIN, bottom + 4, buttonWidth, 20).build());
		resetButton = addRenderableWidget(Button.builder(Component.translatable("mcfontcontrol.screen.reset"), b -> resetToVanilla())
			.bounds(MARGIN + (buttonWidth + 4), bottom + 4, buttonWidth, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("mcfontcontrol.screen.rescan"), b -> index.scan())
			.bounds(MARGIN + 2 * (buttonWidth + 4), bottom + 4, buttonWidth, 20).build());
		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
			.bounds(MARGIN + 3 * (buttonWidth + 4), bottom + 4, buttonWidth, 20).build());

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
		list.show(families, draft.familyName());
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
		return index.families(true).stream().filter(f -> f.name().equalsIgnoreCase(draft.familyName())).findFirst().orElse(null);
	}

	public void selectFamily(FontFamily family) {
		FaceInfo face = draft.styleName() == null ? family.defaultStyle() : family.findStyle(draft.styleName()).orElse(family.defaultStyle());
		if (!family.name().equalsIgnoreCase(Objects.toString(draft.familyName(), ""))) {
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
		controller.applyWithConfirmation(draft.withEnabled(true), this);
	}

	private void resetToVanilla() {
		closePreview();
		draft = draft.withEnabled(false);
		controller.applyWithConfirmation(controller.appliedSettings().withEnabled(false), this);
	}

	private void updateButtons() {
		if (applyButton == null) {
			return;
		}
		boolean hasFont = draft.fontPath() != null;
		FontSettings applied = controller.appliedSettings();
		applyButton.active = hasFont && !controller.stepAside() && !applied.sameRendering(draft.withEnabled(true));
		resetButton.active = applied.isActive();
		FontFamily family = selectedFamily();
		boolean manyStyles = family != null && family.styles().size() > 1;
		prevStyle.active = manyStyles;
		nextStyle.active = manyStyles;
		styleButton.active = manyStyles;
		styleButton.setMessage(draft.styleName() == null
			? Component.translatable("mcfontcontrol.screen.style.none")
			: Component.translatable("mcfontcontrol.screen.style", draft.styleName()));
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
			previewError = Component.translatable("mcfontcontrol.preview.error");
			McFontControl.LOGGER.warn("[{}] Preview of '{} {}' failed: {}", McFontControl.DISPLAY_NAME, draft.familyName(), draft.styleName(), e.toString());
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

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}

	// ---- Drawing -------------------------------------------------------------------------------

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		graphics.centeredText(font, title, width / 2, 8, 0xFFFFFFFF);
		extractPreview(graphics);
		if (list.children().isEmpty()) {
			// Empty list: explain why, wrapped inside the list area (FD-7, FD-10).
			Component why = search.isBlank() ? status() : Component.translatable("mcfontcontrol.screen.no_match", search);
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
				: draft.fontPath() == null ? Component.translatable("mcfontcontrol.preview.empty")
				: Component.translatable("mcfontcontrol.preview.loading");
			graphics.textWithWordWrap(font, message, previewX0 + 6, previewY0 + 6, previewX1 - previewX0 - 12, 0xFFA0A0A0);
			return;
		}
		Font pf = current.font();
		int x = previewX0 + 6;
		int w = previewX1 - previewX0 - 12;
		int line = Math.max(10, (int) Math.ceil(draft.size()) + 3);
		int y = previewY0 + 5;
		graphics.enableScissor(previewX0 + 1, previewY0 + 1, previewX1 - 1, previewY1 - 1);
		graphics.text(pf, Component.translatable("mcfontcontrol.preview.pangram"), x, y, 0xFFFFFFFF, true);
		y += line;
		graphics.text(pf, Component.translatable("mcfontcontrol.preview.digits"), x, y, 0xFFD0D0D0, true);
		y += line + 3;
		// Chat line
		graphics.fill(x - 2, y - 2, x + w, y + line - 2, 0x80000000);
		graphics.text(pf, Component.translatable("mcfontcontrol.preview.chat"), x, y, 0xFFFFFFFF, true);
		y += line + 4;
		// Tooltip and sign samples side by side
		Component tooltip = Component.translatable("mcfontcontrol.preview.tooltip");
		int tw = Math.min(w / 2 - 4, pf.width(tooltip) + 8);
		graphics.fill(x - 2, y - 3, x - 2 + tw, y + line - 1, 0xF0100010);
		graphics.outline(x - 2, y - 3, tw, line + 2, 0xFF5000FF);
		graphics.text(pf, tooltip, x + 2, y, 0xFFFFFF55, true);
		Component sign = Component.translatable("mcfontcontrol.preview.sign");
		int sx = x + tw + 6;
		int sw = Math.min(x + w - sx, pf.width(sign) + 12);
		graphics.fill(sx, y - 3, sx + sw, y + line - 1, 0xFFA2824E);
		graphics.text(pf, sign, sx + 6, y, 0xFF000000, false);
		graphics.disableScissor();
	}

	private Component status() {
		if (controller.conflict().isPresent()) {
			return Component.translatable("mcfontcontrol.status.conflict", controller.conflict().get().name());
		}
		if (index.os() == OsFamily.OTHER) {
			return Component.translatable("mcfontcontrol.status.unsupported_os");
		}
		if (index.state() == SystemFontIndex.State.SCANNING) {
			return Component.translatable("mcfontcontrol.status.scanning");
		}
		if (index.families(true).isEmpty() && io.github.jekitchen.mcfontcontrol.discovery.FontDirectories.flatpakWithoutHostFonts()) {
			return Component.translatable("mcfontcontrol.status.flatpak");
		}
		if (index.state() == SystemFontIndex.State.FAILED || index.families(true).isEmpty()) {
			return Component.translatable("mcfontcontrol.status.no_fonts");
		}
		FontSettings applied = controller.appliedSettings();
		if (applied.isActive() && !PackContents.providerLoaded() && Minecraft.getInstance().gui.overlay() == null) {
			return Component.translatable("mcfontcontrol.status.load_failed", applied.familyName());
		}
		return applied.isActive()
			? Component.translatable("mcfontcontrol.status.current", applied.familyName() + " " + applied.styleName())
			: Component.translatable("mcfontcontrol.status.vanilla");
	}

	// ---- Family list widget ----------------------------------------------------------------------

	private final class FamilyList extends ObjectSelectionList<FamilyList.Entry> {
		FamilyList(Minecraft minecraft, int width, int height, int y) {
			super(minecraft, width, height, y, 14);
		}

		void show(List<FontFamily> families, @Nullable String selectedName) {
			Entry selected = null;
			List<Entry> entries = families.stream().map(Entry::new).toList();
			for (Entry e : entries) {
				if (e.family.name().equalsIgnoreCase(Objects.toString(selectedName, ""))) {
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
				graphics.text(font, family.name(), getContentX() + 2, y, 0xFFFFFFFF, false);
				Visibility v = family.visibility();
				if (v != Visibility.VISIBLE) {
					Component tag = Component.translatable("mcfontcontrol.hidden." + v.name().toLowerCase(Locale.ROOT));
					graphics.text(font, tag, getContentX() + getContentWidth() - font.width(tag) - 2, y, 0xFF909090, false);
				}
			}

			@Override
			public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
				FamilyList.this.setSelected(this);
				selectFamily(family);
				return true;
			}

			@Override
			public Component getNarration() {
				return Component.translatable("narrator.select", family.name());
			}
		}
	}
}
