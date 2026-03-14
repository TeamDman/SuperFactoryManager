package ca.teamdman.sfml;

import ca.teamdman.sfm.common.config.SFMConfig;
import org.junit.jupiter.api.Test;

import static ca.teamdman.sfml.SFMLTestHelpers.assertCompileErrorsPresent;
import static ca.teamdman.sfml.SFMLTestHelpers.assertNoCompileErrors;
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
    public void every_1_000_000_000_000_000_000_000_000_ticks_do_end() {
        assertCompileErrorsPresent(
                """
                            every 1_000_000_000_000_000_000_000_000 ticks do -- too big for a long
                            end
                        """,
                new NumberFormatException()
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
    public void every_second_ticks_do_end() {
        var input = """
                    every second do
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
                new IllegalArgumentException()
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
    public void label_conflicts() {
        var input = """
                    EVERY 20 TICKS DO
                        INPUT FROM firstcgh
                        OUTPUT TO second, seconds, global, a,b,c,d,e,f,g,h,i,j,k,l,m,n,o,p,q,r,s,t,u,v,w,x,y,z
                    END
                """;
        assertNoCompileErrors(input);
    }


    @Test
    public void bulk_timer_triggers() {
        // This is also checked in timer_triggers.sfml
        // Because the contract between examples and unit tests isn't clear, we will keep the duplication.
        var input = """
                EVERY TICKS DO END
                EVERY TICK DO END
                EVERY SECONDS DO END
                EVERY SECOND DO END
                EVERY GLOBAL TICKS DO END
                EVERY GLOBAL TICK DO END
                EVERY GLOBAL SECONDS DO END
                EVERY GLOBAL SECOND DO END
                EVERY 20 TICKS OFFSET BY 1 TICKS DO END
                EVERY 20 TICKS OFFSET BY 1 TICK DO END
                EVERY 20 TICKS OFFSET BY 1 SECONDS DO END
                EVERY 20 TICKS OFFSET BY 1 SECOND DO END
                EVERY 20 TICKS OFFSET BY 1 DO END
                EVERY 20 TICKS DO END
                EVERY 20 TICK OFFSET BY 1 TICKS DO END
                EVERY 20 TICK OFFSET BY 1 TICK DO END
                EVERY 20 TICK OFFSET BY 1 SECONDS DO END
                EVERY 20 TICK OFFSET BY 1 SECOND DO END
                EVERY 20 TICK OFFSET BY 1 DO END
                EVERY 20 TICK DO END
                EVERY 20 SECONDS OFFSET BY 1 TICKS DO END
                EVERY 20 SECONDS OFFSET BY 1 TICK DO END
                EVERY 20 SECONDS OFFSET BY 1 SECONDS DO END
                EVERY 20 SECONDS OFFSET BY 1 SECOND DO END
                EVERY 20 SECONDS OFFSET BY 1 DO END
                EVERY 20 SECONDS DO END
                EVERY 20 SECOND OFFSET BY 1 TICKS DO END
                EVERY 20 SECOND OFFSET BY 1 TICK DO END
                EVERY 20 SECOND OFFSET BY 1 SECONDS DO END
                EVERY 20 SECOND OFFSET BY 1 SECOND DO END
                EVERY 20 SECOND OFFSET BY 1 DO END
                EVERY 20 SECOND DO END
                EVERY 20 GLOBAL TICKS OFFSET BY 1 TICKS DO END
                EVERY 20 GLOBAL TICKS OFFSET BY 1 TICK DO END
                EVERY 20 GLOBAL TICKS OFFSET BY 1 SECONDS DO END
                EVERY 20 GLOBAL TICKS OFFSET BY 1 SECOND DO END
                EVERY 20 GLOBAL TICKS OFFSET BY 1 DO END
                EVERY 20 GLOBAL TICKS DO END
                EVERY 20 GLOBAL TICK OFFSET BY 1 TICKS DO END
                EVERY 20 GLOBAL TICK OFFSET BY 1 TICK DO END
                EVERY 20 GLOBAL TICK OFFSET BY 1 SECONDS DO END
                EVERY 20 GLOBAL TICK OFFSET BY 1 SECOND DO END
                EVERY 20 GLOBAL TICK OFFSET BY 1 DO END
                EVERY 20 GLOBAL TICK DO END
                EVERY 20 GLOBAL SECONDS OFFSET BY 1 TICKS DO END
                EVERY 20 GLOBAL SECONDS OFFSET BY 1 TICK DO END
                EVERY 20 GLOBAL SECONDS OFFSET BY 1 SECONDS DO END
                EVERY 20 GLOBAL SECONDS OFFSET BY 1 SECOND DO END
                EVERY 20 GLOBAL SECONDS OFFSET BY 1 DO END
                EVERY 20 GLOBAL SECONDS DO END
                EVERY 20 GLOBAL SECOND OFFSET BY 1 TICKS DO END
                EVERY 20 GLOBAL SECOND OFFSET BY 1 TICK DO END
                EVERY 20 GLOBAL SECOND OFFSET BY 1 SECONDS DO END
                EVERY 20 GLOBAL SECOND OFFSET BY 1 SECOND DO END
                EVERY 20 GLOBAL SECOND OFFSET BY 1 DO END
                EVERY 20 GLOBAL SECOND DO END
                """;
        assertNoCompileErrors(input);
    }
}
