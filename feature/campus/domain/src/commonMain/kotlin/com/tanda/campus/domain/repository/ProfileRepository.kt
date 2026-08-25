package com.tanda.campus.domain.repository

import kotlinx.coroutines.flow.Flow

interface ProfileRepository {
    fun observeName(): Flow<String?>

    fun getName(): String?
}
