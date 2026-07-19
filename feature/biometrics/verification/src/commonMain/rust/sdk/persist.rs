//! Bounded binary persistence for the hot candidate index.

use std::io::{Cursor, Read, Write};

use super::error::{SdkError, SdkResult};
use super::extractor::TemplateFeature;
use super::index::{BiometricIndex, IndexRecord, RecordFeatures, TokenPosting};
use super::limits::SdkLimits;

const MAGIC: &[u8; 8] = b"BMSIDX\0\0";

pub(crate) fn encode_index(index: &BiometricIndex) -> SdkResult<Vec<u8>> {
    let mut bytes = Vec::new();
    write_index(index, &mut bytes)?;
    Ok(bytes)
}

pub(crate) fn decode_index(bytes: &[u8], limits: SdkLimits) -> SdkResult<BiometricIndex> {
    if bytes.len() > limits.max_index_bytes {
        return Err(SdkError::resource_limit(
            "encoded index exceeds max_index_bytes",
        ));
    }
    read_index(Cursor::new(bytes), limits)
}

pub(crate) fn write_index(index: &BiometricIndex, mut writer: impl Write) -> SdkResult<()> {
    let limits = SdkLimits::default();
    index.validate(limits)?;
    writer.write_all(MAGIC)?;
    writer.write_all(&[index.orientation_bins])?;
    write_count(&mut writer, index.records.len(), "index record count")?;
    write_count(
        &mut writer,
        index.record_features.len(),
        "index feature-set count",
    )?;
    write_count(
        &mut writer,
        index.dictionary.len(),
        "index dictionary count",
    )?;
    write_count(&mut writer, index.postings.len(), "index posting count")?;
    for record in &index.records {
        write_string(&mut writer, &record.record_id, limits)?;
        write_string(&mut writer, &record.user_id, limits)?;
        write_u16(&mut writer, record.token_count)?;
        writer.write_all(&[record.quality])?;
    }
    for record_features in &index.record_features {
        write_count(
            &mut writer,
            record_features.features.len(),
            "record feature count",
        )?;
        for feature in &record_features.features {
            writer.write_all(&[
                feature.x,
                feature.y,
                feature.orientation,
                feature.contrast,
                feature.coherence,
                feature.kind,
            ])?;
        }
    }
    for posting in &index.dictionary {
        write_u64(&mut writer, posting.token)?;
        write_u32(&mut writer, posting.start)?;
        write_u32(&mut writer, posting.len)?;
        write_u32(&mut writer, posting.user_count)?;
    }
    for posting in &index.postings {
        write_u32(&mut writer, *posting)?;
    }
    Ok(())
}

pub(crate) fn read_index(mut reader: impl Read, limits: SdkLimits) -> SdkResult<BiometricIndex> {
    let mut magic = [0; 8];
    reader.read_exact(&mut magic)?;
    if &magic != MAGIC {
        return Err(SdkError::invalid_format("invalid biometric index magic"));
    }
    let mut orientation_bins = [0];
    reader.read_exact(&mut orientation_bins)?;
    if !(8..=32).contains(&orientation_bins[0]) || !orientation_bins[0].is_multiple_of(2) {
        return Err(SdkError::integrity("index orientation bins are invalid"));
    }
    let record_count = read_count(&mut reader)?;
    let feature_set_count = read_count(&mut reader)?;
    let dictionary_count = read_count(&mut reader)?;
    let postings_count = read_count(&mut reader)?;
    if record_count > limits.max_records {
        return Err(SdkError::resource_limit("index record count exceeds limit"));
    }
    if feature_set_count != record_count {
        return Err(SdkError::integrity(
            "index record and feature-set counts differ",
        ));
    }
    let max_postings = limits
        .max_records
        .checked_mul(limits.max_tokens_per_template)
        .ok_or_else(|| SdkError::resource_limit("index posting limit overflows usize"))?;
    if dictionary_count > max_postings || postings_count > max_postings {
        return Err(SdkError::resource_limit(
            "index dictionary or postings exceed configured limits",
        ));
    }

    let mut records = reserved_vec(record_count, "index records")?;
    for _ in 0..record_count {
        let record_id = read_string(&mut reader, limits)?;
        let user_id = read_string(&mut reader, limits)?;
        let token_count = read_u16(&mut reader)?;
        let mut quality = [0];
        reader.read_exact(&mut quality)?;
        records.push(IndexRecord {
            record_id,
            user_id,
            token_count,
            quality: quality[0],
        });
    }

    let mut record_features = reserved_vec(feature_set_count, "index feature sets")?;
    for _ in 0..feature_set_count {
        let feature_count = read_count(&mut reader)?;
        if feature_count > limits.max_features_per_template {
            return Err(SdkError::resource_limit(
                "record feature count exceeds configured limit",
            ));
        }
        let mut features = reserved_vec(feature_count, "record features")?;
        for _ in 0..feature_count {
            let mut bytes = [0; 6];
            reader.read_exact(&mut bytes)?;
            features.push(TemplateFeature {
                x: bytes[0],
                y: bytes[1],
                orientation: bytes[2],
                contrast: bytes[3],
                coherence: bytes[4],
                kind: bytes[5],
            });
        }
        record_features.push(RecordFeatures::new(features, orientation_bins[0]));
    }

    let total_users = records
        .iter()
        .map(|record| record.user_id.as_str())
        .collect::<std::collections::HashSet<_>>()
        .len()
        .max(1) as f32;
    let mut dictionary = reserved_vec(dictionary_count, "index dictionary")?;
    for _ in 0..dictionary_count {
        let token = read_u64(&mut reader)?;
        let start = read_u32(&mut reader)?;
        let len = read_u32(&mut reader)?;
        let user_count = read_u32(&mut reader)?;
        dictionary.push(TokenPosting {
            token,
            start,
            len,
            user_count,
            idf: ((total_users + 1.0) / (user_count as f32 + 0.5)).ln(),
        });
    }
    let mut postings = reserved_vec(postings_count, "index postings")?;
    for _ in 0..postings_count {
        postings.push(read_u32(&mut reader)?);
    }
    ensure_eof(&mut reader)?;
    let index = BiometricIndex {
        orientation_bins: orientation_bins[0],
        records,
        record_features,
        dictionary,
        postings,
    };
    index.validate(limits)?;
    Ok(index)
}

fn reserved_vec<T>(capacity: usize, label: &str) -> SdkResult<Vec<T>> {
    let mut values = Vec::new();
    values
        .try_reserve_exact(capacity)
        .map_err(|_| SdkError::resource_limit(format!("cannot reserve {label}")))?;
    Ok(values)
}

fn write_string(mut writer: impl Write, value: &str, limits: SdkLimits) -> SdkResult<()> {
    if value.len() > limits.max_string_bytes {
        return Err(SdkError::resource_limit(
            "encoded string exceeds configured limit",
        ));
    }
    let len = u16::try_from(value.len())
        .map_err(|_| SdkError::resource_limit("encoded string exceeds u16"))?;
    write_u16(&mut writer, len)?;
    writer.write_all(value.as_bytes())?;
    Ok(())
}

fn read_string(mut reader: impl Read, limits: SdkLimits) -> SdkResult<String> {
    let len = usize::from(read_u16(&mut reader)?);
    if len > limits.max_string_bytes {
        return Err(SdkError::resource_limit(
            "encoded string exceeds configured limit",
        ));
    }
    let mut bytes = reserved_vec(len, "encoded string")?;
    bytes.resize(len, 0);
    reader.read_exact(&mut bytes)?;
    String::from_utf8(bytes).map_err(|_| SdkError::invalid_format("index string is not UTF-8"))
}

fn write_count(writer: impl Write, count: usize, label: &str) -> SdkResult<()> {
    let count = u32::try_from(count)
        .map_err(|_| SdkError::resource_limit(format!("{label} exceeds u32")))?;
    write_u32(writer, count)
}

fn read_count(reader: impl Read) -> SdkResult<usize> {
    Ok(read_u32(reader)? as usize)
}

fn ensure_eof(mut reader: impl Read) -> SdkResult<()> {
    let mut extra = [0; 1];
    if reader.read(&mut extra)? != 0 {
        return Err(SdkError::invalid_format(
            "biometric index has trailing bytes",
        ));
    }
    Ok(())
}

fn write_u16(mut writer: impl Write, value: u16) -> SdkResult<()> {
    writer.write_all(&value.to_le_bytes())?;
    Ok(())
}

fn write_u32(mut writer: impl Write, value: u32) -> SdkResult<()> {
    writer.write_all(&value.to_le_bytes())?;
    Ok(())
}

fn write_u64(mut writer: impl Write, value: u64) -> SdkResult<()> {
    writer.write_all(&value.to_le_bytes())?;
    Ok(())
}

fn read_u16(mut reader: impl Read) -> SdkResult<u16> {
    let mut bytes = [0; 2];
    reader.read_exact(&mut bytes)?;
    Ok(u16::from_le_bytes(bytes))
}

fn read_u32(mut reader: impl Read) -> SdkResult<u32> {
    let mut bytes = [0; 4];
    reader.read_exact(&mut bytes)?;
    Ok(u32::from_le_bytes(bytes))
}

fn read_u64(mut reader: impl Read) -> SdkResult<u64> {
    let mut bytes = [0; 8];
    reader.read_exact(&mut bytes)?;
    Ok(u64::from_le_bytes(bytes))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn rejects_out_of_range_posting_before_search() {
        let mut bytes = Vec::new();
        bytes.extend_from_slice(MAGIC);
        bytes.push(16);
        for count in [1u32, 1, 1, 1] {
            bytes.extend_from_slice(&count.to_le_bytes());
        }
        write_string(&mut bytes, "r", SdkLimits::default()).unwrap();
        write_string(&mut bytes, "u", SdkLimits::default()).unwrap();
        bytes.extend_from_slice(&1u16.to_le_bytes());
        bytes.push(90);
        bytes.extend_from_slice(&0u32.to_le_bytes());
        bytes.extend_from_slice(&7u64.to_le_bytes());
        bytes.extend_from_slice(&1u32.to_le_bytes());
        bytes.extend_from_slice(&1u32.to_le_bytes());
        bytes.extend_from_slice(&1u32.to_le_bytes());
        bytes.extend_from_slice(&0u32.to_le_bytes());
        let error = decode_index(&bytes, SdkLimits::default()).unwrap_err();
        assert_eq!(error.code(), super::super::error::SdkErrorCode::Integrity);
    }

    #[test]
    fn rejects_counts_before_large_allocation() {
        let mut bytes = Vec::new();
        bytes.extend_from_slice(MAGIC);
        bytes.push(16);
        bytes.extend_from_slice(&u32::MAX.to_le_bytes());
        bytes.extend_from_slice(&u32::MAX.to_le_bytes());
        bytes.extend_from_slice(&0u32.to_le_bytes());
        bytes.extend_from_slice(&0u32.to_le_bytes());
        let error = decode_index(&bytes, SdkLimits::default()).unwrap_err();
        assert_eq!(
            error.code(),
            super::super::error::SdkErrorCode::ResourceLimit
        );
    }

    #[test]
    fn rejects_invalid_feature_orientation_without_panicking() {
        let mut bytes = Vec::new();
        bytes.extend_from_slice(MAGIC);
        bytes.push(16);
        for count in [1u32, 1, 0, 0] {
            bytes.extend_from_slice(&count.to_le_bytes());
        }
        write_string(&mut bytes, "r", SdkLimits::default()).unwrap();
        write_string(&mut bytes, "u", SdkLimits::default()).unwrap();
        bytes.extend_from_slice(&0u16.to_le_bytes());
        bytes.push(90);
        bytes.extend_from_slice(&1u32.to_le_bytes());
        bytes.extend_from_slice(&[1, 1, u8::MAX, 1, 50, 1]);

        let error = decode_index(&bytes, SdkLimits::default()).unwrap_err();
        assert_eq!(error.code(), super::super::error::SdkErrorCode::Integrity);
    }
}
