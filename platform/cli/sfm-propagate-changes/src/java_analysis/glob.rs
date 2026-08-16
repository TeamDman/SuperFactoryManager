use super::JavaSymbolKind;
use facet::Facet;

/// A case-sensitive glob over canonical Access Transformer style symbol selectors.
///
/// Only `*` (zero or more characters) and `?` (exactly one character) are
/// special. Every other character, including `$`, `(`, `)`, `[`, and `;`, is
/// matched literally.
#[derive(Facet, Clone, Debug, PartialEq, Eq, PartialOrd, Ord)]
pub struct JavaSymbolGlob {
    pattern: String,
}

impl Default for JavaSymbolGlob {
    fn default() -> Self {
        Self {
            pattern: "*".to_owned(),
        }
    }
}

impl JavaSymbolGlob {
    #[must_use]
    pub fn new(pattern: Option<String>) -> Self {
        Self {
            pattern: pattern.unwrap_or_else(|| "*".to_owned()),
        }
    }

    #[must_use]
    pub fn pattern(&self) -> &str {
        &self.pattern
    }

    #[must_use]
    pub fn matches(&self, candidate: &str) -> bool {
        wildcard_matches(&self.pattern, candidate)
    }

    #[must_use]
    pub(crate) fn matches_parts(
        &self,
        kind: JavaSymbolKind,
        owner: &str,
        name: &str,
        descriptor: Option<&str>,
        qualified_name: &str,
    ) -> bool {
        match kind {
            JavaSymbolKind::Class
            | JavaSymbolKind::Interface
            | JavaSymbolKind::Enum
            | JavaSymbolKind::Record
            | JavaSymbolKind::Annotation
            | JavaSymbolKind::LocalVariable
            | JavaSymbolKind::Parameter => {
                wildcard_matches_segments(&self.pattern, &[qualified_name])
            }
            JavaSymbolKind::Field => wildcard_matches_segments(&self.pattern, &[owner, " ", name]),
            JavaSymbolKind::Method | JavaSymbolKind::Constructor => wildcard_matches_segments(
                &self.pattern,
                &[owner, " ", name, descriptor.unwrap_or_default()],
            ),
        }
    }
}

fn wildcard_matches(pattern: &str, candidate: &str) -> bool {
    wildcard_matches_segments(pattern, &[candidate])
}

#[derive(Clone, Copy)]
struct CandidatePosition {
    segment: usize,
    byte: usize,
}

fn wildcard_matches_segments(pattern: &str, candidate: &[&str]) -> bool {
    let mut pattern_byte = 0_usize;
    let mut candidate_position = CandidatePosition {
        segment: 0,
        byte: 0,
    };
    let mut star_pattern_byte = None;
    let mut star_candidate_position = None;

    while let Some((candidate_char, next_candidate_position)) =
        next_candidate_char(candidate, candidate_position)
    {
        let pattern_char = next_char(pattern, pattern_byte);
        match pattern_char {
            Some((token, next_pattern_byte)) if token == '?' || token == candidate_char => {
                pattern_byte = next_pattern_byte;
                candidate_position = next_candidate_position;
            }
            Some(('*', next_pattern_byte)) => {
                pattern_byte = next_pattern_byte;
                star_pattern_byte = Some(next_pattern_byte);
                star_candidate_position = Some(candidate_position);
            }
            _ => {
                let (Some(after_star), Some(star_candidate)) =
                    (star_pattern_byte, star_candidate_position)
                else {
                    return false;
                };
                let Some((_, advanced_candidate)) = next_candidate_char(candidate, star_candidate)
                else {
                    return false;
                };
                pattern_byte = after_star;
                candidate_position = advanced_candidate;
                star_candidate_position = Some(advanced_candidate);
            }
        }
    }

    while let Some((token, next_pattern_byte)) = next_char(pattern, pattern_byte) {
        if token != '*' {
            return false;
        }
        pattern_byte = next_pattern_byte;
    }
    true
}

fn next_char(value: &str, byte: usize) -> Option<(char, usize)> {
    let character = value.get(byte..)?.chars().next()?;
    Some((character, byte + character.len_utf8()))
}

fn next_candidate_char(
    candidate: &[&str],
    mut position: CandidatePosition,
) -> Option<(char, CandidatePosition)> {
    while let Some(segment) = candidate.get(position.segment) {
        if let Some((character, next_byte)) = next_char(segment, position.byte) {
            return Some((
                character,
                CandidatePosition {
                    segment: position.segment,
                    byte: next_byte,
                },
            ));
        }
        position.segment += 1;
        position.byte = 0;
    }
    None
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn omitted_glob_matches_every_selector() {
        assert!(JavaSymbolGlob::default().matches("example.A run()V"));
    }

    #[test]
    fn glob_supports_only_star_and_question_mark_wildcards() {
        let method = "example.Outer$Inner run([Ljava/lang/String;)V";
        assert!(
            JavaSymbolGlob::new(Some("example.* run(?Ljava/lang/String;)V".to_owned()))
                .matches(method)
        );
        assert!(!JavaSymbolGlob::new(Some("Example.*".to_owned())).matches("example.A"));
        assert!(
            JavaSymbolGlob::new(Some("example.Outer$*".to_owned())).matches("example.Outer$Inner")
        );
        assert!(!JavaSymbolGlob::new(Some("example.[AB]".to_owned())).matches("example.A"));
    }

    #[test]
    fn glob_covers_prefix_suffix_infix_single_character_and_zero_match() {
        let field = "example.Outer$Inner value";
        let overload = "example.Outer$Inner run(Ljava/lang/String;)V";

        assert!(JavaSymbolGlob::new(Some("example.*".to_owned())).matches(field));
        assert!(JavaSymbolGlob::new(Some("* value".to_owned())).matches(field));
        assert!(JavaSymbolGlob::new(Some("*Outer$Inner*".to_owned())).matches(overload));
        assert!(JavaSymbolGlob::new(Some("example.Outer$Inne? value".to_owned())).matches(field));
        assert!(!JavaSymbolGlob::new(Some("* missing".to_owned())).matches(field));
        assert!(!JavaSymbolGlob::new(Some("*OuterXInner*".to_owned())).matches(overload));
    }

    #[test]
    fn routed_matching_equals_materialized_canonical_selectors_without_allocating_candidates() {
        let cases = [
            (
                JavaSymbolKind::Class,
                "example",
                "Editor",
                None,
                "example.Editor",
                "*Editor",
                true,
            ),
            (
                JavaSymbolKind::Field,
                "example.Editor",
                "value",
                None,
                "example.Editor.value",
                "example.* value",
                true,
            ),
            (
                JavaSymbolKind::Method,
                "example.Editor",
                "run",
                Some("(Ljava/lang/String;)V"),
                "example.Editor.run(Ljava/lang/String;)V",
                "* run(?java/lang/String;)V",
                true,
            ),
            (
                JavaSymbolKind::Method,
                "example.Editor",
                "run",
                Some("()V"),
                "example.Editor.run()V",
                "example.Other*",
                false,
            ),
        ];

        for (kind, owner, name, descriptor, qualified_name, pattern, expected) in cases {
            let glob = JavaSymbolGlob::new(Some(pattern.to_owned()));
            assert_eq!(
                glob.matches_parts(kind, owner, name, descriptor, qualified_name),
                expected,
                "pattern {pattern} for {owner} {name}{}",
                descriptor.unwrap_or_default()
            );
        }
        assert!(JavaSymbolGlob::new(Some("example.?ditor".to_owned())).matches("example.Éditor"));
    }
}
