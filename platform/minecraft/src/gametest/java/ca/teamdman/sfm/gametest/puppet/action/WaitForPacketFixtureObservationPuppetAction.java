package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.client.net.SFMPacketObservationLog;
import ca.teamdman.sfm.client.net.SFMPacketObservationRuntime;
import ca.teamdman.sfm.common.value.SFMValue;
import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetHelper;

import java.util.Optional;

/** Waits for one fixture request to arrive through the production client observation log. */
public final class WaitForPacketFixtureObservationPuppetAction implements SFMPuppetAction {
    private final String fixtureId;
    private int ticks;

    public WaitForPacketFixtureObservationPuppetAction(String fixtureId) {
        if (fixtureId == null || fixtureId.isBlank()) {
            throw new IllegalArgumentException("Packet fixture id must not be blank");
        }
        this.fixtureId = fixtureId;
    }

    @Override
    public String description() {
        return "wait for packet fixture observation " + fixtureId;
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        boolean received = SFMPacketObservationRuntime.get()
                .page(Optional.empty(), SFMPacketObservationLog.MAX_PAGE_SIZE)
                .stream()
                .flatMap(page -> page.entries().stream())
                .map(SFMPacketObservationLog.Entry::value)
                .anyMatch(this::isFixtureRequest);
        if (received) {
            return true;
        }
        if (++ticks > SFMGamePuppetHelper.SCREEN_TIMEOUT_TICKS) {
            throw new IllegalStateException("Timed out waiting for packet fixture observation " + fixtureId);
        }
        return false;
    }

    private boolean isFixtureRequest(SFMValue value) {
        if (!(value instanceof SFMValue.ObjectValue object)) {
            return false;
        }
        return SFMValue.of(fixtureId).equals(object.fields().get("fixture"));
    }
}
