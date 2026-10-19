use crate::jar_build::json_path::JsonPath;
use facet::Facet;
use std::path::PathBuf;

/// A plan locates its cache; only the snapshot receipt proves compile success.
#[derive(Facet)]
pub(super) struct HotswapCachePlan {
    #[facet(proxy = JsonPath)]
    pub cache_dir: PathBuf,
}
