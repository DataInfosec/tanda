package com.tanda.core.remote.exception

class BusinessException(
    val code: String,
    message: String,
    cause: Throwable? = null
) : Throwable(message, cause)
