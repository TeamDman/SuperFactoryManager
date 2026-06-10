Ahoy!

I'm pretty stingy with accepting contributions beyond localization changes.
You are welcome to submit stuff, but **there's no guarantee your submissions will get merged or that they will receive much of a response.**

To get started, you are going to want to create a dir for SFM where you will clone the repo once and subsequently set up adjacent workdirs.
See [AGENTS.md](./AGENTS.md) for additional context on how my personal setup looks like. I use Windows for my support tooling but it's not mandatory.

An IntelliJ [code style XML file](../platform/minecraft/codestyles/Default.xml) is provided.

When opening IntelliJ, I recommend you open the [platform/minecraft](../platform/minecraft/) dir instead of the top-level of the repo.

Personally, I have a [function in my shell](../platform/pwsh/profile.ps1) that lets me `Set-Location` between the different dirs.  
So for me it's as easy as 

```powershell
cs # "change source"; opens a fuzzy finder TUI with all the platform/minecraft dirs listed
1.19.2 # pick the 1.19.2 TUI entry and get Set-Location'd to `D:/Repos/Minecraft/SFM/repos2/1.19.2/platform/minecraft`
idea . # open intellij in the cwd
```

Changes are generally performed on the 1.19.2 branch and changes get forward propagate by daisy chaining git merges from the the old branches into the new ones.

You may ping me on [Discord](https://discord.gg/5mbUY3mu6m) if you have any questions.