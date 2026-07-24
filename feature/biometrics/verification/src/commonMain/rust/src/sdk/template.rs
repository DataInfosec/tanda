//! Versioned, bounded fingerprint template storage.
//!
//! A [`TemplateStore`] is matcher input, not a synchronized gallery snapshot.
//! The binary representation is suitable for one-student enrollment artifacts
//! stored in libSQL and PostgreSQL. It contains extracted templates only: raw
//! sensor captures, gallery identity, synchronization cursors, and derived
//! indexes are deliberately absent.

use std::collections::{BTreeMap, HashSet};
use std::io::{Cursor, Read, Write};

use super::error::{SdkError, SdkResult};
use super::extractor::{ExtractedTemplate, ExtractorConfig, FingerRecord, TemplateFeature};
use super::limits::SdkLimits;

const TEMPLATE_MAGIC: &[u8; 8] = b"BMSTPL\0\0";

/// Stable format identifier stored beside template payloads in libSQL.
pub const TEMPLATE_FORMAT_VERSION: &str = "tanda-fingerprint-template-v1";

/// Stable name for the current default fingerprint extraction profile.
pub const DEFAULT_EXTRACTOR_PROFILE: &str = "tanda-fingerprint-default-v1";

/// Ordered extracted templates keyed by SDK finger record identifier.
#[derive(Debug, Clone, PartialEq)]
pub struct TemplateStore {
    pub(crate) templates: BTreeMap<String, ExtractedTemplate>,
}

impl TemplateStore {
    /// Create an empty template store.
    pub fn new() -> Self {
        Self {
            templates: BTreeMap::new(),
        }
    }

    /// Construct a store while rejecting duplicate record identifiers.
    pub fn from_templates(templates: Vec<ExtractedTemplate>) -> SdkResult<Self> {
        let mut store = Self::new();
        for template in templates {
            if store.templates.contains_key(&template.record.record_id) {
                return Err(SdkError::conflict(format!(
                    "duplicate template record id: {}",
                    template.record.record_id
                )));
            }
            store.upsert(template)?;
        }
        Ok(store)
    }

    /// Insert or replace a record without allowing ownership reassignment.
    pub fn upsert(&mut self, template: ExtractedTemplate) -> SdkResult<()> {
        if let Some(existing) = self.templates.get(&template.record.record_id)
            && existing.record.user_id != template.record.user_id
        {
            return Err(SdkError::conflict(format!(
                "record {} already belongs to another user",
                template.record.record_id
            )));
        }
        self.templates
            .insert(template.record.record_id.clone(), template);
        Ok(())
    }

    /// Remove one finger record.
    pub fn remove_record(&mut self, record_id: &str) -> bool {
        self.templates.remove(record_id).is_some()
    }

    /// Remove every finger record owned by one user.
    pub fn remove_user(&mut self, user_id: &str) -> usize {
        let before = self.templates.len();
        self.templates
            .retain(|_, template| template.record.user_id != user_id);
        before - self.templates.len()
    }

    /// Return templates in stable record-id order.
    pub fn templates(&self) -> Vec<ExtractedTemplate> {
        self.templates.values().cloned().collect()
    }

    /// Return templates owned by one user in stable record-id order.
    pub fn templates_for_user(&self, user_id: &str) -> Vec<ExtractedTemplate> {
        self.templates
            .values()
            .filter(|template| template.record.user_id == user_id)
            .cloned()
            .collect()
    }

    /// Number of finger records.
    pub fn len(&self) -> usize {
        self.templates.len()
    }

    /// Whether the store contains no records.
    pub fn is_empty(&self) -> bool {
        self.templates.is_empty()
    }

    /// Number of distinct application users.
    pub fn user_count(&self) -> usize {
        self.templates
            .values()
            .map(|template| template.record.user_id.as_str())
            .collect::<HashSet<_>>()
            .len()
    }

    /// Return the sole user identifier in a one-student artifact.
    pub fn single_user_id(&self) -> SdkResult<&str> {
        let mut users = self
            .templates
            .values()
            .map(|template| template.record.user_id.as_str());
        let user_id = users
            .next()
            .ok_or_else(|| SdkError::invalid_input("template artifact cannot be empty"))?;
        if users.any(|candidate| candidate != user_id) {
            return Err(SdkError::conflict(
                "template artifact contains more than one user",
            ));
        }
        Ok(user_id)
    }

    /// Encode with the current default extraction profile and limits.
    pub fn to_bytes(&self) -> SdkResult<Vec<u8>> {
        self.to_bytes_with_config(ExtractorConfig::default(), SdkLimits::default())
    }

    /// Encode with an explicit extraction profile and resource limits.
    pub fn to_bytes_with_config(
        &self,
        extractor: ExtractorConfig,
        limits: SdkLimits,
    ) -> SdkResult<Vec<u8>> {
        encode_templates(&self.templates(), extractor, limits)
    }

    /// Decode with the current default extraction profile and limits.
    pub fn from_bytes(bytes: &[u8]) -> SdkResult<Self> {
        Self::from_bytes_with_config(bytes, ExtractorConfig::default(), SdkLimits::default())
    }

    /// Decode with an explicit extraction profile and resource limits.
    pub fn from_bytes_with_config(
        bytes: &[u8],
        extractor: ExtractorConfig,
        limits: SdkLimits,
    ) -> SdkResult<Self> {
        decode_template_store(bytes, extractor, limits)
    }
}

impl Default for TemplateStore {
    fn default() -> Self {
        Self::new()
    }
}

fn encode_templates(
    templates: &[ExtractedTemplate],
    extractor: ExtractorConfig,
    limits: SdkLimits,
) -> SdkResult<Vec<u8>> {
    if templates.len() > limits.max_records {
        return Err(SdkError::resource_limit(
            "template record count exceeds limit",
        ));
    }
    let mut bytes = Vec::new();
    bytes.write_all(TEMPLATE_MAGIC)?;
    write_u32(&mut bytes, checked_u32(templates.len(), "template count")?)?;
    for template in templates {
        template.validate(extractor, limits)?;
        write_string(&mut bytes, &template.record.record_id, limits)?;
        write_string(&mut bytes, &template.record.user_id, limits)?;
        bytes.write_all(&[template.quality])?;
        write_u16(&mut bytes, template.token_count)?;
        write_u16(
            &mut bytes,
            checked_u16(template.tokens.len(), "template token count")?,
        )?;
        for token in &template.tokens {
            write_u64(&mut bytes, *token)?;
        }
        write_u16(
            &mut bytes,
            checked_u16(template.features.len(), "template feature count")?,
        )?;
        for feature in &template.features {
            bytes.write_all(&[
                feature.x,
                feature.y,
                feature.orientation,
                feature.contrast,
                feature.coherence,
                feature.kind,
            ])?;
        }
        if bytes.len() > limits.max_template_bytes {
            return Err(SdkError::resource_limit(
                "encoded template section exceeds max_template_bytes",
            ));
        }
    }
    Ok(bytes)
}

fn decode_template_store(
    bytes: &[u8],
    extractor: ExtractorConfig,
    limits: SdkLimits,
) -> SdkResult<TemplateStore> {
    if bytes.len() > limits.max_template_bytes {
        return Err(SdkError::resource_limit(
            "template section exceeds max_template_bytes",
        ));
    }
    let mut cursor = Cursor::new(bytes);
    let mut magic = [0; 8];
    cursor.read_exact(&mut magic)?;
    if &magic != TEMPLATE_MAGIC {
        return Err(SdkError::invalid_format("invalid template store magic"));
    }
    let count = read_u32(&mut cursor)? as usize;
    if count > limits.max_records {
        return Err(SdkError::resource_limit(
            "template record count exceeds limit",
        ));
    }
    let mut templates = reserved_vec(count, "templates")?;
    for _ in 0..count {
        let record_id = read_string(&mut cursor, limits)?;
        let user_id = read_string(&mut cursor, limits)?;
        let mut quality = [0];
        cursor.read_exact(&mut quality)?;
        let token_count = read_u16(&mut cursor)?;
        let token_len = usize::from(read_u16(&mut cursor)?);
        if token_len > limits.max_tokens_per_template {
            return Err(SdkError::resource_limit(
                "template token count exceeds limit",
            ));
        }
        let mut tokens = reserved_vec(token_len, "template tokens")?;
        for _ in 0..token_len {
            tokens.push(read_u64(&mut cursor)?);
        }
        let feature_len = usize::from(read_u16(&mut cursor)?);
        if feature_len > limits.max_features_per_template {
            return Err(SdkError::resource_limit(
                "template feature count exceeds limit",
            ));
        }
        let mut features = reserved_vec(feature_len, "template features")?;
        for _ in 0..feature_len {
            let mut payload = [0; 6];
            cursor.read_exact(&mut payload)?;
            features.push(TemplateFeature {
                x: payload[0],
                y: payload[1],
                orientation: payload[2],
                contrast: payload[3],
                coherence: payload[4],
                kind: payload[5],
            });
        }
        let template = ExtractedTemplate {
            record: FingerRecord { record_id, user_id },
            quality: quality[0],
            token_count,
            tokens,
            features,
        };
        template.validate(extractor, limits)?;
        templates.push(template);
    }
    ensure_eof(&mut cursor, "template store")?;
    TemplateStore::from_templates(templates)
}

fn write_string(mut writer: impl Write, value: &str, limits: SdkLimits) -> SdkResult<()> {
    if value.len() > limits.max_string_bytes {
        return Err(SdkError::resource_limit(
            "encoded string exceeds configured limit",
        ));
    }
    write_u16(&mut writer, checked_u16(value.len(), "encoded string")?)?;
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
    String::from_utf8(bytes).map_err(|_| SdkError::invalid_format("encoded string is not UTF-8"))
}

fn ensure_eof(mut reader: impl Read, label: &str) -> SdkResult<()> {
    let mut extra = [0; 1];
    if reader.read(&mut extra)? != 0 {
        return Err(SdkError::invalid_format(format!(
            "{label} has trailing bytes"
        )));
    }
    Ok(())
}

fn reserved_vec<T>(capacity: usize, label: &str) -> SdkResult<Vec<T>> {
    let mut values = Vec::new();
    values
        .try_reserve_exact(capacity)
        .map_err(|_| SdkError::resource_limit(format!("cannot reserve {label}")))?;
    Ok(values)
}

fn checked_u16(value: usize, label: &str) -> SdkResult<u16> {
    u16::try_from(value).map_err(|_| SdkError::resource_limit(format!("{label} exceeds u16")))
}

fn checked_u32(value: usize, label: &str) -> SdkResult<u32> {
    u32::try_from(value).map_err(|_| SdkError::resource_limit(format!("{label} exceeds u32")))
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

    fn template(record_id: &str, user_id: &str) -> ExtractedTemplate {
        ExtractedTemplate {
            record: FingerRecord {
                record_id: record_id.to_owned(),
                user_id: user_id.to_owned(),
            },
            quality: 80,
            token_count: 2,
            tokens: vec![1, 2],
            features: vec![],
        }
    }

    #[test]
    fn one_student_artifact_round_trips() {
        let store = TemplateStore::from_templates(vec![
            template("record-1", "student-1"),
            template("record-2", "student-1"),
        ])
        .unwrap();
        let decoded = TemplateStore::from_bytes(&store.to_bytes().unwrap()).unwrap();
        assert_eq!(decoded, store);
        assert_eq!(decoded.single_user_id().unwrap(), "student-1");
    }

    #[test]
    fn one_student_artifact_rejects_mixed_ownership() {
        let store = TemplateStore::from_templates(vec![
            template("record-1", "student-1"),
            template("record-2", "student-2"),
        ])
        .unwrap();
        assert_eq!(
            store.single_user_id().unwrap_err().code(),
            super::super::error::SdkErrorCode::Conflict
        );
    }
}
