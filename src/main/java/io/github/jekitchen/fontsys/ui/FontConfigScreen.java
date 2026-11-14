package io.github.jekitchen.fontsys.ui;

import com.mojang.blaze3d.Blaze3D;
import io.github.jekitchen.fontsys.Fontsys;
import io.github.jekitchen.fontsys.apply.FontController;
import io.github.jekitchen.fontsys.config.FontSettings;
import io.github.jekitchen.fontsys.config.FontTuning;
import io.github.jekitchen.fontsys.discovery.FaceInfo;
import io.github.jekitchen.fontsys.config.RecentFont;
import io.github.jekitchen.fontsys.discovery.FontFamily;
import io.github.jekitchen.fontsys.discovery.FontFileWalker;
import io.github.jekitchen.fontsys.discovery.InstanceFonts;
import io.github.jekitchen.fontsys.discovery.OsFamily;
import io.github.jekitchen.fontsys.discovery.SystemFontIndex;
import io.github.jekitchen.fontsys.discovery.Visibility;
import io.github.jekitchen.fontsys.pack.PackContents;
import io.github.jekitchen.fontsys.pack.RuntimeFontPackSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
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
import net.minecraft.network.chat.MutableComponent;
import org.jspecify.annotations.Nullable;

/**
 * The config screen (PRD 5.4): searchable family list, style picker, sliders, show-hidden
 * toggle, live preview with a size strip, and the buttons along the bottom: the list's
 * Fonts folder and Rescan fonts on the left, Reset to vanilla on its own in the middle, and
 * Apply and Done on the right (UI-7). Font files dropped on the screen are copied into the
 * instance's fonts folder (UI-16). Advanced... opens {@link AdvancedFontScreen}, which edits
 * the same draft (UI-19, D52).
 */
public final class FontConfigScreen extends Screen {
	private static final int MARGIN = 8;
	private static final int TOP = 22;
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
	private Checkbox crispBox;
	/**
	 * Tuning the player left behind on each family they browsed away from in this visit,
	 * unapplied, so going back to a family brings it back (D55). Keyed by {@link #familyKey}.
	 */
	private final Map<String, FontTuning> browsed = new HashMap<>();
	private Button advancedButton;
	/** Widgets given a tooltip in {@link #init()}; read by the game tests. */
	private final List<AbstractWidget> tooltipped = new ArrayList<>();
	private int previewX0;
	private int previewY0;
	private int previewX1;
	private int previewY1;

	/** Each family's name in its own font in the list (D49). */
	private final FamilyNameFonts nameFonts = new FamilyNameFonts();
	/** The live preview; the Advanced screen shows the same one (D52). */
	private final PreviewPanel preview = new PreviewPanel();
	/** Set while the Advanced screen is open over this one: keeps the preview, and focus returns to its button. */
	private boolean advancedOpen;

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
		// Shift X, Shift Y and Replace Unicode font live on the Advanced screen (UI-19, D52).
		advancedButton = addRenderableWidget(tip(Button.builder(Component.translatable("fontsys.screen.advanced"), b -> openAdvanced())
			.bounds(rightX, TOP + 48, half, 20).build(), "fontsys.screen.advanced.tooltip"));
		Checkbox crisp = addRenderableWidget(crispCheckbox());
		// Under the sliders (UI-9): beside the Advanced button when both fit, otherwise below it.
		int crispX = rightX + rightWidth - crisp.getWidth();
		boolean sameRow = crispX >= advancedButton.getRight() + 8;
		crisp.setPosition(sameRow ? crispX : rightX, sameRow ? TOP + 48 + (20 - crisp.getHeight()) / 2 : TOP + 72);
		crispBox = crisp;
		updateOversampleSlider();

		previewX0 = rightX;
		previewY0 = sameRow ? TOP + 72 : TOP + 92;
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
		preview.markDirty();
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
		if (advancedOpen) {
			// Back from the Advanced screen: from the keyboard, focus returns to its button.
			advancedOpen = false;
			if (minecraft.getLastInputType().isKeyboard()) {
				setInitialFocus(advancedButton);
				return;
			}
		}
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
		boolean switching = !family.holds(draft.familyName(), draft.fontPath());
		if (switching) {
			face = family.defaultStyle();
			if (draft.familyName() != null) {
				browsed.put(familyKey(draft.familyName(), draft.fontPath()), FontTuning.of(draft));
			}
		}
		selectFace(face);
		if (switching) {
			// Each font keeps its own tuning (D55): what the player left on it in this visit, else
			// what it was last kept with, else the defaults for a font never kept.
			FontTuning tuning = browsed.get(familyKey(face.family(), face.path()));
			if (tuning == null) {
				tuning = controller.rememberedTuning(face.family(), face.path());
			}
			applyTuning(tuning == null ? FontTuning.DEFAULTS : tuning);
		}
	}

	/** Families with the same name but one an instance font count separately (FD-14), as in the recent list. */
	private static String familyKey(String familyName, @Nullable String fontPath) {
		return familyName.toLowerCase(Locale.ROOT) + (InstanceFonts.contains(fontPath) ? "|instance" : "|system");
	}

	/** Puts a font's tuning into the draft and the controls that show it (D55). */
	private void applyTuning(FontTuning tuning) {
		draft = tuning.applyTo(draft);
		// The sliders write the draft back as they move; it already holds these values.
		sizeSlider.setCurrent(draft.size());
		oversampleSlider.setCurrent(draft.oversample());
		if (crispBox.selected() != draft.crispEdges()) {
			// A Checkbox can't be set from code in 26.3, so it is replaced in place.
			Checkbox replacement = crispCheckbox();
			replacement.setPosition(crispBox.getX(), crispBox.getY());
			tooltipped.remove(crispBox);
			removeWidget(crispBox);
			crispBox = addRenderableWidget(replacement);
		}
		updateOversampleSlider();
		preview.markDirty();
		updateButtons();
	}

	private void selectFace(FaceInfo face) {
		draft = draft.withFont(face.path(), face.faceIndex(), face.family(), face.style());
		preview.markDirty();
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

	private Checkbox crispCheckbox() {
		return tip(Checkbox.builder(Component.translatable("fontsys.screen.crisp"), font)
			.selected(draft.crispEdges())
			.onValueChange((box, value) -> setCrispEdges(value))
			.build(), "fontsys.screen.crisp.tooltip");
	}

	/** Crisp mode (AP-10, D34): sharpness follows the GUI scale, so the slider steps aside. */
	private void setCrispEdges(boolean value) {
		draft = draft.withCrispEdges(value);
		updateOversampleSlider();
		preview.markDirty();
		updateButtons();
	}

	private void updateOversampleSlider() {
		oversampleSlider.active = !draft.crispEdges();
		oversampleSlider.refreshMessage();
	}

	private void updateLayout(double size, float oversample, float shiftX, float shiftY) {
		draft = draft.withLayout((float) size, oversample, shiftX, shiftY).sanitized();
		preview.markDirty();
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
		Float size = preview.stripSizeAt(event.x(), event.y());
		if (size == null) {
			return false;
		}
		if (size != draft.size()) {
			sizeSlider.setCurrent(size);
		}
		return true;
	}

	/** The sizes the size strip shows now, ascending; empty before the preview is ready. */
	public List<Float> stripSizes() {
		return preview.stripSizes();
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
		return preview.stripCenter(size);
	}

	/** The families in the list, top to bottom, and whether each is tagged Recent; for the game tests. */
	public List<Map.Entry<FontFamily, Boolean>> listedFamilies() {
		return list.children().stream().map(e -> Map.entry(e.family, e.recent)).toList();
	}

	/** The list's name fonts (D49); for the game tests. */
	public FamilyNameFonts nameFonts() {
		return nameFonts;
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
		updateAdvancedButton();
		FontFamily family = selectedFamily();
		boolean manyStyles = family != null && family.styles().size() > 1;
		prevStyle.active = manyStyles;
		nextStyle.active = manyStyles;
		styleButton.active = manyStyles;
		styleButton.setMessage(draft.styleName() == null
			? Component.translatable("fontsys.screen.style.none")
			: Component.translatable("fontsys.screen.style", draft.styleName()));
	}

	// ---- Advanced screen (UI-19, D52) ------------------------------------------------------

	private void openAdvanced() {
		advancedOpen = true;
		minecraft.gui.setScreen(new AdvancedFontScreen(this));
	}

	/**
	 * Frees the preview and the list's name fonts (D14, D49). Called by {@link #removed()}, and by
	 * the Advanced screen when the player leaves it some other way than back to this screen, since
	 * this screen kept them while the Advanced screen was open.
	 */
	void releaseFonts() {
		advancedOpen = false;
		closePreview();
		nameFonts.clear();
	}

	/** The settings Apply will apply; the Advanced screen edits this same draft. Also read by the game tests. */
	public FontSettings draft() {
		return draft;
	}

	/** A change on the Advanced screen: kept here, unapplied, until Apply (or Done's prompt, UI-13). */
	void setDraft(FontSettings value) {
		draft = value.sanitized();
		preview.markDirty();
		updateButtons();
	}

	PreviewPanel preview() {
		return preview;
	}

	/** "Advanced..." gains a count of the settings there that aren't at their defaults, named in its tooltip. */
	private void updateAdvancedButton() {
		List<String> changed = AdvancedSettings.changedLabelKeys(draft);
		if (changed.isEmpty()) {
			advancedButton.setMessage(Component.translatable("fontsys.screen.advanced"));
			advancedButton.setTooltip(Tooltip.create(Component.translatable("fontsys.screen.advanced.tooltip")));
			return;
		}
		MutableComponent names = Component.empty();
		for (int i = 0; i < changed.size(); i++) {
			if (i > 0) {
				names.append(Component.translatable("fontsys.screen.advanced.separator"));
			}
			names.append(Component.translatable(changed.get(i)));
		}
		advancedButton.setMessage(Component.translatable("fontsys.screen.advanced.changed", changed.size()));
		advancedButton.setTooltip(Tooltip.create(Component.translatable("fontsys.screen.advanced.tooltip.changed", names)));
	}

	/** The Advanced button's label; read by the game tests. */
	public Component advancedLabel() {
		return advancedButton.getMessage();
	}

	// ---- Preview -----------------------------------------------------------------------------

	@Override
	public void tick() {
		if (index.generation() != seenGeneration) {
			refreshList();
		}
		preview.tick(draft);
	}

	private void closePreview() {
		preview.close();
	}

	@Override
	public void removed() {
		if (!advancedOpen) {
			releaseFonts();
		}
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
		preview.extract(graphics, font, draft, PreviewPanel.Layout.FULL, previewX0, previewY0, previewX1, previewY1, mouseX, mouseY);
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

		/** Starts each frame's names: their fonts load only for the rows drawn below (D49). */
		@Override
		public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
			nameFonts.beginFrame(RuntimeFontPackSource.currentGuiScale(), minecraft.gui.overlay() == null);
			super.extractWidgetRenderState(graphics, mouseX, mouseY, a);
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
				// In the family's own font once it's ready (D49). Long names (or a wide font) are cut
				// short rather than run under the tags.
				nameFonts.drawName(graphics, font, family, getContentX() + 2, y, right - (getContentX() + 2), 0xFFFFFFFF);
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
