# Historical released-input fixtures

`released-2612-original-schema2.json` preserves the exact 26.1.2 schema-2 lock
bound by `docs/tasks/sfm-core-released-native-adapter-review.json`. Its source is
`platform/minecraft/core-liquid-template/build/lockfiles/26.1.2/schema-2.json`
at Git commit `e9755fa9be3b9ccb1adb0ada36fd9b13422e1f82`:

- Length: 50,153 bytes.
- SHA-256: `475d2e71d7d640d6520a62e109e647308f5fff309bc8d57b02b45ce0c1aac0de`.

Historical review tests use these unchanged bytes to exercise unresolved weak
identity and tamper rejection. They must not read the current production lock,
whose stronger producer-receipt identity is tested separately. This fixture
does not authorize production admission, dependency acquisition or a rebuild.
