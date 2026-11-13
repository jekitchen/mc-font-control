package io.github.jekitchen.fontsys.ui;

import com.mojang.blaze3d.Blaze3D;
import io.github.jekitchen.fontsys.Fontsys;
import io.github.jekitchen.fontsys.apply.FontController;
import io.github.jekitchen.fontsys.config.FontSettings;
import io.github.jekitchen.fontsys.discovery.FaceInfo;
import io.github.jekitchen.fontsys.config.RecentFont;
import io.github.jekitchen.fontsys.discovery.FontFamily;
import io.github.jekitchen.fontsys.discovery.FontFileWalker;
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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.navigation.ScreenPosition;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * The config screen (PRD 5.4): searchable family list, style picker, sliders, show-hidden
 * toggle, live preview with a size strip, and the buttons along the bottom: the list's
 * Fonts folder and Rescan fonts on the left, Reset to vanilla on its own in the middle, and
 * Apply and Done on the right (UI-7). Font files dropped on the screen are copied into the
 * instance's fonts folder (UI-16).
 */
public final class FontConfigScreen extends Screen {
	private static final int MARGIN = 8;
	private static final int TOP = 22;
	private static final int PREVIEW_DEBOUNCE_TICKS = 3;
	/** Space between buttons in a group, and between the groups along the bottom. */
	private static final int BUTTON_GAP = 4;
	private static final int GROUP_GAP = 14;
	/** Width and height of the search box's clear button, and the search box's height. */
	private static final int CLEAR_SIZE = 16;
	/** Recently applied fonts listed first while the search box is empty (UI-17). */
	private static final int RECENT_SHOWN = 5;
	/**
	 * The green of "sys" in the logo (D48). The logo fades from #78ECBA to #34C496; Minecraft
	 * gives each character one color, so the title uses the midpoint.
	 */
	private static final int TITLE_GREEN = 0x56D9A8;

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
	private EditBox searchBox;
	private Button clearSearch;
	private Button styleButton;
	private Button prevStyle;
	private Button nextStyle;
	private Button applyButton;
	private Button resetButton;
	private ValueSlider sizeSlider;
	private ValueSlider oversampleSlider;
	/** Widgets given a tooltip in {@link #init()}; read by the game tests. */
	private final List<AbstractWidget> tooltipped = new ArrayList<>();
	/** Where the size strip drew each size last frame, for clicks. */
	private final List<StripHit> stripHits = new ArrayList<>();
	private int previewX0;
	private int previewY0;
	private int previewX1;
	private int previewY1;

	private @Nullable PreviewFont preview;
	private @Nullable Component previewError;
	private boolean previewDirty = true;
	private int previewWait;

	public FontConfigScreen(@Nullable Screen parent) {
		// "Font" in white and "sys" in the logo's green (D48). One component, so narration
		// still reads "Fontsys"; the color is only drawn, never spoken.
		super(Component.translatable("fontsys.screen.title", Component.translatable("fontsys.screen.title.font"),
			Component.translatable("fontsys.screen.title.sys").withColor(TITLE_GREEN)));
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
		tooltipped.clear();

		// The search box, and a clear button at its right end while it has text (UI-3).
		searchBox = new EditBox(font, MARGIN, TOP, listWidth - CLEAR_SIZE - 2, CLEAR_SIZE, Component.translatable("fontsys.screen.search"));
		searchBox.setHint(Component.translatable("fontsys.screen.search").withStyle(EditBox.SEARCH_HINT_STYLE));
		searchBox.setValue(search);
		clearSearch = Button.builder(Component.literal("×"), b -> {
			searchBox.setValue("");
			setFocused(searchBox);
		}).bounds(MARGIN + listWidth - CLEAR_SIZE, TOP, CLEAR_SIZE, CLEAR_SIZE).build();
		clearSearch.setTooltip(Tooltip.create(Component.translatable("fontsys.screen.search.clear")));
		clearSearch.visible = !search.isEmpty();
		searchBox.setResponder(value -> {
			search = value;
			clearSearch.visible = !value.isEmpty();
			refreshList();
		});
		addRenderableWidget(searchBox);
		addRenderableWidget(clearSearch);

		list = new FamilyList(minecraft, listWidth, bottom - 26 - (TOP + 20), TOP + 20);
		list.updateSizeAndPosition(listWidth, bottom - 26 - (TOP + 20), MARGIN, TOP + 20);
		addRenderableWidget(list);

		addRenderableWidget(tip(Checkbox.builder(Component.translatable("fontsys.screen.show_hidden"), font)
			.pos(MARGIN, bottom - 22)
			.selected(controller.config().showHiddenFonts)
			.onValueChange((box, value) -> {
				controller.setShowHiddenFonts(value);
				refreshList();
			})
			.build(), "fontsys.screen.show_hidden.tooltip"));

		prevStyle = addRenderableWidget(Button.builder(Component.literal("<"), b -> cycleStyle(-1)).bounds(rightX, TOP, 20, 20).build());
		styleButton = addRenderableWidget(Button.builder(Component.empty(), b -> cycleStyle(1)).bounds(rightX + 22, TOP, rightWidth - 44, 20).build());
		nextStyle = addRenderableWidget(Button.builder(Component.literal(">"), b -> cycleStyle(1)).bounds(rightX + rightWidth - 20, TOP, 20, 20).build());

		sizeSlider = addRenderableWidget(tip(new ValueSlider(rightX, TOP + 24, half, FontSettings.MIN_SIZE, FontSettings.MAX_SIZE, 0.5, draft.size(),
			v -> Component.translatable("fontsys.screen.size", format(v)),
			v -> updateLayout(v, draft.oversample(), draft.shiftX(), draft.shiftY())), "fontsys.screen.size.tooltip"));
		oversampleSlider = addRenderableWidget(tip(new ValueSlider(rightX + half + 4, TOP + 24, half, 0, FontSettings.MAX_OVERSAMPLE, 1, draft.oversample(),
			v -> draft.crispEdges()
				? Component.translatable("fontsys.screen.oversample.crisp", RuntimeFontPackSource.currentGuiScale())
				: v <= 0
				? Component.translatable("fontsys.screen.oversample.auto", RuntimeFontPackSource.currentGuiScale())
				: Component.translatable("fontsys.screen.oversample", format(v)),
			v -> updateLayout(draft.size(), (float) v, draft.shiftX(), draft.shiftY())), "fontsys.screen.oversample.tooltip"));
		addRenderableWidget(tip(new ValueSlider(rightX, TOP + 48, half, -FontSettings.MAX_SHIFT, FontSettings.MAX_SHIFT, 0.5, draft.shiftX(),
			v -> Component.translatable("fontsys.screen.shift_x", format(v)),
			v -> updateLayout(draft.size(), draft.oversample(), (float) v, draft.shiftY())), "fontsys.screen.shift_x.tooltip"));
		addRenderableWidget(tip(new ValueSlider(rightX + half + 4, TOP + 48, half, -FontSettings.MAX_SHIFT, FontSettings.MAX_SHIFT, 0.5, draft.shiftY(),
			v -> Component.translatable("fontsys.screen.shift_y", format(v)),
			v -> updateLayout(draft.size(), draft.oversample(), draft.shiftX(), (float) v)), "fontsys.screen.shift_y.tooltip"));
		Checkbox uniform = addRenderableWidget(tip(Checkbox.builder(Component.translatable("fontsys.screen.apply_to_uniform"), font)
			.pos(rightX, TOP + 72)
			.selected(draft.applyToUniform())
			.onValueChange((box, value) -> {
				draft = draft.withApplyToUniform(value);
				updateButtons();
			})
			.build(), "fontsys.screen.apply_to_uniform.tooltip"));
		Checkbox crisp = addRenderableWidget(tip(Checkbox.builder(Component.translatable("fontsys.screen.crisp"), font)
			.selected(draft.crispEdges())
			.onValueChange((box, value) -> setCrispEdges(value))
			.build(), "fontsys.screen.crisp.tooltip"));
		// Under the sliders (UI-9): beside the Unicode checkbox when both fit, otherwise below it.
		int crispX = rightX + rightWidth - crisp.getWidth();
		boolean sameRow = crispX >= uniform.getRight() + 8;
		crisp.setPosition(sameRow ? crispX : rightX, sameRow ? TOP + 72 : TOP + 92);
		updateOversampleSlider();

		previewX0 = rightX;
		previewY0 = sameRow ? TOP + 96 : TOP + 116;
		previewX1 = rightX + rightWidth;
		previewY1 = bottom - 16;

		// [Fonts folder] [Rescan fonts]   [Reset to vanilla]   [Apply] [Done] (UI-7)
		Component[] labels = {Component.translatable("fontsys.screen.open_folder"), Component.translatable("fontsys.screen.rescan"),
			Component.translatable("fontsys.screen.reset"), Component.translatable("fontsys.screen.apply"), CommonComponents.GUI_DONE};
		int[] widths = buttonWidths(labels, width - 2 * MARGIN - 2 * BUTTON_GAP - 2 * GROUP_GAP);
		int x = MARGIN;
		int y = bottom + 4;
		addRenderableWidget(tip(Button.builder(labels[0], b -> openInstanceFolder())
			.bounds(x, y, widths[0], 20).build(), "fontsys.screen.open_folder.tooltip"));
		x += widths[0] + BUTTON_GAP;
		addRenderableWidget(tip(Button.builder(labels[1], b -> index.scan())
			.bounds(x, y, widths[1], 20).build(), "fontsys.screen.rescan.tooltip"));
		x += widths[1] + GROUP_GAP;
		resetButton = addRenderableWidget(tip(Button.builder(labels[2], b -> resetToVanilla())
			.bounds(x, y, widths[2], 20).build(), "fontsys.screen.reset.tooltip"));
		x += widths[2] + GROUP_GAP;
		applyButton = addRenderableWidget(Button.builder(labels[3], b -> apply())
			.bounds(x, y, widths[3], 20).build());
		x += widths[3] + BUTTON_GAP;
		addRenderableWidget(Button.builder(labels[4], b -> onClose())
			.bounds(x, y, widths[4], 20).build());

		seenGeneration = -1;
		refreshList();
		updateButtons();
		setInitialFocus(searchBox);
		previewDirty = true;
	}

	/**
	 * Splits {@code total} between buttons: each gets its label's width plus padding, and the
	 * rest is shared equally, so long labels (Reset to vanilla) aren't cut off while short ones
	 * (Apply, Done) shrink. Equal widths if the labels don't all fit.
	 */
	private int[] buttonWidths(Component[] labels, int total) {
		int[] widths = new int[labels.length];
		int needed = 0;
		for (int i = 0; i < labels.length; i++) {
			widths[i] = font.width(labels[i]) + 12;
			needed += widths[i];
		}
		if (needed > total) {
			Arrays.fill(widths, total / labels.length);
			return widths;
		}
		int extra = total - needed;
		for (int i = 0; i < labels.length; i++) {
			widths[i] += extra / labels.length + (i < extra % labels.length ? 1 : 0);
		}
		return widths;
	}

	/**
	 * Gives a widget its tooltip after it is built. 26.3's {@code Checkbox.Builder.build()}
	 * keeps the builder's tooltip only when the label wraps past two lines, so set on the
	 * builder it never showed for our one-line checkboxes.
	 */
	private <T extends AbstractWidget> T tip(T widget, String key) {
		widget.setTooltip(Tooltip.create(Component.translatable(key)));
		tooltipped.add(widget);
		return widget;
	}

	/** The widgets that have a tooltip; read by the game tests. */
	public List<AbstractWidget> tooltipWidgets() {
		return List.copyOf(tooltipped);
	}

	/**
	 * The cursor starts in the search box, so players can type a font name straight away (UI-3).
	 * When a screen opens after keyboard input (F7, or Enter on a button), 26.3 runs this after
	 * {@link #init()}, and the default moves focus one Tab step on, out of the search box; so it
	 * is overridden, as vanilla's own screens do. {@link #init()} also focuses the box, for the
	 * rebuild after a resize, which doesn't call this.
	 */
	@Override
	protected void setInitialFocus() {
		setInitialFocus(searchBox);
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
		List<FontFamily> recent = needle.isEmpty() ? recentFirst(families) : List.of();
		if (!recent.isEmpty()) {
			List<FontFamily> ordered = new ArrayList<>(recent);
			families.stream().filter(f -> !recent.contains(f)).forEach(ordered::add);
			families = ordered;
		}
		list.show(families, recent, draft.familyName(), draft.fontPath());
		updateButtons();
	}

	/** The listed families applied most recently, newest first (UI-17). */
	private List<FontFamily> recentFirst(List<FontFamily> families) {
		List<FontFamily> result = new ArrayList<>();
		for (RecentFont recent : controller.recentFonts()) {
			families.stream()
				.filter(f -> f.holds(recent.familyName(), recent.fontPath()) && !result.contains(f))
				.findFirst()
				.ifPresent(result::add);
			if (result.size() == RECENT_SHOWN) {
				break;
			}
		}
		return result;
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

	/**
	 * Font files dropped on the screen are copied into the instance's fonts folder and the list
	 * rescans (UI-16). Files already there are left alone; other files are ignored. A toast says
	 * what happened.
	 */
	@Override
	public void onFilesDrop(List<Path> paths) {
		Path dir = InstanceFonts.dir().orElse(null);
		if (dir == null) {
			return;
		}
		int copied = 0;
		int skipped = 0;
		Path first = null;
		try {
			InstanceFonts.createDir(InstanceFonts.gameDir().orElseThrow());
			for (Path path : paths) {
				Path target = path.getFileName() == null ? null : dir.resolve(path.getFileName().toString());
				if (target == null || !FontFileWalker.isFontFile(path) || !Files.isRegularFile(path) || Files.exists(target)) {
					skipped++;
					continue;
				}
				Files.copy(path, target);
				copied++;
				if (first == null) {
					first = target.toRealPath();
				}
			}
		} catch (IOException | RuntimeException e) {
			Fontsys.LOGGER.warn("[{}] Could not copy dropped fonts to {}: {}", Fontsys.DISPLAY_NAME, dir.getFileName(), e.toString());
			skipped = paths.size() - copied;
		}
		if (first != null) {
			selectWhenScanned(first, true);
		}
		Component title = Component.translatable(copied > 0 ? "fontsys.drop.title" : "fontsys.drop.none_title");
		Component message = copied == 0 ? Component.translatable("fontsys.drop.none", skipped)
			: skipped == 0 ? Component.translatable("fontsys.drop.added", copied)
			: Component.translatable("fontsys.drop.added_skipped", copied, skipped);
		SystemToast.addOrUpdate(minecraft.gui.toastManager(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION, title, message);
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

	/**
	 * Selects the family of a dropped font file once a scan has listed it (UI-16). A scan that
	 * was already running when the file was copied may have missed it, so if the first scan
	 * doesn't list it, one more is run. Nothing happens if the player has left the screen, or if
	 * the font is hidden (no Latin letters, say) and hidden fonts aren't shown.
	 */
	private void selectWhenScanned(Path file, boolean retry) {
		index.scan().thenRun(() -> minecraft.execute(() -> {
			if (minecraft.gui.screen() != this) {
				return;
			}
			FontFamily family = familyOf(file, index.families(true));
			if (family == null) {
				if (retry) {
					selectWhenScanned(file, false);
				}
				return;
			}
			if (!index.families(controller.config().showHiddenFonts).contains(family)) {
				return;
			}
			String needle = search.toLowerCase(Locale.ROOT).strip();
			if (!needle.isEmpty() && !family.name().toLowerCase(Locale.ROOT).contains(needle)) {
				searchBox.setValue(""); // so the selection is in view
			}
			selectFamily(family);
			refreshList();
		}));
	}

	/** The family with a face in {@code file}, if the index lists one. */
	static @Nullable FontFamily familyOf(Path file, List<FontFamily> families) {
		for (FontFamily family : families) {
			for (FaceInfo face : family.styles()) {
				if (Path.of(face.path()).equals(file)) {
					return family;
				}
			}
		}
		return null;
	}

	/** A click on a size in the size strip selects that size (UI-15). */
	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}
		StripHit hit = stripHitAt(event.x(), event.y());
		if (hit == null) {
			return false;
		}
		if (hit.size() != draft.size()) {
			sizeSlider.setCurrent(hit.size());
		}
		return true;
	}

	private @Nullable StripHit stripHitAt(double mouseX, double mouseY) {
		for (StripHit hit : stripHits) {
			if (mouseX >= hit.x0() && mouseX < hit.x1() && mouseY >= hit.y0() && mouseY < hit.y1()) {
				return hit;
			}
		}
		return null;
	}

	/** The sizes the size strip shows now, ascending; empty before the preview is ready. */
	public List<Float> stripSizes() {
		return preview == null ? List.of() : preview.stripSizes();
	}

	/** The search box's text; read by the game tests. */
	public String searchText() {
		return search;
	}

	public float draftSize() {
		return draft.size();
	}

	/** The middle of a size in the size strip as last drawn, in GUI coordinates; for the game tests. */
	public @Nullable ScreenPosition stripCenter(float size) {
		return stripHits.stream().filter(h -> h.size() == size).findFirst()
			.map(h -> new ScreenPosition((h.x0() + h.x1()) / 2, (h.y0() + h.y1()) / 2)).orElse(null);
	}

	/** The families in the list, top to bottom, and whether each is tagged Recent; for the game tests. */
	public List<Map.Entry<FontFamily, Boolean>> listedFamilies() {
		return list.children().stream().map(e -> Map.entry(e.family, e.recent)).toList();
	}

	private record StripHit(float size, int x0, int y0, int x1, int y1) {
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
		extractPreview(graphics, mouseX, mouseY);
		if (list.children().isEmpty()) {
			// Empty list: explain why, wrapped inside the list area (FD-7, FD-10).
			Component why = search.isBlank() ? status() : Component.translatable("fontsys.screen.no_match", search);
			graphics.textWithWordWrap(font, why, list.getX() + 6, list.getY() + 6, list.getWidth() - 12, 0xFFA0A0A0);
		} else {
			graphics.text(font, status(), MARGIN + list.getWidth() + MARGIN, height - 40, 0xFFA0A0A0, false);
		}
	}

	private void extractPreview(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		stripHits.clear();
		graphics.fill(previewX0, previewY0, previewX1, previewY1, 0xA0000000);
		graphics.outline(previewX0, previewY0, previewX1 - previewX0, previewY1 - previewY0, 0xFF555555);
		int x = previewX0 + 6;
		int w = previewX1 - previewX0 - 12;
		int top = previewY0 + 5;
		if (controller.conflict().isPresent()) {
			// AP-12: say plainly why nothing happens, above everything else.
			Component why = Component.translatable("fontsys.conflict.banner", controller.conflict().get().name());
			int h = font.wordWrapHeight(why, w - 8) + 8;
			graphics.fill(previewX0 + 1, previewY0 + 1, previewX1 - 1, previewY0 + 1 + h, 0xD0702020);
			graphics.textWithWordWrap(font, why, x + 2, previewY0 + 5, w - 8, 0xFFFFFFFF);
			top = previewY0 + h + 6;
		}
		PreviewFont current = preview;
		if (current == null || minecraft.gui.overlay() != null) {
			Component message = previewError != null ? previewError
				: draft.fontPath() == null ? Component.translatable("fontsys.preview.empty")
				: Component.translatable("fontsys.preview.loading");
			graphics.textWithWordWrap(font, message, x, top + 1, w, 0xFFA0A0A0);
			return;
		}
		Font pf = current.font();
		int line = Math.max(10, (int) Math.ceil(draft.size()) + 3);
		int y = top;
		graphics.enableScissor(previewX0 + 1, previewY0 + 1, previewX1 - 1, previewY1 - 1);
		Component hint = pixelFontHint(current);
		if (hint != null) {
			// First, so it stays visible however little room the samples leave.
			graphics.text(font, hint, x, y, 0xFFFFE080, false);
			y += font.lineHeight + 3;
		}
		y = extractSizeStrip(graphics, current, x, y, w, mouseX, mouseY);
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
	 * The size strip (UI-15): a short sample at the next size down, the current size and the next
	 * size up, each after its size in the vanilla font. Clicking one selects that size.
	 *
	 * @return the y below the strip
	 */
	private int extractSizeStrip(GuiGraphicsExtractor graphics, PreviewFont current, int x, int y, int w, int mouseX, int mouseY) {
		List<Float> sizes = current.stripSizes();
		if (sizes.size() < 2) {
			return y;
		}
		Component sample = Component.translatable("fontsys.preview.size_sample");
		if (stripWidth(current, sizes, sample) > w) {
			sample = Component.translatable("fontsys.preview.size_sample.short");
		}
		// Vanilla places every glyph from a baseline at a fixed distance below the text's y, at any
		// size, so drawing all sizes at one y lines them up. Start low enough for the largest
		// size's ascenders (about 3/4 of the size above the baseline, which sits 7 below y).
		float largest = sizes.get(sizes.size() - 1);
		int textY = y + 2 + Math.max(0, Math.round(largest * 0.75F) - 7);
		int rowHeight = textY - y + 7 + Math.round(largest * 0.25F) + 4;
		int sx = x;
		for (float size : sizes) {
			Component label = Component.literal(format(size));
			boolean selected = size == draft.size();
			int sampleWidth = current.fontAt(size).width(sample);
			int segment = font.width(label) + 3 + sampleWidth;
			StripHit hit = new StripHit(size, sx - 2, y - 2, sx + segment + 2, y + rowHeight - 1);
			stripHits.add(hit);
			boolean hovered = !selected && stripHitAt(mouseX, mouseY) == hit;
			if (selected || hovered) {
				graphics.outline(hit.x0(), hit.y0(), hit.x1() - hit.x0(), hit.y1() - hit.y0(), selected ? 0xFF808080 : 0xFF505050);
			}
			graphics.text(font, label, sx, textY, selected ? 0xFFFFFFFF : 0xFF909090, false);
			graphics.text(current.fontAt(size), sample, sx + font.width(label) + 3, textY, 0xFFFFFFFF, true);
			if (hovered) {
				graphics.setTooltipForNextFrame(font, Component.translatable("fontsys.preview.size_strip.tooltip", format(size)), mouseX, mouseY);
			}
			sx += segment + 12;
		}
		return y + rowHeight + 3;
	}

	private int stripWidth(PreviewFont current, List<Float> sizes, Component sample) {
		int total = 0;
		for (float size : sizes) {
			total += font.width(format(size)) + 3 + current.fontAt(size).width(sample) + 12;
		}
		return total - 12;
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
			return Component.translatable("fontsys.status.off"); // the banner explains (AP-12)
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

		void show(List<FontFamily> families, List<FontFamily> recent, @Nullable String selectedName, @Nullable String selectedPath) {
			Entry selected = null;
			List<Entry> entries = families.stream().map(f -> new Entry(f, recent.contains(f))).toList();
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
			final boolean recent;

			Entry(FontFamily family, boolean recent) {
				this.family = family;
				this.recent = recent;
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
				if (recent) {
					Component tag = Component.translatable("fontsys.screen.recent_tag");
					right -= font.width(tag);
					graphics.text(font, tag, right, y, 0xFFFFE080, false);
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
