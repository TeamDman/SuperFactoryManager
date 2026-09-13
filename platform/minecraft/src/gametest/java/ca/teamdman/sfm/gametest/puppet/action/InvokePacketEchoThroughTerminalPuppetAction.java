package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;
import ca.teamdman.sfm.gametest.tests.general.PacketTerminalEchoGameTest;

import java.nio.file.Path;

/**
 * Types the A4.1 deterministic worker into the real Rust-owned PowerShell PTY.
 * The selected {@code sfm.exe} remains a separate process launched by the PTY,
 * while Minecraft continues ticking and servicing its control request.
 */
public final class InvokePacketEchoThroughTerminalPuppetAction implements SFMPuppetAction {
    public static final String LIST_SCHEMA_WITNESS = "SFM_A4_PACKET_LIST_SCHEMA=sfm.packet.list/1";
    public static final String SEND_STATUS_WITNESS = "SFM_A4_PACKET_SEND_STATUS=send_attempted";

    @Override
    public String description() {
        return "invoke the source-matched sfm.exe packet echo through the Rust terminal";
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
        String quotedFixture = SFMPacketTerminalCommandSupport.powerShellLiteral(PacketTerminalEchoGameTest.FIXTURE_ID);
        String quotedWorker = SFMPacketTerminalCommandSupport.powerShellLiteral(PacketTerminalEchoGameTest.WORKER);
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
                "if($matches.Count -ne 1){throw ('expected one terminal echo request, found '+$matches.Count)}",
                "$entry=$matches[0]",
                "$reply=$entry.value.reply",
                "$response=[ordered]@{type='Response';JobId=$entry.value.JobId;text=$entry.value.prompt;"
                        + "worker=" + quotedWorker + ";extra=$entry.value.extra}",
                "$responseJson=$response|ConvertTo-Json -Compress -Depth 8",
                "$sendText=((& $sfm --output-format json packet send --session-id $page.session_id "
                        + "--instance-pid " + pid
                        + " -- $reply.dimension $reply.x $reply.y $reply.z $responseJson) "
                        + "-join [Environment]::NewLine)",
                "if($LASTEXITCODE -ne 0){throw 'sfm packet send failed'}",
                "$sent=$sendText|ConvertFrom-Json",
                "if($sent.schema -ne 'sfm.packet.send/1'){throw 'unexpected packet send schema'}",
                "if($sent.status -ne 'send_attempted'){throw ('unexpected packet send status '+$sent.status)}",
                "if(-not $sent.local_transport_accepted){throw 'packet send was not locally accepted'}",
                "Write-Output ('SFM_A4_PACKET_LIST_SCHEMA='+$page.schema)",
                "Write-Output ('SFM_A4_PACKET_SEND_STATUS='+$sent.status)"
        );
    }

}
