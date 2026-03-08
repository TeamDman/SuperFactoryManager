pub(super) fn invoke() -> eyre::Result<()> {
    let path = super::get_repo_root()?;
    open::that(&path)?;
    Ok(())
}
