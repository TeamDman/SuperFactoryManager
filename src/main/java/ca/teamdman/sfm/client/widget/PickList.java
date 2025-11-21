package ca.teamdman.sfm.client.widget;

import java.util.Comparator;
import java.util.List;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;

import org.jetbrains.annotations.Nullable;
import org.simmetrics.StringDistance;
import org.simmetrics.builders.StringDistanceBuilder;
import org.simmetrics.metrics.StringDistances;
import org.simmetrics.simplifiers.Simplifiers;

import com.bbscn.AbstractScrollWidget;
import com.bbscn.ScreenRectangle;

import ca.teamdman.sfm.client.screen.SFMFontUtils;
import ca.teamdman.sfm.client.screen.SFMScreenRenderUtils;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import ca.teamdman.sfm.common.util.Mth;

public class PickList<T extends PickListItem> extends AbstractScrollWidget {

    protected final FontRenderer font;
    protected List<T> items;
    protected int selectionIndex = -1;
    protected ITextComponent query = new TextComponentString("");

    public PickList(
                    FontRenderer font,
                    int pX,
                    int pY,
                    int pWidth,
                    int pHeight,
                    ITextComponent title,
                    List<T> items) {
        super(pX, pY, pWidth, pHeight, title);
        this.font = font;
        this.items = items;
        this.clampOrUnsetSelectionIndex();
    }

    public List<T> getItems() {
        return items;
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
        if (items.isEmpty()) return null;
        if (selectionIndex < 0) return null;
        if (selectionIndex >= items.size()) return null;
        return getItems().get(selectionIndex);
    }

    public void setQuery(ITextComponent query) {
        this.query = query;
        sortItems();
        selectionIndex = 0;
        clampOrUnsetSelectionIndex();
        scrollSelectedIntoView();
    }

    @MCVersionDependentBehaviour
    public void setXY(
                      int x,
                      int y) {
        this.setX(x);
        this.setY(y);
    }

    @Override
    public void renderWidget(
                             int pMouseX,
                             int pMouseY,
                             float pPartialTick) {
        if (items.isEmpty()) return;
        super.renderWidget(pMouseX, pMouseY, pPartialTick);
    }

    public void selectPreviousWrapping() {
        if (this.selectionIndex == -1) {
            this.selectionIndex = this.items.size() - 1;
            return;
        }
        this.selectionIndex = (this.selectionIndex - 1 + this.items.size()) % this.items.size();
        scrollSelectedIntoView();
    }

    public void selectNextWrapping() {
        if (this.selectionIndex == -1) {
            this.selectionIndex = 0;
            return;
        }
        this.selectionIndex = (this.selectionIndex + 1) % this.items.size();
        scrollSelectedIntoView();
    }

    public boolean isEmpty() {
        return this.items.isEmpty();
    }

    public void clear() {
        this.items.clear();
        this.selectionIndex = -1;
    }

    private void clampOrUnsetSelectionIndex() {
        if (this.items.isEmpty()) {
            this.selectionIndex = -1;
        } else {
            this.selectionIndex = Mth.clamp(this.selectionIndex, 0, this.items.size() - 1);
        }
    }

    private void scrollSelectedIntoView() {
        if (this.isEmpty()) {
            this.setScrollAmount(0);
        } else {
            this.setScrollAmount(
                    this.selectionIndex * this.getItemHeight() - this.height / 2.0f + this.getItemHeight());
        }
    }

    private void sortItems() {
        StringDistance distance = StringDistanceBuilder
                .with(StringDistances.jaroWinkler())
                .simplify(Simplifiers.toLowerCase())
                .build();
        String queryString = query.getUnformattedText();
        if (queryString.trim().isEmpty()) {
            var preferredOrder = new String[] {
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
            // SFM.LOGGER.debug("Sorting by distance using query: {}", queryString);
            items.sort(Comparator.comparing(item -> distance.distance(
                    item.getComponent().getUnformattedComponentText(),
                    queryString)));
        }
    }

    @Override
    protected int getInnerHeight() {
        return getItemHeight() * items.size();
    }

    @Override
    protected boolean scrollbarVisible() {
        return this.items.size() > this.getDisplayableItemCount();
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
                                  float partialTick) {
        var tess = Tessellator.getInstance();

        if (items.isEmpty()) return;

        // Calculate which items are visible in the current viewport
        int itemHeight = getItemHeight();
        int startIndex = (int) (scrollAmount() / itemHeight);
        int visibleCount = (int) Math.ceil((double) height / itemHeight) + 1;
        int endIndex = Math.min(items.size(), startIndex + visibleCount);

        var buffer = Tessellator.getInstance().getBuffer();
        int lineX = SFMScreenRenderUtils.getX(this) + this.innerPadding();
        ScreenRectangle highlight = null;

        // Render only the visible subset of items
        for (int i = startIndex; i < endIndex; i++) {
            PickListItem item = items.get(i);
            // Calculate the y position based on the item's position in the full list
            int lineY = SFMScreenRenderUtils.getY(this) + this.innerPadding() + (i * itemHeight);

            SFMFontUtils.drawInBatch(
                    item.getComponent(),
                    this.font,
                    lineX,
                    lineY,
                    true,
                    false);

            if (i == this.selectionIndex) {
                highlight = new ScreenRectangle(
                        lineX,
                        lineY,
                        this.width,
                        itemHeight);
            }
        }

        if (highlight != null) {
            SFMScreenRenderUtils.renderHighlight(
                    highlight.getBoundInDirection(ScreenRectangle.ScreenDirection.LEFT),
                    highlight.getBoundInDirection(ScreenRectangle.ScreenDirection.UP),
                    highlight.getBoundInDirection(ScreenRectangle.ScreenDirection.RIGHT) + highlight.getWidth(),
                    highlight.getBoundInDirection(ScreenRectangle.ScreenDirection.DOWN));
        }
    }
}
