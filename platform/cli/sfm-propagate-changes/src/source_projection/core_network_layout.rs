//! Pure acceptance rules for the three reviewed outer SFM wire layouts.
//!
//! Complete core project collection invokes this before reading selected inputs.
//! Isolated source selection/rendering deliberately remains a separate proof.
//! Feature support, prerequisites and wire codec semantics are separate gates.
//! Historical sources are evidence, not runtime inputs to this validator.
//!
//! Only new message families affect this identity. Handler-only direction/menu
//! fixes and execution-side validation are deliberately not represented here.

use super::context::ProjectionContext;
use eyre::Result;
use eyre::ensure;

pub const NETWORK_REGISTRATION_PATH: &str =
    "src/main/java/ca/teamdman/sfm/common/registry/registration/SFMPackets.java";
const FAMILY_KEYS: [&str; 5] = [
    "packet_transport_private",
    "client_inbox",
    "client_program_signing",
    "multiplayer_packets",
    "manager_operator_queries",
];

const SUPPORTED_TARGET_IDS: [&str; 10] = [
    "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
    "26.1.2",
];
const MAX_TARGET_ID_BYTES: usize = 16;

/// The five owners that add messages to the outer channel.
///
/// This bounded typed input cannot accidentally count a handler-only safety
/// fix, environment, projection key or preset as a wire-layout family.
#[derive(Clone, Copy, Debug, Default, Eq, PartialEq)]
#[expect(
    clippy::struct_excessive_bools,
    reason = "Fixed, independent feature bits, not an implicit state machine."
)]
pub struct NetworkMessageFamilies {
    pub packet_transport_private: bool,
    pub client_inbox: bool,
    pub client_program_signing: bool,
    pub multiplayer_packets: bool,
    pub manager_operator_queries: bool,
}

impl NetworkMessageFamilies {
    fn is_baseline(self) -> bool {
        !self.packet_transport_private
            && !self.client_inbox
            && !self.client_program_signing
            && !self.multiplayer_packets
            && !self.manager_operator_queries
    }

    fn has_complete_shared_transport(self) -> bool {
        self.packet_transport_private
            && self.client_inbox
            && self.client_program_signing
            && self.multiplayer_packets
    }
}

/// A frozen message-family layout that has a reviewed literal channel version.
///
/// This enum establishes registration-set identity only. It does not assert
/// that a later edit to a packet codec is compatible with historical peers.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum ReviewedNetworkLayout {
    /// The thirty-seven released messages, using each target's loader API.
    Baseline37,
    /// Baseline plus private transport, inbox, signing and multiplayer families.
    Complete46,
    /// Complete transport plus the 1.19.2 manager-show request/response pair.
    Complete48,
}

impl ReviewedNetworkLayout {
    /// Literal peer version associated with the reviewed registration layout.
    #[must_use]
    pub const fn protocol_version(self) -> &'static str {
        match self {
            Self::Baseline37 => "1.0.0",
            Self::Complete46 => "1.4.0",
            Self::Complete48 => "1.5.0",
        }
    }

    /// Number of registered messages in the corresponding frozen layout.
    #[must_use]
    pub const fn message_count(self) -> u8 {
        match self {
            Self::Baseline37 => 37,
            Self::Complete46 => 46,
            Self::Complete48 => 48,
        }
    }
}

/// Resolve the complete-project wire identity from explicit feature booleans.
///
/// Old or synthetic registries without any family name describe the baseline.
/// Once any family is registered, every family must be explicitly resolved;
/// omitted companion names cannot silently mean false.
///
/// # Errors
///
/// Rejects incomplete feature resolution and every unreviewed wire layout.
pub fn validate_network_context(
    target_id: &str,
    context: &ProjectionContext,
) -> Result<ReviewedNetworkLayout> {
    if !FAMILY_KEYS
        .iter()
        .any(|name| context.features.contains_key(*name))
    {
        return validate_network_layout(target_id, NetworkMessageFamilies::default());
    }
    let resolve = |name: &str| {
        context.features.get(name).copied().ok_or_else(|| {
            eyre::eyre!("complete project network context must explicitly resolve `{name}`")
        })
    };
    validate_network_layout(
        target_id,
        NetworkMessageFamilies {
            packet_transport_private: resolve("packet_transport_private")?,
            client_inbox: resolve("client_inbox")?,
            client_program_signing: resolve("client_program_signing")?,
            multiplayer_packets: resolve("multiplayer_packets")?,
            manager_operator_queries: resolve("manager_operator_queries")?,
        },
    )
}

/// Refuse unreviewed partial new-message layouts before source generation.
///
/// The baseline is supported on all ten stable target IDs. The complete
/// four-family transport is supported only on 1.19.2 and 1.19.4. Manager-show
/// messages are optional only for that complete 1.19.2 layout. The two D2
/// witnesses share the forty-six-message layout and added packet bodies; adding
/// the query pair gives the frozen forty-eight-message 1.19.2 layout.
///
/// A stable target ID is required: target `1.21.0` is distinct from the
/// effective Minecraft version string `1.21` used by Liquid.
///
/// # Errors
///
/// Rejects unknown or oversized target IDs, new families on unwitnessed
/// targets, every partial four-family transport, and queries without complete
/// transport or on any target other than 1.19.2. No fallback, source lookup,
/// environment-based selection or closest historical layout is used.
pub fn validate_network_layout(
    target_id: &str,
    families: NetworkMessageFamilies,
) -> Result<ReviewedNetworkLayout> {
    ensure!(
        target_id.len() <= MAX_TARGET_ID_BYTES,
        "network layout target ID exceeds its bounded length"
    );
    ensure!(
        SUPPORTED_TARGET_IDS.contains(&target_id),
        "network layout target ID is not one of the ten supported targets"
    );
    if families.is_baseline() {
        return Ok(ReviewedNetworkLayout::Baseline37);
    }
    ensure!(
        matches!(target_id, "1.19.2" | "1.19.4"),
        "new network message families are supported only on 1.19.2 and 1.19.4"
    );
    ensure!(
        families.has_complete_shared_transport(),
        "unreviewed partial network layout: private transport, inbox, signing and multiplayer must all be enabled together until a distinct wire-layout fingerprint is reviewed"
    );
    if families.manager_operator_queries {
        ensure!(
            target_id == "1.19.2",
            "manager-show wire messages are supported only on complete 1.19.2 transport"
        );
        Ok(ReviewedNetworkLayout::Complete48)
    } else {
        Ok(ReviewedNetworkLayout::Complete46)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::collections::BTreeMap;

    fn context(features: BTreeMap<String, bool>) -> ProjectionContext {
        ProjectionContext {
            minecraft_version: "1.19.2".to_owned(),
            preset: "arbitrary/nested".to_owned(),
            environment: "dev".to_owned(),
            projection_key: "arbitrary/nested".to_owned(),
            features,
            targets: BTreeMap::new(),
        }
    }

    #[test]
    fn context_without_registered_family_keys_means_only_baseline() -> Result<()> {
        let legacy = context(BTreeMap::from([("unrelated".to_owned(), true)]));
        assert_eq!(
            validate_network_context("1.19.2", &legacy)?,
            ReviewedNetworkLayout::Baseline37
        );
        let resolved = context(
            FAMILY_KEYS
                .into_iter()
                .map(|key| (key.to_owned(), false))
                .collect(),
        );
        assert_eq!(
            validate_network_context("1.19.2", &resolved)?,
            ReviewedNetworkLayout::Baseline37
        );
        Ok(())
    }

    #[test]
    fn context_with_any_family_name_cannot_omit_a_companion_even_when_false() {
        for present in FAMILY_KEYS {
            let partial = context(BTreeMap::from([(present.to_owned(), false)]));
            assert!(validate_network_context("1.19.2", &partial).is_err());
        }
        for removed in FAMILY_KEYS {
            let partial = context(
                FAMILY_KEYS
                    .into_iter()
                    .filter(|name| *name != removed)
                    .map(|key| (key.to_owned(), false))
                    .collect(),
            );
            assert!(validate_network_context("1.19.2", &partial).is_err());
        }
    }

    fn families_from_mask(mask: u8) -> NetworkMessageFamilies {
        NetworkMessageFamilies {
            packet_transport_private: mask & 1 != 0,
            client_inbox: mask & 2 != 0,
            client_program_signing: mask & 4 != 0,
            multiplayer_packets: mask & 8 != 0,
            manager_operator_queries: mask & 16 != 0,
        }
    }

    #[test]
    fn exhaustive_ten_target_family_matrix_has_only_thirteen_reviewed_cases() -> Result<()> {
        let mut accepted = 0;
        for target in SUPPORTED_TARGET_IDS {
            for mask in 0..32 {
                let expected = if mask == 0 {
                    Some(ReviewedNetworkLayout::Baseline37)
                } else if matches!(target, "1.19.2" | "1.19.4") && mask == 15 {
                    Some(ReviewedNetworkLayout::Complete46)
                } else if target == "1.19.2" && mask == 31 {
                    Some(ReviewedNetworkLayout::Complete48)
                } else {
                    None
                };
                let actual = validate_network_layout(target, families_from_mask(mask));
                if let Some(layout) = expected {
                    assert_eq!(actual?, layout, "target={target}, mask={mask}");
                    accepted += 1;
                } else {
                    assert!(actual.is_err(), "accepted target={target}, mask={mask}");
                }
            }
        }
        assert_eq!(accepted, 13);
        Ok(())
    }

    #[test]
    fn baseline_uses_literal_release_version_on_every_loader_target() -> Result<()> {
        for target in SUPPORTED_TARGET_IDS {
            let layout = validate_network_layout(target, NetworkMessageFamilies::default())?;
            assert_eq!(layout.protocol_version(), "1.0.0");
            assert_eq!(layout.message_count(), 37);
        }
        Ok(())
    }

    #[test]
    fn complete_shared_transport_and_query_pair_keep_frozen_versions() -> Result<()> {
        for target in ["1.19.2", "1.19.4"] {
            let layout = validate_network_layout(target, families_from_mask(15))?;
            assert_eq!(layout.protocol_version(), "1.4.0");
            assert_eq!(layout.message_count(), 46);
        }
        let layout = validate_network_layout("1.19.2", families_from_mask(31))?;
        assert_eq!(layout.protocol_version(), "1.5.0");
        assert_eq!(layout.message_count(), 48);
        Ok(())
    }

    #[test]
    fn removing_any_shared_family_does_not_keep_a_full_layout_identity() {
        for removed in [1, 2, 4, 8] {
            for query in [0, 16] {
                assert!(
                    validate_network_layout("1.19.2", families_from_mask((15 ^ removed) | query))
                        .is_err()
                );
            }
        }
        assert!(validate_network_layout("1.19.2", families_from_mask(16)).is_err());
        assert!(validate_network_layout("1.19.4", families_from_mask(31)).is_err());
    }

    #[test]
    fn input_never_selects_nearest_target_or_accepts_an_effective_version_alias() {
        for target in ["", "1.21", "1.19.3", "1.19.2/dev", "unknown"] {
            assert!(
                validate_network_layout(target, NetworkMessageFamilies::default()).is_err(),
                "accepted unknown target: {target}"
            );
        }
        assert!(
            validate_network_layout(
                &"a".repeat(MAX_TARGET_ID_BYTES + 1),
                NetworkMessageFamilies::default()
            )
            .is_err()
        );
    }

    #[test]
    fn unrelated_handler_fix_toggles_do_not_change_the_typed_wire_identity() -> Result<()> {
        // These caller-owned policy booleans intentionally have no field in
        // NetworkMessageFamilies. They may change validation, not registration.
        for _direction_validation in [false, true] {
            for _menu_validation in [false, true] {
                for _execution_side in [false, true] {
                    assert_eq!(
                        validate_network_layout("1.19.2", families_from_mask(15))?,
                        ReviewedNetworkLayout::Complete46
                    );
                }
            }
        }
        Ok(())
    }
}
