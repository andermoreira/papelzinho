package com.andersonmoreira.papelzinho

object SessionTiming {
    const val ARMED_TTL_MS = 120_000L
    const val TOGGLE_TIMEOUT_MS = 5_000L
    const val PREVIEW_TIMEOUT_MS = 10_000L
    const val HANDOFF_TIMEOUT_MS = 8_000L
    const val SERVICE_POLL_MS = 100L
    const val EXPIRY_POLL_MS = 1_000L
    const val MAX_UI_NODES = 2_000
}

object NoteDimensions {
    const val WIDTH = 1080
    const val STANDARD_HEIGHT = 1350
    const val TALL_HEIGHT = 1920
    const val PREVIEW_WIDTH = 216
}
