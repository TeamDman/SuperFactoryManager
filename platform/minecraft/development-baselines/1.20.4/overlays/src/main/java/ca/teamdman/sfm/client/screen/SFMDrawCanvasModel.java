package ca.teamdman.sfm.client.screen;

import java.util.ArrayList;
import java.util.List;

public class SFMDrawCanvasModel {
    public static final int PRIMARY_CURSOR_COLOR = 0xFFE6EDF3;
    public static final int SECONDARY_CURSOR_COLOR = 0xFF7DD3FC;

    private final List<CanvasGlyph> glyphs = new ArrayList<>();
    private List<CanvasCursor> cursors = new ArrayList<>();
    private int focusedCursorIndex;

    public SFMDrawCanvasModel() {
        ensureCursors();
    }

    public List<CanvasGlyph> glyphs() {
        return glyphs;
    }

    public List<CanvasCursor> cursors() {
        ensureCursors();
        return cursors;
    }

    public int focusedCursorIndex() {
        ensureCursors();
        return focusedCursorIndex;
    }

    public CanvasCursor focusedCursor() {
        ensureCursors();
        return cursors.get(focusedCursorIndex);
    }

    public double cursorCanvasX() {
        return focusedCursor().x();
    }

    public double cursorCanvasY() {
        return focusedCursor().y();
    }

    public void setCursorCanvasX(double cursorCanvasX) {
        focusedCursor().setX(cursorCanvasX);
        collapseDuplicateCursors();
    }

    public void setCursorCanvasY(double cursorCanvasY) {
        focusedCursor().setY(cursorCanvasY);
        collapseDuplicateCursors();
    }

    public void setCursor(
            double cursorCanvasX,
            double cursorCanvasY
    ) {
        focusedCursor().set(cursorCanvasX, cursorCanvasY);
        collapseDuplicateCursors();
    }

    public void setActiveCursors(
            double cursorCanvasX,
            double cursorCanvasY
    ) {
        ensureCursors();
        for (CanvasCursor cursor : cursors) {
            if (cursor.active()) {
                cursor.set(cursorCanvasX, cursorCanvasY);
            }
        }
        collapseDuplicateCursors();
    }

    public void setAllCursors(
            double cursorCanvasX,
            double cursorCanvasY
    ) {
        ensureCursors();
        for (CanvasCursor cursor : cursors) {
            cursor.set(cursorCanvasX, cursorCanvasY);
        }
        collapseDuplicateCursors();
    }

    public void addCursor(
            double cursorCanvasX,
            double cursorCanvasY
    ) {
        ensureCursors();
        cursors.add(new CanvasCursor(cursorCanvasX, cursorCanvasY, nextCursorColor(), true));
        focusedCursorIndex = cursors.size() - 1;
        collapseDuplicateCursors();
    }

    public void addCursorAvoidingCrowding(
            double cursorCanvasX,
            double cursorCanvasY,
            int emptyAreaMinimumWidth,
            int lineHeight
    ) {
        ensureCursors();
        CanvasCursor candidate = new CanvasCursor(cursorCanvasX, cursorCanvasY, nextCursorColor(), true);
        CanvasGlyph glyphAtCandidate = glyphAt(candidate, lineHeight);
        if (glyphAtCandidate != null) {
            if (cursorClosestToGlyph(glyphAtCandidate) != null) {
                return;
            }
            addCursor(cursorCanvasX, cursorCanvasY);
            return;
        }
        int safeMinimumWidth = Math.max(1, emptyAreaMinimumWidth);
        int safeLineHeight = Math.max(1, lineHeight);
        for (CanvasCursor cursor : cursors) {
            double dx = Math.abs(cursor.x() - cursorCanvasX);
            double dy = Math.abs(cursor.y() - cursorCanvasY);
            if (dx < safeMinimumWidth && dy < safeLineHeight) {
                return;
            }
        }
        addCursor(cursorCanvasX, cursorCanvasY);
    }

    public void focusPreviousCursor(boolean include) {
        focusCursor(-1, include);
    }

    public void focusNextCursor(boolean include) {
        focusCursor(1, include);
    }

    public void collapseToFocusedCursor() {
        ensureCursors();
        CanvasCursor focused = focusedCursor();
        cursors.clear();
        cursors.add(focused);
        focusedCursorIndex = 0;
        focused.setActive(true);
    }

    public void ensureCursorClosestToEachGlyph() {
        ensureCursors();
        for (CanvasGlyph glyph : glyphs) {
            CanvasCursor cursor = cursorClosestToGlyph(glyph);
            if (cursor == null) {
                cursors.add(new CanvasCursor(glyph.x(), glyph.y(), nextCursorColor(), true));
                focusedCursorIndex = cursors.size() - 1;
            } else {
                cursor.setActive(true);
            }
        }
        collapseDuplicateCursors();
    }

    public void ensureCursorClosestToEachGlyphOnActiveCursorLines(int lineHeight) {
        List<CanvasCursor> active = activeCursorsSnapshot();
        List<CanvasGlyph> nearestGlyphs = new ArrayList<>();
        for (CanvasCursor cursor : active) {
            CanvasGlyph nearest = nearestGlyph(cursor);
            if (nearest != null && !nearestGlyphs.contains(nearest)) {
                nearestGlyphs.add(nearest);
            }
        }
        List<CanvasGlyph> targetGlyphs = glyphsIntersectingAnyGlyphBounds(nearestGlyphs, lineHeight);
        if (!targetGlyphs.isEmpty() && eachGlyphHasClosestCursor(targetGlyphs)) {
            List<CanvasGlyph> nextLineGlyphs = nextVisualLineBelow(targetGlyphs, lineHeight);
            if (!nextLineGlyphs.isEmpty()) {
                targetGlyphs = nextLineGlyphs;
            }
        }
        ensureCursorClosestToEachGlyph(targetGlyphs);
        collapseDuplicateCursors();
    }

    public void discardCursorsNotClosestToAnyGlyph() {
        ensureCursors();
        if (glyphs.isEmpty()) {
            collapseToFocusedCursor();
            return;
        }

        CanvasCursor originalFocusedCursor = focusedCursor();
        List<CanvasCursor> retained = new ArrayList<>();
        for (CanvasGlyph glyph : glyphs) {
            CanvasCursor closest = closestCursorToGlyph(glyph);
            if (closest != null && !retained.contains(closest)) {
                retained.add(closest);
            }
        }
        if (retained.isEmpty()) {
            collapseToFocusedCursor();
            return;
        }

        cursors = retained;
        focusedCursorIndex = Math.max(0, retained.indexOf(originalFocusedCursor));
        for (CanvasCursor cursor : cursors) {
            cursor.setActive(true);
        }
        collapseDuplicateCursors();
    }

    public void typeGlyph(
            String text,
            int width
    ) {
        typeGlyph(text, width, 1);
    }

    public void typeGlyph(
            String text,
            int width,
            int lineHeight
    ) {
        for (CanvasCursor cursor : activeCursorsSnapshot()) {
            double lineY = visualLineYForCursor(cursor, lineHeight);
            double insertionX = insertionXForCursor(cursor, lineHeight);
            cursor.set(insertionX, lineY);
            shiftLineContentAndCursorsAtOrAfter(lineY, insertionX, width, cursor);
            if (!" ".equals(text)) {
                glyphs.add(new CanvasGlyph(text, cursor.x(), cursor.y(), width));
            }
            cursor.move(width, 0.0D);
        }
        collapseDuplicateCursors();
    }

    public void typeText(
            String text,
            GlyphWidthReader glyphWidthReader,
            int lineHeight
    ) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\r') {
                continue;
            }
            if (c == '\n') {
                moveCursorToNextInputLine(lineHeight);
            } else {
                String glyphText = Character.toString(c);
                typeGlyph(glyphText, glyphWidthReader.width(glyphText), lineHeight);
            }
        }
    }

    public void pasteText(
            String text,
            GlyphWidthReader glyphWidthReader,
            int lineHeight
    ) {
        for (int offset = 0; offset < text.length(); ) {
            int codePoint = text.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (codePoint == '\r') {
                continue;
            }
            if (codePoint == '\n') {
                insertLineBreak(lineHeight);
                continue;
            }
            String glyphText = new String(Character.toChars(codePoint));
            typeGlyph(glyphText, glyphWidthReader.width(glyphText), lineHeight);
        }
    }

    public String copyableText(
            int spaceWidth,
            int lineHeight
    ) {
        List<CanvasGlyph> selectedGlyphs = selectedGlyphs(lineHeight);
        if (selectedGlyphs.isEmpty()) {
            return projectedText(spaceWidth, lineHeight);
        }
        return SFMDrawCanvasSyntaxHighlightingHelper
                .projectCanvasDocument(normalizedGlyphs(selectedGlyphs), spaceWidth, lineHeight)
                .text();
    }

    public String projectedText(
            int spaceWidth,
            int lineHeight
    ) {
        return SFMDrawCanvasSyntaxHighlightingHelper
                .projectCanvasDocument(glyphs, spaceWidth, lineHeight)
                .text();
    }

    public void backspace() {
        deleteLeft();
    }

    public void moveCursorRaw(
            double deltaX,
            double deltaY
    ) {
        applyToActiveCursors(() -> focusedCursor().move(deltaX, deltaY));
    }

    public void deleteLeft() {
        deleteLeft(1);
    }

    public void deleteLeft(int lineHeight) {
        List<CanvasCursor> active = activeCursorsSnapshot();
        if (active.size() <= 1) {
            applyToActiveCursors(() -> deleteLeftFocused(lineHeight));
            return;
        }
        deleteNearestGlyphsTransactionally(active, false);
    }

    public void deleteNearestAndMoveRight() {
        deleteNearestAndMoveRight(1);
    }

    public void deleteNearestAndMoveRight(int lineHeight) {
        List<CanvasCursor> active = activeCursorsSnapshot();
        if (active.size() <= 1) {
            applyToActiveCursors(() -> deleteNearestAndMoveRightFocused(lineHeight));
            return;
        }
        deleteNearestGlyphsTransactionally(active, true);
    }

    public void deleteLeftWord(int lineHeight) {
        deleteNearestWordTransactionally(false);
    }

    public void deleteRightWord(int lineHeight) {
        deleteNearestWordTransactionally(true);
    }

    public void moveCursorLeft() {
        moveCursorLeft(1);
    }

    public void moveCursorLeft(int lineHeight) {
        moveCursorLeft(lineHeight, 1);
    }

    public void moveCursorLeft(
            int lineHeight,
            int spaceWidth
    ) {
        applyToActiveCursors(() -> moveCursorLeftFocused(lineHeight, spaceWidth));
    }

    public void moveCursorRight() {
        moveCursorRight(1);
    }

    public void moveCursorRight(int spaceWidth) {
        applyToActiveCursors(() -> moveCursorRightFocused(spaceWidth));
    }

    public void moveCursorLeftWord(
            int lineHeight,
            int spaceWidth
    ) {
        applyToActiveCursors(() -> moveCursorLeftWordFocused(lineHeight, spaceWidth));
    }

    public void moveCursorRightWord(
            int lineHeight,
            int spaceWidth
    ) {
        applyToActiveCursors(() -> moveCursorRightWordFocused(lineHeight, spaceWidth));
    }

    public void addCursorLeftWord(
            int lineHeight,
            int spaceWidth
    ) {
        CursorPoint target = leftWordTarget(focusedCursor(), lineHeight, spaceWidth);
        addCursor(target.x(), target.y());
    }

    public void addCursorRightWord(
            int lineHeight,
            int spaceWidth
    ) {
        CursorPoint target = rightWordTarget(focusedCursor(), lineHeight, spaceWidth);
        addCursor(target.x(), target.y());
    }

    public void addCursorUpToGlyph(int lineHeight) {
        CursorPoint target = verticalTarget(focusedCursor(), -1, lineHeight, true);
        addCursor(target.x(), target.y());
    }

    public void addCursorDownToGlyph(int lineHeight) {
        CursorPoint target = verticalTarget(focusedCursor(), 1, lineHeight, true);
        addCursor(target.x(), target.y());
    }

    public void moveCursorUp() {
        moveCursorUp(1);
    }

    public void moveCursorDown() {
        moveCursorDown(1);
    }

    public void moveCursorUp(int lineHeight) {
        applyToActiveCursors(() -> moveCursorVertically(-1, lineHeight, false));
    }

    public void moveCursorDown(int lineHeight) {
        applyToActiveCursors(() -> moveCursorVertically(1, lineHeight, false));
    }

    public void moveCursorUpToGlyph(int lineHeight) {
        applyToActiveCursors(() -> moveCursorVertically(-1, lineHeight, true));
    }

    public void moveCursorDownToGlyph(int lineHeight) {
        applyToActiveCursors(() -> moveCursorVertically(1, lineHeight, true));
    }

    public void moveCursorToLineStart() {
        applyToActiveCursors(this::moveCursorToLineStartFocused);
    }

    public void moveCursorToLineEnd() {
        applyToActiveCursors(this::moveCursorToLineEndFocused);
    }

    public void moveCursorToDocumentStart() {
        applyToActiveCursors(this::moveCursorToDocumentStartFocused);
    }

    public void moveCursorToDocumentEnd() {
        applyToActiveCursors(this::moveCursorToDocumentEndFocused);
    }

    public void moveCursorToNextLine(int lineHeight) {
        applyToActiveCursors(() -> moveCursorToNextLineFocused(lineHeight));
    }

    private void moveCursorToNextInputLine(int lineHeight) {
        applyToActiveCursors(() -> focusedCursor().set(0.0D, cursorCanvasY() + Math.max(1, lineHeight)));
    }

    public void insertLineBreak(int lineHeight) {
        List<CanvasCursor> active = activeCursorsSnapshot();
        List<LineBreakShift> breakRows = distinctSortedLineBreakShifts(active, lineHeight);
        List<CursorLineBreakTarget> cursorTargets = new ArrayList<>();
        for (CanvasCursor cursor : active) {
            CanvasGlyph containing = glyphAt(cursor, lineHeight);
            double breakY = containing == null ? cursor.y() : containing.y();
            cursorTargets.add(new CursorLineBreakTarget(
                    cursor,
                    breakY,
                    lineStartX(breakY).orElse(cursor.x()),
                    containing != null
            ));
        }

        List<CanvasGlyph> movedGlyphs = new ArrayList<>();
        for (CanvasGlyph glyph : glyphs) {
            int shiftCount = countLineBreakShiftsBeforeOrAt(breakRows, glyph.y());
            movedGlyphs.add(new CanvasGlyph(glyph.text(), glyph.x(), glyph.y() + shiftCount * lineHeight, glyph.width()));
        }
        glyphs.clear();
        glyphs.addAll(movedGlyphs);

        for (CanvasCursor cursor : cursors) {
            int shiftCount = countLineBreakShiftsBeforeOrAt(breakRows, cursor.y());
            if (shiftCount != 0) {
                cursor.setY(cursor.y() + shiftCount * lineHeight);
            }
        }
        for (CursorLineBreakTarget target : cursorTargets) {
            int shiftCount = countLineBreakShiftsBefore(breakRows, target.originalY());
            double targetY = target.insertBeforeRow()
                             ? target.originalY() + shiftCount * lineHeight
                             : target.originalY() + shiftCount * lineHeight + lineHeight;
            target.cursor().set(target.lineStartX(), targetY);
        }
        collapseDuplicateCursors();
    }

    private void deleteLeftFocused(int lineHeight) {
        if (glyphs.isEmpty()) {
            return;
        }
        if (glyphsOnVisualLine(cursorCanvasY(), lineHeight).isEmpty()) {
            if (deleteBlankLineBeforeCursor(lineHeight)) {
                return;
            }
            CanvasGlyph previousLineLast = rightMostGlyphOnPreviousLine(cursorCanvasY());
            if (previousLineLast != null) {
                deleteTargetsTransactionally(List.of(new CursorTarget(focusedCursor(), previousLineLast)), false);
            }
            return;
        }
        CanvasGlyph deleted = nearestGlyph();
        if (deleted != null) {
            deleteTargetsTransactionally(List.of(new CursorTarget(focusedCursor(), deleted)), false);
        }
    }

    private boolean deleteBlankLineBeforeCursor(int lineHeight) {
        int safeLineHeight = Math.max(1, lineHeight);
        Double previousY = previousGlyphRow(cursorCanvasY());
        Double nextY = nextGlyphRow(cursorCanvasY());
        if (previousY == null || nextY == null) {
            return false;
        }
        if (cursorCanvasY() - previousY < safeLineHeight || nextY - cursorCanvasY() < safeLineHeight) {
            return false;
        }

        double originalCursorY = cursorCanvasY();
        moveGlyphRowsAtOrBelow(originalCursorY, -safeLineHeight);
        for (CanvasCursor cursor : cursors) {
            if (cursor != focusedCursor() && cursor.y() >= originalCursorY) {
                cursor.setY(cursor.y() - safeLineHeight);
            }
        }
        if (originalCursorY - previousY > safeLineHeight) {
            focusedCursor().setY(originalCursorY - safeLineHeight);
        } else {
            moveCursorToEndOfLine(glyphsOnLine(previousY));
        }
        collapseDuplicateCursors();
        return true;
    }

    private void moveGlyphRowsAtOrBelow(
            double y,
            double deltaY
    ) {
        List<CanvasGlyph> movedGlyphs = new ArrayList<>();
        for (CanvasGlyph glyph : glyphs) {
            movedGlyphs.add(new CanvasGlyph(
                    glyph.text(),
                    glyph.x(),
                    glyph.y() >= y ? glyph.y() + deltaY : glyph.y(),
                    glyph.width()
            ));
        }
        glyphs.clear();
        glyphs.addAll(movedGlyphs);
    }

    private double visualLineYForCursor(
            CanvasCursor cursor,
            int lineHeight
    ) {
        CanvasGlyph containing = glyphAt(cursor, lineHeight);
        return containing == null ? cursor.y() : containing.y();
    }

    private double insertionXForCursor(
            CanvasCursor cursor,
            int lineHeight
    ) {
        CanvasGlyph containing = glyphAt(cursor, lineHeight);
        return containing == null ? cursor.x() : containing.x();
    }

    private void shiftLineContentAndCursorsAtOrAfter(
            double y,
            double x,
            double deltaX,
            CanvasCursor excludedCursor
    ) {
        List<CanvasGlyph> movedGlyphs = new ArrayList<>();
        for (CanvasGlyph glyph : glyphs) {
            movedGlyphs.add(new CanvasGlyph(
                    glyph.text(),
                    Double.compare(glyph.y(), y) == 0 && glyph.x() >= x ? glyph.x() + deltaX : glyph.x(),
                    glyph.y(),
                    glyph.width()
            ));
        }
        glyphs.clear();
        glyphs.addAll(movedGlyphs);
        for (CanvasCursor cursor : cursors) {
            if (cursor != excludedCursor && Double.compare(cursor.y(), y) == 0 && cursor.x() >= x) {
                cursor.setX(cursor.x() + deltaX);
            }
        }
    }

    private void closeLineGaps(List<CanvasGlyph> removedGlyphs) {
        List<CanvasGlyph> movedGlyphs = new ArrayList<>();
        for (CanvasGlyph glyph : glyphs) {
            movedGlyphs.add(new CanvasGlyph(
                    glyph.text(),
                    glyph.x() - removedWidthBefore(removedGlyphs, glyph.y(), glyph.x()),
                    glyph.y(),
                    glyph.width()
            ));
        }
        glyphs.clear();
        glyphs.addAll(movedGlyphs);
        for (CanvasCursor cursor : cursors) {
            cursor.setX(cursor.x() - removedWidthBefore(removedGlyphs, cursor.y(), cursor.x()));
        }
    }

    private double removedWidthBefore(
            List<CanvasGlyph> removedGlyphs,
            double y,
            double x
    ) {
        double removedWidth = 0.0D;
        for (CanvasGlyph removed : removedGlyphs) {
            if (Double.compare(removed.y(), y) == 0 && removed.x() < x) {
                removedWidth += removed.width();
            }
        }
        return removedWidth;
    }

    private void deleteNearestAndMoveRightFocused(int lineHeight) {
        if (glyphs.isEmpty()) {
            return;
        }
        if (deleteBlankLineAfterCursor(lineHeight)) {
            return;
        }
        CanvasGlyph deleted = nearestGlyph();
        if (deleted == null) {
            return;
        }
        deleteTargetsTransactionally(List.of(new CursorTarget(focusedCursor(), deleted)), true);
    }

    private boolean deleteBlankLineAfterCursor(int lineHeight) {
        int safeLineHeight = Math.max(1, lineHeight);
        double currentY = currentOrPreviousGlyphRow(cursorCanvasY());
        Double nextY = nextGlyphRow(currentY);
        if (nextY == null || nextY - currentY <= safeLineHeight) {
            return false;
        }
        moveGlyphRowsAtOrBelow(currentY + safeLineHeight * 2.0D, -safeLineHeight);
        for (CanvasCursor cursor : cursors) {
            if (cursor != focusedCursor() && cursor.y() >= currentY + safeLineHeight * 2.0D) {
                cursor.setY(cursor.y() - safeLineHeight);
            }
        }
        collapseDuplicateCursors();
        return true;
    }

    private void moveCursorLeftFocused(
            int lineHeight,
            int spaceWidth
    ) {
        int safeSpaceWidth = Math.max(1, spaceWidth);
        if (glyphs.isEmpty()) {
            focusedCursor().move(-safeSpaceWidth, 0.0D);
            return;
        }

        List<CanvasGlyph> line = glyphsOnVisualLine(cursorCanvasY(), lineHeight);
        if (!line.isEmpty()) {
            CanvasGlyph containing = glyphContainingX(line, cursorCanvasX());
            CanvasGlyph left = containing == null
                               ? rightMostGlyphBefore(line, cursorCanvasX())
                               : rightMostGlyphBefore(line, containing.x());
            if (left != null) {
                setCursor(left.x(), left.y());
                return;
            }
            if (!moveCursorToEndOfPreviousLine(line.get(0).y())) {
                focusedCursor().move(-safeSpaceWidth, 0.0D);
            }
            return;
        }

        CanvasGlyph nearest = nearestGlyph();
        if (nearest == null) {
            focusedCursor().move(-safeSpaceWidth, 0.0D);
            return;
        }
        List<CanvasGlyph> nearestLine = glyphsOnLine(nearest.y());
        if (cursorCanvasY() > nearest.y()) {
            moveCursorToEndOfLine(nearestLine);
            return;
        }

        CanvasGlyph left = rightMostGlyphBefore(nearestLine, nearest.x());
        if (left != null) {
            setCursor(left.x(), left.y());
            return;
        }
        if (!moveCursorToEndOfPreviousLine(nearest.y())) {
            focusedCursor().move(-safeSpaceWidth, 0.0D);
        }
    }

    private void moveCursorRightFocused(int spaceWidth) {
        int safeSpaceWidth = Math.max(1, spaceWidth);
        List<CanvasGlyph> line = glyphsOnLine(cursorCanvasY());
        if (line.isEmpty()) {
            CanvasGlyph nearest = nearestGlyph();
            if (nearest == null) {
                focusedCursor().move(safeSpaceWidth, 0.0D);
                return;
            }
            setCursor(nearest.x(), nearest.y());
            return;
        }
        CanvasGlyph next = leftMostGlyphAtOrAfter(line, cursorCanvasX());
        if (next == null) {
            if (!moveCursorToStartOfNextLine(cursorCanvasY())) {
                focusedCursor().move(safeSpaceWidth, 0.0D);
            }
            return;
        }
        setCursor(next.x() + next.width(), next.y());
    }

    private void moveCursorLeftWordFocused(
            int lineHeight,
            int spaceWidth
    ) {
        CursorPoint target = leftWordTarget(focusedCursor(), lineHeight, spaceWidth);
        setCursor(target.x(), target.y());
    }

    private CursorPoint leftWordTarget(
            CanvasCursor cursor,
            int lineHeight,
            int spaceWidth
    ) {
        CanvasGlyph target = glyphAt(cursor, lineHeight);
        if (target == null) {
            target = rightMostGlyphBefore(glyphsOnLine(cursor.y()), cursor.x());
        }
        if (target == null) {
            return leftTarget(cursor, lineHeight, spaceWidth);
        }
        List<CanvasGlyph> run = contiguousGlyphRun(target);
        if (run.isEmpty()) {
            return leftTarget(cursor, lineHeight, spaceWidth);
        }
        CanvasGlyph first = run.get(0);
        return new CursorPoint(first.x(), first.y());
    }

    private void moveCursorRightWordFocused(
            int lineHeight,
            int spaceWidth
    ) {
        CursorPoint target = rightWordTarget(focusedCursor(), lineHeight, spaceWidth);
        setCursor(target.x(), target.y());
    }

    private CursorPoint rightWordTarget(
            CanvasCursor cursor,
            int lineHeight,
            int spaceWidth
    ) {
        CanvasGlyph target = glyphAt(cursor, lineHeight);
        if (target == null) {
            target = leftMostGlyphAtOrAfter(glyphsOnLine(cursor.y()), cursor.x());
        }
        if (target == null) {
            return rightTarget(cursor, spaceWidth);
        }
        List<CanvasGlyph> run = contiguousGlyphRun(target);
        if (run.isEmpty()) {
            return rightTarget(cursor, spaceWidth);
        }
        CanvasGlyph last = run.get(run.size() - 1);
        return new CursorPoint(last.x() + last.width(), last.y());
    }

    private CursorPoint leftTarget(
            CanvasCursor cursor,
            int lineHeight,
            int spaceWidth
    ) {
        int safeSpaceWidth = Math.max(1, spaceWidth);
        if (glyphs.isEmpty()) {
            return new CursorPoint(cursor.x() - safeSpaceWidth, cursor.y());
        }

        List<CanvasGlyph> line = glyphsOnVisualLine(cursor.y(), lineHeight);
        if (!line.isEmpty()) {
            CanvasGlyph containing = glyphContainingX(line, cursor.x());
            CanvasGlyph left = containing == null
                               ? rightMostGlyphBefore(line, cursor.x())
                               : rightMostGlyphBefore(line, containing.x());
            if (left != null) {
                return new CursorPoint(left.x(), left.y());
            }
            CursorPoint previousLineEnd = endOfPreviousLineTarget(line.get(0).y());
            return previousLineEnd == null ? new CursorPoint(cursor.x() - safeSpaceWidth, cursor.y()) : previousLineEnd;
        }

        CanvasGlyph nearest = nearestGlyph(cursor);
        if (nearest == null) {
            return new CursorPoint(cursor.x() - safeSpaceWidth, cursor.y());
        }
        CanvasGlyph left = rightMostGlyphBefore(glyphsOnLine(nearest.y()), cursor.x());
        if (left != null) {
            return new CursorPoint(left.x(), left.y());
        }
        CursorPoint previousLineEnd = endOfPreviousLineTarget(nearest.y());
        return previousLineEnd == null ? new CursorPoint(cursor.x() - safeSpaceWidth, cursor.y()) : previousLineEnd;
    }

    private CursorPoint rightTarget(
            CanvasCursor cursor,
            int spaceWidth
    ) {
        int safeSpaceWidth = Math.max(1, spaceWidth);
        List<CanvasGlyph> line = glyphsOnLine(cursor.y());
        if (line.isEmpty()) {
            CanvasGlyph nearest = nearestGlyph(cursor);
            return nearest == null
                   ? new CursorPoint(cursor.x() + safeSpaceWidth, cursor.y())
                   : new CursorPoint(nearest.x(), nearest.y());
        }
        CanvasGlyph next = leftMostGlyphAtOrAfter(line, cursor.x());
        if (next == null) {
            CanvasGlyph first = firstGlyphOnNextLine(cursor.y());
            return first == null
                   ? new CursorPoint(cursor.x() + safeSpaceWidth, cursor.y())
                   : new CursorPoint(first.x(), first.y());
        }
        return new CursorPoint(next.x() + next.width(), next.y());
    }

    private CursorPoint endOfPreviousLineTarget(double y) {
        Double previousY = previousGlyphRow(y);
        if (previousY == null) {
            return null;
        }
        List<CanvasGlyph> line = glyphsOnLine(previousY);
        if (line.isEmpty()) {
            return null;
        }
        CanvasGlyph rightMost = line.get(line.size() - 1);
        return new CursorPoint(rightMost.x() + rightMost.width(), rightMost.y());
    }

    private void moveCursorToLineStartFocused() {
        List<CanvasGlyph> line = currentOrNearestLine();
        if (!line.isEmpty()) {
            CanvasGlyph first = line.get(0);
            setCursor(first.x(), first.y());
        } else {
            focusedCursor().setX(0.0D);
        }
    }

    private void moveCursorToLineEndFocused() {
        List<CanvasGlyph> line = currentOrNearestLine();
        moveCursorToEndOfLine(line);
    }

    private void moveCursorToDocumentStartFocused() {
        CanvasGlyph first = topmostThenLeftmostGlyph();
        if (first != null) {
            setCursor(first.x(), first.y());
        } else {
            setCursor(0.0D, 0.0D);
        }
    }

    private void moveCursorToDocumentEndFocused() {
        CanvasGlyph last = bottommostThenRightmostGlyph();
        if (last != null) {
            setCursor(last.x() + last.width(), last.y());
        }
    }

    private void moveCursorToNextLineFocused(int lineHeight) {
        CanvasGlyph nearest = nearestGlyph();
        if (nearest == null) {
            focusedCursor().move(0.0D, lineHeight);
            return;
        }

        CanvasGlyph leftMost = leftMostGlyphOnLine(nearest);
        double previousCursorCanvasY = cursorCanvasY();
        focusedCursor().set(leftMost.x(), leftMost.y() + lineHeight);
        if (nearest == leftMost && previousCursorCanvasY > nearest.y()) {
            focusedCursor().setY(Math.max(cursorCanvasY() + lineHeight, previousCursorCanvasY + lineHeight));
        }
    }

    private List<Double> distinctSortedRows(List<CanvasCursor> cursors) {
        List<Double> rows = new ArrayList<>();
        for (CanvasCursor cursor : cursors) {
            if (!rows.contains(cursor.y())) {
                rows.add(cursor.y());
            }
        }
        rows.sort(Double::compare);
        return rows;
    }

    private List<LineBreakShift> distinctSortedLineBreakShifts(
            List<CanvasCursor> cursors,
            int lineHeight
    ) {
        List<LineBreakShift> rows = new ArrayList<>();
        for (CanvasCursor cursor : cursors) {
            CanvasGlyph containing = glyphAt(cursor, lineHeight);
            LineBreakShift row = new LineBreakShift(
                    containing == null ? cursor.y() : containing.y(),
                    containing != null
            );
            if (!rows.contains(row)) {
                rows.add(row);
            }
        }
        rows.sort((left, right) -> {
            int yCompare = Double.compare(left.y(), right.y());
            if (yCompare != 0) {
                return yCompare;
            }
            return Boolean.compare(left.includeRow(), right.includeRow());
        });
        return rows;
    }

    private int countLineBreakShiftsBefore(
            List<LineBreakShift> rows,
            double y
    ) {
        int count = 0;
        for (LineBreakShift row : rows) {
            if (row.y() < y) {
                count++;
            }
        }
        return count;
    }

    private int countLineBreakShiftsBeforeOrAt(
            List<LineBreakShift> rows,
            double y
    ) {
        int count = 0;
        for (LineBreakShift row : rows) {
            if (row.y() < y || (row.includeRow() && Double.compare(row.y(), y) == 0)) {
                count++;
            }
        }
        return count;
    }

    private java.util.OptionalDouble lineStartX(double y) {
        Double lineStartX = null;
        for (CanvasGlyph glyph : glyphs) {
            if (Double.compare(glyph.y(), y) == 0 && (lineStartX == null || glyph.x() < lineStartX)) {
                lineStartX = glyph.x();
            }
        }
        return lineStartX == null ? java.util.OptionalDouble.empty() : java.util.OptionalDouble.of(lineStartX);
    }

    private void deleteNearestGlyphsTransactionally(
            List<CanvasCursor> active,
            boolean moveRight
    ) {
        List<CursorTarget> cursorTargets = new ArrayList<>();
        for (CanvasCursor cursor : active) {
            CanvasGlyph target = nearestGlyph(cursor);
            if (target == null) {
                continue;
            }
            cursorTargets.add(new CursorTarget(cursor, target));
        }
        deleteTargetsTransactionally(cursorTargets, moveRight);
    }

    private void deleteTargetsTransactionally(
            List<CursorTarget> cursorTargets,
            boolean moveRight
    ) {
        List<CanvasGlyph> targets = new ArrayList<>();
        for (CursorTarget cursorTarget : cursorTargets) {
            if (!targets.contains(cursorTarget.target())) {
                targets.add(cursorTarget.target());
            }
        }
        for (CursorTarget cursorTarget : cursorTargets) {
            CanvasGlyph target = cursorTarget.target();
            if (moveRight) {
                CanvasGlyph right = firstGlyphAfterOnLine(target.y(), target.x(), targets);
                if (right != null) {
                    cursorTarget.cursor().set(right.x(), right.y());
                    continue;
                }
                CanvasGlyph nextLineFirst = firstGlyphOnNextLine(target.y(), targets);
                if (nextLineFirst != null) {
                    cursorTarget.cursor().set(nextLineFirst.x(), nextLineFirst.y());
                    continue;
                }
            }
            cursorTarget.cursor().set(target.x(), target.y());
        }

        glyphs.removeAll(targets);
        closeLineGaps(targets);
        collapseDuplicateCursors();
    }

    private void deleteNearestWordTransactionally(boolean moveRight) {
        List<WordCursorTarget> wordTargets = new ArrayList<>();
        List<CanvasGlyph> targets = new ArrayList<>();
        for (CanvasCursor cursor : activeCursorsSnapshot()) {
            CanvasGlyph nearest = nearestGlyph(cursor);
            if (nearest == null) {
                continue;
            }
            List<CanvasGlyph> run = contiguousGlyphRun(nearest);
            if (run.isEmpty()) {
                continue;
            }
            for (CanvasGlyph glyph : run) {
                if (!targets.contains(glyph)) {
                    targets.add(glyph);
                }
            }
            CanvasGlyph left = run.get(0);
            CanvasGlyph right = run.get(run.size() - 1);
            wordTargets.add(new WordCursorTarget(cursor, left.y(), left.x(), right.x()));
        }

        for (WordCursorTarget wordTarget : wordTargets) {
            if (moveRight) {
                CanvasGlyph right = firstGlyphAfterOnLine(wordTarget.y(), wordTarget.rightX(), targets);
                if (right != null) {
                    wordTarget.cursor().set(right.x(), right.y());
                    continue;
                }
                CanvasGlyph nextLineFirst = firstGlyphOnNextLine(wordTarget.y(), targets);
                if (nextLineFirst != null) {
                    wordTarget.cursor().set(nextLineFirst.x(), nextLineFirst.y());
                    continue;
                }
            }
            wordTarget.cursor().set(wordTarget.leftX(), wordTarget.y());
        }

        glyphs.removeAll(targets);
        closeLineGaps(targets);
        collapseDuplicateCursors();
    }

    private void focusCursor(
            int direction,
            boolean include
    ) {
        ensureCursors();
        focusedCursorIndex = Math.floorMod(focusedCursorIndex + direction, cursors.size());
        if (include) {
            focusedCursor().setActive(true);
        } else {
            for (int i = 0; i < cursors.size(); i++) {
                cursors.get(i).setActive(i == focusedCursorIndex);
            }
        }
    }

    private List<CanvasCursor> activeCursorsSnapshot() {
        ensureCursors();
        List<CanvasCursor> active = new ArrayList<>();
        for (CanvasCursor cursor : cursors) {
            if (cursor.active()) {
                active.add(cursor);
            }
        }
        if (active.isEmpty()) {
            focusedCursor().setActive(true);
            active.add(focusedCursor());
        }
        return active;
    }

    private void applyToActiveCursors(CursorOperation operation) {
        List<CanvasCursor> active = activeCursorsSnapshot();
        int originalFocusedCursorIndex = focusedCursorIndex;
        for (CanvasCursor cursor : active) {
            int cursorIndex = cursors.indexOf(cursor);
            if (cursorIndex == -1) {
                continue;
            }
            focusedCursorIndex = cursorIndex;
            operation.apply();
        }
        focusedCursorIndex = cursors.isEmpty() ? 0 : clampIndex(originalFocusedCursorIndex, cursors.size());
        collapseDuplicateCursors();
    }

    private void collapseDuplicateCursors() {
        ensureCursors();
        for (int i = cursors.size() - 1; i >= 0; i--) {
            CanvasCursor cursor = cursors.get(i);
            for (int j = 0; j < i; j++) {
                CanvasCursor kept = cursors.get(j);
                if (Double.compare(cursor.x(), kept.x()) == 0 && Double.compare(cursor.y(), kept.y()) == 0) {
                    kept.setActive(kept.active() || cursor.active());
                    cursors.remove(i);
                    if (focusedCursorIndex == i) {
                        focusedCursorIndex = j;
                    } else if (focusedCursorIndex > i) {
                        focusedCursorIndex--;
                    }
                    break;
                }
            }
        }
        if (cursors.isEmpty()) {
            cursors.add(new CanvasCursor(0.0D, 0.0D, PRIMARY_CURSOR_COLOR, true));
            focusedCursorIndex = 0;
        }
        focusedCursorIndex = clampIndex(focusedCursorIndex, cursors.size());
        focusedCursor().setActive(true);
    }

    private int nextCursorColor() {
        ensureCursors();
        return switch (cursors.size() % 4) {
            case 1 -> SECONDARY_CURSOR_COLOR;
            case 2 -> 0xFFFBBF24;
            case 3 -> 0xFFA78BFA;
            default -> PRIMARY_CURSOR_COLOR;
        };
    }

    private void ensureCursors() {
        if (cursors == null) {
            cursors = new ArrayList<>();
        }
        if (cursors.isEmpty()) {
            cursors.add(new CanvasCursor(0.0D, 0.0D, PRIMARY_CURSOR_COLOR, true));
            focusedCursorIndex = 0;
        }
        focusedCursorIndex = clampIndex(focusedCursorIndex, cursors.size());
    }

    private static int clampIndex(
            int index,
            int size
    ) {
        return Math.max(0, Math.min(index, size - 1));
    }

    private CanvasGlyph nearestGlyph() {
        return nearestGlyph(focusedCursor());
    }

    private CanvasGlyph nearestGlyph(CanvasCursor cursor) {
        CanvasGlyph nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (CanvasGlyph glyph : glyphs) {
            double dx = cursor.x() - glyph.x();
            double dy = cursor.y() - glyph.y();
            double distance = dx * dx + dy * dy;
            if (distance < nearestDistance) {
                nearest = glyph;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    private List<CanvasGlyph> selectedGlyphs(int lineHeight) {
        List<CanvasGlyph> selectedGlyphs = new ArrayList<>();
        for (CanvasGlyph glyph : glyphs) {
            if (uniqueCursorInGlyphBounds(glyph, lineHeight) != null) {
                selectedGlyphs.add(glyph);
            }
        }
        return selectedGlyphs;
    }

    private CanvasCursor uniqueCursorInGlyphBounds(
            CanvasGlyph glyph,
            int lineHeight
    ) {
        CanvasCursor selected = null;
        for (CanvasCursor cursor : cursors) {
            if (!cursorInGlyphBounds(cursor, glyph, lineHeight)) {
                continue;
            }
            if (selected != null) {
                return null;
            }
            selected = cursor;
        }
        return selected;
    }

    private boolean cursorInGlyphBounds(
            CanvasCursor cursor,
            CanvasGlyph glyph,
            int lineHeight
    ) {
        int safeLineHeight = Math.max(1, lineHeight);
        return cursor.x() >= glyph.x()
               && cursor.x() < glyph.x() + glyph.width()
               && cursor.y() >= glyph.y()
               && cursor.y() < glyph.y() + safeLineHeight;
    }

    private List<CanvasGlyph> normalizedGlyphs(List<CanvasGlyph> sourceGlyphs) {
        double minX = 0.0D;
        double minY = 0.0D;
        boolean first = true;
        for (CanvasGlyph glyph : sourceGlyphs) {
            if (first || glyph.x() < minX) {
                minX = glyph.x();
            }
            if (first || glyph.y() < minY) {
                minY = glyph.y();
            }
            first = false;
        }
        List<CanvasGlyph> normalized = new ArrayList<>();
        for (CanvasGlyph glyph : sourceGlyphs) {
            normalized.add(new CanvasGlyph(
                    glyph.text(),
                    glyph.x() - minX,
                    glyph.y() - minY,
                    glyph.width()
            ));
        }
        return normalized;
    }

    private CanvasCursor cursorClosestToGlyph(CanvasGlyph target) {
        for (CanvasCursor cursor : cursors) {
            if (nearestGlyph(cursor) == target) {
                return cursor;
            }
        }
        return null;
    }

    private CanvasCursor closestCursorToGlyph(CanvasGlyph glyph) {
        CanvasCursor closest = null;
        double closestDistance = Double.MAX_VALUE;
        for (CanvasCursor cursor : cursors) {
            double dx = cursor.x() - glyph.x();
            double dy = cursor.y() - glyph.y();
            double distance = dx * dx + dy * dy;
            if (distance < closestDistance) {
                closest = cursor;
                closestDistance = distance;
            }
        }
        return closest;
    }

    private void ensureCursorClosestToEachGlyph(List<CanvasGlyph> targetGlyphs) {
        for (CanvasGlyph glyph : targetGlyphs) {
            CanvasCursor cursor = cursorClosestToGlyph(glyph);
            if (cursor == null) {
                cursors.add(new CanvasCursor(glyph.x(), glyph.y(), nextCursorColor(), true));
                focusedCursorIndex = cursors.size() - 1;
            } else {
                cursor.setActive(true);
            }
        }
    }

    private boolean eachGlyphHasClosestCursor(List<CanvasGlyph> targetGlyphs) {
        for (CanvasGlyph glyph : targetGlyphs) {
            if (cursorClosestToGlyph(glyph) == null) {
                return false;
            }
        }
        return true;
    }

    private List<CanvasGlyph> glyphsIntersectingAnyGlyphBounds(
            List<CanvasGlyph> targets,
            int lineHeight
    ) {
        List<CanvasGlyph> result = new ArrayList<>();
        for (CanvasGlyph glyph : glyphs) {
            if (intersectsAnyGlyphBounds(glyph, targets, lineHeight)) {
                result.add(glyph);
            }
        }
        result.sort((left, right) -> {
            int yCompare = Double.compare(left.y(), right.y());
            if (yCompare != 0) {
                return yCompare;
            }
            return Double.compare(left.x(), right.x());
        });
        return result;
    }

    private List<CanvasGlyph> nextVisualLineBelow(
            List<CanvasGlyph> currentLineGlyphs,
            int lineHeight
    ) {
        Double currentBottomY = null;
        for (CanvasGlyph glyph : currentLineGlyphs) {
            double glyphBottomY = glyph.y() + Math.max(1, lineHeight);
            if (currentBottomY == null || glyphBottomY > currentBottomY) {
                currentBottomY = glyphBottomY;
            }
        }
        if (currentBottomY == null) {
            return List.of();
        }

        CanvasGlyph nextSeed = null;
        for (CanvasGlyph glyph : glyphs) {
            if (glyph.y() >= currentBottomY && (nextSeed == null || glyph.y() < nextSeed.y())) {
                nextSeed = glyph;
            }
        }
        if (nextSeed == null) {
            return List.of();
        }
        return glyphsIntersectingAnyGlyphBounds(List.of(nextSeed), lineHeight);
    }

    private boolean intersectsAnyGlyphBounds(
            CanvasGlyph glyph,
            List<CanvasGlyph> targets,
            int lineHeight
    ) {
        for (CanvasGlyph target : targets) {
            if (glyphVerticalBoundsIntersect(glyph, target, lineHeight)) {
                return true;
            }
        }
        return false;
    }

    private boolean glyphVerticalBoundsIntersect(
            CanvasGlyph left,
            CanvasGlyph right,
            int lineHeight
    ) {
        int safeLineHeight = Math.max(1, lineHeight);
        double leftTop = left.y();
        double leftBottom = left.y() + safeLineHeight;
        double rightTop = right.y();
        double rightBottom = right.y() + safeLineHeight;
        return leftTop < rightBottom && rightTop < leftBottom;
    }

    private CanvasGlyph glyphAtCursor() {
        for (CanvasGlyph glyph : glyphs) {
            if (Double.compare(glyph.x(), cursorCanvasX()) == 0 && Double.compare(glyph.y(), cursorCanvasY()) == 0) {
                return glyph;
            }
        }
        return null;
    }

    private CanvasGlyph glyphAt(
            CanvasCursor cursor,
            int lineHeight
    ) {
        int safeLineHeight = Math.max(1, lineHeight);
        for (CanvasGlyph glyph : glyphs) {
            if (cursor.x() >= glyph.x()
                && cursor.x() < glyph.x() + glyph.width()
                && cursor.y() >= glyph.y()
                && cursor.y() < glyph.y() + safeLineHeight) {
                return glyph;
            }
        }
        return null;
    }

    private List<CanvasGlyph> glyphsOnLine(double y) {
        return glyphsOnLine(y, List.of());
    }

    private List<CanvasGlyph> glyphsOnVisualLine(
            double y,
            int lineHeight
    ) {
        List<CanvasGlyph> line = new ArrayList<>();
        int safeLineHeight = Math.max(1, lineHeight);
        for (CanvasGlyph glyph : glyphs) {
            if (y >= glyph.y() && y < glyph.y() + safeLineHeight) {
                line.add(glyph);
            }
        }
        line.sort((a, b) -> Double.compare(a.x(), b.x()));
        return line;
    }

    private List<CanvasGlyph> glyphsOnLine(
            double y,
            List<CanvasGlyph> excluded
    ) {
        List<CanvasGlyph> line = new ArrayList<>();
        for (CanvasGlyph glyph : glyphs) {
            if (!excluded.contains(glyph) && Double.compare(glyph.y(), y) == 0) {
                line.add(glyph);
            }
        }
        line.sort((a, b) -> Double.compare(a.x(), b.x()));
        return line;
    }

    private List<CanvasGlyph> currentOrNearestLine() {
        List<CanvasGlyph> line = glyphsOnLine(cursorCanvasY());
        if (!line.isEmpty()) {
            return line;
        }
        CanvasGlyph nearest = nearestGlyph();
        return nearest == null ? List.of() : glyphsOnLine(nearest.y());
    }

    private CanvasGlyph rightMostGlyphBefore(
            List<CanvasGlyph> line,
            double x
    ) {
        CanvasGlyph left = null;
        for (CanvasGlyph glyph : line) {
            if (glyph.x() < x && (left == null || glyph.x() > left.x())) {
                left = glyph;
            }
        }
        return left;
    }

    private CanvasGlyph glyphContainingX(
            List<CanvasGlyph> line,
            double x
    ) {
        for (CanvasGlyph glyph : line) {
            if (x > glyph.x() && x < glyph.x() + glyph.width()) {
                return glyph;
            }
        }
        return null;
    }

    private CanvasGlyph leftMostGlyphAtOrAfter(
            List<CanvasGlyph> line,
            double x
    ) {
        for (CanvasGlyph glyph : line) {
            if (glyph.x() >= x) {
                return glyph;
            }
        }
        return null;
    }

    private CanvasGlyph leftMostGlyphAfter(
            List<CanvasGlyph> line,
            double x
    ) {
        for (CanvasGlyph glyph : line) {
            if (glyph.x() > x) {
                return glyph;
            }
        }
        return null;
    }

    private CanvasGlyph leftMostGlyphOnLine(CanvasGlyph nearest) {
        CanvasGlyph leftMost = nearest;
        for (CanvasGlyph glyph : glyphs) {
            if (Double.compare(glyph.y(), nearest.y()) == 0 && glyph.x() < leftMost.x()) {
                leftMost = glyph;
            }
        }
        return leftMost;
    }

    private void moveCursorToEndOfLine(List<CanvasGlyph> line) {
        if (line.isEmpty()) {
            return;
        }
        CanvasGlyph rightMost = line.get(line.size() - 1);
        setCursor(rightMost.x() + rightMost.width(), rightMost.y());
    }

    private boolean moveCursorToEndOfPreviousLine(double y) {
        Double previousY = previousGlyphRow(y);
        if (previousY == null) {
            return false;
        }
        moveCursorToEndOfLine(glyphsOnLine(previousY));
        return true;
    }

    private CanvasGlyph rightMostGlyphOnPreviousLine(double y) {
        Double previousY = previousGlyphRow(y);
        if (previousY == null) {
            return null;
        }
        List<CanvasGlyph> line = glyphsOnLine(previousY);
        return line.isEmpty() ? null : line.get(line.size() - 1);
    }

    private boolean moveCursorToStartOfNextLine(double y) {
        CanvasGlyph first = firstGlyphOnNextLine(y);
        if (first != null) {
            setCursor(first.x(), first.y());
            return true;
        }
        return false;
    }

    private CanvasGlyph firstGlyphOnNextLine(double y) {
        return firstGlyphOnNextLine(y, List.of());
    }

    private CanvasGlyph firstGlyphOnNextLine(
            double y,
            List<CanvasGlyph> excluded
    ) {
        Double nextY = nextGlyphRow(y, excluded);
        if (nextY == null) {
            return null;
        }
        List<CanvasGlyph> line = glyphsOnLine(nextY, excluded);
        return line.isEmpty() ? null : line.get(0);
    }

    private CanvasGlyph firstGlyphAfterOnLine(
            double y,
            double x,
            List<CanvasGlyph> excluded
    ) {
        CanvasGlyph first = null;
        for (CanvasGlyph glyph : glyphs) {
            if (excluded.contains(glyph)) {
                continue;
            }
            if (Double.compare(glyph.y(), y) == 0 && glyph.x() > x && (first == null || glyph.x() < first.x())) {
                first = glyph;
            }
        }
        return first;
    }

    private List<CanvasGlyph> contiguousGlyphRun(CanvasGlyph target) {
        List<CanvasGlyph> line = glyphsOnLine(target.y());
        int targetIndex = line.indexOf(target);
        if (targetIndex == -1) {
            return List.of();
        }
        boolean word = isWordGlyph(target);
        int start = targetIndex;
        while (start > 0
               && isWordGlyph(line.get(start - 1)) == word
               && glyphsTouch(line.get(start - 1), line.get(start))) {
            start--;
        }
        int end = targetIndex;
        while (end < line.size() - 1
               && isWordGlyph(line.get(end + 1)) == word
               && glyphsTouch(line.get(end), line.get(end + 1))) {
            end++;
        }
        return new ArrayList<>(line.subList(start, end + 1));
    }

    private boolean glyphsTouch(
            CanvasGlyph left,
            CanvasGlyph right
    ) {
        return Double.compare(left.x() + left.width(), right.x()) == 0;
    }

    private boolean isWordGlyph(CanvasGlyph glyph) {
        if (glyph.text().isEmpty()) {
            return false;
        }
        int codePoint = glyph.text().codePointAt(0);
        return Character.isLetterOrDigit(codePoint) || codePoint == '_';
    }

    private Double previousGlyphRow(double y) {
        Double previousY = null;
        for (CanvasGlyph glyph : glyphs) {
            if (glyph.y() < y && (previousY == null || glyph.y() > previousY)) {
                previousY = glyph.y();
            }
        }
        return previousY;
    }

    private double currentOrPreviousGlyphRow(double y) {
        if (!glyphsOnLine(y).isEmpty()) {
            return y;
        }
        Double previousY = previousGlyphRow(y);
        return previousY == null ? y : previousY;
    }

    private Double nextGlyphRow(double y) {
        return nextGlyphRow(y, List.of());
    }

    private Double nextGlyphRow(
            double y,
            List<CanvasGlyph> excluded
    ) {
        Double nextY = null;
        for (CanvasGlyph glyph : glyphs) {
            if (!excluded.contains(glyph) && glyph.y() > y && (nextY == null || glyph.y() < nextY)) {
                nextY = glyph.y();
            }
        }
        return nextY;
    }

    private void moveCursorVertically(
            int direction,
            int lineHeight,
            boolean snapToGlyph
    ) {
        CursorPoint target = verticalTarget(focusedCursor(), direction, lineHeight, snapToGlyph);
        setCursor(target.x(), target.y());
    }

    private CursorPoint verticalTarget(
            CanvasCursor cursor,
            int direction,
            int lineHeight,
            boolean snapToGlyph
    ) {
        Double targetY = null;
        for (CanvasGlyph glyph : glyphs) {
            boolean candidate = direction < 0 ? glyph.y() < cursor.y() : glyph.y() > cursor.y();
            boolean better = targetY == null || (direction < 0 ? glyph.y() > targetY : glyph.y() < targetY);
            if (candidate && better) {
                targetY = glyph.y();
            }
        }
        if (targetY == null) {
            return new CursorPoint(cursor.x(), cursor.y() + direction * Math.max(1, lineHeight));
        }
        double gapStart = direction < 0 ? targetY + lineHeight : cursor.y() + lineHeight;
        double gapEnd = direction < 0 ? cursor.y() : targetY;
        if (!snapToGlyph && gapEnd - gapStart >= lineHeight) {
            return new CursorPoint(cursor.x(), cursor.y() + direction * lineHeight);
        }
        List<CanvasGlyph> line = glyphsOnLine(targetY);
        CanvasGlyph target = nearestGlyphByX(line, cursor.x());
        if (target != null) {
            return new CursorPoint(target.x(), target.y());
        }
        return new CursorPoint(cursor.x(), cursor.y() + direction * Math.max(1, lineHeight));
    }

    private CanvasGlyph nearestGlyphByX(
            List<CanvasGlyph> line,
            double x
    ) {
        CanvasGlyph nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (CanvasGlyph glyph : line) {
            double distance = Math.abs(glyph.x() - x);
            if (distance < nearestDistance) {
                nearest = glyph;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    private CanvasGlyph topmostThenLeftmostGlyph() {
        CanvasGlyph first = null;
        for (CanvasGlyph glyph : glyphs) {
            if (first == null
                || glyph.y() < first.y()
                || (Double.compare(glyph.y(), first.y()) == 0 && glyph.x() < first.x())) {
                first = glyph;
            }
        }
        return first;
    }

    private CanvasGlyph bottommostThenRightmostGlyph() {
        CanvasGlyph last = null;
        for (CanvasGlyph glyph : glyphs) {
            if (last == null
                || glyph.y() > last.y()
                || (Double.compare(glyph.y(), last.y()) == 0 && glyph.x() > last.x())) {
                last = glyph;
            }
        }
        return last;
    }

    public record CanvasGlyph(
            String text,
            double x,
            double y,
            int width
    ) {
    }

    private record CursorTarget(
            CanvasCursor cursor,
            CanvasGlyph target
    ) {
    }

    private record WordCursorTarget(
            CanvasCursor cursor,
            double y,
            double leftX,
            double rightX
    ) {
    }

    private record LineBreakShift(
            double y,
            boolean includeRow
    ) {
    }

    private record CursorLineBreakTarget(
            CanvasCursor cursor,
            double originalY,
            double lineStartX,
            boolean insertBeforeRow
    ) {
    }

    private record CursorPoint(
            double x,
            double y
    ) {
    }

    public static class CanvasCursor {
        private double x;
        private double y;
        private final int color;
        private boolean active;

        public CanvasCursor(
                double x,
                double y,
                int color,
                boolean active
        ) {
            this.x = x;
            this.y = y;
            this.color = color;
            this.active = active;
        }

        public double x() {
            return x;
        }

        public double y() {
            return y;
        }

        public int color() {
            return color;
        }

        public boolean active() {
            return active;
        }

        public void setX(double x) {
            this.x = x;
        }

        public void setY(double y) {
            this.y = y;
        }

        public void set(
                double x,
                double y
        ) {
            this.x = x;
            this.y = y;
        }

        public void move(
                double deltaX,
                double deltaY
        ) {
            this.x += deltaX;
            this.y += deltaY;
        }

        public void setActive(boolean active) {
            this.active = active;
        }
    }

    private interface CursorOperation {
        void apply();
    }

    public interface GlyphWidthReader {
        int width(String text);
    }
}
