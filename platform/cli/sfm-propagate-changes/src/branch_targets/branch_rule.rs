use super::BranchGlob;
use super::ExactBranch;
use super::MinecraftVersion;
use super::VersionOp;
use super::VersionScope;
use super::WorktreeTarget;
use glob::Pattern;
use std::fmt;

#[derive(Clone, Debug, Eq, PartialEq)]
#[cfg_attr(test, derive(arbitrary::Arbitrary))]
pub enum BranchRule {
    All,
    Core,
    ExactBranch(ExactBranch),
    BranchGlob(BranchGlob),
    Version {
        scope: VersionScope,
        op: VersionOp,
        version: MinecraftVersion,
    },
}

impl BranchRule {
    /// Parse one branch-query rule.
    ///
    /// # Errors
    ///
    /// Returns an error if the rule is empty, contains an invalid glob, or contains an invalid
    /// Minecraft version predicate.
    pub fn parse(input: &str) -> eyre::Result<Self> {
        let trimmed = input.trim();
        if trimmed.is_empty() {
            eyre::bail!("Branch query rule cannot be empty");
        }
        if trimmed == "*" {
            return Ok(Self::All);
        }
        if trimmed.eq_ignore_ascii_case("core") {
            return Ok(Self::Core);
        }
        if let Some(rest) = trimmed.strip_prefix("core")
            && let Some((op, version_text)) = VersionOp::parse_prefix(rest.trim_start())
        {
            return Ok(Self::Version {
                scope: VersionScope::CoreOnly,
                op,
                version: MinecraftVersion::parse(version_text.trim())?,
            });
        }
        if let Some((op, version_text)) = VersionOp::parse_prefix(trimmed) {
            return Ok(Self::Version {
                scope: VersionScope::AnyTargetWithMinecraftVersion,
                op,
                version: MinecraftVersion::parse(version_text.trim())?,
            });
        }
        if is_glob_pattern(trimmed) {
            return Ok(Self::BranchGlob(BranchGlob::new(trimmed)?));
        }

        Ok(Self::ExactBranch(ExactBranch::from(trimmed)))
    }

    #[must_use]
    pub fn matches(&self, target: &WorktreeTarget) -> bool {
        match self {
            Self::All => true,
            Self::Core => target.core,
            Self::ExactBranch(branch) => target.branch.as_str() == branch.as_str(),
            Self::BranchGlob(pattern) => Pattern::new(pattern.as_str())
                .is_ok_and(|pattern| pattern.matches(target.branch.as_str())),
            Self::Version { scope, op, version } => {
                if *scope == VersionScope::CoreOnly && !target.core {
                    return false;
                }
                target
                    .mc_version
                    .as_ref()
                    .is_some_and(|target_version| op.matches(target_version.cmp(version)))
            }
        }
    }
}

impl fmt::Display for BranchRule {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Self::All => f.write_str("*"),
            Self::Core => f.write_str("core"),
            Self::ExactBranch(branch) => write!(f, "{branch}"),
            Self::BranchGlob(pattern) => write!(f, "{pattern}"),
            Self::Version { scope, op, version } => match scope {
                VersionScope::AnyTargetWithMinecraftVersion => write!(f, "{op}{version}"),
                VersionScope::CoreOnly => write!(f, "core{op}{version}"),
            },
        }
    }
}

fn is_glob_pattern(text: &str) -> bool {
    text.contains('*') || text.contains('?') || text.contains('[')
}
