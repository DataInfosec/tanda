package com.tanda.core.remote.exception

class NetworkException(
    reason: String?,
    cause: Throwable? = null
) : Throwable(reason, cause)
