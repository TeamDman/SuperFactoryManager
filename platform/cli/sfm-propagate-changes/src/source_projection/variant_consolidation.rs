//! Migration-time factoring of witnessed sources into genuinely shared text.
//!
//! Historical inputs are examples for this pure authoring operation, never
//! production renderer routes. Version changes become small grouped cases;
//! same-version changes must have explicitly reviewed feature ownership.

use super::context::ProjectionContext;
use super::provenance::sha256;
use super::render_java_source;
use eyre::Result;
use eyre::WrapErr;
use eyre::ensure;
use facet::Facet;
use std::collections::BTreeMap;
use std::collections::BTreeSet;
use std::fmt::Write as _;

const MAX_WITNESSES: usize = 256;
const MAX_TOTAL_SOURCE_BYTES: usize = 128 * 1024 * 1024;
const MAX_REVIEWED_FEATURE_OWNERS: usize = 64;
pub const REGION_REVIEW_ALGORITHM: &str = "sfm:line-anchor-region-review@1";

/// One named ordinary source witness, or an explicit absent-file witness.
#[derive(Clone, Debug)]
pub struct SourceVariant {
    pub id: String,
    pub minecraft_version: String,
    /// The complete registered boolean map, including disabled features.
    pub features: BTreeMap<String, bool>,
    pub source: Option<Vec<u8>>,
}

/// Reviewed functional owners allowed to account for differing source regions.
///
/// This is not an inferred release/development switch. Correlated candidates
/// remain ambiguous and are rejected rather than arbitrarily choosing a flag.
#[derive(Clone, Debug, Default)]
pub struct ConsolidationOptions {
    pub allowed_features: BTreeSet<String>,
}

/// Migration evidence only: a changed witness invalidates this entire review.
#[derive(Clone, Debug, Eq, Facet, PartialEq)]
#[facet(deny_unknown_fields)]
pub struct RegionReviewReport {
    pub algorithm: String,
    pub input_fingerprint: String,
    pub regions: Vec<RegionReview>,
}

#[derive(Clone, Copy, Debug, Eq, Facet, PartialEq)]
#[facet(tag = "kind", rename_all = "snake_case")]
#[repr(u8)]
pub enum RegionKind {
    Membership,
    Content { ordinal: usize },
}

#[derive(Clone, Debug, Eq, Facet, PartialEq)]
#[facet(deny_unknown_fields)]
pub struct RegionReview {
    pub id: String,
    pub kind: RegionKind,
    pub before_anchor_sha256: Option<String>,
    pub after_anchor_sha256: Option<String>,
    pub alternatives: Vec<RegionAlternative>,
    pub requires_feature_ownership: bool,
    /// Differing witnessed flag values are candidates, not inferred ownership.
    pub varying_features: BTreeSet<String>,
}

#[derive(Clone, Debug, Eq, Facet, Ord, PartialEq, PartialOrd)]
#[facet(tag = "kind", content = "value", rename_all = "snake_case")]
#[repr(u8)]
pub enum RegionPayload {
    Membership(bool),
    Text(String),
}

#[derive(Clone, Debug, Eq, Facet, PartialEq)]
#[facet(deny_unknown_fields)]
pub struct RegionAlternative {
    pub payload: RegionPayload,
    pub payload_sha256: String,
    pub line_count: usize,
    pub witnesses: Vec<RegionWitness>,
}

#[derive(Clone, Debug, Eq, Facet, PartialEq)]
#[facet(deny_unknown_fields)]
pub struct RegionWitness {
    pub id: String,
    pub minecraft_version: String,
    pub features: BTreeMap<String, bool>,
}

/// Explicit, reviewed ownership for one fingerprint-bound conditional region.
#[derive(Clone, Debug, Default, Eq, Facet, PartialEq)]
#[facet(deny_unknown_fields)]
pub struct ReviewedRegionOwnership {
    pub allowed_features: BTreeSet<String>,
    /// Used only for content: partition every witnessed payload contiguously.
    /// The parent owner set must be empty when subregions supply their own sets.
    pub subregions: Vec<ReviewedSubregion>,
}

#[derive(Clone, Debug, Default, Eq, Facet, PartialEq)]
#[facet(deny_unknown_fields)]
pub struct ReviewedSubregion {
    pub allowed_features: BTreeSet<String>,
    /// Exactly one range per region witness ID, including empty ranges.
    pub witness_lines: BTreeMap<String, ReviewedLineRange>,
}

#[derive(Clone, Copy, Debug, Eq, Facet, PartialEq)]
#[facet(deny_unknown_fields)]
pub struct ReviewedLineRange {
    pub start: usize,
    pub end: usize,
}

/// A typed decision containing only feature booleans and Minecraft versions.
#[derive(Clone, Debug, Eq, PartialEq)]
pub enum SelectionRule<T> {
    Value(T),
    Feature {
        feature: String,
        enabled: Box<Self>,
        disabled: Box<Self>,
    },
    MinecraftVersion {
        cases: Vec<VersionCase<T>>,
    },
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct VersionCase<T> {
    pub minecraft_versions: Vec<String>,
    pub rule: Box<SelectionRule<T>>,
}

#[derive(Clone, Debug, Eq, Ord, PartialEq, PartialOrd)]
struct ContextSelector {
    minecraft_version: String,
    features: BTreeMap<String, bool>,
}

/// Evaluate membership before rendering: an absent file is not empty Java.
#[derive(Clone, Debug)]
pub struct MembershipRule {
    pub decision: SelectionRule<bool>,
    witnessed_contexts: BTreeSet<ContextSelector>,
    registered_features: BTreeSet<String>,
    allow_unseen_features: bool,
}

impl MembershipRule {
    /// Refuse unseen selectors rather than selecting a nearest historic source.
    /// Identical, universally present text may accept new known-flag combinations.
    ///
    /// # Errors
    /// Fails for unknown versions, changed feature registrations, unwitnessed
    /// combinations for conditional sources, or a malformed decision.
    pub fn includes(
        &self,
        minecraft_version: &str,
        features: &BTreeMap<String, bool>,
    ) -> Result<bool> {
        ensure!(
            features.keys().cloned().collect::<BTreeSet<_>>() == self.registered_features,
            "consolidated source feature registration differs from its reviewed witnesses"
        );
        ensure!(
            self.witnessed_contexts
                .iter()
                .any(|context| context.minecraft_version == minecraft_version),
            "unwitnessed consolidated Minecraft version '{minecraft_version}'"
        );
        if !self.allow_unseen_features {
            ensure!(
                self.witnessed_contexts.contains(&ContextSelector {
                    minecraft_version: minecraft_version.to_owned(),
                    features: features.clone(),
                }),
                "unwitnessed consolidated feature combination for Minecraft {minecraft_version}"
            );
        }
        evaluate_rule(&self.decision, minecraft_version, features).copied()
    }
}

#[derive(Clone, Debug, Default, Eq, PartialEq)]
pub struct ConsolidationStats {
    pub input_contexts: usize,
    pub present_contexts: usize,
    pub distinct_sources: usize,
    pub shared_lines: usize,
    pub conditional_regions: usize,
}

#[derive(Clone, Debug)]
pub struct ConsolidatedSource {
    /// None only when all witnesses omit the file.
    pub template: Option<String>,
    pub membership: MembershipRule,
    pub statistics: ConsolidationStats,
}

#[derive(Clone)]
struct Example<'a, T> {
    variant: &'a SourceVariant,
    value: T,
}

enum SourcePiece<'a> {
    Common(String),
    Conditional {
        region_id: String,
        examples: Vec<Example<'a, String>>,
    },
}

struct PreparedSource<'a> {
    ordered: Vec<&'a SourceVariant>,
    presence: Vec<Example<'a, bool>>,
    pieces: Vec<SourcePiece<'a>>,
    report: RegionReviewReport,
    statistics: ConsolidationStats,
}

/// Factor line-exact source witnesses without reading or writing any files.
///
/// # Errors
/// Rejects conflicting selector tuples, unsupported source text, unreviewed or
/// ambiguous feature decisions, whole-file dispatch without shared anchors,
/// and changed EOF text that whole-line directives cannot represent exactly.
pub fn consolidate(
    variants: &[SourceVariant],
    options: &ConsolidationOptions,
) -> Result<ConsolidatedSource> {
    let prepared = prepare_sources(variants, options)?;
    let decisions = prepared
        .report
        .regions
        .iter()
        .map(|region| {
            (
                region.id.clone(),
                ReviewedRegionOwnership {
                    allowed_features: options.allowed_features.clone(),
                    ..ReviewedRegionOwnership::default()
                },
            )
        })
        .collect();
    assemble_sources(&prepared, &decisions)
}

/// Preview conditional content and membership without guessing feature owners.
///
/// # Errors
/// Rejects invalid/conflicting witnesses or text that cannot be safely factored.
pub fn preview_regions(variants: &[SourceVariant]) -> Result<RegionReviewReport> {
    Ok(prepare_sources(variants, &ConsolidationOptions::default())?.report)
}

/// Apply explicit decisions to the exact previewed migration input set.
///
/// Every reported region requires one decision, including version-only regions
/// whose owner set can be empty. Subregion splits must cover every witness in
/// order, with no gaps, overlaps, omissions or unwitnessed IDs. These hashes are
/// migration review evidence, not runtime source ownership or an edit-adoption
/// requirement after the core template has been accepted.
///
/// # Errors
/// Rejects stale/tampered reports, missing/extra decisions, invalid partitions,
/// unregistered/ambiguous owners, or non-exact reconstruction.
pub fn consolidate_reviewed(
    variants: &[SourceVariant],
    report: &RegionReviewReport,
    decisions: &BTreeMap<String, ReviewedRegionOwnership>,
) -> Result<ConsolidatedSource> {
    let prepared = prepare_sources(variants, &ConsolidationOptions::default())?;
    ensure!(
        prepared.report == *report,
        "stale or modified source-region review: witnesses, algorithm or region evidence changed"
    );
    assemble_sources(&prepared, decisions)
}

fn selector(variant: &SourceVariant) -> ContextSelector {
    ContextSelector {
        minecraft_version: variant.minecraft_version.clone(),
        features: variant.features.clone(),
    }
}

fn valid_feature_id(value: &str) -> bool {
    !value.is_empty()
        && value.bytes().enumerate().all(|(index, byte)| {
            byte.is_ascii_lowercase() || (index > 0 && (byte.is_ascii_digit() || byte == b'_'))
        })
}

fn validate_and_order<'a>(
    variants: &'a [SourceVariant],
    options: &ConsolidationOptions,
) -> Result<Vec<&'a SourceVariant>> {
    ensure!(
        !variants.is_empty(),
        "source consolidation requires witnesses"
    );
    ensure!(
        variants.len() <= MAX_WITNESSES,
        "source consolidation exceeds the bounded witness count"
    );
    ensure!(
        variants
            .iter()
            .try_fold(0_usize, |total, variant| {
                total.checked_add(variant.source.as_ref().map_or(0, Vec::len))
            })
            .is_some_and(|total| total <= MAX_TOTAL_SOURCE_BYTES),
        "source consolidation exceeds the bounded source-byte budget"
    );
    ensure!(
        options.allowed_features.len() <= MAX_REVIEWED_FEATURE_OWNERS,
        "source consolidation exceeds the bounded feature-owner count"
    );
    let registered: BTreeSet<_> = variants[0].features.keys().cloned().collect();
    ensure!(
        registered.iter().all(|id| valid_feature_id(id)),
        "source consolidation contains an invalid feature identifier"
    );
    ensure!(
        options.allowed_features.is_subset(&registered),
        "source consolidation declares an unregistered feature owner"
    );
    let mut ids = BTreeSet::new();
    let mut by_selector = BTreeMap::<ContextSelector, &SourceVariant>::new();
    for variant in variants {
        ensure!(
            !variant.id.is_empty() && ids.insert(&variant.id),
            "source witness identifiers must be nonempty and unique"
        );
        ensure!(
            variant
                .minecraft_version
                .split('.')
                .all(|part| { !part.is_empty() && part.bytes().all(|byte| byte.is_ascii_digit()) }),
            "source witness '{}' has an unsupported Minecraft version literal",
            variant.id
        );
        ensure!(
            variant.features.keys().cloned().collect::<BTreeSet<_>>() == registered,
            "source witness '{}' differs in registered feature keys",
            variant.id
        );
        match by_selector.entry(selector(variant)) {
            std::collections::btree_map::Entry::Vacant(entry) => {
                entry.insert(variant);
            }
            std::collections::btree_map::Entry::Occupied(mut entry) => {
                let existing = entry.get();
                ensure!(
                    existing.source == variant.source,
                    "conflicting sources for identical selectors: '{}' and '{}'",
                    existing.id,
                    variant.id
                );
                if variant.id < existing.id {
                    entry.insert(variant);
                }
            }
        }
    }
    Ok(by_selector.into_values().collect())
}

fn prepare_sources<'a>(
    variants: &'a [SourceVariant],
    options: &ConsolidationOptions,
) -> Result<PreparedSource<'a>> {
    let ordered = validate_and_order(variants, options)?;
    let present: Vec<_> = ordered
        .iter()
        .copied()
        .filter(|variant| variant.source.is_some())
        .collect();
    let distinct: BTreeSet<_> = present
        .iter()
        .filter_map(|variant| variant.source.as_deref())
        .collect();
    let presence: Vec<_> = ordered
        .iter()
        .map(|variant| Example {
            variant,
            value: variant.source.is_some(),
        })
        .collect();
    let mut report = RegionReviewReport {
        algorithm: REGION_REVIEW_ALGORITHM.to_owned(),
        input_fingerprint: input_fingerprint(variants),
        regions: Vec::new(),
    };
    if presence
        .iter()
        .any(|example| example.value != presence[0].value)
    {
        report.regions.push(make_region_review(
            RegionKind::Membership,
            &presence,
            |value| RegionPayload::Membership(*value),
            None,
            None,
            &report.input_fingerprint,
        ));
    }
    let pieces = if present.is_empty() {
        Vec::new()
    } else {
        factor_sources(&present, distinct.len(), &mut report)?
    };
    Ok(PreparedSource {
        ordered,
        presence,
        pieces,
        report,
        statistics: ConsolidationStats {
            input_contexts: variants.len(),
            present_contexts: present.len(),
            distinct_sources: distinct.len(),
            ..ConsolidationStats::default()
        },
    })
}

fn input_fingerprint(variants: &[SourceVariant]) -> String {
    let mut ordered: Vec<_> = variants.iter().collect();
    ordered.sort_by(|left, right| {
        selector(left)
            .cmp(&selector(right))
            .then_with(|| left.id.cmp(&right.id))
    });
    let mut bytes = Vec::new();
    append_fingerprint_field(&mut bytes, REGION_REVIEW_ALGORITHM.as_bytes());
    for variant in ordered {
        append_fingerprint_field(&mut bytes, variant.id.as_bytes());
        append_fingerprint_field(&mut bytes, variant.minecraft_version.as_bytes());
        for (feature, enabled) in &variant.features {
            append_fingerprint_field(&mut bytes, feature.as_bytes());
            bytes.push(u8::from(*enabled));
        }
        bytes.push(0);
        if let Some(source) = &variant.source {
            bytes.push(1);
            append_fingerprint_field(&mut bytes, sha256(source).as_bytes());
        } else {
            bytes.push(0);
        }
    }
    sha256(&bytes)
}

fn append_fingerprint_field(output: &mut Vec<u8>, value: &[u8]) {
    output.extend_from_slice(value.len().to_string().as_bytes());
    output.push(b':');
    output.extend_from_slice(value);
}

fn make_region_review<T>(
    kind: RegionKind,
    examples: &[Example<'_, T>],
    payload: impl Fn(&T) -> RegionPayload,
    before_anchor: Option<&str>,
    after_anchor: Option<&str>,
    input_fingerprint: &str,
) -> RegionReview {
    let mut alternatives = BTreeMap::<RegionPayload, Vec<RegionWitness>>::new();
    let mut version_payloads = BTreeMap::<&str, BTreeSet<RegionPayload>>::new();
    let mut feature_states = BTreeMap::<&str, BTreeSet<bool>>::new();
    for example in examples {
        let value = payload(&example.value);
        version_payloads
            .entry(&example.variant.minecraft_version)
            .or_default()
            .insert(value.clone());
        alternatives.entry(value).or_default().push(RegionWitness {
            id: example.variant.id.clone(),
            minecraft_version: example.variant.minecraft_version.clone(),
            features: example.variant.features.clone(),
        });
        for (feature, enabled) in &example.variant.features {
            feature_states.entry(feature).or_default().insert(*enabled);
        }
    }
    let identity = match kind {
        RegionKind::Membership => "membership".to_owned(),
        RegionKind::Content { ordinal } => format!("content-{ordinal}"),
    };
    RegionReview {
        id: format!(
            "region-{}",
            sha256(
                format!("{REGION_REVIEW_ALGORITHM}\0{input_fingerprint}\0{identity}").as_bytes()
            )
        ),
        kind,
        before_anchor_sha256: before_anchor.map(|anchor| sha256(anchor.as_bytes())),
        after_anchor_sha256: after_anchor.map(|anchor| sha256(anchor.as_bytes())),
        alternatives: alternatives
            .into_iter()
            .map(|(payload, witnesses)| {
                let (bytes, line_count) = match &payload {
                    RegionPayload::Membership(present) => {
                        (if *present { "present" } else { "absent" }, 0)
                    }
                    RegionPayload::Text(text) => {
                        (text.as_str(), text.split_inclusive('\n').count())
                    }
                };
                RegionAlternative {
                    payload_sha256: sha256(bytes.as_bytes()),
                    payload,
                    line_count,
                    witnesses,
                }
            })
            .collect(),
        requires_feature_ownership: version_payloads.values().any(|values| values.len() > 1),
        varying_features: feature_states
            .into_iter()
            .filter(|(_, values)| values.len() > 1)
            .map(|(feature, _)| feature.to_owned())
            .collect(),
    }
}

#[derive(Clone, Copy)]
struct RegionBoundary<'a> {
    starts: &'a [usize],
    ends: &'a [usize],
    before_anchor: Option<&'a str>,
    after_anchor: Option<&'a str>,
}

fn factor_sources<'a>(
    variants: &[&'a SourceVariant],
    distinct_sources: usize,
    report: &mut RegionReviewReport,
) -> Result<Vec<SourcePiece<'a>>> {
    let sources: Vec<&str> = variants
        .iter()
        .map(|variant| {
            std::str::from_utf8(variant.source.as_deref().expect("present source"))
                .wrap_err_with(|| format!("source witness '{}' is not UTF-8", variant.id))
        })
        .collect::<Result<_>>()?;
    let lines: Vec<Vec<&str>> = sources
        .iter()
        .map(|source| source.split_inclusive('\n').collect())
        .collect();
    let base = choose_reference(&sources);
    let maps: Vec<_> = lines
        .iter()
        .map(|other| shared_line_map(&lines[base], other))
        .collect();
    let anchors: Vec<_> = (0..lines[base].len())
        .filter(|index| maps.iter().all(|map| map[*index].is_some()))
        .collect();
    ensure!(
        !anchors.is_empty() || distinct_sources == 1,
        "source variants have no shared line anchor; explicit API adapter review is required instead of whole-file dispatch"
    );
    let mut pieces = Vec::new();
    let mut cursors = vec![0; variants.len()];
    let mut before_anchor = None;
    for anchor in anchors {
        let ends: Vec<_> = maps
            .iter()
            .map(|map| map[anchor].expect("verified common anchor"))
            .collect();
        if cursors.iter().zip(&ends).any(|(start, end)| start != end) {
            collect_region(
                &mut pieces,
                variants,
                &lines,
                RegionBoundary {
                    starts: &cursors,
                    ends: &ends,
                    before_anchor,
                    after_anchor: Some(lines[base][anchor]),
                },
                report,
            )?;
        }
        push_common(&mut pieces, lines[base][anchor]);
        before_anchor = Some(lines[base][anchor]);
        for (cursor, end) in cursors.iter_mut().zip(ends) {
            *cursor = end + 1;
        }
    }
    let ends: Vec<_> = lines.iter().map(Vec::len).collect();
    if cursors.iter().zip(&ends).any(|(start, end)| start != end) {
        collect_region(
            &mut pieces,
            variants,
            &lines,
            RegionBoundary {
                starts: &cursors,
                ends: &ends,
                before_anchor,
                after_anchor: None,
            },
            report,
        )?;
    }
    Ok(pieces)
}

fn push_common(pieces: &mut Vec<SourcePiece<'_>>, text: &str) {
    if let Some(SourcePiece::Common(common)) = pieces.last_mut() {
        common.push_str(text);
    } else {
        pieces.push(SourcePiece::Common(text.to_owned()));
    }
}

fn choose_reference(sources: &[&str]) -> usize {
    let mut counts = BTreeMap::<&str, usize>::new();
    for source in sources {
        *counts.entry(source).or_default() += 1;
    }
    let chosen = counts
        .iter()
        .max_by(|(left, left_count), (right, right_count)| {
            left_count.cmp(right_count).then_with(|| right.cmp(left))
        })
        .expect("at least one present source")
        .0;
    sources
        .iter()
        .position(|source| source == chosen)
        .expect("counted reference source")
}

fn shared_line_map(before: &[&str], after: &[&str]) -> Vec<Option<usize>> {
    let mut input = gix::diff::blob::InternedInput::<&str>::default();
    input.update_before(before.iter().copied());
    input.update_after(after.iter().copied());
    let diff =
        gix::diff::blob::diff_with_slider_heuristics(gix::diff::blob::Algorithm::Histogram, &input);
    let mut map = vec![None; before.len()];
    let mut before_cursor = 0;
    let mut after_cursor = 0;
    for hunk in diff.hunks() {
        while before_cursor < hunk.before.start as usize {
            map[before_cursor] = Some(after_cursor);
            before_cursor += 1;
            after_cursor += 1;
        }
        before_cursor = hunk.before.end as usize;
        after_cursor = hunk.after.end as usize;
    }
    while before_cursor < before.len() {
        map[before_cursor] = Some(after_cursor);
        before_cursor += 1;
        after_cursor += 1;
    }
    map
}

fn collect_region<'a>(
    pieces: &mut Vec<SourcePiece<'a>>,
    variants: &[&'a SourceVariant],
    lines: &[Vec<&str>],
    boundary: RegionBoundary<'_>,
    report: &mut RegionReviewReport,
) -> Result<()> {
    let examples: Vec<_> = variants
        .iter()
        .enumerate()
        .map(|(index, variant)| Example {
            variant,
            value: lines[index][boundary.starts[index]..boundary.ends[index]].concat(),
        })
        .collect();
    let first = examples
        .first()
        .ok_or_else(|| eyre::eyre!("content region has no witnesses"))?;
    if examples.iter().all(|example| example.value == first.value) {
        push_common(pieces, &first.value);
    } else {
        let ordinal = report
            .regions
            .iter()
            .filter(|region| matches!(region.kind, RegionKind::Content { .. }))
            .count()
            + 1;
        let review = make_region_review(
            RegionKind::Content { ordinal },
            &examples,
            |text| RegionPayload::Text(text.clone()),
            boundary.before_anchor,
            boundary.after_anchor,
            &report.input_fingerprint,
        );
        pieces.push(SourcePiece::Conditional {
            region_id: review.id.clone(),
            examples,
        });
        report.regions.push(review);
    }
    Ok(())
}

fn assemble_sources(
    prepared: &PreparedSource<'_>,
    decisions: &BTreeMap<String, ReviewedRegionOwnership>,
) -> Result<ConsolidatedSource> {
    let expected: BTreeSet<_> = prepared
        .report
        .regions
        .iter()
        .map(|region| region.id.as_str())
        .collect();
    ensure!(
        decisions
            .keys()
            .map(String::as_str)
            .collect::<BTreeSet<_>>()
            == expected,
        "source-region decisions must cover exactly the reviewed region IDs"
    );
    let first = prepared
        .ordered
        .first()
        .ok_or_else(|| eyre::eyre!("prepared source has no witnesses"))?;
    let registered_features: BTreeSet<_> = first.features.keys().cloned().collect();
    for ownership in decisions.values() {
        validate_owner_set(&ownership.allowed_features, &registered_features)?;
        for subregion in &ownership.subregions {
            validate_owner_set(&subregion.allowed_features, &registered_features)?;
        }
    }
    let membership_decision = if let Some(review) = prepared
        .report
        .regions
        .iter()
        .find(|region| matches!(region.kind, RegionKind::Membership))
    {
        let ownership = &decisions[&review.id];
        ensure!(
            ownership.subregions.is_empty(),
            "membership regions cannot be split into text ranges"
        );
        build_rule(&prepared.presence, &ownership.allowed_features)?
    } else {
        SelectionRule::Value(first.source.is_some())
    };
    let membership = MembershipRule {
        decision: membership_decision,
        witnessed_contexts: prepared
            .ordered
            .iter()
            .map(|variant| selector(variant))
            .collect(),
        registered_features,
        allow_unseen_features: prepared.statistics.present_contexts == prepared.ordered.len()
            && prepared.statistics.distinct_sources == 1,
    };
    let mut statistics = prepared.statistics.clone();
    let template = if statistics.present_contexts == 0 {
        None
    } else {
        let mut output = String::new();
        for piece in &prepared.pieces {
            match piece {
                SourcePiece::Common(text) => {
                    statistics.shared_lines += text.split_inclusive('\n').count();
                    output.push_str(&escape_opaque_text(text)?);
                }
                SourcePiece::Conditional {
                    region_id,
                    examples,
                } => {
                    emit_reviewed_region(
                        &mut output,
                        examples,
                        &decisions[region_id],
                        &mut statistics,
                    )?;
                }
            }
        }
        Some(output)
    };
    let result = ConsolidatedSource {
        template,
        membership,
        statistics,
    };
    verify_reconstruction(&result, &prepared.ordered)?;
    Ok(result)
}

fn validate_owner_set(owners: &BTreeSet<String>, registered: &BTreeSet<String>) -> Result<()> {
    ensure!(
        owners.len() <= MAX_REVIEWED_FEATURE_OWNERS && owners.is_subset(registered),
        "reviewed region contains unregistered or excessive feature owners"
    );
    Ok(())
}

fn emit_reviewed_region(
    output: &mut String,
    examples: &[Example<'_, String>],
    ownership: &ReviewedRegionOwnership,
    statistics: &mut ConsolidationStats,
) -> Result<()> {
    if ownership.subregions.is_empty() {
        return emit_examples(output, examples, &ownership.allowed_features, statistics);
    }
    ensure!(
        ownership.allowed_features.is_empty(),
        "split regions must use explicit subregion owners, not a parent catchall"
    );
    ensure!(
        ownership.subregions.len() <= MAX_WITNESSES,
        "reviewed region exceeds the bounded subregion count"
    );
    let expected_ids: BTreeSet<_> = examples
        .iter()
        .map(|example| example.variant.id.as_str())
        .collect();
    let lines: Vec<Vec<_>> = examples
        .iter()
        .map(|example| example.value.split_inclusive('\n').collect())
        .collect();
    let mut cursors = vec![0; examples.len()];
    for subregion in &ownership.subregions {
        ensure!(
            subregion
                .witness_lines
                .keys()
                .map(String::as_str)
                .collect::<BTreeSet<_>>()
                == expected_ids,
            "subregion ranges must name exactly every reviewed content witness"
        );
        let mut pieces = Vec::new();
        for (index, example) in examples.iter().enumerate() {
            let range = subregion.witness_lines[&example.variant.id];
            ensure!(
                range.start == cursors[index]
                    && range.start <= range.end
                    && range.end <= lines[index].len(),
                "noncontiguous, overlapping or out-of-bounds subregion range for '{}'",
                example.variant.id
            );
            pieces.push(Example {
                variant: example.variant,
                value: lines[index][range.start..range.end].concat(),
            });
            cursors[index] = range.end;
        }
        emit_examples(output, &pieces, &subregion.allowed_features, statistics)?;
    }
    ensure!(
        cursors
            .iter()
            .zip(&lines)
            .all(|(cursor, source)| *cursor == source.len()),
        "subregion partitions omit reviewed content lines"
    );
    Ok(())
}

fn emit_examples(
    output: &mut String,
    examples: &[Example<'_, String>],
    owners: &BTreeSet<String>,
    statistics: &mut ConsolidationStats,
) -> Result<()> {
    let rule = build_rule(examples, owners)?;
    if let SelectionRule::Value(text) = &rule {
        statistics.shared_lines += text.split_inclusive('\n').count();
        output.push_str(&escape_opaque_text(text)?);
    } else {
        ensure!(
            output.is_empty() || output.ends_with('\n'),
            "conditional region does not start at a whole-line boundary"
        );
        ensure!(
            examples
                .iter()
                .all(|example| example.value.is_empty() || example.value.ends_with('\n')),
            "changed trailing text without a final newline cannot be represented by whole-line directives"
        );
        emit_text_rule(&rule, output)?;
        statistics.conditional_regions += 1;
    }
    Ok(())
}

fn build_rule<T: Clone + Eq>(
    examples: &[Example<'_, T>],
    owners: &BTreeSet<String>,
) -> Result<SelectionRule<T>> {
    let first = examples.first().expect("a region has source witnesses");
    if examples.iter().all(|example| example.value == first.value) {
        return Ok(SelectionRule::Value(first.value.clone()));
    }
    let mut versions = BTreeMap::<&str, Vec<Example<'_, T>>>::new();
    for example in examples {
        versions
            .entry(&example.variant.minecraft_version)
            .or_default()
            .push(example.clone());
    }
    let mut cases = Vec::<VersionCase<T>>::new();
    for (version, examples) in versions {
        let rule = build_feature_rule(&examples, owners)?;
        if let Some(existing) = cases.iter_mut().find(|case| *case.rule == rule) {
            existing.minecraft_versions.push(version.to_owned());
        } else {
            cases.push(VersionCase {
                minecraft_versions: vec![version.to_owned()],
                rule: Box::new(rule),
            });
        }
    }
    if cases.len() == 1 {
        Ok(*cases.pop().expect("one version rule").rule)
    } else {
        Ok(SelectionRule::MinecraftVersion { cases })
    }
}

fn build_feature_rule<T: Clone + Eq>(
    examples: &[Example<'_, T>],
    owners: &BTreeSet<String>,
) -> Result<SelectionRule<T>> {
    let first = examples.first().expect("a feature branch has witnesses");
    if examples.iter().all(|example| example.value == first.value) {
        return Ok(SelectionRule::Value(first.value.clone()));
    }
    let candidates = discriminating_features(examples, owners)?;
    let feature = candidates.first().ok_or_else(|| {
        eyre::eyre!(
            "same-version source differences for Minecraft {} have no reviewed discriminating feature",
            first.variant.minecraft_version
        )
    })?;
    let mut remaining = owners.clone();
    remaining.remove(feature);
    let (enabled, disabled): (Vec<_>, Vec<_>) = examples
        .iter()
        .cloned()
        .partition(|example| example.variant.features[feature]);
    Ok(SelectionRule::Feature {
        feature: feature.clone(),
        enabled: Box::new(build_feature_rule(&enabled, &remaining)?),
        disabled: Box::new(build_feature_rule(&disabled, &remaining)?),
    })
}

fn discriminating_features<T>(
    examples: &[Example<'_, T>],
    owners: &BTreeSet<String>,
) -> Result<Vec<String>> {
    let mut partitions = BTreeMap::<Vec<bool>, &str>::new();
    let mut candidates = Vec::new();
    for feature in owners {
        let partition: Vec<_> = examples
            .iter()
            .map(|example| example.variant.features[feature])
            .collect();
        if partition.iter().all(|value| *value == partition[0]) {
            continue;
        }
        let inverse: Vec<_> = partition.iter().map(|value| !value).collect();
        let signature = if partition < inverse {
            partition
        } else {
            inverse
        };
        if let Some(existing) = partitions.insert(signature, feature) {
            eyre::bail!(
                "ambiguous correlated feature owners '{existing}' and '{feature}'; narrow the reviewed region ownership"
            );
        }
        candidates.push(feature.clone());
    }
    Ok(candidates)
}

fn evaluate_rule<'a, T>(
    rule: &'a SelectionRule<T>,
    minecraft_version: &str,
    features: &BTreeMap<String, bool>,
) -> Result<&'a T> {
    match rule {
        SelectionRule::Value(value) => Ok(value),
        SelectionRule::Feature {
            feature,
            enabled,
            disabled,
        } => {
            let enabled_value = features.get(feature).ok_or_else(|| {
                eyre::eyre!("unknown consolidated membership feature '{feature}'")
            })?;
            evaluate_rule(
                if *enabled_value { enabled } else { disabled },
                minecraft_version,
                features,
            )
        }
        SelectionRule::MinecraftVersion { cases } => {
            let case = cases
                .iter()
                .find(|case| {
                    case.minecraft_versions
                        .iter()
                        .any(|version| version == minecraft_version)
                })
                .ok_or_else(|| {
                    eyre::eyre!("no consolidated rule for Minecraft {minecraft_version}")
                })?;
            evaluate_rule(&case.rule, minecraft_version, features)
        }
    }
}

fn emit_text_rule(rule: &SelectionRule<String>, output: &mut String) -> Result<()> {
    match rule {
        SelectionRule::Value(text) => output.push_str(&escape_opaque_text(text)?),
        SelectionRule::Feature {
            feature,
            enabled,
            disabled,
        } => {
            writeln!(output, "{{% if features.{feature} %}}")
                .expect("writing to a String cannot fail");
            emit_text_rule(enabled, output)?;
            output.push_str("{% else %}\n");
            emit_text_rule(disabled, output)?;
            output.push_str("{% endif %}\n");
        }
        SelectionRule::MinecraftVersion { cases } => {
            output.push_str("{% case minecraft_version %}\n");
            for case in cases {
                let alternatives = case
                    .minecraft_versions
                    .iter()
                    .map(|version| format!("'{version}'"))
                    .collect::<Vec<_>>()
                    .join(", ");
                writeln!(output, "{{% when {alternatives} %}}")
                    .expect("writing to a String cannot fail");
                emit_text_rule(&case.rule, output)?;
            }
            output.push_str("{% endcase %}\n");
        }
    }
    Ok(())
}

fn escape_opaque_text(text: &str) -> Result<String> {
    let mut output = String::new();
    for line in text.split_inclusive('\n') {
        let trimmed = line.trim_start();
        ensure!(
            !trimmed.starts_with("\\{%"),
            "literal escaped directive text requires explicit authoring review"
        );
        if trimmed.starts_with("{%") {
            let prefix = line.len() - trimmed.len();
            output.push_str(&line[..prefix]);
            output.push('\\');
            output.push_str(trimmed);
        } else {
            output.push_str(line);
        }
    }
    Ok(output)
}

fn verify_reconstruction(result: &ConsolidatedSource, variants: &[&SourceVariant]) -> Result<()> {
    for variant in variants {
        ensure!(
            result
                .membership
                .includes(&variant.minecraft_version, &variant.features)?
                == variant.source.is_some(),
            "consolidated membership does not reconstruct witness '{}'",
            variant.id
        );
        let Some(expected) = &variant.source else {
            continue;
        };
        let context = ProjectionContext {
            minecraft_version: variant.minecraft_version.clone(),
            preset: "consolidation-validation".to_owned(),
            environment: "dev".to_owned(),
            projection_key: variant.id.clone(),
            features: variant.features.clone(),
            targets: BTreeMap::new(),
        };
        let rendered = render_java_source(
            result
                .template
                .as_deref()
                .expect("present source has a template"),
            &context,
        )?;
        ensure!(
            rendered.as_bytes() == expected,
            "consolidated template does not reconstruct witness '{}' byte-for-byte",
            variant.id
        );
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    fn variant(id: &str, version: &str, enabled: bool, source: Option<&str>) -> SourceVariant {
        SourceVariant {
            id: id.to_owned(),
            minecraft_version: version.to_owned(),
            features: BTreeMap::from([("packet_computation".to_owned(), enabled)]),
            source: source.map(|source| source.as_bytes().to_vec()),
        }
    }

    fn options() -> ConsolidationOptions {
        ConsolidationOptions {
            allowed_features: BTreeSet::from(["packet_computation".to_owned()]),
        }
    }

    fn decisions(
        report: &RegionReviewReport,
        owner: &str,
    ) -> BTreeMap<String, ReviewedRegionOwnership> {
        report
            .regions
            .iter()
            .map(|region| {
                (
                    region.id.clone(),
                    ReviewedRegionOwnership {
                        allowed_features: if owner.is_empty() {
                            BTreeSet::new()
                        } else {
                            BTreeSet::from([owner.to_owned()])
                        },
                        ..ReviewedRegionOwnership::default()
                    },
                )
            })
            .collect()
    }

    fn render(result: &ConsolidatedSource, variant: &SourceVariant) -> Option<String> {
        if !result
            .membership
            .includes(&variant.minecraft_version, &variant.features)
            .unwrap()
        {
            return None;
        }
        Some(
            render_java_source(
                result.template.as_deref().unwrap(),
                &ProjectionContext {
                    minecraft_version: variant.minecraft_version.clone(),
                    preset: "test".to_owned(),
                    environment: "dev".to_owned(),
                    projection_key: variant.id.clone(),
                    features: variant.features.clone(),
                    targets: BTreeMap::new(),
                },
            )
            .unwrap(),
        )
    }

    #[test]
    fn version_variants_share_real_lines_and_group_identical_alternatives() {
        let old = "package example;\nimport legacy.Handler;\npublic class Example {}\n";
        let new = "package example;\nimport modern.Handler;\npublic class Example {}\n";
        let variants = vec![
            variant("old", "1.19.2", false, Some(old)),
            variant("old2", "1.19.4", false, Some(old)),
            variant("new", "26.1.2", false, Some(new)),
        ];
        let result = consolidate(&variants, &options()).unwrap();
        let template = result.template.as_deref().unwrap();
        assert_eq!(template.matches("package example;").count(), 1);
        assert_eq!(template.matches("public class Example {}").count(), 1);
        assert!(template.contains("{% when '1.19.2', '1.19.4' %}"));
        assert_eq!(result.statistics.shared_lines, 2);
        assert_eq!(result.statistics.conditional_regions, 1);
        for variant in &variants {
            assert_eq!(
                render(&result, variant).unwrap().as_bytes(),
                variant.source.as_ref().unwrap()
            );
        }
        let reversed: Vec<_> = variants.into_iter().rev().collect();
        assert_eq!(
            consolidate(&reversed, &options()).unwrap().template,
            result.template
        );
    }

    #[test]
    fn reviewed_feature_changes_are_not_environment_or_snapshot_dispatch() {
        let base = "class Example {\n    common();\n}\n";
        let enabled = "class Example {\n    common();\n    packet();\n}\n";
        let variants = vec![
            variant("released-old", "1.19.2", false, Some(base)),
            variant("dev-old", "1.19.2", true, Some(enabled)),
            variant("released-new", "26.1.2", false, Some(base)),
            variant("dev-new", "26.1.2", true, Some(enabled)),
        ];
        let result = consolidate(&variants, &options()).unwrap();
        let template = result.template.as_deref().unwrap();
        assert!(template.contains("{% if features.packet_computation %}"));
        assert!(!template.contains("case minecraft_version"));
        for forbidden in [
            "environment",
            "preset",
            "projection_key",
            "released-old",
            "dev-old",
        ] {
            assert!(!template.contains(forbidden));
        }
        assert!(consolidate(&variants, &ConsolidationOptions::default()).is_err());
    }

    #[test]
    fn one_common_edit_reaches_every_present_context() {
        let variants = vec![
            variant(
                "a",
                "1.19.2",
                false,
                Some("class Example {\nold();\ncommon();\n}\n"),
            ),
            variant(
                "b",
                "26.1.2",
                false,
                Some("class Example {\nnewer();\ncommon();\n}\n"),
            ),
        ];
        let mut result = consolidate(&variants, &options()).unwrap();
        result.template = result
            .template
            .map(|text| text.replace("common();", "commonUpdated();"));
        for variant in &variants {
            let rendered = render(&result, variant).unwrap();
            assert!(rendered.contains("commonUpdated();"));
            assert!(!rendered.contains("common();"));
        }
    }

    #[test]
    fn absence_is_explicit_membership_not_an_empty_source_dispatch() {
        let variants = vec![
            variant("disabled", "1.19.2", false, None),
            variant("enabled", "1.19.2", true, Some("class Packet {}\n")),
        ];
        let result = consolidate(&variants, &options()).unwrap();
        assert_eq!(result.template.as_deref(), Some("class Packet {}\n"));
        assert_eq!(render(&result, &variants[0]), None);
        assert_eq!(
            render(&result, &variants[1]).as_deref(),
            Some("class Packet {}\n")
        );
        assert!(matches!(
            result.membership.decision,
            SelectionRule::Feature { .. }
        ));
        let absent = consolidate(&[variant("absent", "1.19.2", false, None)], &options()).unwrap();
        assert!(absent.template.is_none());
        assert_eq!(absent.membership.decision, SelectionRule::Value(false));
    }

    #[test]
    fn crlf_braces_literal_directives_and_shared_unterminated_tail_survive() {
        let variants = vec![
            variant(
                "a",
                "1.19.2",
                false,
                Some(
                    "class Example {\r\nold();\r\nint[][] a = {{1, 2}};\r\n{% literal text %}\r\n}",
                ),
            ),
            variant(
                "b",
                "26.1.2",
                false,
                Some(
                    "class Example {\r\nnewer();\r\nint[][] a = {{1, 2}};\r\n{% literal text %}\r\n}",
                ),
            ),
        ];
        let result = consolidate(&variants, &options()).unwrap();
        for variant in &variants {
            let rendered = render(&result, variant).unwrap();
            assert_eq!(rendered.as_bytes(), variant.source.as_ref().unwrap());
            assert!(rendered.ends_with('}'));
        }
    }

    #[test]
    fn repeated_lines_reconstruct_without_whole_file_copies() {
        let variants = vec![
            variant(
                "a",
                "1.19.2",
                false,
                Some("class Example {\n}\nclass Second {\n}\n"),
            ),
            variant(
                "b",
                "26.1.2",
                false,
                Some("class Example {\nnewer();\n}\nclass Second {\nchanged();\n}\n"),
            ),
        ];
        let result = consolidate(&variants, &options()).unwrap();
        assert_eq!(
            result
                .template
                .as_deref()
                .unwrap()
                .matches("class Second {")
                .count(),
            1
        );
        assert!(result.statistics.shared_lines >= 3);
        for variant in &variants {
            assert_eq!(
                render(&result, variant).unwrap().as_bytes(),
                variant.source.as_ref().unwrap()
            );
        }
    }

    #[test]
    fn conflicting_and_ambiguous_selectors_fail_closed() {
        let variants = vec![
            variant("a", "1.19.2", false, Some("class Example {}\n")),
            variant("b", "1.19.2", false, Some("class Different {}\n")),
        ];
        assert!(
            consolidate(&variants, &options())
                .unwrap_err()
                .to_string()
                .contains("identical selectors")
        );
        let mut variants = vec![
            variant("off", "1.19.2", false, Some("class Example {\n}\n")),
            variant(
                "on",
                "1.19.2",
                true,
                Some("class Example {\npacket();\n}\n"),
            ),
        ];
        for variant in &mut variants {
            variant.features.insert(
                "other_feature".to_owned(),
                variant.features["packet_computation"],
            );
        }
        let owners = ConsolidationOptions {
            allowed_features: BTreeSet::from([
                "packet_computation".to_owned(),
                "other_feature".to_owned(),
            ]),
        };
        assert!(
            consolidate(&variants, &owners)
                .unwrap_err()
                .to_string()
                .contains("ambiguous correlated")
        );
        consolidate(&variants, &options()).unwrap();
    }

    #[test]
    fn changed_unterminated_tail_and_unshared_dispatch_are_rejected() {
        let variants = vec![
            variant("a", "1.19.2", false, Some("shared();\nold();")),
            variant("b", "26.1.2", false, Some("shared();\nnewer();")),
        ];
        assert!(
            consolidate(&variants, &options())
                .unwrap_err()
                .to_string()
                .contains("final newline")
        );
        let variants = vec![
            variant("a", "1.19.2", false, Some("old();\n")),
            variant("b", "26.1.2", false, Some("newer();\n")),
        ];
        assert!(
            consolidate(&variants, &options())
                .unwrap_err()
                .to_string()
                .contains("whole-file dispatch")
        );
    }

    #[test]
    fn unknown_contexts_are_not_nearest_variant_fallbacks() {
        let variants = vec![
            variant("a", "1.19.2", false, Some("class Example {\nold();\n}\n")),
            variant("b", "26.1.2", false, Some("class Example {\nnewer();\n}\n")),
        ];
        let result = consolidate(&variants, &options()).unwrap();
        assert!(
            result
                .membership
                .includes("1.20.1", &variants[0].features)
                .is_err()
        );
        let mut unseen = variants[0].features.clone();
        unseen.insert("packet_computation".to_owned(), true);
        assert!(result.membership.includes("1.19.2", &unseen).is_err());
        let identity = consolidate(
            &[variant(
                "identity",
                "1.19.2",
                false,
                Some("class Example {}\n"),
            )],
            &options(),
        )
        .unwrap();
        assert!(identity.membership.includes("1.19.2", &unseen).unwrap());
        unseen.insert("unknown_feature".to_owned(), true);
        assert!(identity.membership.includes("1.19.2", &unseen).is_err());
    }

    #[test]
    fn reviewed_regions_assign_distinct_owners_despite_correlated_witness_flags() {
        let mut variants = vec![
            variant(
                "off",
                "1.19.2",
                false,
                Some("class Example {\ncommon();\nbetween();\n}\n"),
            ),
            variant(
                "on",
                "1.19.2",
                true,
                Some("class Example {\ncommon();\npacket();\nbetween();\nconsole();\n}\n"),
            ),
        ];
        for variant in &mut variants {
            variant.features.insert(
                "console_tools".to_owned(),
                variant.features["packet_computation"],
            );
        }
        let broad = ConsolidationOptions {
            allowed_features: BTreeSet::from([
                "packet_computation".to_owned(),
                "console_tools".to_owned(),
            ]),
        };
        assert!(consolidate(&variants, &broad).is_err());
        let report = preview_regions(&variants).unwrap();
        assert_eq!(report.regions.len(), 2);
        let mut decisions = BTreeMap::new();
        for region in &report.regions {
            assert!(region.requires_feature_ownership);
            assert_eq!(region.varying_features.len(), 2);
            assert!(region.before_anchor_sha256.is_some());
            assert!(region.after_anchor_sha256.is_some());
            let owner = if region.alternatives.iter().any(|alternative| {
                matches!(&alternative.payload, RegionPayload::Text(text) if text.contains("packet();"))
            }) { "packet_computation" } else { "console_tools" };
            decisions.insert(
                region.id.clone(),
                ReviewedRegionOwnership {
                    allowed_features: BTreeSet::from([owner.to_owned()]),
                    ..ReviewedRegionOwnership::default()
                },
            );
        }
        let result = consolidate_reviewed(&variants, &report, &decisions).unwrap();
        let template = result.template.as_deref().unwrap();
        assert!(template.contains("{% if features.packet_computation %}"));
        assert!(template.contains("{% if features.console_tools %}"));
        for variant in &variants {
            assert_eq!(
                render(&result, variant).unwrap().as_bytes(),
                variant.source.as_ref().unwrap()
            );
        }
    }

    #[test]
    fn reviewed_membership_owner_is_independent_from_content_owner() {
        let mut variants = vec![
            variant("absent", "1.19.2", false, None),
            variant("base", "1.19.2", true, Some("class Example {\n}\n")),
            variant(
                "console",
                "1.19.2",
                true,
                Some("class Example {\nconsole();\n}\n"),
            ),
        ];
        for variant in &mut variants {
            variant
                .features
                .insert("console_tools".to_owned(), variant.id == "console");
        }
        let report = preview_regions(&variants).unwrap();
        assert!(matches!(report.regions[0].kind, RegionKind::Membership));
        let choices: BTreeMap<_, _> = report
            .regions
            .iter()
            .map(|region| {
                let owner = if matches!(region.kind, RegionKind::Membership) {
                    "packet_computation"
                } else {
                    "console_tools"
                };
                (
                    region.id.clone(),
                    ReviewedRegionOwnership {
                        allowed_features: BTreeSet::from([owner.to_owned()]),
                        ..ReviewedRegionOwnership::default()
                    },
                )
            })
            .collect();
        let result = consolidate_reviewed(&variants, &report, &choices).unwrap();
        assert!(
            matches!(&result.membership.decision, SelectionRule::Feature { feature, .. } if feature == "packet_computation")
        );
        assert!(
            !result
                .template
                .as_deref()
                .unwrap()
                .contains("features.packet_computation")
        );
        assert_eq!(render(&result, &variants[0]), None);
    }

    fn split_fixture() -> (
        Vec<SourceVariant>,
        RegionReviewReport,
        BTreeMap<String, ReviewedRegionOwnership>,
    ) {
        let mut variants = vec![
            variant("off", "1.19.2", false, Some("class Example {\n}\n")),
            variant(
                "on",
                "1.19.2",
                true,
                Some("class Example {\npacket();\nconsole();\n}\n"),
            ),
        ];
        for variant in &mut variants {
            variant.features.insert(
                "console_tools".to_owned(),
                variant.features["packet_computation"],
            );
        }
        let report = preview_regions(&variants).unwrap();
        assert_eq!(report.regions.len(), 1);
        let choices = BTreeMap::from([(
            report.regions[0].id.clone(),
            ReviewedRegionOwnership {
                allowed_features: BTreeSet::new(),
                subregions: vec![
                    ReviewedSubregion {
                        allowed_features: BTreeSet::from(["packet_computation".to_owned()]),
                        witness_lines: BTreeMap::from([
                            ("off".to_owned(), ReviewedLineRange { start: 0, end: 0 }),
                            ("on".to_owned(), ReviewedLineRange { start: 0, end: 1 }),
                        ]),
                    },
                    ReviewedSubregion {
                        allowed_features: BTreeSet::from(["console_tools".to_owned()]),
                        witness_lines: BTreeMap::from([
                            ("off".to_owned(), ReviewedLineRange { start: 0, end: 0 }),
                            ("on".to_owned(), ReviewedLineRange { start: 1, end: 2 }),
                        ]),
                    },
                ],
            },
        )]);
        (variants, report, choices)
    }

    #[test]
    fn explicit_subregions_partition_adjacent_features_without_a_catchall() {
        let (variants, report, choices) = split_fixture();
        let result = consolidate_reviewed(&variants, &report, &choices).unwrap();
        assert_eq!(result.statistics.conditional_regions, 2);
        let template = result.template.as_deref().unwrap();
        assert!(template.contains("features.packet_computation"));
        assert!(template.contains("features.console_tools"));
        for variant in &variants {
            assert_eq!(
                render(&result, variant).unwrap().as_bytes(),
                variant.source.as_ref().unwrap()
            );
        }
    }

    #[test]
    fn stale_or_modified_review_and_missing_extra_decisions_are_rejected() {
        let variants = vec![
            variant("a", "1.19.2", false, Some("class Example {\nold();\n}\n")),
            variant("b", "26.1.2", false, Some("class Example {\nnewer();\n}\n")),
        ];
        let report = preview_regions(&variants).unwrap();
        let choices = decisions(&report, "");
        consolidate_reviewed(&variants, &report, &choices).unwrap();
        let reversed: Vec<_> = variants.iter().cloned().rev().collect();
        assert_eq!(preview_regions(&reversed).unwrap(), report);
        let mut changed = variants.clone();
        changed[0].source = Some(b"class Example {\nchanged();\n}\n".to_vec());
        assert!(
            consolidate_reviewed(&changed, &report, &choices)
                .unwrap_err()
                .to_string()
                .contains("stale or modified")
        );
        let mut forged = report.clone();
        forged.regions[0].alternatives[0].payload_sha256 = "wrong".to_owned();
        assert!(consolidate_reviewed(&variants, &forged, &choices).is_err());
        assert!(consolidate_reviewed(&variants, &report, &BTreeMap::new()).is_err());
        let mut extra = choices;
        extra.insert(
            "unknown-region".to_owned(),
            ReviewedRegionOwnership::default(),
        );
        assert!(consolidate_reviewed(&variants, &report, &extra).is_err());
    }

    #[test]
    fn invalid_subregion_ranges_and_parent_catchall_are_rejected() {
        let (variants, report, original) = split_fixture();
        for failure in 0..5 {
            let mut choices = original.clone();
            let owner = choices.values_mut().next().unwrap();
            match failure {
                0 => {
                    owner.subregions[1]
                        .witness_lines
                        .get_mut("on")
                        .unwrap()
                        .start = 0;
                }
                1 => {
                    owner.subregions[1].witness_lines.get_mut("on").unwrap().end = 3;
                }
                2 => {
                    owner.subregions.pop();
                }
                3 => {
                    owner.subregions[0].witness_lines.remove("off");
                }
                4 => {
                    owner
                        .allowed_features
                        .insert("packet_computation".to_owned());
                }
                _ => unreachable!(),
            }
            assert!(
                consolidate_reviewed(&variants, &report, &choices).is_err(),
                "accepted invalid split {failure}"
            );
        }
    }

    #[test]
    fn duplicate_equivalent_selector_ids_do_not_make_review_input_order_sensitive() {
        let variants = vec![
            variant("z", "1.19.2", false, Some("class Example {\nold();\n}\n")),
            variant("a", "1.19.2", false, Some("class Example {\nold();\n}\n")),
            variant("b", "26.1.2", false, Some("class Example {\nnewer();\n}\n")),
        ];
        let report = preview_regions(&variants).unwrap();
        let reversed: Vec<_> = variants.iter().cloned().rev().collect();
        assert_eq!(preview_regions(&reversed).unwrap(), report);
        let choices = decisions(&report, "");
        assert_eq!(
            consolidate_reviewed(&variants, &report, &choices)
                .unwrap()
                .template,
            consolidate_reviewed(&reversed, &report, &choices)
                .unwrap()
                .template
        );
    }

    #[test]
    fn portable_review_roundtrip_preserves_fingerprints_and_actual_reconstruction() {
        let (variants, report, choices) = split_fixture();
        let report_json = facet_json::to_string_pretty(&report).unwrap();
        let decoded_report: RegionReviewReport = facet_json::from_str(&report_json).unwrap();
        let choices_json = facet_json::to_string_pretty(&choices).unwrap();
        let decoded_choices: BTreeMap<String, ReviewedRegionOwnership> =
            facet_json::from_str(&choices_json).unwrap();
        assert_eq!(decoded_report, report);
        assert_eq!(decoded_choices, choices);
        assert_eq!(
            decoded_report.input_fingerprint,
            input_fingerprint(&variants)
        );
        let result = consolidate_reviewed(&variants, &decoded_report, &decoded_choices).unwrap();
        for variant in &variants {
            assert_eq!(
                render(&result, variant).unwrap().as_bytes(),
                variant.source.as_ref().unwrap()
            );
        }
        assert!(
            facet_json::from_str::<RegionReviewReport>(&report_json.replacen(
                '{',
                "{\"unexpected\":true,",
                1
            ))
            .is_err()
        );
        assert!(
            facet_json::from_str::<ReviewedRegionOwnership>(
                r#"{"allowed_features":[],"subregions":[],"unexpected":true}"#
            )
            .is_err()
        );
    }

    #[test]
    fn portable_region_kinds_and_payloads_have_explicit_stable_tags() {
        let membership: RegionKind = facet_json::from_str(r#"{"kind":"membership"}"#).unwrap();
        let content: RegionKind =
            facet_json::from_str(r#"{"kind":"content","ordinal":7}"#).unwrap();
        assert_eq!(membership, RegionKind::Membership);
        assert_eq!(content, RegionKind::Content { ordinal: 7 });
        let presence: RegionPayload =
            facet_json::from_str(r#"{"kind":"membership","value":true}"#).unwrap();
        let text: RegionPayload =
            facet_json::from_str(r#"{"kind":"text","value":"line\n"}"#).unwrap();
        assert_eq!(presence, RegionPayload::Membership(true));
        assert_eq!(text, RegionPayload::Text("line\n".to_owned()));
        for payload in [presence, text] {
            let encoded = facet_json::to_string(&payload).unwrap();
            assert_eq!(
                facet_json::from_str::<RegionPayload>(&encoded).unwrap(),
                payload
            );
            assert!(encoded.contains("\"kind\""));
            assert!(encoded.contains("\"value\""));
        }
    }
}
