Ahoy!

The single-branch source-projection workflow is experimental. If you are working in a `platform/minecraft/mc-version/<target>` project, follow the [generated-project contributor guide](source-projection-contributor-guide.md): plain Gradle builds work, and you may propose edits to generated Java. Maintainers must reconcile those edits into canonical source before regeneration. The version-branch workflow below remains the production contribution path until the projection is accepted.

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
1.19.2 # pick the 1.19.2 TUI entry and enter its platform/minecraft directory
idea . # open intellij in the cwd
```

Changes are generally performed on the 1.19.2 branch and changes get forward propagate by daisy chaining git merges from the the old branches into the new ones.

You may ping me on [Discord](https://discord.gg/5mbUY3mu6m) if you have any questions.

If you are reporting a bug rather than contributing code, the [issue-reporting guide](REPORTING_ISSUES.md) explains which environment details and small comparison tests help us reproduce it.
