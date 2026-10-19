use super::*;
use crate::jar_build::nfrt_launch_contract::NfrtInputSnapshotExport;

fn actual_development_runtime() -> NfrtInputSnapshotExport {
    facet_json::from_str(include_str!(
        "../../tests/fixtures/neoform/development-runtime-snapshot.json"
    ))
    .unwrap()
}

#[test]
fn actual_schema4_runtime_retains_exact_pin_and_source_origin() {
    let development = actual_development_runtime();
    assert_eq!(development.origin, "captured_schema4_pin");
    assert_eq!(development.bytes, 4_479_682);
    // This exact row from the failed native invocation was rejected by
    // the old unconditional schema-2 comparison despite the exact hash.
    assert_ne!(development.origin, "original_schema2_pin");
    assert_eq!(
        unique_runtime_snapshot(std::slice::from_ref(&development), "captured_schema4_pin")
            .unwrap(),
        "inputs/artifacts/0000.bin"
    );
    let mut released = development.clone();
    released.origin = "original_schema2_pin".to_owned();
    assert!(
        unique_runtime_snapshot(std::slice::from_ref(&released), "original_schema2_pin").is_ok()
    );
    for (row, expected) in [
        (&development, "original_schema2_pin"),
        (&released, "captured_schema4_pin"),
        (&development, "unreviewed_source"),
    ] {
        assert!(unique_runtime_snapshot(std::slice::from_ref(row), expected).is_err());
    }
}

#[test]
fn runtime_source_refuses_missing_duplicate_changed_pin_and_other_version() {
    let original = actual_development_runtime();
    assert!(unique_runtime_snapshot(&[], "captured_schema4_pin").is_err());
    assert!(
        unique_runtime_snapshot(
            &[original.clone(), original.clone()],
            "captured_schema4_pin"
        )
        .is_err()
    );
    let mut wrong_hash = original.clone();
    wrong_hash.full_sha256 = format!("sha256:{}", "0".repeat(64));
    let mut wrong_origin = original.clone();
    wrong_origin.origin = "approved_nfrt_child".to_owned();
    let mut wrong_version = original;
    wrong_version.coordinate = Some("net.neoforged:neoform-runtime:2.0.20:all".to_owned());
    for row in [wrong_hash, wrong_origin, wrong_version] {
        assert!(unique_runtime_snapshot(&[row], "captured_schema4_pin").is_err());
    }
}
