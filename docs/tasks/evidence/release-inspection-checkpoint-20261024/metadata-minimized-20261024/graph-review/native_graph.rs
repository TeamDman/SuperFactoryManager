// Analytical harness only. Production parser modules are compiled unchanged.
#![allow(dead_code)]
include!("coordinate.rs");
#[path = "/workspace/SuperFactoryManager/platform/cli/sfm-propagate-changes/src/jar_build/maven_pom_model.rs"]
mod maven_pom_model;
#[path = "/workspace/SuperFactoryManager/platform/cli/sfm-propagate-changes/src/jar_build/maven_pom_dependency.rs"]
mod maven_pom_dependency;
#[path = "/workspace/SuperFactoryManager/platform/cli/sfm-propagate-changes/src/jar_build/xml_node.rs"]
mod xml_node;
use std::collections::{BTreeMap, BTreeSet, VecDeque};
use std::io::Read;
use serde_json::{Value, json};

fn main() -> eyre::Result<()> {
    let args: Vec<String> = std::env::args().collect();
    eyre::ensure!(args.len() == 3, "usage: native_graph INPUT.json OUTPUT.json");
    let input: Value = serde_json::from_slice(&std::fs::read(&args[1])?)?;
    let roots: Vec<String> = input["roots"].as_array().unwrap().iter()
        .map(|v| v.as_str().unwrap().to_owned()).collect();
    let locked: BTreeSet<String> = input["lockedCoordinates"].as_array().unwrap().iter()
        .map(|v| v.as_str().unwrap().to_owned()).collect();
    let expected: BTreeSet<String> = input["expectedCoordinates"].as_array().unwrap().iter()
        .map(|v| v.as_str().unwrap().to_owned()).collect();
    let sources = input["sources"].as_object().unwrap();
    let mut queue: VecDeque<String> = roots.iter().cloned().collect();
    let mut reached = BTreeSet::new();
    let mut reads = Vec::<Value>::new();
    let mut results = BTreeMap::<String, Value>::new();
    let mut edges = BTreeSet::<(String, String)>::new();
    let mut errors = BTreeMap::<String, String>::new();
    while let Some(coordinate) = queue.pop_front() {
        if !reached.insert(coordinate.clone()) { continue; }
        eyre::ensure!(reached.len() <= 128, "canonical test artifact graph budget exceeded");
        let root = MavenCoordinate::parse(&coordinate)?;
        let dependencies = maven_pom_model::runtime_dependencies(&root, |pom| {
            let key = pom.to_string();
            let Some(source) = sources.get(&key) else {
                reads.push(json!({"artifact": coordinate, "coordinate": key, "kind": "missing"}));
                eyre::bail!("Analytical reader has no metadata for {key}");
            };
            reads.push(json!({"artifact": coordinate, "coordinate": key, "kind": source["kind"],
                "path": source["path"], "sha256": source["sha256"]}));
            let mut text = String::new();
            std::fs::File::open(source["path"].as_str().unwrap())?
                .take(2 * 1024 * 1024 + 1).read_to_string(&mut text)?;
            eyre::ensure!(text.len() <= 2 * 1024 * 1024, "analytical input exceeds native metadata limit");
            Ok(text)
        });
        match dependencies {
            Ok(dependencies) => {
                let names: Vec<String> = dependencies.iter().map(ToString::to_string).collect();
                for name in &names {
                    edges.insert((coordinate.clone(), name.clone()));
                    queue.push_back(name.clone());
                }
                results.insert(coordinate.clone(), json!({"status":"parsed", "dependencies": names}));
            }
            Err(error) => {
                let message = format!("{error:#}");
                errors.insert(coordinate.clone(), message.clone());
                results.insert(coordinate.clone(), json!({"status":"rejected", "error": message}));
            }
        }
    }
    let mut versions = BTreeMap::<String, BTreeSet<String>>::new();
    for coordinate in &reached {
        let c = MavenCoordinate::parse(coordinate)?;
        versions.entry(format!("{}:{}:{}:{}", c.group, c.artifact, c.extension,
            c.classifier.as_deref().unwrap_or(""))).or_default().insert(c.version);
    }
    let conflicts: BTreeMap<_, _> = versions.into_iter().filter(|(_, v)| v.len() > 1).collect();
    let unique_reads: BTreeMap<String, Value> = reads.iter().map(|r|
        (r["coordinate"].as_str().unwrap().to_owned(), r.clone())).collect();
    let external: Vec<Value> = unique_reads.values()
        .filter(|r| r["kind"] == "staged-external-analysis-only").cloned().collect();
    let output = json!({
        "scope": "Analytical native-parser execution only; no metadata admission, artifact download, or JUnit execution",
        "roots": roots, "reachedCoordinates": reached, "results": results,
        "edges": edges.iter().map(|(a,b)| json!({"from":a,"to":b})).collect::<Vec<_>>(),
        "parserErrors": errors, "versionConflicts": conflicts,
        "unlockedCoordinates": reached.difference(&locked).collect::<Vec<_>>(),
        "outsideExpectedCoordinates": reached.difference(&expected).collect::<Vec<_>>(),
        "expectedNotReached": expected.difference(&reached).collect::<Vec<_>>(),
        "metadataReads": reads, "uniqueMetadataReads": unique_reads,
        "stagedExternalMetadataActuallyRead": external
    });
    std::fs::write(&args[2], serde_json::to_vec_pretty(&output)?)?;
    println!("roots={} artifacts={} edges={} parser_errors={} staged_external_reads={}",
        roots.len(), reached.len(), edges.len(), errors.len(), external.len());
    Ok(())
}
