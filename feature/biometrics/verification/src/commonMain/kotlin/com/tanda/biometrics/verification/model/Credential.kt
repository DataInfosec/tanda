package com.tanda.biometrics.verification.model

data class Credential(
    val id: String,
    val url: String,
    val path: String,
    val secret: String,
    val quality: Int = 65,
)
