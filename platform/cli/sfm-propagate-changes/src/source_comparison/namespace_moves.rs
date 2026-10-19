//! Narrow advancement relocation pairing; actual changed bytes remain in the diff.
use super::snapshot::ComparisonSnapshot;
use std::collections::BTreeMap;
fn identity(path: &str) -> Option<(&str, &str, &str)> {
    let path = path.strip_prefix("src/generated/resources/data/")?;
    let (namespace, tail) = path.split_once('/')?;
    if !namespace
        .bytes()
        .all(|b| b.is_ascii_lowercase() || b.is_ascii_digit() || matches!(b, b'_' | b'-' | b'.'))
    {
        return None;
    }
    if !(tail.starts_with("advancement/recipes/") || tail.starts_with("advancements/recipes/")) {
        return None;
    }
    let stem = tail.rsplit('/').next()?.strip_suffix(".json")?;
    Some((namespace, tail, stem))
}
fn matching(old: &str, new: &str, before: &[u8], after: &[u8]) -> Option<bool> {
    let (old_namespace, old_tail, stem) = identity(old)?;
    let (new_namespace, new_tail, _) = identity(new)?;
    if old_tail != new_tail || old_namespace == new_namespace {
        return None;
    }
    let before: BTreeMap<String, facet_value::Value> = facet_json::from_slice(before).ok()?;
    let after: BTreeMap<String, facet_value::Value> = facet_json::from_slice(after).ok()?;
    let a = facet_json::to_string(&before).ok()?;
    let b = facet_json::to_string(&after).ok()?;
    let old_id = facet_json::to_string(&format!("{old_namespace}:{stem}")).ok()?;
    let new_id = facet_json::to_string(&format!("{new_namespace}:{stem}")).ok()?;
    Some(a.matches(&old_id).count() == 2 && a.replace(&old_id, &new_id) == b)
}
pub(super) fn extend<'a>(
    left: &'a ComparisonSnapshot,
    right: &'a ComparisonSnapshot,
    moves: &mut BTreeMap<&'a str, &'a str>,
) {
    let mut removed: BTreeMap<&str, Vec<&str>> = BTreeMap::new();
    let mut added: BTreeMap<&str, Vec<&str>> = BTreeMap::new();
    for path in left.files.keys() {
        if !right.files.contains_key(path)
            && !moves.values().any(|old| *old == path)
            && let Some((_, tail, _)) = identity(path)
        {
            removed.entry(tail).or_default().push(path);
        }
    }
    for path in right.files.keys() {
        if !left.files.contains_key(path)
            && !moves.contains_key(path.as_str())
            && let Some((_, tail, _)) = identity(path)
        {
            added.entry(tail).or_default().push(path);
        }
    }
    for (tail, old) in removed {
        if let Some(new) = added.get(tail)
            && old.len() == 1
            && new.len() == 1
            && matching(
                old[0],
                new[0],
                &left.files[old[0]].bytes,
                &right.files[new[0]].bytes,
            ) == Some(true)
        {
            moves.insert(new[0], old[0]);
        }
    }
}
#[cfg(test)]
mod tests {
    #[test]
    fn advancement_move_requires_unique_tail_and_full_content_match() {
        let a = "src/generated/resources/data/minecraft/advancement/recipes/misc/tough_cable_horizontal.json";
        let b = a.replace("/minecraft/", "/sfm/");
        let before=br#"{"criteria":{"has_the_recipe":{"conditions":{"recipe":"minecraft:tough_cable_horizontal"}}},"rewards":{"recipes":["minecraft:tough_cable_horizontal"]},"other":"unchanged"}"#;
        let after = String::from_utf8(before.to_vec()).unwrap().replace(
            "minecraft:tough_cable_horizontal",
            "sfm:tough_cable_horizontal",
        );
        assert_eq!(super::matching(a, &b, before, after.as_bytes()), Some(true));
        assert_eq!(
            super::matching(
                a,
                &b,
                before,
                after.replace("unchanged", "different").as_bytes()
            ),
            Some(false)
        );
        assert!(
            super::matching(
                a,
                &b.replace("tough_cable_horizontal", "other"),
                before,
                after.as_bytes()
            )
            .is_none()
        );
        assert!(super::matching("A.json", "B.json", before, after.as_bytes()).is_none());
    }
    #[test]
    fn ambiguous_namespace_tails_do_not_pair() {
        let root = tempfile::tempdir().unwrap();
        let left = root.path().join("left");
        let right = root.path().join("right");
        for (base, namespace) in [(&left, "minecraft"), (&left, "other"), (&right, "sfm")] {
            let file = base.join(format!(
                "src/generated/resources/data/{namespace}/advancement/recipes/misc/cable.json"
            ));
            std::fs::create_dir_all(file.parent().unwrap()).unwrap();
            std::fs::write(file,format!(r#"{{"criteria":{{"recipe":"{namespace}:cable"}},"rewards":{{"recipes":["{namespace}:cable"]}}}}"#)).unwrap();
        }
        let capture = |base: &std::path::Path| {
            crate::source_comparison::snapshot(
                &crate::source_comparison::input::ComparisonInput::Directory {
                    root: base.to_string_lossy().into_owned(),
                },
                &crate::cancellation::CancellationToken::new(),
            )
            .unwrap()
        };
        let a = capture(&left);
        let b = capture(&right);
        let mut moves = std::collections::BTreeMap::new();
        super::extend(&a, &b, &mut moves);
        assert!(moves.is_empty());
    }
}

/// Reuse the same narrow identity and full-document guard for actual JAR members.
pub(crate) fn extend_archive(
    before: &BTreeMap<String, Vec<u8>>,
    after: &BTreeMap<String, Vec<u8>>,
    moves: &mut BTreeMap<String, String>,
) {
    let mut removed: BTreeMap<String, Vec<&str>> = BTreeMap::new();
    let mut added: BTreeMap<String, Vec<&str>> = BTreeMap::new();
    for (entries, other, groups, is_old) in [
        (before, after, &mut removed, true),
        (after, before, &mut added, false),
    ] {
        for path in entries.keys() {
            let paired = if is_old {
                moves.contains_key(path)
            } else {
                moves.values().any(|new| new == path)
            };
            let logical = format!("src/generated/resources/{path}");
            if !other.contains_key(path)
                && !paired
                && let Some((_, tail, _)) = identity(&logical)
            {
                groups.entry(tail.to_owned()).or_default().push(path);
            }
        }
    }
    for (tail, old) in removed {
        if let Some(new) = added.get(&tail)
            && old.len() == 1
            && new.len() == 1
            && matching(
                &format!("src/generated/resources/{}", old[0]),
                &format!("src/generated/resources/{}", new[0]),
                &before[old[0]],
                &after[new[0]],
            ) == Some(true)
        {
            moves.insert(old[0].to_owned(), new[0].to_owned());
        }
    }
}

#[cfg(test)]
mod archive_tests {
    #[test]
    fn archive_moves_share_the_guard_and_reject_ambiguous_tails() {
        use std::collections::BTreeMap;
        let old = "data/minecraft/advancement/recipes/misc/cable.json";
        let new = "data/sfm/advancement/recipes/misc/cable.json";
        let bytes=br#"{"criteria":{"has_the_recipe":{"conditions":{"recipe":"minecraft:cable"}}},"rewards":{"recipes":["minecraft:cable"]}}"#.to_vec();
        let after = String::from_utf8(bytes.clone())
            .unwrap()
            .replace("minecraft:cable", "sfm:cable")
            .into_bytes();
        let mut before = BTreeMap::from([(old.to_owned(), bytes.clone())]);
        let after = BTreeMap::from([(new.to_owned(), after)]);
        let mut paired = BTreeMap::new();
        super::extend_archive(&before, &after, &mut paired);
        assert_eq!(paired[old], new);
        before.insert(
            "data/other/advancement/recipes/misc/cable.json".to_owned(),
            bytes,
        );
        paired.clear();
        super::extend_archive(&before, &after, &mut paired);
        assert!(paired.is_empty());
    }
}
