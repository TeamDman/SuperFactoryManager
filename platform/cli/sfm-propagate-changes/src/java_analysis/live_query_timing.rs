use std::time::Duration;
use std::time::Instant;

/// Stable stage names for live Java symbol-query timing evidence.
///
/// These names are written through `tracing` to stderr. They are deliberately
/// independent from the public Facet output schemas so measurement cannot
/// perturb JSON/CSV/stdout contracts.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub(crate) enum LiveQueryStage {
    ResolveContext,
    AcquireLock,
    ProbeSources,
    ReadWorkspaceVocabulary,
    ScanDependencyIndex,
    BuildLiveIndex,
    ScanLiveIndex,
    MergeIndex,
}

impl LiveQueryStage {
    #[cfg(test)]
    pub(crate) const ALL: [Self; 8] = [
        Self::ResolveContext,
        Self::AcquireLock,
        Self::ProbeSources,
        Self::ReadWorkspaceVocabulary,
        Self::ScanDependencyIndex,
        Self::BuildLiveIndex,
        Self::ScanLiveIndex,
        Self::MergeIndex,
    ];

    pub(crate) const fn name(self) -> &'static str {
        match self {
            Self::ResolveContext => "resolve-context",
            Self::AcquireLock => "acquire-lock",
            Self::ProbeSources => "probe-sources",
            Self::ReadWorkspaceVocabulary => "read-workspace-vocabulary",
            Self::ScanDependencyIndex => "scan-dependency-index",
            Self::BuildLiveIndex => "build-live-index",
            Self::ScanLiveIndex => "scan-live-index",
            Self::MergeIndex => "merge-index",
        }
    }
}

pub(crate) struct LiveQueryTimings {
    started: Instant,
    stage_started: Instant,
    source_files: usize,
}

impl LiveQueryTimings {
    pub(crate) fn start(source_files: usize) -> Self {
        let started = Instant::now();
        Self {
            started,
            stage_started: started,
            source_files,
        }
    }

    pub(crate) fn finish(&mut self, stage: LiveQueryStage) {
        let now = Instant::now();
        emit_stage_timing(
            stage,
            now.saturating_duration_since(self.stage_started),
            now.saturating_duration_since(self.started),
            self.source_files,
        );
        self.stage_started = now;
    }
}

fn emit_stage_timing(
    stage: LiveQueryStage,
    stage_duration: Duration,
    elapsed: Duration,
    source_files: usize,
) {
    tracing::info!(
        target: "sfm::java_analysis",
        event = "SFM_JAVA_LIVE_QUERY_STAGE",
        stage = stage.name(),
        stage_ms = u64::try_from(stage_duration.as_millis()).unwrap_or(u64::MAX),
        elapsed_ms = u64::try_from(elapsed.as_millis()).unwrap_or(u64::MAX),
        source_files,
        "live Java symbol query stage completed"
    );
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn live_query_stage_names_are_stable_and_unique() {
        let names = LiveQueryStage::ALL.map(LiveQueryStage::name);
        assert_eq!(
            names,
            [
                "resolve-context",
                "acquire-lock",
                "probe-sources",
                "read-workspace-vocabulary",
                "scan-dependency-index",
                "build-live-index",
                "scan-live-index",
                "merge-index",
            ]
        );
        let unique = names.into_iter().collect::<std::collections::BTreeSet<_>>();
        assert_eq!(unique.len(), LiveQueryStage::ALL.len());
    }
}
