package ca.teamdman.sfm.client.screen;

import ca.teamdman.sfm.client.widget.SFMButtonBuilder;
import ca.teamdman.sfm.common.config.SFMConfig;
import ca.teamdman.sfm.common.localization.LocalizationKeys;
import ca.teamdman.sfm.common.registry.SFMResourceTypes;
import ca.teamdman.sfml.ast.Program;
import ca.teamdman.sfml.program_builder.ProgramBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
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
    private final BiConsumer<String, Map<String, String>> CALLBACK;

    public ExamplesScreen(BiConsumer<String, Map<String, String>> callback) {
        super(LocalizationKeys.EXAMPLES_GUI_TITLE.getComponent());
        CALLBACK = callback;
    }

    @Override
    public void render(
            PoseStack pPoseStack,
            int pMouseX,
            int pMouseY,
            float pPartialTick
    ) {
        this.renderBackground(pPoseStack);
        this.renderBackground(pPoseStack);
        this.renderBackground(pPoseStack);
        super.render(pPoseStack, pMouseX, pMouseY, pPartialTick);
        MutableComponent warning1 = LocalizationKeys.EXAMPLES_GUI_WARNING_1.getComponent();

        SFMFontUtils.draw(
                pPoseStack,
                this.font,
                warning1,
                this.width / 2 - this.font.width(warning1) / 2,
                20,
                0xffffff,
                false
        );
        MutableComponent warning2 = LocalizationKeys.EXAMPLES_GUI_WARNING_2.getComponent();
        SFMFontUtils.draw(
                pPoseStack,
                this.font,
                warning2,
                this.width / 2 - this.font.width(warning2) / 2,
                36,
                0xffffff,
                false
        );
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
                String programString = reader.lines().collect(Collectors.joining("\n"));
                if (programString.contains("$REPLACE_RESOURCE_TYPES_HERE$")) {
                    List<? extends String> disallowedResourceTypesForTransfer = SFMConfig.getOrDefault(SFMConfig.SERVER_CONFIG.disallowedResourceTypesForTransfer);
                    var replacement = SFMResourceTypes.registry().keys()
                            .stream()
                            .map(ResourceLocation::getPath)
                            .map(e -> {
                                String text = "";
                                if (disallowedResourceTypesForTransfer.contains(e))
                                    text += "-- (disallowed in config) ";
                                text += "INPUT " + e + ":: FROM a";
                                return text;
                            })
                            .collect(Collectors.joining("\n    "));
                    programString = programString.replace("$REPLACE_RESOURCE_TYPES_HERE$", replacement);
                }
                String finalProgram = programString;
                ProgramBuilder.build(programString)
                        .caseSuccess((program, metadata) -> templatePrograms.put(
                                program.name().isBlank() ? entry.getKey().toString() : program.name(),
                                finalProgram
                        ))
                        .caseFailure(result -> templatePrograms.put(
                                String.format("(compile failed) %s", entry.getKey().toString()),
                                finalProgram
                        ));
                Program.compile(
                        programString,
                        successProgram -> templatePrograms.put(
                                successProgram.name().isBlank() ? entry.getKey().toString() : successProgram.name(),
                                finalProgram
                        ),
                        failure -> templatePrograms.put(
                                String.format("(compile failed) %s", entry.getKey().toString()),
                                finalProgram
                        )
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
                this.addRenderableWidget(
                        new SFMButtonBuilder()
                                .setText(Component.literal(entry.getKey()))
                                .setOnPress(btn -> {
                                    onClose();
                                    CALLBACK.accept(entry.getValue(), templatePrograms);
                                })
                                .setPosition(x, y)
                                .setSize(buttonWidth, buttonHeight)
                                .build()
                );
                i++;
            }
        }
    }
}
