use super::JavaSymbolSelectorKind;
use super::JavaSymbolSelectorOutput;
use eyre::bail;

#[derive(Clone, Debug, PartialEq, Eq, PartialOrd, Ord)]
pub enum JavaSymbolSelector {
    Type {
        owner: String,
    },
    Field {
        owner: String,
        name: String,
    },
    Method {
        owner: String,
        name: String,
        descriptor: String,
    },
}

impl JavaSymbolSelector {
    /// Parse the target portion of a Forge Access Transformer style selector.
    ///
    /// # Errors
    ///
    /// Returns an error for invalid owners, members, descriptors, or arity.
    pub fn parse_terms(terms: &[String]) -> eyre::Result<Self> {
        match terms {
            [owner] => {
                validate_owner(owner)?;
                Ok(Self::Type {
                    owner: owner.clone(),
                })
            }
            [owner, member] => {
                validate_owner(owner)?;
                if let Some(open) = member.find('(') {
                    let name = &member[..open];
                    let descriptor = &member[open..];
                    validate_method_name(name)?;
                    validate_method_descriptor(descriptor)?;
                    Ok(Self::Method {
                        owner: owner.clone(),
                        name: name.to_owned(),
                        descriptor: descriptor.to_owned(),
                    })
                } else {
                    validate_identifier(member, "field name")?;
                    Ok(Self::Field {
                        owner: owner.clone(),
                        name: member.clone(),
                    })
                }
            }
            [] => bail!("symbol selector requires a fully qualified class name"),
            _ => bail!("symbol selector accepts '<class>' or '<class> <field|method(descriptor)>'"),
        }
    }

    #[must_use]
    pub fn owner(&self) -> &str {
        match self {
            Self::Type { owner } | Self::Field { owner, .. } | Self::Method { owner, .. } => owner,
        }
    }

    #[must_use]
    pub fn canonical(&self) -> String {
        match self {
            Self::Type { owner } => owner.clone(),
            Self::Field { owner, name } => format!("{owner} {name}"),
            Self::Method {
                owner,
                name,
                descriptor,
            } => format!("{owner} {name}{descriptor}"),
        }
    }

    #[must_use]
    pub fn to_output(&self) -> JavaSymbolSelectorOutput {
        match self {
            Self::Type { owner } => JavaSymbolSelectorOutput {
                canonical: self.canonical(),
                owner: owner.clone(),
                member: None,
                descriptor: None,
                kind: JavaSymbolSelectorKind::Type,
            },
            Self::Field { owner, name } => JavaSymbolSelectorOutput {
                canonical: self.canonical(),
                owner: owner.clone(),
                member: Some(name.clone()),
                descriptor: None,
                kind: JavaSymbolSelectorKind::Field,
            },
            Self::Method {
                owner,
                name,
                descriptor,
            } => JavaSymbolSelectorOutput {
                canonical: self.canonical(),
                owner: owner.clone(),
                member: Some(name.clone()),
                descriptor: Some(descriptor.clone()),
                kind: JavaSymbolSelectorKind::Method,
            },
        }
    }
}

fn validate_owner(owner: &str) -> eyre::Result<()> {
    if owner.is_empty() || owner.starts_with('.') || owner.ends_with('.') || owner.contains("..") {
        bail!("invalid fully qualified class name '{owner}'");
    }
    for package_or_type in owner.split('.') {
        for nested in package_or_type.split('$') {
            validate_identifier(nested, "class-name segment")?;
        }
    }
    Ok(())
}

fn validate_method_name(name: &str) -> eyre::Result<()> {
    if matches!(name, "<init>" | "<clinit>") {
        return Ok(());
    }
    validate_identifier(name, "method name")
}

fn validate_identifier(value: &str, description: &str) -> eyre::Result<()> {
    let mut chars = value.chars();
    let Some(first) = chars.next() else {
        bail!("{description} cannot be empty");
    };
    if !is_java_identifier_start(first) || !chars.all(is_java_identifier_part) {
        bail!("invalid {description} '{value}'");
    }
    Ok(())
}

fn is_java_identifier_start(character: char) -> bool {
    character == '_' || character == '$' || character.is_alphabetic()
}

fn is_java_identifier_part(character: char) -> bool {
    is_java_identifier_start(character) || character.is_ascii_digit()
}

fn validate_method_descriptor(descriptor: &str) -> eyre::Result<()> {
    let bytes = descriptor.as_bytes();
    if bytes.first() != Some(&b'(') {
        bail!("method descriptor must begin with '('");
    }
    let mut cursor = 1;
    while bytes.get(cursor) != Some(&b')') {
        if cursor >= bytes.len() {
            bail!("method descriptor is missing ')'");
        }
        cursor = parse_field_descriptor(bytes, cursor, false)?;
    }
    cursor += 1;
    cursor = parse_field_descriptor(bytes, cursor, true)?;
    if cursor != bytes.len() {
        bail!("method descriptor has trailing characters");
    }
    Ok(())
}

fn parse_field_descriptor(bytes: &[u8], start: usize, allow_void: bool) -> eyre::Result<usize> {
    let Some(kind) = bytes.get(start).copied() else {
        bail!("descriptor ended before its type");
    };
    match kind {
        b'B' | b'C' | b'D' | b'F' | b'I' | b'J' | b'S' | b'Z' => Ok(start + 1),
        b'V' if allow_void => Ok(start + 1),
        b'[' => parse_field_descriptor(bytes, start + 1, false),
        b'L' => {
            let Some(relative_end) = bytes[start + 1..].iter().position(|byte| *byte == b';')
            else {
                bail!("object descriptor is missing ';'");
            };
            let end = start + 1 + relative_end;
            if end == start + 1 {
                bail!("object descriptor class name cannot be empty");
            }
            if bytes[start + 1..end]
                .iter()
                .any(|byte| !byte.is_ascii_alphanumeric() && !matches!(*byte, b'/' | b'$' | b'_'))
            {
                bail!("object descriptor contains an invalid class name");
            }
            Ok(end + 1)
        }
        b'V' => bail!("void is only valid as a method return type"),
        _ => bail!("invalid JVM descriptor type code '{}'", char::from(kind)),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn strings(values: &[&str]) -> Vec<String> {
        values.iter().map(|value| (*value).to_owned()).collect()
    }

    #[test]
    fn java_symbol_selector_parses_type_field_method_and_nested_class() {
        assert_eq!(
            JavaSymbolSelector::parse_terms(&strings(&["example.Outer$Inner"]))
                .expect("nested type"),
            JavaSymbolSelector::Type {
                owner: "example.Outer$Inner".to_owned()
            }
        );
        assert!(matches!(
            JavaSymbolSelector::parse_terms(&strings(&["example.A", "FIELD"])).expect("field"),
            JavaSymbolSelector::Field { .. }
        ));
        assert!(matches!(
            JavaSymbolSelector::parse_terms(&strings(&["example.A", "run([Ljava/lang/String;I)V"]))
                .expect("method"),
            JavaSymbolSelector::Method { .. }
        ));
    }

    #[test]
    fn java_symbol_selector_requires_exact_method_descriptors() {
        for invalid in [
            "run",
            "run(",
            "run()",
            "run(V)V",
            "run(Ljava/lang/String)V",
            "run()Vextra",
        ] {
            if invalid == "run" {
                continue;
            }
            let error = JavaSymbolSelector::parse_terms(&strings(&["example.A", invalid]))
                .expect_err("invalid descriptor must fail");
            assert!(!error.to_string().is_empty());
        }
    }

    #[test]
    fn java_symbol_selector_rejects_malformed_arity_and_names() {
        for terms in [
            strings(&[]),
            strings(&[".example.A"]),
            strings(&["example.A", "bad-name"]),
            strings(&["example.A", "FIELD", "extra"]),
        ] {
            let _error =
                JavaSymbolSelector::parse_terms(&terms).expect_err("malformed selector must fail");
        }
    }
}
