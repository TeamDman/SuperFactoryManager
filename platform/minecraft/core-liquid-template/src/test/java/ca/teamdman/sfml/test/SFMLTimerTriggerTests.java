package ca.teamdman.sfml.test;

import ca.teamdman.sfm.common.config.SFMConfig;
import org.junit.jupiter.api.Test;

import static ca.teamdman.sfml.test.SFMLTestHelpers.assertCompileErrorsPresent;
import static ca.teamdman.sfml.test.SFMLTestHelpers.assertNoCompileErrors;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class SFMLTimerTriggerTests {
    @Test
    public void every_1_ticks_do_end() {
        assertNoCompileErrors(
                """
                            every 1 ticks do
                            end
                        """
        );
    }

    @Test
    public void every_10000000000_ticks_do_end() {
        assertCompileErrorsPresent(
                """
                            every 10000000000 ticks do
                            end
                        """,
                new NumberFormatException("For input string: \"10000000000\"")
        );
    }

    @Test
    public void every_tick_ticks_do_end() {
        var input = """
                    every tick do
                    end
                """;
        assertNoCompileErrors(input);
    }

    @Test
    public void every_1_ticks_do_fe_end() {
        var min = SFMConfig.SERVER_CONFIG.timerTriggerMinimumIntervalInTicksWhenOnlyForgeEnergyIO.getDefault();
        assertEquals(1, min);
        assertNoCompileErrors(
                """
                            every 1 ticks do
                                INPUT fe:: FROM a
                                OUTPUT fe:: TO b
                            end
                        """
        );
    }

    @Test
    public void every_0_ticks_do_input_end() {
        assertCompileErrorsPresent(
                """
                            name "hello world"
                        
                            every 0 ticks do
                                input from a
                            end
                        """,
                new IllegalArgumentException("Minimum trigger interval is 20 ticks.")
        );
    }

    @Test
    public void every_20_ticks_do_io_end() {
        var input = """
                    every 20 ticks do
                        INPUT FROM a
                        OUTPUT TO b
                    end
                """;
        assertNoCompileErrors(input);
    }

    @Test
    public void every_20g_ticks_do_end() {
        assertNoCompileErrors("EVERY 20g TICKS DO END");
    }

    @Test
    public void every_20G_ticks_do_end() {
        assertNoCompileErrors("EVERY 20G TICKS DO END");
    }

    @Test
    public void every_20_g_ticks_do_end() {
        assertNoCompileErrors("EVERY 20 g TICKS DO END");
    }

    @Test
    public void every_20_G_ticks_do_end() {
        assertNoCompileErrors("EVERY 20 G TICKS DO END");
    }

    @Test
    public void every_20_global_ticks_do_end() {
        assertNoCompileErrors("EVERY 20 GLOBAL TICKS DO END");
    }

    @Test
    public void label_conflicts() {
        var input = """
                    EVERY 20 TICKS DO
                        INPUT FROM first
                        OUTPUT TO second, seconds, global, a,b,c,d,e,f,g,h,i,j,k,l,m,n,o,p,q,r,s,t,u,v,w,x,y,z
                    END
                """;
        assertNoCompileErrors(input);
    }
    @Test
    public void bulk() {
        var input = """
                EVERY TICK DO END
                EVERY TICKS DO END
                EVERY SECOND DO END
                EVERY SECONDS DO END
                EVERY 20 TICKS DO END
                EVERY 20 TICK DO END
                EVERY 20 SECONDS DO END
                EVERY 20 SECOND DO END
                EVERY 20 GLOBAL TICKS DO END
                EVERY 20 GLOBAL SECONDS DO END
                EVERY 20G TICKS DO END
                EVERY 20G TICK DO END
                EVERY 20G SECONDS DO END
                EVERY 20G SECOND DO END
                EVERY 20G+1 TICKS DO END
                EVERY 20G+1 TICK DO END
                EVERY 20G+1 SECONDS DO END
                EVERY 20G+1 SECOND DO END
                EVERY 20+1 TICKS DO END
                EVERY 20+1 TICK DO END
                EVERY 20+1 SECONDS DO END
                EVERY 20+1 SECOND DO END
                EVERY 20G + 1 TICKS DO END
                EVERY 20G + 1 TICK DO END
                EVERY 20G + 1 SECONDS DO END
                EVERY 20G + 1 SECOND DO END
                EVERY 20G PLUS 1 TICKS DO END
                EVERY 20G PLUS 1 TICK DO END
                EVERY 20G PLUS 1 SECONDS DO END
                EVERY 20G PLUS 1 SECOND DO END
                EVERY 20 PLUS 1 TICKS DO END
                EVERY 20 PLUS 1 TICK DO END
                EVERY 20 PLUS 1 SECONDS DO END
                EVERY 20 PLUS 1 SECOND DO END
                """;
        assertNoCompileErrors(input);
    }
}
