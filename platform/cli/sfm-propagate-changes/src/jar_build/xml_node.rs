use eyre::ensure;
#[derive(Clone, Debug)]
pub(super) struct XmlNode {
    pub name: String,
    pub text: String,
    pub children: Vec<Self>,
}
impl XmlNode {
    pub fn child(&self, name: &str) -> eyre::Result<Option<&Self>> {
        let mut found = self.children.iter().filter(|n| n.name == name);
        let first = found.next();
        ensure!(found.next().is_none(), "duplicate POM element: {name}");
        Ok(first)
    }
    pub fn value(&self, name: &str) -> eyre::Result<Option<String>> {
        self.child(name)?
            .map(|n| {
                ensure!(n.children.is_empty(), "non-scalar POM element: {name}");
                Ok(n.text.trim().to_owned())
            })
            .transpose()
    }
    pub fn parse(input: &str) -> eyre::Result<Self> {
        ensure!(
            input.len() <= 2 * 1024 * 1024,
            "POM metadata byte budget exceeded"
        );
        let mut stack: Vec<Self> = Vec::new();
        let mut roots = Vec::new();
        let mut rest = input;
        let mut count = 0;
        while !rest.is_empty() {
            let offset = rest.find('<').unwrap_or(rest.len());
            let text = entities(&rest[..offset])?;
            if let Some(node) = stack.last_mut() {
                node.text.push_str(&text);
            } else {
                ensure!(text.trim().is_empty(), "XML text outside POM root");
            }
            rest = &rest[offset..];
            if rest.is_empty() {
                break;
            }
            if rest.starts_with("<!--") {
                let end = rest
                    .find("-->")
                    .ok_or_else(|| eyre::eyre!("unclosed XML comment"))?;
                rest = &rest[end + 3..];
                continue;
            }
            if rest.starts_with("<![CDATA[") {
                let end = rest
                    .find("]]>")
                    .ok_or_else(|| eyre::eyre!("unclosed XML CDATA"))?;
                stack
                    .last_mut()
                    .ok_or_else(|| eyre::eyre!("CDATA outside root"))?
                    .text
                    .push_str(&rest[9..end]);
                rest = &rest[end + 3..];
                continue;
            }
            if rest.starts_with("<?") {
                let end = rest
                    .find("?>")
                    .ok_or_else(|| eyre::eyre!("unclosed XML processing instruction"))?;
                rest = &rest[end + 2..];
                continue;
            }
            ensure!(
                !rest.starts_with("<!"),
                "DTD/entity declarations are forbidden in POM metadata"
            );
            let end = tag_end(rest)?;
            let tag = rest[1..end].trim();
            rest = &rest[end + 1..];
            consume_tag(tag, &mut stack, &mut roots, &mut count)?;
        }
        ensure!(
            stack.is_empty() && roots.len() == 1,
            "POM XML must have one complete root"
        );
        Ok(roots.remove(0))
    }
}
fn local_name(name: &str) -> &str {
    name.rsplit(':').next().unwrap_or(name)
}
fn entities(input: &str) -> eyre::Result<String> {
    let mut result = String::new();
    let mut rest = input;
    while let Some(start) = rest.find('&') {
        result.push_str(&rest[..start]);
        let end = rest[start..]
            .find(';')
            .ok_or_else(|| eyre::eyre!("unclosed XML entity"))?
            + start;
        let name = &rest[start + 1..end];
        let value = match name {
            "amp" => '&',
            "lt" => '<',
            "gt" => '>',
            "quot" => '"',
            "apos" => '\'',
            _ => {
                let code = if let Some(hex) = name.strip_prefix("#x") {
                    u32::from_str_radix(hex, 16)?
                } else if let Some(decimal) = name.strip_prefix('#') {
                    decimal.parse()?
                } else {
                    eyre::bail!("unsupported XML entity: {name}");
                };
                char::from_u32(code).ok_or_else(|| eyre::eyre!("invalid XML character"))?
            }
        };
        ensure!(
            value == '\t' || value == '\n' || value == '\r' || value >= ' ',
            "invalid XML control character"
        );
        result.push(value);
        rest = &rest[end + 1..];
    }
    result.push_str(rest);
    Ok(result)
}

fn tag_end(rest: &str) -> eyre::Result<usize> {
    let mut quote = None;
    let end = rest
        .char_indices()
        .skip(1)
        .find_map(|(i, c)| {
            if quote == Some(c) {
                quote = None;
            } else if quote.is_none() && (c == '\'' || c == '"') {
                quote = Some(c);
            } else if c == '>' && quote.is_none() {
                return Some(i);
            }
            None
        })
        .ok_or_else(|| eyre::eyre!("unclosed XML tag"))?;
    Ok(end)
}

fn consume_tag(
    tag: &str,
    stack: &mut Vec<XmlNode>,
    roots: &mut Vec<XmlNode>,
    count: &mut usize,
) -> eyre::Result<()> {
    if let Some(close) = tag.strip_prefix('/') {
        let node = stack
            .pop()
            .ok_or_else(|| eyre::eyre!("unexpected XML closing tag"))?;
        ensure!(
            node.name == local_name(close.trim()),
            "mismatched XML closing tag"
        );
        if let Some(parent) = stack.last_mut() {
            parent.children.push(node);
        } else {
            roots.push(node);
        }
    } else {
        let empty = tag.ends_with('/');
        let name = tag
            .trim_end_matches('/')
            .split_whitespace()
            .next()
            .ok_or_else(|| eyre::eyre!("empty XML tag"))?;
        ensure!(
            name.bytes()
                .all(|c| c.is_ascii_alphanumeric() || b"_.:-".contains(&c)),
            "unsupported XML name"
        );
        *count += 1;
        ensure!(
            *count <= 20000 && stack.len() < 64,
            "XML node/depth budget exceeded"
        );
        let node = XmlNode {
            name: local_name(name).to_owned(),
            text: String::new(),
            children: Vec::new(),
        };
        if empty {
            if let Some(parent) = stack.last_mut() {
                parent.children.push(node);
            } else {
                roots.push(node);
            }
        } else {
            stack.push(node);
        }
    }
    Ok(())
}
