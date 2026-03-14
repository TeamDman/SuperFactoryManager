package ca.teamdman.sfm.common.item;

import ca.teamdman.sfm.client.registry.SFMKeyMappings;
import ca.teamdman.sfm.client.screen.SFMScreenChangeHelpers;
import ca.teamdman.sfm.client.text_editor.SFMTextEditScreenOpenContext;
import ca.teamdman.sfm.client.text_editor.TextEditScreenContentLanguage;
import ca.teamdman.sfm.client.text_styling.ProgramSyntaxHighlightingHelper;
import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.localization.LocalizationKeys;
import ca.teamdman.sfm.common.logging.TranslatableLogger;
import ca.teamdman.sfm.common.net.ServerboundDiskItemSetProgramPacket;
import ca.teamdman.sfm.common.program.linting.ProgramLinter;
import ca.teamdman.sfm.common.registry.SFMCreativeTabs;
import ca.teamdman.sfm.common.registry.SFMPackets;
import ca.teamdman.sfm.common.timing.SFMInstant;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import ca.teamdman.sfm.common.util.SFMEnvironmentUtils;
import ca.teamdman.sfm.common.util.SFMItemUtils;
import ca.teamdman.sfm.common.util.SFMTranslationUtils;
import ca.teamdman.sfml.ast.SFMLProgram;
import ca.teamdman.sfml.program_builder.SFMLProgramBuildResult;
import ca.teamdman.sfml.program_builder.SFMLProgramBuilder;
import ca.teamdman.sfml.program_builder.SFMLProgramMetadata;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class DiskItem extends Item {
    public DiskItem() {

        super(new Properties().tab(SFMCreativeTabs.TAB));
    }

    public static String getProgramString(ItemStack stack) {

        return stack
                .getOrCreateTag()
                .getString("sfm:program");
    }

    public static void setProgram(
            ItemStack stack,
            String program
    ) {

        program = program.replaceAll("\r", "");
        stack
                .getOrCreateTag()
                .putString("sfm:program", program);
    }

    /// Ensure disks can stack after being used and cleared.
    @MCVersionDependentBehaviour
    public static void pruneIfDefault(ItemStack stack) {

        if (getProgramString(stack).isBlank() && LabelPositionHolder.from(stack).isEmpty()) {
            for (String key : stack.getOrCreateTag().getAllKeys().toArray(String[]::new)) {
                stack.removeTagKey(key);
            }
        }
    }

    /// Build the {@link SFMLProgram} contained in the given {@link DiskItem}.
    /// @param manager used for logging and running {@link ProgramLinter}s.
    public static @Nullable SFMLProgram rebuildSfmlProgram(
            ItemStack stack,
            @Nullable ManagerBlockEntity manager,
            boolean shouldRebuildWarnings
    ) {
        // Identify logger if present
        final TranslatableLogger logger;
        if (manager == null) {
            logger = null;
        } else {
            logger = manager.logger;
        }

        if (logger != null) {
            // Log beginning
            logger.info(x -> x.accept(LocalizationKeys.PROGRAM_BUILD_BEGIN.get()));
        }

        // Get the program source code
        String programString = getProgramString(stack);

        // Start stopwatch
        SFMInstant start = SFMInstant.now();

        // Build the program
        SFMLProgramBuildResult buildResult = new SFMLProgramBuilder(programString).build();
        SFMLProgramMetadata metadata = buildResult.metadata();

        if (buildResult.isSuccess()) {
            // Build was successful
            SFMLProgram successProgram = buildResult.unwrapProgram();

            // Update warnings
            final Collection<TranslatableContents> warnings;
            if (shouldRebuildWarnings) {
                warnings = ProgramLinter.gatherWarnings(
                        successProgram,
                        LabelPositionHolder.from(stack),
                        manager
                );
                setWarnings(stack, warnings);
            } else {
                warnings = new ArrayList<>();
            }

            // Stop stopwatch
            Duration elapsed = start.elapsed();

            if (logger != null) {
                // Log build success
                logger.info(x -> x.accept(LocalizationKeys.PROGRAM_BUILD_SUCCESS_WITH_WARNING_COUNT_AND_ELAPSED_DURATION.get(
                        successProgram.name(),
                        warnings.size(),
                        elapsed
                )));

                // Log each warning
                logger.warn(warnings::forEach);
            }

            // Update disk properties
            setProgramName(stack, successProgram.name());
            setErrors(stack, Collections.emptyList());

        } else {
            // Build was unsuccessful
            List<TranslatableContents> warnings = Collections.emptyList();
            List<TranslatableContents> errors = metadata.errors();

            // Stop stopwatch
            Duration elapsed = start.elapsed();

            if (logger != null) {
                // Log to disk
                logger.error(x -> x.accept(LocalizationKeys.PROGRAM_BUILD_FAILURE_WITH_ERROR_COUNT_AND_ELAPSED_DURATION.get(
                        errors.size(),
                        elapsed
                )));

                // Log each error
                logger.error(errors::forEach);
            }

            // Update disk properties
            setWarnings(stack, warnings);
            setErrors(stack, errors);
        }

        // Clear
        DiskItem.pruneIfDefault(stack);

        return buildResult.maybeProgram();
    }

    public static List<TranslatableContents> getErrors(ItemStack stack) {

        return stack
                .getOrCreateTag()
                .getList("sfm:errors", Tag.TAG_COMPOUND)
                .stream()
                .map(CompoundTag.class::cast)
                .map(SFMTranslationUtils::deserializeTranslation)
                .toList();
    }

    public static void setErrors(
            ItemStack stack,
            List<TranslatableContents> errors
    ) {

        stack
                .getOrCreateTag()
                .put(
                        "sfm:errors",
                        errors
                                .stream()
                                .map(SFMTranslationUtils::serializeTranslation)
                                .collect(ListTag::new, ListTag::add, ListTag::addAll)
                );
    }

    public static List<TranslatableContents> getWarnings(ItemStack stack) {

        return stack
                .getOrCreateTag()
                .getList("sfm:warnings", Tag.TAG_COMPOUND)
                .stream()
                .map(CompoundTag.class::cast)
                .map(SFMTranslationUtils::deserializeTranslation)
                .collect(
                        Collectors.toList());
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

        stack
                .getOrCreateTag()
                .put(
                        "sfm:warnings",
                        warnings
                                .stream()
                                .map(SFMTranslationUtils::serializeTranslation)
                                .collect(ListTag::new, ListTag::add, ListTag::addAll)
                );
    }

    public static String getProgramName(ItemStack stack) {

        return stack
                .getOrCreateTag()
                .getString("sfm:name");
    }

    public static void setProgramName(
            ItemStack stack,
            String name
    ) {

        if (stack.getItem() instanceof DiskItem) {
            stack
                    .getOrCreateTag()
                    .putString("sfm:name", name);
        }
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(
            Level pLevel,
            Player pPlayer,
            InteractionHand pUsedHand
    ) {

        var stack = pPlayer.getItemInHand(pUsedHand);
        if (pLevel.isClientSide) {
            SFMScreenChangeHelpers.showPreferredTextEditScreen(new SFMTextEditScreenOpenContext(
                    getProgramString(stack),
                    LabelPositionHolder.from(stack),
                    TextEditScreenContentLanguage.SFML,
                    newProgramString -> SFMPackets.sendToServer(new ServerboundDiskItemSetProgramPacket(
                            newProgramString,
                            pUsedHand
                    ))
            ));
        }
        return InteractionResultHolder.sidedSuccess(stack, pLevel.isClientSide());
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
    public void appendHoverText(
            ItemStack stack,
            @Nullable Level level,
            List<Component> lines,
            TooltipFlag detail
    ) {

        var program = getProgramString(stack);
        if (SFMItemUtils.isClientAndMoreInfoKeyPressed() && !program.isEmpty()) {
            lines.add(SFMItemUtils.getRainbow(getName(stack).getString().length()));
            lines.addAll(ProgramSyntaxHighlightingHelper.withSyntaxHighlighting(
                    program,
                    false,
                    TextEditScreenContentLanguage.SFML
            ));
        } else {
            lines.addAll(LabelPositionHolder.from(stack).asHoverText());
            getErrors(stack)
                    .stream()
                    .map(MutableComponent::create)
                    .map(line -> line.withStyle(ChatFormatting.RED))
                    .forEach(lines::add);
            getWarnings(stack)
                    .stream()
                    .map(MutableComponent::create)
                    .map(line -> line.withStyle(ChatFormatting.YELLOW))
                    .forEach(lines::add);
            if (!program.isEmpty()) {
                SFMItemUtils.appendMoreInfoKeyReminderTextIfOnClient(lines);
            }
        }
        if (program.isEmpty()) {
            lines.add(LocalizationKeys.DISK_EDIT_IN_HAND_TOOLTIP.getComponent().withStyle(ChatFormatting.GRAY));
        }
    }

}
