use crate::worktree::Worktree;
use crate::worktree::get_sorted_worktrees;
use color_eyre::owo_colors::OwoColorize;
use eyre::Context;
use eyre::bail;
use facet::Facet;
use figue as args;
use std::io::Write;
use std::path::PathBuf;
use std::process::Command;

/// Get the git status for a worktree
fn get_worktree_status(worktree: &Worktree, short: bool) -> eyre::Result<WorktreeStatus> {
    // Check if we're in a merge state
    let merge_head = worktree.path.join(".git").join("MERGE_HEAD");
    let is_merging = if merge_head.exists() {
        true
    } else {
        // For worktrees, .git might be a file pointing to the real git dir
        let output = Command::new("git")
            .args(["rev-parse", "--git-dir"])
            .current_dir(&worktree.path)
            .output()
            .wrap_err("Failed to get git dir")?;

        let git_dir = PathBuf::from(String::from_utf8_lossy(&output.stdout).trim());
        let merge_head = if git_dir.is_absolute() {
            git_dir.join("MERGE_HEAD")
        } else {
            worktree.path.join(&git_dir).join("MERGE_HEAD")
        };
        merge_head.exists()
    };

    // Get porcelain status for parsing
    let output = Command::new("git")
        .args(["status", "--porcelain=v1"])
        .current_dir(&worktree.path)
        .output()
        .wrap_err_with(|| format!("Failed to get status in {}", worktree.path.display()))?;

    if !output.status.success() {
        bail!(
            "git status failed in {}: {}",
            worktree.path.display(),
            String::from_utf8_lossy(&output.stderr)
        );
    }

    let stdout = String::from_utf8_lossy(&output.stdout);
    let lines: Vec<&str> = stdout.lines().collect();

    let mut staged = Vec::new();
    let mut unstaged = Vec::new();
    let mut untracked = Vec::new();
    let mut conflicts = Vec::new();

    for line in &lines {
        if line.len() < 3 {
            continue;
        }
        let index_status = line.chars().next().unwrap_or(' ');
        let worktree_status = line.chars().nth(1).unwrap_or(' ');
        let file = &line[3..];

        // Check for conflicts (both modified, or various unmerged states)
        if matches!(
            (index_status, worktree_status),
            ('U', _) | (_, 'U') | ('A', 'A') | ('D', 'D')
        ) {
            conflicts.push(file.to_string());
        } else if index_status == '?' {
            untracked.push(file.to_string());
        } else {
            // Check index (staged) changes
            if index_status != ' ' && index_status != '?' {
                staged.push(format!("{index_status} {file}"));
            }
            // Check worktree (unstaged) changes
            if worktree_status != ' ' && worktree_status != '?' {
                unstaged.push(format!("{worktree_status} {file}"));
            }
        }
    }

    // Get ahead/behind info
    let output = Command::new("git")
        .args(["rev-list", "--left-right", "--count", "@{upstream}...HEAD"])
        .current_dir(&worktree.path)
        .output();

    let (ahead, behind) = match output {
        Ok(out) if out.status.success() => {
            let stdout = String::from_utf8_lossy(&out.stdout);
            let parts: Vec<&str> = stdout.trim().split('\t').collect();
            if parts.len() == 2 {
                let behind = parts[0].parse().unwrap_or(0);
                let ahead = parts[1].parse().unwrap_or(0);
                (ahead, behind)
            } else {
                (0, 0)
            }
        }
        _ => (0, 0), // No upstream or error
    };

    Ok(WorktreeStatus {
        branch: worktree.branch.clone(),
        path: worktree.path.clone(),
        is_merging,
        staged,
        unstaged,
        untracked,
        conflicts,
        ahead,
        behind,
        short,
    })
}

/// Ensure all worktrees are clean. If only generated resources changed, prompt to auto-commit.
///
/// # Errors
///
/// Returns an error if any worktree has uncommitted changes outside generated resources
/// or if the user declines the auto-commit prompt.
pub(crate) fn assert_worktrees_clean_or_autocommit_generated(
    worktrees: &[Worktree],
) -> eyre::Result<()> {
    for wt in worktrees {
        let status = get_worktree_status(wt, false)?;
        if status.is_clean() && !status.is_merging {
            continue;
        }

        status.display();

        if status.only_generated_changes() {
            let committed = prompt_autocommit_generated(&status)?;
            if committed {
                continue;
            }
            bail!("Uncommitted generated changes left uncommitted; aborting");
        }

        bail!("Uncommitted changes found; aborting");
    }

    Ok(())
}

/// Status information for a single worktree
struct WorktreeStatus {
    branch: String,
    path: PathBuf,
    is_merging: bool,
    staged: Vec<String>,
    unstaged: Vec<String>,
    untracked: Vec<String>,
    conflicts: Vec<String>,
    ahead: usize,
    behind: usize,
    short: bool,
}

impl WorktreeStatus {
    fn changed_paths(&self) -> Vec<String> {
        let mut paths: Vec<String> = Vec::new();
        let parse_entry = |entry: &String| -> String {
            entry
                .split_once(' ')
                .map_or(entry.as_str(), |(_, path)| path)
                .trim_matches('"')
                .to_string()
        };

        for entry in &self.staged {
            paths.push(parse_entry(entry));
        }
        for entry in &self.unstaged {
            paths.push(parse_entry(entry));
        }
        for entry in &self.untracked {
            paths.push(parse_entry(entry));
        }
        for entry in &self.conflicts {
            paths.push(entry.clone());
        }

        paths
    }

    fn only_generated_changes(&self) -> bool {
        if self.is_merging || !self.conflicts.is_empty() {
            return false;
        }

        let paths = self.changed_paths();
        if paths.is_empty() {
            return false;
        }

        paths.iter().all(|path| {
            let normalized = path.replace('\\', "/");
            normalized.starts_with("platform/minecraft/src/generated/")
                || normalized == "platform/minecraft/src/generated"
        })
    }

    fn is_clean(&self) -> bool {
        !self.is_merging
            && self.staged.is_empty()
            && self.unstaged.is_empty()
            && self.untracked.is_empty()
            && self.conflicts.is_empty()
    }

    fn display(&self) {
        // Header with branch name
        let sync_info = match (self.ahead, self.behind) {
            (0, 0) => String::new(),
            (a, 0) => format!(" {}", format!("↑{a}").green()),
            (0, b) => format!(" {}", format!("↓{b}").red()),
            (a, b) => format!(" {}{}", format!("↑{a}").green(), format!("↓{b}").red()),
        };

        let status_indicator = if self.is_merging {
            format!(" {}", "[MERGING]".yellow().bold())
        } else if !self.conflicts.is_empty() {
            format!(" {}", "[CONFLICTS]".red().bold())
        } else if self.is_clean() {
            format!(" {}", "✓".green().bold())
        } else {
            String::new()
        };

        println!(
            "\n{} {} {}{}{}",
            "━━━".dimmed(),
            self.branch.cyan().bold(),
            self.path.display().to_string().dimmed(),
            sync_info,
            status_indicator
        );

        if self.is_clean() && !self.is_merging {
            if !self.short {
                println!("  {}", "Nothing to commit, working tree clean".dimmed());
            }
            return;
        }

        if self.is_merging {
            println!("  {} {}", "⚠".yellow(), "Merge in progress".yellow());
        }

        if !self.conflicts.is_empty() {
            println!(
                "  {} ({}):",
                "Unmerged paths".red().bold(),
                self.conflicts.len()
            );
            for file in &self.conflicts {
                println!("    {} {}", "!!".red().bold(), file.red());
            }
        }

        if !self.staged.is_empty() {
            println!("  {} ({}):", "Staged".green().bold(), self.staged.len());
            for change in &self.staged {
                println!("    {} {}", "+".green(), change.green());
            }
        }

        if !self.unstaged.is_empty() {
            println!(
                "  {} ({}):",
                "Unstaged".yellow().bold(),
                self.unstaged.len()
            );
            for change in &self.unstaged {
                println!("    {} {}", "~".yellow(), change.yellow());
            }
        }

        if !self.untracked.is_empty() {
            if self.short {
                println!(
                    "  {}: {} file(s)",
                    "Untracked".dimmed(),
                    self.untracked.len()
                );
            } else {
                println!("  {} ({}):", "Untracked".dimmed(), self.untracked.len());
                for file in &self.untracked {
                    println!("    {} {}", "?".dimmed(), file.dimmed());
                }
            }
        }
    }
}

fn prompt_autocommit_generated(status: &WorktreeStatus) -> eyre::Result<bool> {
    println!();
    println!(
        "{}",
        "Only generated resources changed under platform/minecraft/src/generated."
            .yellow()
            .bold()
    );
    print!("Would you like to auto-commit these changes? [Y/n] ");
    std::io::stdout()
        .flush()
        .wrap_err("Failed to flush stdout")?;

    let mut input = String::new();
    std::io::stdin()
        .read_line(&mut input)
        .wrap_err("Failed to read user input")?;
    let input = input.trim().to_lowercase();
    let should_commit = input.is_empty() || input == "y" || input == "yes";

    if !should_commit {
        return Ok(false);
    }

    let add_output = Command::new("git")
        .args(["add", "platform/minecraft/src/generated"])
        .current_dir(&status.path)
        .output()
        .wrap_err("Failed to stage generated resources")?;

    if !add_output.status.success() {
        bail!(
            "git add failed in {}: {}",
            status.path.display(),
            String::from_utf8_lossy(&add_output.stderr)
        );
    }

    let message = format!("{{{}}} - resources - datagen", status.branch);
    let commit_output = Command::new("git")
        .args(["commit", "-m", &message])
        .current_dir(&status.path)
        .output()
        .wrap_err("Failed to commit generated resources")?;

    if !commit_output.status.success() {
        bail!(
            "git commit failed in {}: {}",
            status.path.display(),
            String::from_utf8_lossy(&commit_output.stderr)
        );
    }

    Ok(true)
}

/// Status command - show git status for all worktrees
#[derive(Facet, Debug)]
#[repr(u8)]
pub enum StatusCommand {
    /// Show status for all worktrees (default)
    All {
        /// Show short status (hide untracked file details)
        #[facet(default, args::named, args::short = 's')]
        short: bool,
    },
    /// Show only dirty (uncommitted changes) worktrees
    Dirty {
        /// Show short status (hide untracked file details)
        #[facet(default, args::named, args::short = 's')]
        short: bool,
    },
    /// Show summary counts only
    Summary,
}

impl Default for StatusCommand {
    fn default() -> Self {
        StatusCommand::All { short: false }
    }
}

impl StatusCommand {
    /// # Errors
    ///
    /// Returns an error if getting worktree status fails.
    pub fn invoke(self) -> eyre::Result<()> {
        let worktrees = get_sorted_worktrees()?;

        if worktrees.is_empty() {
            println!("No worktrees found.");
            return Ok(());
        }

        match self {
            StatusCommand::All { short } => StatusCommand::run_all(&worktrees, short)?,
            StatusCommand::Dirty { short } => StatusCommand::run_dirty(&worktrees, short)?,
            StatusCommand::Summary => StatusCommand::run_summary(&worktrees)?,
        }

        Ok(())
    }

    fn run_all(worktrees: &[Worktree], short: bool) -> eyre::Result<()> {
        println!(
            "Status for {} worktree(s):",
            worktrees.len().to_string().cyan().bold()
        );
        for wt in worktrees {
            let status = get_worktree_status(wt, short)?;
            status.display();
            if !status.is_clean() && !status.is_merging && status.only_generated_changes() {
                let _ = prompt_autocommit_generated(&status)?;
            }
        }
        Ok(())
    }

    fn run_dirty(worktrees: &[Worktree], short: bool) -> eyre::Result<()> {
        let mut dirty_count = 0;
        let mut statuses = Vec::new();

        for wt in worktrees {
            let status = get_worktree_status(wt, short)?;
            if !status.is_clean() || status.is_merging {
                dirty_count += 1;
                statuses.push(status);
            }
        }

        if statuses.is_empty() {
            println!(
                "All {} worktree(s) are clean! {}",
                worktrees.len().to_string().cyan().bold(),
                "✓".green().bold()
            );
        } else {
            println!(
                "{} of {} worktree(s) have uncommitted changes:",
                dirty_count.to_string().yellow().bold(),
                worktrees.len().to_string().cyan().bold()
            );
            for status in statuses {
                status.display();
                if !status.is_merging && status.only_generated_changes() {
                    let _ = prompt_autocommit_generated(&status)?;
                }
            }
        }

        Ok(())
    }

    fn run_summary(worktrees: &[Worktree]) -> eyre::Result<()> {
        let mut clean = 0;
        let mut dirty = 0;
        let mut merging = 0;
        let mut conflicts = 0;

        // Print one-line status for each worktree
        for wt in worktrees {
            let status = get_worktree_status(wt, true)?;

            // Determine the status icon
            let icon = if status.is_merging {
                "⚠".yellow().to_string()
            } else if !status.conflicts.is_empty() {
                "!!".red().bold().to_string()
            } else if status.is_clean() {
                "✓".green().to_string()
            } else {
                "~".yellow().to_string()
            };

            // Build compact info
            let mut info_parts = Vec::new();
            if status.is_merging {
                info_parts.push("merging".yellow().to_string());
            }
            if !status.conflicts.is_empty() {
                let n = status.conflicts.len();
                let word = if n == 1 { "conflict" } else { "conflicts" };
                info_parts.push(format!("{n} {word}").red().to_string());
            }
            if !status.staged.is_empty() {
                let n = status.staged.len();
                info_parts.push(format!("{n} staged").green().to_string());
            }
            if !status.unstaged.is_empty() {
                let n = status.unstaged.len();
                info_parts.push(format!("{n} unstaged").yellow().to_string());
            }
            if !status.untracked.is_empty() {
                let n = status.untracked.len();
                info_parts.push(format!("{n} untracked").dimmed().to_string());
            }

            let info = if info_parts.is_empty() {
                "clean".dimmed().to_string()
            } else {
                info_parts.join(" ")
            };

            println!("  {} {} {}", icon, status.branch.cyan().bold(), info);

            // Count for totals
            if status.is_merging {
                merging += 1;
            }
            if !status.conflicts.is_empty() {
                conflicts += 1;
            }
            if status.is_clean() && !status.is_merging {
                clean += 1;
            } else {
                dirty += 1;
            }
        }

        // Print totals
        println!();
        println!(
            "Totals ({} worktrees):",
            worktrees.len().to_string().cyan().bold()
        );
        println!(
            "  {} Clean:      {}",
            "✓".green(),
            clean.to_string().green()
        );
        println!(
            "  {} Dirty:      {}",
            "~".yellow(),
            dirty.to_string().yellow()
        );
        if merging > 0 {
            println!(
                "  {} Merging:    {}",
                "⚠".yellow(),
                merging.to_string().yellow().bold()
            );
        }
        if conflicts > 0 {
            println!(
                "  {} Conflicts:  {}",
                "!!".red(),
                conflicts.to_string().red().bold()
            );
        }

        Ok(())
    }
}

/// Run status with default options (show all)
///
/// # Errors
///
/// Returns an error if getting worktree status fails.
pub fn run() -> eyre::Result<()> {
    StatusCommand::default().invoke()
}
