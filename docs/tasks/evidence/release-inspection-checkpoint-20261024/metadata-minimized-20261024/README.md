# Minimal external Maven metadata — not admitted

Reduced from 22 to 18 POMs after verifying 24 locked JARs, restoring 9 from exact locked URLs and copying jopt-simple from the verified local Minecraft cache. Total external POM bytes: 185,009. No denial occurred; no versions or artifact pins changed. The four removed external POMs are byte-identical to their embedded counterparts.

Actual native-parser analysis: 13 declared test roots → 24 hash-locked artifact coordinates → 29 runtime edges; no parser errors, unlocked coordinates, or version conflicts. All 18 staged POMs were actually read; each leave-one-out analytical run rejected a reachable artifact. This is lock compatibility for the tested candidate graph, not universal resolution equivalence or aggregate JUnit completion. Parent/BOM metadata affects expansion and remains unadmitted.

Proposed catalog (19 entries including the unchanged approved parent) SHA-256: `76be4cbc67e4bd6d780b00523bed173249a9d7dfec799294f3b0969f2d60358d`. Live catalog remains exactly one approved entry.

| Coordinate | Bytes | SHA-256 | Exact URL |
| --- | ---: | --- | --- |
| com.google.errorprone:error_prone_parent:2.36.0@pom | 15958 | `3a4cfc8a6bed61eb48e969796fc31ea0d270b63e670599946a61883adb7094dc` | https://repo1.maven.org/maven2/com/google/errorprone/error_prone_parent/2.36.0/error_prone_parent-2.36.0.pom |
| com.google.guava:guava-parent:26.0-android@pom | 10180 | `f8698ab46ca996ce889c1afc8ca4f25eb8ac6b034dc898d4583742360016cc04` | https://repo1.maven.org/maven2/com/google/guava/guava-parent/26.0-android/guava-parent-26.0-android.pom |
| com.google.guava:guava-parent:33.4.0-jre@pom | 21100 | `3a499ed34a0d9ee0f1bcc39230021a1cd4e2f7dd0426ab6844f585465d41dcd7` | https://repo1.maven.org/maven2/com/google/guava/guava-parent/33.4.0-jre/guava-parent-33.4.0-jre.pom |
| org.apache.commons:commons-parent:39@pom | 61975 | `87cd27e1a02a5c3eb6d85059ce98696bb1b44c2b8b650f0567c86df60fa61da7` | https://repo1.maven.org/maven2/org/apache/commons/commons-parent/39/commons-parent-39.pom |
| org.apache:apache:16@pom | 15397 | `9f85ff2fd7d6cb3097aa47fb419ee7f0ebe869109f98aba9f4eca3f49e74a40e` | https://repo1.maven.org/maven2/org/apache/apache/16/apache-16.pom |
| org.apiguardian:apiguardian-api:1.1.2@pom | 1526 | `32355081d109095c3d5d374d5a43b4f4c1b75d549e983ef50723e2772e5302a0` | https://repo1.maven.org/maven2/org/apiguardian/apiguardian-api/1.1.2/apiguardian-api-1.1.2.pom |
| org.checkerframework:checker-qual:3.43.0@pom | 2096 | `9313bf53b3efd8aaca266eea8b96e307976b65c0b16510cc6f02319fbaebed43` | https://repo1.maven.org/maven2/org/checkerframework/checker-qual/3.43.0/checker-qual-3.43.0.pom |
| org.junit.jupiter:junit-jupiter-api:5.8.1@pom | 3181 | `77ad7ed4a630bad1fc8746a0a6e675c23fb6940b27ab62cccb34e4fcfcfe39bf` | https://repo1.maven.org/maven2/org/junit/jupiter/junit-jupiter-api/5.8.1/junit-jupiter-api-5.8.1.pom |
| org.junit.jupiter:junit-jupiter-engine:5.8.1@pom | 3199 | `aa320a318a7271e33262c480fcf39965b9a86daa76666eb775342b80e9cdd45e` | https://repo1.maven.org/maven2/org/junit/jupiter/junit-jupiter-engine/5.8.1/junit-jupiter-engine-5.8.1.pom |
| org.junit.platform:junit-platform-commons:1.8.1@pom | 2831 | `e197282e52e700d12b88989edc5489874693502e4aa8907acf0829801aba6d44` | https://repo1.maven.org/maven2/org/junit/platform/junit-platform-commons/1.8.1/junit-platform-commons-1.8.1.pom |
| org.junit.platform:junit-platform-engine:1.8.1@pom | 3202 | `86aad4e657754e43a00df226dd54c8aec1ec6ab6533f50121907e4662de2e5f2` | https://repo1.maven.org/maven2/org/junit/platform/junit-platform-engine/1.8.1/junit-platform-engine-1.8.1.pom |
| org.junit:junit-bom:5.8.1@pom | 5630 | `ef7dc47f8e4a16864f4779728e87ed78539819e4fb892768a9da6b8ef903f863` | https://repo1.maven.org/maven2/org/junit/junit-bom/5.8.1/junit-bom-5.8.1.pom |
| org.openjdk.jmh:jmh-parent:1.37@pom | 11704 | `0c24f216f3637dde7639114f70273a697f8546f7a4c6d5acd4cc6daee9bef4c9` | https://repo1.maven.org/maven2/org/openjdk/jmh/jmh-parent/1.37/jmh-parent-1.37.pom |
| org.opentest4j:opentest4j:1.2.0@pom | 1683 | `a96e671816c1ff8803bdec74c9241f025bdfb277da5d2b4ee02266405936f994` | https://repo1.maven.org/maven2/org/opentest4j/opentest4j/1.2.0/opentest4j-1.2.0.pom |
| org.ow2.asm:asm:9.0@pom | 2724 | `de0355590dd1bfcccdc8d79024ae992972e855268acedb9ad682d085e03a94ad` | https://repo1.maven.org/maven2/org/ow2/asm/asm/9.0/asm-9.0.pom |
| org.ow2:ow2:1.5@pom | 11230 | `0f8a1b116e760b8fe6389c51b84e4b07a70fc11082d4f936e453b583dd50b43b` | https://repo1.maven.org/maven2/org/ow2/ow2/1.5/ow2-1.5.pom |
| org.sonatype.oss:oss-parent:7@pom | 4824 | `b51f8867c92b6a722499557fc3a1fdea77bdf9ef574722fe90ce436a29559454` | https://repo1.maven.org/maven2/org/sonatype/oss/oss-parent/7/oss-parent-7.pom |
| org.sonatype.oss:oss-parent:9@pom | 6569 | `fb40265f982548212ff82e362e59732b2187ec6f0d80182885c14ef1f982827a` | https://repo1.maven.org/maven2/org/sonatype/oss/oss-parent/9/oss-parent-9.pom |

See `artifacts.json`, `omitted-external-poms.json`, `minimal-receipts.json`, and `graph-review/independent-review.md`. The graph harness used existing cached Rust dependencies and unchanged native parser modules. External POMs were analytical inputs only; no cache/catalog admission or aggregate run used them. The intended coordinate set was the inspected locked test graph; the artifact lock does not independently encode every transitive edge.

Original recording, picker and 22-POM proposal evidence remains unchanged. No push, merge, desktop action or Library retry occurred.
