package ca.teamdman.sfm;

import org.junit.jupiter.api.Test;

/// I forget what the different class name things print
public class SFMClassTests {
    @Test
    public void classSimpleName() {

        System.out.println(getClass().getSimpleName());
    }

    @Test
    public void classCanonicalName() {
        System.out.println(getClass().getCanonicalName());
    }
}
