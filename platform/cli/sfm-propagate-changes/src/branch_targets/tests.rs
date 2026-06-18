use super::BranchGlob;
use super::BranchName;
use super::BranchQuery;
use super::BranchRule;
use super::ExactBranch;
use super::MinecraftVersion;
use super::VersionOp;
use super::VersionScope;
use super::WorktreePath;
use super::WorktreeTarget;
use arbitrary::Arbitrary;
use std::path::Path;
use std::path::PathBuf;

#[test]
fn minecraft_version_orders_new_scheme_after_old_scheme() {
    let old_scheme = MinecraftVersion::parse("1.21.1").expect("version should parse");
    let new_scheme = MinecraftVersion::parse("26.1.2").expect("version should parse");
    let old_without_patch = MinecraftVersion::parse("1.20").expect("version should parse");
    let old_with_patch = MinecraftVersion::parse("1.20.0").expect("version should parse");

    assert!(new_scheme > old_scheme);
    assert_eq!(old_without_patch, old_with_patch);
    let _ = MinecraftVersion::parse("1.19.2-patch").unwrap_err();
}

#[test]
fn worktree_target_reads_gradle_properties_and_classifies_core_branch() {
    let temp = TestDir::new();
    let core_path = temp.worktree_with_minecraft_version("core", "1.19.2");
    let feature_path = temp.worktree_with_minecraft_version("feature", "1.19.2");
    let patch_path = temp.worktree_with_minecraft_version("patch", "1.19.2");

    let core =
        WorktreeTarget::from_parts(BranchName::from("1.19.2"), WorktreePath::from(core_path))
            .expect("core target should load");
    let feature = WorktreeTarget::from_parts(
        BranchName::from("feat/1.19.2/draw"),
        WorktreePath::from(feature_path),
    )
    .expect("feature target should load");
    let patch = WorktreeTarget::from_parts(
        BranchName::from("1.19.2-patch"),
        WorktreePath::from(patch_path),
    )
    .expect("patch target should load");

    assert!(core.core);
    assert_eq!(
        core.mc_version.as_ref().map(MinecraftVersion::as_str),
        Some("1.19.2")
    );
    assert!(!feature.core);
    assert_eq!(
        feature.mc_version.as_ref().map(MinecraftVersion::as_str),
        Some("1.19.2")
    );
    assert!(!patch.core);
}

#[test]
fn branch_query_default_matches_core_targets_only() {
    let targets = sample_targets();
    let matches = matching_branches(&BranchQuery::default(), &targets);

    assert_eq!(
        matches,
        vec![
            "1.19.2".to_string(),
            "1.20.1".to_string(),
            "1.21.1".to_string(),
            "26.1.2".to_string()
        ]
    );
}

#[test]
fn branch_query_star_matches_every_target() {
    let targets = sample_targets();
    let query = BranchQuery::parse("*").expect("query should parse");
    let matches = matching_branches(&query, &targets);

    assert_eq!(
        matches,
        vec![
            "1.19.2".to_string(),
            "1.20.1".to_string(),
            "1.21.1".to_string(),
            "26.1.2".to_string(),
            "feat/1.19.2/draw".to_string()
        ]
    );
}

#[test]
fn branch_query_supports_or_aliases_for_exact_branches() {
    let targets = sample_targets();
    let queries = [
        "1.19.2 OR 26.1.2",
        "1.19.2,26.1.2",
        "1.19.2|26.1.2",
        "1.19.2||26.1.2",
    ];

    for query_text in queries {
        let query = BranchQuery::parse(query_text).expect("query should parse");
        assert_eq!(
            matching_branches(&query, &targets),
            vec!["1.19.2".to_string(), "26.1.2".to_string()],
            "query {query_text:?}"
        );
    }
}

#[test]
fn branch_query_supports_and_aliases() {
    let targets = sample_targets();
    let queries = ["core AND >=1.20", "core & >=1.20", "core && >=1.20"];

    for query_text in queries {
        let query = BranchQuery::parse(query_text).expect("query should parse");
        assert_eq!(
            matching_branches(&query, &targets),
            vec![
                "1.20.1".to_string(),
                "1.21.1".to_string(),
                "26.1.2".to_string()
            ],
            "query {query_text:?}"
        );
    }
}

#[test]
fn branch_query_supports_glob_and_exact_feature_branches() {
    let targets = sample_targets();
    let glob = BranchQuery::parse("feat/*").expect("query should parse");
    let exact = BranchQuery::parse("feat/1.19.2/draw").expect("query should parse");

    assert_eq!(
        matching_branches(&glob, &targets),
        vec!["feat/1.19.2/draw".to_string()]
    );
    assert_eq!(
        matching_branches(&exact, &targets),
        vec!["feat/1.19.2/draw".to_string()]
    );
}

#[test]
fn plain_version_predicate_matches_feature_branches_by_minecraft_version() {
    let targets = sample_targets();
    let query = BranchQuery::parse(">=1.19.2").expect("query should parse");
    let matches = matching_branches(&query, &targets);

    assert_eq!(
        matches,
        vec![
            "1.19.2".to_string(),
            "1.20.1".to_string(),
            "1.21.1".to_string(),
            "26.1.2".to_string(),
            "feat/1.19.2/draw".to_string()
        ]
    );
}

#[test]
fn compact_core_version_predicate_excludes_feature_branches() {
    let targets = sample_targets();
    let query = BranchQuery::parse("core>=1.19.2").expect("query should parse");
    let matches = matching_branches(&query, &targets);

    assert_eq!(
        matches,
        vec![
            "1.19.2".to_string(),
            "1.20.1".to_string(),
            "1.21.1".to_string(),
            "26.1.2".to_string()
        ]
    );
}

#[test]
fn compact_core_version_predicate_treats_26_as_newer_than_1_21() {
    let targets = sample_targets();
    let query = BranchQuery::parse("core>=26.0.0").expect("query should parse");
    let matches = matching_branches(&query, &targets);

    assert_eq!(matches, vec!["26.1.2".to_string()]);
}

#[test]
fn branch_query_parses_to_strong_rule_types() {
    let query = BranchQuery::parse("core>=1.20,feat/*").expect("query should parse");

    assert_eq!(query.alternatives.len(), 2);
    assert_eq!(
        query.alternatives[0].rules,
        vec![BranchRule::Version {
            scope: VersionScope::CoreOnly,
            op: VersionOp::Gte,
            version: MinecraftVersion::parse("1.20").expect("version should parse"),
        }]
    );
    assert_eq!(
        query.alternatives[1].rules,
        vec![BranchRule::BranchGlob(
            BranchGlob::new("feat/*").expect("glob should parse")
        )]
    );
}

#[test]
fn branch_query_display_canonicalizes_and_round_trips() {
    let query = BranchQuery::parse("core>=1.20|feat/*&&<26.0.0").expect("query should parse");

    assert_eq!(query.to_string(), "core>=1.20 OR feat/* AND <26.0.0");
    assert_eq!(
        BranchQuery::parse(&query.to_string()).expect("displayed query should parse"),
        query
    );
}

#[test]
fn branch_query_display_handles_exact_branch_rules() {
    let query = BranchQuery {
        alternatives: vec![super::BranchConjunction {
            rules: vec![BranchRule::ExactBranch(ExactBranch::from(
                "feat/1.19.2/draw",
            ))],
        }],
    };

    assert_eq!(query.to_string(), "feat/1.19.2/draw");
    assert_eq!(
        BranchQuery::parse(&query.to_string()).expect("displayed query should parse"),
        query
    );
}

#[test]
fn arbitrary_branch_queries_round_trip_through_display() {
    let mut successes = 0usize;
    for seed in 0..512u64 {
        let bytes = deterministic_bytes(seed);
        let mut unstructured = arbitrary::Unstructured::new(&bytes);
        let Ok(query) = BranchQuery::arbitrary(&mut unstructured) else {
            continue;
        };
        let displayed = query.to_string();
        let reparsed = BranchQuery::parse(&displayed).unwrap_or_else(|err| {
            panic!("displayed arbitrary query should parse: {displayed}: {err}");
        });
        assert_eq!(reparsed, query, "displayed query: {displayed}");
        successes += 1;
    }

    assert!(successes > 100, "expected enough arbitrary query cases");
}

#[test]
fn branch_query_rejects_empty_expression_groups() {
    let _ = BranchQuery::parse("core OR ").unwrap_err();
    let _ = BranchQuery::parse("|core").unwrap_err();
    let _ = BranchQuery::parse("core && ").unwrap_err();
}

#[test]
fn word_operators_do_not_split_branch_path_segments() {
    let target = WorktreeTarget {
        branch: BranchName::from("feat/or/draw"),
        worktree_path: WorktreePath::from(PathBuf::from("feat-or-draw")),
        core: false,
        mc_version: Some(MinecraftVersion::parse("1.19.2").expect("version should parse")),
    };
    let query = BranchQuery::parse("feat/or/draw").expect("query should parse");

    assert!(query.matches(&target));
}

fn matching_branches(query: &BranchQuery, targets: &[WorktreeTarget]) -> Vec<String> {
    query
        .filter_targets(targets)
        .into_iter()
        .map(|target| target.branch.as_str().to_string())
        .collect()
}

fn sample_targets() -> Vec<WorktreeTarget> {
    [
        ("1.19.2", true, "1.19.2"),
        ("1.20.1", true, "1.20.1"),
        ("1.21.1", true, "1.21.1"),
        ("26.1.2", true, "26.1.2"),
        ("feat/1.19.2/draw", false, "1.19.2"),
    ]
    .into_iter()
    .map(|(branch, core, mc_version)| WorktreeTarget {
        branch: BranchName::from(branch),
        worktree_path: WorktreePath::from(PathBuf::from(branch)),
        core,
        mc_version: Some(MinecraftVersion::parse(mc_version).expect("version should parse")),
    })
    .collect()
}

fn deterministic_bytes(seed: u64) -> Vec<u8> {
    let mut state = seed.wrapping_add(0x9e37_79b9_7f4a_7c15);
    let mut bytes = Vec::with_capacity(256);
    for _ in 0..256 {
        state = state
            .wrapping_mul(6_364_136_223_846_793_005)
            .wrapping_add(1);
        bytes.push(state.to_le_bytes()[4]);
    }
    bytes
}

struct TestDir {
    dir: tempfile::TempDir,
}

impl TestDir {
    fn new() -> Self {
        let dir = tempfile::Builder::new()
            .prefix("sfm-branch-targets-test-")
            .tempdir()
            .expect("test temp dir should be created");
        Self { dir }
    }

    fn worktree_with_minecraft_version(&self, name: &str, minecraft_version: &str) -> PathBuf {
        let worktree = self.dir.path().join(name);
        write_gradle_properties(&worktree, minecraft_version);
        worktree
    }
}

fn write_gradle_properties(worktree: &Path, minecraft_version: &str) {
    let minecraft_dir = worktree.join("platform").join("minecraft");
    std::fs::create_dir_all(&minecraft_dir).expect("minecraft dir should be created");
    std::fs::write(
        minecraft_dir.join("gradle.properties"),
        format!("minecraft_version={minecraft_version}\n"),
    )
    .expect("gradle.properties should be written");
}
