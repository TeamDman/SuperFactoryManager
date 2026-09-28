package ca.teamdman.sfm.client.screen;

import ca.teamdman.sfm.common.containermenu.TestBarrelTankContainerMenu;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import ca.teamdman.sfm.common.util.SFMResourceLocation;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

public class TestBarrelTankScreen extends AbstractContainerScreen<TestBarrelTankContainerMenu> {
    private static final Identifier BACKGROUND_TEXTURE_LOCATION = SFMResourceLocation.fromSFMPath(
            "textures/gui/container/manager.png"
    );

    public TestBarrelTankScreen(
            TestBarrelTankContainerMenu menu,
            Inventory inv,
            Component title
    ) {
        super(menu, inv, title);
    }

    @Override
    public void extractRenderState(
            GuiGraphicsExtractor graphics,
            int mx,
            int my,
            float partialTicks
    ) {
        this.extractTransparentBackground(graphics);
        super.extractRenderState(graphics, mx, my, partialTicks);
        this.extractTooltip(graphics, mx, my);
    }


    @Override
    protected void init() {
        super.init();
    }

    @Override
    protected void extractLabels(
            GuiGraphicsExtractor pGuiGraphics,
            int pMouseX,
            int pMouseY
    ) {
        // draw title
        super.extractLabels(pGuiGraphics, pMouseX, pMouseY);
    }

    @MCVersionDependentBehaviour
    @Override
    protected void extractTooltip(
            GuiGraphicsExtractor pGuiGraphics,
            int mx,
            int my
    ) {
        drawChildTooltips(pGuiGraphics, mx, my);

        // render hovered item
        super.extractTooltip(pGuiGraphics, mx, my);
    }

    @MCVersionDependentBehaviour
    private void drawChildTooltips(
            GuiGraphicsExtractor guiGraphics,
            int mx,
            int my
    ) {
        // 1.19.2: manually render button tooltips
//        this.renderables
//                .stream()
//                .filter(SFMExtendedButtonWithTooltip.class::isInstance)
//                .map(SFMExtendedButtonWithTooltip.class::cast)
//                .forEach(x -> x.renderToolTip(pose, mx, my));
    }

    @Override
    public void extractBackground(
            GuiGraphicsExtractor guiGraphics,
            int mx,
            int my,
            float partialTicks
    ) {
        int i = (this.width - this.imageWidth) / 2;
        int j = (this.height - this.imageHeight) / 2;
        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND_TEXTURE_LOCATION, i, j, 0f, 0f, this.imageWidth, this.imageHeight, 256, 256);

        var fluidStack = menu.tank.getResource(0).toStack(menu.tank.getAmountAsInt(0));
        if (!fluidStack.isEmpty()) {
            var fluidModel = this.getMinecraft().getModelManager().getFluidStateModelSet().get(fluidStack.getFluid().defaultFluidState());
            TextureAtlasSprite fluidSprite = fluidModel.flowingMaterial().sprite();
            int fluidColour = fluidModel.fluidTintSource() != null ? fluidModel.fluidTintSource().colorAsStack(fluidStack) : -1;
            guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, fluidSprite, i + 80, j + 20, 16, 16, fluidColour);
        }
    }
}
