//! Fixed root-first bindings for the reviewed flat closure. Common ordering
//! follows the previous root/BFS traversal, after the existing base jsr305 entry
//! wins deduplication. No POM is read or resolved by this adapter. Target26 adds
//! its already-pinned launcher as the last declared runtime root; its official
//! 1.8.1 module dependencies are already covered by the common closure.
use super::approved_pins::LAUNCHER;
use super::classpath_bindings::ClasspathBindings;
const COMPILE: [&str; 21] = [
    "com.github.javaparser:javaparser-symbol-solver-core:3.26.4",
    "org.junit.jupiter:junit-jupiter-api:5.8.1",
    "org.openjdk.jmh:jmh-core:1.37",
    "org.openjdk.jmh:jmh-generator-annprocess:1.37",
    "org.openjdk.jmh:jmh-generator-bytecode:1.37",
    "com.github.javaparser:javaparser-core:3.26.4",
    "org.javassist:javassist:3.30.2-GA",
    "com.google.guava:guava:33.4.0-jre",
    "org.opentest4j:opentest4j:1.2.0",
    "org.junit.platform:junit-platform-commons:1.8.1",
    "org.apiguardian:apiguardian-api:1.1.2",
    "net.sf.jopt-simple:jopt-simple:5.0.4",
    "org.apache.commons:commons-math3:3.6.1",
    "org.openjdk.jmh:jmh-generator-reflection:1.37",
    "org.openjdk.jmh:jmh-generator-asm:1.37",
    "com.google.guava:failureaccess:1.0.2",
    "com.google.guava:listenablefuture:9999.0-empty-to-avoid-conflict-with-guava",
    "org.checkerframework:checker-qual:3.43.0",
    "com.google.errorprone:error_prone_annotations:2.36.0",
    "com.google.j2objc:j2objc-annotations:3.0.0",
    "org.ow2.asm:asm:9.0",
];
const RUNTIME: [&str; 23] = [
    "com.github.javaparser:javaparser-symbol-solver-core:3.26.4",
    "org.junit.jupiter:junit-jupiter-api:5.8.1",
    "org.junit.jupiter:junit-jupiter-engine:5.8.1",
    "org.openjdk.jmh:jmh-core:1.37",
    "org.openjdk.jmh:jmh-generator-annprocess:1.37",
    "org.openjdk.jmh:jmh-generator-bytecode:1.37",
    "com.github.javaparser:javaparser-core:3.26.4",
    "org.javassist:javassist:3.30.2-GA",
    "com.google.guava:guava:33.4.0-jre",
    "org.opentest4j:opentest4j:1.2.0",
    "org.junit.platform:junit-platform-commons:1.8.1",
    "org.apiguardian:apiguardian-api:1.1.2",
    "org.junit.platform:junit-platform-engine:1.8.1",
    "net.sf.jopt-simple:jopt-simple:5.0.4",
    "org.apache.commons:commons-math3:3.6.1",
    "org.openjdk.jmh:jmh-generator-reflection:1.37",
    "org.openjdk.jmh:jmh-generator-asm:1.37",
    "com.google.guava:failureaccess:1.0.2",
    "com.google.guava:listenablefuture:9999.0-empty-to-avoid-conflict-with-guava",
    "org.checkerframework:checker-qual:3.43.0",
    "com.google.errorprone:error_prone_annotations:2.36.0",
    "com.google.j2objc:j2objc-annotations:3.0.0",
    "org.ow2.asm:asm:9.0",
];

pub(super) fn for_target(target: &str) -> ClasspathBindings {
    let mut runtime: Vec<_> = RUNTIME.iter().map(|s| (*s).to_owned()).collect();
    if target == "26.1.2" {
        runtime.insert(6, LAUNCHER.0.to_owned());
    }
    ClasspathBindings {
        compile: COMPILE.iter().map(|s| (*s).to_owned()).collect(),
        runtime,
        annotation_processor_roots: vec![
            "org.openjdk.jmh:jmh-generator-annprocess:1.37".to_owned(),
        ],
        console: "org.junit.platform:junit-platform-console-standalone:1.8.1".to_owned(),
    }
}
