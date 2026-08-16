use super::SymbolWorkspaceArgs;
use crate::cancellation::CancellationToken;
use crate::cli::output::CliOutput;
use crate::java_analysis::DefinitionAtPositionEngine;
use crate::java_analysis::DefinitionAtPositionEngineLimits;
use crate::java_analysis::SymbolServerIdentity;
use crate::java_analysis::SymbolServerLimits;
use crate::java_analysis::SymbolServerState;
use crate::java_analysis::SymbolServerWorkspaceOutput;
use crate::java_analysis::run_symbol_server_runtime;
use facet::Facet;
use std::io::Read;
use std::path::Path;
use std::sync::Arc;

#[derive(Facet, Debug)]
pub struct SymbolServeArgs {
    #[facet(flatten)]
    pub workspace: SymbolWorkspaceArgs,
}

impl SymbolServeArgs {
    /// Resolve one authoritative workspace and serve framed definition and
    /// usage-at-position requests until the client disconnects, shuts down, or
    /// the host is cancelled.
    ///
    /// # Errors
    ///
    /// Returns an error for workspace/dependency setup, invalid worker
    /// configuration, protocol I/O, or cache-generation failure.
    pub fn invoke_in(
        self,
        cancellation_token: &CancellationToken,
        invocation_dir: &Path,
    ) -> eyre::Result<CliOutput> {
        let branch = self.workspace.branch.clone();
        let workspace = self.workspace.resolve(invocation_dir)?;
        let (dependencies, dependency_index, dependency_source_roots) =
            super::load_definition_at_position_dependencies(
                &workspace,
                &branch,
                cancellation_token,
            )?;
        let served_workspace = SymbolServerWorkspaceOutput::from_workspace_with_dependency_sources(
            &workspace,
            dependency_index
                .as_ref()
                .map(|index| index.expected_identity.clone()),
            0,
            &dependency_source_roots,
        )?;
        let engine = Arc::new(
            DefinitionAtPositionEngine::new_with_dependency_source_roots(
                workspace,
                dependencies,
                dependency_index,
                dependency_source_roots,
                DefinitionAtPositionEngineLimits::default(),
            )?,
        );
        let limits = SymbolServerLimits::default();
        let mut state = SymbolServerState::new(
            SymbolServerIdentity {
                server_name: "sfm-propagate-changes".to_owned(),
                server_version: env!("CARGO_PKG_VERSION").to_owned(),
            },
            served_workspace,
            limits,
            cancellation_token.clone(),
        )?;

        tracing::info!(
            target: "sfm::symbol_server",
            source_roots = state.served_workspace().request_workspace.source_roots.len(),
            request_root_mappings = state.served_workspace().roots.len(),
            managed_source_roots = state.served_workspace().managed_source_roots.len(),
            maximum_frame_bytes = limits.max_frame_bytes,
            maximum_pending_definitions = limits.max_pending_definitions,
            "symbol server ready for handshake"
        );
        let stdout = std::io::stdout();
        let mut writer = stdout.lock();
        let summary = run_symbol_server_runtime(
            SymbolServerStdin,
            &mut writer,
            &mut state,
            &engine,
            cancellation_token,
            limits.max_frame_bytes,
        )?;
        tracing::info!(
            target: "sfm::symbol_server",
            completed_requests = summary.completed_requests,
            failed_requests = summary.failed_requests,
            final_cache_entries = summary.final_cache_entries,
            final_cache_bytes = summary.final_cache_bytes,
            "symbol server stopped"
        );
        Ok(CliOutput::none())
    }
}

struct SymbolServerStdin;

impl Read for SymbolServerStdin {
    fn read(&mut self, buf: &mut [u8]) -> std::io::Result<usize> {
        std::io::stdin().lock().read(buf)
    }
}
