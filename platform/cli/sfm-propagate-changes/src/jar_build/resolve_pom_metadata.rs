use std::fs::File;
use std::io::Cursor;
use std::io::Read as _;
use zip::ZipArchive;
impl Resolver {
    // POM discovery is deliberately cache-only. Public metadata provisioning is a
    // separate reviewed operation; an offline test never silently fetches parents.
    fn read_pinned_pom(&self, coordinate: &MavenCoordinate) -> eyre::Result<String> {
        self.read_pinned_pom_traced(coordinate, None)
    }
    fn read_pinned_pom_traced(
        &self,
        coordinate: &MavenCoordinate,
        mut trace: Option<&mut super::maven_metadata_trace::MavenMetadataTrace>,
    ) -> eyre::Result<String> {
        if let Some(t) = trace.as_mut() {
            t.visit()?;
        }
        super::maven_pom_model::validate_coordinate(coordinate)?;
        let path = maven_cache_path_for(&self.cache_dir, coordinate);
        let catalog_path = self.cache_dir.join("maven-metadata-pins.json");
        if catalog_path.exists() {
            let _catalog_guard = acquire_artifact_path_read_lock_cancellable(
                &catalog_path,
                &self.cancellation_token,
            )?;
            let raw = bounded_regular_read(&catalog_path, &self.cache_dir, 2 * 1024 * 1024)?;
            let catalog: super::maven_metadata_catalog::MavenMetadataCatalog =
                facet_json::from_slice(&raw)?;
            eyre::ensure!(
                catalog.schema == "sfm:maven_metadata_provision@1"
                    && catalog.artifacts.len() <= 4096,
                "unsupported/excessive pinned Maven metadata catalog"
            );
            let mut matching = catalog
                .artifacts
                .iter()
                .filter(|p| p.coordinate == coordinate.to_string());
            if let Some(pin) = matching.next() {
                eyre::ensure!(
                    matching.next().is_none(),
                    "duplicate Maven metadata pin: {coordinate}"
                );
                let relative = path
                    .strip_prefix(&self.cache_dir)?
                    .to_string_lossy()
                    .replace('\\', "/");
                eyre::ensure!(
                    pin.path == relative && pin.bytes <= 2 * 1024 * 1024,
                    "Maven metadata pin path/size mismatch: {coordinate}"
                );
                let origin = reqwest::Url::parse(&pin.url)?;
                let repository = reqwest::Url::parse(&catalog.repository)?;
                eyre::ensure!(
                    origin.username().is_empty()
                        && origin.password().is_none()
                        && origin.query().is_none()
                        && origin.fragment().is_none()
                        && origin.scheme() == "https"
                        && origin.port().is_none(),
                    "unsafe metadata origin: {coordinate}"
                );
                eyre::ensure!(
                    origin.as_str()
                        == format!(
                            "{}{}",
                            catalog.repository.trim_end_matches('/').to_owned() + "/",
                            relative
                        )
                        && self
                            .repositories
                            .iter()
                            .any(|r| metadata_repository_matches(&r.url, &repository)),
                    "metadata origin is not a configured repository: {coordinate}"
                );
                let _pom_guard =
                    acquire_artifact_path_read_lock_cancellable(&path, &self.cancellation_token)?;
                let raw = bounded_regular_read(&path, &self.cache_dir, 2 * 1024 * 1024)
                    .wrap_err_with(|| format!("offline Maven metadata missing/unreadable: {coordinate}; expected pinned POM at {}", path.display()))?;
                eyre::ensure!(
                    raw.len() as u64 == pin.bytes
                        && crate::source_projection::provenance::sha256(&raw) == pin.sha256,
                    "pinned Maven metadata hash/size mismatch: {coordinate}"
                );
                if let Some(t) = trace {
                    t.capture(&catalog.repository, pin, &raw)?;
                }
                return Ok(String::from_utf8(raw)?);
            }
        }
        if let Some(pom) = self.read_frozen_embedded_pom(coordinate)? {
            if let Some(t) = trace {
                t.count_bytes(pom.len())?;
            }
            return Ok(pom);
        }

        eyre::bail!(
            "offline Maven metadata missing: {coordinate}; expected hash-pinned POM at {} and origin entry in {}; provision authoritative public metadata separately",
            path.display(),
            catalog_path.display()
        )
    }
    fn read_frozen_embedded_pom(
        &self,
        coordinate: &MavenCoordinate,
    ) -> eyre::Result<Option<String>> {
        let jar = coordinate.with_extension("jar");
        if let Some(pin) = self
            .locked_artifact_hash(&jar)
            .filter(|p| p.algorithm == ContentHashAlgorithm::Blake3)
        {
            let jar_path = maven_cache_path_for(&self.cache_dir, &jar);
            if jar_path.exists() {
                let _guard = acquire_artifact_path_read_lock_cancellable(
                    &jar_path,
                    &self.cancellation_token,
                )?;
                let raw = bounded_regular_read(&jar_path, &self.cache_dir, 64 * 1024 * 1024)?;
                eyre::ensure!(
                    ContentHash::from_bytes(&raw, ContentHashAlgorithm::Blake3) == *pin,
                    "frozen JAR hash mismatch during POM discovery: {jar}"
                );
                let mut archive = ZipArchive::new(Cursor::new(raw))?;
                let entry = format!(
                    "META-INF/maven/{}/{}/pom.xml",
                    coordinate.group, coordinate.artifact
                );
                match archive.by_name(&entry) {
                    Ok(pom) => {
                        eyre::ensure!(
                            pom.size() <= 2 * 1024 * 1024,
                            "embedded POM byte budget exceeded: {jar}"
                        );
                        let mut text = String::new();
                        pom.take(2 * 1024 * 1024 + 1).read_to_string(&mut text)?;
                        eyre::ensure!(
                            text.len() <= 2 * 1024 * 1024,
                            "embedded POM byte budget exceeded: {jar}"
                        );
                        return Ok(Some(text));
                    }
                    Err(zip::result::ZipError::FileNotFound) => {}
                    Err(error) => return Err(error.into()),
                }
            }
        }
        Ok(None)
    }
}
fn bounded_regular_read(path: &Path, root: &Path, budget: u64) -> eyre::Result<Vec<u8>> {
    let relative = path.strip_prefix(root)?;
    let mut current = root.to_path_buf();
    for component in relative.components() {
        eyre::ensure!(
            matches!(component, std::path::Component::Normal(_)),
            "unsafe metadata cache path"
        );
        current.push(component);
        eyre::ensure!(
            !fs::symlink_metadata(&current)?.file_type().is_symlink(),
            "metadata cache symlink refused: {}",
            current.display()
        );
    }
    let file = File::open(path)?;
    eyre::ensure!(
        file.metadata()?.is_file() && file.metadata()?.len() <= budget,
        "metadata artifact byte/type budget exceeded: {}",
        path.display()
    );
    let mut raw = Vec::new();
    file.take(budget + 1).read_to_end(&mut raw)?;
    eyre::ensure!(
        raw.len() as u64 <= budget,
        "metadata artifact grew past budget"
    );
    Ok(raw)
}
fn metadata_repository_matches(configured: &str, origin: &reqwest::Url) -> bool {
    let Ok(configured) = reqwest::Url::parse(configured) else {
        return false;
    };
    let central = |url: &reqwest::Url| {
        url.scheme() == "https"
            && url.port().is_none()
            && url.username().is_empty()
            && url.password().is_none()
            && url.query().is_none()
            && url.fragment().is_none()
            && url.path().trim_end_matches('/') == "/maven2"
            && matches!(
                url.host_str(),
                Some("repo1.maven.org" | "repo.maven.apache.org")
            )
    };
    configured.as_str().trim_end_matches('/') == origin.as_str().trim_end_matches('/')
        || central(&configured) && central(origin)
}
