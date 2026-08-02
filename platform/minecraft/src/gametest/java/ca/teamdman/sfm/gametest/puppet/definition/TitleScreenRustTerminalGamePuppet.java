package ca.teamdman.sfm.gametest.puppet.definition;

import ca.teamdman.sfm.gametest.puppet.SFMGamePuppet;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Proof that the in-game action can launch and use the Rust-authoritative terminal. */
@SFMGamePuppet
public final class TitleScreenRustTerminalGamePuppet {
    private TitleScreenRustTerminalGamePuppet() {
    }

    public static void run(SFMGamePuppetHelper puppet) {
        puppet.waitForOverlayToNotBePresent(LoadingOverlay.class);
        puppet.waitTicks(20);
        // Prove the Java-only surface is usable before any Rust process exists.
        puppet.openTerminal();
        puppet.executeTerminal("pwd");
        puppet.writeTerminalContent("java-first-repl", "/", null);
        puppet.pressTerminalKey(GLFW.GLFW_KEY_ESCAPE);
        puppet.pressTerminalKey(GLFW.GLFW_KEY_ESCAPE);
        puppet.pressTerminalKey(GLFW.GLFW_KEY_ESCAPE);
        puppet.waitTicks(10);
        // The Rust scene must remain a truthful disconnected surface before
        // the lifecycle action starts a companion server; it must not borrow
        // the Java-local REPL help or input strip.
        puppet.openCommandPalette();
        puppet.executeCommandPalette("sfm action invoke sfm:panel/open sfm:terminal");
        puppet.waitTicks(30);
        puppet.capture("rust-terminal-disconnected", Component.literal("SFM Terminal ")
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal("Rust-authoritative terminal shows an explicit disconnected state.")));
        puppet.pressTerminalKey(GLFW.GLFW_KEY_ESCAPE);
        puppet.pressTerminalKey(GLFW.GLFW_KEY_ESCAPE);
        puppet.pressTerminalKey(GLFW.GLFW_KEY_ESCAPE);
        puppet.waitTicks(10);
        puppet.openCommandPalette();
        puppet.executeCommandPalette("sfm action invoke sfm:terminal/server/start");
        puppet.openCommandPalette();
        puppet.executeCommandPalette("sfm action invoke sfm:terminal/server/connect");
        puppet.capture("rust-terminal-lifecycle-actions", Component.literal("SFM Terminal ")
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal("Rust terminal lifecycle actions do not open or replace a panel.")));
        puppet.openCommandPalette();
        puppet.executeCommandPalette("sfm action invoke sfm:panel/open sfm:terminal");
        puppet.pressTerminalKey(GLFW.GLFW_KEY_ESCAPE);
        puppet.capture("rust-terminal-escape-guidance", Component.literal("SFM Terminal ")
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal("Rust-authoritative terminal Escape close guidance.")));
        puppet.pressTerminalKey(GLFW.GLFW_KEY_TAB);
        puppet.capture("rust-terminal-tab-guidance", Component.literal("SFM Terminal ")
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal("Rust-authoritative terminal Tab focus guidance.")));
        puppet.pressTerminalKey(GLFW.GLFW_KEY_TAB);
        puppet.pressTerminalKey(GLFW.GLFW_KEY_TAB);
        puppet.capture("rust-terminal-tab-focus-traversal", Component.literal("SFM Terminal ")
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal("Three-Tab focus traversal remains inside the workspace.")));
        puppet.typeTerminalText("Write-Output alpha beta");
        puppet.pressTerminalKey(GLFW.GLFW_KEY_BACKSPACE, GLFW.GLFW_MOD_CONTROL);
        puppet.typeTerminalText("gamma");
        puppet.pressTerminalKey(GLFW.GLFW_KEY_ENTER);
        puppet.waitTicks(30);
        puppet.capture("rust-terminal-control-backspace", Component.literal("SFM Terminal ")
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal("Ctrl+Backspace edits a Rust-owned PowerShell prompt.")));
        puppet.writeTerminalContent("control-backspace", "alpha gamma", "alpha beta");
        puppet.typeTerminalText("Write-Output ctrl-a-cancel");
        puppet.pressTerminalKey(GLFW.GLFW_KEY_A, GLFW.GLFW_MOD_CONTROL);
        puppet.pressTerminalKey(GLFW.GLFW_KEY_C, GLFW.GLFW_MOD_CONTROL);
        puppet.typeTerminalText("Write-Output ctrl-navigation");
        puppet.pressTerminalKey(GLFW.GLFW_KEY_LEFT, GLFW.GLFW_MOD_CONTROL);
        puppet.pressTerminalKey(GLFW.GLFW_KEY_LEFT, GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SHIFT);
        puppet.pressTerminalKey(GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_MOD_CONTROL);
        puppet.pressTerminalKey(GLFW.GLFW_KEY_ENTER);
        puppet.waitTicks(30);
        puppet.capture("rust-terminal-control-navigation", Component.literal("SFM Terminal ")
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal("Ctrl+A/C and Ctrl+arrow/Shift navigation reach Rust.")));
        puppet.writeTerminalContent("control-navigation", null, null);
        puppet.typeTerminalText("Write-Output ctrl-l-before");
        puppet.pressTerminalKey(GLFW.GLFW_KEY_ENTER);
        puppet.waitTicks(30);
        puppet.pressTerminalKey(GLFW.GLFW_KEY_L, GLFW.GLFW_MOD_CONTROL);
        puppet.typeTerminalText("Write-Output ctrl-l-after");
        puppet.pressTerminalKey(GLFW.GLFW_KEY_ENTER);
        puppet.waitTicks(30);
        puppet.writeTerminalContent("ctrl-l", "ctrl-l-after", "ctrl-l-before");
        puppet.pasteTerminalText("Write-Output pasted-through-ctrl-v");
        puppet.pressTerminalKey(GLFW.GLFW_KEY_ENTER);
        puppet.waitTicks(30);
        puppet.capture("rust-terminal-paste", Component.literal("SFM Terminal ")
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal("Ctrl+V sends clipboard text directly to the Rust-owned PTY.")));
        puppet.writeTerminalContent("paste", "pasted-through-ctrl-v", null);
        puppet.executeTerminal("1..10000 | ForEach-Object { Write-Output $_; Start-Sleep -Milliseconds 1 }");
        puppet.waitTicks(10);
        puppet.pressTerminalKey(GLFW.GLFW_KEY_C, GLFW.GLFW_MOD_CONTROL);
        puppet.waitTicks(30);
        puppet.capture("rust-terminal-ctrl-c-interrupt", Component.literal("SFM Terminal ")
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal("Ctrl+C interrupts a running Rust-owned PowerShell command.")));
        puppet.writeTerminalContent("ctrl-c-interrupt", "❯", "10000");
        puppet.executeTerminal("1..100");
        puppet.waitTicks(30);
        puppet.pressTerminalKey(GLFW.GLFW_KEY_HOME);
        puppet.capture("rust-terminal-powershell-range", Component.literal("SFM Terminal ")
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal("Rust-authoritative PNG terminal range output 1..100.")));
        puppet.writeTerminalContent("powershell-range", "100", null);
        puppet.pressTerminalKey(GLFW.GLFW_KEY_END);
        puppet.executeTerminal("write-host -foregroundcolor cyan \"hello, world!\"");
        puppet.waitTicks(30);
        puppet.capture("rust-terminal-powershell-cyan", Component.literal("SFM Terminal ")
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal("Rust-authoritative PNG terminal with cyan Write-Host output.")));
        puppet.writeTerminalContent("powershell-cyan", "hello, world!", null);
        puppet.executeTerminal("& 'G:\\Programming\\Repos\\ratatui-key-debug\\target\\debug\\ratatui_key_debug.exe'");
        puppet.waitTicks(30);
        puppet.clickTerminal();
        puppet.dragTerminal();
        puppet.scrollTerminal(1);
        puppet.scrollTerminal(-1);
        puppet.resizeTerminal(80, 20);
        puppet.waitTicks(30);
        puppet.capture("rust-terminal-alternate-screen", Component.literal("SFM Terminal ")
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal("Rust terminal restores a child alternate-screen TUI.")));
        puppet.writeTerminalContent("alternate-screen-active", "Key Events", null);
        puppet.writeTerminalContent("alternate-screen-mouse", "MouseEvent", null);
        puppet.writeTerminalContent("alternate-screen-drag", "Drag(Left)", null);
        puppet.writeTerminalContent("alternate-screen-wheel", "ScrollUp", null);
        puppet.writeTerminalContent("alternate-screen-resize", "Resize(80, 20)", null);
        puppet.pressTerminalKeyDirect(GLFW.GLFW_KEY_ESCAPE, 0);
        puppet.pressTerminalKeyDirect(GLFW.GLFW_KEY_ESCAPE, 0);
        puppet.pressTerminalKeyDirect(GLFW.GLFW_KEY_ESCAPE, 0);
        puppet.waitTicks(30);
        puppet.writeTerminalContent("alternate-screen-restored", "❯", "Key Events");
        puppet.restartRustTerminalServer();
        puppet.waitTicks(30);
        puppet.writeTerminalContent("rust-server-reconnected", "❯", null);
        puppet.executeTerminal("1..10000 | ForEach-Object { Write-Output $_; Start-Sleep -Milliseconds 1 }");
        puppet.waitTicks(10);
        puppet.cancelTerminal();
        puppet.waitTicks(30);
        puppet.writeTerminalContent("cancel-rpc", "❯", "10000");
        puppet.pressTerminalKey(GLFW.GLFW_KEY_ESCAPE);
        puppet.pressTerminalKey(GLFW.GLFW_KEY_ESCAPE);
        puppet.pressTerminalKey(GLFW.GLFW_KEY_ESCAPE);
        puppet.capture("rust-terminal-triple-escape-close", Component.literal("SFM Terminal ")
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal("Three-Escape close returns to the underlying screen.")));
    }
}
