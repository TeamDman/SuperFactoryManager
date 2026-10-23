use sha2::{Digest, Sha256};
use std::collections::BTreeSet;

fn main() {
    let loaded = rustls_native_certs::load_native_certs();
    assert!(loaded.errors.is_empty(), "native root loading errors: {:?}", loaded.errors);
    let roots: BTreeSet<String> = loaded.certs.iter()
        .map(|cert| format!("{:x}", Sha256::digest(cert.as_ref())))
        .collect();
    let denied = [
        "bc4d809b15189d78db3e1d8cf4f9726a795da1643ca5f1358e1ddb0edc0d7eb3",
        "86a1ecba089c4a8d3bbe2734c612ba341d813e043cf9e8a862cd5c57a36bbe6b",
        "125609aa301da0a249b97a8239cb6a34216f44dcac9f3954b14292f2e8c8608f",
        "40f6af0346a99aa1cd1d555a4e9cce62c7f9634603ee406615833dc8c8d00367",
    ];
    assert_eq!(roots.len(), 150);
    for fingerprint in denied { assert!(!roots.contains(fingerprint)); }
    println!("PASS: {} unique native roots; all four excluded roots absent; no loading errors", roots.len());
    for fingerprint in roots { println!("{fingerprint}"); }
}
