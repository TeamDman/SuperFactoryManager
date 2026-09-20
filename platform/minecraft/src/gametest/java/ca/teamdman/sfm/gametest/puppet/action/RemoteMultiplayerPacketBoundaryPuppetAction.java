package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;
import ca.teamdman.sfm.gametest.puppet.SFMMultiplayerPuppetControl;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;

import java.util.concurrent.CompletableFuture;

/** File requests use the ordinary connection screen; no integrated server, mouse, or keyboard automation. */
public final class RemoteMultiplayerPacketBoundaryPuppetAction implements SFMPuppetAction {
    private SFMMultiplayerPuppetControl control;
    private CompletableFuture<JsonObject> read;
    private CompletableFuture<Void> write;
    private JsonObject pending;
    private int sequence = 1;
    private int connections;
    private long nextPoll;
    private long deadline;
    private boolean connecting;
    private boolean finish;
    private boolean failed;
    private final SFMMultiplayerPuppetProtocolProof proof = new SFMMultiplayerPuppetProtocolProof();
    private boolean proving;

    @Override
    public String description() {
        return "loopback dedicated-server file puppet, request " + sequence;
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getSingleplayerServer() != null) {
            throw new IllegalStateException("Remote boundary proof must not own an integrated server");
        }
        if (control == null) {
            control = SFMMultiplayerPuppetControl.open("client");
            publish(control.path("client", "ready.json"), observation());
            return false;
        }
        if (write != null) {
            if (!write.isDone()) return false;
            write.join();
            write = null;
            if (finish) {
                if (failed) throw new IllegalStateException("Remote puppet had failed requests; inspect response artifacts");
                return true;
            }
        }
        if (pending != null) {
            if (proving) {
                proof.tick().ifPresent(result -> {
                    proving = false;
                    respond(result.get("success").getAsBoolean(), result.get("message").getAsString());
                });
                return false;
            }
            if (connecting) {
                if (minecraft.level != null && minecraft.player != null && minecraft.getConnection() != null) {
                    connections++;
                    connecting = false;
                    respond(true, "Normal remote connection established");
                } else if (System.nanoTime() >= deadline) {
                    connecting = false;
                    respond(false, "Normal remote connection timed out");
                }
            }
            return false;
        }
        if (read == null) {
            if (System.nanoTime() < nextPoll) return false;
            nextPoll = System.nanoTime() + 100_000_000L;
            int requestSequence = sequence;
            read = CompletableFuture.supplyAsync(() -> control.readRequest("client", requestSequence));
            return false;
        }
        if (!read.isDone()) return false;
        pending = read.join();
        read = null;
        if (pending == null) return false;
        try {
            switch (pending.get("op").getAsString()) {
                case "connect" -> {
                    if (minecraft.level != null || minecraft.getConnection() != null) {
                        throw new IllegalStateException("Disconnect before requesting another connection");
                    }
                    connecting = true;
                    deadline = System.nanoTime() + 120_000_000_000L;
                    ConnectScreen.startConnecting(new TitleScreen(), minecraft,
                            ServerAddress.parseString(control.endpoint()),
                            new ServerData("SFM isolated loopback proof", control.endpoint(), false));
                }
                case "disconnect" -> {
                    proof.cleanup();
                    if (minecraft.level != null) minecraft.level.disconnect();
                    minecraft.clearLevel(new TitleScreen());
                    respond(true, "Normal remote connection closed");
                }
                case "observe" -> respond(true, "Remote state observed");
                case "finish" -> {
                    if (connections < 2) throw new IllegalStateException("Connect and reconnect evidence is required");
                    proof.cleanup();
                    finish = true;
                    respond(true, "Requested remote proof completed; recorded assertions determine its coverage");
                }
                default -> {
                    String operation = pending.get("op").getAsString();
                    if (!proof.supports(operation)) throw new IllegalArgumentException("Unknown remote puppet operation");
                    proof.begin(operation);
                    proving = true;
                }
            }
        } catch (RuntimeException exception) {
            connecting = false;
            respond(false, exception.getMessage());
        }
        return false;
    }

    private JsonObject observation() {
        Minecraft minecraft = Minecraft.getInstance();
        JsonObject result = control.observation();
        result.addProperty("integratedServer", minecraft.getSingleplayerServer() != null);
        result.addProperty("connected", minecraft.level != null && minecraft.player != null && minecraft.getConnection() != null);
        result.addProperty("connections", connections);
        result.add("protocol", proof.observation());
        result.addProperty("screen", minecraft.screen == null ? "none" : minecraft.screen.getClass().getSimpleName());
        if (minecraft.player != null) result.addProperty("player", minecraft.player.getUUID().toString());
        if (minecraft.level != null) result.addProperty("dimension", minecraft.level.dimension().location().toString());
        return result;
    }

    private void respond(boolean success, String message) {
        JsonObject result = observation();
        result.addProperty("sequence", sequence);
        result.addProperty("success", success);
        result.addProperty("message", message == null ? "Operation failed" : message);
        result.addProperty("op", pending.get("op").getAsString());
        if (!success) failed = true;
        publish(control.response("client", sequence++), result);
        pending = null;
    }

    private void publish(java.nio.file.Path path, JsonObject value) {
        write = CompletableFuture.runAsync(() -> SFMMultiplayerPuppetControl.write(path, value));
    }

    @Override
    public void abort() {
        if (read != null) read.cancel(false);
        proof.cleanup();
        // No global process or user-window lifecycle is owned by this action.
    }
}
