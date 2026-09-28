//! Typed source-projection inputs and the narrow Facet-to-Liquid bridge.
//!
//! Facet owns the application data model. Liquid only receives the scalar and
//! object values needed by the controlled source directives; there is no
//! serialization through JSON or Serde at this boundary.

use eyre::Result;
use eyre::eyre;
use facet::Facet;
use facet_value::Value as FacetValue;
use facet_value::ValueType;
use liquid::model::Value as LiquidValue;
use std::collections::BTreeMap;

/// Values available to a source projection. Absent feature and target keys
/// remain absent, rather than being silently treated as false.
#[derive(Debug, Facet)]
pub struct ProjectionContext {
    pub minecraft_version: String,
    pub preset: String,
    pub features: BTreeMap<String, bool>,
    pub targets: BTreeMap<String, bool>,
}

/// Reflect the typed context with Facet, then adapt its supported shapes to
/// Liquid. Unknown variables are not inserted; the directive validator must
/// reject unknown feature/target names before rendering a conditional.
///
/// # Errors
///
/// Returns an error if Facet cannot reflect the context or if reflection
/// produces a value other than strings, booleans, and nested objects.
pub fn to_liquid_object(context: &ProjectionContext) -> Result<liquid::Object> {
    let reflected = facet_value::to_value(context)
        .map_err(|error| eyre!("could not reflect projection context: {error}"))?;
    match convert_value(&reflected, "context")? {
        LiquidValue::Object(object) => Ok(object),
        _ => Err(eyre!("projection context must reflect to an object")),
    }
}

fn convert_value(value: &FacetValue, path: &str) -> Result<LiquidValue> {
    match value.value_type() {
        ValueType::Bool => Ok(LiquidValue::scalar(
            value.as_bool().expect("Facet reported a boolean"),
        )),
        ValueType::String => Ok(LiquidValue::scalar(
            value
                .as_string()
                .expect("Facet reported a string")
                .as_str()
                .to_owned(),
        )),
        ValueType::Object => {
            let mut object = liquid::Object::new();
            for (key, child) in value.as_object().expect("Facet reported an object") {
                let key = key.as_str();
                object.insert(
                    key.to_owned().into(),
                    convert_value(child, &format!("{path}.{key}"))?,
                );
            }
            Ok(LiquidValue::Object(object))
        }
        unsupported => Err(eyre!(
            "unsupported projection context shape at {path}: {unsupported:?}; only strings, booleans, and objects are allowed"
        )),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn fixture() -> ProjectionContext {
        ProjectionContext {
            minecraft_version: "1.19.2".to_owned(),
            preset: "released-4.34.0".to_owned(),
            features: BTreeMap::from([
                ("packet_computation".to_owned(), false),
                ("touch_display".to_owned(), true),
            ]),
            targets: BTreeMap::from([("mc_1_19_2".to_owned(), true), ("forge".to_owned(), true)]),
        }
    }

    #[test]
    fn facet_context_renders_typed_version_preset_and_flags() {
        let object = to_liquid_object(&fixture()).unwrap();
        let parser = liquid::ParserBuilder::with_stdlib().build().unwrap();
        let template = parser
            .parse("{{ minecraft_version }}|{{ preset }}|{% if features.touch_display %}touch{% endif %}{% unless features.packet_computation %} no-packet{% endunless %}|{% if targets.forge %}forge{% endif %}")
            .unwrap();

        assert_eq!(
            template.render(&object).unwrap(),
            "1.19.2|released-4.34.0|touch no-packet|forge"
        );
    }

    #[test]
    fn absent_flags_are_not_added_as_false_values() {
        let object = to_liquid_object(&fixture()).unwrap();
        let Some(LiquidValue::Object(features)) = object.get("features") else {
            panic!("features must remain an object");
        };
        assert!(features.contains_key("packet_computation"));
        assert!(!features.contains_key("unregistered_feature"));
    }

    #[test]
    fn unsupported_numeric_and_byte_shapes_fail_closed() {
        assert!(convert_value(&FacetValue::from(7_i64), "context.bad_number").is_err());
        assert!(convert_value(&FacetValue::from(vec![1_u8]), "context.bad_bytes").is_err());
    }
}
