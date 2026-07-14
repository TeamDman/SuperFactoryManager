package ca.teamdman.sfm.common.item;

import ca.teamdman.sfm.client.registry.SFMKeyMappings;
import ca.teamdman.sfm.client.screen.SFMScreenChangeHelpers;
import ca.teamdman.sfm.client.text_editor.SFMTextEditScreenDiskOpenContext;
import ca.teamdman.sfm.client.text_styling.ProgramSyntaxHighlightingHelper;
import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import ca.teamdman.sfm.common.net.ServerboundDiskItemSetProgramPacket;
import ca.teamdman.sfm.common.program.linting.ProgramLinter;
import ca.teamdman.sfm.common.registry.registration.SFMDataComponents;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.common.registry.registration.SFMPackets;
import ca.teamdman.sfm.common.util.SFMEnvironmentUtils;
import ca.teamdman.sfm.common.util.SFMItemUtils;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import ca.teamdman.sfml.ast.Program;
import ca.teamdman.sfml.program_builder.ProgramBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipProvider;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class DiskItem extends Item implements TooltipProvider {

    @SFMLocalizationDatagen
    public static final LocalizationEntry DISK_EDIT_IN_HAND_TOOLTIP = new LocalizationEntry(
            "gui.sfm.disk.tooltip.edit_in_hand",
            "You can right-click a disk in your hand to edit outside of a manager."
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry DISK_ITEM = new LocalizationEntry(
            () -> SFMItems.DISK.get().getDescriptionId(),
            () -> "Factory Manager Program Disk"
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry PROGRAM_COMPILE_FAILED_WITH_ERRORS = new LocalizationEntry(
            "program.sfm.error.compile_failed_with_errors",
            "Failed to compile with %d errors."
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry PROGRAM_COMPILE_SUCCEEDED_WITH_WARNINGS = new LocalizationEntry(
            "program.sfm.error.compile_success_with_warnings",
            "Successfully compiled \"%s\" with %d warnings."
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry PROGRAM_COMPILE_FROM_DISK_BEGIN = new LocalizationEntry(
            "program.sfm.compile_begin",
            "Compiling program from disk."
    );

    public DiskItem(Properties properties) {

        super(properties);
    }

    public static String getProgramString(ItemStack stack) {

        return stack.getOrDefault(SFMDataComponents.PROGRAM_STRING.get(), "");
    }

    /**
     * Reads the stored program without creating data on an otherwise blank disk.
     */
    @MCVersionDependentBehaviour // 1.21+ stores program data in item components
    public static String getProgramStringReadOnly(ItemStack stack) {

        return stack.getOrDefault(SFMDataComponents.PROGRAM_STRING.get(), "");
    }

    public static void setProgram(
            ItemStack stack,
            String programString
    ) {

        programString = programString.replaceAll("\r", "");
        stack.set(SFMDataComponents.PROGRAM_STRING.get(), programString);
    }

    public static void pruneIfDefault(ItemStack stack) {

        if (getProgramString(stack).isBlank() && LabelPositionHolder.from(stack).isEmpty()) {
            clearData(stack);
        }
    }

    public static void clearData(ItemStack stack) {

        stack.remove(SFMDataComponents.PROGRAM_STRING);
        stack.remove(SFMDataComponents.PROGRAM_ERRORS);
        stack.remove(SFMDataComponents.PROGRAM_WARNINGS);
        stack.remove(SFMDataComponents.LABEL_POSITION_HOLDER);
    }

    public static @Nullable Program compileAndUpdateErrorsAndWarnings(
            ItemStack stack,
            @Nullable ManagerBlockEntity manager,
            boolean updateWarnings
    ) {

        if (manager != null) {
            manager.logger.info(x -> x.accept(PROGRAM_COMPILE_FROM_DISK_BEGIN.get()));
        }
        AtomicReference<Program> rtn = new AtomicReference<>(null);
        String programString = getProgramString(stack);

        new ProgramBuilder(programString).build()
                .caseSuccess((successProgram, metadata) -> {
                    if (updateWarnings) {
                        Collection<TranslatableContents> warnings = ProgramLinter.gatherWarnings(
                                successProgram,
                                LabelPositionHolder.from(stack),
                                manager
                        );

                        // Log to disk
                        if (manager != null) {
                            manager.logger.info(x -> x.accept(PROGRAM_COMPILE_SUCCEEDED_WITH_WARNINGS.get(
                                    successProgram.name(),
                                    warnings.size()
                            )));
                            manager.logger.warn(warnings::forEach);
                        }
                        setWarnings(stack, warnings);
                    }

                    // Update disk properties
                    setProgramName(stack, successProgram.name());
                    setErrors(stack, Collections.emptyList());

                    // Track result
                    rtn.set(successProgram);
                })
                .caseFailure(result -> {
                    List<TranslatableContents> warnings = Collections.emptyList();
                    List<TranslatableContents> errors = result.metadata().errors();

                    // Log to disk
                    if (manager != null) {
                        manager.logger.error(x -> x.accept(PROGRAM_COMPILE_FAILED_WITH_ERRORS.get(
                                errors.size())));
                        manager.logger.error(errors::forEach);
                    }

                    // Update disk properties
                    setWarnings(stack, warnings);
                    setErrors(stack, errors);
                });
        return rtn.get();
    }

    public static List<Component> getErrors(DataComponentGetter components) {

        return components.getOrDefault(SFMDataComponents.PROGRAM_ERRORS.get(), Collections.emptyList());
    }

    public static void setErrors(
            ItemStack stack,
            List<TranslatableContents> errors
    ) {

        stack.set(
                SFMDataComponents.PROGRAM_ERRORS.get(), errors
                        .stream()
                        .map(MutableComponent::create)
                        .collect(Collectors.toList())
        );
    }

    public static List<Component> getWarnings(DataComponentGetter components) {

        return components.getOrDefault(SFMDataComponents.PROGRAM_WARNINGS.get(), Collections.emptyList());
    }

    public static void rebuildWarnings(
            ManagerBlockEntity manager
    ) {

        var disk = manager.getDisk();
        if (disk != null) {
            var program = manager.getProgram();
            if (program != null) {
                DiskItem.setWarnings(
                        disk,
                        ProgramLinter.gatherWarnings(program, LabelPositionHolder.from(disk), manager)
                );
            }
        }
    }

    public static void setWarnings(
            ItemStack stack,
            Collection<TranslatableContents> warnings
    ) {

        stack.set(
                SFMDataComponents.PROGRAM_WARNINGS, warnings
                        .stream()
                        .map(MutableComponent::create)
                        .collect(Collectors.toList())
        );
    }

    /**
     * Reads the stored program name without creating data on an otherwise blank disk.
     */
    @MCVersionDependentBehaviour // 1.21+ stores program data in item components
    public static String getProgramNameReadOnly(ItemStack stack) {

        return getProgramName(stack);
    }

    public static void setProgramName(
            ItemStack stack,
            String name
    ) {

        if (!name.isEmpty()) {
            stack.set(DataComponents.ITEM_NAME, Component.literal(name));
        }
    }

    public static String getProgramName(DataComponentGetter components) {

        return components.getOrDefault(DataComponents.ITEM_NAME, Component.empty()).getString();
    }

    @Override
    public InteractionResult use(
            Level pLevel,
            Player pPlayer,
            InteractionHand pUsedHand
    ) {

        var stack = pPlayer.getItemInHand(pUsedHand);
        if (pLevel.isClientSide()) {
            SFMScreenChangeHelpers.showProgramEditScreen(new SFMTextEditScreenDiskOpenContext(
                    getProgramString(stack),
                    LabelPositionHolder.from(stack),
                    newProgramString -> SFMPackets.sendToServer(new ServerboundDiskItemSetProgramPacket(
                            newProgramString,
                            pUsedHand
                    ))
            ));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public Component getName(ItemStack stack) {

        if (SFMEnvironmentUtils.isClient()) {
            if (SFMKeyMappings.isKeyDown(SFMKeyMappings.MORE_INFO_TOOLTIP_KEY))
                return super.getName(stack);
        }
        var name = getProgramName(stack);
        if (name.isEmpty()) return super.getName(stack);
        return Component.literal(name).withStyle(ChatFormatting.AQUA);
    }

    @Override
    public void addToTooltip(TooltipContext context, Consumer<Component> consumer, TooltipFlag flag, DataComponentGetter components) {
        String program = components.getOrDefault(SFMDataComponents.PROGRAM_STRING.get(), "");
        if (SFMItemUtils.isClientAndMoreInfoKeyPressed() && !program.isEmpty()) {
            consumer.accept(SFMItemUtils.getRainbow(DiskItem.getProgramName(components).length()));
            ProgramSyntaxHighlightingHelper.withSyntaxHighlighting(program, false)
                    .forEach(consumer);
        } else {
            LabelPositionHolder.from(components).asHoverText()
                    .forEach(consumer);
            DiskItem.getErrors(components)
                    .stream()
                    .map(Component::copy)
                    .map(line -> line.withStyle(ChatFormatting.RED))
                    .forEach(consumer);
            DiskItem.getWarnings(components)
                    .stream()
                    .map(Component::copy)
                    .map(line -> line.withStyle(ChatFormatting.YELLOW))
                    .forEach(consumer);
            if (!program.isEmpty()) {
                SFMItemUtils.appendMoreInfoKeyReminderTextIfOnClient(consumer);
            }
        }
        if (!program.isEmpty()) {
            consumer.accept(
                    DiskItem.DISK_EDIT_IN_HAND_TOOLTIP.getComponent().withStyle(ChatFormatting.GRAY)
            );
        }
    }
}
