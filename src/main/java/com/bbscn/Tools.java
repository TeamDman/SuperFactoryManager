package com.bbscn;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.capability.*;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.realmsclient.gui.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.util.text.event.HoverEvent;
import net.minecraftforge.common.capabilities.Capability;
import org.lwjgl.opengl.GL11;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * class copied frombbscncom's unofficial 1.12 backport of sfm4 <a href="https://github.com/bbscncom/SuperFactoryManagerCodeVersion112Unoffical">here</a>
 */
public class Tools {

    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final FontRenderer fontRenderer = mc.fontRenderer;
    private static final String ELLIPSIS = "...";

    public static <T> T getOrDefault(ItemStack stack, Capability<T> cap, T result) {
        T capability = stack.getCapability(cap, null);
        if (capability == null) {
            return result;
        }
        return capability;
//        try {
//            Class<?> clazz = capability.getClass();
//            Field dataField = clazz.getDeclaredField("data");
//            Object dataValue = dataField.get(capability);
//            if (dataValue != null) {
//                return (T) dataValue;
//            }
//        } catch (NoSuchFieldException | IllegalAccessException e) {
//            e.printStackTrace();
//        }
//        return result;
    }

    public static String strip(String s) {
        if (s == null || s.isEmpty()) {
            return s; // 如果字符串为空或null，直接返回
        }
        return s.replaceAll("^\\s+|\\s+$", ""); // 去除开头和结尾的空白字符
    }

    public static String stripLeading(String s) {
        if (s == null || s.isEmpty()) {
            return s; // 如果字符串为空或null，直接返回
        }
        return s.replaceAll("^\\s+", ""); // 仅去除开头的空白字符
    }

    public static String truncate(
            String input,
            int maxLength
    ) {
        if (input.length() > maxLength) {
            SFM.LOGGER.warn(
                    "input too big, truncation has occurred! (len={}, max={}, over={})",
                    input.length(),
                    maxLength,
                    maxLength - input.length()
            );
            String truncationWarning = "\n...truncated";
            return input.substring(0, maxLength - truncationWarning.length()) + truncationWarning;
        }
        return input;
    }

    public static int clamp(double value, double min, double max) {
        if (value < min) return (int) min;
        if (value > max) return (int) max;
        return (int) value;
    }


    public static boolean isAllowedChatCharacter(char pCharacter) {
        return pCharacter != 167 && pCharacter >= ' ' && pCharacter != 127;
    }

    public static String truncateStringIfNecessary(String pString, int pMaxSize, boolean pAddEllipsis) {
        if (pString.length() <= pMaxSize) {
            return pString;
        } else {
            return pAddEllipsis && pMaxSize > 3 ? pString.substring(0, pMaxSize - 3) + "..." : pString.substring(0, pMaxSize);
        }
    }


    public static Set<Integer> ofset(int ruleResourceId, int ruleLabel) {
        HashSet<Integer> integers = new HashSet<>();
        integers.add(ruleResourceId);
        integers.add(ruleLabel);
        return integers;
    }

    public static double lerp(double pDelta, double pStart, double pEnd) {
        return pStart + pDelta * (pEnd - pStart);
    }

    public static ITextComponent formatList(Collection<? extends ITextComponent> pElements, ITextComponent pSeparator) {
        return formatList(pElements, pSeparator, Function.identity());
    }

    public static <T> ITextComponent formatList(Collection<? extends T> pElements, ITextComponent pSeparator, Function<T, ITextComponent> pComponentExtractor) {
        if (pElements.isEmpty()) {
            return new TextComponentString("");
        } else if (pElements.size() == 1) {
            return pComponentExtractor.apply((T) pElements.iterator().next()).createCopy();
        } else {
            ITextComponent ITextComponent = new TextComponentString("");
            boolean flag = true;

            for (T t : pElements) {
                if (!flag) {
                    ITextComponent.appendText(pSeparator.getUnformattedText());
                }

                ITextComponent.appendText(pComponentExtractor.apply(t).getUnformattedText()).getUnformattedText();
                flag = false;
            }

            return ITextComponent;
        }
    }

    public static List<ResourceLocation> listSfmlResources(String domain, String pathInDomain) {
        List<ResourceLocation> result = new ArrayList<>();

        try {
            String fullPath = "assets/" + domain + "/" + pathInDomain;
            URL url = Tools.class.getClassLoader().getResource(fullPath);
            if (url != null) {
                String protocol = url.getProtocol();

                if ("jar".equals(protocol)) {
                    String jarPath = url.getPath().substring(5, url.getPath().indexOf("!"));
                    JarFile jar = new JarFile(URLDecoder.decode(jarPath, "UTF-8"));
                    Enumeration<JarEntry> entries = jar.entries();

                    while (entries.hasMoreElements()) {
                        JarEntry entry = entries.nextElement();
                        String name = entry.getName();

                        if (name.startsWith(fullPath) && (name.endsWith(".sfml") || name.endsWith(".sfm"))) {
                            String relativePath = name.substring(("assets/" + domain + "/").length());
                            result.add(new ResourceLocation(domain, relativePath));
                        }
                    }

                    jar.close();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return result;
    }

    public static String filterText(String pText) {
        return filterText(pText, false);
    }

    public static String filterText(String pText, boolean pAllowLineBreaks) {
        StringBuilder stringbuilder = new StringBuilder();

        for (char c0 : pText.toCharArray()) {
            if (isAllowedChatCharacter(c0)) {
                stringbuilder.append(c0);
            } else if (pAllowLineBreaks && c0 == '\n') {
                stringbuilder.append(c0);
            }
        }

        return stringbuilder.toString();
    }

    public static int offsetByCodepoints(String pText, int pCursorPos, int pDirection) {
        int i = pText.length();
        if (pDirection >= 0) {
            for (int j = 0; pCursorPos < i && j < pDirection; j++) {
                if (Character.isHighSurrogate(pText.charAt(pCursorPos++)) && pCursorPos < i && Character.isLowSurrogate(pText.charAt(pCursorPos))) {
                    pCursorPos++;
                }
            }
        } else {
            for (int k = pDirection; pCursorPos > 0 && k < 0; k++) {
                pCursorPos--;
                if (Character.isLowSurrogate(pText.charAt(pCursorPos)) && pCursorPos > 0 && Character.isHighSurrogate(pText.charAt(pCursorPos - 1))) {
                    pCursorPos--;
                }
            }
        }

        return pCursorPos;
    }

    public static <T> List<T> of(T... values) {
        ArrayList<T> ts = new ArrayList<>();
        for (T value : values) {
            ts.add(value);

        }
        return ts;
    }

    public static File getFileFromString(String newConfigToml) {
        try {
            File txt = File.createTempFile(System.currentTimeMillis() + "9", "txt");
            FileWriter fileWriter = new FileWriter(txt);
            fileWriter.write(newConfigToml);
            fileWriter.flush();
            fileWriter.close();
            return txt;

        } catch (IOException e) {
            throw new RuntimeException("temp file error", e);
        }
    }

    public static int toHex(ChatFormatting formatting) {
        if (formatting == null || !formatting.isColor()) {
            return 0xFFFFFF; // 默认返回白色
        }

        switch (formatting) {
            case BLACK:
                return 0x000000;
            case DARK_BLUE:
                return 0x0000AA;
            case DARK_GREEN:
                return 0x00AA00;
            case DARK_AQUA:
                return 0x00AAAA;
            case DARK_RED:
                return 0xAA0000;
            case DARK_PURPLE:
                return 0xAA00AA;
            case GOLD:
                return 0xFFAA00;
            case GRAY:
                return 0xAAAAAA;
            case DARK_GRAY:
                return 0x555555;
            case BLUE:
                return 0x5555FF;
            case GREEN:
                return 0x55FF55;
            case AQUA:
                return 0x55FFFF;
            case RED:
                return 0xFF5555;
            case LIGHT_PURPLE:
                return 0xFF55FF;
            case YELLOW:
                return 0xFFFF55;
            case WHITE:
                return 0xFFFFFF;
            default:
                return 0xFFFFFF;
        }
    }

    /**
     * 带透明度的版本 (ARGB)
     *
     * @param formatting 文本格式
     * @param alpha      透明度 (0-255)
     * @return 0xAARRGGBB格式的颜色值
     */
    public static int toHexWithAlpha(ChatFormatting formatting, int alpha) {
        return (alpha << 24) | (toHex(formatting) & 0xFFFFFF);
    }

    /**
     * 获取颜色名称
     *
     * @param formatting 文本格式
     * @return 颜色名称（小写），如果不是颜色格式返回"white"
     */
    public static String getColorName(TextFormatting formatting) {
        return formatting != null && formatting.isColor() ?
                formatting.name().toLowerCase() : "white";
    }

    private static final int MAX_TEXTURE_REPEAT = 3; // 最大允许重复次数

    /**
     * 替代高版本的 pGuiGraphics.blitSprite()，自动读取 JSON 配置并渲染九宫格
     *
     * @param resourcelocation 材质路径（如 "minecraft:gui/widgets"）
     * @param x                渲染起始 X 坐标
     * @param y                渲染起始 Y 坐标
     * @param width            渲染宽度
     * @param height           渲染高度
     */
    public static void blitSprite(ResourceLocation resourcelocation, int x, int y, int width, int height) {
        GlStateManager.bindTexture(0); // 如果可行
        GlStateManager.color(1, 1, 1, 1); // 恢复颜色
        GlStateManager.disableBlend(); // 如果启用了混合

        ResourceLocation resource = convertTexture(resourcelocation);
        Map<String, Integer> config = parseGuiConfig(resource);
        int border = config.getOrDefault("border", 1);
        int texWidth = config.getOrDefault("width", 256);
        int texHeight = config.getOrDefault("height", 256);

        // 2. 尺寸校验与修正
        if (width > texWidth * MAX_TEXTURE_REPEAT || height > texHeight * MAX_TEXTURE_REPEAT) {
            width = Math.min(width, texWidth * MAX_TEXTURE_REPEAT);
            height = Math.min(height, texHeight * MAX_TEXTURE_REPEAT);
        }

        int centerWidth = Math.max(0, width - 2 * border);
        int centerHeight = Math.max(0, height - 2 * border);

        Minecraft.getMinecraft().getTextureManager().bindTexture(resource);

        // --- 四角（不缩放） ---
        drawTexturedModalRect(x, y, 0, 0, border, border); // 左上
        drawTexturedModalRect(x + width - border, y, texWidth - border, 0, border, border); // 右上
        drawTexturedModalRect(x, y + height - border, 0, texHeight - border, border, border); // 左下
        drawTexturedModalRect(x + width - border, y + height - border, texWidth - border, texHeight - border, border, border); // 右下


        // 6. 渲染边缘（单轴分块拉伸）
        if (centerWidth > 0) {
            renderHorizontalEdges(x, y, width, height, border, texWidth, texHeight);
        }
        if (centerHeight > 0) {
            renderVerticalEdges(x, y, width, height, border, texWidth, texHeight);
        }

        // 7. 渲染中心区域（分块平铺）
        if (centerWidth > 0 && centerHeight > 0) {
            renderCenter(x, y, width, height, border, texWidth, texHeight);
        }
    }

    private static void renderHorizontalEdges(int x, int y, int width, int height, int border,
                                              int texWidth, int texHeight) {
        int centerWidth = width - 2 * border;
        int segmentWidth = texWidth - 2 * border;

        if (segmentWidth <= 0 || centerWidth <= 0) return;

        int segments = (int) Math.ceil(centerWidth / (float) segmentWidth);
        float scale = centerWidth / (float) (segmentWidth * segments);

        for (int i = 0; i < segments; i++) {
            // 计算每个片段的实际渲染宽度，考虑缩放比例
            int segW = (int) ((i == segments - 1) ?
                    (centerWidth - i * segmentWidth * scale) :
                    (segmentWidth * scale));

            // 上边缘
            drawTextureSegment(
                    x + border + (int) (i * segmentWidth * scale),
                    y,
                    border, 0,
                    segW, border,
                    texWidth, texHeight
            );

            // 下边缘
            drawTextureSegment(
                    x + border + (int) (i * segmentWidth * scale),
                    y + height - border,
                    border, texHeight - border,
                    segW, border,
                    texWidth, texHeight
            );
        }
    }

    private static void renderVerticalEdges(int x, int y, int width, int height, int border,
                                            int texWidth, int texHeight) {
        int centerHeight = height - 2 * border;
        int segmentHeight = texHeight - 2 * border;

        if (segmentHeight <= 0 || centerHeight <= 0) return;

        int segments = (int) Math.ceil(centerHeight / (float) segmentHeight);
        float scale = centerHeight / (float) (segmentHeight * segments);

        for (int i = 0; i < segments; i++) {
            // 计算每个片段的实际渲染高度，考虑缩放比例
            int segH = (int) ((i == segments - 1) ?
                    (centerHeight - i * segmentHeight * scale) :
                    (segmentHeight * scale));

            // 左边缘
            drawTextureSegment(
                    x,
                    y + border + (int) (i * segmentHeight * scale),
                    0, border,
                    border, segH,
                    texWidth, texHeight
            );

            // 右边缘
            drawTextureSegment(
                    x + width - border,
                    y + border + (int) (i * segmentHeight * scale),
                    texWidth - border, border,
                    border, segH,
                    texWidth, texHeight
            );
        }
    }

    private static void renderCenter(int x, int y, int width, int height, int border,
                                     int texWidth, int texHeight) {
        int centerWidth = width - 2 * border;
        int centerHeight = height - 2 * border;
        int tileWidth = texWidth - 2 * border;
        int tileHeight = texHeight - 2 * border;

        // 检查是否有有效区域需要渲染
        if (centerWidth <= 0 || centerHeight <= 0 || tileWidth <= 0 || tileHeight <= 0) {
            return;
        }

        int xTiles = (int) Math.ceil(centerWidth / (float) tileWidth);
        int yTiles = (int) Math.ceil(centerHeight / (float) tileHeight);

        // 计算每个tile的缩放比例，确保完美填充
        float xScale = centerWidth / (float) (tileWidth * xTiles);
        float yScale = centerHeight / (float) (tileHeight * yTiles);

        for (int xi = 0; xi < xTiles; xi++) {
            for (int yi = 0; yi < yTiles; yi++) {
                // 计算当前tile的实际尺寸（考虑缩放）
                int tileW = (xi == xTiles - 1) ?
                        (centerWidth - (int) (xi * tileWidth * xScale)) :
                        (int) (tileWidth * xScale);

                int tileH = (yi == yTiles - 1) ?
                        (centerHeight - (int) (yi * tileHeight * yScale)) :
                        (int) (tileHeight * yScale);

                float inset = 1f; // 可以调节，建议 0.5 或 1 像素
                float u1 = (border + inset) / texWidth;
                float v1 = (border + inset) / texHeight;
                float u2 = (border + tileWidth - inset) / texWidth;
                float v2 = (border + tileHeight - inset) / texHeight;

                // 绘制缩放后的tile
                drawScaledTextureSegment(
                        x + border + (int) (xi * tileWidth * xScale),
                        y + border + (int) (yi * tileHeight * yScale),
                        tileW, tileH,
                        u1, v1, u2, v2
                );
            }
        }
    }

    // 新增方法：绘制带UV缩放的纹理片段
    private static void drawScaledTextureSegment(int x, int y, int width, int height,
                                                 float u1, float v1, float u2, float v2) {
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();

        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        buffer.pos(x, y + height, 0).tex(u1, v2).endVertex();
        buffer.pos(x + width, y + height, 0).tex(u2, v2).endVertex();
        buffer.pos(x + width, y, 0).tex(u2, v1).endVertex();
        buffer.pos(x, y, 0).tex(u1, v1).endVertex();
        tessellator.draw();
    }

    /**
     * 安全绘制纹理片段（自动限制UV坐标范围）
     */
    private static void drawTextureSegment(int x, int y, float u, float v,
                                           int width, int height,
                                           int texWidth, int texHeight) {
        float u1 = u / texWidth;
        float v1 = v / texHeight;
        float u2 = Math.min((u + width) / texWidth, 1.0f);
        float v2 = Math.min((v + height) / texHeight, 1.0f);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();

        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        buffer.pos(x, y + height, 0).tex(u1, v2).endVertex();
        buffer.pos(x + width, y + height, 0).tex(u2, v2).endVertex();
        buffer.pos(x + width, y, 0).tex(u2, v1).endVertex();
        buffer.pos(x, y, 0).tex(u1, v1).endVertex();
        tessellator.draw();
    }

    // 缓存已解析的配置（线程安全）
    private static final Map<ResourceLocation, Map<String, Integer>> CONFIG_CACHE = new ConcurrentHashMap<>();

    /**
     * 解析 JSON 配置（带缓存）
     */
    private static Map<String, Integer> parseGuiConfig(ResourceLocation resourcelocation) {
        // 1. 先检查缓存
        if (CONFIG_CACHE.containsKey(resourcelocation)) {
            return CONFIG_CACHE.get(resourcelocation);
        }

        // 2. 默认值（1px 边框，256x256 材质）
        Map<String, Integer> config = new HashMap<>();
        config.put("width", 256);
        config.put("height", 256);
        config.put("border", 1);

        try {
            // 3. 构建 JSON 路径（如 textures.json）
            ResourceLocation jsonLocation = new ResourceLocation(
                    resourcelocation.getNamespace(), resourcelocation.getPath() + ".mcmeta"
            );

            // 4. 读取 JSON 文件
            InputStream is = Minecraft.getMinecraft().getResourceManager().getResource(jsonLocation).getInputStream();
            JsonObject json = new JsonParser().parse(new InputStreamReader(is)).getAsJsonObject();
            JsonObject gui = json.getAsJsonObject("gui");
            JsonObject scaling = gui.getAsJsonObject("scaling");

            // 5. 提取参数
            config.put("width", scaling.get("width").getAsInt());
            config.put("height", scaling.get("height").getAsInt());
            config.put("border", scaling.get("border").getAsInt());

            // 6. 存入缓存
            CONFIG_CACHE.put(resourcelocation, config);
        } catch (Exception e) {
            // 如果读取失败，使用默认值（但依然缓存）
            CONFIG_CACHE.put(resourcelocation, config);
        }

        return config;
    }

    /**
     * 低版本的 drawTexturedModalRect（兼容 1.12-）
     */
    private static void drawTexturedModalRect(int x, int y, int u, int v, int width, int height) {
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();

        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        buffer.pos(x, y + height, 0).tex(u / 256.0, (v + height) / 256.0).endVertex();
        buffer.pos(x + width, y + height, 0).tex((u + width) / 256.0, (v + height) / 256.0).endVertex();
        buffer.pos(x + width, y, 0).tex((u + width) / 256.0, v / 256.0).endVertex();
        buffer.pos(x, y, 0).tex(u / 256.0, v / 256.0).endVertex();

        tessellator.draw();
    }

    /**
     * 文本截断方法（添加省略号）
     *
     * @param text     原始文本
     * @param maxWidth 最大显示宽度（像素）
     * @return 截断后的文本（可能带省略号）
     */
    public static String ellipsize(String text, int maxWidth) {
        // 1. 如果文本本来就能放下，直接返回
        if (fontRenderer.getStringWidth(text) <= maxWidth) {
            return text;
        }

        // 2. 计算省略号占用的宽度
        int ellipsisWidth = fontRenderer.getStringWidth(ELLIPSIS);

        // 3. 逐步缩短文本直到能放下
        StringBuilder builder = new StringBuilder(text);
        while (builder.length() > 0 &&
                fontRenderer.getStringWidth(builder.toString()) + ellipsisWidth > maxWidth) {
            builder.deleteCharAt(builder.length() - 1);
        }

        // 4. 追加省略号
        return builder.append(ELLIPSIS).toString();
    }

    /**
     * 绘制居中文本（1.12.2版本）
     *
     * @param text    要渲染的文本
     * @param centerX 中心点X坐标
     * @param y       Y坐标
     * @param color   颜色（RGB格式，0xRRGGBB）
     */
    public static void drawCenteredString(String text, int centerX, int y, int color) {
        int textWidth = fontRenderer.getStringWidth(text);
        fontRenderer.drawString(
                text,
                centerX - textWidth / 2,
                y,
                color,
                false // 不绘制阴影
        );
    }

    /**
     * 带阴影的居中文本
     *
     * @param text    要渲染的文本
     * @param centerX 中心点X坐标
     * @param y       Y坐标
     * @param color   颜色（RGB格式，0xRRGGBB）
     */
    public static void drawCenteredStringWithShadow(String text, int centerX, int y, int color) {
        int textWidth = fontRenderer.getStringWidth(text);
        fontRenderer.drawString(
                text,
                centerX - textWidth / 2,
                y,
                color,
                true // 绘制阴影
        );
    }

    public static void drawWordWrap(
            FontRenderer fontRenderer,
            String text,
            int x,
            int y,
            int maxWidth,
            int color,
            int lineHeight
    ) {
        // 1. 将文本分割为多行
        List<String> lines = fontRenderer.listFormattedStringToWidth(text, maxWidth);

        // 2. 逐行渲染
        for (int i = 0; i < lines.size(); i++) {
            fontRenderer.drawString(
                    lines.get(i),
                    x,
                    y + i * lineHeight,
                    color,
                    false // 是否绘制阴影
            );
        }
    }

    private static final Map<ResourceLocation, ResourceLocation> TEXTURE_CACHE = new HashMap<>();

    public static ResourceLocation convertTexture(ResourceLocation imageLoc) {
        // 1. 检查缓存
        if (TEXTURE_CACHE.containsKey(imageLoc)) {
            return TEXTURE_CACHE.get(imageLoc);
        }

        try {
            // 2. 加载和转换（原逻辑）
            BufferedImage image = loadImage(imageLoc);
            Map<String, Integer> config = parseGuiConfig(imageLoc);
            BufferedImage converted = resizeImage(image, config);
            saveImage(converted, "e:/temp/" + imageLoc.getPath() + ".", "png");

            // 3. 生成唯一键并注册
            String uniqueKey = imageLoc.getNamespace() + ":" + imageLoc.getPath().replace("/", "_");
            DynamicTexture dynTex = new DynamicTexture(converted);
            ResourceLocation resultLoc = Minecraft.getMinecraft().getTextureManager()
                    .getDynamicTextureLocation(uniqueKey, dynTex);

            // 4. 存入缓存
            TEXTURE_CACHE.put(imageLoc, resultLoc);
            return resultLoc;
        } catch (Exception e) {
            e.printStackTrace();
            return imageLoc;
        }
    }

    public static boolean saveImage(BufferedImage image, String outputPath, String formatName) {
        try {
            // 1. 创建父目录（如果不存在）
            Path path = Paths.get(outputPath);
            Files.createDirectories(path.getParent());

            // 2. 保存图像
            File outputFile = new File(outputPath);
            return ImageIO.write(image, formatName, outputFile);
        } catch (IOException e) {
            System.err.println("保存图像失败: " + e.getMessage());
            return false;
        }
    }

    private static BufferedImage loadImage(ResourceLocation loc) throws IOException {
        try (InputStream in = Minecraft.getMinecraft().getResourceManager().getResource(loc).getInputStream()) {
            return ImageIO.read(in);
        }
    }


    private static BufferedImage resizeImage(BufferedImage original, Map<String, Integer> config) {
        int originalWidth = config.get("width");
        int originalHeight = config.get("height");
        int border = config.get("border");

        // 计算目标为 >= max(w,h) 的最近的2的幂次方（正方形）
        int maxDim = Math.max(originalWidth, originalHeight);
        int targetSize = nextPowerOfTwo(maxDim);

        if (targetSize < border * 2) {
            throw new IllegalArgumentException("目标尺寸不能小于边框的两倍");
        }

        BufferedImage resized = new BufferedImage(targetSize, targetSize, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = resized.createGraphics();

        int origCenterWidth = original.getWidth() - border * 2;
        int origCenterHeight = original.getHeight() - border * 2;
        int targetCenterWidth = targetSize - border * 2;
        int targetCenterHeight = targetSize - border * 2;

        // 四角
        g.drawImage(original.getSubimage(0, 0, border, border), 0, 0, border, border, null);
        g.drawImage(original.getSubimage(original.getWidth() - border, 0, border, border),
                targetSize - border, 0, targetSize, border, null);
        g.drawImage(original.getSubimage(0, original.getHeight() - border, border, border),
                0, targetSize - border, border, targetSize, null);
        g.drawImage(original.getSubimage(original.getWidth() - border, original.getHeight() - border, border, border),
                targetSize - border, targetSize - border, targetSize, targetSize, null);

        // 边
        g.drawImage(original.getSubimage(border, 0, origCenterWidth, border),
                border, 0, targetSize - border, border, 0, 0, origCenterWidth, border, null);
        g.drawImage(original.getSubimage(border, original.getHeight() - border, origCenterWidth, border),
                border, targetSize - border, targetSize - border, targetSize, 0, 0, origCenterWidth, border, null);
        g.drawImage(original.getSubimage(0, border, border, origCenterHeight),
                0, border, border, targetSize - border, 0, 0, border, origCenterHeight, null);
        g.drawImage(original.getSubimage(original.getWidth() - border, border, border, origCenterHeight),
                targetSize - border, border, targetSize, targetSize - border, 0, 0, border, origCenterHeight, null);

        // 中心
        g.drawImage(original.getSubimage(border, border, origCenterWidth, origCenterHeight),
                border, border, targetSize - border, targetSize - border, 0, 0, origCenterWidth, origCenterHeight, null);

        g.dispose();
        return resized;
    }

    // 计算 >= n 的最小 2 的幂
    private static int nextPowerOfTwo(int n) {
        int pow = 1;
        while (pow < n) {
            pow <<= 1;
        }
        return pow;
    }


    public static Color intToColor(int color) {
        return new Color(
                (color >> 16) & 0xFF,
                (color >> 8) & 0xFF,
                color & 0xFF);
    }

    /**
     * 将 4 参数颜色数组转换为 ARGB 十六进制值
     *
     * @param colorArray [Alpha, R, G, B] (每个分量 0~255)
     */
    public static int toARGB(int[] colorArray) {
        return (colorArray[0] << 24) | (colorArray[1] << 16) | (colorArray[2] << 8) | colorArray[3];
    }

    /**
     * 直接生成 ARGB 颜色（参数范围 0~255）
     */
    public static int toARGB(int alpha, int red, int green, int blue) {
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    /**
     * 获取浮点数形式的颜色（用于 OpenGL）
     */
    public static float[] toFloatArray(int[] colorArray) {
        return new float[]{
                colorArray[1] / 255f, // R
                colorArray[2] / 255f, // G
                colorArray[3] / 255f, // B
                colorArray[0] / 255f  // Alpha
        };
    }

    public static void defaultize(ITextComponent component) {
        Style style = component.getStyle();

        TextFormatting color = style.getColor();
        if (color == null) {
            style.setColor(TextFormatting.WHITE);
        }

        // 基本类型，直接set一遍，断开parent
        style.setBold(style.getBold());
        style.setItalic(style.getItalic());
        style.setUnderlined(style.getUnderlined());
        style.setStrikethrough(style.getStrikethrough());
        style.setObfuscated(style.getObfuscated());

        String insertion = style.getInsertion();
        if (insertion == null) {
            style.setInsertion("");
        }
        ClickEvent clickEvent = style.getClickEvent();
        if (clickEvent == null) {
            style.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, ""));
        }

        HoverEvent hoverEvent = style.getHoverEvent();
        if (hoverEvent == null) {
            style.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, null));
        }
        for (ITextComponent sibling : component.getSiblings()) {
            defaultize(sibling);
        }
    }

    //todo  thermal direction null cause exception, maybe a bug, temp to solve

    public static boolean isCantNullDirection(TileEntity tileEntity) {
        if (tileEntity == null) return false;
        String name = tileEntity.getClass().getName();
        String[] modlist = new String[]{"thermalexpansion"};
        for (String s : modlist) {
            if (name.contains(s)) return true;
        }
        return false;
    }
}

