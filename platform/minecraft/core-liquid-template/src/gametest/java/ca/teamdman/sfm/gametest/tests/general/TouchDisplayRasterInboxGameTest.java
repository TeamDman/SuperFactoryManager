package ca.teamdman.sfm.gametest.tests.general;

import ca.teamdman.sfm.client.program.ClientManagerFrameRuntime;
import ca.teamdman.sfm.client.program.ClientProgramConsentGate;
import ca.teamdman.sfm.client.program.ClientProgramIdentity;
import ca.teamdman.sfm.client.raster.MinecraftRasterTextureAccess;
import ca.teamdman.sfm.client.raster.TouchDisplayRasterFrame;
import ca.teamdman.sfm.client.raster.TouchDisplayRasterInbox;
import ca.teamdman.sfm.client.raster.TouchDisplayRasterRuntime;
import ca.teamdman.sfm.client.raster.TouchDisplayRasterTextureCache;
import ca.teamdman.sfm.common.block.TouchDisplayBlock;
import ca.teamdman.sfm.common.blockentity.ClientManagerBlockEntity;
import ca.teamdman.sfm.common.blockentity.TouchDisplayBlockEntity;
import ca.teamdman.sfm.common.item.DiskItem;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.common.util.SFMDist;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** Real GPU/lease proof using an ambient scheduler seam, without controlling the camera or opening screens. */
@SFMGameTest(SFMDist.CLIENT)
public final class TouchDisplayRasterInboxGameTest extends SFMGameTestDefinition {
    private static final BlockPos MANAGER = new BlockPos(0, 2, 0);
    private static final BlockPos DISPLAY = new BlockPos(2, 2, 0);
    private static final BlockPos OTHER_DISPLAY = new BlockPos(2, 2, 2);
    private static final String SOURCE = """
            CLIENT BTW
            NAME "Raster lease fixture"
            EVERY FRAME FOR displays AS display DO
                RENDER IMAGE "%s" TO display
            END
            """.formatted(TouchDisplayBlockEntity.RED_FIXTURE_IMAGE);
    private static final String EDITED_SOURCE = SOURCE.replace("Raster lease fixture", "Raster lease revised");

    @Override
    public String template() { return "3x3x3"; }

    @Override
    public int maxTicks() { return 300; }

    @Override
    public void run(SFMGameTestHelper helper) {
        helper.setBlock(MANAGER, SFMBlocks.CLIENT_MANAGER.get());
        for (var position : Set.of(DISPLAY, OTHER_DISPLAY)) {
            helper.setBlock(position, SFMBlocks.TOUCH_DISPLAY.get().defaultBlockState()
                    .setValue(TouchDisplayBlock.FACING, Direction.SOUTH));
        }
        install(helper, SOURCE, DISPLAY);
        Minecraft minecraft = Minecraft.getInstance();
        AtomicInteger stage = new AtomicInteger();
        AtomicInteger serverStage = new AtomicInteger();
        AtomicBoolean scheduled = new AtomicBoolean();
        AtomicReference<String> failure = new AtomicReference<>();
        AtomicReference<ClientProgramIdentity> priorIdentity = new AtomicReference<>();
        AtomicReference<TouchDisplayRasterRuntime.Stream> active = new AtomicReference<>();
        Set<ClientProgramIdentity> identities = new HashSet<>();
        List<TouchDisplayRasterRuntime.Stream> streams = new ArrayList<>();
        List<ResourceLocation> textures = new ArrayList<>();
        helper.succeedWhen(() -> {
            helper.assertTrue(failure.get() == null, String.valueOf(failure.get()));
            int next = stage.get();
            if (serverStage.get() != next) {
                switch (next) {
                    case 1 -> install(helper, EDITED_SOURCE, DISPLAY);
                    case 2 -> install(helper, EDITED_SOURCE, OTHER_DISPLAY);
                    case 3 -> helper.setBlock(MANAGER, Blocks.AIR);
                    default -> { }
                }
                serverStage.set(next);
            }
            if (next < 5 && scheduled.compareAndSet(false, true)) {
                minecraft.execute(() -> {
                    var screenBefore = minecraft.screen;
                    try {
                        if (minecraft.level == null) return;
                        var level = minecraft.level;
                        if (!(level.getBlockEntity(helper.absolutePos(DISPLAY)) instanceof TouchDisplayBlockEntity display)
                            || !(level.getBlockEntity(helper.absolutePos(OTHER_DISPLAY)) instanceof TouchDisplayBlockEntity other)) return;
                        if (next == 4) {
                            for (ResourceLocation location : textures) {
                                check(!(minecraft.getTextureManager().getTexture(location) instanceof DynamicTexture),
                                        "Retired raster texture was not released at the client tick boundary");
                            }
                            cleanup(streams, identities);
                            stage.set(5);
                            return;
                        }
                        if (next == 3) {
                            if (level.getBlockEntity(helper.absolutePos(MANAGER)) instanceof ClientManagerBlockEntity) return;
                            check(TouchDisplayRasterRuntime.textureForSelectedFrame(other, 4000).isEmpty(),
                                    "Removed manager retained raster presentation authority");
                            check(!active.get().isCurrent(), "Manager removal left a live raster writer");
                            stage.set(4);
                            return;
                        }
                        if (!(level.getBlockEntity(helper.absolutePos(MANAGER)) instanceof ClientManagerBlockEntity manager)
                            || manager.worldId() == null || !manager.storedSource().equals(next == 0 ? SOURCE : EDITED_SOURCE)) return;
                        if (!manager.labels().getPositions("displays").contains(helper.absolutePos(next == 2 ? OTHER_DISPLAY : DISPLAY))) return;
                        ClientProgramIdentity identity = ClientManagerFrameRuntime.identityFor(manager).orElseThrow();
                        identities.add(identity);
                        switch (next) {
                            case 0 -> {
                                check(TouchDisplayRasterRuntime.open(display, identity).isEmpty(), "Unapproved program acquired a raster stream");
                                approve(identity);
                                selectProgram(display, 100);
                                var stream = TouchDisplayRasterRuntime.open(display, identity).orElseThrow();
                                streams.add(stream);
                                long semanticRevision = display.content().revision();
                                byte[] redGreen = {(byte) 255, 0, 0, (byte) 255, 0, (byte) 255, 0, (byte) 255};
                                check(stream.offer(TouchDisplayRasterFrame.full(1, 2, 1, redGreen)).accepted(), "Full frame rejected");
                                ResourceLocation location = selectRaster(display, 1000, textures);
                                check(pixel(location, 0) == 0xFF0000FF && pixel(location, 1) == 0xFF00FF00,
                                        "RGBA byte order did not reach the actual dynamic texture");
                                stream.offer(TouchDisplayRasterFrame.full(2, 2, 1, redGreen));
                                check(selectRaster(display, 1001, textures).equals(location), "Unchanged frame replaced the texture");
                                check(TouchDisplayRasterRuntime.textureInfo(stream).orElseThrow().uploads() == 1,
                                        "Unchanged pixels uploaded again");
                                stream.offer(TouchDisplayRasterFrame.dirty(3, 2, 2, 1, 1, 0, 1, 1,
                                        new byte[]{0, 0, (byte) 255, (byte) 255}));
                                check(selectRaster(display, 1002, textures).equals(location) && pixel(location, 1) == 0xFFFF0000,
                                        "Dirty region did not update the existing texture");
                                stream.offer(solid(4, 255, 0, 0));
                                stream.offer(solid(5, 0, 255, 0));
                                selectRaster(display, 1003, textures);
                                check(pixel(location, 0) == 0xFF00FF00, "Newest frame was not presented");
                                stream.offer(solid(6, 0, 0, 255));
                                selectRaster(display, 1003, textures);
                                check(pixel(location, 0) == 0xFF00FF00, "The same render epoch uploaded twice");
                                selectRaster(display, 1004, textures);
                                check(pixel(location, 0) == 0xFFFF0000, "Next render epoch did not present pending pixels");
                                check(display.content().revision() == semanticRevision, "Local animation changed server semantic revision");

                                long generation = stream.generation();
                                long uploadsBeforeHidden = TouchDisplayRasterRuntime.textureInfo(stream).orElseThrow().uploads();
                                long evaluationsBeforeHidden = ClientManagerFrameRuntime.observation(display).evaluations();
                                stream.offer(solid(7, 255, 0, 0));
                                ClientManagerFrameRuntime.textureForSelectedFrame(display, 111, false);
                                check(TouchDisplayRasterRuntime.textureForSelectedFrame(display, 1005, false).orElseThrow().equals(location),
                                        "Hidden display lost its retained texture");
                                stream.offer(solid(8, 0, 255, 0));
                                ClientManagerFrameRuntime.textureForSelectedFrame(display, 112, false);
                                TouchDisplayRasterRuntime.textureForSelectedFrame(display, 1006, false);
                                check(ClientManagerFrameRuntime.observation(display).evaluations() == evaluationsBeforeHidden,
                                        "Hidden display evaluated its program");
                                check(TouchDisplayRasterRuntime.textureInfo(stream).orElseThrow().uploads() == uploadsBeforeHidden
                                                && pixel(location, 0) == 0xFFFF0000,
                                        "Hidden display uploaded a pending raster");
                                check(TouchDisplayRasterRuntime.open(display, identity).orElseThrow() == stream,
                                        "Hidden display disconnected its approved stream");
                                selectProgram(display, 113);
                                selectRaster(display, 1007, textures);
                                check(stream.generation() == generation && pixel(location, 0) == 0xFF00FF00,
                                        "Resumed display did not retain its generation and present the newest frame");
                                ClientManagerFrameRuntime.textureForSelectedFrame(display, 114, false);
                                ClientManagerFrameRuntime.consent().revoke(identity, ClientProgramConsentGate.RENDER);
                                check(TouchDisplayRasterRuntime.textureForSelectedFrame(display, 1008, false).isEmpty(),
                                        "Revoked hidden raster stayed visible");
                                check(!stream.isCurrent() && stream.offer(solid(9, 1, 2, 3)) == TouchDisplayRasterInbox.OfferResult.REJECTED_LEASE,
                                        "Hidden revocation retained a writable stream");
                                approve(identity);
                                selectProgram(display, 110);
                                var replacement = TouchDisplayRasterRuntime.open(display, identity).orElseThrow();
                                check(replacement.generation() > stream.generation(), "New stream reused the old generation");
                                streams.add(replacement);
                                replacement.offer(solid(0, 255, 0, 0));
                                selectRaster(display, 1010, textures);
                                active.set(replacement);
                                checkRealGpuBudgets();
                            }
                            case 1, 2 -> {
                                check(!identity.equals(priorIdentity.get()), "Source/scope edit reused consent identity");
                                check(TouchDisplayRasterRuntime.textureForSelectedFrame(display, next * 1000L + 1).isEmpty(),
                                        "Source/scope edit retained the old raster");
                                check(!active.get().isCurrent(), "Source/scope edit retained a writer lease");
                                TouchDisplayBlockEntity target = next == 1 ? display : other;
                                approve(identity);
                                selectProgram(target, next * 100L + 1);
                                var replacement = TouchDisplayRasterRuntime.open(target, identity).orElseThrow();
                                streams.add(replacement);
                                replacement.offer(solid(0, 0, 255, 0));
                                selectRaster(target, next * 1000L + 2, textures);
                                active.set(replacement);
                            }
                            default -> throw new IllegalStateException("Unexpected raster fixture stage");
                        }
                        priorIdentity.set(identity);
                        check(minecraft.screen == screenBefore, "Ambient raster test changed the current screen");
                        stage.incrementAndGet();
                    } catch (RuntimeException exception) {
                        failure.set(exception.toString());
                        cleanup(streams, identities);
                    } finally {
                        scheduled.set(false);
                    }
                });
            }
            helper.assertTrue(stage.get() == 5, "Waiting for ambient raster stage " + stage.get());
        });
    }

    private static void checkRealGpuBudgets() {
        AtomicLong clock = new AtomicLong();
        TouchDisplayRasterInbox inbox = new TouchDisplayRasterInbox(
                new TouchDisplayRasterInbox.Limits(4, 1024, 1024, 1024, 8, 100), clock::get);
        var cache = new TouchDisplayRasterTextureCache<>(inbox, new MinecraftRasterTextureAccess(), 16, clock::get);
        UUID world = UUID.randomUUID();
        var first = inbox.acquire(new TouchDisplayRasterInbox.Display(world, "minecraft:overworld", 0, 0, 0), "first").orElseThrow();
        var second = inbox.acquire(new TouchDisplayRasterInbox.Display(world, "minecraft:overworld", 1, 0, 0), "second").orElseThrow();
        var third = inbox.acquire(new TouchDisplayRasterInbox.Display(world, "minecraft:overworld", 2, 0, 0), "third").orElseThrow();
        try {
            for (var lease : List.of(first, second, third)) inbox.offer(lease, solid(0, 255, 0, 0));
            cache.textureFor(first).orElseThrow();
            check(cache.textureFor(second).isEmpty(), "Upload budget admitted two frames in one window");
            clock.set(100);
            cache.textureFor(second).orElseThrow();
            clock.set(200);
            check(cache.textureFor(third).isEmpty() && cache.snapshot().gpuBytes() == 16,
                    "GPU memory budget admitted an excess texture");
            check(inbox.latestInfo(third).orElseThrow().pending(), "GPU pressure consumed the pending frame");
            inbox.release(first);
            cache.maintain();
            cache.textureFor(third).orElseThrow();
            check(cache.snapshot().gpuBytes() == 16, "Released GPU allocation was not reusable");
        } finally {
            inbox.clear();
            cache.maintain();
        }
        check(cache.snapshot().gpuBytes() == 0 && cache.snapshot().textures() == 0,
                "Isolated GPU budget fixture leaked textures");
    }

    private static TouchDisplayRasterFrame solid(long sequence, int red, int green, int blue) {
        byte[] rgba = {(byte) red, (byte) green, (byte) blue, (byte) 255, (byte) red, (byte) green, (byte) blue, (byte) 255};
        return TouchDisplayRasterFrame.full(sequence, 2, 1, rgba);
    }

    private static int pixel(ResourceLocation location, int x) {
        var texture = Minecraft.getInstance().getTextureManager().getTexture(location);
        check(texture instanceof DynamicTexture, "Raster texture was not registered");
        var pixels = ((DynamicTexture) texture).getPixels();
        check(pixels != null, "Raster texture lost its native pixels");
        return pixels.getPixelRGBA(x, 0);
    }

    private static ResourceLocation selectRaster(TouchDisplayBlockEntity display, long epoch, List<ResourceLocation> textures) {
        var location = TouchDisplayRasterRuntime.textureForSelectedFrame(display, epoch).orElseThrow();
        if (!textures.contains(location)) textures.add(location);
        return location;
    }

    private static void selectProgram(TouchDisplayBlockEntity display, long epoch) {
        check(ClientManagerFrameRuntime.textureForSelectedFrame(display, epoch, true).isPresent(), "Approved fixture program did not render");
    }

    private static void install(SFMGameTestHelper helper, String source, BlockPos target) {
        ItemStack disk = new ItemStack(SFMItems.DISK.get());
        DiskItem.setProgram(disk, source);
        LabelPositionHolder.empty().add("displays", helper.absolutePos(target)).save(disk);
        helper.getBlockEntity(MANAGER, ClientManagerBlockEntity.class).setDisk(disk);
    }

    private static void approve(ClientProgramIdentity identity) {
        var consent = ClientManagerFrameRuntime.consent();
        for (var capability : Set.of(ClientProgramConsentGate.EXECUTE, ClientProgramConsentGate.RENDER)) {
            if (consent.state(identity, capability) == ClientProgramConsentGate.ConsentState.APPROVED) continue;
            consent.request(identity, capability);
            consent.decide(identity, capability, ClientProgramConsentGate.Decision.APPROVE);
        }
    }

    private static void cleanup(List<TouchDisplayRasterRuntime.Stream> streams, Set<ClientProgramIdentity> identities) {
        streams.forEach(TouchDisplayRasterRuntime.Stream::close);
        identities.forEach(identity -> ClientManagerFrameRuntime.consent().store().forget(identity));
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
