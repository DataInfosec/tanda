//! Cross-repository smoke client for the Tanda Campus compatibility suite.

use std::env;
use std::error::Error;

use biometric_sdk::sdk::{CampusBiometricSdk, CampusConfig, CampusProvisioning, EnrollmentConfig};

fn main() -> Result<(), Box<dyn Error>> {
    let arguments = env::args().skip(1).collect::<Vec<_>>();
    if arguments.len() < 5 || arguments.len() > 6 {
        return Err(
            "usage: campus-smoke <storage> <device> <sync-url> <token> <open|enroll> [student]"
                .into(),
        );
    }
    let provisioning = CampusProvisioning::new(
        arguments[1].clone(),
        arguments[2].clone(),
        arguments[3].clone(),
    );
    let config = CampusConfig::new(arguments[0].clone(), provisioning)
        .with_enrollment_config(EnrollmentConfig::default().with_min_quality(0));
    let sdk = CampusBiometricSdk::open(config)?;
    match arguments[4].as_str() {
        "open" => {}
        "enroll" => {
            let student_id = arguments
                .get(5)
                .ok_or("enroll requires a student identifier")?;
            sdk.enroll_student(student_id.clone(), [ridge_pattern(3)], None)?;
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
            "users": stats.users,
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
