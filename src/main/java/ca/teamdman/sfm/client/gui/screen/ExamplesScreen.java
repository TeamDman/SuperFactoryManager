package ca.teamdman.sfm.client.gui.screen;

import ca.teamdman.sfm.common.SFMConfig;
import ca.teamdman.sfm.common.localization.LocalizationKeys;
import ca.teamdman.sfm.common.registry.SFMResourceTypes;
import ca.teamdman.sfml.ast.Program;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

public class ExamplesScreen extends Screen {
    private final BiConsumer<String, Map<String,String>> CALLBACK;

    public ExamplesScreen(BiConsumer<String, Map<String,String>> callback) {
        super(LocalizationKeys.EXAMPLES_GUI_TITLE.getComponent());
        CALLBACK = callback;
    }

    @Override
    protected void init() {
        super.init();

        //discover template programs
        var irm = Minecraft.getInstance().getResourceManager();
        Map<ResourceLocation, Resource> found = irm.listResources(
                "template_programs",
                (path) -> path.getPath().endsWith(".sfml") || path.getPath().endsWith(".sfm")
        );
        Map<String, String> templatePrograms = new HashMap<>();
        for (var entry : found.entrySet()) {
            try (BufferedReader reader = entry.getValue().openAsReader()) {
                String program = reader.lines().collect(Collectors.joining("\n"));
                if (program.contains("$REPLACE_RESOURCE_TYPES_HERE$")) {
                    List<? extends String> blacklistedResources = SFMConfig.getOrDefault(SFMConfig.COMMON.blacklistedResourceTypesForTransfer);
                    var replacement = SFMResourceTypes.DEFERRED_TYPES.keySet()
                            .stream()
                            .map(ResourceLocation::getPath)
                            .map(e -> {
                                String text = "";
                                if (blacklistedResources.contains(e))
                                    text += "-- BLACKLISTED: ";
                                text += "INPUT " + e + ":: FROM a";
                                return text;
                            })
                            .collect(Collectors.joining("\n    "));
                    program = program.replace("$REPLACE_RESOURCE_TYPES_HERE$", replacement);
                }
                String finalProgram = program;
                Program.compile(
                        program,
                        successProgram -> templatePrograms.put(
                                successProgram.name().isBlank() ? entry.getKey().toString() : successProgram.name(),
                                finalProgram
                        ),
                        failure -> templatePrograms.put(String.format("(compile failed) %s", entry.getKey().toString()), finalProgram)
                );
            } catch (IOException ignored) {
            }
        }

        // add picker buttons
        {
            int i = 0;
            int buttonWidth = templatePrograms.keySet()
                                      .stream()
                                      .mapToInt(this.font::width)
                                      .max().orElse(50) + 10;
            int buttonHeight = 20;
            int paddingX = 5;
            int paddingY = 5;
            int buttonsPerRow = this.width / (buttonWidth + paddingX);
            for (var entry : templatePrograms
                    .entrySet()
                    .stream()
                    .sorted((o1, o2) -> Comparator.<String>naturalOrder().compare(o1.getKey(), o2.getKey()))
                    .toList()) {
                int x = (this.width - (buttonWidth + paddingX) * Math.min(buttonsPerRow, templatePrograms.size())) / 2
                        + paddingX
                        + (i % buttonsPerRow) * (
                        buttonWidth
                        + paddingX
                );
                int y = 50 + (i / buttonsPerRow) * (buttonHeight + paddingY);
                addRenderableWidget(
                        Button.builder(
                                        Component.literal(entry.getKey()),
                                        btn -> {
                                            onClose();
                                            CALLBACK.accept(entry.getValue(), templatePrograms);
                                        }
                                )
                                .pos(x, y)
                                .size(buttonWidth, buttonHeight)
                                .build()
                );
                i++;
            }
        }
    }


    @Override
    public void render(GuiGraphics graphics, int pMouseX, int pMouseY, float pPartialTick) {
        this.renderTransparentBackground(graphics);
        this.renderTransparentBackground(graphics);
        this.renderTransparentBackground(graphics);
        super.render(graphics, pMouseX, pMouseY, pPartialTick);
        MutableComponent warning1 = LocalizationKeys.EXAMPLES_GUI_WARNING_1.getComponent();
        graphics.drawString(
                this.font,
                warning1,
                this.width / 2 - this.font.width(warning1) / 2,
                20,
                0xffffff,
                false
        );
        graphics.drawString(
                this.font,
                warning1,
                this.width / 2 - this.font.width(warning1) / 2,
                20,
                0xffffff,
                false
        );
        MutableComponent warning2 = LocalizationKeys.EXAMPLES_GUI_WARNING_2.getComponent();
        graphics.drawString(
                this.font,
                warning2,
                this.width / 2 - this.font.width(warning2) / 2,
                36,
                0xffffff,
                false
        );
    }
}
