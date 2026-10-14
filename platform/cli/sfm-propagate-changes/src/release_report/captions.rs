//! Human-facing capture labels; original IDs, captions and hashes stay in details.
use super::image::ReleaseReportImage;
pub(super) fn human(image: &ReleaseReportImage) -> String {
    if !image.id.starts_with("issue610-") {
        return image.caption.clone();
    }
    let version = if image.id.contains("-green-") {
        "4.35 candidate"
    } else {
        "4.34 baseline"
    };
    let historical = if image.id.ends_with("-final") {
        ""
    } else {
        "Earlier capture (historical export failure). "
    };
    let description = if image.id.contains("dropper-initial") {
        "Before any SFM transfer: the first dropper performed a real 25-FE item drop and holds 99,975 / 100,000 FE. The actual hovered tooltip rounds to 100.0k/100.0k; synchronized server/client assertions establish the 25-FE gap. This setup capture predates the surrounding second-pass result views."
    } else if image.id.contains("source-before") {
        "Before the first SFM transfer: a full Mekanism cube. The rounded gauge shows 102.4 MFE; the fixture asserts 256,000,000 J."
    } else if image.id.contains("source-after") && image.id.contains("-green-") {
        "After the second transfer: the cube supplied the later consumer. Its rounded gauge shows 102.3 MFE; the raw source assertion is 255,750,000 J."
    } else if image.id.contains("source-after") {
        "After the second transfer: the cube stayed full while the later consumer stayed empty. Its rounded gauge shows 102.4 MFE; the raw assertion is 256,000,000 J."
    } else if image.id.contains("dropper-after") {
        "After two transfer passes: the idle first dropper still has a one-FE gap. The raw assertion is 99,999 / 100,000 FE; a rounded or full-looking gauge cannot resolve that gap."
    } else if image.id.contains("-green-") {
        "After the second transfer: the later consumer has 100,000 FE. In the final capture its actual hovered tooltip reads 100.0k/100.0k FE."
    } else {
        "After the second transfer: the later consumer has zero FE. In the final capture its actual hovered tooltip reads 0/100.0k FE."
    };
    format!("{version}. {historical}{description}")
}
