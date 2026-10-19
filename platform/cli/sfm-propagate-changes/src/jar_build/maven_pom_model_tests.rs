use super::*;
fn coordinate(text: &str) -> eyre::Result<MavenCoordinate> {
    MavenCoordinate::parse(text)
}
fn resolve(root: &str, poms: &[(&str, &str)]) -> eyre::Result<Vec<String>> {
    let poms = poms
        .iter()
        .map(|(c, p)| ((*c).to_owned(), (*p).to_owned()))
        .collect::<BTreeMap<_, _>>();
    let resolved = runtime_dependencies(&coordinate(root)?, |c| {
        poms.get(&c.to_string())
            .cloned()
            .ok_or_else(|| eyre::eyre!("offline Maven metadata missing: {c}"))
    })?;
    Ok(resolved.into_iter().map(|c| c.to_string()).collect())
}
#[test]
fn authoritative_javaparser_parent_resolves_managed_versions() -> eyre::Result<()> {
    let result = resolve(
        "com.github.javaparser:javaparser-symbol-solver-core:3.26.4",
        &[
            (
                "com.github.javaparser:javaparser-symbol-solver-core:3.26.4@pom",
                include_str!(
                    "fixtures/maven/com/github/javaparser/javaparser-symbol-solver-core/3.26.4/javaparser-symbol-solver-core-3.26.4.pom"
                ),
            ),
            (
                "com.github.javaparser:javaparser-parent:3.26.4@pom",
                include_str!(
                    "fixtures/maven/com/github/javaparser/javaparser-parent/3.26.4/javaparser-parent-3.26.4.pom"
                ),
            ),
        ],
    )?;
    assert_eq!(
        result,
        vec![
            "com.github.javaparser:javaparser-core:3.26.4",
            "org.javassist:javassist:3.30.2-GA",
            "com.google.guava:guava:33.4.0-jre"
        ]
    );
    Ok(())
}
#[test]
fn authoritative_guava_management_resolves_six_runtime_edges() -> eyre::Result<()> {
    let result = resolve(
        "com.google.guava:guava:33.4.0-jre",
        &[
            (
                "com.google.guava:guava:33.4.0-jre@pom",
                include_str!(
                    "fixtures/maven/com/google/guava/guava/33.4.0-jre/guava-33.4.0-jre.pom"
                ),
            ),
            (
                "com.google.guava:guava-parent:33.4.0-jre@pom",
                include_str!(
                    "fixtures/maven/com/google/guava/guava-parent/33.4.0-jre/guava-parent-33.4.0-jre.pom"
                ),
            ),
        ],
    )?;
    assert_eq!(
        result,
        vec![
            "com.google.guava:failureaccess:1.0.2",
            "com.google.guava:listenablefuture:9999.0-empty-to-avoid-conflict-with-guava",
            "com.google.code.findbugs:jsr305:3.0.2",
            "org.checkerframework:checker-qual:3.43.0",
            "com.google.errorprone:error_prone_annotations:2.36.0",
            "com.google.j2objc:j2objc-annotations:3.0.0"
        ]
    );
    Ok(())
}
#[test]
fn inherited_management_is_retained_before_bom_imports() -> eyre::Result<()> {
    let result = resolve(
        "x:child:1",
        &[
            (
                "x:parent:1@pom",
                r"<project><groupId>x</groupId><artifactId>parent</artifactId><version>1</version><properties><v>1</v></properties><dependencyManagement><dependencies><dependency><groupId>lib</groupId><artifactId>a</artifactId><version>${v}</version></dependency></dependencies></dependencyManagement></project>",
            ),
            (
                "x:bom:1@pom",
                r"<project><groupId>x</groupId><artifactId>bom</artifactId><version>1</version><properties><v>3</v></properties><dependencyManagement><dependencies><dependency><groupId>lib</groupId><artifactId>a</artifactId><version>${v}</version></dependency></dependencies></dependencyManagement></project>",
            ),
            (
                "x:child:1@pom",
                r"<project><parent><groupId>x</groupId><artifactId>parent</artifactId><version>1</version></parent><artifactId>child</artifactId><properties><v>2</v></properties><dependencyManagement><dependencies><dependency><groupId>x</groupId><artifactId>bom</artifactId><version>1</version><type>pom</type><scope>import</scope></dependency></dependencies></dependencyManagement><dependencies><dependency><groupId>lib</groupId><artifactId>a</artifactId></dependency><dependency><groupId>lib</groupId><artifactId>unused</artifactId><version>${unknown}</version><scope>test</scope></dependency></dependencies></project>",
            ),
        ],
    )?;
    assert_eq!(result, vec!["lib:a:2"]);
    Ok(())
}
#[test]
fn missing_parent_is_an_exact_error_not_empty_closure() -> eyre::Result<()> {
    let error=resolve("x:child:1",&[("x:child:1@pom",r"<project><parent><groupId>x</groupId><artifactId>parent</artifactId><version>1</version></parent><artifactId>child</artifactId></project>")]).expect_err("missing parent must fail");
    assert!(error.to_string().contains("x:parent:1@pom"));
    Ok(())
}
#[test]
fn unsafe_coordinates_cyclic_properties_dtd_and_missing_versions_fail() -> eyre::Result<()> {
    assert!(validate_coordinate(&coordinate("x:../escape:1")?).is_err());
    assert!(expand("${a}", &BTreeMap::from([("a".into(), "${a}".into())])).is_err());
    assert!(
        XmlNode::parse(
            r"<!DOCTYPE project [<!ENTITY leak SYSTEM 'file:///secret'>]><project>&leak;</project>"
        )
        .is_err()
    );
    assert!(resolve("x:a:1",&[("x:a:1@pom",r"<project><groupId>x</groupId><artifactId>a</artifactId><version>1</version><dependencies><dependency><groupId>x</groupId><artifactId>b</artifactId></dependency></dependencies></project>")]).is_err());
    Ok(())
}

#[test]
fn maven_399_importer_source_comparison_retains_parent_and_child_then_first_import()
-> eyre::Result<()> {
    let fixture: BTreeMap<String, String> = facet_json::from_str(include_str!(
        "fixtures/maven/maven-3.9.9-management-comparison.json"
    ))?;
    let result = runtime_dependencies(&coordinate("x:child:1")?, |c| {
        fixture
            .get(&c.to_string())
            .cloned()
            .ok_or_else(|| eyre::eyre!("missing fixture {c}"))
    })?;
    assert_eq!(
        result
            .into_iter()
            .map(|c| c.to_string())
            .collect::<Vec<_>>(),
        vec!["lib:a:1", "lib:b:3", "lib:c:2", "lib:d:4"]
    );
    Ok(())
}
#[test]
fn diamond_bom_dag_reads_each_unique_model_once() -> eyre::Result<()> {
    let levels = 22;
    let mut poms = BTreeMap::new();
    for level in 0..levels {
        for side in ['a', 'b'] {
            let imports = if level + 1 == levels {
                vec!["leaf".to_owned()]
            } else {
                vec![format!("{}a", level + 1), format!("{}b", level + 1)]
            };
            let management = imports.into_iter().map(|id| format!("<dependency><groupId>x</groupId><artifactId>{id}</artifactId><version>1</version><type>pom</type><scope>import</scope></dependency>")).collect::<String>();
            let dependencies = if level == 0 && side == 'a' {
                "<dependencies><dependency><groupId>lib</groupId><artifactId>a</artifactId></dependency></dependencies>"
            } else {
                ""
            };
            poms.insert(format!("x:{level}{side}:1@pom"), format!("<project><groupId>x</groupId><artifactId>{level}{side}</artifactId><version>1</version><dependencyManagement><dependencies>{management}</dependencies></dependencyManagement>{dependencies}</project>"));
        }
    }
    poms.insert("x:leaf:1@pom".into(), "<project><groupId>x</groupId><artifactId>leaf</artifactId><version>1</version><dependencyManagement><dependencies><dependency><groupId>lib</groupId><artifactId>a</artifactId><version>7</version></dependency></dependencies></dependencyManagement></project>".into());
    let mut reads = BTreeMap::<String, usize>::new();
    let result = runtime_dependencies(&coordinate("x:0a:1")?, |c| {
        *reads.entry(c.to_string()).or_default() += 1;
        poms.get(&c.to_string())
            .cloned()
            .ok_or_else(|| eyre::eyre!("missing DAG node {c}"))
    })?;
    assert_eq!(result[0].to_string(), "lib:a:7");
    assert_eq!(reads.len(), 44);
    assert!(reads.values().all(|count| *count == 1));
    Ok(())
}
#[test]
fn cached_imports_still_consume_global_visit_budget() -> eyre::Result<()> {
    let import = "<dependency><groupId>x</groupId><artifactId>leaf</artifactId><version>1</version><type>pom</type><scope>import</scope></dependency>";
    let root = format!(
        "<project><groupId>x</groupId><artifactId>root</artifactId><version>1</version><dependencyManagement><dependencies>{}</dependencies></dependencyManagement></project>",
        import.repeat(1024)
    );
    let mut reads = 0;
    let error = runtime_dependencies(&coordinate("x:root:1")?, |c| { reads += 1; Ok(if c.artifact == "root" { root.clone() } else { "<project><groupId>x</groupId><artifactId>leaf</artifactId><version>1</version></project>".into() }) }).expect_err("repeated cached imports remain bounded");
    assert!(error.to_string().contains("total visit budget"));
    assert_eq!(reads, 2);
    Ok(())
}
#[test]
fn aggregate_pom_bytes_are_bounded_before_parsing() -> eyre::Result<()> {
    let mut state = MavenPomResolution {
        bytes: 16 * 1024 * 1024 - 1,
        ..MavenPomResolution::default()
    };
    let error = load(&coordinate("x:a:1")?, &mut state, &mut |_| {
        Ok(
            "<project><groupId>x</groupId><artifactId>a</artifactId><version>1</version></project>"
                .into(),
        )
    })
    .expect_err("aggregate byte budget");
    assert!(error.to_string().contains("total byte budget"));
    assert!(state.cache.is_empty());
    Ok(())
}

#[test]
fn repeated_scalar_properties_follow_maven_last_value_wins() -> eyre::Result<()> {
    let pom = r"<project><groupId>x</groupId><artifactId>root</artifactId><version>1</version><properties><v>1</v><v>2</v><empty/><identical>100</identical><identical>100</identical></properties><dependencies><dependency><groupId>lib</groupId><artifactId>a</artifactId><version>${v}</version></dependency></dependencies></project>";
    assert_eq!(
        resolve("x:root:1", &[("x:root:1@pom", pom)])?,
        vec!["lib:a:2"]
    );
    Ok(())
}

#[test]
fn nested_property_is_rejected_with_coordinate_and_key() {
    let pom = r"<project><groupId>x</groupId><artifactId>root</artifactId><version>1</version><properties><v><nested>2</nested></v></properties></project>";
    let error = resolve("x:root:1", &[("x:root:1@pom", pom)])
        .unwrap_err()
        .to_string();
    assert!(error.contains("non-scalar POM property v at x:root:1"));
}
