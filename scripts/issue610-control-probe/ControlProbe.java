import ca.teamdman.sfm.gametest.tests.compat.justdirethings.Issue610VisualControls;
import static ca.teamdman.sfm.gametest.tests.compat.justdirethings.Issue610VisualControls.Action.*;

/** JDK-only behavioral probe; no Minecraft, JUnit, agent, or external artifact. */
public final class ControlProbe {
    private static int assertions;
    private static void require(boolean value) {
        assertions++;
        if (!value) throw new AssertionError("Behavioral assertion " + assertions + " failed");
    }
    private static void fault(Runnable operation) {
        try { operation.run(); }
        catch (IllegalStateException expected) { require(expected.getMessage().contains("no automatic retry")); return; }
        throw new AssertionError("Expected sticky acknowledgement failure");
    }
    public static void main(String[] args) {
        var selection = new Issue610VisualControls();
        require(selection.tick(59,false,false,false,0,false)==NONE);
        require(selection.mode()==Issue610VisualControls.Mode.SELECTING);
        require(selection.tick(60,false,false,false,0,false)==PRESS_MANAGER);
        require(selection.tick(61,false,true,false,0,false)==NONE);

        var manual = new Issue610VisualControls(1000);
        require(manual.tick(1001,true,false,false,0,false)==NONE);
        require(manual.tick(1_000_000,false,false,false,0,false)==NONE);
        require(manual.mode()==Issue610VisualControls.Mode.MANUAL);
        require(manual.tick(1_000_001,false,false,false,1,false)==NONE);
        require(manual.phase()==Issue610VisualControls.Phase.DRAIN);
        require(manual.tick(2_000_000,false,false,false,1,true)==NONE);
        require(manual.phase()==Issue610VisualControls.Phase.SECOND_RUN);
        require(manual.tick(3_000_000,false,false,false,2,true)==NONE);
        require(manual.phase()==Issue610VisualControls.Phase.DONE);

        var boundary = new Issue610VisualControls();
        require(boundary.tick(60,true,false,false,0,false)==NONE);
        require(boundary.mode()==Issue610VisualControls.Mode.MANUAL);
        var lateLever = new Issue610VisualControls();
        require(lateLever.tick(61,true,false,false,0,false)==PRESS_MANAGER);
        require(lateLever.mode()==Issue610VisualControls.Mode.AUTOMATIC);

        var sequence = new Issue610VisualControls();
        require(sequence.tick(60,false,false,false,0,false)==PRESS_MANAGER);
        require(sequence.tick(61,false,true,false,0,false)==NONE);
        require(sequence.tick(62,false,true,false,1,false)==NONE);
        require(sequence.tick(101,false,false,false,1,false)==NONE);
        require(sequence.tick(102,false,true,false,1,false)==NONE);
        require(sequence.tick(103,false,false,true,1,false)==NONE);
        require(sequence.tick(104,false,false,false,1,false)==PRESS_DRAIN);
        require(sequence.tick(105,false,false,true,1,false)==NONE);
        require(sequence.tick(106,false,false,true,1,true)==NONE);
        require(sequence.tick(145,false,false,false,1,true)==NONE);
        require(sequence.tick(146,false,false,true,1,true)==NONE);
        require(sequence.tick(147,false,false,false,1,true)==PRESS_MANAGER);
        require(sequence.tick(148,false,true,false,1,true)==NONE);
        require(sequence.tick(149,false,true,false,2,true)==NONE);
        require(sequence.phase()==Issue610VisualControls.Phase.DONE);
        require(sequence.tick(10000,false,false,false,2,true)==NONE);

        var timeout = new Issue610VisualControls();
        require(timeout.tick(60,false,false,false,0,false)==PRESS_MANAGER);
        require(timeout.tick(159,false,false,false,0,false)==NONE);
        fault(()->timeout.tick(160,false,false,false,0,false));
        fault(()->timeout.tick(161,false,false,false,1,false));

        var exactAck = new Issue610VisualControls(10);
        require(exactAck.tick(70,false,false,false,0,false)==PRESS_MANAGER);
        require(exactAck.tick(170,false,false,false,1,false)==NONE);
        require(exactAck.tick(209,false,false,false,1,false)==NONE);
        require(exactAck.tick(210,false,false,false,1,false)==PRESS_DRAIN);
        require(exactAck.tick(211,true,false,false,1,false)==NONE);
        require(exactAck.mode()==Issue610VisualControls.Mode.AUTOMATIC);

        var lateAck = new Issue610VisualControls();
        require(lateAck.tick(60,false,false,false,0,false)==PRESS_MANAGER);
        fault(()->lateAck.tick(161,false,false,false,1,false));

        var held = new Issue610VisualControls();
        require(held.tick(60,false,true,false,0,false)==NONE);
        require(held.tick(5000,false,false,true,0,false)==NONE);
        require(held.tick(5001,false,false,false,0,false)==PRESS_MANAGER);
        System.out.println("PASS: 8 behavioral scenarios; " + assertions + " assertions; JDK-only, no Minecraft/JUnit runtime");
    }
}
