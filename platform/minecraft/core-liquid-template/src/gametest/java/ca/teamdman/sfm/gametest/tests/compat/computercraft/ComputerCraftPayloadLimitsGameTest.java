package ca.teamdman.sfm.gametest.tests.compat.computercraft;

import ca.teamdman.sfm.common.compat.computercraft.SFMManagerCollectionHandle;
import ca.teamdman.sfm.common.compat.computercraft.SFMNetworkPeripheral;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3' %}
import ca.teamdman.sfm.common.compat.computercraft.SFMNetworkPeripheralProvider;
{% when '1.20.4', '1.21', '1.21.1', '26.1.2' %}
{% endcase %}
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3' %}
{% when '1.20.4', '1.21', '1.21.1', '26.1.2' %}
import dan200.computercraft.api.peripheral.PeripheralCapability;
{% endcase %}

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3' %}
{% when '1.20.4', '1.21', '1.21.1', '26.1.2' %}
import java.util.Objects;

{% endcase %}
/** Ensures manager discovery is no longer bounded by the former table-payload cap. */
@SFMGameTest
public class ComputerCraftPayloadLimitsGameTest extends SFMGameTestDefinition {
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3' %}
    private static final SFMNetworkPeripheralProvider PROVIDER = new SFMNetworkPeripheralProvider();

{% when '1.20.4', '1.21', '1.21.1', '26.1.2' %}
{% endcase %}
    @Override
    public String template() {

        return "33x3x2";
    }

    @Override
    public void run(SFMGameTestHelper helper) {

        for (int x = 0; x < 33; x++) {
            helper.setBlock(new BlockPos(x, 2, 1), SFMBlocks.CABLE.get());
        }
        for (int x = 0; x < 33; x += 2) {
            helper.setBlock(new BlockPos(x, 2, 0), SFMBlocks.MANAGER.get());
        }

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3' %}
        SFMNetworkPeripheral peripheral = (SFMNetworkPeripheral) PROVIDER
                .getPeripheral(helper.getLevel(), helper.absolutePos(new BlockPos(0, 2, 1)), Direction.NORTH)
                .resolve()
                .orElseThrow();
{% when '1.20.4', '1.21', '1.21.1', '26.1.2' %}
        SFMNetworkPeripheral peripheral = (SFMNetworkPeripheral) Objects.requireNonNull(
                helper.getLevel().getCapability(
                        PeripheralCapability.get(),
                        helper.absolutePos(new BlockPos(0, 2, 1)),
                        Direction.NORTH
                ),
                "SFM cable did not expose an SFM network peripheral"
        );
{% endcase %}
        SFMManagerCollectionHandle managers = peripheral.getManagers();
        helper.assertTrue(managers.count() == 17, "Manager collection did not include every loaded manager");
        helper.assertTrue(
                managers.get(17).position().length == 3,
                "Manager collection did not expose its final entry"
        );
        helper.succeed();
    }
}
