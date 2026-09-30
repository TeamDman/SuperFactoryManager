package ca.teamdman.sfm.client.screen;

{% case minecraft_version %}
{% when '1.19.2' %}
import ca.teamdman.sfm.client.screen.widget.SFMExtendedButtonWithTooltip;
{% when '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
{% endcase %}
import ca.teamdman.sfm.common.containermenu.TestBarrelTankContainerMenu;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import ca.teamdman.sfm.common.util.SFMResourceLocation;
{% case minecraft_version %}
{% when '1.19.2' %}
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Matrix4f;
{% when '1.19.4' %}
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
{% when '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.gui.GuiGraphics;
{% when '26.1.2' %}
import net.minecraft.client.gui.GuiGraphicsExtractor;
{% endcase %}
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
{% when '26.1.2' %}
import net.minecraft.client.renderer.RenderPipelines;
{% endcase %}
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor.ARGB32;
{% when '26.1.2' %}
import net.minecraft.resources.Identifier;
{% endcase %}
import net.minecraft.world.entity.player.Inventory;
{% case minecraft_version %}
{% when '1.19.2' %}
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;
{% when '1.19.4', '1.20', '1.20.1' %}
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;
import org.joml.Matrix4f;
{% when '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;
import org.joml.Matrix4f;
{% when '26.1.2' %}
{% endcase %}

public class TestBarrelTankScreen extends AbstractContainerScreen<TestBarrelTankContainerMenu> {
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    private static final ResourceLocation BACKGROUND_TEXTURE_LOCATION = SFMResourceLocation.fromSFMPath(
{% when '26.1.2' %}
    private static final Identifier BACKGROUND_TEXTURE_LOCATION = SFMResourceLocation.fromSFMPath(
{% endcase %}
            "textures/gui/container/manager.png"
    );

    public TestBarrelTankScreen(
            TestBarrelTankContainerMenu menu,
            Inventory inv,
            Component title
    ) {
        super(menu, inv, title);
    }

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20' %}
    @SuppressWarnings({"deprecation", "resource"})
{% when '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    @SuppressWarnings({"deprecation"})
{% when '26.1.2' %}
{% endcase %}
    @Override
{% case minecraft_version %}
{% when '1.19.2', '1.19.4' %}
    public void render(
            PoseStack poseStack,
{% when '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    public void render(
            GuiGraphics graphics,
{% when '26.1.2' %}
    public void extractRenderState(
            GuiGraphicsExtractor graphics,
{% endcase %}
            int mx,
            int my,
            float partialTicks
    ) {
{% case minecraft_version %}
{% when '1.19.2', '1.19.4' %}
        this.renderBackground(poseStack);
        super.render(poseStack, mx, my, partialTicks);
        this.renderTooltip(poseStack, mx, my);

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
        Matrix4f matrix = poseStack.last().pose();

        vertexBuffer.vertex(matrix, 0f, 128f, 1f).uv(fluidSprite.getU0(), fluidSprite.getV1()).endVertex();
        vertexBuffer.vertex(matrix, 128f, 128f, 1f).uv(fluidSprite.getU1(), fluidSprite.getV1()).endVertex();
        vertexBuffer.vertex(matrix, 128f, 0f, 1f).uv(fluidSprite.getU1(), fluidSprite.getV0()).endVertex();
        vertexBuffer.vertex(matrix, 0f, 0f, 1f).uv(fluidSprite.getU0(), fluidSprite.getV0()).endVertex();
        BufferUploader.drawWithShader(vertexBuffer.end());
        RenderSystem.disableBlend();
{% when '1.20', '1.20.1' %}
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
{% when '1.20.2', '1.20.3', '1.20.4' %}
        this.renderTransparentBackground(graphics);
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
{% when '1.21', '1.21.1' %}
        this.renderTransparentBackground(graphics);
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

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder vertexBuffer = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        Matrix4f matrix = graphics.pose().last().pose();

        vertexBuffer.addVertex(matrix, 0f, 128f, 1f).setUv(fluidSprite.getU0(), fluidSprite.getV1());
        vertexBuffer.addVertex(matrix, 128f, 128f, 1f).setUv(fluidSprite.getU1(), fluidSprite.getV1());
        vertexBuffer.addVertex(matrix, 128f, 0f, 1f).setUv(fluidSprite.getU1(), fluidSprite.getV0());
        vertexBuffer.addVertex(matrix, 0f, 0f, 1f).setUv(fluidSprite.getU0(), fluidSprite.getV0());
        BufferUploader.drawWithShader(vertexBuffer.buildOrThrow());
        RenderSystem.disableBlend();
{% when '26.1.2' %}
        this.extractTransparentBackground(graphics);
        super.extractRenderState(graphics, mx, my, partialTicks);
        this.extractTooltip(graphics, mx, my);
{% endcase %}
    }


    @Override
    protected void init() {
        super.init();
    }

    @Override
{% case minecraft_version %}
{% when '1.19.2', '1.19.4' %}
    protected void renderLabels(
            PoseStack poseStack,
{% when '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
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
{% when '26.1.2' %}
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
{% endcase %}
            int mx,
            int my
    ) {
{% case minecraft_version %}
{% when '1.19.2', '1.19.4' %}
        // draw title
        super.renderLabels(poseStack, mx, my);
    }

    @Override
    protected void renderTooltip(
            PoseStack pose,
            int mx,
            int my
    ) {
        drawChildTooltips(pose, mx, my);
{% when '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
        drawChildTooltips(pGuiGraphics, mx, my);
{% endcase %}

        // render hovered item
{% case minecraft_version %}
{% when '1.19.2', '1.19.4' %}
        super.renderTooltip(pose, mx, my);
{% when '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        super.renderTooltip(pGuiGraphics, mx, my);
{% when '26.1.2' %}
        super.extractTooltip(pGuiGraphics, mx, my);
{% endcase %}
    }

    @MCVersionDependentBehaviour
    private void drawChildTooltips(
{% case minecraft_version %}
{% when '1.19.2', '1.19.4' %}
            PoseStack pose,
{% when '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
            GuiGraphics guiGraphics,
{% when '26.1.2' %}
            GuiGraphicsExtractor guiGraphics,
{% endcase %}
            int mx,
            int my
    ) {
        // 1.19.2: manually render button tooltips
{% case minecraft_version %}
{% when '1.19.2' %}
        this.renderables
                .stream()
                .filter(SFMExtendedButtonWithTooltip.class::isInstance)
                .map(SFMExtendedButtonWithTooltip.class::cast)
                .forEach(x -> x.renderToolTip(pose, mx, my));
{% when '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
//        this.renderables
//                .stream()
//                .filter(SFMExtendedButtonWithTooltip.class::isInstance)
//                .map(SFMExtendedButtonWithTooltip.class::cast)
//                .forEach(x -> x.renderToolTip(pose, mx, my));
{% endcase %}
    }

    @Override
{% case minecraft_version %}
{% when '1.19.2', '1.19.4' %}
    protected void renderBg(
            PoseStack matrixStack,
            float partialTicks,
{% when '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    protected void renderBg(
            GuiGraphics guiGraphics,
            float partialTicks,
{% when '26.1.2' %}
    public void extractBackground(
            GuiGraphicsExtractor guiGraphics,
{% endcase %}
            int mx,
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
            int my
{% when '26.1.2' %}
            int my,
            float partialTicks
{% endcase %}
    ) {
{% case minecraft_version %}
{% when '1.19.2', '1.19.4' %}
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, BACKGROUND_TEXTURE_LOCATION);
{% when '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
{% endcase %}
        int i = (this.width - this.imageWidth) / 2;
        int j = (this.height - this.imageHeight) / 2;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4' %}
        this.blit(matrixStack, i, j, 0, 0, this.imageWidth, this.imageHeight);
{% when '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        guiGraphics.blit(BACKGROUND_TEXTURE_LOCATION, i, j, 0, 0, this.imageWidth, this.imageHeight);
{% when '26.1.2' %}
        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND_TEXTURE_LOCATION, i, j, 0f, 0f, this.imageWidth, this.imageHeight, 256, 256);

        var fluidStack = menu.tank.getResource(0).toStack(menu.tank.getAmountAsInt(0));
        if (!fluidStack.isEmpty()) {
            var fluidModel = this.getMinecraft().getModelManager().getFluidStateModelSet().get(fluidStack.getFluid().defaultFluidState());
            TextureAtlasSprite fluidSprite = fluidModel.flowingMaterial().sprite();
            int fluidColour = fluidModel.fluidTintSource() != null ? fluidModel.fluidTintSource().colorAsStack(fluidStack) : -1;
            guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, fluidSprite, i + 80, j + 20, 16, 16, fluidColour);
        }
{% endcase %}
    }
}
