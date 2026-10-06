package com.andersonmoreira.papelzinho.selectors

import com.andersonmoreira.papelzinho.NodeSelector
import com.andersonmoreira.papelzinho.SelectorSet
import com.andersonmoreira.papelzinho.SendRoute

// All external UI values originate in fixtures/whatsapp/pt-BR (2.26.38.73).
@Suppress("ForbiddenComment") // The spec requires TODOs for selectors without captured fixtures.
object WhatsAppSelectors {
    val current =
        SelectorSet(
            version = "1.0.0",
            supportedPackage = "com.whatsapp",
            supportedVersion = "2.26.38.73",
            supportedLocale = "pt-BR",
            previewScreen = NodeSelector(resourceIds = listOf("com.whatsapp:id/gallery_selected_media")),
            viewOnceToggle = NodeSelector(resourceIds = listOf("com.whatsapp:id/view_once_toggle")),
            viewOnceToggleActive = NodeSelector(contentDescriptions = listOf(Regex("Desativar a visualização única"))),
            firstTimeDialogConfirm = NodeSelector(), // TODO: capture the real first-time dialog.
            sendButton = NodeSelector(resourceIds = listOf("com.whatsapp:id/send_media_btn")),
        )

    fun forRoute(route: SendRoute): SelectorSet = if (route == SendRoute.CLIPBOARD) clipboardPreview else current

    // Captured clipboard editor profile. Diagnostic copying does not arm sending.
    val clipboardPreview =
        current.copy(
            version = "1.1.0",
            previewScreen = NodeSelector(resourceIds = listOf("com.whatsapp:id/media_composer_layout")),
            sendButton = NodeSelector(resourceIds = listOf("com.whatsapp:id/send")),
        )
    // TODO: English and WhatsApp Business fixtures; unsupported combinations remain disabled.
}
