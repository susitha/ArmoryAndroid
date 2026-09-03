package com.cenango.fetchcicg.rfid

/**
 * Kotlin equivalent of iOS's `AsReaderGUNManager.Error` (AsReaderGUNManager+Error.swift),
 * now mapped onto the Chainway SDK's real `com.rscja.deviceapi.UhfBase.ErrorCode`
 * constants (confirmed from the SDK's bundled Javadoc,
 * `API_Ver20251103/doc/com/rscja/deviceapi/UhfBase.ErrorCode.html`) rather than
 * the old AsReader result codes. Call [fromCode] with `RFIDWithUHFUART.getErrCode()`
 * after a failed operation to resolve which one occurred.
 */
enum class RfidError(val code: Int, val description: String) {
    INVALID_MASK(-999, "Cannot create a mask with the given data"), // not an SDK code — thrown locally by setMask()

    SUCCESS(0, "Success"),
    NO_TAG(1, "No tags found"),
    INSUFFICIENT_PRIVILEGES(2, "The interrogator did not authenticate with sufficient privileges for the tag to perform the operation"),
    MEMORY_OVERRUN(3, "Memory overflow"),
    MEMORY_LOCK(4, "Memory is locked"),
    TAG_LOST(5, "Tag not responding"),
    PASSWORD_INCORRECT(6, "Incorrect password"),
    RESPONSE_BUFFER_OVERFLOW(7, "Response buffer overflowed"),
    NO_ENOUGH_POWER_ON_TAG(11, "Not enough power on tag"),
    SEND_FAIL(252, "Send failure"),
    RECV_FAIL(253, "Receive failure"),
    OPERATION_FAILED(255, "Operation failed"),
    UNKNOWN(-1, "An error has occurred due to unknown reason");

    companion object {
        fun fromCode(code: Int): RfidError = entries.firstOrNull { it.code == code } ?: UNKNOWN
    }
}
