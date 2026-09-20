package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;
import ca.teamdman.sfm.gametest.tests.general.PacketLanDisabledGameTest;

import java.nio.file.Path;

/** Invokes the real CLI after LAN publication and requires an effects-disabled result. */
public final class InvokePacketLanDisabledThroughTerminalPuppetAction implements SFMPuppetAction {
    public static final String STATUS_WITNESS = "SFM_A4_LAN_SEND_STATUS=effects_disabled";
    public static final String ACCEPTED_WITNESS = "SFM_A4_LAN_LOCAL_ACCEPTED=false";

    @Override
    public String description() {
        return "prove LAN publication disables sfm.exe packet send through the Rust terminal";
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
        String quotedFixture = SFMPacketTerminalCommandSupport.powerShellLiteral(PacketLanDisabledGameTest.FIXTURE_ID);
        String quotedResponse = SFMPacketTerminalCommandSupport.powerShellLiteral(PacketLanDisabledGameTest.RESPONSE_TEXT);
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
                "if($matches.Count -ne 1){throw ('expected one LAN fixture request, found '+$matches.Count)}",
                "$reply=$matches[0].value.reply",
                "$response=[ordered]@{type='LanDisabledResponse';text=" + quotedResponse + "}",
                "$responseJson=$response|ConvertTo-Json -Compress -Depth 8",
                "$sendText=((& $sfm --output-format json packet send --session-id $page.session_id "
                        + "--instance-pid " + pid
                        + " -- $reply.dimension $reply.x $reply.y $reply.z $responseJson) "
                        + "-join [Environment]::NewLine)",
                "if($LASTEXITCODE -ne 0){throw 'sfm packet send failed'}",
                "$sent=$sendText|ConvertFrom-Json",
                "if($sent.schema -ne 'sfm.packet.send/1'){throw 'unexpected packet send schema'}",
                "if($sent.status -ne 'effects_disabled'){throw ('unexpected LAN packet status '+$sent.status)}",
                "if($sent.local_transport_accepted){throw 'LAN packet send was locally accepted'}",
                "Write-Output ('SFM_A4_LAN_SEND_STATUS='+$sent.status)",
                "Write-Output 'SFM_A4_LAN_LOCAL_ACCEPTED=false'"
        );
    }
}
