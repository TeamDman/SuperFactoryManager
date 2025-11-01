package ca.teamdman.sfm.common.item;

import ca.teamdman.sfm.client.ProgramSyntaxHighlightingHelper;
import ca.teamdman.sfm.client.registry.SFMKeyMappings;
import ca.teamdman.sfm.client.screen.SFMScreenChangeHelpers;
import ca.teamdman.sfm.client.text_editor.SFMTextEditScreenDiskOpenContext;
import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.localization.LocalizationKeys;
import ca.teamdman.sfm.common.net.ServerboundDiskItemSetProgramPacket;
import ca.teamdman.sfm.common.program.linting.ProgramLinter;
import ca.teamdman.sfm.common.registry.SFMPackets;
import ca.teamdman.sfm.common.util.SFMEnvironmentUtils;
import ca.teamdman.sfm.common.util.SFMItemUtils;
import ca.teamdman.sfm.common.util.SFMTranslationUtils;
import ca.teamdman.sfml.ast.Program;
import net.minecraft.ChatFormatting;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.EnumHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.World;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.util.Constants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class DiskItem extends Item {
    public DiskItem() {
        super();
    }

    public static String getProgram(ItemStack stack) {
        return stack.getTagCompound() != null ? stack.getTagCompound().getString("sfm:program") : "";
    }

    public static void setProgram(
            ItemStack stack,
            String program
    ) {
        program = program.replaceAll("\r", "");
        if (stack
                .getTagCompound() == null) {
            stack.setTagCompound(new NBTTagCompound());
        }
        stack
                .getTagCompound()
                .setString("sfm:program", program);

    }

    public static void pruneIfDefault(ItemStack stack) {
        if (getProgram(stack).isBlank() && LabelPositionHolder.from(stack).isEmpty()) {
            stack.setTagCompound(new NBTTagCompound());
        }
    }

    public static @Nullable Program compileAndUpdateErrorsAndWarnings(
            ItemStack stack,
            @Nullable ManagerBlockEntity manager
    ) {
        if (manager != null) {
            manager.logger.info(x -> x.accept(LocalizationKeys.PROGRAM_COMPILE_FROM_DISK_BEGIN.get()));
        }
        AtomicReference<Program> rtn = new AtomicReference<>(null);
        Program.compile(
                getProgram(stack),
                successProgram -> {
                    ArrayList<TextComponentTranslation> warnings = ProgramLinter.gatherWarnings(
                            successProgram,
                            LabelPositionHolder.from(stack),
                            manager
                    );

                    // Log to disk
                    if (manager != null) {
                        manager.logger.info(x -> x.accept(LocalizationKeys.PROGRAM_COMPILE_SUCCEEDED_WITH_WARNINGS.get(
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
                    List<TextComponentTranslation> warnings = Collections.emptyList();

                    // Log to disk
                    if (manager != null) {
                        manager.logger.error(x -> x.accept(LocalizationKeys.PROGRAM_COMPILE_FAILED_WITH_ERRORS.get(
                                errors.size())));
                        manager.logger.error(errors::forEach);
                    }

                    // Update disk properties
                    setWarnings(stack, warnings);
                    setErrors(stack, errors);
                }
        );
        return rtn.get();
    }

    public static List<TextComponentTranslation> getErrors(ItemStack stack) {
        return stack.getTagCompound() != null
                ? stack
                .getTagCompound()
                .getTagList("sfm:errors", Constants.NBT.TAG_COMPOUND // tag compound
                        .iterator()

                        .map(NBTTagCompound.class::cast)
                        .map(SFMTranslationUtils::deserializeTranslation)
                        .toList()
                : null;
    }

    public static void setErrors(
            ItemStack stack,
            List<TextComponentTranslation> errors
    ) {
        stack
                .getOrCreateTag()
                .put(
                        "sfm:errors",
                        errors
                                .stream()
                                .map(SFMTranslationUtils::serializeTranslation)
                                .collect(NBTTagList::new, NBTTagList::add, NBTTagList::addAll)
                );
    }

    public static List<TextComponentTranslation> getWarnings(ItemStack stack) {
        return stack
                .getOrCreateTag()
                .getList("sfm:warnings", Tag.TAG_COMPOUND)
                .stream()
                .map(NBTTagCompound.class::cast)
                .map(SFMTranslationUtils::deserializeTranslation)
                .collect(
                        Collectors.toList());
    }

    public static void setWarnings(
            ItemStack stack,
            List<TextComponentTranslation> warnings
    ) {
        stack
                .getOrCreateTag()
                .put(
                        "sfm:warnings",
                        warnings
                                .stream()
                                .map(SFMTranslationUtils::serializeTranslation)
                                .collect(NBTTagList::new, NBTTagList::add, NBTTagList::addAll)
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

    @NotNull
    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World worldIn, BlockPos pos, EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (worldIn.isRemote) {
            var stack = player.getHeldItem(hand);
            SFMScreenChangeHelpers.showProgramEditScreen(new SFMTextEditScreenDiskOpenContext(
                    getProgram(stack),
                    LabelPositionHolder.from(stack),
                    newProgramString -> SFMPackets.sendToServer(new ServerboundDiskItemSetProgramPacket(
                            newProgramString,
                            pUsedHand
                    ))
            ));
        }
        return super.onItemUse(player, worldIn, pos, hand, facing, hitX, hitY, hitZ);
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        if (world.isRemote) {
            var stack = player.getHeldItem(hand);
            SFMScreenChangeHelpers.showProgramEditScreen(new SFMTextEditScreenDiskOpenContext(
                    getProgram(stack),
                    LabelPositionHolder.from(stack),
                    newProgramString -> SFMPackets.sendToServer(new ServerboundDiskItemSetProgramPacket(
                            newProgramString,
                            hand
                    ))
            ));
        }
        return super.onItemRightClick(world, playerIn, handIn);
    }

    @NotNull
    @Override
    public EnumActionResult onItemUseFirst(EntityPlayer player, World world, BlockPos pos, EnumFacing side, float hitX, float hitY, float hitZ, EnumHand hand) {
        return super.onItemUseFirst(player, world, pos, side, hitX, hitY, hitZ, hand);
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(
            Level pLevel,
            Player pPlayer,
            EnumHand pUsedHand
    ) {
        var stack = pPlayer.getItemInHand(pUsedHand);
        if (pLevel.isClientSide) {
            SFMScreenChangeHelpers.showProgramEditScreen(new SFMTextEditScreenDiskOpenContext(
                    getProgram(stack),
                    LabelPositionHolder.from(stack),
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
        var program = getProgram(stack);
        if (SFMItemUtils.isClientAndMoreInfoKeyPressed() && !program.isEmpty()) {
            lines.add(SFMItemUtils.getRainbow(getName(stack).getString().length()));
            lines.addAll(ProgramSyntaxHighlightingHelper.withSyntaxHighlighting(program, false));
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
