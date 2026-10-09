//! Read repository metadata without starting Git. Mutating Git operations and
//! porcelain status remain separate so their hooks and worktree semantics survive.
use std::collections::BTreeSet;
use std::path::Path;

pub(crate) fn head_revision(path: &Path) -> eyre::Result<String> {
    Ok(gix::discover(path)?.head_id()?.to_string())
}

pub(crate) fn is_merging(path: &Path) -> eyre::Result<bool> {
    Ok(gix::discover(path)?
        .git_dir()
        .join("MERGE_HEAD")
        .try_exists()?)
}

pub(crate) fn conflicted_paths(path: &Path) -> eyre::Result<Vec<String>> {
    let repository = gix::discover(path)?;
    // The pinned gix-index version subtracts the checksum length before
    // validating a truncated file. Reject that corrupt input before decoding.
    match std::fs::metadata(repository.index_path()) {
        Ok(metadata) => eyre::ensure!(
            metadata.len() >= (12 + repository.object_hash().len_in_bytes()) as u64,
            "Git index is truncated"
        ),
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => {}
        Err(error) => return Err(error.into()),
    }
    let index = repository.index_or_empty()?;
    let paths = index
        .entries()
        .iter()
        .filter(|entry| entry.stage() != gix::index::entry::Stage::Unconflicted)
        .map(|entry| std::str::from_utf8(entry.path(&index)).map(str::to_owned))
        .collect::<Result<BTreeSet<_>, _>>()?;
    Ok(paths.into_iter().collect())
}

pub(crate) fn tag_names(path: &Path) -> eyre::Result<Vec<String>> {
    let repository = gix::discover(path)?;
    let mut names = Vec::new();
    for reference in repository.references()?.tags()? {
        let reference = reference.map_err(|error| eyre::eyre!("reading Git tag: {error}"))?;
        let name = std::str::from_utf8(reference.name().as_bstr())?;
        if let Some(name) = name.strip_prefix("refs/tags/") {
            names.push(name.to_owned());
        }
    }
    names.sort();
    Ok(names)
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::Write;
    use std::process::Command;
    use std::process::Stdio;

    // Native Git constructs the reference fixtures and acts as the independent
    // oracle. Production metadata reads must not use this subprocess helper.
    fn git(root: &Path, args: &[&str], input: Option<&str>) -> String {
        let mut command = Command::new("git");
        for (key, _) in std::env::vars_os() {
            if key.to_string_lossy().starts_with("GIT_") {
                command.env_remove(key);
            }
        }
        let mut child = command
            .args([
                "-c",
                "user.name=Test",
                "-c",
                "user.email=test@example.invalid",
                "-c",
                "commit.gpgsign=false",
            ])
            .args(args)
            .current_dir(root)
            .stdin(Stdio::piped())
            .stdout(Stdio::piped())
            .stderr(Stdio::piped())
            .spawn()
            .unwrap();
        if let Some(input) = input {
            child
                .stdin
                .take()
                .unwrap()
                .write_all(input.as_bytes())
                .unwrap();
        }
        let output = child.wait_with_output().unwrap();
        assert!(
            output.status.success(),
            "git {args:?}: {}",
            String::from_utf8_lossy(&output.stderr)
        );
        String::from_utf8(output.stdout).unwrap().trim().to_owned()
    }

    fn committed_repository(root: &Path) {
        git(root, &["init", "-b", "main"], None);
        git(root, &["commit", "--allow-empty", "-m", "fixture"], None);
    }

    #[test]
    fn metadata_reads_cover_packed_tags_and_detached_heads() {
        let temp = tempfile::tempdir().unwrap();
        committed_repository(temp.path());
        let expected = git(temp.path(), &["rev-parse", "HEAD"], None);
        git(temp.path(), &["tag", "4.34.0-1.19.2"], None);
        git(
            temp.path(),
            &["tag", "-a", "4.34.0-1.21.1", "-m", "annotated"],
            None,
        );
        git(temp.path(), &["pack-refs", "--all"], None);
        assert_eq!(
            tag_names(temp.path()).unwrap(),
            ["4.34.0-1.19.2", "4.34.0-1.21.1"]
        );
        let nested = temp.path().join("nested");
        std::fs::create_dir(&nested).unwrap();
        assert_eq!(head_revision(&nested).unwrap(), expected);
        git(temp.path(), &["checkout", "--detach"], None);
        assert_eq!(head_revision(temp.path()).unwrap(), expected);
        assert!(!is_merging(temp.path()).unwrap());
    }

    #[test]
    fn conflict_paths_are_unique_unquoted_and_fail_on_corrupt_index() {
        let temp = tempfile::tempdir().unwrap();
        committed_repository(temp.path());
        let blob = git(temp.path(), &["hash-object", "-w", "--stdin"], Some("data"));
        let name = "space and café.java";
        let input = format!(
            "100644 {blob} 1\t{name}\n100644 {blob} 2\t{name}\n100644 {blob} 3\t{name}\n100644 {blob} 0\tclean.java\n"
        );
        git(temp.path(), &["update-index", "--index-info"], Some(&input));
        assert_eq!(conflicted_paths(temp.path()).unwrap(), [name]);
        std::fs::write(temp.path().join(".git/index"), b"corrupt").unwrap();
        assert!(conflicted_paths(temp.path()).is_err());
    }

    #[test]
    fn worktree_inventory_and_merge_state_are_worktree_local() {
        let temp = tempfile::tempdir().unwrap();
        let main = temp.path().join("main");
        std::fs::create_dir(&main).unwrap();
        committed_repository(&main);
        let linked = temp.path().join("linked space");
        git(
            &main,
            &["worktree", "add", "-b", "1.19.2", linked.to_str().unwrap()],
            None,
        );
        let expected = crate::worktree::get_worktrees(&main).unwrap();
        assert_eq!(expected.len(), 2);
        assert_eq!(expected[0].branch, "main");
        assert_eq!(expected[1].branch, "1.19.2");
        let observed = crate::worktree::get_worktrees(&linked).unwrap();
        assert_eq!(
            observed.iter().map(|wt| &wt.branch).collect::<Vec<_>>(),
            expected.iter().map(|wt| &wt.branch).collect::<Vec<_>>()
        );
        let repo = gix::open(&linked).unwrap();
        std::fs::write(
            repo.git_dir().join("MERGE_HEAD"),
            head_revision(&main).unwrap(),
        )
        .unwrap();
        assert!(is_merging(&linked).unwrap());
        assert!(!is_merging(&main).unwrap());
        std::fs::remove_file(repo.git_dir().join("MERGE_HEAD")).unwrap();
        git(&linked, &["checkout", "--detach"], None);
        assert_eq!(crate::worktree::get_worktrees(&main).unwrap().len(), 1);
    }

    #[test]
    fn unborn_repository_has_no_revision_or_conflicts() {
        let temp = tempfile::tempdir().unwrap();
        gix::init(temp.path()).unwrap();
        assert!(head_revision(temp.path()).is_err());
        assert!(conflicted_paths(temp.path()).unwrap().is_empty());
        assert!(tag_names(temp.path()).unwrap().is_empty());
    }
}
