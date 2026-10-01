package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.program.LimitedInputSlot;
import ca.teamdman.sfm.common.program.ProgramContext;
import ca.teamdman.sfm.common.program.SimulateExploreAllPathsProgramBehaviour;
import ca.teamdman.sfm.common.program.SimulateExploreAllPathsProgramBehaviour.Branch;
import ca.teamdman.sfm.common.registry.registration.SFMPackets;
import ca.teamdman.sfm.common.registry.registration.SFMResourceTypes;
import ca.teamdman.sfm.common.resourcetype.ResourceType;
import ca.teamdman.sfm.common.util.SFMASTUtils;
import ca.teamdman.sfm.common.util.SFMResourceLocation;
import ca.teamdman.sfml.ast.*;
import ca.teamdman.sfml.ast.Number;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}
import net.minecraft.network.FriendlyByteBuf;
{% when "1.21", "1.21.1", "26.1.2" %}
import net.minecraft.network.RegistryFriendlyByteBuf;
{% endcase %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
{% when "26.1.2" %}
import net.minecraft.resources.Identifier;
{% endcase %}
import net.minecraft.resources.ResourceKey;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
import net.minecraft.resources.ResourceLocation;
{% when "26.1.2" %}
{% endcase %}
import org.antlr.v4.runtime.misc.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

public record ServerboundOutputInspectionRequestPacket(
        String programString,

        int outputNodeIndex
) implements SFMPacket {
    private static final int MAX_RESULTS_LENGTH = 20480;

    public static String getOutputStatementInspectionResultsString(
            ManagerBlockEntity manager,
            Program successProgram,
            OutputStatement outputStatement
    ) {

        StringBuilder payload = new StringBuilder();
        payload.append(outputStatement.toStringPretty()).append("\n");
        payload.append("-- predictions may differ from actual execution results\n");

        AtomicInteger branchCount = new AtomicInteger(0);
        successProgram.replaceOutputStatement(
                outputStatement,
                new OutputStatement(
                        outputStatement.labelAccess(),
                        outputStatement.resourceLimits(),
                        outputStatement.each(),
                        outputStatement.emptySlotsOnly()
                ) {
                    @Override
                    public void tick(ProgramContext context) {

                        if (!(context.getBehaviour() instanceof SimulateExploreAllPathsProgramBehaviour behaviour)) {
                            throw new IllegalStateException(
                                    "Expected behaviour to be SimulateExploreAllPathsProgramBehaviour");
                        }
                        StringBuilder branchPayload = new StringBuilder();

                        payload
                                .append("-- POSSIBILITY ")
                                .append(branchCount.getAndIncrement())
                                .append(" --");
                        if (behaviour.getCurrentPath().streamBranches().allMatch(Branch::wasTrue)) {
                            payload.append(" all true\n");
                        } else if (behaviour
                                .getCurrentPath()
                                .streamBranches()
                                .allMatch(Predicate.not(Branch::wasTrue))) {
                            payload.append(" all false\n");
                        } else {
                            payload.append('\n');
                        }
                        behaviour.getCurrentPath()
                                .streamBranches()
                                .forEach(branch -> {
                                    if (branch.wasTrue()) {
                                        payload
                                                .append(branch.ifStatement().condition().toStringPretty())
                                                .append(" -- true");
                                    } else {
                                        payload
                                                .append(branch.ifStatement().condition().toStringPretty())
                                                .append(" -- false");
                                    }
                                    payload.append("\n");
                                });
                        payload.append("\n");


                        branchPayload.append("-- predicted inputs:\n");
                        List<Pair<LimitedInputSlot<?, ?, ?>, LabelAccess>> inputSlots = new ArrayList<>();
{% if features.packet_computation %}
                        context
                                .getInputs()
                                .stream()
                                .forEach(inputSource -> inputSource.inputStatement().ifPresent(inputStatement ->
                                        inputSource.gatherSlots(
                                                context,
                                                slot -> inputSlots.add(new Pair<>(
                                                        slot,
                                                        inputStatement.labelAccess()
                                                ))
                                        )
                                ));
{% else %}
                        context
                                .getInputs()
                                .forEach(inputStatement -> inputStatement.gatherSlots(
                                        context,
                                        slot -> inputSlots.add(new Pair<>(
                                                slot,
                                                inputStatement.labelAccess()
                                        ))
                                ));
{% endif %}
                        List<InputStatement> inputStatements = inputSlots.stream()
                                .map(slot -> SFMASTUtils.getInputStatementForSlot(slot.a, slot.b))
                                .filter(Optional::isPresent)
                                .map(Optional::get)
                                .toList();
                        if (inputStatements.isEmpty()) {
                            branchPayload.append("none\n-- predicted outputs:\nnone");
                        } else {
                            inputStatements.stream()
                                    .map(InputStatement::toStringPretty)
                                    .map(x -> x + "\n")
                                    .forEach(branchPayload::append);

                            branchPayload.append(
                                    "-- predicted outputs:\n");
                            ResourceLimits condensedResourceLimits;
                            {
                                ResourceLimits resourceLimits = new ResourceLimits(
                                        inputSlots
                                                .stream()
                                                .map(slot -> slot.a)
                                                .map(ServerboundOutputInspectionRequestPacket::getSlotResource)
                                                .toList(),
                                        ResourceIdSet.EMPTY
                                );
                                List<ResourceLimit> condensedResourceLimitList = new ArrayList<>();
                                for (ResourceLimit resourceLimit : resourceLimits.resourceLimitList()) {
                                    // check if an existing resource limit has the same resource identifier
                                    condensedResourceLimitList
                                            .stream()
                                            .filter(x -> x
                                                    .resourceIds()
                                                    .equals(resourceLimit.resourceIds()))
                                            .findFirst()
                                            .ifPresentOrElse(
                                                    found -> {
                                                        int i = condensedResourceLimitList.indexOf(found);
                                                        ResourceLimit newLimit = found.withLimit(new Limit(
                                                                found
                                                                        .limit()
                                                                        .quantity()
                                                                        .add(resourceLimit.limit().quantity()),
                                                                ResourceQuantity.MAX_QUANTITY
                                                        ));
                                                        condensedResourceLimitList.set(i, newLimit);
                                                    }, () -> condensedResourceLimitList.add(resourceLimit)
                                            );
                                }
                                {
                                    // prune items not covered by the output resource limits
                                    ListIterator<ResourceLimit> iter = condensedResourceLimitList.listIterator();
                                    while (iter.hasNext()) {
                                        ResourceLimit resourceLimit = iter.next();
                                        if (resourceLimit.resourceIds().size() != 1) {
                                            throw new IllegalStateException(
                                                    "Expected resource limit to have exactly one resource id");
                                        }
                                        ResourceIdentifier<?, ?, ?> resourceId = resourceLimit
                                                .resourceIds()
                                                .stream()
                                                .iterator()
                                                .next();

                                        // because these resource limits were generated from resource stacks
                                        // they should always be valid resource locations (not patterns)
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
                                        ResourceLocation resourceLimitLocation = SFMResourceLocation.fromNamespaceAndPath(
{% when "26.1.2" %}
                                        Identifier resourceLimitLocation = SFMResourceLocation.fromNamespaceAndPath(
{% endcase %}
                                                resourceId.resourceNamespace,
                                                resourceId.resourceName
                                        );
                                        long accept = outputStatement
                                                .resourceLimits()
                                                .resourceLimitList()
                                                .stream()
                                                .filter(outputResourceLimit -> outputResourceLimit
                                                                                       .resourceIds()
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
                                                                                       .anyMatchResourceLocation(
{% when "26.1.2" %}
                                                                                       .anyMatchIdentifier(
{% endcase %}
                                                                                               resourceLimitLocation)
                                                                               && outputStatement
                                                                                       .resourceLimits()
                                                                                       .exclusions()
                                                                                       .stream()
                                                                                       .noneMatch(
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
                                                                                               exclusion -> exclusion.matchesResourceLocation(
{% when "26.1.2" %}
                                                                                               exclusion -> exclusion.matchesIdentifier(
{% endcase %}
                                                                                                       resourceLimitLocation)))
                                                .mapToLong(rl -> rl.limit().quantity().number().value())
                                                .max()
                                                .orElse(0);
                                        if (accept == 0) {
                                            iter.remove();
                                        } else {
                                            iter.set(resourceLimit.withLimit(new Limit(
                                                    new ResourceQuantity(
                                                            new Number(Long.min(
                                                                    accept,
                                                                    resourceLimit
                                                                            .limit()
                                                                            .quantity()
                                                                            .number()
                                                                            .value()
                                                            )), resourceLimit.limit().quantity()
                                                                    .idExpansionBehaviour()
                                                    ),
                                                    ResourceQuantity.MAX_QUANTITY
                                            )));
                                        }
                                    }
                                }
                                condensedResourceLimits = new ResourceLimits(
                                        condensedResourceLimitList,
                                        ResourceIdSet.EMPTY
                                );
                            }
                            if (condensedResourceLimits.resourceLimitList().isEmpty()) {
                                branchPayload.append("none\n");
                            } else {
                                branchPayload
                                        .append(new OutputStatement(
                                                outputStatement.labelAccess(),
                                                condensedResourceLimits,
                                                outputStatement.each(),
                                                outputStatement.emptySlotsOnly()
                                        ).toStringPretty());
                            }

                        }
                        branchPayload.append("\n");
                        payload.append(branchPayload.toString().indent(4));
                    }
                }
        );

        successProgram.tick(new ProgramContext(
                successProgram,
                manager,
                new SimulateExploreAllPathsProgramBehaviour()
        ));

        return payload.toString().strip();
    }

    private static <STACK, ITEM, CAP> ResourceLimit getSlotResource(
            LimitedInputSlot<STACK, ITEM, CAP> limitedInputSlot
    ) {

        ResourceType<STACK, ITEM, CAP> resourceType = limitedInputSlot.type;
        //noinspection OptionalGetWithoutIsPresent
        ResourceKey<ResourceType<STACK, ITEM, CAP>> resourceTypeResourceKey = SFMResourceTypes
                .registry()
                .getKey(limitedInputSlot.type)
                .map(x -> {
                    //noinspection unchecked,rawtypes
                    return (ResourceKey<ResourceType<STACK, ITEM, CAP>>) (ResourceKey) x;
                })
                .get();
        STACK stack = limitedInputSlot.peekStackInSlot();
        long amount = limitedInputSlot.type.getAmount(stack);
        amount = Long.min(amount, limitedInputSlot.tracker.getResourceLimit().limit().quantity().number().value());
        long remainingObligation = limitedInputSlot.tracker.getRemainingRetentionObligation(resourceType, stack);
        amount -= Long.min(amount, remainingObligation);
        Limit amountLimit = new Limit(
                new ResourceQuantity(new Number(amount), ResourceQuantity.IdExpansionBehaviour.NO_EXPAND),
                ResourceQuantity.MAX_QUANTITY
        );
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
        ResourceLocation stackId = resourceType.getRegistryKeyForStack(stack);
{% when "26.1.2" %}
        Identifier stackId = resourceType.getRegistryKeyForStack(stack);
{% endcase %}
        ResourceIdentifier<STACK, ITEM, CAP> resourceIdentifier = new ResourceIdentifier<>(
                resourceTypeResourceKey,
                stackId
        );
        return new ResourceLimit(
                new ResourceIdSet(List.of(resourceIdentifier)),
                amountLimit,
                With.ALWAYS_TRUE
        );
    }

    public static class Daddy implements SFMPacketDaddy<ServerboundOutputInspectionRequestPacket> {
        @Override
        public PacketDirection getPacketDirection() {

            return PacketDirection.SERVERBOUND;
        }

        @Override
        public void encode(
                ServerboundOutputInspectionRequestPacket msg,
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}
                FriendlyByteBuf friendlyByteBuf
{% when "1.21", "1.21.1", "26.1.2" %}
                RegistryFriendlyByteBuf friendlyByteBuf
{% endcase %}
        ) {

            friendlyByteBuf.writeUtf(msg.programString, Program.MAX_PROGRAM_LENGTH);
            friendlyByteBuf.writeInt(msg.outputNodeIndex());
        }

        @Override
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}
        public ServerboundOutputInspectionRequestPacket decode(FriendlyByteBuf friendlyByteBuf) {

{% when "1.21", "1.21.1", "26.1.2" %}
        public ServerboundOutputInspectionRequestPacket decode(RegistryFriendlyByteBuf friendlyByteBuf) {
{% endcase %}
            return new ServerboundOutputInspectionRequestPacket(
                    friendlyByteBuf.readUtf(Program.MAX_PROGRAM_LENGTH),
                    friendlyByteBuf.readInt()
            );
        }

        @Override
        public void handle(
                ServerboundOutputInspectionRequestPacket msg,
                SFMPacketHandlingContext context
        ) {

            context.compileAndThen(
                    msg.programString,
                    true,
                    (program, player, managerBlockEntity) -> program.astBuilder()
                            .getNodeAtIndex(msg.outputNodeIndex)
                            .filter(OutputStatement.class::isInstance)
                            .map(OutputStatement.class::cast)
                            .ifPresent(outputStatement -> {
                                String payload = getOutputStatementInspectionResultsString(
                                        managerBlockEntity,
                                        program,
                                        outputStatement
                                );
                                payload = SFMPacketDaddy.truncate(
                                        payload,
                                        ServerboundOutputInspectionRequestPacket.MAX_RESULTS_LENGTH
                                );
                                SFM.LOGGER.debug(
                                        "Sending output inspection results packet with length {}",
                                        payload.length()
                                );
                                SFMPackets.sendToPlayer(
                                        () -> player,
                                        new ClientboundOutputInspectionResultsPacket(payload)
                                );
                            })
            );
        }

        @Override
        public Class<ServerboundOutputInspectionRequestPacket> getPacketClass() {

            return ServerboundOutputInspectionRequestPacket.class;
        }

    }

}
