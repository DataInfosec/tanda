//! Cross-repository smoke client for the Tanda attendance compatibility suite.

use std::env;
use std::error::Error;

use biometric_sdk::sdk::{
    AttendanceBiometricSdk, AttendanceConfig, AttendanceProvisioning, EnrollmentConfig,
    SubjectEnrollmentAuthorization,
};

fn main() -> Result<(), Box<dyn Error>> {
    let arguments = env::args().skip(1).collect::<Vec<_>>();
    if arguments.len() != 5 && arguments.len() != 9 {
        return Err(
            "usage: attendance-smoke <storage> <device-instance> <sync-url> <token> open\n\
             or: attendance-smoke <storage> <device-instance> <sync-url> <token> enroll \
             <subject> <operation-id> <performed-by> <expires-at>"
                .into(),
        );
    }
    let provisioning = AttendanceProvisioning::new(
        arguments[1].clone(),
        arguments[2].clone(),
        arguments[3].clone(),
    );
    let config = AttendanceConfig::new(arguments[0].clone(), provisioning)
        .with_enrollment_config(EnrollmentConfig::default().with_min_quality(0));
    let sdk = AttendanceBiometricSdk::open(config)?;
    match arguments[4].as_str() {
        "open" => {}
        "enroll" => {
            let subject_id = arguments
                .get(5)
                .ok_or("enroll requires a subject identifier")?;
            let gallery_id = sdk.gallery_stats().gallery_id;
            sdk.enroll_subject(
                SubjectEnrollmentAuthorization {
                    enrollment_operation_id: arguments[6].clone(),
                    performed_by: arguments[7].clone(),
                    device_instance_id: arguments[1].clone(),
                    gallery_id,
                    subject_id: subject_id.clone(),
                    batch_id: None,
                    authorization_expires_at: arguments[8].clone(),
                },
                [ridge_pattern(3)],
            )?;
            sdk.sync()?;
            sdk.sync()?;
        }
        command => return Err(format!("unsupported command {command}").into()),
    }
    let stats = sdk.gallery_stats();
    println!(
        "{}",
        serde_json::json!({
            "gallery_id": stats.gallery_id,
            "gallery_revision": stats.gallery_revision,
            "records": stats.records,
            "subjects": stats.subjects,
            "pending_enrollments": sdk.pending_enrollment_count()?,
            "sync_state": format!("{:?}", sdk.sync_state()?),
        })
    );

    Ok(())
}

fn ridge_pattern(seed: u8) -> Vec<u8> {
    let mut raw = vec![0_u8; biometric_sdk::fingerprint::RAW_LEN];
    for y in 0..biometric_sdk::fingerprint::RAW_HEIGHT as usize {
        for x in 0..biometric_sdk::fingerprint::RAW_WIDTH as usize {
            let wave = ((x + usize::from(seed) * y / 17) / 5) % 2;
            raw[y * biometric_sdk::fingerprint::RAW_WIDTH as usize + x] =
                if wave == 0 { 35 } else { 220 };
        }
    }
    raw
}
