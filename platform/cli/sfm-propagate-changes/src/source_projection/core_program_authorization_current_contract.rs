//! Test-only current-to-historical proof, not a production source provider.
use super::provenance::sha256;
use eyre::Result;
use eyre::ensure;

const CURRENT_SHA: &str = "sha256:8822cb07e826275c64511f261025f4db03ca986bd24bc62a2dfd039f80cbcb4d";
const PREVIOUS_SHA: &str =
    "sha256:c1b41edbd8696d3cbe7fff2aa55b368be711c4c435c3cdec9979b764b07ac509";

// Select only the new program-owner wrappers. Preserve the three older frame
// directives verbatim, including their alternate hooks. Exact pins constrain
// both ends, so unrelated edits or unknown directives are never accepted.
pub(super) fn reviewed_pre_human_authorization(current: &str) -> Result<String> {
    ensure!(
        current.len() == 18967 && sha256(current.as_bytes()) == CURRENT_SHA,
        "current authorization template changed outside reviewed owner boundaries"
    );
    enum Frame {
        Program(bool),
        Original,
    }
    let mut stack = Vec::new();
    let mut emit = true;
    let mut original = String::new();
    for line in current.split_inclusive('\n') {
        match line.trim() {
            "{% if features.client_program_actions %}" => {
                stack.push(Frame::Program(emit));
            }
            "{% if features.client_frame_language %}" => {
                stack.push(Frame::Original);
                if emit {
                    original.push_str(line);
                }
            }
            "{% else %}" => match stack.last() {
                Some(Frame::Program(_)) => emit = false,
                Some(Frame::Original) => {
                    if emit {
                        original.push_str(line);
                    }
                }
                None => eyre::bail!("authorization alternate without owner boundary"),
            },
            "{% endif %}" => match stack.pop() {
                Some(Frame::Program(parent)) => emit = parent,
                Some(Frame::Original) => {
                    if emit {
                        original.push_str(line);
                    }
                }
                None => eyre::bail!("authorization close without owner boundary"),
            },
            directive if directive.starts_with("{%") => {
                eyre::bail!("unreviewed authorization directive: {directive}");
            }
            _ => {
                if emit {
                    original.push_str(line);
                }
            }
        }
    }
    ensure!(stack.is_empty(), "unclosed authorization owner boundary");
    ensure!(
        original.len() == 16892 && sha256(original.as_bytes()) == PREVIOUS_SHA,
        "authorization owner inverse did not recover the unchanged original pin"
    );
    Ok(original)
}
