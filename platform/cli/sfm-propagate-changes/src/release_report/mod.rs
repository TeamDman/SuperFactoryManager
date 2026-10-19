//! Modular Liquid report rendering with explicit evidence, hashes and budgets.
pub mod case;
pub mod code_diff;
pub mod document;
pub mod image;
pub mod jar_pair;
pub mod receipt;
mod render;
pub mod request;
pub mod section;
#[cfg(test)]
mod tests;
pub use render::render;

pub mod class_entry;
pub mod class_evidence;
mod class_tree;
mod static_patch;

mod compressed_html;

pub mod article;
mod article_appendix;
pub mod article_figure;
mod article_render;
mod rendered_image;

#[cfg(test)]
mod article_tests;

mod sfml;

mod captions;

mod correlation_render;
mod evidence_download;
pub mod source_correlation;
mod source_explorer;

pub(crate) mod comparison_render;

mod review_controls;
mod review_file;
mod review_payload;

pub mod reference_evidence;
mod whitespace_review;

pub mod source_paths;

mod archive_resource_pair;
mod archive_tree;

mod fonts;

mod article_release_artifact;
mod article_release_delivery;
mod article_release_validation;

#[cfg(test)]
mod article_release_validation_tests;

mod article_release_class_receipt;
