package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.program.LimitedInputSlot;
import ca.teamdman.sfm.common.program.ProgramContext;
import ca.teamdman.sfm.common.program.SimulateExploreAllPathsProgramBehaviour;
import ca.teamdman.sfm.common.registry.SFMPackets;
import ca.teamdman.sfm.common.registry.SFMResourceTypes;
import ca.teamdman.sfm.common.resourcetype.ResourceType;
import ca.teamdman.sfm.common.util.SFMASTUtils;
import ca.teamdman.sfm.common.util.SFMResourceLocation;
import ca.teamdman.sfml.ast.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
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
            SFMLProgram successProgram,
            OutputStatement outputStatement
    ) {

        StringBuilder payload = new StringBuilder();
        payload.append(outputStatement.toStringPretty()).append("\n");
        payload.append("-- predictions may differ from actual execution results\n");


        successProgram.tick(ProgramContext.of(
                successProgram,
                manager,
                createSimulation(outputStatement, payload)
        ));

        return payload.toString().strip();
    }

    private static SimulateExploreAllPathsProgramBehaviour createSimulation(
            OutputStatement targetOutputStatement,
            StringBuilder payload
    ) {
        AtomicInteger branchCount = new AtomicInteger(0);

        return new SimulateExploreAllPathsProgramBehaviour() {
            @Override
            public void onOutputStatementExecution(
                    ProgramContext context,
                    OutputStatement outputStatement
            ) {
                super.onOutputStatementExecution(context, outputStatement);

                // Only update payload if it was the target output statement that ticked
                if (targetOutputStatement != outputStatement) {
                    return;
                }
                SimulateExploreAllPathsProgramBehaviour simulation = this;

                // Store branch info in a new string builder so we can indent it
                StringBuilder branchPayload = new StringBuilder();

                // Print the un-indented header
                payload
                        .append("-- POSSIBILITY ")
                        .append(branchCount.getAndIncrement())
                        .append(" --");


                if (simulation.getCurrentPath().streamBranches().allMatch(BranchPathElement::wasTrue)) {
                    payload.append(" all true\n");
                } else if (simulation
                        .getCurrentPath()
                        .streamBranches()
                        .allMatch(Predicate.not(BranchPathElement::wasTrue))) {
                    payload.append(" all false\n");
                } else {
                    payload.append('\n');
                }
                simulation.getCurrentPath()
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
                List<Pair<LimitedInputSlot<?, ?, ?>, ResourceAccess>> inputSlots = new ArrayList<>();

                context.inputs()
                        .forEach(inputStatement -> inputStatement.gatherSlots(
                                context,
                                slot -> inputSlots.add(new Pair<>(
                                        slot,
                                        inputStatement.resourceAccess()
                                ))
                        ));
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
                                                                .add(resourceLimit.limit().quantity()), // TODO v5: double check export inspector, might print 5+5 instead of 10
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
                                ResourceLocation resourceLimitLocation = SFMResourceLocation.fromNamespaceAndPath(
                                        resourceId.resourceNamespace,
                                        resourceId.resourceName
                                );
                                long accept = outputStatement
                                        .resourceLimits()
                                        .resourceLimitList()
                                        .stream()
                                        .filter(outputResourceLimit -> outputResourceLimit
                                                                               .resourceIds()
                                                                               .anyMatchResourceLocation(
                                                                                       resourceLimitLocation)
                                                                       && outputStatement
                                                                               .resourceLimits()
                                                                               .exclusions()
                                                                               .stream()
                                                                               .noneMatch(
                                                                                       exclusion -> exclusion.matchesResourceLocation(
                                                                                               resourceLimitLocation)))
                                        .mapToLong(rl -> rl.limit().quantity().number().value())
                                        .max()
                                        .orElse(0);
                                if (accept == 0) {
                                    iter.remove();
                                } else {
                                    iter.set(resourceLimit.withLimit(new Limit(
                                            new ResourceQuantity(
                                                    resourceLimit.limit().quantity().idExpansionBehaviour(),
                                                    NumberExpression.fromLiteral(Long.min(
                                                            accept,
                                                            resourceLimit
                                                                    .limit()
                                                                    .quantity()
                                                                    .number()
                                                                    .value()
                                                    ))
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
                                        outputStatement.resourceAccess(),
                                        condensedResourceLimits,
                                        outputStatement.each(),
                                        outputStatement.emptySlotsOnly()
                                ).toStringPretty());
                    }

                }
                branchPayload.append("\n");
                payload.append(branchPayload.toString().indent(4));

            }
        };
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
        STACK stack = limitedInputSlot.peekExtractPotential();
        long amount = limitedInputSlot.type.getAmount(stack);
        amount = Long.min(amount, limitedInputSlot.tracker.getResourceLimit().limit().quantity().number().value());
        long remainingObligation = limitedInputSlot.tracker.getRemainingRetentionObligation(resourceType, stack);
        amount -= Long.min(amount, remainingObligation);
        Limit amountLimit = new Limit(
                new ResourceQuantity(ResourceQuantity.IdExpansionBehaviour.NO_EXPAND, NumberExpression.fromLiteral(amount)),
                ResourceQuantity.MAX_QUANTITY
        );
        ResourceLocation stackId = resourceType.getRegistryKeyForStack(stack);
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
                FriendlyByteBuf friendlyByteBuf
        ) {

            friendlyByteBuf.writeUtf(msg.programString, SFMLProgram.MAX_PROGRAM_LENGTH);
            friendlyByteBuf.writeInt(msg.outputNodeIndex());
        }

        @Override
        public ServerboundOutputInspectionRequestPacket decode(FriendlyByteBuf friendlyByteBuf) {

            return new ServerboundOutputInspectionRequestPacket(
                    friendlyByteBuf.readUtf(SFMLProgram.MAX_PROGRAM_LENGTH),
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
