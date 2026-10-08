package ca.teamdman.sfm.common.item;

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.tooltip_mode_override %}
{% else %}
import ca.teamdman.sfm.client.registry.SFMKeyMappings;
{% endif %}
{% else %}
import ca.teamdman.sfm.client.registry.SFMKeyMappings;
{% endcase %}
import ca.teamdman.sfm.client.screen.SFMScreenChangeHelpers;
import ca.teamdman.sfm.client.text_editor.SFMTextEditScreenDiskOpenContext;
import ca.teamdman.sfm.client.text_styling.ProgramSyntaxHighlightingHelper;
import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import ca.teamdman.sfm.common.net.ServerboundDiskItemSetProgramPacket;
import ca.teamdman.sfm.common.program.linting.ProgramLinter;
{% case minecraft_version %}
{% when "1.19.2" %}
import ca.teamdman.sfm.common.registry.registration.SFMCreativeTabs;
{% when "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}
{% when "1.21", "1.21.1", "26.1.2" %}
import ca.teamdman.sfm.common.registry.registration.SFMDataComponents;
{% endcase %}
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.common.registry.registration.SFMPackets;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.tooltip_mode_override %}
{% else %}
import ca.teamdman.sfm.common.util.SFMEnvironmentUtils;
{% endif %}
{% else %}
import ca.teamdman.sfm.common.util.SFMEnvironmentUtils;
{% endcase %}
import ca.teamdman.sfm.common.util.SFMItemUtils;
{% if features.disk_readonly_access %}
{% case minecraft_version %}
{% when "1.21", "1.21.1", "26.1.2" %}
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
{% endcase %}
{% endif %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}
import ca.teamdman.sfm.common.util.SFMTranslationUtils;
{% when "1.21", "1.21.1", "26.1.2" %}
{% endcase %}
import ca.teamdman.sfml.ast.Program;
import ca.teamdman.sfml.program_builder.ProgramBuilder;
import net.minecraft.ChatFormatting;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
{% when "1.21", "1.21.1" %}
import net.minecraft.core.component.DataComponents;
{% when "26.1.2" %}
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponents;
{% endcase %}
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.InteractionHand;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
import net.minecraft.world.InteractionResultHolder;
{% when "26.1.2" %}
import net.minecraft.world.InteractionResult;
{% endcase %}
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
{% when "26.1.2" %}
import net.minecraft.world.item.component.TooltipProvider;
{% endcase %}
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
{% when "26.1.2" %}
import java.util.function.Consumer;
{% endcase %}
import java.util.stream.Collectors;

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
public class DiskItem extends Item {
{% when "26.1.2" %}
public class DiskItem extends Item implements TooltipProvider {
{% endcase %}
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

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
    public DiskItem() {
{% when "26.1.2" %}
    public DiskItem(Properties properties) {
{% endcase %}

{% case minecraft_version %}
{% when "1.19.2" %}
        super(new Item.Properties().tab(SFMCreativeTabs.MAIN));
{% when "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
        super(new Item.Properties());
{% when "26.1.2" %}
        super(properties);
{% endcase %}
    }

    public static String getProgramString(ItemStack stack) {

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}
        return stack
                .getOrCreateTag()
                .getString("sfm:program");
{% when "1.21", "1.21.1" %}
        return stack.getOrDefault(SFMDataComponents.PROGRAM_STRING, "");
{% when "26.1.2" %}
        return stack.getOrDefault(SFMDataComponents.PROGRAM_STRING.get(), "");
{% endcase %}
    }

{% if features.disk_readonly_access or features.packet_computation %}
    /**
{% case minecraft_version %}
{% when "1.21", "1.21.1", "26.1.2" %}
     * Reads the stored program without creating data on an otherwise blank disk.
{% else %}
     * Reads the stored program without creating an NBT tag on an otherwise blank disk.
{% endcase %}
     */
{% case minecraft_version %}
{% when "1.21", "1.21.1", "26.1.2" %}
    @MCVersionDependentBehaviour // 1.21+ stores program data in item components
{% endcase %}
    public static String getProgramStringReadOnly(ItemStack stack) {

{% case minecraft_version %}
{% when "1.21", "1.21.1" %}
        return getProgramString(stack);
{% when "26.1.2" %}
        return stack.getOrDefault(SFMDataComponents.PROGRAM_STRING.get(), "");
{% else %}
        var tag = stack.getTag();
        return tag == null ? "" : tag.getString("sfm:program");
{% endcase %}
    }

{% endif %}
    public static void setProgram(
            ItemStack stack,
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}
            String program
{% when "1.21", "1.21.1", "26.1.2" %}
            String programString
{% endcase %}
    ) {

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}
        program = program.replaceAll("\r", "");
        stack
                .getOrCreateTag()
                .putString("sfm:program", program);
{% when "1.21", "1.21.1" %}
        programString = programString.replaceAll("\r", "");
        stack.set(SFMDataComponents.PROGRAM_STRING, programString);
{% when "26.1.2" %}
        programString = programString.replaceAll("\r", "");
        stack.set(SFMDataComponents.PROGRAM_STRING.get(), programString);
{% endcase %}
    }

    public static void pruneIfDefault(ItemStack stack) {

        if (getProgramString(stack).isBlank() && LabelPositionHolder.from(stack).isEmpty()) {
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}
            for (String key : stack.getOrCreateTag().getAllKeys().toArray(String[]::new)) {
                stack.removeTagKey(key);
            }
{% when "1.21", "1.21.1", "26.1.2" %}
            clearData(stack);
{% endcase %}
        }
    }

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}
{% when "1.21", "1.21.1", "26.1.2" %}
    public static void clearData(ItemStack stack) {

        stack.remove(SFMDataComponents.PROGRAM_STRING);
        stack.remove(SFMDataComponents.PROGRAM_ERRORS);
        stack.remove(SFMDataComponents.PROGRAM_WARNINGS);
        stack.remove(SFMDataComponents.LABEL_POSITION_HOLDER);
    }

{% endcase %}
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

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.sfml_execution_side %}
        ProgramBuilder builder = new ProgramBuilder(programString);
        if (manager != null) {
            builder.forExecutionSide(ca.teamdman.sfml.ast.ProgramExecutionSide.SERVER);
        }
        builder.build()
{% else %}
        new ProgramBuilder(programString).build()
{% endif %}
{% else %}
        new ProgramBuilder(programString).build()
{% endcase %}
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

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}
    public static List<TranslatableContents> getErrors(ItemStack stack) {
{% when "1.21", "1.21.1" %}
    public static List<Component> getErrors(ItemStack stack) {
{% when "26.1.2" %}
    public static List<Component> getErrors(DataComponentGetter components) {
{% endcase %}

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}
        return stack
                .getOrCreateTag()
                .getList("sfm:errors", Tag.TAG_COMPOUND)
                .stream()
                .map(CompoundTag.class::cast)
                .map(SFMTranslationUtils::deserializeTranslation)
                .toList();
{% when "1.21", "1.21.1" %}
        return stack.getOrDefault(SFMDataComponents.PROGRAM_ERRORS, Collections.emptyList());
{% when "26.1.2" %}
        return components.getOrDefault(SFMDataComponents.PROGRAM_ERRORS.get(), Collections.emptyList());
{% endcase %}
    }

    public static void setErrors(
            ItemStack stack,
            List<TranslatableContents> errors
    ) {

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}
        stack
                .getOrCreateTag()
                .put(
                        "sfm:errors",
                        errors
                                .stream()
                                .map(SFMTranslationUtils::serializeTranslation)
                                .collect(ListTag::new, ListTag::add, ListTag::addAll)
                );
{% when "1.21", "1.21.1" %}
        stack.set(
                        SFMDataComponents.PROGRAM_ERRORS,
                        errors
                                .stream()
                                .map(MutableComponent::create)
                                .collect(Collectors.toList())
                );
{% when "26.1.2" %}
        stack.set(
                        SFMDataComponents.PROGRAM_ERRORS.get(),
                        errors
                                .stream()
                                .map(MutableComponent::create)
                                .collect(Collectors.toList())
                );
{% endcase %}
    }

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}
    public static List<TranslatableContents> getWarnings(ItemStack stack) {
{% when "1.21", "1.21.1" %}
    public static List<Component> getWarnings(ItemStack stack) {
{% when "26.1.2" %}
    public static List<Component> getWarnings(DataComponentGetter components) {
{% endcase %}

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}
        return stack
                .getOrCreateTag()
                .getList("sfm:warnings", Tag.TAG_COMPOUND)
                .stream()
                .map(CompoundTag.class::cast)
                .map(SFMTranslationUtils::deserializeTranslation)
                .collect(
                        Collectors.toList());
{% when "1.21", "1.21.1" %}
        return stack.getOrDefault(SFMDataComponents.PROGRAM_WARNINGS, Collections.emptyList());
{% when "26.1.2" %}
        return components.getOrDefault(SFMDataComponents.PROGRAM_WARNINGS.get(), Collections.emptyList());
{% endcase %}
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

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}
        stack
                .getOrCreateTag()
                .put(
                        "sfm:warnings",
                        warnings
                                .stream()
                                .map(SFMTranslationUtils::serializeTranslation)
                                .collect(ListTag::new, ListTag::add, ListTag::addAll)
                );
{% when "1.21", "1.21.1", "26.1.2" %}
        stack.set(
                        SFMDataComponents.PROGRAM_WARNINGS,
                        warnings
                                .stream()
                                .map(MutableComponent::create)
                                .collect(Collectors.toList())
                );
{% endcase %}
    }

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}
    public static String getProgramName(ItemStack stack) {

        return stack
                .getOrCreateTag()
                .getString("sfm:name");
    }

{% when "1.21", "1.21.1", "26.1.2" %}
{% endcase %}
{% if features.disk_readonly_access %}
    /**
{% case minecraft_version %}
{% when "1.21", "1.21.1", "26.1.2" %}
     * Reads the stored program name without creating data on an otherwise blank disk.
{% else %}
     * Reads the stored program name without creating an NBT tag on an otherwise blank disk.
{% endcase %}
     */
{% case minecraft_version %}
{% when "1.21", "1.21.1", "26.1.2" %}
    @MCVersionDependentBehaviour // 1.21+ stores program data in item components
{% endcase %}
    public static String getProgramNameReadOnly(ItemStack stack) {

{% case minecraft_version %}
{% when "1.21", "1.21.1", "26.1.2" %}
        return getProgramName(stack);
{% else %}
        var tag = stack.getTag();
        return tag == null ? "" : tag.getString("sfm:name");
{% endcase %}
    }

{% endif %}
    public static void setProgramName(
            ItemStack stack,
            String name
    ) {

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}
        if (stack.getItem() instanceof DiskItem) {
            stack
                    .getOrCreateTag()
                    .putString("sfm:name", name);
{% when "1.21", "1.21.1", "26.1.2" %}
        if (!name.isEmpty()) {
            stack.set(DataComponents.ITEM_NAME, Component.literal(name));
{% endcase %}
        }
    }

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}
{% when "1.21", "1.21.1" %}
    public static String getProgramName(ItemStack stack) {

        return stack.getOrDefault(DataComponents.ITEM_NAME, Component.empty()).getString();
    }

{% when "26.1.2" %}
    public static String getProgramName(DataComponentGetter components) {

        return components.getOrDefault(DataComponents.ITEM_NAME, Component.empty()).getString();
    }

{% endcase %}
    @Override
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
    public InteractionResultHolder<ItemStack> use(
{% when "26.1.2" %}
    public InteractionResult use(
{% endcase %}
            Level pLevel,
            Player pPlayer,
            InteractionHand pUsedHand
    ) {

        var stack = pPlayer.getItemInHand(pUsedHand);
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
        if (pLevel.isClientSide) {
{% when "26.1.2" %}
        if (pLevel.isClientSide()) {
{% endcase %}
            SFMScreenChangeHelpers.showProgramEditScreen(new SFMTextEditScreenDiskOpenContext(
                    getProgramString(stack),
                    LabelPositionHolder.from(stack),
                    newProgramString -> SFMPackets.sendToServer(new ServerboundDiskItemSetProgramPacket(
                            newProgramString,
                            pUsedHand
                    ))
            ));
        }
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
        return InteractionResultHolder.sidedSuccess(stack, pLevel.isClientSide());
{% when "26.1.2" %}
        return InteractionResult.SUCCESS;
{% endcase %}
    }

    @Override
    public Component getName(ItemStack stack) {

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.tooltip_mode_override %}
        if (SFMItemUtils.isClientAndMoreInfoRequested()) return super.getName(stack);
{% else %}
        if (SFMEnvironmentUtils.isClient()) {
            if (SFMKeyMappings.isKeyDown(SFMKeyMappings.MORE_INFO_TOOLTIP_KEY))
                return super.getName(stack);
        }
{% endif %}
{% else %}
        if (SFMEnvironmentUtils.isClient()) {
            if (SFMKeyMappings.isKeyDown(SFMKeyMappings.MORE_INFO_TOOLTIP_KEY))
                return super.getName(stack);
        }
{% endcase %}
        var name = getProgramName(stack);
        if (name.isEmpty()) return super.getName(stack);
        return Component.literal(name).withStyle(ChatFormatting.AQUA);
    }

    @Override
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}
    public void appendHoverText(
            ItemStack stack,
            @Nullable Level level,
            List<Component> lines,
            TooltipFlag detail
    ) {

        var program = getProgramString(stack);
{% when "1.21", "1.21.1" %}
    public void appendHoverText(
            ItemStack stack,
            TooltipContext context,
            List<Component> lines,
            TooltipFlag detail
    ) {

        String program = DiskItem.getProgramString(stack);
{% when "26.1.2" %}
    public void addToTooltip(
            TooltipContext context,
            Consumer<Component> consumer,
            TooltipFlag flag,
            DataComponentGetter components
    ) {

        String program = components.getOrDefault(SFMDataComponents.PROGRAM_STRING.get(), "");
{% endcase %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.tooltip_mode_override %}
        if (SFMItemUtils.isClientAndMoreInfoRequested() && !program.isEmpty()) {
{% else %}
        if (SFMItemUtils.isClientAndMoreInfoKeyPressed() && !program.isEmpty()) {
{% endif %}
{% else %}
        if (SFMItemUtils.isClientAndMoreInfoKeyPressed() && !program.isEmpty()) {
{% endcase %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}
            lines.add(SFMItemUtils.getRainbow(getName(stack).getString().length()));
            lines.addAll(ProgramSyntaxHighlightingHelper.withSyntaxHighlighting(program, false));
{% when "1.21", "1.21.1" %}
            // show the program
            lines.add(SFMItemUtils.getRainbow(getName(stack).getString().length()));
            lines.addAll(ProgramSyntaxHighlightingHelper.withSyntaxHighlighting(program, false));
{% when "26.1.2" %}
            consumer.accept(SFMItemUtils.getRainbow(DiskItem.getProgramName(components).length()));
            ProgramSyntaxHighlightingHelper.withSyntaxHighlighting(program, false)
                    .forEach(consumer);
{% endcase %}
        } else {
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
            lines.addAll(LabelPositionHolder.from(stack).asHoverText());
            getErrors(stack)
{% when "26.1.2" %}
            LabelPositionHolder.from(components).asHoverText()
                    .forEach(consumer);
            DiskItem.getErrors(components)
{% endcase %}
                    .stream()
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}
                    .map(MutableComponent::create)
{% when "1.21", "1.21.1", "26.1.2" %}
                    .map(Component::copy)
{% endcase %}
                    .map(line -> line.withStyle(ChatFormatting.RED))
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
                    .forEach(lines::add);
            getWarnings(stack)
{% when "26.1.2" %}
                    .forEach(consumer);
            DiskItem.getWarnings(components)
{% endcase %}
                    .stream()
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}
                    .map(MutableComponent::create)
{% when "1.21", "1.21.1", "26.1.2" %}
                    .map(Component::copy)
{% endcase %}
                    .map(line -> line.withStyle(ChatFormatting.YELLOW))
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
                    .forEach(lines::add);
{% when "26.1.2" %}
                    .forEach(consumer);
{% endcase %}
            if (!program.isEmpty()) {
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
                SFMItemUtils.appendMoreInfoKeyReminderTextIfOnClient(lines);
{% when "26.1.2" %}
                SFMItemUtils.appendMoreInfoKeyReminderTextIfOnClient(consumer);
{% endcase %}
            }
        }
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}
        if (program.isEmpty()) {
            lines.add(DISK_EDIT_IN_HAND_TOOLTIP.getComponent().withStyle(ChatFormatting.GRAY));
{% when "1.21", "1.21.1" %}
        if (!program.isEmpty()) {
            lines.add(DISK_EDIT_IN_HAND_TOOLTIP.getComponent().withStyle(ChatFormatting.GRAY));
{% when "26.1.2" %}
        if (!program.isEmpty()) {
            consumer.accept(DiskItem.DISK_EDIT_IN_HAND_TOOLTIP.getComponent().withStyle(ChatFormatting.GRAY));
{% endcase %}
        }
    }
}
