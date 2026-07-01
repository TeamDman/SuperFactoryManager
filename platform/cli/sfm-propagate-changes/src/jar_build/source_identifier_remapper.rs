use super::SourceIdentifierMappingPath;
use super::SourceJarPath;
use eyre::Context;
use std::collections::BTreeMap;
use std::fs;
use std::fs::File;
use std::io::Cursor;
use std::io::Read;
use std::io::Write;
use std::path::Path;
use std::path::PathBuf;
use std::time::SystemTime;
use std::time::UNIX_EPOCH;
use zip::CompressionMethod;
use zip::ZipArchive;
use zip::ZipWriter;
use zip::write::SimpleFileOptions;

pub(super) fn remap_source_jar_identifiers(
    input: &SourceJarPath,
    mapping: &SourceIdentifierMappingPath,
    output: &SourceJarPath,
) -> eyre::Result<()> {
    let replacements = read_source_identifier_replacements(mapping.as_path())?;
    let input_bytes = fs::read(input.as_path())
        .wrap_err_with(|| format!("Failed to read {}", input.as_path().display()))?;
    let mut archive = ZipArchive::new(Cursor::new(input_bytes))
        .wrap_err_with(|| format!("Failed to open {}", input.as_path().display()))?;
    let temporary_output = unique_sibling_path(output.as_path(), "tmp")?;
    if let Some(parent) = temporary_output.parent() {
        fs::create_dir_all(parent)
            .wrap_err_with(|| format!("Failed to create {}", parent.display()))?;
    }

    let temporary_file = File::create(&temporary_output)
        .wrap_err_with(|| format!("Failed to create {}", temporary_output.display()))?;
    let mut writer = ZipWriter::new(temporary_file);
    let options = SimpleFileOptions::default().compression_method(CompressionMethod::Deflated);

    for index in 0..archive.len() {
        let mut entry = archive.by_index(index).wrap_err_with(|| {
            format!(
                "Failed to read {} entry #{index}",
                input.as_path().display()
            )
        })?;
        let name = entry.name().replace('\\', "/");
        if name.is_empty() {
            continue;
        }
        if name.ends_with('/') {
            writer.add_directory(name, options)?;
            continue;
        }
        writer.start_file(&name, options)?;
        if zip_entry_has_extension(&name, "java") {
            let mut source = String::new();
            entry.read_to_string(&mut source).wrap_err_with(|| {
                format!(
                    "Failed to read Java source {name} from {}",
                    input.as_path().display()
                )
            })?;
            let remapped = replace_java_identifiers(&source, &replacements);
            writer.write_all(remapped.as_bytes())?;
        } else {
            std::io::copy(&mut entry, &mut writer)?;
        }
    }

    writer.finish()?;
    replace_file(&temporary_output, output.as_path())?;
    Ok(())
}

fn read_source_identifier_replacements(mapping: &Path) -> eyre::Result<BTreeMap<String, String>> {
    let content = fs::read_to_string(mapping)
        .wrap_err_with(|| format!("Failed to read {}", mapping.display()))?;
    let mut replacements = BTreeMap::new();
    for line in content.lines() {
        let tab_count = line.chars().take_while(|ch| *ch == '\t').count();
        if tab_count == 0 {
            continue;
        }
        let parts = line.split_whitespace().collect::<Vec<_>>();
        let replacement = match tab_count {
            1 if parts.len() >= 2 => {
                let source = parts[0];
                let target = parts[parts.len() - 1];
                Some((source, target))
            }
            2 if parts.len() >= 3 => Some((parts[1], parts[2])),
            _ => None,
        };
        let Some((source, target)) = replacement else {
            continue;
        };
        if source == target
            || !looks_like_generated_minecraft_identifier(source)
            || !is_java_identifier(target)
        {
            continue;
        }
        match replacements.get(source) {
            Some(existing) if existing != target => {
                replacements.remove(source);
            }
            Some(_) => {}
            None => {
                replacements.insert(source.to_string(), target.to_string());
            }
        }
    }
    Ok(replacements)
}

fn looks_like_generated_minecraft_identifier(value: &str) -> bool {
    (value.starts_with("m_") || value.starts_with("f_") || value.starts_with("p_"))
        && value.ends_with('_')
        && is_java_identifier(value)
}

fn is_java_identifier(value: &str) -> bool {
    let mut chars = value.chars();
    let Some(first) = chars.next() else {
        return false;
    };
    (first == '_' || first == '$' || first.is_ascii_alphabetic())
        && chars.all(|ch| ch == '_' || ch == '$' || ch.is_ascii_alphanumeric())
}

fn replace_java_identifiers(source: &str, replacements: &BTreeMap<String, String>) -> String {
    let mut output = String::with_capacity(source.len());
    let mut identifier_start = None;
    for (index, ch) in source.char_indices() {
        if is_java_identifier_char(ch) {
            identifier_start.get_or_insert(index);
            continue;
        }
        if let Some(start) = identifier_start.take() {
            push_replaced_java_identifier(&mut output, &source[start..index], replacements);
        }
        output.push(ch);
    }
    if let Some(start) = identifier_start {
        push_replaced_java_identifier(&mut output, &source[start..], replacements);
    }
    output
}

fn is_java_identifier_char(ch: char) -> bool {
    ch == '_' || ch == '$' || ch.is_ascii_alphanumeric()
}

fn push_replaced_java_identifier(
    output: &mut String,
    identifier: &str,
    replacements: &BTreeMap<String, String>,
) {
    if let Some(replacement) = replacements.get(identifier) {
        output.push_str(replacement);
    } else {
        output.push_str(identifier);
    }
}

fn zip_entry_has_extension(name: &str, extension: &str) -> bool {
    Path::new(name)
        .extension()
        .is_some_and(|actual| actual.eq_ignore_ascii_case(extension))
}

fn unique_sibling_path(path: &Path, kind: &str) -> eyre::Result<PathBuf> {
    let parent = path.parent().unwrap_or_else(|| Path::new("."));
    let file_name = path
        .file_name()
        .and_then(|name| name.to_str())
        .ok_or_else(|| eyre::eyre!("Path has no file name: {}", path.display()))?;
    let timestamp = SystemTime::now().duration_since(UNIX_EPOCH)?.as_nanos();
    for attempt in 0..100 {
        let candidate = parent.join(format!("{file_name}.{kind}.{timestamp}.{attempt}"));
        if !candidate.exists() {
            return Ok(candidate);
        }
    }
    eyre::bail!("Could not allocate temporary path for {}", path.display())
}

fn replace_file(temporary_path: &Path, path: &Path) -> eyre::Result<()> {
    if path.exists() {
        fs::remove_file(path).wrap_err_with(|| format!("Failed to remove {}", path.display()))?;
    }
    fs::rename(temporary_path, path).wrap_err_with(|| {
        format!(
            "Failed to move {} to {}",
            temporary_path.display(),
            path.display()
        )
    })
}
