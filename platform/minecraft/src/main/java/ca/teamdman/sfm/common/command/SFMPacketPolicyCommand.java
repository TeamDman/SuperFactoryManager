package ca.teamdman.sfm.common.command;

import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
import ca.teamdman.sfm.common.net.SFMPacketInventoryAddress;
import ca.teamdman.sfm.common.net.multiplayer.SFMMultiplayerPacketPolicy;
import ca.teamdman.sfm.common.net.multiplayer.SFMMultiplayerPacketPolicySavedData;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;

import java.time.Instant;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

import static ca.teamdman.sfm.common.net.multiplayer.SFMMultiplayerPacketProtocol.*;

/** Owner-only exact grants. No selectors, wildcard targets, indefinite command grants or client self-enrolment. */
public final class SFMPacketPolicyCommand {
    public static final long DEFAULT_EXPIRY_SECONDS = 3600;
    public static final long MAX_EXPIRY_SECONDS = 31_536_000;
    public static final int PAGE_SIZE = 20;
    private SFMPacketPolicyCommand() {}

    @SFMSubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        // Brigadier merges this child into the existing sfm root, independent of subscriber order.
        event.getDispatcher().register(Commands.literal("sfm").then(tree()));
    }

    public static LiteralArgumentBuilder<CommandSourceStack> tree() {
        return Commands.literal("packet_policy").requires(source -> source.hasPermission(Commands.LEVEL_OWNERS))
                .then(Commands.literal("grant").then(grantTree("inventory", Action.PACKET_SEND))
                        .then(grantTree("inbox", Action.INBOX_SUBSCRIBE))
                        .then(grantTree("delivery", Action.INBOX_DELIVER)))
                .then(Commands.literal("revoke").then(Commands.argument("grant", UuidArgument.uuid())
                        .executes(context -> guarded(context, () -> {
                            boolean removed = data(context).revoke(UuidArgument.getUuid(context, "grant"));
                            success(context.getSource(), removed ? "Exact packet grant revoked immediately." : "No grant has that ID.");
                            return removed ? 1 : 0;
                        }))))
                .then(Commands.literal("list").executes(context -> list(context, 1))
                        .then(Commands.argument("page", IntegerArgumentType.integer(1, 205))
                                .executes(context -> list(context, IntegerArgumentType.getInteger(context, "page")))))
                .then(Commands.literal("recover_empty").executes(context -> guarded(context, () -> {
                    boolean recovered = data(context).recoverEmpty();
                    success(context.getSource(), recovered
                            ? "Rejected stored policy replaced with an empty default-deny policy. No grants restored."
                            : "Policy is valid; recovery made no changes.");
                    return recovered ? 1 : 0;
                })));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> grantTree(String literal, Action action) {
        var dimension = Commands.argument("dimension", ResourceLocationArgument.id());
        if (action == Action.PACKET_SEND) {
            var z = Commands.argument("z", IntegerArgumentType.integer(-30_000_000, 30_000_000));
            for (Direction side : Direction.values()) z.then(timed(Commands.literal(side.getName()), action, Optional.of(side)));
            // Unsided means the exact unsided capability, never all six sides.
            z.then(timed(Commands.literal("unsided"), action, Optional.empty()));
            dimension.then(xyz(z));
        } else if (action == Action.INBOX_SUBSCRIBE) {
            dimension.then(timed(Commands.argument("channel", ResourceLocationArgument.id()), action, Optional.empty()));
        } else {
            var z = Commands.argument("z", IntegerArgumentType.integer(-30_000_000, 30_000_000));
            z.then(timed(Commands.argument("channel", ResourceLocationArgument.id()), action, Optional.empty()));
            dimension.then(xyz(z));
        }
        return Commands.literal(literal).then(Commands.argument("player", UuidArgument.uuid()).then(dimension));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, Integer> xyz(RequiredArgumentBuilder<CommandSourceStack, Integer> z) {
        return Commands.argument("x", IntegerArgumentType.integer(-30_000_000, 30_000_000))
                .then(Commands.argument("y", IntegerArgumentType.integer(-2048, 2047)).then(z));
    }

    private static <T extends ArgumentBuilder<CommandSourceStack, T>> T timed(T node, Action action, Optional<Direction> side) {
        node.executes(context -> grant(context, action, side, DEFAULT_EXPIRY_SECONDS));
        node.then(Commands.literal("expires_in")
                .then(Commands.argument("seconds", LongArgumentType.longArg(1, MAX_EXPIRY_SECONDS))
                        .executes(context -> grant(context, action, side, LongArgumentType.getLong(context, "seconds")))));
        return node;
    }

    private static int grant(CommandContext<CommandSourceStack> context, Action action, Optional<Direction> side, long lifetime) {
        return guarded(context, () -> {
            var dimension = ResourceLocationArgument.getId(context, "dimension");
            Scope scope;
            if (action == Action.PACKET_SEND) {
                scope = new InventoryScope(new SFMPacketInventoryAddress(dimension, position(context), side));
            } else {
                var inbox = new InboxScope(dimension, ResourceLocationArgument.getId(context, "channel"));
                scope = action == Action.INBOX_SUBSCRIBE ? inbox : new DeliveryScope(new ManagerAddress(dimension, position(context)), inbox);
            }
            long expiry = expiresAt(System.currentTimeMillis(), lifetime);
            var grant = new SFMMultiplayerPacketPolicy.Grant(UUID.randomUUID(), UuidArgument.getUuid(context, "player"),
                    action, scope, Optional.empty(), OptionalLong.of(expiry));
            data(context).grant(grant);
            success(context.getSource(), "Granted " + describe(grant) + ". Unsided is exact; no other target or side is authorized.");
            return 1;
        });
    }

    public static long expiresAt(long now, long lifetimeSeconds) {
        if (now < 0 || lifetimeSeconds < 1 || lifetimeSeconds > MAX_EXPIRY_SECONDS) {
            throw new IllegalArgumentException("Grant lifetime must be between 1 second and 365 days");
        }
        return Math.addExact(now, Math.multiplyExact(lifetimeSeconds, 1000));
    }

    private static BlockPos position(CommandContext<CommandSourceStack> context) {
        return new BlockPos(IntegerArgumentType.getInteger(context, "x"), IntegerArgumentType.getInteger(context, "y"),
                IntegerArgumentType.getInteger(context, "z"));
    }
    private static SFMMultiplayerPacketPolicySavedData data(CommandContext<CommandSourceStack> context) {
        return SFMMultiplayerPacketPolicySavedData.forServer(context.getSource().getServer());
    }
    private static int list(CommandContext<CommandSourceStack> context, int page) {
        return guarded(context, () -> {
            var data = data(context);
            if (data.quarantined()) {
                context.getSource().sendFailure(Component.literal(data.diagnostic()));
                return 0;
            }
            var grants = data.policy().snapshot();
            int pages = Math.max(1, (grants.size() + PAGE_SIZE - 1) / PAGE_SIZE);
            if (page > pages) {
                context.getSource().sendFailure(Component.literal("Packet policy has " + pages + " page(s)."));
                return 0;
            }
            success(context.getSource(), "Packet policy: " + grants.size() + " exact grants; page " + page + "/" + pages + ". Default is deny.");
            grants.stream().skip((long) (page - 1) * PAGE_SIZE).limit(PAGE_SIZE)
                    .forEach(grant -> success(context.getSource(), describe(grant)));
            return 1;
        });
    }
    private static String describe(SFMMultiplayerPacketPolicy.Grant grant) {
        return grant.id() + " player=" + grant.player() + " action=" + grant.action() + " scope=" + grant.scope()
                + grant.program().map(program -> " exactProgram=" + program).orElse(" caller=player")
                + (grant.expiresAtEpochMillis().isPresent()
                ? " expires=" + Instant.ofEpochMilli(grant.expiresAtEpochMillis().getAsLong()) : " expires=none (stored legacy rule)");
    }
    @FunctionalInterface private interface Operation { int run() throws Exception; }
    private static int guarded(CommandContext<CommandSourceStack> context, Operation operation) {
        var source = context.getSource();
        if (!source.hasPermission(Commands.LEVEL_OWNERS)) {
            source.sendFailure(Component.literal("Packet policy requires owner permission level 4."));
            return 0;
        }
        try { return operation.run(); }
        catch (Exception rejected) {
            source.sendFailure(Component.literal("Packet policy unchanged: "
                    + (rejected instanceof IllegalArgumentException || rejected instanceof IllegalStateException
                    ? rejected.getMessage() : "operation failed")));
            return 0;
        }
    }
    @MCVersionDependentBehaviour
    private static void success(CommandSourceStack source, String message) {
        source.sendSuccess(Component.literal(message), false);
    }
}
