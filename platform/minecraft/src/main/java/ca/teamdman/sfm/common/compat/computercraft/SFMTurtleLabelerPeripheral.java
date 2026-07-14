package ca.teamdman.sfm.common.compat.computercraft;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.item.DiskItem;
import ca.teamdman.sfm.common.item.LabelGunItem;
import ca.teamdman.sfm.common.label.LabelGunActions;
import ca.teamdman.sfml.ast.Program;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.lua.MethodResult;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.turtle.ITurtleAccess;
import dan200.computercraft.api.turtle.TurtleCommandResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.function.Predicate;

/**
 * The {@code sfm_labeler} peripheral installed by the SFM label-gun turtle upgrade.
 *
 * <p>Action methods use CC:Tweaked's turtle command queue. This keeps label actions ordered
 * with native turtle movement and resolves the turtle's selected inventory slot at execution
 * time.</p>
 */
public final class SFMTurtleLabelerPeripheral implements IPeripheral {
    public static final String TYPE = "sfm_labeler";

    private final ITurtleAccess turtle;

    SFMTurtleLabelerPeripheral(ITurtleAccess turtle) {

        this.turtle = turtle;
    }

    @Override
    public String getType() {

        return TYPE;
    }

    @Override
    public boolean equals(@Nullable IPeripheral other) {

        return other instanceof SFMTurtleLabelerPeripheral otherLabeler && otherLabeler.turtle == turtle;
    }

    @LuaFunction
    public final SFMDiskHandle disk() {

        return new SFMDiskHandle(selectedTarget(
                stack -> stack.getItem() instanceof DiskItem,
                "not_disk"
        ));
    }

    @LuaFunction
    public final SFMLabelGunHandle labelGun() {

        return new SFMLabelGunHandle(selectedTarget(
                stack -> stack.getItem() instanceof LabelGunItem,
                "not_label_gun"
        ));
    }

    @LuaFunction
    public final MethodResult toggle(
            String direction,
            Optional<Boolean> contiguous
    ) {

        return executeLabelAction(direction, (level, gun, target) -> LabelGunActions.toggle(
                level,
                gun,
                target,
                contiguous.orElse(false)
        ));
    }

    @LuaFunction
    public final MethodResult clearActive(
            String direction,
            Optional<Boolean> contiguous
    ) {

        return executeLabelAction(direction, (level, gun, target) -> LabelGunActions.clearActive(
                level,
                gun,
                target,
                contiguous.orElse(false)
        ));
    }

    @LuaFunction
    public final MethodResult clearAll(
            String direction,
            Optional<Boolean> contiguous
    ) {

        return executeLabelAction(direction, (level, gun, target) -> LabelGunActions.clearAll(
                level,
                gun,
                target,
                contiguous.orElse(false)
        ));
    }

    @LuaFunction
    public final MethodResult pick(
            String direction,
            Optional<Boolean> contiguous
    ) {

        return executeLabelAction(direction, (level, gun, target) -> LabelGunActions.pick(
                level,
                gun,
                target,
                contiguous.orElse(false)
        ));
    }

    @LuaFunction
    public final MethodResult push(String direction) {

        return executeManagerAction(direction, LabelGunActions::push);
    }

    @LuaFunction
    public final MethodResult pull(String direction) {

        return executeManagerAction(direction, LabelGunActions::pull);
    }

    private MethodResult executeLabelAction(
            String direction,
            LabelAction action
    ) {

        if (relativeDirection(direction) == null) {
            return MethodResult.of(false, "invalid_direction");
        }
        return turtle.executeCommand(access -> {
            if (access.isRemoved()) {
                return TurtleCommandResult.failure("target_changed");
            }
            ItemStack gun = selectedLabelGun(access);
            if (gun == null) {
                return TurtleCommandResult.failure("not_label_gun");
            }
            Direction worldDirection = relativeDirection(access, direction);
            LabelGunActions.LabelGunActionResult result = action.apply(
                    access.getLevel(),
                    gun,
                    access.getPosition().relative(worldDirection)
            );
            if (!result.success()) {
                return TurtleCommandResult.failure(result.errorCode());
            }
            access.getInventory().setChanged();
            return TurtleCommandResult.success(new Object[]{true});
        });
    }

    private MethodResult executeManagerAction(
            String direction,
            ManagerAction action
    ) {

        if (relativeDirection(direction) == null) {
            return MethodResult.of(false, "invalid_direction");
        }
        return turtle.executeCommand(access -> {
            if (access.isRemoved()) {
                return TurtleCommandResult.failure("target_changed");
            }
            ItemStack gun = selectedLabelGun(access);
            if (gun == null) {
                return TurtleCommandResult.failure("not_label_gun");
            }
            BlockPos target = access.getPosition().relative(relativeDirection(access, direction));
            if (!(access.getLevel().getBlockEntity(target) instanceof ManagerBlockEntity manager)) {
                return TurtleCommandResult.failure("not_manager");
            }
            LabelGunActions.LabelGunActionResult result = action.apply(gun, manager);
            if (!result.success()) {
                return TurtleCommandResult.failure(result.errorCode());
            }
            access.getInventory().setChanged();
            return TurtleCommandResult.success(new Object[]{true});
        });
    }

    private SFMItemHandleTarget selectedTarget(
            Predicate<ItemStack> expectedType,
            String missingTypeCode
    ) {

        int[] selectedSlot = {-1};
        ItemStack[] expected = {null};
        return new SFMItemHandleTarget(
                () -> {
                    if (turtle.isRemoved()) {
                        return SFMItemHandleTarget.Resolution.failure("target_changed");
                    }
                    Container inventory = turtle.getInventory();
                    if (selectedSlot[0] < 0) {
                        selectedSlot[0] = turtle.getSelectedSlot();
                    }
                    if (selectedSlot[0] < 0 || selectedSlot[0] >= inventory.getContainerSize()) {
                        return SFMItemHandleTarget.Resolution.failure("target_changed");
                    }
                    ItemStack current = inventory.getItem(selectedSlot[0]);
                    if (expected[0] == null) {
                        if (current.isEmpty() || !expectedType.test(current)) {
                            return SFMItemHandleTarget.Resolution.failure(missingTypeCode);
                        }
                        expected[0] = current;
                        return SFMItemHandleTarget.Resolution.success(current);
                    }
                    return current == expected[0] && !current.isEmpty()
                           ? SFMItemHandleTarget.Resolution.success(current)
                           : SFMItemHandleTarget.Resolution.failure("target_changed");
                },
                ignored -> turtle.getInventory().setChanged(),
                stack -> {
                    Program program = DiskItem.compileAndUpdateErrorsAndWarnings(stack, null, true);
                    turtle.getInventory().setChanged();
                    return program;
                }
        );
    }

    private static @Nullable ItemStack selectedLabelGun(ITurtleAccess turtle) {

        Container inventory = turtle.getInventory();
        int slot = turtle.getSelectedSlot();
        if (slot < 0 || slot >= inventory.getContainerSize()) {
            return null;
        }
        ItemStack stack = inventory.getItem(slot);
        return stack.getItem() instanceof LabelGunItem ? stack : null;
    }

    private static @Nullable Direction relativeDirection(String direction) {

        return switch (direction) {
            case "front" -> Direction.NORTH;
            case "up" -> Direction.UP;
            case "down" -> Direction.DOWN;
            default -> null;
        };
    }

    private static Direction relativeDirection(
            ITurtleAccess turtle,
            String direction
    ) {

        return switch (direction) {
            case "front" -> turtle.getDirection();
            case "up" -> Direction.UP;
            case "down" -> Direction.DOWN;
            default -> throw new IllegalArgumentException("Unsupported turtle labeler direction: " + direction);
        };
    }

    @FunctionalInterface
    private interface LabelAction {
        LabelGunActions.LabelGunActionResult apply(
                net.minecraft.world.level.Level level,
                ItemStack gun,
                BlockPos target
        );
    }

    @FunctionalInterface
    private interface ManagerAction {
        LabelGunActions.LabelGunActionResult apply(
                ItemStack gun,
                ManagerBlockEntity manager
        );
    }
}
