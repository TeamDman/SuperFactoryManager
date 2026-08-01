use crate::branch_targets::select_single_worktree_target;
use crate::cancellation::CancellationToken;
use crate::cli::jar::BranchSelector;
use crate::toolchain_lockfile_schema::MigrationAnalysis;
use crate::toolchain_lockfile_schema::analyze_migration;
use crate::toolchain_lockfile_write::write_lockfile_atomically;
use eyre::Context;
use facet::Facet;
use figue as args;

#[derive(Facet, Debug)]
pub struct DependencyMigrateArgs {
    /// Branch selector to migrate. Must match exactly one worktree.
    #[facet(args::named)]
    pub branch: BranchSelector,

    /// Validate and preview migration requirements without writing.
    #[facet(default = false, args::named)]
    pub check: bool,
}

impl DependencyMigrateArgs {
    /// # Errors
    ///
    /// Returns an error when branch resolution, lockfile parsing, or migration validation fails.
    pub fn invoke(self, _cancellation_token: CancellationToken) -> eyre::Result<()> {
        let check = self.check;
        let query = self.branch.into_query()?;
        let target = select_single_worktree_target(&query)?;
        let lockfile_path = target
            .worktree_path
            .join("platform")
            .join("minecraft")
            .join("sfm-toolchain.lock.json");
        let input = std::fs::read_to_string(&lockfile_path)
            .wrap_err_with(|| format!("Failed to read {}", lockfile_path.display()))?;

        match analyze_migration(&input)? {
            MigrationAnalysis::LegacyV3 { candidate } => {
                let output = candidate
                    .to_canonical_json()
                    .wrap_err("Failed to serialize migrated schema v4 lockfile")?;
                println!(
                    "Migration check for {} (schema v3 -> v4):",
                    lockfile_path.display()
                );
                if check {
                    println!("  ready: schema v3 can be upgraded to the profile-aware v4 format");
                    println!("\n{output}");
                } else {
                    write_lockfile_atomically(&lockfile_path, &input, output.as_bytes())?;
                    println!("Migrated {} from schema v3 to v4.", lockfile_path.display());
                }
                Ok(())
            }
            MigrationAnalysis::CurrentV4(lockfile) => {
                let output = lockfile
                    .to_canonical_json()
                    .wrap_err("Failed to serialize canonical schema v4 lockfile")?;
                if output == input {
                    println!(
                        "{} already uses canonical schema version 4.",
                        lockfile_path.display()
                    );
                } else if check {
                    println!(
                        "{} uses schema version 4 but is not canonically ordered.",
                        lockfile_path.display()
                    );
                } else {
                    write_lockfile_atomically(&lockfile_path, &input, output.as_bytes())?;
                    println!("Canonicalized {}.", lockfile_path.display());
                }
                Ok(())
            }
            MigrationAnalysis::Legacy {
                source_schema_version,
                diagnostics,
                candidate,
            } => {
                println!(
                    "Migration check for {} (schema v{source_schema_version} -> v3):",
                    lockfile_path.display()
                );
                if diagnostics.is_empty() {
                    let Some(candidate) = candidate else {
                        eyre::bail!(
                            "migration analysis returned no diagnostics and no v3 candidate"
                        );
                    };
                    let output = candidate
                        .to_canonical_json()
                        .wrap_err("Failed to serialize migrated schema v3 lockfile")?;
                    if check {
                        println!("  ready: all required v3 migration evidence is present");
                        println!("\n{output}");
                    } else {
                        write_lockfile_atomically(&lockfile_path, &input, output.as_bytes())?;
                        println!(
                            "Migrated {} from schema v{source_schema_version} to v3.",
                            lockfile_path.display()
                        );
                    }
                    return Ok(());
                }
                for diagnostic in &diagnostics {
                    println!("\n  {}: {}", diagnostic.path, diagnostic.message);
                    if let Some(index) = diagnostic.legacy_dependency_index {
                        println!("    legacy dependency index: {index}");
                    }
                    if let Some(configuration) = &diagnostic.configuration {
                        println!("    configuration: {configuration}");
                    }
                    if let Some(coordinate) = &diagnostic.coordinate {
                        println!("    coordinate: {coordinate}");
                    }
                    if !diagnostic.candidates.is_empty() {
                        println!("    candidates: {}", diagnostic.candidates.join(", "));
                    }
                    println!("    action: {}", diagnostic.remediation);
                }
                eyre::bail!(
                    "migration requires {} correction(s); the lockfile was not modified",
                    diagnostics.len()
                )
            }
        }
    }
}
