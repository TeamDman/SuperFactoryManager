package ca.teamdman.sfm.common.item;

import ca.teamdman.sfm.client.ClientStuff;
import ca.teamdman.sfm.client.ProgramSyntaxHighlightingHelper;
import ca.teamdman.sfm.client.registry.SFMKeyMappings;
import ca.teamdman.sfm.common.Constants;
import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.net.ServerboundDiskItemSetProgramPacket;
import ca.teamdman.sfm.common.program.LabelPositionHolder;
import ca.teamdman.sfm.common.program.ProgramLinter;
import ca.teamdman.sfm.common.registry.SFMItems;
import ca.teamdman.sfm.common.registry.SFMPackets;
import ca.teamdman.sfm.common.util.SFMUtils;
import ca.teamdman.sfml.ast.Program;
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
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.DistExecutor;
import net.neoforged.fml.loading.FMLEnvironment;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class DiskItem extends Item {
    public DiskItem() {
        super(new Item.Properties());
    }

    public static String getProgram(ItemStack stack) {
        return stack
                .getOrCreateTag()
                .getString("sfm:program");
    }

    public static void setProgram(ItemStack stack, String program) {
        stack
                .getOrCreateTag()
                .putString("sfm:program", program.replaceAll("\r", ""));

    }

    public static Optional<Program> compileAndUpdateErrorsAndWarnings(ItemStack stack, @Nullable ManagerBlockEntity manager) {
        if (manager != null) {
            manager.logger.info(x -> x.accept(Constants.LocalizationKeys.PROGRAM_COMPILE_FROM_DISK_BEGIN.get()));
        }
        AtomicReference<Program> rtn = new AtomicReference<>(null);
        Program.compile(
                getProgram(stack),
                successProgram -> {
                    ArrayList<TranslatableContents> warnings = ProgramLinter.gatherWarnings(successProgram, LabelPositionHolder.from(stack), manager);

                    // Log to disk
                    if (manager != null) {
                        manager.logger.info(x -> x.accept(Constants.LocalizationKeys.PROGRAM_COMPILE_SUCCEEDED_WITH_WARNINGS.get(
                                successProgram.name(),
                                warnings.size()
                        )));
                        manager.logger.warn(warnings::forEach);
                    }

                    // Update disk properties
                    setProgramName(stack, successProgram.name());
                    setWarnings(stack, warnings);
                    setErrors(stack, Collections.emptyList());

                    // Track result
                    rtn.set(successProgram);
                },
                errors -> {
                    List<TranslatableContents> warnings = Collections.emptyList();

                    // Log to disk
                    if (manager != null) {
                        manager.logger.error(x -> x.accept(Constants.LocalizationKeys.PROGRAM_COMPILE_FAILED_WITH_ERRORS.get(
                                errors.size())));
                        manager.logger.error(errors::forEach);
                    }

                    // Update disk properties
                    setWarnings(stack, warnings);
                    setErrors(stack, errors);
                }
        );
        return Optional.ofNullable(rtn.get());
    }

    public static List<TranslatableContents> getErrors(ItemStack stack) {
        return stack
                .getOrCreateTag()
                .getList("sfm:errors", Tag.TAG_COMPOUND)
                .stream()
                .map(CompoundTag.class::cast)
                .map(SFMUtils::deserializeTranslation)
                .toList();
    }

    public static void setErrors(ItemStack stack, List<TranslatableContents> errors) {
        stack
                .getOrCreateTag()
                .put(
                        "sfm:errors",
                        errors
                                .stream()
                                .map(SFMUtils::serializeTranslation)
                                .collect(ListTag::new, ListTag::add, ListTag::addAll)
                );
    }

    public static List<TranslatableContents> getWarnings(ItemStack stack) {
        return stack
                .getOrCreateTag()
                .getList("sfm:warnings", Tag.TAG_COMPOUND)
                .stream()
                .map(CompoundTag.class::cast)
                .map(SFMUtils::deserializeTranslation)
                .collect(
                        Collectors.toList());
    }

    public static void setWarnings(ItemStack stack, List<TranslatableContents> warnings) {
        stack
                .getOrCreateTag()
                .put(
                        "sfm:warnings",
                        warnings
                                .stream()
                                .map(SFMUtils::serializeTranslation)
                                .collect(ListTag::new, ListTag::add, ListTag::addAll)
                );
    }

    public static String getProgramName(ItemStack stack) {
        return stack
                .getOrCreateTag()
                .getString("sfm:name");
    }

    public static void setProgramName(ItemStack stack, String name) {
        if (stack.getItem() instanceof DiskItem) {
            stack
                    .getOrCreateTag()
                    .putString("sfm:name", name);
        }
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(Level pLevel, Player pPlayer, InteractionHand pUsedHand) {
        var stack = pPlayer.getItemInHand(pUsedHand);
        if (pLevel.isClientSide) {
            ClientStuff.showProgramEditScreen(
                    getProgram(stack),
                    programString -> SFMPackets.DISK_ITEM_CHANNEL.sendToServer(new ServerboundDiskItemSetProgramPacket(
                                programString,
                                pUsedHand
                        ))
            );
        }
        return InteractionResultHolder.sidedSuccess(stack, pLevel.isClientSide());
    }

    @Override
    public Component getName(ItemStack stack) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            if (ClientStuff.isMoreInfoKeyDown()) return super.getName(stack);
        }
        var name = getProgramName(stack);
        if (name.isEmpty()) return super.getName(stack);
        return Component.literal(name).withStyle(ChatFormatting.AQUA);
    }

    @SuppressWarnings("UnnecessaryLocalVariable")
    @Override
    public void appendHoverText(
            ItemStack stack, @Nullable Level level, List<Component> list, TooltipFlag detail
    ) {
        if (stack.hasTag()) {
            boolean isClient = FMLEnvironment.dist.isClient();
            boolean isMoreInfoKeyDown = isClient && ClientStuff.isMoreInfoKeyDown();
            boolean showProgram = isMoreInfoKeyDown;
            if (!showProgram) {
                list.addAll(LabelPositionHolder.from(stack).asHoverText());
                getErrors(stack)
                        .stream()
                        .map(MutableComponent::create)
                        .map(line -> line.withStyle(ChatFormatting.RED))
                        .forEach(list::add);
                getWarnings(stack)
                        .stream()
                        .map(MutableComponent::create)
                        .map(line -> line.withStyle(ChatFormatting.YELLOW))
                        .forEach(list::add);
                if (isClient) {
                    list.add(Constants.LocalizationKeys.GUI_ADVANCED_TOOLTIP_HINT
                                     .getComponent(SFMKeyMappings.MORE_INFO_TOOLTIP_KEY.get().getTranslatedKeyMessage())
                                     .withStyle(ChatFormatting.AQUA));
                }
            } else {
                var program = getProgram(stack);
                if (!program.isEmpty()) {
                    var start = Component.empty();
                    ChatFormatting[] rainbowColors = new ChatFormatting[]{
                            ChatFormatting.DARK_RED,
                            ChatFormatting.RED,
                            ChatFormatting.GOLD,
                            ChatFormatting.YELLOW,
                            ChatFormatting.DARK_GREEN,
                            ChatFormatting.GREEN,
                            ChatFormatting.DARK_AQUA,
                            ChatFormatting.AQUA,
                            ChatFormatting.DARK_BLUE,
                            ChatFormatting.BLUE,
                            ChatFormatting.DARK_PURPLE,
                            ChatFormatting.LIGHT_PURPLE
                    };
                    int rainbowColorsLength = rainbowColors.length;
                    int fullCycleLength = 2 * rainbowColorsLength - 2;
                    for (int i = 0; i < getName(stack).getString().length() - 2; i++) {
                        int cyclePosition = i % fullCycleLength;
                        int adjustedIndex = cyclePosition < rainbowColorsLength
                                            ? cyclePosition
                                            : fullCycleLength - cyclePosition;
                        ChatFormatting color = rainbowColors[adjustedIndex];
                        start = start.append(Component.literal("=").withStyle(color));
                    }
                    list.add(start);
                    list.addAll(ProgramSyntaxHighlightingHelper.withSyntaxHighlighting(program, false));
                }
            }
        }
    }
}
