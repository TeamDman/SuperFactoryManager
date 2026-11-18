//! Inputs for publishing one successful compiler snapshot.
//! The publisher admits directory ownership and Java/source identities before copying.

use crate::jdk::ResolvedJava;
use std::path::Path;

#[derive(Clone, Copy)]
pub(crate) struct BuildSnapshotPublicationRequest<'a> {
    pub(crate) minecraft_dir: &'a Path,
    pub(crate) cache_dir: &'a Path,
    pub(crate) source_cache_dir: &'a Path,
    pub(crate) minecraft_version: &'a str,
    pub(crate) java: &'a ResolvedJava,
    pub(crate) project_dir: &'a Path,
    pub(crate) identities: (&'a str, &'a str),
}
