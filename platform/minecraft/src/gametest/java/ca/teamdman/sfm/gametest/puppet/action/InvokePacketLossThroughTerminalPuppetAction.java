package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;
import ca.teamdman.sfm.gametest.tests.general.PacketTerminalLossGameTest;

import java.nio.file.Path;

/** Sends real CLI packets to lossy destinations, followed by a valid ordering barrier. */
public final class InvokePacketLossThroughTerminalPuppetAction implements SFMPuppetAction {
    public static final String ATTEMPTS_WITNESS = "SFM_A4_LOSS_ATTEMPTS=4";
    public static final String STATUS_WITNESS = "SFM_A4_LOSS_LOCAL_STATUS=send_attempted";
    public static final String DELIVERY_WITNESS = "SFM_A4_LOSS_DELIVERY=not_acknowledged";

    @Override
    public String description() {
        return "prove best-effort sfm.exe packet loss through the Rust terminal";
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        Path executable = SFMPacketTerminalCommandSupport.controlCliExecutable();
        runtime.executeTerminal(command(executable.toString(), ProcessHandle.current().pid()));
        return true;
    }

    static String command(String executable, long minecraftPid) {
        if (minecraftPid <= 0) {
            throw new IllegalArgumentException("Minecraft process id must be positive");
        }
        String quotedExecutable = SFMPacketTerminalCommandSupport.powerShellLiteral(executable);
        String quotedFixture = SFMPacketTerminalCommandSupport.powerShellLiteral(PacketTerminalLossGameTest.FIXTURE_ID);
        String pid = Long.toString(minecraftPid);
        return String.join("; ",
                "$ErrorActionPreference='Stop'",
                "$sfm=" + quotedExecutable,
                "$pageText=((& $sfm --output-format json packet list --limit 100 --instance-pid "
                        + pid + ") -join [Environment]::NewLine)",
                "if($LASTEXITCODE -ne 0){throw 'sfm packet list failed'}",
                "$page=$pageText|ConvertFrom-Json",
                "if($page.schema -ne 'sfm.packet.list/1'){throw 'unexpected packet list schema'}",
                "$matches=@($page.entries|Where-Object{$_.value.fixture -eq " + quotedFixture + "})",
                "if($matches.Count -ne 1){throw ('expected one loss fixture request, found '+$matches.Count)}",
                "$targets=$matches[0].value.targets",
                "$attempts=0",
                "foreach($name in @('missing_dimension','unloaded','no_handler','full')){"
                        + "$target=$targets.$name; "
                        + "$payload=[ordered]@{fixture=" + quotedFixture + ";case=$name}; "
                        + "$payloadJson=$payload|ConvertTo-Json -Compress -Depth 8; "
                        + "$sendText=((& $sfm --output-format json packet send --session-id $page.session_id "
                        + "--instance-pid " + pid
                        + " -- $target.dimension $target.x $target.y $target.z $payloadJson) "
                        + "-join [Environment]::NewLine); "
                        + "if($LASTEXITCODE -ne 0){throw ('sfm packet send failed for '+$name)}; "
                        + "$sent=$sendText|ConvertFrom-Json; "
                        + "if($sent.schema -ne 'sfm.packet.send/1'){throw ('unexpected send schema for '+$name)}; "
                        + "if($sent.status -ne 'send_attempted'){throw ('unexpected send status for '+$name+': '+$sent.status)}; "
                        + "if(-not $sent.local_transport_accepted){throw ('send was not locally accepted for '+$name)}; "
                        + "$attempts++}",
                "$barrier=$targets.barrier",
                "$barrierPayload=[ordered]@{fixture=" + quotedFixture + ";case='network-order-barrier'}",
                "$barrierJson=$barrierPayload|ConvertTo-Json -Compress -Depth 8",
                "$barrierText=((& $sfm --output-format json packet send --session-id $page.session_id "
                        + "--instance-pid " + pid
                        + " -- $barrier.dimension $barrier.x $barrier.y $barrier.z $barrierJson) "
                        + "-join [Environment]::NewLine)",
                "if($LASTEXITCODE -ne 0){throw 'sfm barrier packet send failed'}",
                "$barrierSent=$barrierText|ConvertFrom-Json",
                "if($barrierSent.schema -ne 'sfm.packet.send/1'){throw 'unexpected barrier send schema'}",
                "if($barrierSent.status -ne 'send_attempted'){throw ('unexpected barrier status '+$barrierSent.status)}",
                "if(-not $barrierSent.local_transport_accepted){throw 'barrier send was not locally accepted'}",
                "Write-Output ('SFM_A4_LOSS_ATTEMPTS='+$attempts)",
                "Write-Output 'SFM_A4_LOSS_LOCAL_STATUS=send_attempted'",
                "Write-Output 'SFM_A4_LOSS_DELIVERY=not_acknowledged'"
        );
    }
}
