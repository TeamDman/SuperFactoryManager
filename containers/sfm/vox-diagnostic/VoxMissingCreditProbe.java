package org.facet.vox;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.Socket;
import java.util.List;
import java.util.Map;
import org.facet.phon.Value;
import org.facet.vox.generated.JavaFixtureServiceDescriptor;
import org.facet.vox.tcp.StreamFraming;
import org.facet.vox.tcp.WireConstants;

/** Review-only deterministic sequence using the pinned runtime's actual driver and wire types. */
public final class VoxMissingCreditProbe {
    public static void main(String[] args) throws Exception {
        try (Fixture fixture = new Fixture()) {
            switch (args[0]) {
                case "active_credit_passes" -> {
                    fixture.bind(1);
                    fixture.inbound(fixture.codec.channelGrant(1, 1));
                }
                case "late_credit_after_close_passes" -> {
                    fixture.bindAndClose(1);
                    fixture.inbound(fixture.codec.channelGrant(1, 1));
                }
                case "missing_credit_passes" ->
                    fixture.inbound(fixture.codec.channelGrant(99, 1));
                case "credit_on_zero_channel_fails" ->
                    expectVoxFailure(() -> fixture.inbound(fixture.codec.channelGrant(0, 1)),
                        "message for unknown channel 1:0");
                case "credit_on_control_lane_fails" ->
                    expectVoxFailure(() -> fixture.inboundOnLane(0,
                        fixture.codec.channelGrant(1, 1)), "message for unknown channel 0:1");
                case "credit_on_unopened_lane_fails" ->
                    expectVoxFailure(() -> fixture.inboundOnLane(99,
                        fixture.codec.channelGrant(1, 1)), "message for unknown channel 99:1");
                case "credit_on_opening_lane_fails" -> {
                    fixture.registerLane(2, LaneState.OPENING);
                    expectVoxFailure(() -> fixture.inboundOnLane(2,
                        fixture.codec.channelGrant(1, 1)), "message for unknown channel 2:1");
                }
                case "credit_after_local_lane_close_passes" -> {
                    fixture.closeLane();
                    fixture.inbound(fixture.codec.channelGrant(99, 1));
                }
                case "late_credit_has_no_history_window" -> {
                    fixture.bindAndClose(1);
                    for (long id = 3; id < 100; id += 2) fixture.bindAndClose(id);
                    fixture.inbound(fixture.codec.channelGrant(1, 1));
                }
                case "zero_credit_on_active_fails" -> {
                    fixture.bind(1);
                    expectVoxFailure(() -> fixture.inbound(fixture.codec.channelGrant(1, 0)),
                        "invalid channel credit 0");
                }
                case "zero_credit_on_missing_fails" ->
                    expectVoxFailure(() -> fixture.inbound(fixture.codec.channelGrant(99, 0)),
                        "invalid channel credit 0");
                case "zero_credit_on_closed_fails" -> {
                    fixture.bindAndClose(1);
                    expectVoxFailure(() -> fixture.inbound(fixture.codec.channelGrant(1, 0)),
                        "invalid channel credit 0");
                }
                case "overflow_credit_on_missing_fails" ->
                    expectVoxFailure(() -> fixture.inbound(fixture.codec.channelGrant(99, -1)),
                        "invalid channel credit 4294967295");
                case "missing_credit_field_fails" ->
                    expectVoxFailure(() -> fixture.malformedGrant(Value.map(Map.of())),
                        "missing wire field additional");
                case "wrong_type_credit_fails" ->
                    expectVoxFailure(() -> fixture.malformedGrant(
                        Value.map(Map.of("additional", Value.string("1")))),
                        "expected unsigned wire integer");
                case "credit_to_active_receiver_fails" -> {
                    fixture.bindReceiver(1);
                    expectVoxFailure(() -> fixture.inbound(fixture.codec.channelGrant(1, 1)),
                        "peer granted credit to locally-receiving channel");
                }
                case "item_to_closed_sender_fails" -> {
                    fixture.bindAndClose(1);
                    expectVoxFailure(() -> fixture.inbound(fixture.codec.channelItem(1, new byte[0])),
                        "message for unknown channel 1:1");
                }
                case "close_to_missing_channel_fails" ->
                    expectVoxFailure(() -> fixture.inbound(fixture.codec.channelClose(99)),
                        "message for unknown channel 1:99");
                case "reset_to_closed_sender_fails_unchanged" -> {
                    fixture.bindAndClose(1);
                    expectVoxFailure(() -> fixture.inbound(fixture.codec.channelReset(1)),
                        "message for unknown channel 1:1");
                }
                default -> throw new IllegalArgumentException(args[0]);
            }
            System.out.println("PASS " + args[0]);
        }
    }

    @FunctionalInterface private interface Checked { void run() throws Exception; }
    private static void expectVoxFailure(Checked action, String message) throws Exception {
        try { action.run(); } catch (VoxException expected) {
            if (!message.equals(expected.getMessage())) {
                throw new AssertionError("Expected '" + message + "', got '"
                    + expected.getMessage() + "'", expected);
            }
            return;
        }
        throw new AssertionError("Expected VoxException: " + message);
    }

    private static Object invoke(Method method, Object owner, Object... args) throws Exception {
        try { return method.invoke(owner, args); }
        catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof Exception cause) throw cause;
            throw failure;
        }
    }

    private static Method method(String name, Class<?>... parameters) throws Exception {
        Method method = VoxConnection.class.getDeclaredMethod(name, parameters);
        method.setAccessible(true);
        return method;
    }

    private static final class Fixture implements AutoCloseable {
        final ConnectionOptions options = ConnectionOptions.builder().maxPendingRequests(2).build();
        final VoxConnection connection;
        final WireCodec codec = new WireCodec(options);
        final StreamFraming framing;
        final Method bind = method("bindInboundChannel", String.class, long.class, long.class,
            MethodDescriptor.class, ChannelDescriptor.class, int.class);
        final Method inbound = method("processInboundChannel", long.class, Value.class);
        final Method command;
        final Constructor<?> close;

        Fixture() throws Exception {
            connection = VoxConnection.accept(new Socket(), new ServiceRegistry(), options);
            registerLane(1, LaneState.OPEN);
            framing = new StreamFraming(new ByteArrayInputStream(new byte[] {
                'V', 'O', 'X', 'L', (byte) WireConstants.LINK_VERSION, 0
            }), new ByteArrayOutputStream(), options.maxFrameBytes());
            framing.exchangeLinkPrologue();
            Class<?> driverCommand = Class.forName("org.facet.vox.VoxConnection$DriverCommand");
            command = method("processCommand", driverCommand, StreamFraming.class, WireCodec.class);
            close = Class.forName("org.facet.vox.VoxConnection$ChannelCloseCommand")
                .getDeclaredConstructor(long.class, long.class);
            close.setAccessible(true);
        }

        void bind(long id) throws Exception {
            MethodDescriptor generate = JavaFixtureServiceDescriptor.GENERATE;
            invoke(bind, connection, "1:" + id, 1L, id,
                generate, generate.channels().get(0), 3);
        }

        void bindReceiver(long id) throws Exception {
            MethodDescriptor generate = JavaFixtureServiceDescriptor.GENERATE;
            ChannelDescriptor sender = generate.channels().get(0);
            ChannelDescriptor receiver = new ChannelDescriptor(sender.argumentIndex(),
                ChannelDescriptor.Direction.RX, sender.role(), sender.elementAdapter());
            invoke(bind, connection, "1:" + id, 1L, id, generate, receiver, 3);
        }

        void bindAndClose(long id) throws Exception {
            bind(id);
            invoke(command, connection, close.newInstance(1L, id), framing, codec);
        }

        void inbound(Value message) throws Exception {
            inboundOnLane(1, message);
        }

        void inboundOnLane(long laneId, Value message) throws Exception {
            invoke(inbound, connection, laneId, WireCodec.variantPayload(message));
        }

        @SuppressWarnings("unchecked")
        void registerLane(long id, LaneState state) throws Exception {
            Field field = VoxConnection.class.getDeclaredField("lanes");
            field.setAccessible(true);
            List<ServiceLane> lanes = (List<ServiceLane>) field.get(connection);
            lanes.add(new ServiceLane(id, JavaFixtureServiceDescriptor.INSTANCE, connection,
                options, LaneOptions.defaults(), state, 1, 64, 3));
        }

        void closeLane() throws Exception {
            Constructor<?> constructor = Class.forName("org.facet.vox.VoxConnection$CloseLaneCommand")
                .getDeclaredConstructor(long.class);
            constructor.setAccessible(true);
            invoke(command, connection, constructor.newInstance(1L), framing, codec);
        }

        void malformedGrant(Value payload) throws Exception {
            // Deliberately bypass the codec to cover validation at the driver seam too.
            invoke(inbound, connection, 1L, Value.map(Map.of("id", Value.unsigned(99),
                "body", Value.enumValue("GrantCredit", payload))));
        }

        @Override public void close() { connection.close(); }
    }
}
