//! Conservative review partition; raw bytes and comparison policy remain intact.
pub(super) fn basis(path: &str, before: &str, after: &str) -> Option<String> {
    if before == after
        || !std::path::Path::new(path)
            .extension()
            .is_some_and(|ext| ext.eq_ignore_ascii_case("java"))
    {
        return None;
    }
    let left = crate::source_projection::simplify::parse(before.to_owned()).ok()?;
    let right = crate::source_projection::simplify::parse(after.to_owned()).ok()?;
    let result = crate::source_projection::simplify::compare(&left, &right);
    (result.classification
        == crate::source_projection::simplify::ComparisonClassification::WhitespaceOnly)
        .then(|| crate::source_projection::simplify::ALGORITHM.to_owned())
}
#[cfg(test)]
mod tests {
    #[test]
    fn whitespace_partition_preserves_literals_comments_and_real_sfml_tests_change() {
        let classify = |a, b| super::basis("src/test/java/SFMLTests.java", a, b).is_some();
        assert!(classify(
            "class A { int n=1; }\n",
            "class A {\n int n = 1;\n}\n"
        ));
        assert!(classify("class A {}\r\n", "class A {}\n"));
        assert!(!classify(
            "class A { String s=\"a b\"; }",
            "class A { String s=\"ab\"; }"
        ));
        assert!(!classify("class A { /* a b */ }", "class A { /* ab */ }"));
        assert!(!classify("class A {}", "class A {}"));
        assert!(!classify(
            "class A { void f(){ for(int i=0;i<5;i++) {} } }",
            "class A { void f(){ while(cwd!=null) {} } }"
        ));
        assert!(super::basis("data.yaml", "a: b\n", "a:  b\n").is_none());
    }
}
