use color_eyre::owo_colors::Rgb;

#[must_use]
#[expect(
    clippy::cast_possible_truncation,
    reason = "Stable color hues are bounded to 0..=360 before converting to f32."
)]
pub fn stable_hue(input: &str) -> f32 {
    let hash = blake3::hash(input.as_bytes());
    let Some(seed_bytes) = hash
        .as_bytes()
        .get(..4)
        .and_then(|bytes| <[u8; 4]>::try_from(bytes).ok())
    else {
        return 0.0;
    };
    let hue_seed = u32::from_le_bytes(seed_bytes);

    (f64::from(hue_seed) / f64::from(u32::MAX) * 360.0) as f32
}

#[must_use]
pub fn stable_color(input: &str) -> Rgb {
    hsv_to_rgb(stable_hue(input), 0.72, 0.95)
}

#[must_use]
#[expect(clippy::many_single_char_names)]
pub fn hsv_to_rgb(hue: f32, saturation: f32, value: f32) -> Rgb {
    let hue = hue.rem_euclid(360.0);
    let c = value * saturation;
    let x = c * (1.0 - ((hue / 60.0) % 2.0 - 1.0).abs());
    let m = value - c;

    let (r, g, b) = match hue {
        h if h < 60.0 => (c, x, 0.0),
        h if h < 120.0 => (x, c, 0.0),
        h if h < 180.0 => (0.0, c, x),
        h if h < 240.0 => (0.0, x, c),
        h if h < 300.0 => (x, 0.0, c),
        _ => (c, 0.0, x),
    };

    Rgb(
        unit_channel_to_u8(r + m),
        unit_channel_to_u8(g + m),
        unit_channel_to_u8(b + m),
    )
}

#[expect(
    clippy::cast_possible_truncation,
    reason = "The channel is clamped to the inclusive 0..=255 byte range before conversion."
)]
#[expect(
    clippy::cast_sign_loss,
    reason = "The channel is clamped to a non-negative range before conversion."
)]
fn unit_channel_to_u8(channel: f32) -> u8 {
    (channel.clamp(0.0, 1.0) * 255.0).round() as u8
}

#[cfg(test)]
mod tests {
    use super::stable_color;
    use std::collections::BTreeMap;

    #[test]
    fn minecraft_versions_have_distinct_stable_colors() {
        let versions = [
            "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
            "26.1.2",
        ];

        let mut colors = BTreeMap::new();
        for version in versions {
            let color = stable_color(version);
            let color = (color.0, color.1, color.2);

            if let Some(previous_version) = colors.insert(color, version) {
                panic!(
                    "Minecraft versions {previous_version} and {version} both resolve to RGB {color:?}"
                );
            }
        }
    }
}
