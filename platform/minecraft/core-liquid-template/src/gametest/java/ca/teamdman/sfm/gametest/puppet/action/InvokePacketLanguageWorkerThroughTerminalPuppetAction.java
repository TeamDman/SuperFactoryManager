package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;
import ca.teamdman.sfm.gametest.tests.general.PacketLanguageTerminalWorkerGameTest;

import java.nio.file.Path;

/** Runs the deterministic Slice D ACK plus duplicate-response worker through the real terminal PTY. */
public final class InvokePacketLanguageWorkerThroughTerminalPuppetAction implements SFMPuppetAction {
    public static final String LIST_SCHEMA_WITNESS = "SFM_D_PACKET_LIST_SCHEMA=sfm.packet.list/1";
    public static final String ACK_STATUS_WITNESS = "SFM_D_PACKET_ACK_STATUS=send_attempted";
    public static final String RESPONSE_STATUS_WITNESS = "SFM_D_PACKET_RESPONSE_STATUS=send_attempted";
    public static final String DUPLICATE_WITNESS = "SFM_D_PACKET_RESPONSE_ATTEMPTS=2";

    @Override
    public String description() {
        return "run the source-matched deterministic packet language worker through the Rust terminal";
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
        String sfm = SFMPacketTerminalCommandSupport.powerShellLiteral(executable);
        String fixture = SFMPacketTerminalCommandSupport.powerShellLiteral(
                PacketLanguageTerminalWorkerGameTest.FIXTURE_ID
        );
        String worker = SFMPacketTerminalCommandSupport.powerShellLiteral(
                PacketLanguageTerminalWorkerGameTest.WORKER
        );
        String pid = Long.toString(minecraftPid);
        String sendPrefix = "((& $sfm --output-format json packet send --session-id $page.session_id "
                            + "--instance-pid " + pid
                            + " -- $entry.value.reply_dimension $entry.value.reply_x $entry.value.reply_y "
                            + "$entry.value.reply_z ";
        return String.join("; ",
                "$ErrorActionPreference='Stop'",
                "$sfm=" + sfm,
                "$pageText=((& $sfm --output-format json packet list --limit 100 --instance-pid "
                        + pid + ") -join [Environment]::NewLine)",
                "if($LASTEXITCODE -ne 0){throw 'sfm packet list failed'}",
                "$page=$pageText|ConvertFrom-Json",
                "if($page.schema -ne 'sfm.packet.list/1'){throw 'unexpected packet list schema'}",
                "$matches=@($page.entries|Where-Object{$_.value.fixture -eq " + fixture + "})",
                "if($matches.Count -ne 1){throw ('expected one language request, found '+$matches.Count)}",
                "$entry=$matches[0]",
                "$ack=[ordered]@{type='Ack';JobId=$entry.value.JobId;worker=" + worker + "}",
                "$ackJson=$ack|ConvertTo-Json -Compress",
                "$ackText=" + sendPrefix + "$ackJson) -join [Environment]::NewLine)",
                "if($LASTEXITCODE -ne 0){throw 'sfm packet ACK send failed'}",
                "$ackSent=$ackText|ConvertFrom-Json",
                "if($ackSent.schema -ne 'sfm.packet.send/1'){throw 'unexpected ACK send schema'}",
                "if($ackSent.status -ne 'send_attempted' -or -not $ackSent.local_transport_accepted)"
                        + "{throw 'ACK was not locally accepted'}",
                "$response=[ordered]@{type='Response';JobId=$entry.value.JobId;text=$entry.value.prompt;"
                        + "worker=" + worker + "}",
                "$responseJson=$response|ConvertTo-Json -Compress",
                "$responseStatuses=@()",
                "1..2|ForEach-Object{$responseText=" + sendPrefix
                        + "$responseJson) -join [Environment]::NewLine);"
                        + "if($LASTEXITCODE -ne 0){throw 'sfm packet Response send failed'};"
                        + "$responseStatuses+=($responseText|ConvertFrom-Json)}",
                "if(@($responseStatuses|Where-Object{$_.schema -ne 'sfm.packet.send/1' -or "
                        + "$_.status -ne 'send_attempted' -or "
                        + "-not $_.local_transport_accepted}).Count -ne 0){throw 'Response was not locally accepted'}",
                "Write-Output ('SFM_D_PACKET_LIST_SCHEMA='+$page.schema)",
                "Write-Output ('SFM_D_PACKET_ACK_STATUS='+$ackSent.status)",
                "Write-Output ('SFM_D_PACKET_RESPONSE_STATUS='+$responseStatuses[0].status)",
                "Write-Output ('SFM_D_PACKET_RESPONSE_ATTEMPTS='+$responseStatuses.Count)"
        );
    }
}
