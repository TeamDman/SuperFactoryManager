# Isolated 1.19.4 production-server witness

This test-only runner compares unchanged official and projected SFM 4.34.0 JARs on Forge 45.0.9 with JDK 17. It does not modify either input JAR, a user save, or any generated SFM project. The same auxiliary probe JAR is loaded in both production servers. Its registry snapshot includes server-visible built-in entries, Forge creative tabs, and SFM's three unwrapped custom registries. Client-only text-editor registries are outside this dedicated-server witness.

The runner requires a **new, absent scratch directory**. It verifies both SFM JAR SHA-256 values, the cached Forge installer against [Forge's published 45.0.9 SHA-1](https://files.minecraftforge.net/net/minecraftforge/forge/index_1.19.4.html), the Forge 45.0.9 SRG compile JAR, and the JDK 17 runtime before writing to scratch. The installer, JDK, launcher cache, compile JAR, and source JARs are read-only inputs. The exact Forge server is installed under scratch; the existing test server is never used as a run root. The script may need network access if the Forge installer cannot satisfy its dependencies from cache.

Run from this directory with paths selected on the local machine:

```powershell
.\Run-ReleaseServerWitness.ps1 `
  -OfficialJar <official-4.34.0-jar> `
  -OfficialSha256 09b8c7d2ae6453d39444c61174979d1938d25d4f24282f8396711f329c5b5fba `
  -ProjectedJar <checked-in-1.19.4-reobf-jar> `
  -ProjectedSha256 4a39e9512a47925639a1ba7126ea903576917b120d43dcedfa66563ebcea9735 `
  -ForgeInstaller <forge-1.19.4-45.0.9-installer.jar> `
  -ForgeSrgJar <forge-1.19.4-45.0.9-srg.jar> `
  -LauncherCacheRoot <launcher-cache-root> `
  -JavaHome <jdk-17-home> `
  -RunRoot <new-absent-scratch-directory>
```

`ForgeSrgJar` is the exact-version ForgeGradle SRG output from a built 1.19.4 project. The test-only Java class is compiled directly against that runtime naming, then packaged with its own `mods.toml`; it is never copied into an SFM source or release root. `LauncherCacheRoot` supplies only cached 45.0.9 FML/eventbus compile inputs. If the sandbox cannot read a cached installer or JDK, use scoped access to those inputs; do not change dependency declarations or substitute a different loader.

For a retry after a non-disk harness error, `-InstalledForgeRoot <previous-scratch-forge-install>` may reuse a previously completed exact 45.0.9 installation as a read-only library input. The new `RunRoot` and all worlds still have to be absent. Never point this option at an existing player or shared test server. The installer hash and installed launch identity are checked even when its files are reused. Do not use this option after a disk-space error; stop and wait for the user instead.

The driver starts four isolated production servers in order: official seed; official control and projected candidate from byte-identical complete seed-world copies; and official reverse from the candidate-saved world. It requires `Done`, exact Forge/Minecraft identity, a sorted registry snapshot, successful save and exit zero in every boot. Control, candidate, and reverse must agree case-sensitively on disk program, derived disk name, legacy label, empty errors, empty warnings, and stone/north/`STRETCH` facade. It compares registry IDs as strings, not runtime numeric ordinals. A missing snapshot, missing selected value, or missing required registry family fails closed.

The new scratch root retains per-boot `commands.txt`, `logs/latest.log`, `registry-snapshot.json`, process logs, worlds, and a path-free `result.json`. The result binds input, runner, probe-source and probe-JAR hashes and includes the exact six observations and registry-ID diff. Do not commit scratch files or absolute machine paths. A disk-space error is a terminal stop: do not clean, delete, or retry until the user resolves it. A passing result is only a selected server registry/save witness, not general save, gameplay, client, or release acceptance.
