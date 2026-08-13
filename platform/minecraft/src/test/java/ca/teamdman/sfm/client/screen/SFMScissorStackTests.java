package ca.teamdman.sfm.client.screen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SFMScissorStackTests {
    @Test
    public void childScissorIsIntersectedWithParentPanel() {
        assertEquals(
                new SFMScissorStack.FramebufferRect(100, 100, 20, 30),
                SFMScissorStack.intersect(
                        new SFMScissorStack.FramebufferRect(100, 100, 50, 50),
                        new SFMScissorStack.FramebufferRect(90, 80, 30, 50)
                )
        );
    }

    @Test
    public void disjointChildProducesEmptyScissor() {
        assertEquals(
                new SFMScissorStack.FramebufferRect(200, 200, 0, 0),
                SFMScissorStack.intersect(
                        new SFMScissorStack.FramebufferRect(0, 0, 100, 100),
                        new SFMScissorStack.FramebufferRect(200, 200, 10, 10)
                )
        );
    }
}
