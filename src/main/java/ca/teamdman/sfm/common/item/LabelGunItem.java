package ca.teamdman.sfm.common.item;

import ca.teamdman.sfm.client.ClientLabelGunWarningHelper;
import ca.teamdman.sfm.client.handler.LabelGunKeyMappingHandler;
import ca.teamdman.sfm.client.registry.SFMKeyMappings;
import ca.teamdman.sfm.client.screen.SFMScreenChangeHelpers;
import ca.teamdman.sfm.common.block.ManagerBlock;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.localization.LocalizationKeys;
import ca.teamdman.sfm.common.net.ServerboundLabelGunUsePacket;
import ca.teamdman.sfm.common.util.SFMItemUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagInt;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import vswe.superfactory.blocks.BlockManager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class LabelGunItem extends Item {
    public LabelGunItem() {
        super();
        setMaxStackSize(1);
    }

    public static void setActiveLabel(
            ItemStack stack,
            @Nullable String label
    ) {
        if (label == null || label.isEmpty()) {
            clearActiveLabel(stack);
        } else {
            LabelPositionHolder.from(stack).addReferencedLabel(label).save(stack);
            stack.setTagInfo("sfm:active_label", new NBTTagString(label));
        }
    }

    public static void clearActiveLabel(
            ItemStack gun
    ) {
        gun.removeSubCompound("sfm:active_label");
    }

    public static String getActiveLabel(ItemStack stack) {
        //noinspection DataFlowIssue
        return !stack.hasTagCompound() ? "" : stack.getTagCompound().getString("sfm:active_label");
    }

    public static String getNextLabel(
            ItemStack gun,
            int change
    ) {
        var labels = LabelPositionHolder
                .from(gun)
                .labels()
                .keySet()
                .stream()
                .sorted(Comparator.naturalOrder())
                .collect(Collectors.toList());
        if (labels.isEmpty()) return "";
        var currentLabel = getActiveLabel(gun);

        int currentLabelIndex = 0;
        for (int i = 0; i < labels.size(); i++) {
            if (labels.get(i).equals(currentLabel)) {
                currentLabelIndex = i;
                break;
            }
        }

        int nextLabelIndex = currentLabelIndex + change;
        // ensure going negative wraps around
        nextLabelIndex = ((nextLabelIndex % labels.size()) + labels.size()) % labels.size();

        return labels.get(nextLabelIndex);
    }

    /**
     * Returns the current enum mode for the label gun item.
     */
    public static LabelGunViewMode getViewMode(ItemStack stack) {
        int ordinal = stack.getTagCompound() != null ? stack.getTagCompound().getInteger("sfm:label_gun_view_mode") : 0;
        // fallback if out of bounds or missing
        if (ordinal < 0 || ordinal >= LabelGunViewMode.values().length) {
            return LabelGunViewMode.SHOW_ALL;
        }
        return LabelGunViewMode.values()[ordinal];
    }

    /**
     * Sets the view mode in NBT.
     */
    public static void setViewMode(
            ItemStack stack,
            LabelGunViewMode mode
    ) {
        stack.setTagInfo("sfm:label_gun_view_mode", new NBTTagInt(mode.ordinal()));
    }

    public static void cycleViewMode(ItemStack stack) {
        LabelGunViewMode current = getViewMode(stack);
        int nextOrdinal = (current.ordinal() + 1) % LabelGunViewMode.values().length;
        setViewMode(stack, LabelGunViewMode.values()[nextOrdinal]);
    }


    @Override
    public EnumActionResult onItemUseFirst(
            EntityPlayer player,
            World world,
            BlockPos pos,
            EnumFacing side,
            float hitX,
            float hitY,
            float hitZ,
            EnumHand hand
    ) {
        if (world.isRemote && player != null) {
            boolean pickBlock = SFMKeyMappings.isKeyDown(SFMKeyMappings.LABEL_GUN_PICK_BLOCK_MODIFIER_KEY);
            boolean contiguous = SFMKeyMappings.isKeyDown(SFMKeyMappings.LABEL_GUN_CONTIGUOUS_MODIFIER_KEY);
            boolean clear = SFMKeyMappings.isKeyDown(SFMKeyMappings.LABEL_GUN_CLEAR_MODIFIER_KEY);
            boolean pull = SFMKeyMappings.isKeyDown(SFMKeyMappings.LABEL_GUN_PULL_MODIFIER_KEY);
            boolean targetManager = SFMKeyMappings.isKeyDown(SFMKeyMappings.LABEL_GUN_TARGET_MANAGER_MODIFIER_KEY);

            if (targetManager && !(world.getBlockState(pos).getBlock() instanceof ManagerBlock || world.getBlockState(pos).getBlock() instanceof BlockManager)) {
                SFMScreenChangeHelpers.showLabelGunScreen(player.getHeldItem(hand), hand);
                return EnumActionResult.SUCCESS;
            }

            ServerboundLabelGunUsePacket msg = new ServerboundLabelGunUsePacket(
                    hand,
                    pos,
                    contiguous,
                    pickBlock,
                    clear,
                    pull,
                    targetManager
            );
            ClientLabelGunWarningHelper.sendLabelGunUsePacketFromClientWithConfirmationIfNecessary(msg, player);
            if (pickBlock) {
                // we don't want to toggle the overlay if we're using pick-block
                LabelGunKeyMappingHandler.setExternalDebounce();
            }
            return EnumActionResult.SUCCESS;
        }
        return EnumActionResult.SUCCESS;
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(
            World world,
            EntityPlayer player,
            EnumHand hand
    ) {
        ItemStack stack = player.getHeldItem(hand);

        if (world.isRemote) {
            SFMScreenChangeHelpers.showLabelGunScreen(stack, hand);
        }
        return ActionResult.newResult(EnumActionResult.SUCCESS, stack);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(
            ItemStack stack,
            @Nullable World level,
            List<String> lines,
            ITooltipFlag detail
    ) {
        ArrayList<ITextComponent> textComponentStrings = new ArrayList<>();
        if (SFMItemUtils.isClientAndMoreInfoKeyPressed()) {
            GameSettings options = Minecraft.getMinecraft().gameSettings;
            lines.add(
                    LocalizationKeys.LABEL_GUN_ITEM_TOOLTIP_TOGGLE_LABEL_REMINDER.get(
                            SFMKeyMappings.getKeyDisplay(options.keyBindUseItem)
                    ).setStyle(new Style().setColor(TextFormatting.GRAY)).getFormattedText()
            );
            lines.add(
                    LocalizationKeys.LABEL_GUN_ITEM_TOOLTIP_CLEAR_REMINDER.getComponent(
                            SFMKeyMappings.getKeyDisplay(SFMKeyMappings.LABEL_GUN_PULL_MODIFIER_KEY),
                            SFMKeyMappings.getKeyDisplay(options.keyBindUseItem)
                    ).setStyle(new Style().setColor(TextFormatting.GRAY)).getFormattedText()
            );
            lines.add(
                    LocalizationKeys.LABEL_GUN_ITEM_TOOLTIP_PULL_REMINDER.getComponent(
                            SFMKeyMappings.getKeyDisplay(SFMKeyMappings.LABEL_GUN_PULL_MODIFIER_KEY),
                            SFMKeyMappings.getKeyDisplay(options.keyBindUseItem)
                    ).setStyle(new Style().setColor(TextFormatting.GRAY)).getFormattedText()
            );
            lines.add(
                    LocalizationKeys.LABEL_GUN_ITEM_TOOLTIP_PUSH_REMINDER.getComponent(
                            SFMKeyMappings.getKeyDisplay(options.keyBindUseItem)
                    ).setStyle(new Style().setColor(TextFormatting.GRAY)).getFormattedText()
            );
            lines.add(
                    LocalizationKeys.LABEL_GUN_ITEM_TOOLTIP_TARGET_MANAGER_REMINDER.getComponent(
                            SFMKeyMappings.getKeyDisplay(SFMKeyMappings.LABEL_GUN_TARGET_MANAGER_MODIFIER_KEY),
                            SFMKeyMappings.getKeyDisplay(options.keyBindUseItem)
                    ).setStyle(new Style().setColor(TextFormatting.GRAY)).getFormattedText()
            );
            lines.add(
                    LocalizationKeys.LABEL_GUN_ITEM_TOOLTIP_CONTIGUOUS_REMINDER.getComponent(
                            SFMKeyMappings.getKeyDisplay(SFMKeyMappings.LABEL_GUN_CONTIGUOUS_MODIFIER_KEY)
                    ).setStyle(new Style().setColor(TextFormatting.GRAY)).getFormattedText()
            );
            lines.add(
                    LocalizationKeys.LABEL_GUN_ITEM_TOOLTIP_PICK_REMINDER.getComponent(
                            SFMKeyMappings.getKeyDisplay(SFMKeyMappings.LABEL_GUN_PICK_BLOCK_MODIFIER_KEY),
                            SFMKeyMappings.getKeyDisplay(options.keyBindUseItem)
                    ).setStyle(new Style().setColor(TextFormatting.GRAY)).getFormattedText()
            );
            lines.add(
                    LocalizationKeys.LABEL_GUN_ITEM_TOOLTIP_NEXT_REMINDER.getComponent(
                            SFMKeyMappings.getKeyDisplay(SFMKeyMappings.LABEL_GUN_NEXT_LABEL_KEY)
                    ).setStyle(new Style().setColor(TextFormatting.GRAY)).getFormattedText()
            );
            lines.add(
                    LocalizationKeys.LABEL_GUN_ITEM_TOOLTIP_PREVIOUS_REMINDER.getComponent(
                            SFMKeyMappings.getKeyDisplay(SFMKeyMappings.LABEL_GUN_PREVIOUS_LABEL_KEY)
                    ).setStyle(new Style().setColor(TextFormatting.GRAY)).getFormattedText()
            );
            lines.add(
                    LocalizationKeys.LABEL_GUN_ITEM_TOOLTIP_SCROLL_REMINDER.getComponent(
                            SFMKeyMappings.getKeyDisplay(SFMKeyMappings.LABEL_GUN_SCROLL_MODIFIER_KEY)
                    ).setStyle(new Style().setColor(TextFormatting.GRAY)).getFormattedText()
            );
            lines.add(
                    LocalizationKeys.LABEL_GUN_ITEM_TOOLTIP_CYCLE_VIEW_REMINDER.getComponent(
                            SFMKeyMappings.getKeyDisplay(SFMKeyMappings.CYCLE_LABEL_VIEW_KEY)
                    ).setStyle(new Style().setColor(TextFormatting.GRAY)).getFormattedText()
            );
            lines.add(
                    LocalizationKeys.LABEL_GUN_ITEM_TOOLTIP_GUI_REMINDER.getComponent(
                            SFMKeyMappings.getKeyDisplay(options.keyBindUseItem)
                    ).setStyle(new Style().setColor(TextFormatting.GRAY)).getFormattedText()
            );
        } else {
            SFMItemUtils.appendMoreInfoKeyReminderTextIfOnClient(lines);
            lines.addAll(LabelPositionHolder.from(stack).asHoverText());
        }
    }

    @NotNull
    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        var stack = player.getHeldItem(hand);
        if (world.isRemote) {
            SFMScreenChangeHelpers.showLabelGunScreen(stack, hand);
        }
        return EnumActionResult.SUCCESS;
    }


    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        var name = getActiveLabel(stack);
        if (name.isEmpty()) return super.getItemStackDisplayName(stack);
        return LocalizationKeys.LABEL_GUN_ITEM_NAME_WITH_LABEL
                .getComponent(new TextComponentString(name).setStyle(new Style().setBold(true)))
                .setStyle(new Style().setColor(TextFormatting.AQUA)).getFormattedText();
    }

    public static void clearAll(ItemStack stack) {
        LabelPositionHolder.clear(stack);
        LabelGunItem.setActiveLabel(stack, null);
    }

    public enum LabelGunViewMode {
        SHOW_ALL,
        SHOW_ONLY_ACTIVE_LABEL_AND_TARGETED_BLOCK,
        SHOW_ONLY_TARGETED_BLOCK,
    }
}
