// Prepared actual-core/API tests only. No case launches a process or opens a network connection.
#[cfg(test)]
mod named_forge_jar_tests {
    use super::*;
    use crate::source_projection::catalog_owned_project::tests::Fixture;

    #[test]
    fn named_jar_four_raw_recipes_eight_owned_contexts() -> eyre::Result<()> {
        for (target, expected_lock) in NAMED_JAR_RECIPE_LOCKS {
            let (fixture, slot, recipe, raw) = named_forge_compile_cohort_tests::fixture(target)?;
            for environment in ["release", "dev"] {
                let project = named_forge_compile_cohort_tests::prepared(
                    &fixture,
                    slot,
                    environment,
                    &recipe,
                )?;
                validate_named_jar_project(&project)?;
                assert_eq!(project.dependencies().raw_source_lock_bytes(), raw);
                assert_eq!(
                    project.dependencies().receipt().source_lock_sha256,
                    expected_lock
                );
                assert_eq!(
                    project.receipt().ownership.projection_key,
                    Fixture::key(slot, environment)
                );
                let tool = project
                    .dependencies()
                    .coordinate(NAMED_JAR_TOOL_COORDINATE, false)?;
                assert_eq!(tool.original_content_hash(), NAMED_JAR_TOOL_HASH);
                assert_eq!(
                    (
                        project.receipt().compiler_release,
                        project.receipt().tool_jvm_minimum
                    ),
                    (17, 17)
                );
                project.recheck(false)?;
                assert!(project.recheck(true).is_err());
            }
        }
        Ok(())
    }

    #[test]
    fn named_jar_exact_identity_mismatches_refuse_without_preparation() -> eyre::Result<()> {
        for (target, lock) in NAMED_JAR_RECIPE_LOCKS {
            let recipe = format!("sfm:released-native-inputs/4.34.0/{target}@1");
            require_named_jar_recipe_identity(target, target, &recipe, lock, (17, 17))?;
            for other in ["1.19.2", "1.19.4", "1.20", "1.20.1"]
                .into_iter()
                .filter(|other| *other != target)
            {
                assert!(
                    require_named_jar_recipe_identity(target, other, &recipe, lock, (17, 17))
                        .is_err()
                );
            }
            for bad in [
                "unknown",
                "sfm:released-native-inputs/4.34.1/1.19.2@1",
                "sfm:released-native-inputs/4.34.0/1.19.2@2",
            ] {
                assert!(
                    require_named_jar_recipe_identity(target, target, bad, lock, (17, 17)).is_err()
                );
            }
            assert!(
                require_named_jar_recipe_identity(
                    target,
                    target,
                    &recipe,
                    "sha256:wrong",
                    (17, 17)
                )
                .is_err()
            );
            for boundary in [(8, 17), (17, 8), (21, 17), (17, 21)] {
                assert!(
                    require_named_jar_recipe_identity(target, target, &recipe, lock, boundary)
                        .is_err()
                );
            }
        }
        for unsupported in [
            "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1", "26.1.2", "1.21", "unknown",
        ] {
            let recipe = format!("sfm:released-native-inputs/4.34.0/{unsupported}@1");
            assert!(
                require_named_jar_recipe_identity(
                    unsupported,
                    unsupported,
                    &recipe,
                    NAMED_JAR_RECIPE_LOCKS[0].1,
                    (17, 17)
                )
                .is_err()
            );
        }
        Ok(())
    }

    #[test]
    fn named_jar_two_only_targets_and_no_existence_only_package_cache() -> eyre::Result<()> {
        for target in [
            BuildTarget::Compile,
            BuildTarget::Jar,
            BuildTarget::Run,
            BuildTarget::SourceOutputs,
        ] {
            require_named_native_target(false, target)?;
            assert_eq!(
                require_named_native_target(true, target).is_ok(),
                matches!(target, BuildTarget::Compile | BuildTarget::Jar)
            );
        }
        assert!(named_package_cache_reuse_allowed(false));
        assert!(!named_package_cache_reuse_allowed(true));
        assert!(named_package_previous_output_removal_allowed(false));
        assert!(!named_package_previous_output_removal_allowed(true));
        refuse_specialsource_jvm_overrides(|_| None)?;
        for name in ["JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS"] {
            assert!(
                refuse_specialsource_jvm_overrides(
                    |key| (key == name).then(|| std::ffi::OsString::from("-Dunreviewed=true"))
                )
                .is_err()
            );
        }
        let run_source = include_str!("engine_run.rs");
        assert!(run_source.contains("validate_named_native_build_target(plan, target)?;"));
        let package_source = include_str!("engine_execute.rs");
        let package_tokens: String = package_source
            .chars()
            .filter(|character| !character.is_whitespace())
            .collect();
        assert!(package_tokens.contains("named_package_cache_reuse_allowed(context.plan.named_project().is_some())&&cache_state_matches_outputs("));
        assert!(
            package_source
                .contains("let named_admission = context.prepare_named_package_admission()?;")
        );
        assert!(
            package_source
                .contains("context.recheck_named_package_admission(named_admission.as_ref())?;")
        );
        assert!(package_source.contains(
            "named_package_previous_output_removal_allowed(context.plan.named_project().is_some())"
        ));
        assert!(package_source.contains("context.run_project_package_specialsource("));
        Ok(())
    }

    #[test]
    fn named_jar_exact_two_mapping_live_arguments_and_path_refusals() -> eyre::Result<()> {
        let temp = tempfile::tempdir()?;
        let input = temp.path().join("dev.jar");
        let output = temp.path().join("sfm-MC1.19.4-4.34.0-rust.jar");
        let official = temp.path().join("official_to_srg.tsrg");
        let mixin = temp.path().join("compileJava-mappings.tsrg");
        let expected = vec![
            "--in-jar".to_owned(),
            require_named_jar_path(&input)?.to_owned(),
            "--out-jar".to_owned(),
            require_named_jar_path(&output)?.to_owned(),
            "--srg-in".to_owned(),
            require_named_jar_path(&official)?.to_owned(),
            "--srg-in".to_owned(),
            require_named_jar_path(&mixin)?.to_owned(),
            "--live".to_owned(),
        ];
        assert_eq!(
            named_jar_arguments(&input, &output, &official, &mixin)?,
            expected
        );
        require_named_jar_invocation(&expected, &input, &output, &official, &mixin)?;
        for index in 0..expected.len() {
            let mut changed = expected.clone();
            changed[index].push_str("-changed");
            assert!(
                require_named_jar_invocation(&changed, &input, &output, &official, &mixin).is_err()
            );
        }
        let mut extra = expected.clone();
        extra.push("--unreviewed".to_owned());
        assert!(require_named_jar_invocation(&extra, &input, &output, &official, &mixin).is_err());
        let mut swapped = expected.clone();
        swapped.swap(5, 7);
        assert!(
            require_named_jar_invocation(&swapped, &input, &output, &official, &mixin).is_err()
        );
        for bad in [
            PathBuf::from("relative.jar"),
            temp.path().join("../escape.jar"),
            temp.path().join(if cfg!(windows) {
                "split;classpath.jar"
            } else {
                "split:classpath.jar"
            }),
        ] {
            assert!(require_named_jar_path(&bad).is_err());
        }
        require_named_jar_output_path(temp.path(), &output)?;
        require_named_jar_output_absent(temp.path(), &output)?;
        fs::write(&output, b"prior packaged evidence")?;
        assert!(require_named_jar_output_absent(temp.path(), &output).is_err());
        assert_eq!(fs::read(&output)?, b"prior packaged evidence");
        let directory_output = temp.path().join("directory.jar");
        fs::create_dir(&directory_output)?;
        assert!(require_named_jar_output_absent(temp.path(), &directory_output).is_err());
        assert!(directory_output.is_dir());
        assert!(
            require_named_jar_output_path(temp.path(), &temp.path().join("../other.jar")).is_err()
        );
        Ok(())
    }

    #[test]
    fn named_jar_cache_tree_budgets_generated_names_and_conflicting_pins() -> eyre::Result<()> {
        let temp = tempfile::tempdir()?;
        let root = temp.path().join("dependencies");
        fs::create_dir(&root)?;
        let first = root.join("declared.jar");
        fs::write(&first, b"fixture bytes")?;
        require_named_jar_cache_tree_with_bounds(&root, 1, 1)?;
        let allowed = BTreeSet::from([first.clone()]);
        require_named_generated_jar(&allowed, &first)?;
        let second = root.join("unknown.jar");
        fs::write(&second, b"not a declared path")?;
        assert!(require_named_generated_jar(&allowed, &second).is_err());
        assert!(require_named_jar_cache_tree_with_bounds(&root, 1, 1).is_err());
        assert!(require_named_jar_cache_tree_with_bounds(&root, 0, 1).is_err());
        assert!(require_named_jar_cache_tree_with_bounds(&root, usize::MAX, 1).is_err());
        let nested = root.join("one/two");
        fs::create_dir_all(&nested)?;
        assert!(require_named_jar_cache_tree_with_bounds(&root, 8, 1).is_err());
        require_named_jar_cache_tree_with_bounds(&root, 8, 2)?;
        let one = ContentHash::from_bytes(b"one", ContentHashAlgorithm::Blake3);
        let two = ContentHash::from_bytes(b"two", ContentHashAlgorithm::Blake3);
        let mut pins = BTreeMap::new();
        insert_named_jar_pin(&mut pins, first.clone(), one)?;
        insert_named_jar_pin(&mut pins, first.clone(), one)?;
        assert!(insert_named_jar_pin(&mut pins, first, two).is_err());
        assert!(require_named_jar_output_path(temp.path(), &root).is_err());
        let project = temp.path().join("project");
        fs::create_dir(&project)?;
        let diagnostic = project.join("reobf");
        fs::create_dir(&diagnostic)?;
        let old_args = diagnostic.join("tool-specialsource-package.java.args");
        let old_log = diagnostic.join("console.log");
        // Nonregular pre-existing old leaves must be retained, never followed or reset.
        fs::create_dir(&old_args)?;
        fs::create_dir(&old_log)?;
        fs::write(old_args.join("kept.txt"), b"older diagnostic evidence")?;
        let mut created = NamedPackageDiagnosticFile::create(&project, &diagnostic, "argfile")?;
        let log = NamedPackageDiagnosticFile::create(&project, &diagnostic, "console_log")?;
        assert_ne!(created.path, old_args);
        assert_ne!(log.path, old_log);
        created.held.write_all(b"new owned arguments")?;
        created.held.sync_all()?;
        created.recheck()?;
        log.recheck()?;
        assert_eq!(fs::read(&created.path)?, b"new owned arguments");
        assert!(old_args.is_dir() && old_log.is_dir());
        assert_eq!(
            fs::read(old_args.join("kept.txt"))?,
            b"older diagnostic evidence"
        );
        assert!(NamedPackageDiagnosticFile::create(&project, &diagnostic, "unreviewed").is_err());
        Ok(())
    }

    #[test]
    fn named_jar_held_inputs_refuse_bytes_and_same_byte_path_replacement() -> eyre::Result<()> {
        let temp = tempfile::tempdir()?;
        let path = temp.path().join("input.jar");
        let bytes = b"original bounded input";
        fs::write(&path, bytes)?;
        let hash = ContentHash::from_bytes(bytes, ContentHashAlgorithm::Blake3);
        assert_eq!(capture_named_package_hash(&path)?, hash);
        let held = ReviewedSpecialSourceFile::read(&path, &hash)?;
        held.recheck()?;
        fs::write(&path, b"changed bounded input!")?;
        assert!(held.recheck().is_err());
        fs::write(&path, bytes)?;
        let before_replacement = ReviewedSpecialSourceFile::read(&path, &hash)?;
        fs::rename(&path, temp.path().join("old-kept.jar"))?;
        fs::write(&path, bytes)?;
        assert!(before_replacement.recheck().is_err());
        let empty = temp.path().join("empty.jar");
        fs::write(&empty, [])?;
        let empty_hash = ContentHash::from_bytes(&[], ContentHashAlgorithm::Blake3);
        assert_eq!(
            empty_hash.to_string(),
            "blake3:af1349b9f5f9a1a6a0404dea36dcc9499bcb25c9"
        );
        assert!(ReviewedSpecialSourceFile::read(&empty, &empty_hash).is_err());
        assert!(capture_named_package_hash(&empty).is_err());
        assert_eq!(capture_named_mixin_hash(&empty)?, empty_hash);
        assert!(NamedPackageHeldFile::read("tool", &empty, &empty_hash).is_err());
        assert!(NamedPackageHeldFile::read("official_to_srg", &empty, &empty_hash).is_err());
        assert!(NamedPackageHeldFile::read("live_classpath", &empty, &empty_hash).is_err());
        let empty_held =
            NamedPackageHeldFile::read("package_mixin_reobfuscation", &empty, &empty_hash)?;
        assert_eq!(empty_held.bytes(), 0);
        empty_held.recheck()?;
        fs::write(&empty, b"changed after admission")?;
        assert!(empty_held.recheck().is_err());
        fs::write(&empty, [])?;
        let empty_before_replacement =
            NamedPackageHeldFile::read("package_mixin_reobfuscation", &empty, &empty_hash)?;
        fs::rename(&empty, temp.path().join("empty-kept.tsrg"))?;
        fs::write(&empty, [])?;
        assert!(empty_before_replacement.recheck().is_err());
        assert!(ReviewedSpecialSourceFile::read(temp.path(), &hash).is_err());
        Ok(())
    }

    fn unlaunched_receipt() -> NamedPackageLaunchReceipt {
        NamedPackageLaunchReceipt {
            schema: "sfm:named_jar_specialsource_launch@1".to_owned(),
            mode: "project_jar_two_mappings_live".to_owned(),
            phase: "prepared".to_owned(),
            projection_key: "review-only".to_owned(),
            context_identity: "review-only".to_owned(),
            preparation_identity: "review-only".to_owned(),
            recipe_id: "sfm:released-native-inputs/4.34.0/1.19.2@1".to_owned(),
            source_lock_sha256: NAMED_JAR_RECIPE_LOCKS[0].1.to_owned(),
            sdk_identity: "not executed".to_owned(),
            cache_identity: "not executed".to_owned(),
            classpath_entries: 0,
            exact_jar_arguments: vec![],
            compilation: "not_executed_fixture".to_owned(),
            application_execution: "not_performed".to_owned(),
            runtime_compatibility: "not_performed".to_owned(),
            java_executable: "not executed".to_owned(),
            tool_coordinate: NAMED_JAR_TOOL_COORDINATE.to_owned(),
            tool_content_hash: NAMED_JAR_TOOL_HASH.to_owned(),
            main_class: NAMED_JAR_MAIN.to_owned(),
            inputs: vec![],
            output_jar: "not executed".to_owned(),
            output_content_hash: None,
            output_bytes: None,
            diagnostic_cwd: "not executed".to_owned(),
            launch_cwd: "not executed".to_owned(),
            launch_cwd_utf16_units: 0,
            scratch_kept: true,
            diagnostics_kept: true,
            original_argfile: "not executed".to_owned(),
            transport_argfile: "not executed".to_owned(),
            console_log: "not executed".to_owned(),
            argfile_bytes: 0,
            argfile_sha256: crate::source_projection::provenance::sha256(&[]),
            child_created: false,
            child_pid: None,
            io_error_kind: None,
            raw_os_error: None,
            child_status: None,
            cancelled: false,
        }
    }

    #[test]
    fn named_jar_receipt_observes_spawn_refusal_without_process_execution() -> eyre::Result<()> {
        let mut receipt = unlaunched_receipt();
        receipt.inputs.push(NamedPackageInputWitness {
            role: "package_mixin_reobfuscation".to_owned(),
            path: "not executed".to_owned(),
            content_hash: "blake3:af1349b9f5f9a1a6a0404dea36dcc9499bcb25c9".to_owned(),
            bytes: 0,
            empty: true,
        });
        let failure = observe_process_spawn_result::<u32>(
            Err(std::io::Error::from_raw_os_error(123)),
            |value| *value,
            |report| receipt.observe(report),
        );
        assert!(failure.is_err());
        assert_eq!(receipt.phase, "spawn_failed");
        assert!(!receipt.child_created);
        assert_eq!(receipt.child_pid, None);
        assert_eq!(receipt.raw_os_error, Some(123));
        assert!(receipt.io_error_kind.is_some());
        let json = facet_json::to_string(&receipt)?;
        assert!(json.contains("\"application_execution\":\"not_performed\""));
        assert!(json.contains("\"runtime_compatibility\":\"not_performed\""));
        assert!(json.contains("\"output_content_hash\":null"));
        assert!(json.contains("\"role\":\"package_mixin_reobfuscation\""));
        assert!(json.contains("\"bytes\":0,\"empty\":true"));
        assert!(json.contains("blake3:af1349b9f5f9a1a6a0404dea36dcc9499bcb25c9"));
        assert!(json.contains("\"mode\":\"project_jar_two_mappings_live\""));
        assert_eq!(NAMED_JAR_MAX_CLASSPATH, 256);
        assert_eq!(NAMED_JAR_MAX_HELD_BYTES, 2 * 1024 * 1024 * 1024);
        assert_eq!(NAMED_JAR_MAX_ARGFILE_BYTES, 1024 * 1024);
        assert_eq!(NAMED_JAR_MAX_RECEIPT_BYTES, 2 * 1024 * 1024);
        Ok(())
    }

    #[cfg(windows)]
    #[test]
    fn named_jar_windows_equivalent_mapping_paths_do_not_authorize_changed_argv() -> eyre::Result<()>
    {
        let temp = tempfile::tempdir()?;
        let cache = temp.path().join("build/sfm-toolchain/native-project/owned");
        let paths = project_package_input_paths(&cache, "1.20");
        let output = temp.path().join("release.jar");
        let args = named_jar_arguments(
            &paths.development_jar,
            &output,
            &paths.reobf_mapping,
            &paths.mixin_reobf_mapping,
        )?;
        let old_guard_mapping = cache.join("forge/1.20/mappings/official_to_srg.tsrg");
        assert_eq!(paths.reobf_mapping, old_guard_mapping);
        assert_ne!(
            paths.reobf_mapping.as_os_str(),
            old_guard_mapping.as_os_str()
        );
        // Original native refusal: same Windows file, different exact argument bytes.
        assert!(
            require_named_jar_invocation(
                &args,
                &paths.development_jar,
                &output,
                &old_guard_mapping,
                &paths.mixin_reobf_mapping,
            )
            .is_err()
        );
        require_named_jar_invocation(
            &args,
            &paths.development_jar,
            &output,
            &paths.reobf_mapping,
            &paths.mixin_reobf_mapping,
        )?;
        for index in [1, 3, 5, 7] {
            let mut changed = args.clone();
            changed[index] = changed[index].replace('\\', "/");
            assert_ne!(changed[index], args[index]);
            assert!(
                require_named_jar_invocation(
                    &changed,
                    &paths.development_jar,
                    &output,
                    &paths.reobf_mapping,
                    &paths.mixin_reobf_mapping,
                )
                .is_err()
            );
        }
        Ok(())
    }

    #[test]
    fn named_jar_producer_admission_guard_and_receipt_share_original_package_paths()
    -> eyre::Result<()> {
        let temp = tempfile::tempdir()?;
        for (target, _) in NAMED_JAR_RECIPE_LOCKS {
            let cache = temp.path().join("build/sfm-toolchain/native-project/owned");
            let paths = project_package_input_paths(&cache, target);
            assert_eq!(
                paths.development_jar.as_os_str(),
                cache.join("project").join("dev.jar").as_os_str()
            );
            assert_eq!(
                paths.mixin_reobf_mapping.as_os_str(),
                cache
                    .join("project")
                    .join("compileJava-mappings.tsrg")
                    .as_os_str()
            );
            assert_eq!(
                paths.reobf_mapping.as_os_str(),
                cache
                    .join("forge")
                    .join(target)
                    .join("mappings")
                    .join("official_to_srg.tsrg")
                    .as_os_str()
            );
            let output = temp.path().join(format!("release-{target}.jar"));
            let args = named_jar_arguments(
                &paths.development_jar,
                &output,
                &paths.reobf_mapping,
                &paths.mixin_reobf_mapping,
            )?;
            require_named_jar_invocation(
                &args,
                &paths.development_jar,
                &output,
                &paths.reobf_mapping,
                &paths.mixin_reobf_mapping,
            )?;
            assert_eq!(args.len(), 9);
            assert_eq!(args[4], "--srg-in");
            assert_eq!(args[6], "--srg-in");
            assert_eq!(args[8], "--live");
        }
        let producer = include_str!("engine_execute.rs");
        let admission = include_str!("engine_named_jar.rs");
        assert!(producer.contains("} = project_package_input_paths("));
        // Ignore formatting in this source check, never in live argument admission.
        let compact_admission = admission.split_whitespace().collect::<String>();
        assert_eq!(
            compact_admission
                .matches("=project_package_input_paths(")
                .count(),
            4
        );
        assert!(admission.contains("args == named_jar_arguments(input, output, official, mixin)?"));
        // No path-normalization permission: original exact guard remains unchanged.
        Ok(())
    }

    #[derive(Facet)]
    struct LegacyJarIdentity {
        schema_version: u32,
        #[facet(proxy = JsonBranchName)]
        branch_name: BranchName,
        #[facet(proxy = JsonPath)]
        worktree_path: PathBuf,
        #[facet(proxy = JsonPath)]
        minecraft_dir: PathBuf,
    }
    #[derive(Facet)]
    struct ExistingAdditiveJarIdentity {
        schema_version: u32,
        #[facet(proxy = JsonOptionalBranchName, skip_unless_truthy)]
        branch_name: Option<BranchName>,
        #[facet(proxy = JsonOptionalPath, skip_unless_truthy)]
        worktree_path: Option<PathBuf>,
        #[facet(proxy = JsonPath)]
        minecraft_dir: PathBuf,
        #[facet(skip_unless_truthy)]
        catalog_project: Option<String>,
    }

    #[test]
    fn named_jar_legacy_wire_and_owner_output_conventions_remain_exact() -> eyre::Result<()> {
        let legacy = LegacyJarIdentity {
            schema_version: 1,
            branch_name: BranchName::from("1.19.2"),
            worktree_path: PathBuf::from("example/repository"),
            minecraft_dir: PathBuf::from("example/repository/platform/minecraft"),
        };
        let additive = ExistingAdditiveJarIdentity {
            schema_version: legacy.schema_version,
            branch_name: Some(legacy.branch_name.clone()),
            worktree_path: Some(legacy.worktree_path.clone()),
            minecraft_dir: legacy.minecraft_dir.clone(),
            catalog_project: None,
        };
        assert_eq!(
            facet_json::to_string(&legacy)?,
            facet_json::to_string(&additive)?
        );
        let named = ExistingAdditiveJarIdentity {
            schema_version: 2,
            branch_name: None,
            worktree_path: None,
            minecraft_dir: PathBuf::from("projections/arbitrary/nested/project"),
            catalog_project: Some("exact-owned-receipt".to_owned()),
        };
        let json = facet_json::to_string(&named)?;
        assert!(!json.contains("branch_name"));
        assert!(!json.contains("worktree_path"));
        assert!(json.contains("catalog_project"));
        for (target, _) in NAMED_JAR_RECIPE_LOCKS {
            let root = Path::new("owner/project");
            let rust = rust_output_jar_path(root, "sfm", target, "4.34.0");
            let gradle = gradle_output_jar_path(root, "sfm", target, "4.34.0");
            assert_ne!(rust, gradle);
            assert_eq!(
                rust,
                root.join(format!("build/libs/sfm-MC{target}-4.34.0-rust.jar"))
            );
            assert_eq!(
                gradle,
                root.join(format!("build/libs/sfm-MC{target}-4.34.0.jar"))
            );
        }
        Ok(())
    }
}
