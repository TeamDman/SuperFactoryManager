package ca.teamdman.sfm.client.screen.widget;

import ca.teamdman.sfm.client.screen.SFMFontUtils;
import ca.teamdman.sfm.client.screen.SFMScreenRenderUtils;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
import ca.teamdman.sfm.client.screen.SFMWidgetUtils;
{% when '26.1.2' %}
{% endcase %}
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
{% case minecraft_version %}
{% when '1.19.2' %}
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.math.Matrix4f;
{% when '1.19.4' %}
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
{% when '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
import com.mojang.blaze3d.vertex.Tesselator;
{% when '1.21', '1.21.1', '26.1.2' %}
{% endcase %}
import net.minecraft.client.gui.Font;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4' %}
import net.minecraft.client.gui.components.AbstractScrollWidget;
{% when '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractScrollWidget;
{% when '26.1.2' %}
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractScrollArea;
{% endcase %}
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
import net.minecraft.client.renderer.MultiBufferSource;
{% when '1.21', '1.21.1' %}
{% when '26.1.2' %}
import net.minecraft.client.input.MouseButtonEvent;
{% endcase %}
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
{% case minecraft_version %}
{% when '1.19.2', '26.1.2' %}
{% when '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
import org.joml.Matrix4f;
{% endcase %}
import org.simmetrics.StringDistance;
import org.simmetrics.builders.StringDistanceBuilder;
import org.simmetrics.metrics.StringDistances;
import org.simmetrics.simplifiers.Simplifiers;

import java.util.Comparator;
import java.util.List;

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
public class PickList<T extends PickListItem> extends AbstractScrollWidget {
{% when '26.1.2' %}
public class PickList<T extends PickListItem> extends AbstractScrollArea {
{% endcase %}
    protected final Font font;
    protected List<T> items;
    protected int selectionIndex = -1;
    protected Component query = Component.empty();

    public PickList(
            Font font,
            int pX,
            int pY,
            int pWidth,
            int pHeight,
            Component title,
            List<T> items
    ) {
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        super(pX, pY, pWidth, pHeight, title);
{% when '26.1.2' %}
        super(pX, pY, pWidth, pHeight, title, defaultSettings((int) (font.lineHeight / 2.0d)));
{% endcase %}
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
        return font.lineHeight;
    }

    public @Nullable T getSelected() {
        if (items.isEmpty()) return null;
        if (selectionIndex < 0) return null;
        if (selectionIndex >= items.size()) return null;
        return getItems().get(selectionIndex);
    }

    public void setQuery(Component query) {
        this.query = query;
        sortItems();
        selectionIndex = 0;
        clampOrUnsetSelectionIndex();
        scrollSelectedIntoView();
    }

    @MCVersionDependentBehaviour
    public void setXY(
            int x,
            int y
    ) {
{% case minecraft_version %}
{% when '1.19.2' %}
        this.x = x;
        this.y = y;
{% when '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
        this.setX(x);
        this.setY(y);
{% endcase %}
    }

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
{% when '26.1.2' %}
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        boolean scrolling = this.updateScrolling(event);
        return super.mouseClicked(event, doubleClick) || scrolling;
    }

{% endcase %}
    @Override
    @MCVersionDependentBehaviour
{% case minecraft_version %}
{% when '1.19.2' %}
    public void updateNarration(NarrationElementOutput narration) {
{% when '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
    protected void updateWidgetNarration(NarrationElementOutput narration) {
{% endcase %}
        narration.add(NarratedElementType.TITLE, getMessage());
    }
{% case minecraft_version %}
{% when '1.19.2', '1.19.4' %}

    @Override
    public void render(
            PoseStack pPoseStack,
            int pMouseX,
            int pMouseY,
            float pPartialTick
    ) {
        if (items.isEmpty()) return;
        super.render(pPoseStack, pMouseX, pMouseY, pPartialTick);
{% when '1.20', '1.20.1', '1.20.2' %}

    @Override
    public void render(
            GuiGraphics graphics,
            int pMouseX,
            int pMouseY,
            float pPartialTick
    ) {
        if (items.isEmpty()) return;
        super.render(graphics, pMouseX, pMouseY, pPartialTick);
    }

    @Override
    @MCVersionDependentBehaviour
    public void renderWidget(
            GuiGraphics graphics,
            int pMouseX,
            int pMouseY,
            float pPartialTick
    ) {
        if (items.isEmpty()) return;

        graphics.pose().pushPose();
        // Fixes https://github.com/TeamDman/SuperFactoryManager/issues/518
        // Adjust the Z-index such that the popup renders on top of the editor text
        graphics.pose().translate(0.0F, 0.0F, 400.0F);

        super.renderWidget(graphics, pMouseX, pMouseY, pPartialTick);

        graphics.pose().popPose();
{% when '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    
    @Override
    @MCVersionDependentBehaviour
    public void renderWidget(
            GuiGraphics graphics,
            int pMouseX,
            int pMouseY,
            float pPartialTick
    ) {
        if (items.isEmpty()) return;

        graphics.pose().pushPose();
        // Fixes https://github.com/TeamDman/SuperFactoryManager/issues/518
        // Adjust the Z-index such that the popup renders on top of the editor text
        graphics.pose().translate(0.0F, 0.0F, 400.0F);

        super.renderWidget(graphics, pMouseX, pMouseY, pPartialTick);

        graphics.pose().popPose();
{% when '26.1.2' %}

    @Override
    @MCVersionDependentBehaviour
    public void extractWidgetRenderState(
            GuiGraphicsExtractor graphics,
            int pMouseX,
            int pMouseY,
            float pPartialTick
    ) {
        if (items.isEmpty() || !this.visible) return;

        // Fixes https://github.com/TeamDman/SuperFactoryManager/issues/518
        // Adjust the Z-index such that the popup renders on top of the editor text
        graphics.nextStratum();

        // Render background
        graphics.fill(this.getX(), this.getY(), this.getRight(), this.getBottom(), 0xAA000000);

        graphics.enableScissor(this.getX() + 1, this.getY() + 1, this.getX() + this.width - 1, this.getY() + this.height - 1);
        graphics.pose().pushMatrix();
        graphics.pose().translate(0.0F, (float) (-this.scrollAmount()));
        this.extractContents(graphics, pMouseX, pMouseY, pPartialTick);
        graphics.pose().popMatrix();
        graphics.disableScissor();

        this.extractScrollbar(graphics, pMouseX, pMouseY);
{% endcase %}
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
                    this.selectionIndex * this.getItemHeight()
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
                    - this.height / 2.0f + this.getItemHeight()
{% when '26.1.2' %}
                            - this.height / 2.0f + this.getItemHeight()
{% endcase %}
            );
        }
    }

    private void sortItems() {
        StringDistance distance = StringDistanceBuilder
                .with(StringDistances.jaroWinkler())
                .simplify(Simplifiers.toLowerCase())
                .build();
        String queryString = query.getString();
        if (queryString.isBlank()) {
            var preferredOrder = new String[]{
                    "TICKS",
                    "INPUT",
                    "OUTPUT",
                    "FORGET",
                    "FROM",
                    "TO"
            };
            items.sort(Comparator.comparing(item -> {
                String itemString = item.getComponent().getString();
                for (int i = 0; i < preferredOrder.length; i++) {
                    if (itemString.contains(preferredOrder[i])) {
                        return i;
                    }
                }
                return preferredOrder.length;
            }));
        } else {
//            SFM.LOGGER.debug("Sorting by distance using query: {}", queryString);
            items.sort(Comparator.comparing(item -> distance.distance(
                    item.getComponent().getString(),
                    queryString
            )));
        }
    }

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    @Override
    protected int getInnerHeight() {
        return getItemHeight() * items.size();
{% when '26.1.2' %}
    protected int innerPadding() {
        return 4;
    }

    protected int totalInnerPadding() {
        return this.innerPadding() * 2;
{% endcase %}
    }

    @Override
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    protected boolean scrollbarVisible() {
        return this.items.size() > this.getDisplayableItemCount();
    }

    private double getDisplayableItemCount() {
        return (double) (this.height - this.totalInnerPadding()) / (double) getItemHeight();
{% when '26.1.2' %}
    protected int contentHeight() {
        return getItemHeight() * items.size() + totalInnerPadding();
{% endcase %}
    }


{% case minecraft_version %}
{% when '1.19.2', '1.19.4' %}
    @Override
    protected double scrollRate() {
        return this.getItemHeight() / 2.0d;
    }


    @Override
    protected void renderContents(
            PoseStack poseStack,
{% when '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    @Override
    protected double scrollRate() {
        return this.getItemHeight() / 2.0d;
    }


    @Override
    protected void renderContents(
            GuiGraphics graphics,
{% when '26.1.2' %}
    protected void extractContents(
            GuiGraphicsExtractor graphics,
{% endcase %}
            int mx,
            int my,
            float partialTick
    ) {
        if (items.isEmpty()) return;

        // Calculate which items are visible in the current viewport
        int itemHeight = getItemHeight();
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        int startIndex = (int)(scrollAmount() / itemHeight);
        int visibleCount = (int)Math.ceil((double)height / itemHeight) + 1;
{% when '26.1.2' %}
        int startIndex = (int) (scrollAmount() / itemHeight);
        int visibleCount = (int) Math.ceil((double) height / itemHeight) + 1;
{% endcase %}
        int endIndex = Math.min(items.size(), startIndex + visibleCount);

{% case minecraft_version %}
{% when '1.19.2', '1.19.4' %}
        // Only render the visible items
        Matrix4f matrix4f = poseStack.last().pose();
        var buffer = MultiBufferSource.immediate(Tesselator.getInstance().getBuilder());
        int lineX = SFMWidgetUtils.getX(this) + this.innerPadding();
{% when '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
        // Only render the visible items
        Matrix4f matrix4f = graphics.pose().last().pose();
        var buffer = MultiBufferSource.immediate(Tesselator.getInstance().getBuilder());
        int lineX = SFMWidgetUtils.getX(this) + this.innerPadding();
{% when '1.21', '1.21.1' %}
        // Only render the visible items
        Matrix4f matrix4f = graphics.pose().last().pose();
        var buffer = graphics.bufferSource();
        int lineX = SFMWidgetUtils.getX(this) + this.innerPadding();
{% when '26.1.2' %}
        int lineX = this.getX() + this.innerPadding();
{% endcase %}
        Rect2i highlight = null;

        // Render only the visible subset of items
        for (int i = startIndex; i < endIndex; i++) {
            PickListItem item = items.get(i);
            // Calculate the y position based on the item's position in the full list
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
            int lineY = SFMWidgetUtils.getY(this) + this.innerPadding() + (i * itemHeight);
{% when '26.1.2' %}
            int lineY = this.getY() + this.innerPadding() + (i * itemHeight);
{% endcase %}

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
            SFMFontUtils.drawInBatch(
                    item.getComponent(),
{% when '26.1.2' %}
            SFMFontUtils.draw(
                    graphics,
{% endcase %}
                    this.font,
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
{% when '26.1.2' %}
                    item.getComponent(),
{% endcase %}
                    lineX,
                    lineY,
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
                    true,
                    false,
                    matrix4f,
                    buffer
{% when '26.1.2' %}
                    -1,
                    true
{% endcase %}
            );

            if (i == this.selectionIndex) {
                highlight = new Rect2i(
                        lineX,
                        lineY,
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
                        this.width,
{% when '26.1.2' %}
                        this.width - this.totalInnerPadding(),
{% endcase %}
                        itemHeight
                );
            }
        }

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        buffer.endBatch();

{% when '26.1.2' %}
{% endcase %}
        if (highlight != null) {
            SFMScreenRenderUtils.renderHighlight(
{% case minecraft_version %}
{% when '1.19.2', '1.19.4' %}
                    poseStack,
{% when '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
                    graphics,
{% endcase %}
                    highlight.getX(),
                    highlight.getY(),
                    highlight.getX() + highlight.getWidth(),
                    highlight.getY() + highlight.getHeight()
            );
        }
    }

}
