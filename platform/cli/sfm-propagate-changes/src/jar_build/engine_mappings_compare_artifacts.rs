#[derive(Clone, Debug)]
struct MojangMethodMapping {
    official_name: String,
    official_descriptor: String,
}

#[derive(Debug)]
struct SrgClassMapping {
    fields: BTreeMap<String, String>,
    methods: BTreeMap<(String, String), SrgMethodMapping>,
}

#[derive(Debug)]
struct SrgMethodMapping {
    srg_name: String,
    parameters: BTreeMap<usize, String>,
    is_static: bool,
}

type ParchmentParameters = BTreeMap<String, BTreeMap<(String, String), BTreeMap<usize, String>>>;

#[derive(Debug, Facet)]
struct ParchmentData {
    #[facet(default)]
    classes: Vec<ParchmentClass>,
}

#[derive(Debug, Facet)]
struct ParchmentClass {
    name: String,
    #[facet(default)]
    methods: Vec<ParchmentMethod>,
}

#[derive(Debug, Facet)]
struct ParchmentMethod {
    name: String,
    descriptor: String,
    #[facet(default)]
    parameters: Vec<ParchmentParameter>,
}

#[derive(Debug, Facet)]
struct ParchmentParameter {
    index: usize,
    name: String,
}

fn generate_mojang_tsrg_mappings(
    merged_mcp_mappings: &Path,
    mojang_mapping_paths: &[PathBuf],
    parchment_parameters: Option<&ParchmentParameters>,
    obf_to_official_output: &Path,
    srg_to_official_output: &Path,
    official_to_srg_output: &Path,
) -> eyre::Result<()> {
    let mojang = read_mojang_mappings(mojang_mapping_paths)?;
    let srg = read_srg_member_mappings(merged_mcp_mappings)?;

    let mut obf_to_official = String::from("tsrg2 left right\n");
    let mut srg_to_official = String::from("tsrg2 left right\n");
    let mut official_to_srg = String::from("tsrg2 left right\n");

    for class in mojang.values() {
        writeln!(obf_to_official, "{} {}", class.obf, class.official_slash)?;
        writeln!(
            srg_to_official,
            "{} {}",
            class.official_slash, class.official_slash
        )?;
        writeln!(
            official_to_srg,
            "{} {}",
            class.official_slash, class.official_slash
        )?;

        let srg_class = srg.get(&class.official_slash);
        for (obf_name, official_name) in &class.fields {
            writeln!(obf_to_official, "\t{obf_name} {official_name}")?;
            if let Some(srg_name) = srg_class.and_then(|mapping| mapping.fields.get(obf_name)) {
                writeln!(srg_to_official, "\t{srg_name} {official_name}")?;
                writeln!(official_to_srg, "\t{official_name} {srg_name}")?;
            }
        }

        for ((obf_name, obf_descriptor), method) in &class.methods {
            writeln!(
                obf_to_official,
                "\t{} {} {}",
                obf_name, obf_descriptor, method.official_name
            )?;
            let candidate_descriptors =
                [obf_descriptor.clone(), method.official_descriptor.clone()];
            if let Some(srg_method) = srg_class.and_then(|mapping| {
                find_srg_method_mapping(
                    mapping,
                    obf_name,
                    &method.official_name,
                    &candidate_descriptors,
                )
            }) {
                writeln!(
                    srg_to_official,
                    "\t{} {} {}",
                    srg_method.srg_name, method.official_descriptor, method.official_name
                )?;
                write_parameter_mappings(
                    &mut srg_to_official,
                    &class.official_slash,
                    method,
                    srg_method,
                    parchment_parameters,
                    ParameterDirection::SrgToOfficial,
                )?;
                writeln!(
                    official_to_srg,
                    "\t{} {} {}",
                    method.official_name, method.official_descriptor, srg_method.srg_name
                )?;
                write_parameter_mappings(
                    &mut official_to_srg,
                    &class.official_slash,
                    method,
                    srg_method,
                    parchment_parameters,
                    ParameterDirection::OfficialToSrg,
                )?;
            }
        }
    }

    if let Some(parent) = obf_to_official_output.parent() {
        fs::create_dir_all(parent)?;
    }
    fs::write(obf_to_official_output, obf_to_official)
        .wrap_err_with(|| format!("Failed to write {}", obf_to_official_output.display()))?;
    fs::write(srg_to_official_output, srg_to_official)
        .wrap_err_with(|| format!("Failed to write {}", srg_to_official_output.display()))?;
    fs::write(official_to_srg_output, official_to_srg)
        .wrap_err_with(|| format!("Failed to write {}", official_to_srg_output.display()))?;
    Ok(())
}

#[instrument(
    level = "debug",
    skip_all,
    fields(input = %srg_to_named.display(), output = %output.display())
)]
fn write_runtime_mcp_csv_mappings(srg_to_named: &Path, output: &Path) -> eyre::Result<()> {
    let mapping_text = fs::read_to_string(srg_to_named)
        .wrap_err_with(|| format!("Failed to read {}", srg_to_named.display()))?;
    let mut fields = String::from("searge,name,desc\n");
    let mut methods = String::from("searge,name,desc\n");

    {
        let _span = tracing::debug_span!(
            "write_runtime_mcp_csv_mappings_parse",
            bytes = mapping_text.len()
        )
        .entered();
        for line in mapping_text.lines() {
            if line.trim().is_empty() || line.starts_with("tsrg") {
                continue;
            }
            if !line.starts_with('\t') && !line.starts_with(' ') {
                continue;
            }
            if line.starts_with("\t\t") || line.starts_with("  ") {
                continue;
            }

            let parts = line.split_whitespace().collect::<Vec<_>>();
            match parts.as_slice() {
                [srg, named] => {
                    if srg.starts_with("f_") && *srg != *named {
                        writeln!(fields, "{srg},{named},")?;
                    }
                }
                [srg, _descriptor, named] if srg.starts_with("m_") && *srg != *named => {
                    writeln!(methods, "{srg},{named},")?;
                }
                _ => {}
            }
        }
    }

    {
        let _span = tracing::debug_span!("write_runtime_mcp_csv_mappings_write").entered();
        fs::create_dir_all(output)?;
        let fields_path = output.join("fields.csv");
        let methods_path = output.join("methods.csv");
        fs::write(&fields_path, fields)
            .wrap_err_with(|| format!("Failed to write {}", fields_path.display()))?;
        fs::write(&methods_path, methods)
            .wrap_err_with(|| format!("Failed to write {}", methods_path.display()))?;
    };
    tracing::info!(
        "Generated Forge runtime MCP CSV mappings: {}",
        output.display()
    );
    Ok(())
}

#[instrument(
    level = "debug",
    skip_all,
    fields(input = %srg_to_named.display(), output = %output.display())
)]
fn write_srg_to_named_mapping_file(srg_to_named: &Path, output: &Path) -> eyre::Result<()> {
    let content = fs::read_to_string(srg_to_named)
        .wrap_err_with(|| format!("Failed to read {}", srg_to_named.display()))?;
    let sections = {
        let _span = tracing::debug_span!(
            "write_srg_to_named_mapping_file_collect_sections",
            bytes = content.len()
        )
        .entered();
        collect_srg_mapping_class_sections(&content)
    };
    let class_mappings = {
        let _span = tracing::debug_span!(
            "write_srg_to_named_mapping_file_build_class_map",
            classes = sections.len()
        )
        .entered();
        sections
            .iter()
            .map(|section| {
                (
                    section.srg_class.to_string(),
                    section.named_class.to_string(),
                )
            })
            .collect::<BTreeMap<_, _>>()
    };
    let rendered_sections = {
        let _span = tracing::debug_span!(
            "write_srg_to_named_mapping_file_render",
            classes = sections.len()
        )
        .entered();
        sections
            .par_iter()
            .map(|section| render_srg_mapping_class_section(section, &class_mappings))
            .collect::<Vec<eyre::Result<_>>>()
            .into_iter()
            .collect::<eyre::Result<Vec<_>>>()?
    };
    let output_text = {
        let _span = tracing::debug_span!(
            "write_srg_to_named_mapping_file_merge_rendered",
            classes = rendered_sections.len()
        )
        .entered();
        rendered_sections.concat()
    };

    {
        let _span = tracing::debug_span!("write_srg_to_named_mapping_file_write").entered();
        if let Some(parent) = output.parent() {
            fs::create_dir_all(parent)?;
        }
        fs::write(output, output_text)
            .wrap_err_with(|| format!("Failed to write {}", output.display()))?;
    };
    tracing::info!("Generated Mixin refmap remap file: {}", output.display());
    Ok(())
}

#[derive(Debug)]
struct SrgMappingClassSection<'a> {
    srg_class: &'a str,
    named_class: &'a str,
    member_lines: Vec<&'a str>,
}

fn collect_srg_mapping_class_sections(content: &str) -> Vec<SrgMappingClassSection<'_>> {
    let mut sections = Vec::new();
    let mut current: Option<SrgMappingClassSection<'_>> = None;
    for line in content.lines() {
        if line.trim().is_empty() || line.starts_with("tsrg") {
            continue;
        }
        if !line.starts_with('\t') && !line.starts_with(' ') {
            let parts = line.split_whitespace().collect::<Vec<_>>();
            if let [srg_class, named_class] = parts.as_slice() {
                if let Some(section) = current.take() {
                    sections.push(section);
                }
                current = Some(SrgMappingClassSection {
                    srg_class,
                    named_class,
                    member_lines: Vec::new(),
                });
            }
            continue;
        }
        if let Some(section) = current.as_mut() {
            section.member_lines.push(line);
        }
    }
    if let Some(section) = current {
        sections.push(section);
    }
    sections
}

fn render_srg_mapping_class_section(
    section: &SrgMappingClassSection<'_>,
    class_mappings: &BTreeMap<String, String>,
) -> eyre::Result<String> {
    let mut output = String::new();
    writeln!(output, "CL: {} {}", section.srg_class, section.named_class)?;
    for line in &section.member_lines {
        if line.starts_with("\t\t") || line.starts_with("  ") {
            continue;
        }

        let parts = line.split_whitespace().collect::<Vec<_>>();
        match parts.as_slice() {
            [srg, named] => {
                writeln!(
                    output,
                    "FD: {}/{} {}/{}",
                    section.srg_class, srg, section.named_class, named
                )?;
            }
            [srg, descriptor, named] => {
                let named_descriptor = remap_descriptor_classes(descriptor, class_mappings);
                writeln!(
                    output,
                    "MD: {}/{} {} {}/{} {}",
                    section.srg_class,
                    srg,
                    descriptor,
                    section.named_class,
                    named,
                    named_descriptor
                )?;
            }
            _ => {}
        }
    }
    Ok(output)
}

fn remap_descriptor_classes(descriptor: &str, class_mappings: &BTreeMap<String, String>) -> String {
    let mut output = String::with_capacity(descriptor.len());
    let mut cursor = 0;
    while let Some(relative_start) = descriptor[cursor..].find('L') {
        let start = cursor + relative_start;
        output.push_str(&descriptor[cursor..=start]);
        let name_start = start + 1;
        let Some(relative_end) = descriptor[name_start..].find(';') else {
            cursor = name_start;
            break;
        };
        let end = name_start + relative_end;
        let class_name = &descriptor[name_start..end];
        output.push_str(
            class_mappings
                .get(class_name)
                .map_or(class_name, String::as_str),
        );
        output.push(';');
        cursor = end + 1;
    }
    output.push_str(&descriptor[cursor..]);
    output
}

#[derive(Clone, Copy)]
enum ParameterDirection {
    SrgToOfficial,
    OfficialToSrg,
}

fn write_parameter_mappings(
    output: &mut String,
    class_name: &str,
    method: &MojangMethodMapping,
    srg_method: &SrgMethodMapping,
    parchment_parameters: Option<&ParchmentParameters>,
    direction: ParameterDirection,
) -> eyre::Result<()> {
    if srg_method.parameters.is_empty() {
        return Ok(());
    }

    let parchment_method = parchment_parameters
        .and_then(|classes| classes.get(class_name))
        .and_then(|methods| {
            methods.get(&(
                method.official_name.clone(),
                method.official_descriptor.clone(),
            ))
        });

    for (index, srg_name) in &srg_method.parameters {
        let parchment_index = if srg_method.is_static {
            *index
        } else {
            index + 1
        };
        let official_name = parchment_method
            .and_then(|parameters| parameters.get(&parchment_index))
            .map_or_else(|| srg_name.clone(), |name| forgegradle_parameter_name(name));
        match direction {
            ParameterDirection::SrgToOfficial => {
                writeln!(output, "\t\t{index} {srg_name} {official_name}")?;
            }
            ParameterDirection::OfficialToSrg => {
                writeln!(output, "\t\t{index} {official_name} {srg_name}")?;
            }
        }
    }

    Ok(())
}

fn forgegradle_parameter_name(name: &str) -> String {
    let mut chars = name.chars();
    let Some(first) = chars.next() else {
        return String::new();
    };
    format!("p{}{}", first.to_uppercase(), chars.collect::<String>())
}

fn read_parchment_parameters(path: &Path) -> eyre::Result<ParchmentParameters> {
    let bytes = fs::read(path).wrap_err_with(|| format!("Failed to read {}", path.display()))?;
    let mut archive = ZipArchive::new(Cursor::new(bytes))
        .wrap_err_with(|| format!("Failed to open Parchment zip {}", path.display()))?;
    let mut entry = archive
        .by_name("parchment.json")
        .wrap_err_with(|| format!("Parchment zip {} has no parchment.json", path.display()))?;
    let mut content = String::new();
    entry
        .read_to_string(&mut content)
        .wrap_err_with(|| format!("Failed to read parchment.json from {}", path.display()))?;
    let data: ParchmentData = facet_json::from_str(&content)
        .wrap_err_with(|| format!("Failed to parse parchment.json from {}", path.display()))?;

    let mut classes = BTreeMap::new();
    for class in data.classes {
        let mut methods = BTreeMap::new();
        for method in class.methods {
            let parameters = method
                .parameters
                .into_iter()
                .map(|parameter| (parameter.index, parameter.name))
                .collect::<BTreeMap<_, _>>();
            if !parameters.is_empty() {
                methods.insert((method.name, method.descriptor), parameters);
            }
        }
        if !methods.is_empty() {
            classes.insert(class.name, methods);
        }
    }

    Ok(classes)
}

fn find_srg_method_mapping<'a>(
    mapping: &'a SrgClassMapping,
    obf_name: &str,
    official_name: &str,
    descriptors: &[String],
) -> Option<&'a SrgMethodMapping> {
    for source_name in [obf_name, official_name] {
        for descriptor in descriptors {
            if let Some(srg_method) = mapping
                .methods
                .get(&(source_name.to_owned(), descriptor.clone()))
            {
                return Some(srg_method);
            }
        }
    }

    for source_name in [obf_name, official_name] {
        let mut candidates = mapping
            .methods
            .iter()
            .filter(|((name, _), _)| name == source_name)
            .map(|(_, srg_method)| srg_method);
        let first = candidates.next();
        if first.is_some() && candidates.next().is_none() {
            return first;
        }
    }

    None
}

fn read_mojang_mappings(paths: &[PathBuf]) -> eyre::Result<BTreeMap<String, MojangClassMapping>> {
    let mut class_names = BTreeMap::new();
    for path in paths {
        let content = fs::read_to_string(path)
            .wrap_err_with(|| format!("Failed to read {}", path.display()))?;
        for line in content.lines() {
            if line.starts_with('#') || line.starts_with(' ') {
                continue;
            }
            if let Some((official, obf_with_colon)) = line.split_once(" -> ") {
                let obf = obf_with_colon.trim_end_matches(':');
                class_names.insert(official.replace('.', "/"), obf.to_string());
            }
        }
    }

    let mut classes = BTreeMap::new();
    for path in paths {
        let content = fs::read_to_string(path)
            .wrap_err_with(|| format!("Failed to read {}", path.display()))?;
        let mut current_official = None::<String>;
        let mut current_obf = None::<String>;
        for line in content.lines() {
            if line.starts_with('#') {
                continue;
            }
            if !line.starts_with(' ') {
                if let Some((official, obf_with_colon)) = line.split_once(" -> ") {
                    let official_slash = official.replace('.', "/");
                    let obf = obf_with_colon.trim_end_matches(':').to_string();
                    classes
                        .entry(official_slash.clone())
                        .or_insert_with(|| MojangClassMapping {
                            official_slash: official_slash.clone(),
                            obf: obf.clone(),
                            fields: BTreeMap::new(),
                            methods: BTreeMap::new(),
                        });
                    current_official = Some(official_slash);
                    current_obf = Some(obf);
                }
                continue;
            }

            let Some(class_name) = &current_official else {
                continue;
            };
            let Some(class_obf) = &current_obf else {
                continue;
            };
            let member = line.trim();
            let Some((left, obf_name)) = member.split_once(" -> ") else {
                continue;
            };
            let class = classes
                .get_mut(class_name)
                .ok_or_else(|| eyre::eyre!("Missing Mojang class mapping for {class_name}"))?;
            if left.contains('(') {
                if let Some((official_name, official_descriptor, obf_descriptor)) =
                    parse_mojang_method_signature(left, &class_names)?
                {
                    class.methods.insert(
                        (obf_name.to_string(), obf_descriptor),
                        MojangMethodMapping {
                            official_name,
                            official_descriptor,
                        },
                    );
                }
            } else if let Some(official_name) = left.split_whitespace().last() {
                class
                    .fields
                    .insert(obf_name.to_string(), official_name.to_string());
            }

            if class.obf != *class_obf {
                eyre::bail!("Conflicting Mojang class mapping for {class_name}");
            }
        }
    }

    Ok(classes)
}

fn read_srg_member_mappings(path: &Path) -> eyre::Result<BTreeMap<String, SrgClassMapping>> {
    let content =
        fs::read_to_string(path).wrap_err_with(|| format!("Failed to read {}", path.display()))?;
    let mut classes = BTreeMap::new();
    let mut current_official = None::<String>;
    let mut current_method_key = None::<(String, String)>;

    for line in content.lines() {
        if line.trim().is_empty() || line.starts_with("tsrg") {
            continue;
        }
        if !line.starts_with('\t') && !line.starts_with(' ') {
            let parts = line.split_whitespace().collect::<Vec<_>>();
            if parts.len() >= 2 {
                let official = parts[1].to_string();
                current_official = Some(official.clone());
                current_method_key = None;
                classes
                    .entry(official.clone())
                    .or_insert_with(|| SrgClassMapping {
                        fields: BTreeMap::new(),
                        methods: BTreeMap::new(),
                    });
            }
            continue;
        }

        if line.starts_with("\t\t") || line.starts_with("  ") {
            let Some(class_name) = &current_official else {
                continue;
            };
            let Some(method_key) = &current_method_key else {
                continue;
            };
            let parts = line.split_whitespace().collect::<Vec<_>>();
            if parts.len() == 1 && parts[0] == "static" {
                if let Some(method) = classes
                    .get_mut(class_name)
                    .and_then(|class| class.methods.get_mut(method_key))
                {
                    method.is_static = true;
                }
                continue;
            }
            if parts.len() >= 3 {
                let index = parts[0].parse::<usize>().wrap_err_with(|| {
                    format!(
                        "Invalid TSRG parameter index {} in {}",
                        parts[0],
                        path.display()
                    )
                })?;
                let parameter_name = parts[parts.len() - 1].to_string();
                if let Some(method) = classes
                    .get_mut(class_name)
                    .and_then(|class| class.methods.get_mut(method_key))
                {
                    method.parameters.insert(index, parameter_name);
                }
            }
            continue;
        }
        let Some(class_name) = &current_official else {
            continue;
        };
        let parts = line.split_whitespace().collect::<Vec<_>>();
        let class = classes
            .get_mut(class_name)
            .ok_or_else(|| eyre::eyre!("Missing SRG class mapping for {class_name}"))?;
        if parts.len() >= 3 && parts[1].starts_with('(') {
            let method_key = (parts[0].to_string(), parts[1].to_string());
            class.methods.insert(
                method_key.clone(),
                SrgMethodMapping {
                    srg_name: parts[2].to_string(),
                    parameters: BTreeMap::new(),
                    is_static: false,
                },
            );
            current_method_key = Some(method_key);
        } else if parts.len() >= 2 {
            class
                .fields
                .insert(parts[0].to_string(), parts[1].to_string());
            current_method_key = None;
        }
    }

    Ok(classes)
}

fn parse_mojang_method_signature(
    raw: &str,
    class_names: &BTreeMap<String, String>,
) -> eyre::Result<Option<(String, String, String)>> {
    let signature = strip_mojang_line_numbers(raw);
    let Some(open_paren) = signature.find('(') else {
        return Ok(None);
    };
    let Some(close_paren) = signature.rfind(')') else {
        return Ok(None);
    };
    let before_args = signature[..open_paren].trim();
    let Some((return_type, method_name)) = before_args.rsplit_once(' ') else {
        return Ok(None);
    };
    let args = &signature[open_paren + 1..close_paren];
    let mut official_descriptor = String::from("(");
    let mut obf_descriptor = String::from("(");
    if !args.trim().is_empty() {
        for arg in args.split(',') {
            official_descriptor.push_str(&type_descriptor(arg.trim(), class_names, false)?);
            obf_descriptor.push_str(&type_descriptor(arg.trim(), class_names, true)?);
        }
    }
    official_descriptor.push(')');
    official_descriptor.push_str(&type_descriptor(return_type.trim(), class_names, false)?);
    obf_descriptor.push(')');
    obf_descriptor.push_str(&type_descriptor(return_type.trim(), class_names, true)?);
    Ok(Some((
        method_name.to_string(),
        official_descriptor,
        obf_descriptor,
    )))
}

fn strip_mojang_line_numbers(raw: &str) -> &str {
    let mut remaining = raw;
    for _ in 0..2 {
        let Some((left, right)) = remaining.split_once(':') else {
            return raw;
        };
        if left.chars().all(|character| character.is_ascii_digit()) {
            remaining = right;
        } else {
            return raw;
        }
    }
    remaining
}

fn type_descriptor(
    raw_type: &str,
    class_names: &BTreeMap<String, String>,
    obfuscate_classes: bool,
) -> eyre::Result<String> {
    let mut ty = raw_type.trim();
    let mut array_depth = 0usize;
    while let Some(stripped) = ty.strip_suffix("[]") {
        array_depth += 1;
        ty = stripped;
    }

    let base = match ty {
        "void" => "V".to_string(),
        "boolean" => "Z".to_string(),
        "byte" => "B".to_string(),
        "char" => "C".to_string(),
        "short" => "S".to_string(),
        "int" => "I".to_string(),
        "long" => "J".to_string(),
        "float" => "F".to_string(),
        "double" => "D".to_string(),
        _ => {
            let official_slash = ty.replace('.', "/");
            let mapped = if obfuscate_classes {
                class_names
                    .get(&official_slash)
                    .map_or(official_slash, Clone::clone)
            } else {
                official_slash
            };
            format!("L{mapped};")
        }
    };

    if array_depth == 0 {
        return Ok(base);
    }
    if base == "V" {
        eyre::bail!("Invalid array type: {raw_type}");
    }
    Ok(format!("{}{base}", "[".repeat(array_depth)))
}

fn resolve_compare_paths(
    options: &CompareOptions,
    target: &WorktreeTarget,
) -> eyre::Result<ComparePaths> {
    let minecraft_dir = target
        .worktree_path
        .as_path()
        .join("platform")
        .join("minecraft");
    let properties = read_properties(&minecraft_dir.join("gradle.properties"))?;
    let minecraft_version = required_property(&properties, "minecraft_version")?;
    let mod_name = required_property(&properties, "mod_name")?;
    let mod_version = required_property(&properties, "mod_version")?;

    Ok(ComparePaths {
        gradle_jar: options.gradle_jar.clone().unwrap_or_else(|| {
            gradle_output_jar_path(&minecraft_dir, mod_name, minecraft_version, mod_version)
        }),
        rust_jar: options.rust_jar.clone().unwrap_or_else(|| {
            rust_output_jar_path(&minecraft_dir, mod_name, minecraft_version, mod_version)
        }),
    })
}

fn compare_jars(
    gradle_jar: &Path,
    rust_jar: &Path,
    strict_manifest: bool,
) -> eyre::Result<JarCompareReport> {
    let gradle = read_normalized_jar(gradle_jar, strict_manifest)?;
    let rust = read_normalized_jar(rust_jar, strict_manifest)?;

    let gradle_names: BTreeSet<String> = gradle.entries.keys().cloned().collect();
    let rust_names: BTreeSet<String> = rust.entries.keys().cloned().collect();

    let missing_entries: Vec<String> = gradle_names.difference(&rust_names).cloned().collect();
    let extra_entries: Vec<String> = rust_names.difference(&gradle_names).cloned().collect();
    let common_entries: Vec<String> = gradle_names.intersection(&rust_names).cloned().collect();

    let changed_entries = common_entries
        .iter()
        .filter_map(|path| {
            let gradle_sha1 = gradle.entries.get(path)?;
            let rust_sha1 = rust.entries.get(path)?;
            (gradle_sha1 != rust_sha1).then(|| ChangedEntry {
                path: path.clone(),
                gradle_hash: *gradle_sha1,
                rust_hash: *rust_sha1,
            })
        })
        .collect::<Vec<_>>();

    let manifest_compared = gradle.manifest_sha1.is_some() || rust.manifest_sha1.is_some();
    let manifest_changed = gradle.manifest_sha1 != rust.manifest_sha1;
    let manifest = ManifestCompare {
        compared: manifest_compared,
        changed: manifest_changed,
        ignored_implementation_timestamp: !strict_manifest,
        gradle_sha1: gradle.manifest_sha1,
        rust_sha1: rust.manifest_sha1,
    };

    let matches = missing_entries.is_empty()
        && extra_entries.is_empty()
        && changed_entries.is_empty()
        && !manifest.changed;

    Ok(JarCompareReport {
        gradle_jar: gradle_jar.to_path_buf(),
        rust_jar: rust_jar.to_path_buf(),
        strict_manifest,
        matches,
        total_gradle_entries: gradle.total_entries,
        total_rust_entries: rust.total_entries,
        compared_entries: common_entries.len() + usize::from(manifest_compared),
        missing_entries,
        extra_entries,
        changed_entries,
        manifest,
    })
}

fn read_normalized_jar(path: &Path, strict_manifest: bool) -> eyre::Result<NormalizedJar> {
    if !path.is_file() {
        eyre::bail!("Jar does not exist: {}", path.display());
    }

    let bytes = fs::read(path).wrap_err_with(|| format!("Failed to read {}", path.display()))?;
    let cursor = Cursor::new(bytes);
    let mut archive = ZipArchive::new(cursor)
        .wrap_err_with(|| format!("Failed to open jar {}", path.display()))?;
    let mut entries = BTreeMap::new();
    let mut manifest_sha1 = None;
    let mut total_entries = 0usize;

    for index in 0..archive.len() {
        let mut file = archive.by_index(index).wrap_err_with(|| {
            format!("Failed to read jar entry #{index} from {}", path.display())
        })?;
        let name = file.name().replace('\\', "/");
        if name.ends_with('/') {
            continue;
        }

        total_entries += 1;
        let mut entry_bytes = Vec::new();
        file.read_to_end(&mut entry_bytes)
            .wrap_err_with(|| format!("Failed to read jar entry {name} from {}", path.display()))?;

        if name.eq_ignore_ascii_case("META-INF/MANIFEST.MF") {
            let normalized = normalize_manifest_bytes(&entry_bytes, strict_manifest);
            manifest_sha1 = Some(ContentHash::from_bytes(
                normalized.as_bytes(),
                ContentHashAlgorithm::Blake3,
            ));
        } else {
            entries.insert(
                name,
                ContentHash::from_bytes(&entry_bytes, ContentHashAlgorithm::Blake3),
            );
        }
    }

    Ok(NormalizedJar {
        entries,
        manifest_sha1,
        total_entries,
    })
}

fn normalize_manifest_bytes(bytes: &[u8], strict_manifest: bool) -> String {
    let text = String::from_utf8_lossy(bytes)
        .replace("\r\n", "\n")
        .replace('\r', "\n");
    if strict_manifest {
        return text;
    }

    let mut lines = Vec::new();
    let mut skipping_timestamp = false;
    for line in text.split('\n') {
        if skipping_timestamp && line.starts_with(' ') {
            continue;
        }
        skipping_timestamp = false;
        if line.starts_with("Implementation-Timestamp:") {
            skipping_timestamp = true;
            continue;
        }
        lines.push(line);
    }

    lines.join("\n")
}

fn emit_compare_report(report: &JarCompareReport) {
    tracing::info!("Gradle jar: {}", report.gradle_jar.display());
    tracing::info!("Rust jar:   {}", report.rust_jar.display());
    tracing::info!("Compared entries: {}", report.compared_entries);
    tracing::info!("Gradle entries:   {}", report.total_gradle_entries);
    tracing::info!("Rust entries:     {}", report.total_rust_entries);
    tracing::info!("Missing entries:  {}", report.missing_entries.len());
    tracing::info!("Extra entries:    {}", report.extra_entries.len());
    tracing::info!("Changed entries:  {}", report.changed_entries.len());
    tracing::info!(
        "Manifest changed: {}",
        if report.manifest.changed { "yes" } else { "no" }
    );

    emit_string_list("Missing", &report.missing_entries);
    emit_string_list("Extra", &report.extra_entries);
    emit_changed_entries(&report.changed_entries);

    if report.matches {
        tracing::info!("Jar comparison passed: normalized jars match.");
    } else {
        tracing::info!("Jar comparison failed: normalized jars differ.");
    }
}

fn emit_string_list(label: &str, entries: &[String]) {
    if entries.is_empty() {
        return;
    }

    tracing::info!("{label} entry sample:");
    for entry in entries.iter().take(20) {
        tracing::info!(" - {entry}");
    }
    if entries.len() > 20 {
        tracing::info!(" - ... {} more", entries.len() - 20);
    }
}

fn emit_changed_entries(entries: &[ChangedEntry]) {
    if entries.is_empty() {
        return;
    }

    tracing::info!("Changed entry sample:");
    for entry in entries.iter().take(20) {
        tracing::info!(
            " - {} (gradle {}, rust {})",
            entry.path,
            entry.gradle_hash,
            entry.rust_hash
        );
    }
    if entries.len() > 20 {
        tracing::info!(" - ... {} more", entries.len() - 20);
    }
}

fn write_compare_reports(
    reports: &[TargetJarCompareReport],
    requested_path: Option<&Path>,
) -> eyre::Result<()> {
    let Some(path) = requested_path else {
        return Ok(());
    };

    if let Some(parent) = path.parent() {
        fs::create_dir_all(parent)?;
    }
    let json = if reports.len() == 1 {
        facet_json::to_string_pretty(&reports[0].report)?
    } else {
        facet_json::to_string_pretty(reports)?
    };
    fs::write(path, json).wrap_err_with(|| format!("Failed to write {}", path.display()))?;
    Ok(())
}

fn write_last_plan_output(plan: &BuildPlan) -> eyre::Result<()> {
    fs::create_dir_all(&plan.state_dir)?;
    let plan_json = facet_json::to_string_pretty(plan)?;
    let last_plan_path = plan.state_dir.join("last-plan.json");
    fs::write(&last_plan_path, &plan_json)
        .wrap_err_with(|| format!("Failed to write {}", last_plan_path.display()))?;

    Ok(())
}

fn write_requested_plan_outputs(
    plans: &[BuildPlan],
    requested_path: Option<&Path>,
) -> eyre::Result<()> {
    let Some(path) = requested_path else {
        return Ok(());
    };

    if let Some(parent) = path.parent() {
        fs::create_dir_all(parent)?;
    }

    let plan_json = if let [plan] = plans {
        facet_json::to_string_pretty(plan)?
    } else {
        facet_json::to_string_pretty(&plans)?
    };
    fs::write(path, &plan_json).wrap_err_with(|| format!("Failed to write {}", path.display()))?;

    Ok(())
}

fn write_artifact_lockfile(plan: &BuildPlan) -> eyre::Result<()> {
    write_artifact_lockfile_with_extra_cache_paths(plan, &[])
}

#[tracing::instrument(
    level = "info",
    skip_all,
    fields(
        branch = %plan.branch_name,
        mc = %plan.minecraft_version,
        artifacts = plan.artifacts.len(),
        dependencies = plan.dependencies.len(),
        extra_cache_paths = extra_cache_paths.len(),
    )
)]
fn write_artifact_lockfile_with_extra_cache_paths(
    plan: &BuildPlan,
    extra_cache_paths: &[PathBuf],
) -> eyre::Result<()> {
    if std::fs::read_to_string(&plan.lockfile_path)
        .ok()
        .and_then(|input| crate::toolchain_lockfile_schema::read_current(&input).ok())
        .is_some()
    {
        tracing::debug!(
            lockfile = %plan.lockfile_path.display(),
            "schema v3 lockfile is declaration-owned; legacy build output will not rewrite it"
        );
        return Ok(());
    }
    let lockfile = build_artifact_lockfile(plan, extra_cache_paths)?;
    if let Some(parent) = plan.lockfile_path.parent() {
        fs::create_dir_all(parent)?;
    }
    fs::write(
        &plan.lockfile_path,
        facet_json::to_string_pretty(&lockfile)?,
    )
    .wrap_err_with(|| format!("Failed to write {}", plan.lockfile_path.display()))?;
    tracing::info!(
        "Artifact lockfile: {} ({} artifacts)",
        plan.lockfile_path.display(),
        lockfile.artifacts.len()
    );
    Ok(())
}

#[instrument(
    level = "debug",
    skip_all,
    fields(
        branch = %plan.branch_name,
        mc = %plan.minecraft_version
    )
)]
#[expect(
    clippy::too_many_lines,
    reason = "Lockfile construction deliberately keeps artifact/dependency/extra sections in one pass."
)]
fn build_artifact_lockfile(
    plan: &BuildPlan,
    extra_cache_paths: &[PathBuf],
) -> eyre::Result<ArtifactLockfile> {
    let mut artifacts = Vec::new();
    let mut entries = Vec::new();
    if let Some(existing_lockfile) = &plan.lockfile {
        let _span = tracing::debug_span!(
            "artifact_lock_migrate_entries",
            entries = existing_lockfile.artifacts.len()
        )
        .entered();
        entries.extend(
            existing_lockfile
                .artifacts
                .par_iter()
                .map(|locked| migrate_locked_artifact(plan, locked).map(Some))
                .collect::<Vec<eyre::Result<_>>>()
                .into_iter()
                .collect::<eyre::Result<Vec<_>>>()
                .wrap_err("Failed to migrate artifact lock entry")?,
        );
    }
    {
        let _span =
            tracing::debug_span!("artifact_lock_plan_entries", entries = plan.artifacts.len())
                .entered();
        entries.extend(
            plan.artifacts
                .par_iter()
                .map(|artifact| artifact_lock_entry_from_plan_artifact(plan, artifact).map(Some))
                .collect::<Vec<eyre::Result<_>>>()
                .into_iter()
                .collect::<eyre::Result<Vec<_>>>()
                .wrap_err("Failed to build planned artifact lock entry")?,
        );
    };
    {
        let _span = tracing::debug_span!(
            "artifact_lock_dependency_entries",
            entries = plan.dependencies.len()
        )
        .entered();
        entries.extend(
            plan.dependencies
                .par_iter()
                .map(|dependency| {
                    artifact_lock_entry_from_cache_path(
                        plan,
                        &dependency.cache_path,
                        Some(&dependency.resolved_notation),
                    )
                    .map(Some)
                })
                .collect::<Vec<eyre::Result<_>>>()
                .into_iter()
                .collect::<eyre::Result<Vec<_>>>()
                .wrap_err("Failed to build dependency artifact lock entry")?,
        );
    };
    {
        let _span = tracing::debug_span!(
            "artifact_lock_extra_entries",
            entries = extra_cache_paths.len()
        )
        .entered();
        entries.extend(
            extra_cache_paths
                .par_iter()
                .map(|path| {
                    if should_record_extra_cache_artifact(plan, path)? {
                        artifact_lock_entry_from_cache_path(plan, path, None).map(Some)
                    } else {
                        Ok(None)
                    }
                })
                .collect::<Vec<eyre::Result<_>>>()
                .into_iter()
                .collect::<eyre::Result<Vec<_>>>()
                .wrap_err("Failed to build extra artifact lock entry")?,
        );
    };

    for entry in entries.into_iter().flatten() {
        push_artifact_lock_entry(&mut artifacts, entry);
    }

    artifacts.sort_by(|left, right| {
        (
            left.coordinate.as_deref(),
            left.repository.as_deref(),
            left.cache_path.as_path(),
        )
            .cmp(&(
                right.coordinate.as_deref(),
                right.repository.as_deref(),
                right.cache_path.as_path(),
            ))
    });

    Ok(ArtifactLockfile {
        schema_version: crate::toolchain_lockfile_schema::ENGINE_SCHEMA_VERSION,
        minecraft_version: plan.minecraft_version.to_string(),
        maven_cache_dir: portable_cache_path(plan, &plan.maven_cache_dir),
        allow_local_artifact_cache: plan.allow_local_artifact_cache,
        repositories: plan.repositories.clone(),
        dependencies: plan
            .dependencies
            .iter()
            .map(|dependency| DependencyLockEntry {
                configuration: dependency.configuration.clone(),
                notation: dependency.notation.clone(),
                resolved_notation: dependency.resolved_notation.clone(),
                source: dependency.source.clone(),
                dynamic_version: dependency.dynamic_version,
                cache_path: portable_cache_path(plan, &dependency.cache_path),
            })
            .collect(),
        artifacts,
    })
}

fn should_record_extra_cache_artifact(plan: &BuildPlan, path: &Path) -> eyre::Result<bool> {
    Ok(path.is_file()
        && path.starts_with(&plan.maven_cache_dir)
        && read_artifact_provenance(path)?.is_some())
}

fn migrate_locked_artifact(
    plan: &BuildPlan,
    locked: &ArtifactLockEntry,
) -> eyre::Result<ArtifactLockEntry> {
    let Some(coordinate_text) = locked.coordinate.as_deref() else {
        return Ok(locked.clone());
    };
    let Ok(coordinate) = MavenCoordinate::parse(coordinate_text) else {
        return Ok(locked.clone());
    };
    let cache_path = maven_cache_path_for(&plan.maven_cache_dir, &coordinate);
    if !cache_path.is_file() {
        return Ok(locked.clone());
    }
    let legacy_actual_hash = ContentHash::from_path(&cache_path, locked.hash.algorithm)?;
    if legacy_actual_hash != locked.hash {
        return Ok(locked.clone());
    }
    let actual_hash = if locked.hash.algorithm == ContentHashAlgorithm::Blake3 {
        legacy_actual_hash
    } else {
        ContentHash::from_path(&cache_path, ContentHashAlgorithm::Blake3)?
    };
    let provenance = read_artifact_provenance(&cache_path)?.unwrap_or_else(|| {
        artifact_provenance(
            locked.source.clone(),
            locked.coordinate.clone(),
            locked.repository.clone(),
            locked.url.clone(),
            locked.original_path.clone(),
            locked.source_git.clone(),
            actual_hash,
        )
    });
    Ok(ArtifactLockEntry {
        coordinate: provenance.coordinate.or_else(|| locked.coordinate.clone()),
        source: provenance.source,
        repository: provenance.repository.or_else(|| locked.repository.clone()),
        url: provenance.url.or_else(|| locked.url.clone()),
        cache_path: portable_cache_path(plan, &cache_path),
        original_path: provenance
            .original_path
            .or_else(|| locked.original_path.clone()),
        source_relative_path: provenance
            .source_relative_path
            .or_else(|| locked.source_relative_path.clone()),
        source_git: provenance.source_git.or_else(|| locked.source_git.clone()),
        source_build: provenance
            .source_build
            .or_else(|| locked.source_build.clone()),
        hash: actual_hash,
        weak: locked.weak.clone(),
    })
}

fn artifact_lock_entry_from_plan_artifact(
    plan: &BuildPlan,
    artifact: &ArtifactPlan,
) -> eyre::Result<ArtifactLockEntry> {
    let actual_hash = artifact_actual_hash(&artifact.cache_path, artifact.sha1.as_ref())?;
    let provenance_actual_hash = if artifact.cache_path.is_file()
        && artifact.provenance.hash.algorithm == actual_hash.algorithm
    {
        actual_hash
    } else {
        ContentHash::from_path(&artifact.cache_path, artifact.provenance.hash.algorithm)?
    };
    if provenance_actual_hash != artifact.provenance.hash {
        eyre::bail!(
            "Artifact provenance hash mismatch for {}: sidecar {}, actual {}",
            artifact.cache_path.display(),
            artifact.provenance.hash,
            provenance_actual_hash
        );
    }
    Ok(ArtifactLockEntry {
        coordinate: artifact
            .provenance
            .coordinate
            .clone()
            .or_else(|| artifact.coordinate.clone()),
        source: artifact.provenance.source.clone(),
        repository: artifact.provenance.repository.clone(),
        url: artifact.provenance.url.clone(),
        cache_path: portable_cache_path(plan, &artifact.cache_path),
        original_path: artifact.provenance.original_path.clone(),
        source_relative_path: artifact.provenance.source_relative_path.clone(),
        source_git: artifact.provenance.source_git.clone(),
        source_build: artifact.provenance.source_build.clone(),
        hash: actual_hash,
        weak: None,
    })
}

fn artifact_lock_entry_from_cache_path(
    plan: &BuildPlan,
    path: &Path,
    fallback_coordinate: Option<&str>,
) -> eyre::Result<ArtifactLockEntry> {
    let actual_hash = ContentHash::from_path(path, ContentHashAlgorithm::Blake3)?;
    let provenance = read_artifact_provenance(path)?.unwrap_or_else(|| {
        artifact_provenance(
            ArtifactSource::ExistingSfmCacheUnknown,
            fallback_coordinate.map(str::to_string),
            None,
            None,
            None,
            None,
            actual_hash,
        )
    });
    let provenance_actual_hash = if provenance.hash.algorithm == ContentHashAlgorithm::Blake3 {
        actual_hash
    } else {
        ContentHash::from_path(path, provenance.hash.algorithm)?
    };
    if provenance_actual_hash != provenance.hash {
        eyre::bail!(
            "Artifact provenance hash mismatch for {}: sidecar {}, actual {}",
            path.display(),
            provenance.hash,
            provenance_actual_hash
        );
    }
    Ok(ArtifactLockEntry {
        coordinate: provenance.coordinate,
        source: provenance.source,
        repository: provenance.repository,
        url: provenance.url,
        cache_path: portable_cache_path(plan, path),
        original_path: provenance.original_path,
        source_relative_path: provenance.source_relative_path,
        source_git: provenance.source_git,
        source_build: provenance.source_build,
        hash: actual_hash,
        weak: None,
    })
}

fn artifact_actual_hash(
    path: &Path,
    planned_hash: Option<&ContentHash>,
) -> eyre::Result<ContentHash> {
    if path.is_file() {
        let actual_hash = ContentHash::from_path(path, ContentHashAlgorithm::Blake3)?;
        if let Some(planned_hash) = planned_hash {
            let planned_actual_hash = if planned_hash.algorithm == ContentHashAlgorithm::Blake3 {
                actual_hash
            } else {
                ContentHash::from_path(path, planned_hash.algorithm)?
            };
            if planned_actual_hash != *planned_hash {
                eyre::bail!(
                    "Artifact {} resolved with content hash {}, but the plan recorded {}",
                    path.display(),
                    planned_actual_hash,
                    planned_hash
                );
            }
        }
        return Ok(actual_hash);
    }
    planned_hash.copied().ok_or_else(|| {
        eyre::eyre!(
            "Artifact is missing and has no content hash: {}",
            path.display()
        )
    })
}

fn push_artifact_lock_entry(artifacts: &mut Vec<ArtifactLockEntry>, entry: ArtifactLockEntry) {
    if let Some(existing) = artifacts
        .iter()
        .position(|artifact| artifact.same_locked_artifact(&entry))
    {
        let mut entry = entry;
        if entry.weak.is_none() {
            entry.weak.clone_from(&artifacts[existing].weak);
        }
        artifacts[existing] = entry;
        return;
    }
    artifacts.push(entry);
}

impl ArtifactLockEntry {
    fn same_locked_artifact(&self, other: &Self) -> bool {
        if self.coordinate.is_some() || other.coordinate.is_some() {
            return self.coordinate == other.coordinate;
        }
        self.cache_path == other.cache_path
    }
}

fn portable_cache_path(plan: &BuildPlan, path: &Path) -> PathBuf {
    if let Ok(relative) = path.strip_prefix(&plan.common_cache_dir) {
        return PathBuf::from("$sfm-cache").join(relative);
    }
    relative_path(&plan.minecraft_dir, path)
}

fn read_optional_artifact_lockfile(
    path: &Path,
    minecraft_version: &str,
) -> eyre::Result<Option<ArtifactLockfile>> {
    if !path.is_file() {
        return Ok(None);
    }
    let content =
        fs::read_to_string(path).wrap_err_with(|| format!("Failed to read {}", path.display()))?;
    let lockfile = crate::toolchain_lockfile_schema::upgrade_to_latest(&content)
        .wrap_err_with(|| format!("Failed to parse {}", path.display()))?;
    if lockfile.minecraft_version != minecraft_version {
        eyre::bail!(
            "Lockfile {} is for Minecraft {}, but this plan is for {}",
            path.display(),
            lockfile.minecraft_version,
            minecraft_version
        );
    }
    Ok(Some(lockfile))
}

fn relative_path(base: &Path, path: &Path) -> PathBuf {
    path.strip_prefix(base)
        .map_or_else(|_| path.to_path_buf(), Path::to_path_buf)
}

fn print_plan_summary(plan: &BuildPlan) {
    let dependency_label = if plan.loader_toolchain.kind == LoaderToolchainKind::NeoGradleUserdev {
        "project deps"
    } else {
        "fg.deobf deps"
    };
    let lines = [
        "Clean-slate jar build plan resolved.".to_string(),
        format!("Minecraft:    {}", plan.minecraft_version),
        format!("Worktree:     {}", plan.worktree_path.display()),
        format!("Gradle jar:   {}", plan.gradle_output_jar.display()),
        format!("Rust jar:     {}", plan.rust_output_jar.display()),
        format!("Java:         {}", plan.java.executable.display()),
        format!("Java release: {}", plan.java_release),
        format!("Common cache: {}", plan.common_cache_dir.display()),
        format!(
            "Toolchain:    {:?} ({})",
            plan.loader_toolchain.kind, plan.loader_toolchain.userdev_coordinate
        ),
        format!(
            "State:        {}",
            plan.state_dir.join("last-plan.json").display()
        ),
        format!("Lockfile:     {}", plan.lockfile_path.display()),
        format!("Artifacts:    {}", plan.artifacts.len()),
        format!(
            "Portable:     {}/{} artifacts",
            plan.artifact_portability.portable_artifacts, plan.artifact_portability.total_artifacts
        ),
        format!("{dependency_label}: {}", plan.dependencies.len()),
        format!("Graph nodes:  {}", plan.graph.len()),
    ];
    for line in lines {
        tracing::info!("{line}");
    }

    for warning in &plan.warnings {
        tracing::warn!("Warning: {warning}");
    }
}

#[instrument(level = "info", skip_all, fields(minecraft_version = %minecraft_version))]
fn read_java_toolchain_release(minecraft_dir: &Path, minecraft_version: &str) -> eyre::Result<u32> {
    let path = minecraft_dir
        .join("gradle")
        .join("java-toolchain")
        .join(minecraft_version)
        .join("java-toolchain.gradle");
    let content =
        fs::read_to_string(&path).wrap_err_with(|| format!("Failed to read {}", path.display()))?;
    let start = content.find("JavaLanguageVersion.of(").ok_or_else(|| {
        eyre::eyre!(
            "Could not find JavaLanguageVersion.of(...) in {}",
            path.display()
        )
    })? + "JavaLanguageVersion.of(".len();
    let end = content[start..]
        .find(')')
        .ok_or_else(|| eyre::eyre!("Could not parse Java toolchain in {}", path.display()))?
        + start;
    content[start..end]
        .trim()
        .parse()
        .wrap_err_with(|| format!("Could not parse Java release from {}", path.display()))
}

#[instrument(level = "info", skip_all, fields(loader_toolchain = ?loader_toolchain.kind, java_release = %java_release))]
fn required_java_runtime_major(loader_toolchain: &LoaderToolchainPlan, java_release: u32) -> u32 {
    if loader_toolchain.kind == LoaderToolchainKind::NeoGradleUserdev {
        java_release.max(21)
    } else {
        java_release
    }
}

fn javac_executable(java: &JavaPlan) -> PathBuf {
    java.home.as_ref().map_or_else(
        || {
            if cfg!(windows) {
                PathBuf::from("javac.exe")
            } else {
                PathBuf::from("javac")
            }
        },
        |home| {
            home.join("bin")
                .join(if cfg!(windows) { "javac.exe" } else { "javac" })
        },
    )
}

fn canonicalize_lenient(path: &Path) -> eyre::Result<PathBuf> {
    if path.exists() {
        return dunce::canonicalize(path)
            .wrap_err_with(|| format!("Failed to canonicalize {}", path.display()));
    }

    let parent = path
        .parent()
        .ok_or_else(|| eyre::eyre!("Path has no parent: {}", path.display()))?;
    let canonical_parent = if parent.exists() {
        dunce::canonicalize(parent)
            .wrap_err_with(|| format!("Failed to canonicalize {}", parent.display()))?
    } else {
        canonicalize_lenient(parent)?
    };
    let file_name = path
        .file_name()
        .ok_or_else(|| eyre::eyre!("Path has no file name: {}", path.display()))?;
    Ok(canonical_parent.join(file_name))
}

fn relative_zip_name(root: &Path, path: &Path) -> eyre::Result<String> {
    let relative = path
        .strip_prefix(root)
        .wrap_err_with(|| format!("{} is not under {}", path.display(), root.display()))?;
    Ok(relative.to_string_lossy().replace('\\', "/"))
}

fn gradle_output_jar_path(
    minecraft_dir: &Path,
    mod_name: &str,
    minecraft_version: &str,
    mod_version: &str,
) -> PathBuf {
    minecraft_dir.join("build").join("libs").join(format!(
        "{mod_name}-MC{minecraft_version}-{mod_version}.jar"
    ))
}

fn rust_output_jar_path(
    minecraft_dir: &Path,
    mod_name: &str,
    minecraft_version: &str,
    mod_version: &str,
) -> PathBuf {
    minecraft_dir.join("build").join("libs").join(format!(
        "{mod_name}-MC{minecraft_version}-{mod_version}-rust.jar"
    ))
}

fn read_properties(path: &Path) -> eyre::Result<BTreeMap<String, String>> {
    let content = fs::read_to_string(path)
        .wrap_err_with(|| format!("Failed to read properties file: {}", path.display()))?;
    let mut properties = BTreeMap::new();

    for line in content.lines().map(str::trim) {
        if line.is_empty() || line.starts_with('#') {
            continue;
        }
        if let Some((key, value)) = line.split_once('=') {
            properties.insert(key.trim().to_string(), value.trim().to_string());
        }
    }

    Ok(properties)
}

fn required_property<'a>(
    properties: &'a BTreeMap<String, String>,
    key: &str,
) -> eyre::Result<&'a str> {
    properties
        .get(key)
        .map(String::as_str)
        .filter(|value| !value.is_empty())
        .ok_or_else(|| eyre::eyre!("Missing required gradle.properties key: {key}"))
}

fn plain_artifact(
    id: ArtifactId,
    url: &str,
    cache_path: PathBuf,
    required_for: ArtifactPurpose,
) -> eyre::Result<ArtifactPlan> {
    let hash = ContentHash::from_path(&cache_path, ContentHashAlgorithm::Blake3)?;
    Ok(ArtifactPlan {
        id,
        coordinate: None,
        repository: None,
        url: Some(url.to_string()),
        sha1: Some(hash),
        cache_path,
        downloaded: true,
        required_for,
        provenance: artifact_provenance(
            ArtifactSource::RemoteHttp,
            None,
            None,
            Some(url.to_string()),
            None,
            None,
            hash,
        ),
    })
}

fn source_git_provenance(path: &Path) -> Option<SourceGitProvenance> {
    let working_dir = if path.is_dir() { path } else { path.parent()? };
    let root = PathBuf::from(git_stdout(working_dir, ["rev-parse", "--show-toplevel"])?);
    let commit = git_stdout(&root, ["rev-parse", "HEAD"])?;
    let branch = git_stdout(&root, ["rev-parse", "--abbrev-ref", "HEAD"])?;
    let status = git_stdout(&root, ["status", "--porcelain"])?;
    let remote_url = git_stdout(&root, ["remote", "get-url", "origin"]);
    Some(SourceGitProvenance {
        root,
        commit,
        branch,
        dirty: !status.trim().is_empty(),
        remote_url,
    })
}

fn git_stdout<const N: usize>(working_dir: &Path, args: [&str; N]) -> Option<String> {
    let output = Command::new("git")
        .arg("-C")
        .arg(working_dir)
        .args(args)
        .output()
        .ok()?;
    if !output.status.success() {
        return None;
    }
    let stdout = String::from_utf8(output.stdout).ok()?;
    Some(stdout.trim().to_string())
}

#[instrument(level = "debug", skip_all, fields(source = ?source, coordinate, repository, url, original_path = ?original_path, source_git = ?source_git))]
fn artifact_provenance(
    source: ArtifactSource,
    coordinate: Option<String>,
    repository: Option<String>,
    url: Option<String>,
    original_path: Option<PathBuf>,
    source_git: Option<SourceGitProvenance>,
    hash: ContentHash,
) -> ArtifactProvenance {
    let source_relative_path = source_relative_path(original_path.as_deref(), source_git.as_ref());
    let source_build = source_build_provenance(
        &source,
        coordinate.as_deref(),
        source_relative_path.as_deref(),
        source_git.as_ref(),
    );
    ArtifactProvenance {
        schema_version: 1,
        source,
        coordinate,
        repository,
        url,
        original_path,
        source_relative_path,
        source_git,
        source_build,
        hash,
    }
}

fn source_build_provenance(
    source: &ArtifactSource,
    coordinate: Option<&str>,
    source_relative_path: Option<&Path>,
    source_git: Option<&SourceGitProvenance>,
) -> Option<SourceBuildProvenance> {
    if source != &ArtifactSource::ExplicitSource {
        return None;
    }
    let source_git = source_git?;
    let source_relative_path = source_relative_path?;
    if !source_git.root.join("gradlew").is_file() && !source_git.root.join("gradlew.bat").is_file()
    {
        return None;
    }
    let coordinate = coordinate.and_then(|coordinate| MavenCoordinate::parse(coordinate).ok())?;
    let mut environment = BTreeMap::new();
    if let Some(build_number) = explicit_source_build_number(&coordinate) {
        environment.insert("BUILD_NUMBER".to_string(), build_number);
    }

    Some(SourceBuildProvenance {
        build_system: SourceBuildSystem::GradleWrapper,
        tasks: vec![explicit_source_build_task(&coordinate)],
        environment,
        output_path: source_relative_path.to_path_buf(),
    })
}

fn explicit_source_build_task(coordinate: &MavenCoordinate) -> String {
    coordinate.classifier.as_ref().map_or_else(
        || "jar".to_string(),
        |classifier| format!("{classifier}Jar"),
    )
}

fn explicit_source_build_number(coordinate: &MavenCoordinate) -> Option<String> {
    if coordinate.group != "mekanism" || coordinate.artifact != "Mekanism" {
        return None;
    }
    let (_, basic_version) = coordinate.version.rsplit_once('-')?;
    let (_, build_number) = basic_version.rsplit_once('.')?;
    build_number
        .chars()
        .all(|character| character.is_ascii_digit())
        .then(|| build_number.to_string())
}

fn source_build_checkout_key(remote_url: &str, commit: &str) -> String {
    ContentHash::from_bytes(
        format!("{remote_url}\n{commit}").as_bytes(),
        ContentHashAlgorithm::Blake3,
    )
    .hex()
}

fn materialize_source_build(
    cancellation_token: &CancellationToken,
    remote_url: &str,
    commit: &str,
    source_build: &SourceBuildProvenance,
    checkout_dir: &Path,
    repository_dir: &Path,
) -> eyre::Result<()> {
    match source_build.build_system {
        SourceBuildSystem::GradleWrapper => materialize_gradle_wrapper_source_build(
            cancellation_token,
            remote_url,
            commit,
            source_build,
            checkout_dir,
            repository_dir,
        ),
    }
}

fn materialize_gradle_wrapper_source_build(
    cancellation_token: &CancellationToken,
    remote_url: &str,
    commit: &str,
    source_build: &SourceBuildProvenance,
    checkout_dir: &Path,
    repository_dir: &Path,
) -> eyre::Result<()> {
    cancellation_token.bail_if_cancelled()?;
    if source_build.tasks.is_empty() {
        eyre::bail!("Source build for {remote_url}@{commit} has no Gradle tasks");
    }
    prepare_source_build_checkout(
        cancellation_token,
        remote_url,
        commit,
        checkout_dir,
        repository_dir,
    )?;
    cancellation_token.bail_if_cancelled()?;
    let wrapper = gradle_wrapper_path(checkout_dir)?;
    tracing::info!(
        remote = remote_url,
        commit,
        checkout = %checkout_dir.display(),
        tasks = ?source_build.tasks,
        output = %source_build.output_path.display(),
        "running source build"
    );
    let mut command = Command::new(wrapper);
    command
        .current_dir(checkout_dir)
        .arg("--no-daemon")
        .args(&source_build.tasks);
    for (key, value) in &source_build.environment {
        command.env(key, value);
    }
    run_source_build_process(
        cancellation_token,
        &mut command,
        "source-build-gradle-wrapper",
    )?;
    Ok(())
}

fn prepare_source_build_checkout(
    cancellation_token: &CancellationToken,
    remote_url: &str,
    commit: &str,
    checkout_dir: &Path,
    repository_dir: &Path,
) -> eyre::Result<()> {
    crate::source_git::materialize_source_build_checkout(
        remote_url,
        repository_dir,
        commit,
        checkout_dir,
        cancellation_token,
    )
}

fn gradle_wrapper_path(checkout_dir: &Path) -> eyre::Result<PathBuf> {
    #[cfg(windows)]
    let wrapper = checkout_dir.join("gradlew.bat");
    #[cfg(not(windows))]
    let wrapper = checkout_dir.join("gradlew");

    if !wrapper.is_file() {
        eyre::bail!(
            "Source build checkout {} does not contain {}",
            checkout_dir.display(),
            wrapper
                .file_name()
                .and_then(std::ffi::OsStr::to_str)
                .unwrap_or("gradlew")
        );
    }
    Ok(wrapper)
}

fn run_source_build_process(
    cancellation_token: &CancellationToken,
    command: &mut Command,
    process_name: &str,
) -> eyre::Result<()> {
    let output = run_command_capture_output(cancellation_token, command, process_name)?;
    if !output.stdout.is_empty() {
        tracing::debug!(
            process = process_name,
            stream = "stdout",
            "{}",
            String::from_utf8_lossy(&output.stdout)
        );
    }
    if !output.stderr.is_empty() {
        tracing::debug!(
            process = process_name,
            stream = "stderr",
            "{}",
            String::from_utf8_lossy(&output.stderr)
        );
    }
    if output.cancelled {
        eyre::bail!("{process_name} was cancelled");
    }
    if !output.status.success() {
        eyre::bail!(
            "{process_name} failed with status {}\nstdout:\n{}\nstderr:\n{}",
            output.status,
            String::from_utf8_lossy(&output.stdout),
            String::from_utf8_lossy(&output.stderr)
        );
    }
    Ok(())
}

fn source_relative_path(
    original_path: Option<&Path>,
    source_git: Option<&SourceGitProvenance>,
) -> Option<PathBuf> {
    let original_path = original_path?;
    let source_root = &source_git?.root;
    let canonical_original = dunce::canonicalize(original_path).ok();
    let canonical_root = dunce::canonicalize(source_root).ok();
    if let (Some(canonical_original), Some(canonical_root)) = (&canonical_original, &canonical_root)
        && let Ok(relative) = canonical_original.strip_prefix(canonical_root)
    {
        return Some(relative.to_path_buf());
    }
    original_path
        .strip_prefix(source_root)
        .ok()
        .map(Path::to_path_buf)
}

fn artifact_provenance_path(path: &Path) -> eyre::Result<PathBuf> {
    let file_name = path
        .file_name()
        .and_then(std::ffi::OsStr::to_str)
        .ok_or_else(|| eyre::eyre!("Path has no filename: {}", path.display()))?;
    Ok(path.with_file_name(format!("{file_name}.sfm-provenance.json")))
}

#[instrument(level = "debug", skip_all)]
fn read_artifact_provenance(path: &Path) -> eyre::Result<Option<ArtifactProvenance>> {
    let provenance_path = artifact_provenance_path(path)?;
    if !provenance_path.is_file() {
        return Ok(None);
    }
    let content = fs::read_to_string(&provenance_path)
        .wrap_err_with(|| format!("Failed to read {}", provenance_path.display()))?;
    let provenance = facet_json::from_str(&content)
        .wrap_err_with(|| format!("Failed to parse {}", provenance_path.display()))?;
    Ok(Some(provenance))
}

fn write_artifact_provenance(path: &Path, provenance: &ArtifactProvenance) -> eyre::Result<()> {
    let provenance_path = artifact_provenance_path(path)?;
    if let Some(parent) = provenance_path.parent() {
        fs::create_dir_all(parent)?;
    }
    let content = facet_json::to_string_pretty(provenance)
        .wrap_err("Failed to encode artifact provenance")?;
    fs::write(&provenance_path, content)
        .wrap_err_with(|| format!("Failed to write {}", provenance_path.display()))
}

fn read_json_file<T>(path: &Path) -> eyre::Result<T>
where
    T: Facet<'static>,
{
    let content = fs::read_to_string(path)
        .wrap_err_with(|| format!("Failed to read JSON file: {}", path.display()))?;
    facet_json::from_str(&content).wrap_err_with(|| format!("Failed to parse {}", path.display()))
}

fn read_zip_json_entry<T>(path: &Path, entry_name: &str) -> eyre::Result<T>
where
    T: Facet<'static>,
{
    let bytes = fs::read(path).wrap_err_with(|| format!("Failed to read {}", path.display()))?;
    let cursor = Cursor::new(bytes);
    let mut archive = ZipArchive::new(cursor)
        .wrap_err_with(|| format!("Failed to read zip archive {}", path.display()))?;
    let mut entry = archive
        .by_name(entry_name)
        .wrap_err_with(|| format!("Archive {} missing {entry_name}", path.display()))?;
    let mut content = String::new();
    entry
        .read_to_string(&mut content)
        .wrap_err_with(|| format!("Failed to read {entry_name} from {}", path.display()))?;
    facet_json::from_str(&content)
        .wrap_err_with(|| format!("Failed to parse {entry_name} from {}", path.display()))
}

fn download_to_path(
    cancellation_token: &CancellationToken,
    client: &Client,
    url: &str,
    path: &Path,
) -> eyre::Result<()> {
    download_to_path_overwrite(cancellation_token, client, url, path, false)
}

fn download_to_path_overwrite_with_expected_hash(
    cancellation_token: &CancellationToken,
    client: &Client,
    url: &str,
    path: &Path,
    overwrite: bool,
    expected_hash: &ContentHash,
) -> eyre::Result<()> {
    cancellation_token.bail_if_cancelled()?;
    let _lock = acquire_artifact_path_lock(path)?;
    download_to_path_overwrite_locked(
        cancellation_token,
        client,
        url,
        path,
        overwrite,
        Some(expected_hash),
    )
}

fn download_to_path_overwrite(
    cancellation_token: &CancellationToken,
    client: &Client,
    url: &str,
    path: &Path,
    overwrite: bool,
) -> eyre::Result<()> {
    cancellation_token.bail_if_cancelled()?;
    let _lock = acquire_artifact_path_lock(path)?;
    download_to_path_overwrite_locked(cancellation_token, client, url, path, overwrite, None)
}

fn download_to_path_overwrite_locked(
    cancellation_token: &CancellationToken,
    client: &Client,
    url: &str,
    path: &Path,
    overwrite: bool,
    expected_hash: Option<&ContentHash>,
) -> eyre::Result<()> {
    #[cfg(feature = "tracing_detailed")]
    let _span = tracing::debug_span!(
        "download_to_path",
        url,
        path = %path.display(),
        overwrite,
        expected_hash = expected_hash.map(ToString::to_string),
    )
    .entered();
    cancellation_token.bail_if_cancelled()?;
    prepare_existing_artifact_for_reuse(path, expected_hash)?;

    if path.is_file() && !overwrite {
        tracing::debug!(
            path = %path.display(),
            url,
            "download cache hit"
        );
        return Ok(());
    }
    if path.is_file()
        && expected_hash
            .is_some_and(|expected| existing_file_matches_hash(path, expected).unwrap_or(false))
    {
        tracing::debug!(
            path = %path.display(),
            url,
            "download cache hit after lock wait"
        );
        return Ok(());
    }

    tracing::info!(
        path = %path.display(),
        url,
        overwrite,
        "download cache miss"
    );
    let mut last_error = None;
    for attempt in 1..=DOWNLOAD_RETRY_ATTEMPTS {
        cancellation_token.bail_if_cancelled()?;
        match download_to_path_once(cancellation_token, client, url, path, expected_hash) {
            Ok(()) => {
                remove_bad_artifacts_for(path)?;
                return Ok(());
            }
            Err(error) => {
                if cancellation_token.is_cancelled() {
                    return Err(error);
                }
                tracing::warn!(
                    path = %path.display(),
                    url,
                    attempt,
                    attempts = DOWNLOAD_RETRY_ATTEMPTS,
                    error = %error,
                    "download attempt failed"
                );
                last_error = Some(error);
            }
        }
    }
    Err(last_error.unwrap_or_else(|| eyre::eyre!("Download failed for {url}")))
}

fn download_to_path_once(
    cancellation_token: &CancellationToken,
    client: &Client,
    url: &str,
    path: &Path,
    expected_hash: Option<&ContentHash>,
) -> eyre::Result<()> {
    cancellation_token.bail_if_cancelled()?;
    let parent = path
        .parent()
        .ok_or_else(|| eyre::eyre!("Path has no parent: {}", path.display()))?;
    fs::create_dir_all(parent)?;
    let response = client
        .get(url)
        .send()
        .wrap_err_with(|| format!("Failed to request {url}"))?;
    cancellation_token.bail_if_cancelled()?;
    if !response.status().is_success() {
        eyre::bail!("Failed to download {url}: HTTP {}", response.status());
    }
    let bytes = response
        .bytes()
        .wrap_err_with(|| format!("Failed to read response body for {url}"))?;
    cancellation_token.bail_if_cancelled()?;
    let temporary_path = write_unique_temp_file(path, bytes.as_ref())?;
    if let Some(expected_hash) = expected_hash {
        let actual_hash = ContentHash::from_path(&temporary_path, expected_hash.algorithm)?;
        if actual_hash != *expected_hash {
            let _ = fs::remove_file(&temporary_path);
            eyre::bail!(
                "Downloaded {} with content hash {}, expected {}",
                path.display(),
                actual_hash,
                expected_hash
            );
        }
    }
    replace_artifact_file(&temporary_path, path)
}

#[cfg(test)]
fn copy_file_to_path_checked(
    source: &Path,
    path: &Path,
    expected_hash: Option<&ContentHash>,
) -> eyre::Result<()> {
    let _lock = acquire_artifact_path_lock(path)?;
    copy_file_to_path_checked_locked(source, path, expected_hash)
}

fn copy_file_to_path_checked_locked(
    source: &Path,
    path: &Path,
    expected_hash: Option<&ContentHash>,
) -> eyre::Result<()> {
    prepare_existing_artifact_for_reuse(path, expected_hash)?;
    if path.is_file()
        && expected_hash
            .is_some_and(|expected| existing_file_matches_hash(path, expected).unwrap_or(false))
    {
        return Ok(());
    }

    let bytes =
        fs::read(source).wrap_err_with(|| format!("Failed to read {}", source.display()))?;
    let temporary_path = write_unique_temp_file(path, &bytes)?;
    if let Some(expected_hash) = expected_hash {
        let actual_hash = ContentHash::from_path(&temporary_path, expected_hash.algorithm)?;
        if actual_hash != *expected_hash {
            let _ = fs::remove_file(&temporary_path);
            eyre::bail!(
                "Copied local artifact {} with content hash {}, expected {}",
                source.display(),
                actual_hash,
                expected_hash
            );
        }
    }
    replace_artifact_file(&temporary_path, path)?;
    remove_bad_artifacts_for(path)?;
    Ok(())
}

fn acquire_artifact_path_lock(path: &Path) -> eyre::Result<ArtifactLock> {
    ArtifactLock::acquire(artifact_lock_path(path)?, path.display().to_string())
}

fn acquire_artifact_path_read_lock(path: &Path) -> eyre::Result<ArtifactReadLock> {
    ArtifactReadLock::acquire(artifact_lock_path(path)?, path.display().to_string())
}

fn artifact_lock_path(path: &Path) -> eyre::Result<PathBuf> {
    let file_name = artifact_file_name(path)?;
    Ok(path.with_file_name(format!("{file_name}.lock")))
}

fn prepare_existing_artifact_for_reuse(
    path: &Path,
    expected_hash: Option<&ContentHash>,
) -> eyre::Result<()> {
    let Some(expected_hash) = expected_hash else {
        return Ok(());
    };
    if !path.is_file() {
        return Ok(());
    }
    let actual_hash = ContentHash::from_path(path, expected_hash.algorithm)?;
    if actual_hash == *expected_hash {
        return Ok(());
    }
    quarantine_bad_artifact(path, &actual_hash, expected_hash)
}

#[instrument(level = "debug", skip_all)]
fn existing_file_matches_hash(path: &Path, expected_hash: &ContentHash) -> eyre::Result<bool> {
    Ok(path.is_file() && ContentHash::from_path(path, expected_hash.algorithm)? == *expected_hash)
}

fn quarantine_bad_artifact(
    path: &Path,
    actual_hash: &ContentHash,
    expected_hash: &ContentHash,
) -> eyre::Result<()> {
    let bad_path = unique_sibling_path(path, &format!("bad.{}", actual_hash.hex()))?;
    tracing::warn!(
        path = %path.display(),
        bad_path = %bad_path.display(),
        actual_hash = %actual_hash,
        expected_hash = %expected_hash,
        "quarantining corrupt artifact"
    );
    fs::rename(path, &bad_path).wrap_err_with(|| {
        format!(
            "Failed to quarantine corrupt artifact {} as {}",
            path.display(),
            bad_path.display()
        )
    })
}

fn remove_bad_artifacts_for(path: &Path) -> eyre::Result<()> {
    let Some(parent) = path.parent() else {
        return Ok(());
    };
    if !parent.is_dir() {
        return Ok(());
    }
    let file_name = artifact_file_name(path)?;
    let bad_prefix = format!("{file_name}.bad.");
    for entry in
        fs::read_dir(parent).wrap_err_with(|| format!("Failed to read {}", parent.display()))?
    {
        let entry = entry?;
        let entry_path = entry.path();
        let is_bad_artifact = entry_path
            .file_name()
            .and_then(std::ffi::OsStr::to_str)
            .is_some_and(|name| name.starts_with(&bad_prefix));
        if is_bad_artifact {
            fs::remove_file(&entry_path)
                .wrap_err_with(|| format!("Failed to remove {}", entry_path.display()))?;
        }
    }
    Ok(())
}

fn write_unique_temp_file(path: &Path, bytes: &[u8]) -> eyre::Result<PathBuf> {
    let parent = path
        .parent()
        .ok_or_else(|| eyre::eyre!("Path has no parent: {}", path.display()))?;
    fs::create_dir_all(parent)?;
    for _ in 0..100 {
        let temporary_path = unique_sibling_path(path, "tmp")?;
        match OpenOptions::new()
            .write(true)
            .create_new(true)
            .open(&temporary_path)
        {
            Ok(mut file) => {
                file.write_all(bytes)
                    .wrap_err_with(|| format!("Failed to write {}", temporary_path.display()))?;
                file.sync_all()
                    .wrap_err_with(|| format!("Failed to sync {}", temporary_path.display()))?;
                return Ok(temporary_path);
            }
            Err(error) if error.kind() == std::io::ErrorKind::AlreadyExists => {}
            Err(error) => {
                return Err(error)
                    .wrap_err_with(|| format!("Failed to create {}", temporary_path.display()));
            }
        }
    }
    eyre::bail!(
        "Could not allocate a unique temporary file for {}",
        path.display()
    )
}

fn replace_artifact_file(temporary_path: &Path, path: &Path) -> eyre::Result<()> {
    if path.exists() {
        fs::remove_file(path).wrap_err_with(|| format!("Failed to replace {}", path.display()))?;
    }
    fs::rename(temporary_path, path).wrap_err_with(|| {
        format!(
            "Failed to move downloaded file {} to {}",
            temporary_path.display(),
            path.display()
        )
    })?;
    Ok(())
}

fn unique_sibling_path(path: &Path, kind: &str) -> eyre::Result<PathBuf> {
    let file_name = artifact_file_name(path)?;
    Ok(path.with_file_name(format!(
        "{file_name}.{kind}.{}.{}",
        std::process::id(),
        unique_file_nonce()
    )))
}

fn unique_file_nonce() -> u128 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_default()
        .as_nanos()
}

fn artifact_file_name(path: &Path) -> eyre::Result<&str> {
    path.file_name()
        .and_then(std::ffi::OsStr::to_str)
        .ok_or_else(|| eyre::eyre!("Path has no filename: {}", path.display()))
}

fn download_text_optional(
    cancellation_token: &CancellationToken,
    client: &Client,
    url: &str,
) -> eyre::Result<String> {
    cancellation_token.bail_if_cancelled()?;
    let response = client
        .get(url)
        .send()
        .wrap_err_with(|| format!("Failed to request {url}"))?;
    cancellation_token.bail_if_cancelled()?;
    if response.status() == StatusCode::NOT_FOUND {
        eyre::bail!("not found");
    }
    if !response.status().is_success() {
        eyre::bail!("HTTP {}", response.status());
    }
    let text = response
        .text()
        .wrap_err_with(|| format!("Failed to read response body for {url}"))?;
    cancellation_token.bail_if_cancelled()?;
    Ok(text)
}

fn remote_exists(
    cancellation_token: &CancellationToken,
    client: &Client,
    url: &str,
) -> eyre::Result<bool> {
    #[cfg(feature = "tracing_detailed")]
    let _span = tracing::debug_span!("remote_exists", url).entered();
    cancellation_token.bail_if_cancelled()?;
    let response = client
        .head(url)
        .send()
        .wrap_err_with(|| format!("Failed to request {url}"))?;
    cancellation_token.bail_if_cancelled()?;
    if response.status() == StatusCode::METHOD_NOT_ALLOWED {
        return match download_text_optional(cancellation_token, client, url) {
            Ok(_) => Ok(true),
            Err(error) if cancellation_token.is_cancelled() => Err(error),
            Err(_) => Ok(false),
        };
    }
    Ok(response.status().is_success())
}

fn parse_maven_versions(metadata: &str) -> Vec<String> {
    let mut versions = Vec::new();
    let mut remaining = metadata;

    while let Some(start) = remaining.find("<version>") {
        let after_start = &remaining[start + "<version>".len()..];
        let Some(end) = after_start.find("</version>") else {
            break;
        };
        versions.push(after_start[..end].trim().to_string());
        remaining = &after_start[end + "</version>".len()..];
    }

    versions
}

fn compare_version_text(left: &str, right: &str) -> Ordering {
    let left_parts = split_version_parts(left);
    let right_parts = split_version_parts(right);
    left_parts.cmp(&right_parts)
}

fn split_version_parts(version: &str) -> Vec<VersionPart> {
    let mut parts = Vec::new();
    let mut current = String::new();
    let mut digit_mode = None;

    for character in version.chars() {
        let is_digit = character.is_ascii_digit();
        if digit_mode.is_some_and(|mode| mode != is_digit) && !current.is_empty() {
            parts.push(VersionPart::from_text(&current));
            current.clear();
        }
        digit_mode = Some(is_digit);
        if character == '.' || character == '-' || character == '_' || character == '+' {
            if !current.is_empty() {
                parts.push(VersionPart::from_text(&current));
                current.clear();
            }
            digit_mode = None;
        } else {
            current.push(character);
        }
    }

    if !current.is_empty() {
        parts.push(VersionPart::from_text(&current));
    }

    parts
}

#[derive(Clone, Debug, Eq, Ord, PartialEq, PartialOrd)]
enum VersionPart {
    Number(u64),
    Text(String),
}

impl VersionPart {
    fn from_text(text: &str) -> Self {
        text.parse::<u64>()
            .map_or_else(|_| Self::Text(text.to_string()), Self::Number)
    }
}

#[cfg(test)]
#[path = "engine_tests.rs"]
mod tests;
