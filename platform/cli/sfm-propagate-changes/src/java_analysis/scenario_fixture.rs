//! Explicit, thread-scoped inputs for the CLI's integration scenarios.
//!
//! Production calls leave this unset and retain the branch-selected JDK and
//! platform cache. A scope cannot affect another test thread.

use super::DependencyJavaSymbolIndexBody;
use super::DependencyResolutionSelection;
use super::DependencySymbolIndexCounts;
use super::DependencySymbolIndexIdentity;
use super::scan_dependency_java_symbol_index;
use crate::paths::CacheHome;
use std::cell::RefCell;
use std::path::Path;
use std::path::PathBuf;

#[derive(Clone, Debug)]
pub struct JavaAnalysisScenarioFixture {
    pub jdk_source_tree: PathBuf,
    pub cache_home: CacheHome,
}

thread_local! {
    static CURRENT: RefCell<Option<JavaAnalysisScenarioFixture>> = const { RefCell::new(None) };
}

pub(crate) fn current_scenario_fixture() -> Option<JavaAnalysisScenarioFixture> {
    CURRENT.with(|current| current.borrow().clone())
}

/// Run one CLI scenario with explicit JDK source and cache inputs.
///
/// The previous value is restored even if `run` unwinds, and parallel test
/// threads keep independent contexts.
pub fn with_java_analysis_scenario_fixture<T>(
    fixture: JavaAnalysisScenarioFixture,
    run: impl FnOnce() -> T,
) -> T {
    struct Restore(Option<JavaAnalysisScenarioFixture>);
    impl Drop for Restore {
        fn drop(&mut self) {
            CURRENT.with(|current| *current.borrow_mut() = self.0.take());
        }
    }

    let previous = CURRENT.with(|current| current.borrow_mut().replace(fixture));
    let _restore = Restore(previous);
    run()
}

/// Decode every record in a scenario's authenticated dependency stream.
///
/// The direct at-position CLI query retains definitions but does not request
/// dependency usages. This check proves both fixture record kinds are valid.
///
/// # Errors
///
/// Returns an error if the index cannot be read or fails validation against
/// the supplied identity and counts.
pub fn scan_java_analysis_scenario_index(
    path: &Path,
    identity: &DependencySymbolIndexIdentity,
    counts: &DependencySymbolIndexCounts,
) -> eyre::Result<DependencyJavaSymbolIndexBody> {
    let scan = scan_dependency_java_symbol_index(
        path,
        identity,
        counts,
        DependencyResolutionSelection::None,
        |_, _, _, _, _| true,
        |_, _, _, _, _| true,
    )?;
    Ok(scan.body)
}
