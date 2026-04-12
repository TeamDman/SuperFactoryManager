use crate::cli::jar::get_jar_dir;
use std::ffi::OsStr;

pub(super) fn invoke() -> eyre::Result<()> {
    let jar_dir = get_jar_dir()?;
    let jars = super::jar_dir_command::list_jar_files_sorted(&jar_dir)?;

    if jars.is_empty() {
        println!("No jars found in {}", jar_dir.display());
        return Ok(());
    }

    for jar in jars {
        if let Some(name) = jar.file_name().and_then(OsStr::to_str) {
            println!("{name}");
        } else {
            println!("{}", jar.display());
        }
    }

    Ok(())
}
