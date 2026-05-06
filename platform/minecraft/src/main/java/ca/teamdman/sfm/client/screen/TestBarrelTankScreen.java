package ca.teamdman.sfm.client.screen;

import ca.teamdman.sfm.common.containermenu.TestBarrelTankContainerMenu;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import ca.teamdman.sfm.common.util.SFMResourceLocation;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;
import org.joml.Matrix4f;

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

    @SuppressWarnings({"deprecation"})
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

        FluidStack fluidStack = new FluidStack(Fluids.WATER, 1000);
        IClientFluidTypeExtensions fluidType = IClientFluidTypeExtensions.of(fluidStack.getFluid());
        Identifier fluidSpriteLocation = fluidType.getFlowingTexture(fluidStack);
//        Identifier fluidSpriteLocation = fluidType.getStillTexture(fluidStack);
        TextureAtlasSprite fluidSprite = this.getMinecraft().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(fluidSpriteLocation);
        var fluidColour = IClientFluidTypeExtensions.of(fluidStack.getFluid()).getTintColor(fluidStack);
        RenderSystem.setShaderColor(ARGB.red(fluidColour)/255f, ARGB.green(fluidColour)/255f, ARGB.blue(fluidColour)/255f, ARGB.alpha(fluidColour)/255f);

        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_BLOCKS);
        RenderSystem.enableBlend();

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder vertexBuffer = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        Matrix4f matrix = graphics.pose().last().pose();

        vertexBuffer.addVertex(matrix, 0f, 128f, 1f).setUv(fluidSprite.getU0(), fluidSprite.getV1());
        vertexBuffer.addVertex(matrix, 128f, 128f, 1f).setUv(fluidSprite.getU1(), fluidSprite.getV1());
        vertexBuffer.addVertex(matrix, 128f, 0f, 1f).setUv(fluidSprite.getU1(), fluidSprite.getV0());
        vertexBuffer.addVertex(matrix, 0f, 0f, 1f).setUv(fluidSprite.getU0(), fluidSprite.getV0());
        BufferUploader.drawWithShader(vertexBuffer.buildOrThrow());
        RenderSystem.disableBlend();
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
        guiGraphics.blit(BACKGROUND_TEXTURE_LOCATION, i, j, 0, 0, this.imageWidth, this.imageHeight);
    }
}
