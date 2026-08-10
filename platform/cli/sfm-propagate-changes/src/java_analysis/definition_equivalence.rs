use super::JavaSymbolIndex;
use super::JavaSymbolSelector;
use super::SymbolDefinitionOutput;

pub(crate) fn compare_definition_indexes(
    legacy: &JavaSymbolIndex,
    candidate: &JavaSymbolIndex,
    selectors: &[JavaSymbolSelector],
) -> eyre::Result<()> {
    let legacy_body = legacy.dependency_body(&[]);
    let candidate_body = candidate.dependency_body(&[]);
    if legacy_body.definitions != candidate_body.definitions {
        eyre::bail!(
            "definition-index equivalence mismatch in complete definitions: legacy={:#?}; candidate={:#?}",
            legacy_body.definitions,
            candidate_body.definitions
        );
    }
    if legacy_body.diagnostics != candidate_body.diagnostics {
        eyre::bail!(
            "definition-index equivalence mismatch in complete diagnostics: legacy={:#?}; candidate={:#?}",
            legacy_body.diagnostics,
            candidate_body.diagnostics
        );
    }
    for selector in selectors {
        compare_definition_reports(
            selector,
            &legacy.definition(selector),
            &candidate.definition(selector),
        )?;
    }
    Ok(())
}

fn compare_definition_reports(
    selector: &JavaSymbolSelector,
    legacy: &SymbolDefinitionOutput,
    candidate: &SymbolDefinitionOutput,
) -> eyre::Result<()> {
    if legacy.schema != candidate.schema {
        return mismatch(selector, "schema", &legacy.schema, &candidate.schema);
    }
    if legacy.outcome != candidate.outcome {
        return mismatch(selector, "outcome", &legacy.outcome, &candidate.outcome);
    }
    let mut legacy_context = legacy.context.clone();
    let mut candidate_context = candidate.context.clone();
    let legacy_fingerprint = std::mem::take(&mut legacy_context.index_fingerprint);
    let candidate_fingerprint = std::mem::take(&mut candidate_context.index_fingerprint);
    if legacy_context != candidate_context {
        return mismatch(selector, "context", &legacy_context, &candidate_context);
    }
    if legacy.selector != candidate.selector {
        return mismatch(selector, "selector", &legacy.selector, &candidate.selector);
    }
    if legacy.definitions != candidate.definitions {
        return mismatch(
            selector,
            "definitions",
            &legacy.definitions,
            &candidate.definitions,
        );
    }
    if legacy.diagnostics != candidate.diagnostics {
        return mismatch(
            selector,
            "diagnostics",
            &legacy.diagnostics,
            &candidate.diagnostics,
        );
    }
    if legacy.dependency_index != candidate.dependency_index {
        return mismatch(
            selector,
            "dependency-index",
            &legacy.dependency_index,
            &candidate.dependency_index,
        );
    }
    if legacy_fingerprint != candidate_fingerprint {
        return mismatch(
            selector,
            "index-fingerprint",
            &legacy_fingerprint,
            &candidate_fingerprint,
        );
    }
    Ok(())
}

fn mismatch<T: std::fmt::Debug>(
    selector: &JavaSymbolSelector,
    field: &str,
    legacy: &T,
    candidate: &T,
) -> eyre::Result<()> {
    eyre::bail!(
        "definition-index equivalence mismatch for `{}` at {field}: legacy={legacy:#?}; candidate={candidate:#?}",
        selector.canonical()
    )
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::java_analysis::JavaAnalysisContextOutput;
    use crate::java_analysis::JavaClasspathMode;
    use crate::java_analysis::JavaSymbolSelectorKind;
    use crate::java_analysis::JavaSymbolSelectorOutput;
    use crate::java_analysis::SymbolCommandOutcome;

    fn selector() -> JavaSymbolSelector {
        JavaSymbolSelector::parse_terms(&["p.A".to_owned()]).expect("selector should parse")
    }

    fn report() -> SymbolDefinitionOutput {
        SymbolDefinitionOutput {
            schema: "sfm.symbol-definition/2".to_owned(),
            outcome: SymbolCommandOutcome::NoMatch,
            context: JavaAnalysisContextOutput {
                branch: "1.19.2".to_owned(),
                minecraft_version: "1.19.2".to_owned(),
                java_release: "17".to_owned(),
                jdk: "fixture".to_owned(),
                source_roots: Vec::new(),
                source_sets: Vec::new(),
                source_exclusions: Vec::new(),
                classpath_mode: JavaClasspathMode::Isolated,
                classpath_fingerprint: "fixture".to_owned(),
                parser_fingerprint: "fixture".to_owned(),
                index_fingerprint: "fixture".to_owned(),
            },
            selector: JavaSymbolSelectorOutput {
                canonical: "p.A".to_owned(),
                owner: "p.A".to_owned(),
                member: None,
                descriptor: None,
                kind: JavaSymbolSelectorKind::Type,
            },
            definitions: Vec::new(),
            diagnostics: Vec::new(),
            dependency_index: None,
        }
    }

    #[test]
    fn equivalence_report_accepts_identical_typed_outputs() {
        let report = report();
        compare_definition_reports(&selector(), &report, &report)
            .expect("identical reports should compare equal");
    }

    #[test]
    fn equivalence_report_names_the_first_different_field() {
        let legacy = report();
        let mut candidate = report();
        candidate.context.index_fingerprint = "different".to_owned();
        let error = compare_definition_reports(&selector(), &legacy, &candidate)
            .expect_err("different reports should fail");
        assert!(error.to_string().contains("at index-fingerprint"));
    }
}
