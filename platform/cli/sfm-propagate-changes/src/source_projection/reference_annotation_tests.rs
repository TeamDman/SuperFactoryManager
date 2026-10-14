use super::context::ProjectionContext;
use super::core_inputs::SelectedCoreInput;
use super::core_inputs::attach_reference_template;
use super::core_inputs::render_core_input;
use super::reference_annotations::associations;
use std::collections::BTreeMap;
fn context(enabled: bool) -> ProjectionContext {
    ProjectionContext {
        minecraft_version: "1.21.1".into(),
        preset: String::new(),
        environment: "release".into(),
        projection_key: "fixture/mc-1.21.1".into(),
        features: BTreeMap::from([("change".into(), enabled)]),
        targets: BTreeMap::new(),
    }
}
#[test]
fn executed_branches_deduplicate_and_preserve_exact_java_bytes() {
    let source = "before\r\n{% if features.change %}\r\n{% issue 610 %}\r\n{% issue 610 %}\r\nafter\r\n{% else %}\r\n{% issue 527 %}\r\nold\r\n{% endif %}\r\n";
    let plain = source
        .lines()
        .filter(|line| !line.contains("{% issue "))
        .collect::<Vec<_>>()
        .join("\r\n")
        + "\r\n";
    for (enabled, expected) in [(true, vec![610]), (false, vec![527])] {
        let c = context(enabled);
        assert_eq!(
            super::render_java_source(source, &c).unwrap(),
            super::render_java_source(&plain, &c).unwrap()
        );
        let a = associations(source, "src/Foo.java", source.as_bytes(), &c).unwrap();
        assert_eq!(
            a.references
                .iter()
                .map(|reference| reference.number)
                .collect::<Vec<_>>(),
            expected
        );
        a.validate().unwrap();
        assert_eq!(
            a,
            associations(source, "src/Foo.java", source.as_bytes(), &c).unwrap()
        );
    }
}
#[test]
fn case_elsif_inactive_and_ordinary_comments_do_not_leak() {
    let source = "// issue 999 is just raw evidence\n{% case minecraft_version %}\n{% when \"1.20\" %}\n{% issue 621 %}\n{% else %}\n{% if features.change %}\n{% issue 610 %}\n{% elsif features.change %}\n{% issue 527 %}\n{% endif %}\n{% endcase %}\n";
    assert_eq!(
        associations(source, "src/Foo.java", source.as_bytes(), &context(true))
            .unwrap()
            .references
            .iter()
            .map(|reference| reference.number)
            .collect::<Vec<_>>(),
        vec![610]
    );
    assert!(
        associations(source, "src/Foo.java", source.as_bytes(), &context(false))
            .unwrap()
            .references
            .iter()
            .map(|reference| reference.number)
            .collect::<Vec<_>>()
            .is_empty()
    );
}
#[test]
fn literal_issue_requires_positive_bounded_integer_and_valid_context() {
    for source in [
        "{% issue 0 %}\n",
        "{% issue -1 %}\n",
        "{% issue 610 621 %}\n",
        "{% issue 4294967296 %}\n",
        "{% if features.typo %}\n{% issue 610 %}\n{% endif %}\n",
    ] {
        assert!(
            associations(source, "src/Foo.java", source.as_bytes(), &context(false)).is_err(),
            "{source}"
        );
    }
}
#[test]
fn json_path_companion_is_zero_output_and_context_specific() {
    let temp = tempfile::tempdir().unwrap();
    let raw=b"{% if features.change %}\n{% issue 621 %}\n{% issue 527 %}\n{% issue 527 %}\n{% endif %}\n";
    std::fs::write(temp.path().join("issues.liquid"), raw).unwrap();
    let input = SelectedCoreInput {
        input: "raw.json".into(),
        template: false,
        reference_template: Some("issues.liquid".into()),
    };
    for (enabled, expected) in [(true, vec![527, 621]), (false, vec![])] {
        let c = context(enabled);
        let bytes = b"{\"type\":\"sfm:disk_reset\"}\r\n".to_vec();
        let mut artifact =
            render_core_input("src/data/sfm/disk_reset.json", &input, bytes.clone(), &c).unwrap();
        attach_reference_template(temp.path(), &input, &c, &mut artifact).unwrap();
        assert_eq!(artifact.output_bytes, bytes);
        assert_eq!(
            artifact
                .related_references
                .unwrap()
                .references
                .iter()
                .map(|reference| reference.number)
                .collect::<Vec<_>>(),
            expected
        );
    }
    std::fs::write(
        temp.path().join("issues.liquid"),
        b"not-zero-output\n{% issue 621 %}\n",
    )
    .unwrap();
    let mut artifact = render_core_input(
        "src/data/sfm/disk_reset.json",
        &input,
        b"{}".to_vec(),
        &context(true),
    )
    .unwrap();
    assert!(attach_reference_template(temp.path(), &input, &context(true), &mut artifact).is_err());
}
#[test]
fn real_energy_and_recipe_markers_preserve_both_feature_outputs() {
    for (source, flag, expected) in [
        (
            include_str!(
                "../../../../minecraft/core-liquid-template/src/main/java/ca/teamdman/sfml/ast/OutputStatement.java"
            ),
            "quantity_sensitive_extraction_progress",
            vec![610],
        ),
        (
            include_str!(
                "../../../../minecraft/core-liquid-template/src/datagen/java/ca/teamdman/sfm/datagen/SFMRecipesDatagen.java"
            ),
            "recipe_namespace",
            vec![527, 621],
        ),
    ] {
        let plain = source
            .split_inclusive('\n')
            .filter(|line| !line.trim().starts_with("{% issue "))
            .collect::<String>();
        let super::directive_scanner::ScannedSource::Template(scanned) =
            super::directive_scanner::scan(source).unwrap()
        else {
            panic!("controlled template")
        };
        for enabled in [false, true] {
            let mut c = context(enabled);
            c.features.clear();
            for key in scanned.referenced_conditions.keys() {
                let (root, name) = key.split_once('.').unwrap();
                let map = if root == "features" {
                    &mut c.features
                } else {
                    &mut c.targets
                };
                map.insert(name.into(), name == flag && enabled);
            }
            assert_eq!(
                super::render_java_source(source, &c).unwrap(),
                super::render_java_source(&plain, &c).unwrap()
            );
            assert_eq!(
                associations(source, "src/Fixture.java", source.as_bytes(), &c)
                    .unwrap()
                    .references
                    .iter()
                    .map(|reference| reference.number)
                    .collect::<Vec<_>>(),
                if enabled { expected.clone() } else { vec![] }
            );
        }
    }
}

#[test]
fn typed_reference_namespaces_scopes_serialization_and_links() {
    use super::source_reference::SourceReference;
    use super::source_reference_kind::SourceReferenceKind;
    let source = "{% issue 42 %}\n{% pr 42 %}\n{% discussion 42 %}\n{% issue \"Other/Repository\" 7 %}\n{% issue \"other/repository\" 7 %}\n";
    let associations = associations(
        source,
        "src/Fixture.java",
        source.as_bytes(),
        &context(true),
    )
    .unwrap();
    assert_eq!(associations.references.len(), 4);
    associations.validate().unwrap();
    assert_eq!(
        super::render_java_source(source, &context(true)).unwrap(),
        ""
    );
    let json = facet_json::to_string(&associations).unwrap();
    let decoded: super::reference_associations::ReferenceAssociations =
        facet_json::from_str(&json).unwrap();
    assert_eq!(associations, decoded);
    assert!(json.contains("pull_request") && json.contains("discussion"));
    for (kind, route) in [
        (SourceReferenceKind::Issue, "issues"),
        (SourceReferenceKind::PullRequest, "pull"),
        (SourceReferenceKind::Discussion, "discussions"),
    ] {
        assert_eq!(
            SourceReference::sfm(kind, 42).url(),
            format!("https://github.com/TeamDman/SuperFactoryManager/{route}/42")
        );
    }
}
#[test]
fn references_reject_unsafe_scopes_invalid_kinds_and_duplicate_metadata() {
    for source in [
        "{% pr 0 %}\n",
        "{% discussion -1 %}\n",
        "{% issue \"owner/../repo\" 1 %}\n",
        "{% pr \"/repo\" 1 %}\n",
        "{% discussion \"owner/repo?query\" 1 %}\n",
        "{% reference 1 %}\n",
    ] {
        associations(
            source,
            "src/Fixture.java",
            source.as_bytes(),
            &context(true),
        )
        .unwrap_err();
    }
    let source = "{% issue 610 %}\n";
    let mut metadata = associations(
        source,
        "src/Fixture.java",
        source.as_bytes(),
        &context(true),
    )
    .unwrap();
    metadata.references.push(metadata.references[0].clone());
    metadata.validate().unwrap_err();
    metadata.references.pop();
    metadata.context_sha256 = "unsafe".into();
    metadata.validate().unwrap_err();
    facet_json::from_str::<super::source_reference_kind::SourceReferenceKind>("\"commit\"")
        .unwrap_err();
}

#[test]
fn companion_snapshot_retains_invocation_bytes_and_resolved_guard_evidence() {
    use super::core_inputs::CoreInputSnapshot;
    use super::core_inputs::attach_reference_template_with_snapshot;
    let temp = tempfile::tempdir().unwrap();
    let core = temp.path().join("platform/minecraft/core-liquid-template");
    std::fs::create_dir_all(core.join("src")).unwrap();
    let path = "refs.liquid";
    std::fs::write(
        core.join(path),
        "{% if features.change %}\n{% issue 610 %}\n{% endif %}\n",
    )
    .unwrap();
    let input = SelectedCoreInput {
        input: "raw.json".into(),
        template: false,
        reference_template: Some(path.into()),
    };
    let mut snapshot = CoreInputSnapshot::default();
    let mut first = render_core_input(
        "src/data/sfm/disk_reset.json",
        &input,
        b"{}".to_vec(),
        &context(true),
    )
    .unwrap();
    attach_reference_template_with_snapshot(
        &core,
        &input,
        &context(true),
        &mut first,
        Some(&mut snapshot),
    )
    .unwrap();
    let expected = first.related_references.unwrap();
    assert_eq!(expected.conditions["features.change"], true);
    std::fs::write(core.join(path), "{% issue 621 %}\n").unwrap();
    let mut reused = render_core_input(
        "src/data/sfm/disk_reset.json",
        &input,
        b"{}".to_vec(),
        &context(true),
    )
    .unwrap();
    attach_reference_template_with_snapshot(
        &core,
        &input,
        &context(true),
        &mut reused,
        Some(&mut snapshot),
    )
    .unwrap();
    assert_eq!(reused.related_references.unwrap(), expected);
    let mut fresh = render_core_input(
        "src/data/sfm/disk_reset.json",
        &input,
        b"{}".to_vec(),
        &context(true),
    )
    .unwrap();
    attach_reference_template(&core, &input, &context(true), &mut fresh).unwrap();
    assert_eq!(fresh.related_references.unwrap().references[0].number, 621);
}
