fn crc32(bytes: &[u8]) -> u32 {
    let mut crc = !0_u32;
    for byte in bytes {
        crc ^= u32::from(*byte);
        for _ in 0..8 {
            crc = if crc & 1 != 0 {
                (crc >> 1) ^ 0xedb8_8320
            } else {
                crc >> 1
            };
        }
    }
    !crc
}
pub(super) fn verify(bytes: &[u8]) -> eyre::Result<()> {
    eyre::ensure!(
        bytes.starts_with(b"\x89PNG\r\n\x1a\n") && bytes.len() <= 16 * 1024 * 1024,
        "invalid PNG signature/byte budget"
    );
    let mut offset = 8;
    let mut header = false;
    let mut data = false;
    let mut end = false;
    while offset + 12 <= bytes.len() {
        let size = usize::try_from(u32::from_be_bytes(bytes[offset..offset + 4].try_into()?))?;
        let next = offset
            .checked_add(size)
            .and_then(|n| n.checked_add(12))
            .ok_or_else(|| eyre::eyre!("PNG length overflow"))?;
        eyre::ensure!(next <= bytes.len(), "truncated PNG chunk");
        let kind = &bytes[offset + 4..offset + 8];
        let payload = &bytes[offset + 8..next - 4];
        eyre::ensure!(
            crc32(&bytes[offset + 4..next - 4])
                == u32::from_be_bytes(bytes[next - 4..next].try_into()?),
            "PNG CRC mismatch"
        );
        match kind {
            b"IHDR" => {
                eyre::ensure!(!header && offset == 8 && size == 13, "invalid PNG header");
                let w = u32::from_be_bytes(payload[..4].try_into()?);
                let h = u32::from_be_bytes(payload[4..8].try_into()?);
                eyre::ensure!(
                    (1..=4096).contains(&w)
                        && (1..=4096).contains(&h)
                        && payload[8] == 8
                        && matches!(payload[9], 2 | 6)
                        && payload[10..] == [0, 0, 0],
                    "unsupported PNG dimensions/encoding"
                );
                header = true;
            }
            b"IDAT" => {
                eyre::ensure!(header && !end, "PNG image data order");
                data = true;
            }
            b"IEND" => {
                eyre::ensure!(
                    header && data && size == 0 && next == bytes.len(),
                    "invalid PNG end"
                );
                end = true;
            }
            _ => eyre::ensure!(!end && header, "PNG chunk order"),
        }
        offset = next;
    }
    eyre::ensure!(end && offset == bytes.len(), "PNG lacks complete image/end");
    Ok(())
}
