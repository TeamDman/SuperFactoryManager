package ca.teamdman.sfm.client.screen;

import ca.teamdman.sfm.common.containermenu.TestBarrelTankContainerMenu;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import ca.teamdman.sfm.common.util.SFMResourceLocation;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor.ARGB32;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;
import org.joml.Matrix4f;

public class TestBarrelTankScreen extends AbstractContainerScreen<TestBarrelTankContainerMenu> {
    private static final ResourceLocation BACKGROUND_TEXTURE_LOCATION = SFMResourceLocation.fromSFMPath(
            "textures/gui/container/manager.png"
    );

    public TestBarrelTankScreen(
            TestBarrelTankContainerMenu menu,
            Inventory inv,
            Component title
    ) {
        super(menu, inv, title);
    }

    @SuppressWarnings({"deprecation", "resource"})
    @Override
    public void render(
            GuiGraphics graphics,
            int mx,
            int my,
            float partialTicks
    ) {
        this.renderBackground(graphics);
        super.render(graphics, mx, my, partialTicks);
        this.renderTooltip(graphics, mx, my);

        FluidStack fluidStack = new FluidStack(Fluids.WATER, 1000);
        IClientFluidTypeExtensions fluidType = IClientFluidTypeExtensions.of(fluidStack.getFluid());
        ResourceLocation fluidSpriteLocation = fluidType.getFlowingTexture(fluidStack);
//        ResourceLocation fluidSpriteLocation = fluidType.getStillTexture(fluidStack);
        TextureAtlasSprite fluidSprite = this.getMinecraft().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(fluidSpriteLocation);
        var fluidColour = IClientFluidTypeExtensions.of(fluidStack.getFluid()).getTintColor(fluidStack);
        RenderSystem.setShaderColor(ARGB32.red(fluidColour)/255f, ARGB32.green(fluidColour)/255f, ARGB32.blue(fluidColour)/255f, ARGB32.alpha(fluidColour)/255f);

        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_BLOCKS);
        RenderSystem.enableBlend();

        BufferBuilder vertexBuffer = Tesselator.getInstance().getBuilder();
        vertexBuffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        Matrix4f matrix = graphics.pose().last().pose();

        vertexBuffer.vertex(matrix, 0f, 128f, 1f).uv(fluidSprite.getU0(), fluidSprite.getV1()).endVertex();
        vertexBuffer.vertex(matrix, 128f, 128f, 1f).uv(fluidSprite.getU1(), fluidSprite.getV1()).endVertex();
        vertexBuffer.vertex(matrix, 128f, 0f, 1f).uv(fluidSprite.getU1(), fluidSprite.getV0()).endVertex();
        vertexBuffer.vertex(matrix, 0f, 0f, 1f).uv(fluidSprite.getU0(), fluidSprite.getV0()).endVertex();
        BufferUploader.drawWithShader(vertexBuffer.end());
        RenderSystem.disableBlend();
    }


    @Override
    protected void init() {
        super.init();
    }

    @Override
    protected void renderLabels(
            GuiGraphics pGuiGraphics,
            int pMouseX,
            int pMouseY
    ) {
        // draw title
        super.renderLabels(pGuiGraphics, pMouseX, pMouseY);
    }

    @MCVersionDependentBehaviour
    @Override
    protected void renderTooltip(
            GuiGraphics pGuiGraphics,
            int mx,
            int my
    ) {
        drawChildTooltips(pGuiGraphics, mx, my);

        // render hovered item
        super.renderTooltip(pGuiGraphics, mx, my);
    }

    @MCVersionDependentBehaviour
    private void drawChildTooltips(
            GuiGraphics guiGraphics,
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
    protected void renderBg(
            GuiGraphics guiGraphics,
            float partialTicks,
            int mx,
            int my
    ) {
        int i = (this.width - this.imageWidth) / 2;
        int j = (this.height - this.imageHeight) / 2;
        guiGraphics.blit(BACKGROUND_TEXTURE_LOCATION, i, j, 0, 0, this.imageWidth, this.imageHeight);
    }
}
