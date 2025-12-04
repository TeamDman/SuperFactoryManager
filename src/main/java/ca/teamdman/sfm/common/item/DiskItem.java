package ca.teamdman.sfm.common.item;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import ca.teamdman.sfm.client.ProgramSyntaxHighlightingHelper;
import ca.teamdman.sfm.client.registry.SFMKeyMappings;
import ca.teamdman.sfm.common.util.SFMEnvironmentUtils;
import ca.teamdman.sfm.common.util.SFMItemUtils;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.*;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

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
        if (getProgram(stack).trim().isEmpty() && LabelPositionHolder.from(stack).isEmpty()) {
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
                ? StreamSupport.stream(stack
                        .getTagCompound()
                        .getTagList("sfm:errors", Constants.NBT.TAG_COMPOUND).spliterator(), false)
                .map(NBTTagCompound.class::cast)
                .map(SFMTranslationUtils::deserializeTranslation)
                .collect(Collectors.toList())
                : Collections.emptyList();
    }

    public static void setErrors(
            ItemStack stack,
            List<TextComponentTranslation> errors
    ) {
        stack.setTagInfo(
                "sfm:errors",
                errors
                        .stream()
                        .map(SFMTranslationUtils::serializeTranslation)
                        .collect(NBTTagList::new, NBTTagList::appendTag, (NBTTagList a, NBTTagList b) -> {
                            var tag = new NBTTagList();
                            for (var t : a) {
                                tag.appendTag(t);
                            }
                            for (var t : b) {
                                tag.appendTag(t);
                            }
                        })
        );
    }

    public static List<TextComponentTranslation> getWarnings(ItemStack stack) {
        if (!stack.hasTagCompound()) {
            stack.setTagCompound(new NBTTagCompound());
        }
        assert stack.getTagCompound() != null;
        return StreamSupport.stream(stack
                        .getTagCompound()
                        .getTagList("sfm:warnings", Constants.NBT.TAG_COMPOUND).spliterator(), false
                )
                .map(NBTTagCompound.class::cast)
                .map(SFMTranslationUtils::deserializeTranslation)
                .collect(Collectors.toList());
    }

    public static void setWarnings(
            ItemStack stack,
            List<TextComponentTranslation> warnings
    ) {
        stack.setTagInfo(
                "sfm:warnings",
                warnings
                        .stream()
                        .map(SFMTranslationUtils::serializeTranslation)
                        .collect(NBTTagList::new, NBTTagList::appendTag, (NBTTagList a, NBTTagList b) -> {
                            var tag = new NBTTagList();
                            for (var t : a) {
                                tag.appendTag(t);
                            }
                            for (var t : b) {
                                tag.appendTag(t);
                            }
                        })
        );
    }

    public static String getProgramName(ItemStack stack) {
        return stack.getTagCompound() != null ? stack.getTagCompound().getString("sfm:name") : "";
    }

    public static void setProgramName(
            ItemStack stack,
            String name
    ) {
        if (stack.getItem() instanceof DiskItem) {
            stack.setTagInfo("sfm:name", new NBTTagString(name));
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
                            hand
                    ))
            ));
        }
        return super.onItemUse(player, worldIn, pos, hand, facing, hitX, hitY, hitZ);
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        var stack = player.getHeldItem(hand);
        if (world.isRemote) {
            SFMScreenChangeHelpers.showProgramEditScreen(new SFMTextEditScreenDiskOpenContext(
                    getProgram(stack),
                    LabelPositionHolder.from(stack),
                    newProgramString -> SFMPackets.sendToServer(new ServerboundDiskItemSetProgramPacket(
                            newProgramString,
                            hand
                    ))
            ));
        }
        return ActionResult.newResult(EnumActionResult.SUCCESS, stack);
    }
    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        if (SFMEnvironmentUtils.isClient()) {
            if (SFMKeyMappings.isKeyDown(SFMKeyMappings.MORE_INFO_TOOLTIP_KEY))
                return super.getItemStackDisplayName(stack);
        }
        var name = getProgramName(stack);
        if (name.isEmpty()) return super.getItemStackDisplayName(stack);
        return new TextComponentString(name).setStyle(new Style().setColor(TextFormatting.AQUA)).getFormattedText();// Component.literal(name).withStyle(ChatFormatting.AQUA);
    }

    @Override
       public void addInformation(ItemStack stack,            @Nullable World worldIn,            List<String> lines, ITooltipFlag flagIn) {
        super.addInformation(stack, worldIn, lines, flagIn);

        var program = getProgram(stack);
        if (SFMItemUtils.isClientAndMoreInfoKeyPressed() && !program.isEmpty()) {
                       lines.add(SFMItemUtils.getRainbow(super.getItemStackDisplayName(stack).length()).getFormattedText());
                       lines.addAll(ProgramSyntaxHighlightingHelper.withSyntaxHighlighting(program, false).stream().map(ITextComponent::getFormattedText).collect(Collectors.toList()));
        } else {
            lines.addAll(LabelPositionHolder.from(stack).asHoverText());
            getErrors(stack)
                    .stream()
                                       .map(line -> line.setStyle(line.getStyle().setColor(TextFormatting.RED)).getFormattedText())
                    .forEach(lines::add);
            getWarnings(stack)
                    .stream()
                                       .map(line -> line.setStyle(line.getStyle().setColor(TextFormatting.YELLOW)).getFormattedText())
                    .forEach(lines::add);
            if (!program.isEmpty()) {
                SFMItemUtils.appendMoreInfoKeyReminderTextIfOnClient(lines);
            }
        }
        if (program.isEmpty()) {
                       lines.add(LocalizationKeys.DISK_EDIT_IN_HAND_TOOLTIP.getComponent().setStyle(new Style().setColor(TextFormatting.GRAY)).getFormattedText());
        }
       }
}
