# Release JAR disabled-feature absence check

Run this read-only check after building a candidate JAR. It does not run Gradle,
alter the JAR, or advance a released preset:

```powershell
cargo run --offline --manifest-path platform/cli/sfm-propagate-changes/Cargo.toml --bin source-jar-absence -- --manifest platform/minecraft/source-projection.json --target 1.19.2 --preset released-4.34.0 --jar '<candidate.jar>'
```

The checker derives forbidden outer-JAR entries from disabled `Include` effects
for the selected target and preset. A `src/main/java/.../Name.java` effect
forbids `.../Name.class`, its `Name$*.class` nested classes, and their
multi-release variants. A `src/main/resources/...` effect forbids the matching
resource entry. It ignores test and GameTest inputs, which are not release-JAR
inputs. A disabled supported feature with no derivable runtime `Include` fails
closed instead of reporting a vacuous pass.
Targets without a supported disabled feature report `NO_APPLICABLE_FEATURES`
instead of claiming an absence proof.

This is only an outer-ZIP central-directory entry-name gate. It does not read
compressed payloads or verify their integrity. It cannot prove that conditional
`Template` code, registered behavior, dependency contents, or classes inside
nested JARs are absent. It assumes that a Java Include's output path names its
compiled top-level class, and that Gradle's `processResources` does not rename
or relocate an included resource. Keep source inspection and JAR parity checks
as separate release gates. A passing result does not certify the JAR's
provenance or its identity against the candidate manifest.

The current manifest has disabled Java Includes but no disabled runtime
resource Include in the released preset. An isolated development fixture now
provides a real artifact-level resource proof: two external 1.19.2 ForgeGradle
`reobfJar` builds differed by exactly one resource entry,
`assets/sfm/projection-proof/m3-resource.json`. The enabled control contained
its 22,599-byte payload; the disabled build omitted it. This checker passed
the disabled JAR and rejected the enabled control. The retained custom
manifest SHA-256 is
`d594222db9ad5c5edccaf4b61add599ee617d01603401db8c010c1d5235fd2df`;
the enabled and disabled JAR SHA-256 hashes are
`20d3ae4548a774c93f0c2154434c5d8cb6226fa8931a4a2220bb3d8518d99057`
and `1c44f32d01ce84a42d5d478ea8eb806fabfa5fb2aa94a72970f45b4bb3e21d95`.
The fixture, logs and exact entry comparison remain in external local scratch
evidence, not in the checked-in release preset. This proves the projection
mechanism on a real production JAR, not a resource-absence claim about 4.34.0.
