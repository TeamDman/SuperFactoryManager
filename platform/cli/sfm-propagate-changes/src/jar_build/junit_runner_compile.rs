//! The built-in `JUnit` helper compiles against one selected console JAR only.
//! This held contract is separate from the product's `NeoForm` classpath role.
use eyre::{Result, ensure};
use std::fs::{self, File};
use std::io::Read;
use std::path::{Path, PathBuf};

const SOURCE: &[u8] = include_bytes!("junit_event_runner.java");
const MAX_INPUT_BYTES: u64 = 64 * 1024 * 1024;

struct HeldInput {
    path: PathBuf,
    file: File,
    identity: crate::file_identity::FileIdentity,
    sha256: String,
}

impl HeldInput {
    fn capture(path: &Path) -> Result<Self> {
        let file = File::open(path)?;
        let identity = crate::file_identity::file_identity(&file)?;
        let sha256 = crate::source_projection::provenance::sha256(&Self::read(path)?);
        let input = Self {
            path: path.to_owned(),
            file,
            identity,
            sha256,
        };
        input.recheck()?;
        Ok(input)
    }

    fn read(path: &Path) -> Result<Vec<u8>> {
        let metadata = fs::symlink_metadata(path)?;
        ensure!(
            metadata.is_file()
                && !metadata.file_type().is_symlink()
                && metadata.len() > 0
                && metadata.len() <= MAX_INPUT_BYTES,
            "JUnit runner compiler input must be a bounded regular file"
        );
        let mut bytes = Vec::new();
        File::open(path)?
            .take(MAX_INPUT_BYTES + 1)
            .read_to_end(&mut bytes)?;
        ensure!(
            bytes.len() as u64 <= MAX_INPUT_BYTES,
            "JUnit runner compiler input grew beyond its bound"
        );
        Ok(bytes)
    }

    fn recheck(&self) -> Result<()> {
        let opened = File::open(&self.path)?;
        ensure!(
            crate::file_identity::file_identity(&self.file)? == self.identity
                && crate::file_identity::file_identity(&opened)? == self.identity,
            "JUnit runner compiler input was replaced"
        );
        ensure!(
            crate::source_projection::provenance::sha256(&Self::read(&self.path)?) == self.sha256,
            "JUnit runner compiler input bytes changed"
        );
        ensure!(
            crate::file_identity::file_identity(&File::open(&self.path)?)? == self.identity,
            "JUnit runner compiler input was replaced during recheck"
        );
        Ok(())
    }
}

pub(super) struct JunitRunnerCompile {
    console: HeldInput,
    source: HeldInput,
    arguments: Vec<String>,
}

impl JunitRunnerCompile {
    /// The console must already be selected by the checked `JUnit` resolver.
    /// Capture its bytes for this invocation; this is not artifact admission.
    pub(super) fn prepare(
        console: &Path,
        source: &Path,
        classes: &Path,
        release: u32,
        major: u32,
    ) -> Result<Self> {
        let console = HeldInput::capture(console)?;
        let source = HeldInput::capture(source)?;
        ensure!(
            source.sha256 == crate::source_projection::provenance::sha256(SOURCE),
            "JUnit runner compiler accepts only its built-in source"
        );
        let mut arguments = super::no_ap_javac_arguments(
            std::slice::from_ref(&console.path),
            classes,
            release,
            major,
        );
        // The only source is appended after all fixed compiler options.
        arguments.push(source.path.display().to_string());
        Ok(Self {
            console,
            source,
            arguments,
        })
    }

    pub(super) fn arguments(&self) -> &[String] {
        &self.arguments
    }

    pub(super) fn write_argfile(
        &self,
        context: &super::ExecutionContext<'_>,
        path: &Path,
    ) -> Result<()> {
        self.recheck()?;
        super::write_compilation_arguments(context, path, self.arguments())
    }

    pub(super) fn recheck(&self) -> Result<()> {
        self.console.recheck()?;
        self.source.recheck()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn junit_runner_compiles_without_product_classpath_and_keeps_product_guards() -> Result<()> {
        let root = tempfile::tempdir()?;
        let mut plan = super::super::tests::minimal_plan_for_paths();
        plan.minecraft_dir = root.path().join("minecraft");
        plan.cache_dir = plan.minecraft_dir.join("build/sfm-toolchain");
        let runner = plan.cache_dir.join("junit-runner");
        fs::create_dir_all(&runner)?;
        let console = runner.join("console.jar");
        let source = runner.join("SfmJUnitRunner.java");
        let held = runner.join("held-neoform.jar");
        fs::write(&console, b"selected console fixture; no JVM is authorized")?;
        fs::write(&source, SOURCE)?;
        fs::write(&held, b"held product fixture")?;
        let mut context = super::super::ExecutionContext::new(
            &plan,
            crate::cancellation::CancellationToken::new(),
        )?;
        context.verify_compilation_outputs = true;
        context.held_neoform_compile_jar = Some(&held);
        let classes = runner.join("classes");
        let compile = JunitRunnerCompile::prepare(&console, &source, &classes, 17, 21)?;
        let args = compile.arguments();
        let classpath = args.iter().position(|value| value == "-classpath").unwrap() + 1;
        assert_eq!(args[classpath], console.display().to_string());
        assert!(args.iter().any(|value| value == "-proc:none"));
        assert_eq!(args.last(), Some(&source.display().to_string()));
        let argfile = runner.join("javac-runner.args");
        compile.write_argfile(&context, &argfile)?;
        assert!(argfile.is_file());
        assert!(!argfile.with_extension("fingerprint.args").exists());
        for (name, annotation_processing) in [("product.args", true), ("test.args", false)] {
            let output = runner.join(name);
            let result = if annotation_processing {
                super::super::write_javac_argfile(
                    &context,
                    &output,
                    std::slice::from_ref(&console),
                    std::slice::from_ref(&source),
                    &classes,
                )
            } else {
                super::super::write_javac_no_ap_argfile(
                    &context,
                    &output,
                    std::slice::from_ref(&console),
                    std::slice::from_ref(&source),
                    &classes,
                )
            };
            assert!(
                result
                    .unwrap_err()
                    .to_string()
                    .contains("Held NeoForm classpath is absent")
            );
            assert!(
                !output.exists(),
                "Invalid product argv must not be published"
            );
        }
        let product = runner.join("valid-product.args");
        super::super::write_javac_no_ap_argfile(
            &context,
            &product,
            &[held.clone(), console],
            &[source],
            &classes,
        )?;
        assert!(
            fs::read_to_string(product.with_extension("fingerprint.args"))?
                .contains("__sfm_bound_neoform_classes_v1__")
        );
        Ok(())
    }

    #[test]
    fn junit_runner_rejects_foreign_source_and_changed_console_or_source() -> Result<()> {
        let root = tempfile::tempdir()?;
        let source = root.path().join("SfmJUnitRunner.java");
        let console = root.path().join("console.jar");
        let classes = root.path().join("classes");
        fs::write(&console, b"selected console")?;
        fs::write(&source, b"class Foreign {}")?;
        assert!(JunitRunnerCompile::prepare(&console, &source, &classes, 17, 17).is_err());
        fs::write(&source, SOURCE)?;
        let compile = JunitRunnerCompile::prepare(&console, &source, &classes, 17, 17)?;
        fs::write(&console, b"modified console")?;
        assert!(compile.recheck().is_err());
        drop(compile);
        let compile = JunitRunnerCompile::prepare(&console, &source, &classes, 17, 17)?;
        fs::write(&source, b"modified source")?;
        assert!(compile.recheck().is_err());
        Ok(())
    }
}
