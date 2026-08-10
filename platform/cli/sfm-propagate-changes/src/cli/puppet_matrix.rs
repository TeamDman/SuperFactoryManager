use crate::branch_targets::WorktreeTarget;
use crate::branch_targets::select_required_worktree_targets;
use crate::cancellation::CancellationToken;
use crate::cli::jar::BranchSelector;
use crate::cli::jar::JarBuildOptionsArgs;
use crate::cli::run::invoke_game_puppet;
use crate::cli::run::normalize_game_puppet_game_test;
use crate::jar_build::ErrorAction;
use crate::jar_build::GamePuppetPreviewManifest;
use crate::jar_build::GamePuppetPreviewVariantObservation;
use crate::jar_build::GamePuppetPreviewViewportCrop;
use crate::jar_build::Parallelism;
use crate::jar_build::game_puppet_preview_artifact_root;
use crate::jar_build::hash::ContentHash;
use crate::jar_build::hash::ContentHashAlgorithm;
use crate::jar_build::png_dimensions;
use crate::terminal_output::stdout_line;
use chrono::Local;
use eyre::Context;
use facet::Facet;
use figue as args;
use std::collections::BTreeSet;
use std::fmt::Write as _;
use std::fs;
use std::path::Component;
use std::path::Path;
use std::path::PathBuf;

/// Run a game puppet on multiple version worktrees and collect validated copies.
#[derive(Facet, Debug)]
pub struct PuppetMatrixArgs {
    /// Build and launch options. The branch selector may choose multiple core version worktrees.
    #[facet(flatten)]
    pub options: JarBuildOptionsArgs,
    /// Puppet selector. Supports unqualified names, `*`, `?`, and comma-separated selectors.
    #[facet(args::positional)]
    pub puppet: String,
    /// Exact SFM `GameTest` id supplied to a parameterized puppet. Accepts `sfm:<name>` or `<name>`.
    #[facet(default, args::named)]
    pub game_test: Option<String>,
    /// Window width used for native screenshot captures.
    #[facet(default, args::named)]
    pub width: Option<u16>,
    /// Window height used for native screenshot captures.
    #[facet(default, args::named)]
    pub height: Option<u16>,
    /// Viewport variants per branch target: declared, preferred, or WIDTHxHEIGHT@auto|SCALE.
    #[facet(default = "declared", args::named)]
    pub variant: String,
    /// Mute Minecraft audio during each puppet run by default. Use `--no-mute` to hear it.
    #[facet(default = true, args::named)]
    pub mute: bool,
}

#[derive(Debug, Facet)]
struct PuppetMatrixManifest {
    #[facet(rename = "schemaVersion")]
    schema_version: u32,
    #[facet(rename = "createdAt")]
    created_at: String,
    #[facet(rename = "puppetSelection")]
    puppet_selection: String,
    #[facet(rename = "gameTest")]
    game_test: Option<String>,
    targets: Vec<PuppetMatrixTarget>,
}

#[derive(Clone, Debug, Facet)]
struct PuppetMatrixTarget {
    branch: String,
    #[facet(rename = "minecraftVersion")]
    minecraft_version: String,
    result: String,
    #[facet(rename = "sourceArtifactRoot")]
    source_artifact_root: String,
    #[facet(rename = "sourceManifest")]
    source_manifest: Option<String>,
    #[facet(rename = "diagnosticLog")]
    diagnostic_log: String,
    #[facet(rename = "diagnosticLogExists")]
    diagnostic_log_exists: bool,
    error: Option<String>,
    captures: Vec<PuppetMatrixCapture>,
}

#[derive(Clone, Debug, Facet)]
struct PuppetMatrixCapture {
    puppet: String,
    figure: u32,
    capture: String,
    #[facet(default)]
    variant: String,
    #[facet(default)]
    viewport: Option<GamePuppetPreviewVariantObservation>,
    #[facet(rename = "sourcePath")]
    source_path: String,
    #[facet(rename = "matrixPath")]
    matrix_path: String,
    width: u32,
    height: u32,
    #[facet(rename = "captionPixelHeight")]
    #[facet(default)]
    caption_pixel_height: u32,
    #[facet(rename = "viewportCrop")]
    #[facet(default)]
    viewport_crop: Option<GamePuppetPreviewViewportCrop>,
    hash: ContentHash,
}

impl PuppetMatrixArgs {
    /// # Errors
    ///
    /// Returns an error if target selection, preview execution, artifact validation, or matrix
    /// publication fails. A matrix manifest is written before reporting per-target failures.
    pub fn invoke(self, cancellation_token: &CancellationToken) -> eyre::Result<()> {
        if self.options.plan_json.is_some() {
            eyre::bail!(
                "puppet matrix does not support --plan-json because it launches one exact branch per matrix target"
            );
        }
        if self.options.dry_run {
            eyre::bail!(
                "puppet matrix does not support --dry-run because it requires captured preview artifacts"
            );
        }
        validate_calibrated_parallelism(Parallelism::from_cli(self.options.parallel)?)?;

        let requested_selector = self.options.branch.clone();
        let query = requested_selector.into_query()?;
        let mut targets = select_required_worktree_targets(&query)?;
        if let Some(feature_target) = targets.iter().find(|target| !target.core) {
            eyre::bail!(
                "puppet matrix only accepts normal Minecraft version branches; '{}' is a feature worktree",
                feature_target.branch
            );
        }
        targets.sort_by(|left, right| left.mc_version.cmp(&right.mc_version));

        let expected_selection = self.puppet.trim().to_string();
        if expected_selection.is_empty() {
            eyre::bail!("puppet selector must not be empty");
        }
        let expected_game_test = normalize_game_puppet_game_test(self.game_test.clone())?;
        let matrix_root = create_matrix_root(&targets[0], &expected_selection)?;
        let error_action = self.options.error_action;
        let mut matrix_targets = Vec::with_capacity(targets.len());

        for target in targets {
            cancellation_token.bail_if_cancelled()?;
            let result = run_matrix_target(
                &self.options,
                &target,
                &expected_selection,
                self.game_test.clone(),
                self.width,
                self.height,
                &self.variant,
                self.mute,
                expected_game_test.as_deref(),
                cancellation_token.clone(),
            );
            match result {
                Ok(matrix_target) => {
                    match copy_target_preview(&matrix_root, &matrix_target, &target) {
                        Ok(()) => matrix_targets.push(matrix_target),
                        Err(error) => {
                            matrix_targets.push(failed_target(&target, error.to_string()));
                            if error_action == ErrorAction::Bail {
                                break;
                            }
                        }
                    }
                }
                Err(error) => {
                    matrix_targets.push(failed_target(&target, error.to_string()));
                    if error_action == ErrorAction::Bail {
                        break;
                    }
                }
            }
        }

        let manifest_path = write_matrix_outputs(
            &matrix_root,
            &expected_selection,
            expected_game_test,
            &matrix_targets,
        )?;
        stdout_line(matrix_root.display())?;

        let failures = matrix_targets
            .iter()
            .filter(|target| target.result != "succeeded")
            .count();
        if failures > 0 {
            eyre::bail!(
                "puppet matrix completed with {failures} failed target(s); see {}",
                manifest_path.display()
            );
        }
        Ok(())
    }
}

fn validate_calibrated_parallelism(parallel: Parallelism) -> eyre::Result<()> {
    match parallel {
        Parallelism::Sequential | Parallelism::Parallel { limit: 1 } => Ok(()),
        Parallelism::Parallel { limit } => eyre::bail!(
            "puppet matrix --parallel {limit} is not enabled until Phase 3.2 records two-client calibration evidence; use --parallel 1"
        ),
    }
}

#[expect(
    clippy::too_many_arguments,
    reason = "A target run keeps the matrix inputs visible at the launcher boundary."
)]
fn run_matrix_target(
    template_options: &JarBuildOptionsArgs,
    target: &WorktreeTarget,
    puppet_selection: &str,
    game_test: Option<String>,
    width: Option<u16>,
    height: Option<u16>,
    viewport_selection: &str,
    mute: bool,
    expected_game_test: Option<&str>,
    cancellation_token: CancellationToken,
) -> eyre::Result<PuppetMatrixTarget> {
    let mut target_options = template_options.clone();
    target_options.branch = BranchSelector(target.branch.to_string());
    target_options.parallel = None;
    target_options.error_action = ErrorAction::Bail;
    target_options.plan_json = None;
    invoke_game_puppet(
        target_options,
        puppet_selection,
        game_test,
        width,
        height,
        viewport_selection,
        mute,
        None,
        cancellation_token,
    )?;
    collect_target_preview(target, puppet_selection, expected_game_test)
}

fn collect_target_preview(
    target: &WorktreeTarget,
    expected_selection: &str,
    expected_game_test: Option<&str>,
) -> eyre::Result<PuppetMatrixTarget> {
    let source_artifact_root = game_puppet_preview_artifact_root(target.worktree_path.as_path());
    let source_manifest_path = source_artifact_root.join("preview-manifest.json");
    let source_manifest_text = fs::read_to_string(&source_manifest_path)
        .wrap_err_with(|| format!("Failed to read {}", source_manifest_path.display()))?;
    let source_manifest: GamePuppetPreviewManifest = facet_json::from_str(&source_manifest_text)
        .wrap_err_with(|| format!("Failed to parse {}", source_manifest_path.display()))?;
    validate_source_manifest(
        target,
        &source_manifest,
        expected_selection,
        expected_game_test,
    )?;

    let mut used_figures = BTreeSet::new();
    let mut validated = Vec::with_capacity(source_manifest.captures.len());
    for capture in &source_manifest.captures {
        if capture.figure == 0 || !used_figures.insert((capture.figure, capture.variant.clone())) {
            eyre::bail!(
                "Preview manifest for branch '{}' has duplicate or zero figure {}",
                target.branch,
                capture.figure
            );
        }
        let source_relative_path = safe_preview_relative_path(&capture.path)?;
        let source_path = source_artifact_root.join(&source_relative_path);
        let bytes = fs::read(&source_path)
            .wrap_err_with(|| format!("Failed to read {}", source_path.display()))?;
        let Some((width, height)) = png_dimensions(&bytes) else {
            eyre::bail!(
                "Preview capture was not a valid PNG: {}",
                source_path.display()
            );
        };
        if (width, height) != (capture.width, capture.height) {
            eyre::bail!(
                "Preview capture dimensions disagree with its manifest for {}: PNG is {width}x{height}, manifest is {}x{}",
                source_path.display(),
                capture.width,
                capture.height
            );
        }
        if source_manifest.capture_profile.composition == "captioned-native-main-render-target" {
            validate_captioned_preview_geometry(
                capture.viewport.as_ref(),
                capture.viewport_crop.as_ref(),
                capture.caption_pixel_height,
                width,
                height,
                &source_path,
            )?;
        }
        let actual_hash = ContentHash::from_bytes(&bytes, ContentHashAlgorithm::Blake3);
        if capture.hash.algorithm != ContentHashAlgorithm::Blake3 || capture.hash != actual_hash {
            eyre::bail!(
                "Preview capture BLAKE3 hash disagrees with its manifest for {}",
                source_path.display()
            );
        }

        let matrix_relative_path =
            PathBuf::from(target.branch.as_str()).join(&source_relative_path);
        validated.push(PuppetMatrixCapture {
            puppet: capture.puppet.clone(),
            figure: capture.figure,
            capture: capture.capture.clone(),
            variant: capture.variant.clone(),
            viewport: capture.viewport.clone(),
            source_path: portable_path(&source_relative_path),
            matrix_path: portable_path(&matrix_relative_path),
            width,
            height,
            caption_pixel_height: capture.caption_pixel_height,
            viewport_crop: capture.viewport_crop.clone(),
            hash: actual_hash,
        });
    }
    if validated.is_empty() {
        eyre::bail!(
            "Preview manifest for branch '{}' has no captures",
            target.branch
        );
    }

    Ok(PuppetMatrixTarget {
        branch: target.branch.to_string(),
        minecraft_version: source_manifest.minecraft_version,
        result: "succeeded".to_string(),
        source_artifact_root: source_artifact_root.display().to_string(),
        source_manifest: Some(portable_path(
            &PathBuf::from(target.branch.as_str()).join("preview-manifest.json"),
        )),
        diagnostic_log: target_diagnostic_log(target).display().to_string(),
        diagnostic_log_exists: target_diagnostic_log(target).is_file(),
        error: None,
        captures: validated,
    })
}

fn validate_captioned_preview_geometry(
    viewport: Option<&GamePuppetPreviewVariantObservation>,
    crop: Option<&GamePuppetPreviewViewportCrop>,
    caption_pixel_height: u32,
    width: u32,
    height: u32,
    source_path: &Path,
) -> eyre::Result<()> {
    let viewport = viewport.ok_or_else(|| {
        eyre::eyre!(
            "Captioned preview omitted viewport metadata: {}",
            source_path.display()
        )
    })?;
    let crop = crop.ok_or_else(|| {
        eyre::eyre!(
            "Captioned preview omitted its native viewport crop: {}",
            source_path.display()
        )
    })?;
    let framebuffer_width = u32::from(viewport.framebuffer_width);
    let framebuffer_height = u32::from(viewport.framebuffer_height);
    if caption_pixel_height == 0
        || caption_pixel_height > 1_024
        || crop.x != 0
        || crop.y != caption_pixel_height
        || crop.width != framebuffer_width
        || crop.height != framebuffer_height
        || width != framebuffer_width
        || height != framebuffer_height + caption_pixel_height
    {
        eyre::bail!(
            "Captioned preview native viewport crop is inconsistent for {}",
            source_path.display()
        );
    }
    Ok(())
}

fn copy_target_preview(
    matrix_root: &Path,
    target: &PuppetMatrixTarget,
    source_target: &WorktreeTarget,
) -> eyre::Result<()> {
    if target.result != "succeeded" {
        return Ok(());
    }
    let source_artifact_root =
        game_puppet_preview_artifact_root(source_target.worktree_path.as_path());
    let destination_root = matrix_root.join(&target.branch);
    if destination_root.exists() {
        eyre::bail!(
            "Matrix destination already exists for branch '{}': {}",
            target.branch,
            destination_root.display()
        );
    }

    fs::create_dir_all(&destination_root)
        .wrap_err_with(|| format!("Failed to create {}", destination_root.display()))?;
    let source_manifest = source_artifact_root.join("preview-manifest.json");
    fs::copy(
        &source_manifest,
        destination_root.join("preview-manifest.json"),
    )
    .wrap_err_with(|| {
        format!(
            "Failed to copy source manifest {} into {}",
            source_manifest.display(),
            destination_root.display()
        )
    })?;
    for capture in &target.captures {
        let source_relative_path = safe_preview_relative_path(&capture.source_path)?;
        let source_path = source_artifact_root.join(&source_relative_path);
        let destination = matrix_root.join(&capture.matrix_path);
        let Some(parent) = destination.parent() else {
            eyre::bail!(
                "Matrix destination has no parent: {}",
                destination.display()
            );
        };
        fs::create_dir_all(parent)
            .wrap_err_with(|| format!("Failed to create {}", parent.display()))?;
        fs::copy(&source_path, &destination).wrap_err_with(|| {
            format!(
                "Failed to copy preview capture {} to {}",
                source_path.display(),
                destination.display()
            )
        })?;
    }
    Ok(())
}

fn validate_source_manifest(
    target: &WorktreeTarget,
    manifest: &GamePuppetPreviewManifest,
    expected_selection: &str,
    expected_game_test: Option<&str>,
) -> eyre::Result<()> {
    if manifest.branch != target.branch.as_str() {
        eyre::bail!(
            "Preview manifest branch '{}' does not match selected target '{}'",
            manifest.branch,
            target.branch
        );
    }
    let Some(expected_version) = target.mc_version.as_ref() else {
        eyre::bail!("Matrix target '{}' has no Minecraft version", target.branch);
    };
    if manifest.minecraft_version != expected_version.as_str() {
        eyre::bail!(
            "Preview manifest Minecraft version '{}' does not match target '{}'",
            manifest.minecraft_version,
            expected_version
        );
    }
    if manifest.puppet_selection != expected_selection {
        eyre::bail!(
            "Preview manifest puppet selection '{}' does not match requested selection '{expected_selection}'",
            manifest.puppet_selection
        );
    }
    if manifest.game_test.as_deref() != expected_game_test {
        eyre::bail!(
            "Preview manifest game test {:?} does not match requested game test {expected_game_test:?}",
            manifest.game_test.as_deref()
        );
    }
    Ok(())
}

fn failed_target(target: &WorktreeTarget, error: String) -> PuppetMatrixTarget {
    let source_artifact_root = game_puppet_preview_artifact_root(target.worktree_path.as_path());
    let diagnostic_log = target_diagnostic_log(target);
    PuppetMatrixTarget {
        branch: target.branch.to_string(),
        minecraft_version: target
            .mc_version
            .as_ref()
            .map_or_else(|| "<unknown>".to_string(), ToString::to_string),
        result: "failed".to_string(),
        source_artifact_root: source_artifact_root.display().to_string(),
        source_manifest: None,
        diagnostic_log_exists: diagnostic_log.is_file(),
        diagnostic_log: diagnostic_log.display().to_string(),
        error: Some(error),
        captures: Vec::new(),
    }
}

fn create_matrix_root(anchor: &WorktreeTarget, puppet_selection: &str) -> eyre::Result<PathBuf> {
    let preview_root = game_puppet_preview_artifact_root(anchor.worktree_path.as_path());
    let artifacts_root = preview_root.parent().ok_or_else(|| {
        eyre::eyre!(
            "Preview artifact root has no parent: {}",
            preview_root.display()
        )
    })?;
    let matrix_parent = artifacts_root.join("game-test-preview-matrices");
    fs::create_dir_all(&matrix_parent)
        .wrap_err_with(|| format!("Failed to create {}", matrix_parent.display()))?;

    let base_name = format!(
        "{}-{}",
        safe_matrix_name(puppet_selection),
        Local::now().format("%Y%m%d-%H%M%S")
    );
    for index in 0..1000_u32 {
        let name = if index == 0 {
            base_name.clone()
        } else {
            format!("{base_name}-{index:03}")
        };
        let candidate = matrix_parent.join(name);
        match fs::create_dir(&candidate) {
            Ok(()) => return Ok(candidate),
            Err(error) if error.kind() == std::io::ErrorKind::AlreadyExists => {}
            Err(error) => {
                return Err(error)
                    .wrap_err_with(|| format!("Failed to create {}", candidate.display()));
            }
        }
    }
    eyre::bail!(
        "Could not allocate a unique matrix directory under {}",
        matrix_parent.display()
    )
}

fn write_matrix_outputs(
    matrix_root: &Path,
    puppet_selection: &str,
    game_test: Option<String>,
    targets: &[PuppetMatrixTarget],
) -> eyre::Result<PathBuf> {
    let manifest = PuppetMatrixManifest {
        schema_version: 1,
        created_at: Local::now().to_rfc3339(),
        puppet_selection: puppet_selection.to_string(),
        game_test,
        targets: targets.to_vec(),
    };
    let manifest_path = matrix_root.join("matrix-manifest.json");
    fs::write(&manifest_path, facet_json::to_string_pretty(&manifest)?)
        .wrap_err_with(|| format!("Failed to write {}", manifest_path.display()))?;
    let index_path = matrix_root.join("index.html");
    fs::write(&index_path, render_matrix_index(&manifest))
        .wrap_err_with(|| format!("Failed to write {}", index_path.display()))?;
    Ok(manifest_path)
}

fn render_matrix_index(manifest: &PuppetMatrixManifest) -> String {
    let figures = manifest
        .targets
        .iter()
        .flat_map(|target| {
            target
                .captures
                .iter()
                .map(|capture| (capture.figure, capture.variant.clone()))
        })
        .collect::<BTreeSet<_>>();
    let mut html = String::from(
        "<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\"><title>SFM puppet preview matrix</title><style>body{font-family:system-ui,sans-serif;margin:2rem}table{border-collapse:collapse}th,td{border:1px solid #bbb;padding:.5rem;vertical-align:top}img{max-width:320px;height:auto}code{white-space:pre-wrap}.failed{color:#a00}</style></head><body>",
    );
    let _ = write!(
        html,
        "<h1>SFM puppet preview matrix</h1><p>Selection: <code>{}</code></p><table><thead><tr><th>Branch</th><th>Result</th>",
        html_escape(&manifest.puppet_selection)
    );
    for (figure, variant) in &figures {
        let variant = if variant.is_empty() {
            "singleton"
        } else {
            variant
        };
        let _ = write!(
            html,
            "<th>Figure {figure}<br><code>{}</code></th>",
            html_escape(variant)
        );
    }
    html.push_str("</tr></thead><tbody>");
    for target in &manifest.targets {
        let result_class = if target.result == "succeeded" {
            ""
        } else {
            " class=\"failed\""
        };
        let _ = write!(
            html,
            "<tr><th>{}</th><td{}>{}",
            html_escape(&target.branch),
            result_class,
            html_escape(&target.result)
        );
        if let Some(error) = target.error.as_deref() {
            let _ = write!(html, "<br><code>{}</code>", html_escape(error));
        }
        let _ = write!(
            html,
            "<br><code>{}</code></td>",
            html_escape(&target.diagnostic_log)
        );
        for (figure, variant) in &figures {
            match target
                .captures
                .iter()
                .find(|capture| capture.figure == *figure && capture.variant == *variant)
            {
                Some(capture) => {
                    let path = html_escape(&capture.matrix_path);
                    let _ = write!(
                        html,
                        "<td><a href=\"{path}\"><img src=\"{path}\" alt=\"{}\"></a><br>{}<br><code>{}</code></td>",
                        html_escape(&capture.capture),
                        html_escape(&capture.capture),
                        html_escape(if capture.variant.is_empty() {
                            "singleton"
                        } else {
                            &capture.variant
                        })
                    );
                }
                None => html.push_str("<td>—</td>"),
            }
        }
        html.push_str("</tr>");
    }
    html.push_str("</tbody></table></body></html>");
    html
}

fn safe_preview_relative_path(value: &str) -> eyre::Result<PathBuf> {
    let path = Path::new(value);
    if path.as_os_str().is_empty()
        || !path.is_relative()
        || path
            .components()
            .any(|component| !matches!(component, Component::Normal(_)))
        || path.extension().is_none_or(|extension| extension != "png")
    {
        eyre::bail!("Preview manifest contains an unsafe PNG path: {value:?}");
    }
    Ok(path.to_path_buf())
}

fn target_diagnostic_log(target: &WorktreeTarget) -> PathBuf {
    target
        .worktree_path
        .as_path()
        .join("platform/minecraft/build/sfm-toolchain/run/runGameTestPreview/console.log")
}

fn safe_matrix_name(value: &str) -> String {
    let mut output = value
        .bytes()
        .map(|byte| {
            if byte.is_ascii_alphanumeric() || matches!(byte, b'_' | b'-') {
                char::from(byte)
            } else {
                '-'
            }
        })
        .collect::<String>();
    output.truncate(48);
    let output = output.trim_matches('-');
    if output.is_empty() {
        "puppet".to_string()
    } else {
        output.to_string()
    }
}

fn portable_path(path: &Path) -> String {
    path.to_string_lossy().replace('\\', "/")
}

fn html_escape(value: &str) -> String {
    value
        .replace('&', "&amp;")
        .replace('<', "&lt;")
        .replace('>', "&gt;")
        .replace('"', "&quot;")
        .replace('\'', "&#39;")
}

#[cfg(test)]
mod tests {
    use super::PuppetMatrixManifest;
    use super::collect_target_preview;
    use super::copy_target_preview;
    use super::failed_target;
    use super::render_matrix_index;
    use super::safe_preview_relative_path;
    use super::write_matrix_outputs;
    use crate::branch_targets::BranchName;
    use crate::branch_targets::MinecraftVersion;
    use crate::branch_targets::WorktreePath;
    use crate::branch_targets::WorktreeTarget;
    use crate::jar_build::game_puppet_preview_artifact_root;
    use crate::jar_build::hash::ContentHash;
    use crate::jar_build::hash::ContentHashAlgorithm;
    use std::fs;
    use std::path::Path;
    use tempfile::tempdir;

    #[test]
    fn collector_copies_validated_preview_and_renders_figure_grid() {
        let worktree = tempdir().expect("temporary worktree");
        let target = target(worktree.path(), "1.19.2");
        write_preview(&target, true);
        let matrix = tempdir().expect("temporary matrix");

        let collected = collect_target_preview(&target, "move_1_stack_direct_walkthrough", None)
            .expect("preview should validate");
        copy_target_preview(matrix.path(), &collected, &target).expect("preview should copy");
        assert!(matrix.path().join("1.19.2/preview-manifest.json").is_file());
        assert!(
            matrix
                .path()
                .join("1.19.2/move_1_stack_direct_walkthrough/figure_01_overview.png")
                .is_file()
        );

        let index = render_matrix_index(&PuppetMatrixManifest {
            schema_version: 1,
            created_at: "2026-07-15T00:00:00Z".to_string(),
            puppet_selection: "move_1_stack_direct_walkthrough".to_string(),
            game_test: None,
            targets: vec![collected],
        });
        assert!(index.contains("Figure 1"));
        assert!(index.contains("figure_01_overview.png"));
    }

    #[test]
    fn invalid_target_does_not_delete_completed_matrix_entries() {
        let completed_worktree = tempdir().expect("completed worktree");
        let completed = target(completed_worktree.path(), "1.19.2");
        write_preview(&completed, true);
        let matrix = tempdir().expect("temporary matrix");
        let completed_target =
            collect_target_preview(&completed, "move_1_stack_direct_walkthrough", None)
                .expect("completed preview should validate");
        copy_target_preview(matrix.path(), &completed_target, &completed)
            .expect("completed preview should copy");

        let invalid_worktree = tempdir().expect("invalid worktree");
        let invalid = target(invalid_worktree.path(), "1.19.4");
        write_preview(&invalid, false);
        let error = collect_target_preview(&invalid, "move_1_stack_direct_walkthrough", None)
            .expect_err("invalid PNG should fail validation");
        assert!(error.to_string().contains("not a valid PNG"));
        let failed = failed_target(&invalid, error.to_string());
        write_matrix_outputs(
            matrix.path(),
            "move_1_stack_direct_walkthrough",
            None,
            &[completed_target, failed],
        )
        .expect("matrix should retain completed and failed target records");
        let manifest = fs::read_to_string(matrix.path().join("matrix-manifest.json"))
            .expect("read matrix manifest");
        assert!(manifest.contains("\"branch\": \"1.19.4\""));
        assert!(manifest.contains("\"result\": \"failed\""));
        assert!(
            matrix
                .path()
                .join("1.19.2/move_1_stack_direct_walkthrough/figure_01_overview.png")
                .is_file()
        );
    }

    #[test]
    fn preview_paths_reject_parent_traversal_and_non_png_files() {
        safe_preview_relative_path("puppet/figure_01_overview.png").unwrap();
        let _ = safe_preview_relative_path("../outside.png").unwrap_err();
        let _ = safe_preview_relative_path("puppet/figure_01.txt").unwrap_err();
    }

    #[test]
    fn collector_rejects_a_source_manifest_for_the_wrong_branch() {
        let worktree = tempdir().expect("temporary worktree");
        let target = target(worktree.path(), "1.19.2");
        write_preview(&target, true);
        let manifest_path = game_puppet_preview_artifact_root(target.worktree_path.as_path())
            .join("preview-manifest.json");
        let mismatched = fs::read_to_string(&manifest_path)
            .expect("read fixture manifest")
            .replace("\"branch\": \"1.19.2\"", "\"branch\": \"1.19.4\"");
        fs::write(&manifest_path, mismatched).expect("write mismatched fixture manifest");

        let error = collect_target_preview(&target, "move_1_stack_direct_walkthrough", None)
            .expect_err("mismatched manifest should fail");
        assert!(error.to_string().contains("does not match selected target"));
    }

    fn target(root: &Path, branch: &str) -> WorktreeTarget {
        WorktreeTarget {
            branch: BranchName::from(branch),
            worktree_path: WorktreePath::from(root.to_path_buf()),
            core: true,
            mc_version: Some(MinecraftVersion::parse(branch).expect("version fixture")),
        }
    }

    fn write_preview(target: &WorktreeTarget, valid_png: bool) {
        let artifact_root = game_puppet_preview_artifact_root(target.worktree_path.as_path());
        let relative_path = Path::new("move_1_stack_direct_walkthrough/figure_01_overview.png");
        let capture_path = artifact_root.join(relative_path);
        fs::create_dir_all(capture_path.parent().expect("capture parent"))
            .expect("create capture parent");
        let bytes = if valid_png {
            png(1280, 720)
        } else {
            b"not a png".to_vec()
        };
        fs::write(&capture_path, &bytes).expect("write capture");
        let hash = ContentHash::from_bytes(&bytes, ContentHashAlgorithm::Blake3);
        let manifest = format!(
            r#"{{
  "branch": "{}",
  "minecraftVersion": "{}",
  "puppetSelection": "move_1_stack_direct_walkthrough",
  "gameTest": null,
  "viewport": {{"width": 1280, "height": 720}},
  "captureProfile": {{"nativeMainRenderTarget": true, "hideHud": true, "clearTransientOverlays": false}},
  "captures": [{{
    "puppet": "move_1_stack_direct_walkthrough",
    "figure": 1,
    "capture": "overview",
    "path": "move_1_stack_direct_walkthrough/figure_01_overview.png",
    "width": 1280,
    "height": 720,
    "hash": "{hash}",
    "camera": null,
    "screen": "world",
    "hudHidden": true
  }}]
}}"#,
            target.branch,
            target.mc_version.as_ref().expect("version fixture")
        );
        fs::write(artifact_root.join("preview-manifest.json"), manifest)
            .expect("write preview manifest");
    }

    fn png(width: u32, height: u32) -> Vec<u8> {
        let mut bytes = vec![137, 80, 78, 71, 13, 10, 26, 10, 0, 0, 0, 13];
        bytes.extend_from_slice(b"IHDR");
        bytes.extend_from_slice(&width.to_be_bytes());
        bytes.extend_from_slice(&height.to_be_bytes());
        bytes
    }
}
