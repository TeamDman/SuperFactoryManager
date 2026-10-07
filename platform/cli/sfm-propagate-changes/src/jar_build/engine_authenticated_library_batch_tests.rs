// Proposed engine-namespace include after the separate stage1 production patch.
// Real checked project + original13 role inputs. Synthetic cached child/SDK
// files are test-only; no native build, JVM, publisher or acquisition acceptance.
#[cfg(test)]
mod authenticated_library_batch_integration_tests {
    use super::*;
    use crate::source_projection::catalog_owned_project::recheck_observation;
    use crate::source_projection::catalog_owned_project::tests::Fixture;
    use crate::source_projection::core_inputs::CORE_ROOT;
    use crate::source_projection::sync::MANIFEST_FILE;
    use std::cell::Cell;
    use std::net::TcpListener;
    use std::rc::Rc;

    const ORIGINAL_LOCK_SHA256: &str =
        "sha256:4e25ec4540fb6feecba27836c90ec7eab6a0109bc21f193ef358c64f558567ae";

    // Keep an unserved loopback proxy bound for this test. A regression cannot
    // contact an official host; any attempted connection also fails the test.
    struct OfflineClient {
        client: Client,
        listener: TcpListener,
    }

    impl OfflineClient {
        fn new() -> eyre::Result<Self> {
            let listener = TcpListener::bind((std::net::Ipv4Addr::LOCALHOST, 0))?;
            listener.set_nonblocking(true)?;
            let proxy = reqwest::Proxy::all(format!("http://{}", listener.local_addr()?))?;
            let client = Client::builder()
                .no_proxy()
                .proxy(proxy)
                .timeout(Duration::from_millis(300))
                .build()?;
            Ok(Self { client, listener })
        }

        fn assert_uncontacted(&self) {
            match self.listener.accept() {
                Err(error) if error.kind() == std::io::ErrorKind::WouldBlock => {}
                Ok(_) => panic!("cached/refused fixture attempted an HTTP proxy connection"),
                Err(error) => panic!("cannot inspect fixture proxy listener: {error}"),
            }
        }
    }

    struct LibraryFixture {
        owner: Fixture,
        plan: BuildPlan,
        payload: Vec<u8>,
        original_lock: Vec<u8>,
    }

    impl LibraryFixture {
        #[expect(
            clippy::too_many_lines,
            reason = "The real catalog handle and complete non-executed test plan stay explicit."
        )]
        fn new(count: usize) -> eyre::Result<Self> {
            let (owner, slot, recipe_id, original_lock) =
                named_forge_compile_cohort_tests::fixture("1.19.2")?;
            let frozen = Arc::new(named_forge_compile_cohort_tests::prepared(
                &owner, slot, "release", &recipe_id,
            )?);
            assert_eq!(
                frozen.dependencies().receipt().source_lock_sha256,
                ORIGINAL_LOCK_SHA256,
            );
            assert_eq!(frozen.receipt().role_bindings.len(), 13);
            let runtime = owner.repository().join("isolated-library-fixture");
            fs::create_dir_all(runtime.join("libraries"))?;
            fs::create_dir_all(runtime.join("sdk/bin"))?;
            fs::create_dir_all(runtime.join("sdk/lib"))?;
            let sdk = runtime.join("sdk");
            let executable_name = if cfg!(windows) { "java.exe" } else { "java" };
            let javac_name = if cfg!(windows) { "javac.exe" } else { "javac" };
            for path in [
                sdk.join("bin").join(executable_name),
                sdk.join("bin").join(javac_name),
                sdk.join("release"),
                sdk.join("lib/modules"),
            ] {
                fs::write(path, b"synthetic fixture bytes; never executed")?;
            }
            let mut java = JavaPlan {
                executable: sdk.join("bin").join(executable_name),
                home: Some(sdk),
                version_output: "synthetic SDK identity, no java process".to_owned(),
                major_version: 17,
                selection: "synthetic-files-no-jvm-run".to_owned(),
                pin_url: None,
                pin_sha512: None,
                execution_identity: None,
            };
            java.execution_identity = Some(named_sdk_identity(&java)?);
            let (inputs, payload) =
                authenticated_minecraft_inputs::test_support::cached_library_inputs(count)?;
            for request in inputs.libraries() {
                let path = runtime
                    .join("libraries")
                    .join(request.library_relative_path().unwrap());
                fs::create_dir_all(path.parent().unwrap())?;
                fs::write(path, &payload)?;
            }
            let version_json = ArtifactPlan {
                id: ArtifactId::from("synthetic-parent-not-read"),
                coordinate: None,
                repository: None,
                url: Some(inputs.parent_url().to_owned()),
                cache_path: runtime.join("synthetic-parent-not-read.json"),
                sha1: Some(inputs.parent_hash()),
                downloaded: false,
                required_for: ArtifactPurpose::from("test-only-authenticated-child-selection"),
                provenance: artifact_provenance(
                    ArtifactSource::RemoteHttp,
                    None,
                    None,
                    Some(inputs.parent_url().to_owned()),
                    None,
                    None,
                    inputs.parent_hash(),
                ),
            };
            let minecraft_dir = frozen.project().project_root().to_path_buf();
            let lockfile_path = minecraft_dir.join("sfm-toolchain.lock.json");
            let plan = BuildPlan {
                schema_version: 1,
                mode: "synthetic-library-cache-test-only".to_owned(),
                branch_name: None,
                minecraft_version: MinecraftVersion::parse("1.19.2")?,
                worktree_path: None,
                minecraft_dir,
                gradle_output_jar: runtime.join("not-built/gradle.jar"),
                rust_output_jar: runtime.join("not-built/rust.jar"),
                cache_dir: runtime.join("native-cache-not-executed"),
                common_cache_dir: runtime.clone(),
                state_dir: runtime.join("state-not-published"),
                maven_cache_dir: runtime.join("maven-not-resolved"),
                minecraft_cache_dir: runtime.join("minecraft-not-acquired"),
                minecraft_version_cache_dir: runtime.join("version-not-acquired"),
                minecraft_assets_dir: runtime.join("assets-not-acquired"),
                minecraft_libraries_dir: runtime.join("libraries"),
                lockfile_path,
                lockfile: None,
                java,
                java_release: 17,
                refresh: false,
                allow_local_artifact_cache: false,
                artifact_sources: Vec::new(),
                properties: BTreeMap::new(),
                repositories: Vec::new(),
                loader_toolchain: LoaderToolchainPlan {
                    kind: LoaderToolchainKind::ForgeGradleForge,
                    base_coordinate: "net.minecraftforge:forge:1.19.2-43.4.0".to_owned(),
                    userdev_coordinate: "net.minecraftforge:forge:1.19.2-43.4.0:userdev".to_owned(),
                    sources_coordinate: None,
                    universal_coordinate: None,
                },
                artifacts: Vec::new(),
                minecraft: MinecraftPlan {
                    version_manifest: None,
                    version_json,
                    client_jar_url: String::new(),
                    server_jar_url: String::new(),
                    client_mappings_url: None,
                    server_mappings_url: None,
                    libraries_count: count,
                    authenticated_inputs: Some(inputs),
                },
                forge_userdev: None,
                mcp_config: None,
                dependencies: Vec::new(),
                graph: Vec::new(),
                artifact_portability: ArtifactPortabilityAudit::default(),
                warnings: Vec::new(),
                catalog_project: Some(frozen.receipt().clone()),
                identity: BuildProjectIdentity::Catalog { frozen },
            };
            Ok(Self {
                owner,
                plan,
                payload,
                original_lock,
            })
        }

        fn context(&self) -> eyre::Result<ExecutionContext<'_>> {
            ExecutionContext::new(&self.plan, CancellationToken::new())
        }

        fn expected_paths(&self) -> Vec<PathBuf> {
            self.plan
                .minecraft
                .authenticated_inputs
                .as_ref()
                .unwrap()
                .libraries()
                .map(|request| {
                    self.plan
                        .minecraft_libraries_dir
                        .join(request.library_relative_path().unwrap())
                })
                .collect()
        }

        fn assert_original_lock_unchanged(&self) -> eyre::Result<()> {
            assert_eq!(fs::read(&self.plan.lockfile_path)?, self.original_lock);
            assert_eq!(
                self.plan
                    .named_project()
                    .unwrap()
                    .dependencies()
                    .raw_source_lock_bytes(),
                self.original_lock,
            );
            assert!(
                !self
                    .owner
                    .repository()
                    .join("platform/minecraft/build")
                    .exists()
            );
            Ok(())
        }
    }

    fn observe(
        change: impl FnMut(usize, &crate::source_projection::catalog_owned_project::CatalogOwnedProject)
        + 'static,
    ) -> (Rc<Cell<usize>>, recheck_observation::Scope) {
        let count = Rc::new(Cell::new(0));
        let observed = Rc::clone(&count);
        let mut change = change;
        let scope = recheck_observation::install(move |project| {
            observed.set(observed.get() + 1);
            change(observed.get(), project);
        });
        (count, scope)
    }

    #[test]
    fn real_full_check_count_is_constant_for_one_and_thirty_two_cached_libraries()
    -> eyre::Result<()> {
        for library_count in [1, 32] {
            let fixture = LibraryFixture::new(library_count)?;
            let context = fixture.context()?;
            let offline = OfflineClient::new()?;
            let expected = fixture.expected_paths();
            let (checks, _scope) = observe(|_, _| {});
            assert_eq!(
                resolve_current_minecraft_libraries(&context, &offline.client)?,
                expected
            );
            assert_eq!(
                checks.get(),
                2,
                "real checks before batch and before vector publication"
            );
            assert_eq!(
                context.minecraft_libraries_cache.lock().unwrap().as_ref(),
                Some(&expected)
            );
            assert_eq!(
                resolve_current_minecraft_libraries(&context, &offline.client)?,
                expected
            );
            assert_eq!(checks.get(), 3, "cached vector return must recheck once");
            let cfg = fixture.plan.cache_dir.join("cfg/libraries.cfg");
            write_minecraft_libraries_cfg(&context, &offline.client, &cfg)?;
            assert_eq!(
                checks.get(),
                5,
                "cached-vector return and cfg publication each recheck"
            );
            let mut expected_lines = expected
                .iter()
                .map(|path| dunce::canonicalize(path).map(|path| format!("-e={}", path.display())))
                .collect::<std::io::Result<Vec<_>>>()?;
            expected_lines.sort();
            assert_eq!(
                fs::read_to_string(&cfg)?,
                format!("{}\n", expected_lines.join("\n"))
            );
            offline.assert_uncontacted();
            fixture.assert_original_lock_unchanged()?;
        }
        Ok(())
    }

    #[test]
    fn first_config_write_counts_three_real_checks_not_one_per_library() -> eyre::Result<()> {
        let fixture = LibraryFixture::new(32)?;
        let context = fixture.context()?;
        let offline = OfflineClient::new()?;
        let cfg = fixture.plan.cache_dir.join("cfg/libraries.cfg");
        let (checks, _scope) = observe(|_, _| {});
        write_minecraft_libraries_cfg(&context, &offline.client, &cfg)?;
        assert_eq!(checks.get(), 3);
        assert!(cfg.is_file());
        offline.assert_uncontacted();
        fixture.assert_original_lock_unchanged()?;
        Ok(())
    }

    #[derive(Clone, Copy)]
    enum Change {
        Authored,
        Generated,
        Provenance,
        UnownedGeneratedSource,
    }

    fn change_before_publication(kind: Change) -> eyre::Result<()> {
        let fixture = LibraryFixture::new(8)?;
        let context = fixture.context()?;
        let offline = OfflineClient::new()?;
        let cfg = fixture.plan.cache_dir.join("cfg/not-published.cfg");
        let target = match kind {
            Change::Authored => fixture
                .owner
                .repository()
                .join(CORE_ROOT)
                .join("src/main/java/Shared.java"),
            Change::Generated => fixture.plan.minecraft_dir.join("src/main/java/Shared.java"),
            Change::Provenance => fixture.plan.minecraft_dir.join(MANIFEST_FILE),
            Change::UnownedGeneratedSource => fixture
                .plan
                .minecraft_dir
                .join("src/main/java/Unowned.java"),
        };
        let changed = b"// deterministic isolated fixture change\n".to_vec();
        let expected_changed = changed.clone();
        let observed_target = target.clone();
        let (checks, _scope) = observe(move |number, _| {
            if number == 2 {
                fs::write(&observed_target, &changed).unwrap();
            }
        });
        assert!(write_minecraft_libraries_cfg(&context, &offline.client, &cfg).is_err());
        assert_eq!(checks.get(), 2);
        assert!(context.minecraft_libraries_cache.lock().unwrap().is_none());
        assert!(!cfg.exists());
        assert!(!cfg.parent().unwrap().exists());
        assert_eq!(
            fs::read(&target)?,
            expected_changed,
            "refusal must not repair source/provenance"
        );
        for path in fixture.expected_paths() {
            assert_eq!(fs::read(path)?, fixture.payload);
        }
        offline.assert_uncontacted();
        fixture.assert_original_lock_unchanged()?;
        Ok(())
    }

    #[test]
    fn changed_authored_source_refuses_before_vector_or_cfg_publication() -> eyre::Result<()> {
        change_before_publication(Change::Authored)
    }
    #[test]
    fn changed_generated_source_refuses_before_vector_or_cfg_publication() -> eyre::Result<()> {
        change_before_publication(Change::Generated)
    }
    #[test]
    fn changed_provenance_refuses_before_vector_or_cfg_publication() -> eyre::Result<()> {
        change_before_publication(Change::Provenance)
    }
    #[test]
    fn extra_unowned_generated_source_refuses_before_vector_or_cfg_publication() -> eyre::Result<()>
    {
        change_before_publication(Change::UnownedGeneratedSource)
    }

    #[test]
    fn changed_owner_is_refused_before_an_existing_cached_vector_returns() -> eyre::Result<()> {
        let fixture = LibraryFixture::new(4)?;
        let context = fixture.context()?;
        let offline = OfflineClient::new()?;
        let target = fixture
            .owner
            .repository()
            .join(CORE_ROOT)
            .join("src/main/java/Shared.java");
        let (checks, _scope) = observe(move |number, _| {
            if number == 3 {
                fs::write(&target, b"// changed after vector cached\n").unwrap();
            }
        });
        let expected = resolve_current_minecraft_libraries(&context, &offline.client)?;
        assert_eq!(checks.get(), 2);
        assert!(resolve_current_minecraft_libraries(&context, &offline.client).is_err());
        assert_eq!(checks.get(), 3);
        assert_eq!(
            context.minecraft_libraries_cache.lock().unwrap().as_ref(),
            Some(&expected)
        );
        offline.assert_uncontacted();
        fixture.assert_original_lock_unchanged()?;
        Ok(())
    }

    #[test]
    fn changed_owner_after_cached_vector_return_refuses_before_cfg_parent_or_write()
    -> eyre::Result<()> {
        let fixture = LibraryFixture::new(4)?;
        let context = fixture.context()?;
        let offline = OfflineClient::new()?;
        let target = fixture.plan.minecraft_dir.join(MANIFEST_FILE);
        let (checks, _scope) = observe(move |number, _| {
            if number == 4 {
                fs::write(&target, b"changed provenance before cfg write").unwrap();
            }
        });
        let expected = resolve_current_minecraft_libraries(&context, &offline.client)?;
        let cfg = fixture.plan.cache_dir.join("cfg/not-created/library.cfg");
        assert!(write_minecraft_libraries_cfg(&context, &offline.client, &cfg).is_err());
        assert_eq!(checks.get(), 4);
        assert!(!cfg.exists());
        assert!(!cfg.parent().unwrap().exists());
        assert_eq!(
            context.minecraft_libraries_cache.lock().unwrap().as_ref(),
            Some(&expected)
        );
        offline.assert_uncontacted();
        fixture.assert_original_lock_unchanged()?;
        Ok(())
    }

    #[test]
    fn same_size_corrupt_library_is_refused_under_real_owner_without_repair_or_publication()
    -> eyre::Result<()> {
        let fixture = LibraryFixture::new(4)?;
        let context = fixture.context()?;
        let offline = OfflineClient::new()?;
        let path = fixture.expected_paths()[0].clone();
        let mut corrupt = fixture.payload.clone();
        corrupt[0] ^= 1;
        fs::write(&path, &corrupt)?;
        let (checks, _scope) = observe(|_, _| {});
        let error = resolve_current_minecraft_libraries(&context, &offline.client).unwrap_err();
        assert!(error.to_string().contains("hash mismatch"));
        assert_eq!(checks.get(), 1);
        assert!(context.minecraft_libraries_cache.lock().unwrap().is_none());
        assert_eq!(fs::read(&path)?, corrupt);
        offline.assert_uncontacted();
        fixture.assert_original_lock_unchanged()?;
        Ok(())
    }

    #[test]
    fn missing_library_rechecks_real_owner_before_http_or_artifact_write() -> eyre::Result<()> {
        let fixture = LibraryFixture::new(4)?;
        let context = fixture.context()?;
        let offline = OfflineClient::new()?;
        let path = fixture.expected_paths()[0].clone();
        let retained = path.with_extension("fixture-kept");
        fs::rename(&path, &retained)?;
        let target = fixture
            .owner
            .repository()
            .join(CORE_ROOT)
            .join("src/main/java/Shared.java");
        let (checks, _scope) = observe(move |number, _| {
            if number == 2 {
                fs::write(&target, b"// changed before missing-file acquisition\n").unwrap();
            }
        });
        assert!(resolve_current_minecraft_libraries(&context, &offline.client).is_err());
        assert_eq!(
            checks.get(),
            2,
            "batch check plus fresh missing-effect real owner check"
        );
        assert!(context.minecraft_libraries_cache.lock().unwrap().is_none());
        assert!(!path.exists());
        assert_eq!(fs::read(&retained)?, fixture.payload);
        assert!(artifact_lock_path(&path)?.is_file());
        offline.assert_uncontacted();
        fixture.assert_original_lock_unchanged()?;
        Ok(())
    }

    #[test]
    fn cancellation_after_real_final_check_does_not_publish_cached_vector_or_cfg()
    -> eyre::Result<()> {
        let fixture = LibraryFixture::new(4)?;
        let context = fixture.context()?;
        let offline = OfflineClient::new()?;
        let token = context.cancellation_token.clone();
        let (checks, _scope) = observe(move |number, _| {
            if number == 2 {
                token.request_cancel("deterministic final-publication cancellation");
            }
        });
        let cfg = fixture.plan.cache_dir.join("cfg/not-published.cfg");
        assert!(write_minecraft_libraries_cfg(&context, &offline.client, &cfg).is_err());
        assert_eq!(checks.get(), 2);
        assert!(context.minecraft_libraries_cache.lock().unwrap().is_none());
        assert!(!cfg.exists());
        assert!(!cfg.parent().unwrap().exists());
        offline.assert_uncontacted();
        fixture.assert_original_lock_unchanged()?;
        Ok(())
    }
}
