use std::collections::BTreeMap;
use std::collections::BTreeSet;
use std::error::Error;
use std::fmt;

pub(crate) const LIVE_WORKER_CONCURRENCY: usize = 4;
#[cfg(test)]
pub(crate) const LIVE_WORKER_MAX_SOURCES_PER_SHARD: usize = 32;

/// A stable half-open range of source ordinals.
#[derive(Clone, Copy, Debug, Eq, Ord, PartialEq, PartialOrd)]
pub(crate) struct SourceOrdinalRange {
    start: usize,
    end: usize,
}

impl SourceOrdinalRange {
    fn new(start: usize, end: usize) -> Result<Self, LiveWorkerSchedulerError> {
        if start >= end {
            return Err(LiveWorkerSchedulerError::InvalidRange { start, end });
        }
        Ok(Self { start, end })
    }

    pub(crate) const fn start(self) -> usize {
        self.start
    }

    pub(crate) const fn end(self) -> usize {
        self.end
    }

    pub(crate) const fn len(self) -> usize {
        self.end - self.start
    }

    pub(crate) const fn is_single_source(self) -> bool {
        self.len() == 1
    }

    fn bisect(self) -> Result<(Self, Self), LiveWorkerSchedulerError> {
        if self.is_single_source() {
            return Err(LiveWorkerSchedulerError::CannotBisectSingleSource { range: self });
        }
        let midpoint = self.start + self.len() / 2;
        Ok((
            Self::new(self.start, midpoint)?,
            Self::new(midpoint, self.end)?,
        ))
    }
}

#[derive(Clone, Copy, Debug, Eq, Ord, PartialEq, PartialOrd)]
enum BisectionDirection {
    Left,
    Right,
}

/// A shard identity that remains stable regardless of worker completion order.
///
/// Initial shards are identified by their root range. Children retain that root
/// and append their deterministic midpoint-bisection lineage.
#[derive(Clone, Debug, Eq, Ord, PartialEq, PartialOrd)]
pub(crate) struct StableShardKey {
    root: SourceOrdinalRange,
    lineage: Vec<BisectionDirection>,
}

impl StableShardKey {
    fn root(range: SourceOrdinalRange) -> Self {
        Self {
            root: range,
            lineage: Vec::new(),
        }
    }

    fn child(&self, direction: BisectionDirection) -> Self {
        let mut lineage = self.lineage.clone();
        lineage.push(direction);
        Self {
            root: self.root,
            lineage,
        }
    }
}

impl fmt::Display for StableShardKey {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "{}..{}", self.root.start, self.root.end)?;
        if !self.lineage.is_empty() {
            f.write_str(":")?;
            for direction in &self.lineage {
                f.write_str(match direction {
                    BisectionDirection::Left => "L",
                    BisectionDirection::Right => "R",
                })?;
            }
        }
        Ok(())
    }
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub(crate) struct ScheduledShard {
    key: StableShardKey,
    range: SourceOrdinalRange,
}

impl ScheduledShard {
    pub(crate) const fn key(&self) -> &StableShardKey {
        &self.key
    }

    pub(crate) const fn range(&self) -> SourceOrdinalRange {
        self.range
    }
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub(crate) enum FailedShardDisposition {
    Bisected {
        parent: ScheduledShard,
        left: ScheduledShard,
        right: ScheduledShard,
    },
    Terminal {
        shard: ScheduledShard,
    },
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub(crate) enum LiveWorkerSchedulerError {
    InvalidWorkerLimit,
    InvalidMaximumShardSize,
    InvalidRange {
        start: usize,
        end: usize,
    },
    CannotBisectSingleSource {
        range: SourceOrdinalRange,
    },
    AtCapacity {
        worker_limit: usize,
    },
    UnknownCompletion {
        key: StableShardKey,
    },
    DuplicateCompletion {
        key: StableShardKey,
    },
    TerminalFailureActive {
        key: StableShardKey,
    },
    DuplicateShardKey {
        key: StableShardKey,
    },
    NotReadyToSeal {
        pending: usize,
        running: usize,
    },
    PartitionGap {
        expected_start: usize,
        actual_start: usize,
    },
    PartitionOverlap {
        expected_start: usize,
        actual_start: usize,
    },
    PartitionOutOfBounds {
        source_count: usize,
        range: SourceOrdinalRange,
    },
    PartitionEndsEarly {
        expected_end: usize,
        actual_end: usize,
    },
}

impl fmt::Display for LiveWorkerSchedulerError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Self::InvalidWorkerLimit => f.write_str("worker limit must be greater than zero"),
            Self::InvalidMaximumShardSize => {
                f.write_str("maximum shard size must be greater than zero")
            }
            Self::InvalidRange { start, end } => {
                write!(f, "invalid source ordinal range {start}..{end}")
            }
            Self::CannotBisectSingleSource { range } => write!(
                f,
                "cannot bisect single-source range {}..{}",
                range.start, range.end
            ),
            Self::AtCapacity { worker_limit } => {
                write!(f, "worker scheduler is at its {worker_limit}-worker limit")
            }
            Self::UnknownCompletion { key } => {
                write!(f, "completion refers to unknown shard {key}")
            }
            Self::DuplicateCompletion { key } => {
                write!(f, "completion was already recorded for shard {key}")
            }
            Self::TerminalFailureActive { key } => write!(
                f,
                "single-source shard {key} failed; no more work may be dispatched"
            ),
            Self::DuplicateShardKey { key } => {
                write!(f, "scheduler generated duplicate shard key {key}")
            }
            Self::NotReadyToSeal { pending, running } => write!(
                f,
                "cannot seal scheduler with {pending} pending and {running} running shards"
            ),
            Self::PartitionGap {
                expected_start,
                actual_start,
            } => write!(
                f,
                "successful leaves have a gap at {expected_start}; next range starts at {actual_start}"
            ),
            Self::PartitionOverlap {
                expected_start,
                actual_start,
            } => write!(
                f,
                "successful leaves overlap before {expected_start}; next range starts at {actual_start}"
            ),
            Self::PartitionOutOfBounds {
                source_count,
                range,
            } => write!(
                f,
                "successful range {}..{} exceeds source count {source_count}",
                range.start, range.end
            ),
            Self::PartitionEndsEarly {
                expected_end,
                actual_end,
            } => write!(
                f,
                "successful leaves end at {actual_end}, expected {expected_end}"
            ),
        }
    }
}

impl Error for LiveWorkerSchedulerError {}

/// Pure scheduling state for a bounded synchronous worker supervisor.
///
/// A caller dispatches all currently available work, starts one process for
/// each returned shard, and reports each process completion. Successful leaves
/// can be consumed immediately by a linker while this scheduler keeps the
/// partition and lifecycle bookkeeping deterministic.
#[derive(Debug)]
pub(crate) struct LiveWorkerScheduler {
    source_count: usize,
    worker_limit: usize,
    pending: BTreeMap<StableShardKey, SourceOrdinalRange>,
    running: BTreeMap<StableShardKey, SourceOrdinalRange>,
    successful: BTreeMap<StableShardKey, SourceOrdinalRange>,
    completed: BTreeSet<StableShardKey>,
    terminal_failure: Option<ScheduledShard>,
    peak_active_workers: usize,
}

impl LiveWorkerScheduler {
    #[cfg(test)]
    pub(crate) fn four_worker(source_count: usize) -> Self {
        Self::new(
            source_count,
            LIVE_WORKER_CONCURRENCY,
            LIVE_WORKER_MAX_SOURCES_PER_SHARD,
        )
        .expect("fixed live-worker scheduler limits should be valid")
    }

    pub(crate) fn new(
        source_count: usize,
        worker_limit: usize,
        maximum_shard_size: usize,
    ) -> Result<Self, LiveWorkerSchedulerError> {
        if worker_limit == 0 {
            return Err(LiveWorkerSchedulerError::InvalidWorkerLimit);
        }
        if maximum_shard_size == 0 {
            return Err(LiveWorkerSchedulerError::InvalidMaximumShardSize);
        }

        let mut pending = BTreeMap::new();
        let mut start = 0;
        while start < source_count {
            let end = start.saturating_add(maximum_shard_size).min(source_count);
            let range = SourceOrdinalRange::new(start, end)?;
            let key = StableShardKey::root(range);
            if pending.insert(key.clone(), range).is_some() {
                return Err(LiveWorkerSchedulerError::DuplicateShardKey { key });
            }
            start = end;
        }

        Ok(Self {
            source_count,
            worker_limit,
            pending,
            running: BTreeMap::new(),
            successful: BTreeMap::new(),
            completed: BTreeSet::new(),
            terminal_failure: None,
            peak_active_workers: 0,
        })
    }

    #[cfg(test)]
    pub(crate) const fn worker_limit(&self) -> usize {
        self.worker_limit
    }

    pub(crate) const fn peak_active_workers(&self) -> usize {
        self.peak_active_workers
    }

    pub(crate) fn pending_count(&self) -> usize {
        self.pending.len()
    }

    pub(crate) fn running_count(&self) -> usize {
        self.running.len()
    }

    #[cfg(test)]
    pub(crate) const fn terminal_failure(&self) -> Option<&ScheduledShard> {
        self.terminal_failure.as_ref()
    }

    pub(crate) fn dispatch_one(
        &mut self,
    ) -> Result<Option<ScheduledShard>, LiveWorkerSchedulerError> {
        self.reject_if_terminal()?;
        if self.running.len() >= self.worker_limit {
            return Err(LiveWorkerSchedulerError::AtCapacity {
                worker_limit: self.worker_limit,
            });
        }
        let Some(key) = self.pending.keys().next().cloned() else {
            return Ok(None);
        };
        let range = self
            .pending
            .remove(&key)
            .expect("selected pending shard should still exist");
        if self.running.insert(key.clone(), range).is_some() {
            return Err(LiveWorkerSchedulerError::DuplicateShardKey { key });
        }
        self.peak_active_workers = self.peak_active_workers.max(self.running.len());
        Ok(Some(ScheduledShard { key, range }))
    }

    /// Dispatch enough pending shards to occupy every available worker slot.
    pub(crate) fn dispatch_ready(
        &mut self,
    ) -> Result<Vec<ScheduledShard>, LiveWorkerSchedulerError> {
        self.reject_if_terminal()?;
        let available = self.worker_limit - self.running.len();
        let dispatch_count = available.min(self.pending.len());
        let mut dispatched = Vec::with_capacity(dispatch_count);
        for _ in 0..dispatch_count {
            dispatched.push(
                self.dispatch_one()?
                    .expect("dispatch count should not exceed pending work"),
            );
        }
        Ok(dispatched)
    }

    pub(crate) fn complete_success(
        &mut self,
        key: &StableShardKey,
    ) -> Result<ScheduledShard, LiveWorkerSchedulerError> {
        self.reject_if_terminal()?;
        let shard = self.take_running(key)?;
        self.completed.insert(key.clone());
        if self.successful.insert(key.clone(), shard.range).is_some() {
            return Err(LiveWorkerSchedulerError::DuplicateShardKey { key: key.clone() });
        }
        Ok(shard)
    }

    pub(crate) fn complete_failure(
        &mut self,
        key: &StableShardKey,
    ) -> Result<FailedShardDisposition, LiveWorkerSchedulerError> {
        self.reject_if_terminal()?;
        let parent = self.take_running(key)?;
        self.completed.insert(key.clone());

        if parent.range.is_single_source() {
            self.terminal_failure = Some(parent.clone());
            return Ok(FailedShardDisposition::Terminal { shard: parent });
        }

        let (left_range, right_range) = parent.range.bisect()?;
        let left = ScheduledShard {
            key: parent.key.child(BisectionDirection::Left),
            range: left_range,
        };
        let right = ScheduledShard {
            key: parent.key.child(BisectionDirection::Right),
            range: right_range,
        };
        self.insert_pending(&left)?;
        self.insert_pending(&right)?;
        Ok(FailedShardDisposition::Bisected {
            parent,
            left,
            right,
        })
    }

    /// Validate that all successful leaves exactly partition every source.
    ///
    /// The returned leaves are sorted by source ordinal rather than process
    /// completion order, providing deterministic input to final fan-in.
    pub(crate) fn seal(&self) -> Result<Vec<ScheduledShard>, LiveWorkerSchedulerError> {
        self.reject_if_terminal()?;
        if !self.pending.is_empty() || !self.running.is_empty() {
            return Err(LiveWorkerSchedulerError::NotReadyToSeal {
                pending: self.pending.len(),
                running: self.running.len(),
            });
        }

        let mut leaves = self
            .successful
            .iter()
            .map(|(key, range)| ScheduledShard {
                key: key.clone(),
                range: *range,
            })
            .collect::<Vec<_>>();
        leaves.sort_by_key(|shard| (shard.range.start, shard.range.end, shard.key.clone()));

        let mut expected_start = 0;
        for shard in &leaves {
            if shard.range.end > self.source_count {
                return Err(LiveWorkerSchedulerError::PartitionOutOfBounds {
                    source_count: self.source_count,
                    range: shard.range,
                });
            }
            if shard.range.start > expected_start {
                return Err(LiveWorkerSchedulerError::PartitionGap {
                    expected_start,
                    actual_start: shard.range.start,
                });
            }
            if shard.range.start < expected_start {
                return Err(LiveWorkerSchedulerError::PartitionOverlap {
                    expected_start,
                    actual_start: shard.range.start,
                });
            }
            expected_start = shard.range.end;
        }
        if expected_start != self.source_count {
            return Err(LiveWorkerSchedulerError::PartitionEndsEarly {
                expected_end: self.source_count,
                actual_end: expected_start,
            });
        }
        Ok(leaves)
    }

    fn reject_if_terminal(&self) -> Result<(), LiveWorkerSchedulerError> {
        if let Some(shard) = &self.terminal_failure {
            return Err(LiveWorkerSchedulerError::TerminalFailureActive {
                key: shard.key.clone(),
            });
        }
        Ok(())
    }

    fn take_running(
        &mut self,
        key: &StableShardKey,
    ) -> Result<ScheduledShard, LiveWorkerSchedulerError> {
        if let Some(range) = self.running.remove(key) {
            return Ok(ScheduledShard {
                key: key.clone(),
                range,
            });
        }
        if self.completed.contains(key) {
            return Err(LiveWorkerSchedulerError::DuplicateCompletion { key: key.clone() });
        }
        Err(LiveWorkerSchedulerError::UnknownCompletion { key: key.clone() })
    }

    fn insert_pending(&mut self, shard: &ScheduledShard) -> Result<(), LiveWorkerSchedulerError> {
        if self.pending.contains_key(&shard.key)
            || self.running.contains_key(&shard.key)
            || self.completed.contains(&shard.key)
        {
            return Err(LiveWorkerSchedulerError::DuplicateShardKey {
                key: shard.key.clone(),
            });
        }
        self.pending.insert(shard.key.clone(), shard.range);
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn scheduler(source_count: usize) -> LiveWorkerScheduler {
        LiveWorkerScheduler::four_worker(source_count)
    }

    #[test]
    fn dispatch_never_exceeds_four_active_workers() {
        let mut scheduler = scheduler(160);

        let first_wave = scheduler
            .dispatch_ready()
            .expect("first dispatch wave should succeed");
        assert_eq!(first_wave.len(), 4);
        assert_eq!(scheduler.running_count(), 4);
        assert_eq!(scheduler.pending_count(), 1);
        assert_eq!(scheduler.peak_active_workers(), 4);
        assert_eq!(scheduler.worker_limit(), 4);
        assert_eq!(
            scheduler.dispatch_one(),
            Err(LiveWorkerSchedulerError::AtCapacity { worker_limit: 4 })
        );

        scheduler
            .complete_success(first_wave[2].key())
            .expect("completion should free one worker slot");
        let second_wave = scheduler
            .dispatch_ready()
            .expect("new work should immediately fill the free slot");
        assert_eq!(second_wave.len(), 1);
        assert_eq!(scheduler.running_count(), 4);
        assert_eq!(scheduler.peak_active_workers(), 4);
    }

    #[test]
    fn failed_shards_bisect_at_deterministic_midpoints() {
        let mut scheduler = scheduler(32);
        let root = scheduler.dispatch_ready().unwrap().remove(0);
        let FailedShardDisposition::Bisected { left, right, .. } = scheduler
            .complete_failure(root.key())
            .expect("multi-source root should bisect")
        else {
            panic!("multi-source root unexpectedly became terminal");
        };
        assert_eq!(left.range(), SourceOrdinalRange::new(0, 16).unwrap());
        assert_eq!(right.range(), SourceOrdinalRange::new(16, 32).unwrap());
        assert_eq!(left.key().to_string(), "0..32:L");
        assert_eq!(right.key().to_string(), "0..32:R");

        let next_wave = scheduler.dispatch_ready().unwrap();
        assert_eq!(next_wave, vec![left.clone(), right]);
        let FailedShardDisposition::Bisected {
            left: left_left,
            right: left_right,
            ..
        } = scheduler
            .complete_failure(left.key())
            .expect("left child should bisect deterministically")
        else {
            panic!("multi-source child unexpectedly became terminal");
        };
        assert_eq!(left_left.range(), SourceOrdinalRange::new(0, 8).unwrap());
        assert_eq!(left_right.range(), SourceOrdinalRange::new(8, 16).unwrap());
        assert_eq!(left_left.key().to_string(), "0..32:LL");
        assert_eq!(left_right.key().to_string(), "0..32:LR");
    }

    #[test]
    fn reversed_completion_order_has_deterministic_seal_order() {
        let mut scheduler = scheduler(96);
        let wave = scheduler.dispatch_ready().unwrap();

        for shard in wave.iter().rev() {
            scheduler.complete_success(shard.key()).unwrap();
        }

        let leaves = scheduler
            .seal()
            .expect("all source ranges should be complete");
        let ranges = leaves
            .into_iter()
            .map(|shard| shard.range())
            .collect::<Vec<_>>();
        assert_eq!(
            ranges,
            vec![
                SourceOrdinalRange::new(0, 32).unwrap(),
                SourceOrdinalRange::new(32, 64).unwrap(),
                SourceOrdinalRange::new(64, 96).unwrap(),
            ]
        );
    }

    #[test]
    fn unknown_and_duplicate_completions_are_rejected() {
        let mut scheduler = scheduler(1);
        let shard = scheduler.dispatch_ready().unwrap().remove(0);
        let unknown = StableShardKey::root(SourceOrdinalRange::new(1, 2).unwrap());

        assert_eq!(
            scheduler.complete_success(&unknown),
            Err(LiveWorkerSchedulerError::UnknownCompletion { key: unknown })
        );
        scheduler.complete_success(shard.key()).unwrap();
        assert_eq!(
            scheduler.complete_success(shard.key()),
            Err(LiveWorkerSchedulerError::DuplicateCompletion {
                key: shard.key().clone(),
            })
        );
    }

    #[test]
    fn single_source_failure_is_terminal_and_stops_dispatch() {
        let mut scheduler = LiveWorkerScheduler::new(3, 1, 1).unwrap();
        let shard = scheduler.dispatch_ready().unwrap().remove(0);

        assert_eq!(
            scheduler.complete_failure(shard.key()),
            Ok(FailedShardDisposition::Terminal {
                shard: shard.clone(),
            })
        );
        assert_eq!(scheduler.terminal_failure(), Some(&shard));
        assert_eq!(
            scheduler.dispatch_ready(),
            Err(LiveWorkerSchedulerError::TerminalFailureActive {
                key: shard.key().clone(),
            })
        );
        assert_eq!(scheduler.pending_count(), 2);
        assert_eq!(scheduler.running_count(), 0);
    }

    #[test]
    fn seal_requires_an_exact_gap_free_partition() {
        let mut scheduler = scheduler(65);
        let wave = scheduler.dispatch_ready().unwrap();
        for shard in &wave {
            scheduler.complete_success(shard.key()).unwrap();
        }

        let leaves = scheduler
            .seal()
            .expect("65 sources should form three leaves");
        assert_eq!(leaves.first().unwrap().range().start(), 0);
        assert_eq!(leaves.last().unwrap().range().end(), 65);
        assert_eq!(
            leaves.iter().map(ScheduledShard::range).collect::<Vec<_>>(),
            vec![
                SourceOrdinalRange::new(0, 32).unwrap(),
                SourceOrdinalRange::new(32, 64).unwrap(),
                SourceOrdinalRange::new(64, 65).unwrap(),
            ]
        );

        let removed = leaves[1].key().clone();
        scheduler.successful.remove(&removed);
        assert_eq!(
            scheduler.seal(),
            Err(LiveWorkerSchedulerError::PartitionGap {
                expected_start: 32,
                actual_start: 64,
            })
        );
    }

    #[test]
    fn zero_sources_seal_as_an_empty_exact_partition() {
        let scheduler = scheduler(0);
        assert_eq!(scheduler.seal(), Ok(Vec::new()));
    }
}
