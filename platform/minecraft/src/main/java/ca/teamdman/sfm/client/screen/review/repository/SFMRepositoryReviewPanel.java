package ca.teamdman.sfm.client.screen.review.repository;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.presentation.SFMItemIconRenderer;
import ca.teamdman.sfm.client.review.repository.SFMRepositoryReviewRepository;
import ca.teamdman.sfm.client.screen.SFMFontUtils;
import ca.teamdman.sfm.client.screen.file_explorer.SFMFileExplorerEntry;
import ca.teamdman.sfm.client.screen.file_explorer.SFMFilePresentationRegistry;
import ca.teamdman.sfm.client.screen.review.comment.SFMReviewCommentDataSource;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanelBounds;
import ca.teamdman.sfm.client.theme.SFMClientTheme;
import ca.teamdman.sfm.client.theme.SFMClientThemeService;
import ca.teamdman.sfm.client.theme.SFMColourRole;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Responsive changed-file browser over the loader-owned repository-review boundary. */
public final class SFMRepositoryReviewPanel implements SFMScreenPanel {
    private static final int FILE_ROW_HEIGHT = 22;

    private final SFMRepositoryReviewRepository repository;
    private final SFMFilePresentationRegistry presentations = SFMFilePresentationRegistry.createDefault();
    private SFMRepositoryReviewRepository.OpenBundle bundle;
    private SFMReviewCommentDataSource.SessionView session;
    private int selected;
    private String search = "";
    private boolean searching;
    private boolean editing;
    private String draft = "";
    private String status;
    private int listX;
    private int listY;
    private int listWidth;
    private int sourceTop;
    private int sourceBottom;
    private int beforeSourceX;
    private int afterSourceX;
    private int sourceColumnWidth;
    private @Nullable SFMReviewCommentDataSource.RangeView selectedRange;

    public SFMRepositoryReviewPanel(
            SFMRepositoryReviewRepository repository,
            SFMRepositoryReviewRepository.OpenBundle bundle
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.bundle = Objects.requireNonNull(bundle, "bundle");
        session = bundle.dataSource().refresh();
        status = (bundle.restored() ? "Restored" : "Opened") + " managed bundle · "
                + bundle.summary().changedFileCount() + " changed files";
        SFM.LOGGER.info("SFM_REPOSITORY_REVIEW_OPEN bundle={} session={} restored={}",
                bundle.summary().id(), bundle.sessionId(), bundle.restored());
    }

    public SFMRepositoryReviewRepository.OpenBundle bundle() { return bundle; }
    public int selectedIndex() { return selected; }
    public String status() { return status; }

    @Override public Component title() { return Component.literal("Repository Review"); }
    @Override public Component narration() { return Component.literal(status); }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (searching || editing) return keyPressedDuringInput(key);
        if (key == GLFW.GLFW_KEY_SLASH) {
            searching = true;
            search = "";
            return true;
        }
        if (key == GLFW.GLFW_KEY_DOWN) {
            selected = Math.min(selected + 1, Math.max(0, visibleFiles().size() - 1));
            selectedRange = null;
            status = activeFile() == null ? "No changed files match" : "Selected " + displayPath(activeFile());
            return true;
        }
        if (key == GLFW.GLFW_KEY_UP) {
            selected = Math.max(0, selected - 1);
            selectedRange = null;
            status = activeFile() == null ? "No changed files match" : "Selected " + displayPath(activeFile());
            return true;
        }
        if (key == GLFW.GLFW_KEY_N) {
            beginComment();
            return true;
        }
        return false;
    }

    private boolean keyPressedDuringInput(int key) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            searching = false;
            editing = false;
            status = "Input cancelled";
            return true;
        }
        if (key == GLFW.GLFW_KEY_BACKSPACE) {
            if (editing && !draft.isEmpty()) draft = draft.substring(0, draft.length() - 1);
            else if (searching && !search.isEmpty()) search = search.substring(0, search.length() - 1);
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            if (editing) submitComment();
            else {
                searching = false;
                status = "Filtered to " + visibleFiles().size() + " changed files";
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        if (Character.isISOControl(character)) return false;
        if (searching) {
            search += character;
            selected = 0;
            return true;
        }
        if (editing) {
            draft += character;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        if (mouseX >= listX && mouseX < listX + listWidth && mouseY >= listY) {
            int index = (int) (mouseY - listY) / FILE_ROW_HEIGHT;
            if (index >= 0 && index < visibleFiles().size()) {
                selected = index;
                selectedRange = null;
                status = "Selected " + displayPath(Objects.requireNonNull(activeFile()));
            }
            return true;
        }
        if (mouseY < sourceTop + 28 || mouseY >= sourceBottom) return false;
        int line = (int) (mouseY - sourceTop - 28) / 14;
        if (mouseX >= beforeSourceX && mouseX < beforeSourceX + sourceColumnWidth) {
            return selectSourceLine(SFMReviewCommentDataSource.Side.BEFORE, line);
        }
        if (mouseX >= afterSourceX && mouseX < afterSourceX + sourceColumnWidth) {
            return selectSourceLine(SFMReviewCommentDataSource.Side.AFTER, line);
        }
        return false;
    }

    public void selectFile(int index) {
        if (visibleFiles().isEmpty()) {
            selected = 0;
            selectedRange = null;
            status = "No changed files match";
            return;
        }
        selected = Math.max(0, Math.min(index, visibleFiles().size() - 1));
        selectedRange = null;
        status = "Selected changed file · " + displayPath(Objects.requireNonNull(activeFile()));
    }

    public void setSearch(String value) {
        search = value;
        selected = 0;
        selectedRange = null;
        status = "Search ‘" + value + "’ · " + visibleFiles().size() + " matches";
    }

    public boolean selectSourceLine(SFMReviewCommentDataSource.Side side, int line) {
        SFMReviewCommentDataSource.DocumentView document = document(side);
        if (document == null) {
            status = "No " + side.name().toLowerCase(Locale.ROOT) + " source for selected file";
            return false;
        }
        String[] lines = document.text().split("\\n", -1);
        if (line < 0 || line >= lines.length || line == lines.length - 1 && lines[line].isEmpty()) {
            status = "Source line is outside the document";
            return false;
        }
        int start = 0;
        for (int index = 0; index < line; index++) {
            start += lines[index].getBytes(StandardCharsets.UTF_8).length + 1;
        }
        int selectedLineBytes = lines[line].getBytes(StandardCharsets.UTF_8).length;
        if (selectedLineBytes == 0) {
            status = "Selected line contains no glyphs";
            return false;
        }
        int end = start + selectedLineBytes;
        selectedRange = new SFMReviewCommentDataSource.RangeView(document.id(), start, end);
        status = "Selected " + side.name().toLowerCase(Locale.ROOT) + " line " + (line + 1)
                + " · UTF-8 [" + start + "," + end + ")";
        return true;
    }

    public void beginComment() {
        if (selectedRange == null) {
            status = "Select a before/after source line before creating a comment";
            return;
        }
        editing = true;
        draft = "";
        status = "Type comment; Enter stores literal UTF-8 selection";
    }

    public void setDraft(String value) { draft = value; }

    public void submitComment() {
        if (selectedRange == null || draft.isBlank()) return;
        bundle.dataSource().createLiteralComment(draft, List.of(selectedRange));
        session = bundle.dataSource().refresh();
        editing = false;
        status = "Saved user comment in " + bundle.sessionId();
    }

    public long humanCommentCount() {
        return session.comments().stream().filter(comment -> comment.provenance().startsWith("human")).count();
    }

    /** Reopens by bundle id so this path proves repository/store restoration rather than cached panel state. */
    public void refresh() {
        bundle = repository.open(bundle.summary().id());
        session = bundle.dataSource().refresh();
        status = "Restored " + humanCommentCount() + " user comment from session";
    }

    @Override
    public void render(PoseStack poseStack, Minecraft minecraft, SFMScreenPanelBounds bounds,
                       int mouseX, int mouseY, float partialTick, boolean focused) {
        SFMClientTheme theme = SFMClientThemeService.active();
        int right = bounds.x() + bounds.width();
        int bottom = bounds.y() + bounds.height() - 28;
        GuiComponent.fill(poseStack, bounds.x(), bounds.y(), right, bounds.y() + bounds.height(),
                theme.colour(SFMColourRole.PANEL_BACKGROUND));
        drawReviewText(poseStack, minecraft, "Repository Review · " + bundle.summary().name(), bounds.x() + 8,
                bounds.y() + 7, bounds.width() - 16, theme.colour(SFMColourRole.TEXT_ACCENT), true);
        drawReviewText(poseStack, minecraft, bundle.summary().beforeLabel() + " → " + bundle.summary().afterLabel()
                        + " · managed repository-review inbox", bounds.x() + 8, bounds.y() + 20,
                bounds.width() - 16, theme.colour(SFMColourRole.TEXT_MUTED), false);
        drawReviewText(poseStack, minecraft, "Changed " + bundle.summary().changedFileCount() + " · showing "
                        + visibleFiles().size() + " · / search · ↑/↓ browse · N comment", bounds.x() + 8,
                bounds.y() + 33, bounds.width() - 16, theme.colour(SFMColourRole.TEXT_PRIMARY), false);

        int top = bounds.y() + 52;
        listX = bounds.x() + 8;
        listY = top + 25;
        listWidth = Math.min(330, Math.max(190, bounds.width() / 3));
        GuiComponent.fill(poseStack, listX, top, listX + listWidth, bottom, 0xFF171B20);
        drawReviewText(poseStack, minecraft, searching ? "Search: " + search + "_" : "CHANGED FILES",
                listX + 6, top + 7, listWidth - 12, theme.colour(SFMColourRole.TEXT_ACCENT), true);
        renderFiles(poseStack, minecraft, theme, bottom);

        int sourceX = listX + listWidth + 8;
        int sourceWidth = right - 8 - sourceX;
        int gap = 8;
        int columnWidth = (sourceWidth - gap) / 2;
        sourceTop = top;
        sourceBottom = bottom;
        beforeSourceX = sourceX;
        afterSourceX = sourceX + columnWidth + gap;
        sourceColumnWidth = columnWidth;
        renderDocument(poseStack, minecraft, theme, beforeDocument(), "BEFORE",
                sourceX, top, columnWidth, bottom);
        renderDocument(poseStack, minecraft, theme, afterDocument(), "AFTER",
                sourceX + columnWidth + gap, top, sourceWidth - columnWidth - gap, bottom);

        if (editing) {
            GuiComponent.fill(poseStack, bounds.x() + 20, bottom - 38, right - 20, bottom - 10, 0xFF101419);
            drawReviewText(poseStack, minecraft, "New comment: " + draft + "_", bounds.x() + 28, bottom - 30,
                    bounds.width() - 56, theme.colour(SFMColourRole.TEXT_PRIMARY), false);
        }
        drawReviewText(poseStack, minecraft, status + " · generated " + generatedForActive() + " · user "
                        + humanCommentCount(), bounds.x() + 8, bounds.y() + bounds.height() - 16,
                bounds.width() - 16, theme.colour(SFMColourRole.TEXT_ACCENT), false);
    }

    private void renderFiles(PoseStack poseStack, Minecraft minecraft, SFMClientTheme theme, int bottom) {
        List<SFMRepositoryReviewRepository.ChangedFile> rows = visibleFiles();
        for (int index = 0; index < rows.size() && listY + index * FILE_ROW_HEIGHT + FILE_ROW_HEIGHT < bottom; index++) {
            var file = rows.get(index);
            int y = listY + index * FILE_ROW_HEIGHT;
            if (index == selected) GuiComponent.fill(poseStack, listX + 2, y, listX + listWidth - 2,
                    y + FILE_ROW_HEIGHT - 1, theme.colour(SFMColourRole.PANEL_SELECTION));
            String path = displayPath(file);
            var presentation = presentations.presentationFor(SFMFileExplorerEntry.file(path, fileName(path)));
            SFMItemIconRenderer.render(minecraft, presentation.itemIcon(), listX + 6, y + 2);
            drawReviewText(poseStack, minecraft, kind(file).toUpperCase(Locale.ROOT) + "  " + fileName(path),
                    listX + 28, y + 6, listWidth - 34, presentation.textColour(), index == selected);
        }
        if (rows.isEmpty()) drawReviewText(poseStack, minecraft, "No changed files match", listX + 8, listY + 8,
                listWidth - 16, theme.colour(SFMColourRole.TEXT_MUTED), false);
    }

    private void renderDocument(PoseStack poseStack, Minecraft minecraft, SFMClientTheme theme,
                                SFMReviewCommentDataSource.DocumentView document, String label,
                                int x, int y, int width, int bottom) {
        GuiComponent.fill(poseStack, x, y, x + width, bottom, 0xFF151A1E);
        drawReviewText(poseStack, minecraft, label + " · " + (document == null ? "(none)" : fileName(document.path())),
                x + 5, y + 7, width - 10, label.equals("BEFORE") ? 0xFFFF6666 : 0xFF55FFFF, true);
        if (document == null) return;
        List<SFMReviewCommentDataSource.CommentView> comments = commentsFor(document.id());
        int visibleCommentCount = Math.min(comments.size(), 3);
        int commentTop = visibleCommentCount == 0 ? bottom : bottom - visibleCommentCount * 13 - 8;
        String[] lines = document.text().split("\\n", -1);
        for (int index = 0; index < lines.length && y + 28 + index * 14 < commentTop; index++) {
            if (selectedRange != null && selectedRange.documentRevisionId().equals(document.id())) {
                int start = lineByteStart(lines, index);
                int end = start + lines[index].getBytes(StandardCharsets.UTF_8).length;
                if (selectedRange.startByte() == start && selectedRange.endByte() == end) {
                    GuiComponent.fill(poseStack, x + 3, y + 25 + index * 14, x + width - 3,
                            y + 38 + index * 14, 0x665588CC);
                }
            }
            drawReviewText(poseStack, minecraft, String.format("%2d  %s", index + 1, lines[index]), x + 5,
                    y + 28 + index * 14, width - 10, theme.colour(SFMColourRole.TEXT_PRIMARY), false);
        }
        if (visibleCommentCount > 0) {
            GuiComponent.fill(poseStack, x + 2, commentTop, x + width - 2, bottom, 0xEE101419);
        }
        int row = commentTop + 4;
        for (var comment : comments.stream().limit(visibleCommentCount).toList()) {
            int colour = comment.provenance().startsWith("human") ? 0xFFFFCC55 : 0xFF77AAFF;
            drawReviewText(poseStack, minecraft, "• " + comment.text() + " · " + comment.provenance(),
                    x + 5, row, width - 10, colour, false);
            row += 13;
        }
    }

    private List<SFMRepositoryReviewRepository.ChangedFile> visibleFiles() {
        String query = search.toLowerCase(Locale.ROOT);
        return bundle.changedFiles().stream().filter(file ->
                displayPath(file).toLowerCase(Locale.ROOT).contains(query) || kind(file).contains(query)).toList();
    }

    private @Nullable SFMRepositoryReviewRepository.ChangedFile activeFile() {
        List<SFMRepositoryReviewRepository.ChangedFile> visible = visibleFiles();
        if (visible.isEmpty()) return null;
        selected = Math.min(selected, visible.size() - 1);
        return visible.get(selected);
    }

    private SFMReviewCommentDataSource.DocumentView beforeDocument() {
        return document(SFMReviewCommentDataSource.Side.BEFORE);
    }

    private SFMReviewCommentDataSource.DocumentView afterDocument() {
        return document(SFMReviewCommentDataSource.Side.AFTER);
    }

    private SFMReviewCommentDataSource.DocumentView document(SFMReviewCommentDataSource.Side side) {
        var file = activeFile();
        if (file == null) return null;
        String path = side == SFMReviewCommentDataSource.Side.BEFORE ? file.beforePath() : file.afterPath();
        if (path == null) return null;
        return session.documents().stream().filter(candidate -> candidate.side() == side
                && candidate.path().equals(path)).findFirst().orElse(null);
    }

    private List<SFMReviewCommentDataSource.CommentView> commentsFor(String documentId) {
        return session.comments().stream().filter(comment -> comment.ranges().stream()
                .anyMatch(range -> range.documentRevisionId().equals(documentId))).toList();
    }

    private long generatedForActive() {
        var active = activeFile();
        if (active == null) return 0;
        String path = displayPath(active);
        return session.comments().stream().filter(comment -> !comment.provenance().startsWith("human")
                && comment.ranges().stream().anyMatch(range -> range.documentRevisionId().contains(path))).count();
    }

    private static String displayPath(SFMRepositoryReviewRepository.ChangedFile file) {
        return file.afterPath() != null ? file.afterPath() : Objects.requireNonNull(file.beforePath());
    }

    private static String kind(SFMRepositoryReviewRepository.ChangedFile file) {
        return file.kind().name().toLowerCase(Locale.ROOT);
    }

    private static int lineByteStart(String[] lines, int line) {
        int start = 0;
        for (int index = 0; index < line; index++) start += lines[index].getBytes(StandardCharsets.UTF_8).length + 1;
        return start;
    }

    private static String fileName(String path) {
        int separator = path.lastIndexOf('/');
        return separator < 0 ? path : path.substring(separator + 1);
    }

    private static void drawReviewText(PoseStack poseStack, Minecraft minecraft, String text,
                                       int x, int y, int width, int colour, boolean shadow) {
        if (width <= 0) return;
        SFMFontUtils.draw(poseStack, minecraft.font, minecraft.font.plainSubstrByWidth(text, width),
                x, y, colour, shadow);
    }
}
