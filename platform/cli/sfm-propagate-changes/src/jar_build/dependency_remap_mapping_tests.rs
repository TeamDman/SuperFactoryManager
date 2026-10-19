use super::parse_unique_srg_member_mappings;
use super::read_unique_srg_member_mappings;

#[test]
fn dependency_remap_captured_mapping_matches_legacy_parser_without_rereading_changed_bytes()
-> eyre::Result<()> {
    let file = tempfile::NamedTempFile::new()?;
    let captured = b"class example/Named\n\tf_1_ field\n\tm_2_ ()V method\n";
    std::fs::write(file.path(), captured)?;
    let expected = read_unique_srg_member_mappings(file.path())?;
    std::fs::write(file.path(), b"class other/Named\n\tf_1_ otherField\n")?;
    let actual = parse_unique_srg_member_mappings(std::str::from_utf8(captured)?);
    assert_eq!(actual, expected);
    assert_eq!(actual["f_1_"], "field");
    assert_ne!(actual, read_unique_srg_member_mappings(file.path())?);
    Ok(())
}

#[test]
fn dependency_remap_captured_mapping_keeps_ambiguous_and_nested_members_excluded() {
    let mapping = "class example/Named\n\tf_1_ field\n\tf_1_ anotherField\n\tm_2_ ()V method\n\t\tf_3_ nested\n";
    let selected = parse_unique_srg_member_mappings(mapping);
    assert_eq!(selected.len(), 1);
    assert_eq!(selected["m_2_"], "method");
}
