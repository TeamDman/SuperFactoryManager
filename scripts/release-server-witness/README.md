# Isolated production-server registry and selected-save witness

This test-only runner compares unchanged official and checked-in projected SFM 4.34.0 JARs. It supports the 1.19.4 release on Forge 45.0.9 and the 1.20 release on Forge 46.0.10, both with release-pinned JBRSDK 17.0.14. The same auxiliary probe JAR is loaded in every boot for a target. Its registry snapshot includes server-visible built-in entries, Forge creative tabs, and SFM's three unwrapped custom registries. Client-only text-editor registries are outside this dedicated-server witness.

| Target | Official JAR SHA-256 | Checked-in projected JAR SHA-256 | Forge installer SHA-1 |
| --- | --- | --- | --- |
| 1.19.4 | `09b8c7d2ae6453d39444c61174979d1938d25d4f24282f8396711f329c5b5fba` | `4a39e9512a47925639a1ba7126ea903576917b120d43dcedfa66563ebcea9735` | [`b1cdd5fa1cc50fa23a32c9b38fd9d1f8a9a6c5e9`](https://files.minecraftforge.net/net/minecraftforge/forge/index_1.19.4.html) |
| 1.20 | `9943c04e04f7afc433e3f9ccea223ab15f42ba0930c4cebe31b2008ad3b35ecd` | `90227fa968cd7351adcc5a1e8939c29ee6b3d4109c50d66c48d824425508a583` | [`b87fb7da06335a907a59d32dc22698f3f2a1f885`](https://files.minecraftforge.net/net/minecraftforge/forge/index_1.20.html) |

The 1.20 tag's Gradle properties select Forge 46.0.10; its Java toolchain script selects Java 17. The current toolchain lock pins JBRSDK 17.0.14 b1367.22 for that Java line. The official 1.20 JAR digest also matches the [GitHub 4.34.0 release asset](https://github.com/TeamDman/SuperFactoryManager/releases/tag/4.34.0-26.1.2). The runner verifies both input JAR digests, the selected installer's SHA-1 and SHA-256, the exact-version Forge SRG compile JAR digest, the JBRSDK runtime, and the installed Forge launch identity. The installer, JDK, compile JAR, and SFM JARs are read-only inputs. All server files and worlds are created below a **new, absent scratch directory** outside the repository. The installer may need network access to fetch dependencies.

Run the 1.20 witness with paths selected on the local machine:

```powershell
.\Run-ReleaseServerWitness.ps1 `
  -Target 1.20 `
  -OfficialJar <official-4.34.0-1.20-jar> `
  -OfficialSha256 9943c04e04f7afc433e3f9ccea223ab15f42ba0930c4cebe31b2008ad3b35ecd `
  -ProjectedJar <checked-in-1.20-reobf-jar> `
  -ProjectedSha256 90227fa968cd7351adcc5a1e8939c29ee6b3d4109c50d66c48d824425508a583 `
  -ForgeInstaller <forge-1.20-46.0.10-installer.jar> `
  -ForgeSrgJar <forge-1.20-46.0.10-srg.jar> `
  -JavaHome <jbrsdk-17.0.14-home> `
  -RunRoot <new-absent-scratch-directory>
```

For 1.19.4, omit `-Target` or use `-Target 1.19.4` and substitute that row's JARs and Forge 45.0.9 inputs. `ForgeSrgJar` is the exact-version ForgeGradle SRG output from a built project. The test-only Java class is compiled directly against that runtime naming, then packaged with its target's `mods.toml` and `pack.mcmeta`; it is never copied into an SFM source or release root. Compilation uses the newly installed exact loader libraries. `-LauncherCacheRoot <exact-loader-cache-root>` remains available for an existing complete read-only library cache.

For a retry after a non-disk harness error, `-InstalledForgeRoot <previous-scratch-forge-install>` may reuse a previously completed exact installation as a read-only library input. The new `RunRoot` and all worlds still have to be absent. Never point this option at a player or shared test server. The installer hash and installed launch identity are checked even when files are reused. Do not use this option after a disk-space error; stop and wait for the user instead.

The driver starts four isolated production servers in order: official seed; official control and projected candidate from byte-identical complete seed-world copies; and official reverse from the candidate-saved world. It requires `Done`, exact Forge/Minecraft identity, a sorted registry snapshot, successful save and exit zero in every boot. Control, candidate, and reverse must agree case-sensitively on disk program, derived disk name, legacy label, empty errors, empty warnings, and stone/north/`STRETCH` facade. It compares registry IDs as strings, not runtime numeric ordinals. A missing snapshot, missing selected value, or missing required registry family fails closed.

The scratch root retains per-boot `commands.txt`, `logs/latest.log`, `registry-snapshot.json`, process logs, worlds, and a path-free `result.json`. The result binds input, runner, probe-source and probe-JAR hashes and includes the exact six observations and registry-ID diff. Do not commit scratch files or absolute machine paths. A disk-space error is a terminal stop: do not clean, delete, or retry until the user resolves it. A passing result is only a selected server registry/save witness, not general save, gameplay, client, or release acceptance.
