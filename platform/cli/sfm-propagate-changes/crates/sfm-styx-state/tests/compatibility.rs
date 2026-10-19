mod legacy_state;

// Compile the unchanged historical path proxy against this crate's old Facet
// dependency to compare the exact original state encoding at the boundary.
#[path = "../../../src/sfm_path.rs"]
mod sfm_path;

use legacy_state::LegacyState;
use legacy_state::LegacyStatus;
use sfm_styx_state::PropagationState;
use sfm_styx_state::decode;
use sfm_styx_state::encode;

#[test]
fn idle_preserves_original_styx_bytes() {
    let original = facet_styx::to_string(&LegacyState {
        status: LegacyStatus::Idle,
    })
    .expect("serialize original idle schema");
    assert_eq!(original, include_str!("fixtures/idle.styx"));
    assert_eq!(
        encode(PropagationState::Idle).expect("encode idle"),
        original
    );
    assert_eq!(
        decode(&original).expect("read old idle"),
        PropagationState::Idle
    );
}

#[test]
fn conflict_preserves_original_styx_bytes_and_path_proxy() {
    let source_branch = "1.19.2";
    let source_path = r#"D:\SFM worktrees\雪 \"source\""#;
    let dest_branch = "feature/next\nbranch";
    let dest_path = "D:/SFM worktrees/next/# @ {} \\ dest";
    let original = facet_styx::to_string(&LegacyState {
        status: LegacyStatus::MergingWithConflict {
            source_branch: source_branch.into(),
            source_path: source_path.into(),
            dest_branch: dest_branch.into(),
            dest_path: dest_path.into(),
        },
    })
    .expect("serialize original conflict schema");
    assert_eq!(original, include_str!("fixtures/conflict.styx"));
    let expected = PropagationState::MergingWithConflict {
        source_branch: source_branch.into(),
        source_path: source_path.into(),
        dest_branch: dest_branch.into(),
        dest_path: dest_path.into(),
    };
    assert_eq!(encode(expected).expect("encode conflict"), original);
    assert_eq!(
        decode(&original).expect("read old conflict"),
        PropagationState::MergingWithConflict {
            source_branch: source_branch.into(),
            source_path: source_path.into(),
            dest_branch: dest_branch.into(),
            dest_path: dest_path.into(),
        }
    );
    let legacy: LegacyState =
        facet_styx::from_str(&original).expect("old decoder reads new encoding");
    match &legacy.status {
        LegacyStatus::MergingWithConflict {
            source_branch: actual_source_branch,
            source_path: actual_source_path,
            dest_branch: actual_dest_branch,
            dest_path: actual_dest_path,
        } => {
            assert_eq!(actual_source_branch, source_branch);
            assert_eq!(actual_source_path, &source_path.into());
            assert_eq!(actual_dest_branch, dest_branch);
            assert_eq!(actual_dest_path, &dest_path.into());
        }
        LegacyStatus::Idle => panic!("conflict fixture decoded as idle"),
    }
    assert_eq!(
        facet_styx::to_string(&legacy).expect("legacy encoding"),
        original
    );
}

#[test]
fn invalid_state_is_rejected() {
    assert!(decode("status @Unknown").is_err());
    assert!(decode("status @MergingWithConflict { source_branch 1.19.2 }").is_err());
}

#[test]
fn original_default_and_invalid_input_semantics_are_preserved() {
    for input in [
        "",
        "// no active propagation\n",
        "{}",
        "null",
        "status @Idle",
    ] {
        match facet_styx::from_str::<LegacyState>(input) {
            Ok(original) => {
                let current = decode(input).expect("read originally accepted state");
                assert_eq!(
                    encode(current).expect("encode accepted state"),
                    facet_styx::to_string(&original).expect("encode original state"),
                    "input {input:?}"
                );
            }
            Err(_) => assert!(decode(input).is_err(), "input {input:?}"),
        }
    }
}
