pub(super) fn invoke() -> eyre::Result<()> {
    let path = super::get_repo_root()?;
    println!("{}", path.display());
    Ok(())
}
