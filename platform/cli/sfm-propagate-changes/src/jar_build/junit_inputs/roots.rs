use super::PreflightReport;
use super::TestPin;
use super::approved_pins::LAUNCHER;
use super::authority;
use super::issue;
use super::source_lock::SourceLock;
use super::target_recipe::TargetRecipe;
use super::test_root::TestRoot;

pub(super) fn expected_roots(target: &str) -> Vec<(&'static str, &'static str)> {
    let mut roots = vec![
        (
            "testImplementation",
            "com.github.javaparser:javaparser-symbol-solver-core:3.26.4",
        ),
        (
            "testImplementation",
            "org.junit.jupiter:junit-jupiter-api:5.8.1",
        ),
        (
            "testRuntimeOnly",
            "org.junit.jupiter:junit-jupiter-engine:5.8.1",
        ),
        ("testImplementation", "org.openjdk.jmh:jmh-core:1.37"),
        (
            "testImplementation",
            "org.openjdk.jmh:jmh-generator-annprocess:1.37",
        ),
        (
            "testAnnotationProcessor",
            "org.openjdk.jmh:jmh-generator-annprocess:1.37",
        ),
        (
            "testImplementation",
            "org.openjdk.jmh:jmh-generator-bytecode:1.37",
        ),
    ];
    if target == "26.1.2" {
        roots.push(("testRuntimeOnly", LAUNCHER.0));
    }
    roots
}

pub(super) fn production_base_witnesses(
    report: &mut PreflightReport,
    recipe: &TargetRecipe,
    lock: &SourceLock,
) -> eyre::Result<Vec<TestPin>> {
    let (coordinate, expected) = super::approved_pins::BASE_JSR305;
    let pins = authority::pin_map(lock)?;
    if pins.get(coordinate).is_none_or(|hash| hash != expected) {
        issue(
            report,
            Some(&recipe.target),
            "production",
            "production_base_witness_missing_or_conflicting",
            format!(
                "Guava test transitive requires existing production base pin {coordinate} = {expected}; actual {:?}",
                pins.get(coordinate)
            ),
        );
    }
    Ok(vec![TestPin {
        coordinate: coordinate.to_owned(),
        content_hash: expected.to_owned(),
        full_sha256: None,
        bytes: None,
        origin: None,
        authority: "existing_production_base_classpath".to_owned(),
    }])
}

pub(super) fn validate_target(
    report: &mut PreflightReport,
    recipe: &TargetRecipe,
    lock: &SourceLock,
    pins: &mut Vec<TestPin>,
) -> eyre::Result<Vec<TestRoot>> {
    let target = recipe.target.as_str();
    let existing = authority::pin_map(lock)?;
    if target == "26.1.2" {
        pins.push(TestPin {
            coordinate: LAUNCHER.0.to_owned(),
            content_hash: LAUNCHER.1.to_owned(),
            full_sha256: None,
            bytes: None,
            origin: None,
            authority: "unchanged_target_released_test_pin".to_owned(),
        });
    }
    pins.sort_by(|a, b| a.coordinate.cmp(&b.coordinate));
    for pin in pins.iter() {
        match existing.get(&pin.coordinate) {
            Some(hash) if hash != &pin.content_hash => issue(
                report,
                Some(target),
                "test_only",
                "test_only_pin_conflict",
                format!(
                    "{}: expected {}, released pin {hash}",
                    pin.coordinate, pin.content_hash
                ),
            ),
            None if pin.authority != "reviewed_test_provision" => issue(
                report,
                Some(target),
                "test_only",
                "test_only_missing_pin",
                format!("reviewed base pin absent: {}", pin.coordinate),
            ),
            _ => {}
        }
    }
    let roots = collect_roots(recipe);
    let mut actual: Vec<_> = roots
        .iter()
        .map(|r| (r.configuration.as_str(), r.coordinate.as_str()))
        .collect();
    actual.sort_unstable();
    let mut expected = expected_roots(target);
    expected.sort_unstable();
    if actual != expected {
        issue(
            report,
            Some(target),
            "test_only",
            "test_only_root_mismatch",
            format!("expected roots {expected:?}, actual {actual:?}"),
        );
    }
    for root in &roots {
        if !pins
            .iter()
            .any(|pin| pin.coordinate == root.coordinate && pin.content_hash == root.content_hash)
        {
            issue(
                report,
                Some(target),
                "test_only",
                "test_only_root_pin_mismatch",
                format!(
                    "unapproved test root/hash: {} {} {}",
                    root.configuration, root.coordinate, root.content_hash
                ),
            );
        }
    }
    for dependency in &lock.dependencies {
        if !dependency.configuration.starts_with("test")
            && !existing.contains_key(&dependency.resolved_notation)
        {
            issue(
                report,
                Some(target),
                "production",
                "production_unpinned_dependency",
                format!(
                    "{}: {}",
                    dependency.configuration, dependency.resolved_notation
                ),
            );
        }
    }
    Ok(roots)
}

fn collect_roots(recipe: &TargetRecipe) -> Vec<TestRoot> {
    recipe
        .released_gradle_supplements
        .iter()
        .filter(|r| r.configuration.starts_with("test"))
        .map(|r| TestRoot {
            configuration: r.configuration.clone(),
            coordinate: r.coordinate.clone(),
            content_hash: r.artifact_hash.clone(),
        })
        .collect()
}
