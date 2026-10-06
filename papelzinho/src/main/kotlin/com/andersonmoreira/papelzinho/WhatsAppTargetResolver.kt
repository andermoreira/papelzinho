package com.andersonmoreira.papelzinho

import android.content.Context
import android.content.pm.PackageManager
import com.andersonmoreira.papelzinho.selectors.WhatsAppSelectors

class WhatsAppTargetResolver(
    private val context: Context,
) {
    data class Target(
        val packageName: String,
        val version: String,
    )

    fun installed(): List<Target> =
        listOf("com.whatsapp", "com.whatsapp.w4b").mapNotNull { name ->
            try {
                Target(
                    name,
                    context.packageManager
                        .getPackageInfo(name, 0)
                        .versionName
                        .orEmpty(),
                )
            } catch (_: PackageManager.NameNotFoundException) {
                null
            }
        }

    fun supported(target: Target): Boolean {
        val selectors = WhatsAppSelectors.current
        val locale =
            context.resources.configuration.locales[0]
                .toLanguageTag()
        return target.packageName == selectors.supportedPackage &&
            target.version == selectors.supportedVersion &&
            locale == selectors.supportedLocale
    }
}
