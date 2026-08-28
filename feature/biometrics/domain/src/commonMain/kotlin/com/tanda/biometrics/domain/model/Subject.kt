package com.tanda.biometrics.domain.model

data class Subject(
    val id: String,
    val site: String,
    val type: String,
    val name: String,
    val reference: String,
    val state: String,
    val status: String,
    val record: String,
    val lifecycle: String,
    val organization: String,
    val properties: Map<String, String>,
    val version: Int,
    val updatedAt: String,
    val createdAt: String,
)
