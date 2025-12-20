package ca.teamdman.sfm.client.widget;

import java.util.*;
import java.util.stream.Collectors;

import ca.teamdman.sfm.client.screen.SFMFontUtils;
import ca.teamdman.sfm.client.screen.SFMScreenRenderUtils;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import ca.teamdman.sfm.common.util.Mth;
import com.bbscn.AbstractScrollWidget;
import com.bbscn.ScreenRectangle;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import org.jetbrains.annotations.Nullable;
import org.simmetrics.ListDistance;
import ca.teamdman.sfm.client.screen.SFMWidgetUtils;
import org.simmetrics.StringDistance;
import org.simmetrics.builders.StringDistanceBuilder;
import org.simmetrics.metrics.StringDistances;
import org.simmetrics.simplifiers.Simplifiers;

import java.util.Comparator;
import java.util.List;

import ca.teamdman.sfm.common.util.Pair;
import org.simmetrics.tokenizers.Tokenizers;

public class PickList<T extends PickListItem> extends AbstractScrollWidget {
    protected final FontRenderer font;
    protected List<T> items;
    protected List<T> sortedItems;
    protected int selectionIndex = -1;
    protected String query = "";
    protected boolean dirty = true;

    public PickList(
            FontRenderer font,
            int pX,
            int pY,
            int pWidth,
            int pHeight,
            ITextComponent title,
            List<T> items
    ) {
        super(pX, pY, pWidth, pHeight, title);
        this.font = font;
        this.items = items;
        this.sortedItems = Collections.emptyList();
        this.clampOrUnsetSelectionIndex();
    }

    public List<T> getItems() {
        return items;
    }

    public List<T> getSortedItems() {
        return sortedItems;
    }

    public void setItems(List<T> items) {
        this.items = items;
        sortItems();
        selectionIndex = 0;
        clampOrUnsetSelectionIndex();
        scrollSelectedIntoView();
    }

    public int getItemHeight() {
        return font.FONT_HEIGHT;
    }


    public @Nullable T getSelected() {
        if (sortedItems.isEmpty()) return null;
        if (selectionIndex < 0) return null;
        if (selectionIndex >= sortedItems.size()) return null;
        return sortedItems.get(selectionIndex);
    }

    public void setQuery(String query) {
        if (!query.equals(this.query)) {
            this.query = query;
            this.dirty = true;
        }
    }

    public void updateList() {
        dirty = false;
        sortItems();
        selectionIndex = 0;
        clampOrUnsetSelectionIndex();
        scrollSelectedIntoView();
    }

    public void setActive(boolean active) {
        this.active = active;
        if (active) {
            sortItems();
            selectionIndex = 0;
            clampOrUnsetSelectionIndex();
            scrollSelectedIntoView();
        }
    }

    @MCVersionDependentBehaviour
    public void setXY(
            int x,
            int y
    ) {
        this.setX(x);
        this.setY(y);
    }


    @Override
    public void renderWidget(
            int pMouseX,
            int pMouseY,
            float pPartialTick
    ) {
        if (sortedItems.isEmpty()) return;
        if (!active) return;
        if (this.dirty) {
            updateList();
        }
        super.renderWidget(pMouseX, pMouseY, pPartialTick);
    }

    public void selectPreviousWrapping() {
        if (this.sortedItems.isEmpty()) {
            return;
        }
        if (this.selectionIndex == -1) {
            this.selectionIndex = this.sortedItems.size() - 1;
            return;
        }
        this.selectionIndex = (this.selectionIndex - 1 + this.sortedItems.size()) % this.sortedItems.size();
        scrollSelectedIntoView();
    }

    public void selectNextWrapping() {
        if (this.selectionIndex == -1) {
            this.selectionIndex = 0;
            return;
        }
        this.selectionIndex = (this.selectionIndex + 1) % this.sortedItems.size();
        scrollSelectedIntoView();
    }

    public boolean isEmpty() {
        return this.sortedItems.isEmpty();
    }

    public void clear() {
        this.items.clear();
        this.sortedItems.clear();
        this.selectionIndex = -1;
    }

    private void clampOrUnsetSelectionIndex() {
        if (this.sortedItems.isEmpty()) {
            this.selectionIndex = -1;
        } else {
            this.selectionIndex = Mth.clamp(this.selectionIndex, 0, this.sortedItems.size() - 1);
        }
    }

    private void scrollSelectedIntoView() {
        if (this.isEmpty()) {
            this.setScrollAmount(0);
        } else {
            this.setScrollAmount(
                    this.selectionIndex * this.getItemHeight()
                            - this.height / 2.0f + this.getItemHeight()
            );
        }
    }

    private void sortItems() {
        StringDistance distance = StringDistanceBuilder
                .with(new ListDistance<String>() {
                    final StringDistance subDistance = StringDistances.jaroWinkler();

                    @Override
                    public float distance(List<String> a, List<String> b) {
                        List<String> shorter = a.size() < b.size() ? a : b;
                        List<String> longer = a.size() < b.size() ? b : a;

                        float[] scores = new float[longer.size()];
                        Arrays.fill(scores, -1);
                        for (int i = shorter.size() - 1; i >= 0; i--) {
                            float closestDistance = 2;
                            int closestIndex = -1;
                            for (int j = 0; j < longer.size(); j++) {
                                var distance = subDistance.distance(shorter.get(i), longer.get(j));
                                if (closestDistance > distance && scores[j] == -1) {
                                    closestDistance = distance;
                                    closestIndex = j;
                                }
                            }
                            scores[closestIndex] = closestDistance;
                        }

                        float result = 0;
                        for (float score : scores) {
                            if (score != -1) {
                                result += score;
                            }
                        }

                        return result / (shorter.size() - 0.2f);
                    }
                })
                .simplify(Simplifiers.toLowerCase())
                .tokenize(Tokenizers.pattern(":"))
                .filter(s -> !s.isEmpty())
                .build();


        String queryString = query;
        if (queryString.trim().isEmpty()) {
            var preferredOrder = new String[]{
                    "TICKS",
                    "INPUT",
                    "OUTPUT",
                    "FORGET",
                    "FROM",
                    "TO"
            };
            items.sort(Comparator.comparing(item -> {
                String itemString = item.getComponent().getUnformattedComponentText();
                for (int i = 0; i < preferredOrder.length; i++) {
                    if (itemString.contains(preferredOrder[i])) {
                        return i;
                    }
                }
                return preferredOrder.length;
            }));
        } else {
//            SFM.LOGGER.debug("Sorting by distance using query: {}", queryString);
            var bestOptionsHeap = new PriorityQueue<>(20, Comparator.comparing(Pair<Float, T>::getFirst).reversed());
            for (T item : items) {
                float d = distance.distance(item.getComponent().getUnformattedComponentText(), queryString);
                if (bestOptionsHeap.size() < 20) {
                    bestOptionsHeap.offer(new Pair<>(d, item));
                } else if (d < bestOptionsHeap.peek().getFirst()) {
                    bestOptionsHeap.poll();
                    bestOptionsHeap.offer(new Pair<>(d, item));
                }
            }

            sortedItems = bestOptionsHeap
                    .stream()
                    .sorted(Comparator.comparing(Pair::getFirst))
                    .map(Pair::getSecond)
                    .collect(Collectors.toList());
        }
    }

    @Override
    protected int getInnerHeight() {
        return getItemHeight() * sortedItems.size();
    }

    @Override
    protected boolean scrollbarVisible() {
        return this.sortedItems.size() > this.getDisplayableItemCount();
    }

    private double getDisplayableItemCount() {
        return (double) (this.height - this.totalInnerPadding()) / (double) getItemHeight();
    }


    @Override
    protected double scrollRate() {
        return this.getItemHeight() / 2.0d;
    }


    @Override
    protected void renderContents(
            int mx,
            int my,
            float partialTick
    ) {
        var tess = Tessellator.getInstance();

        if (sortedItems.isEmpty()) return;

        // Calculate which items are visible in the current viewport
        int itemHeight = getItemHeight();
        int startIndex = (int) (scrollAmount() / itemHeight);
        int visibleCount = (int) Math.ceil((double) height / itemHeight) + 1;
        int endIndex = Math.min(sortedItems.size(), startIndex + visibleCount);


        var buffer = Tessellator.getInstance().getBuffer();
        int lineX = SFMWidgetUtils.getX(this) + this.innerPadding();
        ScreenRectangle highlight = null;

        // Render only the visible subset of items
        for (int i = startIndex; i < endIndex; i++) {
            PickListItem item = sortedItems.get(i);
            // Calculate the y position based on the item's position in the full list
            int lineY = SFMWidgetUtils.getY(this) + this.innerPadding() + (i * itemHeight);

            SFMFontUtils.drawInBatch(
                    item.getComponent(),
                    this.font,
                    lineX,
                    lineY,
                    true,
                    false
            );

            if (i == this.selectionIndex) {
                highlight = new ScreenRectangle(
                        lineX,
                        lineY,
                        this.width,
                        itemHeight
                );
            }
        }

        if (highlight != null) {
            SFMScreenRenderUtils.renderHighlight(
                    highlight.getBoundInDirection(ScreenRectangle.ScreenDirection.LEFT),
                    highlight.getBoundInDirection(ScreenRectangle.ScreenDirection.UP),
                    highlight.getBoundInDirection(ScreenRectangle.ScreenDirection.RIGHT) + highlight.getWidth(),
                    highlight.getBoundInDirection(ScreenRectangle.ScreenDirection.DOWN)
            );
        }
    }

}
