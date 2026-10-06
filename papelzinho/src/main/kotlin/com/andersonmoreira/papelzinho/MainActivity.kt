package com.andersonmoreira.papelzinho

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import com.andersonmoreira.papelzinho.selectors.WhatsAppSelectors
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private lateinit var model: NoteViewModel
    private val app get() = application as PapelzinhoApplication
    private var targets by mutableStateOf(emptyList<WhatsAppTargetResolver.Target>())
    private var accessibilityEnabled by mutableStateOf(false)
    private var notificationsAllowed by mutableStateOf(false)
    private var clipboardCopied by mutableStateOf(false)
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        super.onCreate(savedInstanceState)
        model = ViewModelProvider(this)[NoteViewModel::class.java]
        receiveText(intent)
        setContent {
            val colors =
                if (isSystemInDarkTheme()) {
                    darkColorScheme(
                        primary = NIGHT_PRIMARY,
                    )
                } else {
                    lightColorScheme(primary = DAY_PRIMARY)
                }
            MaterialTheme(colorScheme = colors) {
                Surface(Modifier.fillMaxSize()) { NoteScreen() }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        receiveText(intent)
    }

    private fun receiveText(incoming: Intent) {
        if (incoming.action == Intent.ACTION_SEND && incoming.type == "text/plain") {
            incoming.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.let(model::updateText)
            incoming.removeExtra(Intent.EXTRA_TEXT)
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
        app.clipboardDiagnostic.sessions.active()
        app.clipboardDiagnostic.cleanupClipboard()
        clipboardCopied = app.clipboardDiagnostic.sessions.active() != null
    }

    private fun refresh() {
        targets = WhatsAppTargetResolver(this).installed()
        val manager = getSystemService(AccessibilityManager::class.java)
        accessibilityEnabled =
            manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).any {
                it.resolveInfo.serviceInfo.packageName == packageName &&
                    it.resolveInfo.serviceInfo.name == ViewOnceAccessibilityService::class.java.name
            }
        notificationsAllowed =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    private fun send(sample: Boolean) {
        app.clipboardDiagnostic.cancel()
        clipboardCopied = false
        refresh()
        val resolver = WhatsAppTargetResolver(this)
        val target = targets.firstOrNull { it.packageName == model.settings.targetPackage } ?: targets.firstOrNull()
        when {
            target == null -> model.showError(R.string.whatsapp_missing)
            !accessibilityEnabled -> model.showError(R.string.accessibility_enable)
            !resolver.supported(target) -> model.showError(R.string.selectors_unsupported)
            else ->
                model.prepare(sample) { bytes ->
                    val session = app.sessions.arm(bytes, target.packageName)
                    try {
                        startActivity(WhatsAppShareIntent.create(app, session))
                        model.updateText("")
                    } catch (_: ActivityNotFoundException) {
                        launchFailed(session)
                    } catch (_: SecurityException) {
                        launchFailed(session)
                    }
                }
        }
    }

    private fun launchFailed(session: SendSession) {
        app.sessions.release(session.token, SessionState.ABORTED)
        model.showError(R.string.launch_error)
    }

    private fun copySample(assisted: Boolean) {
        refresh()
        val target = targets.firstOrNull { it.packageName == model.settings.targetPackage } ?: targets.firstOrNull()
        if (target == null) {
            model.showError(R.string.whatsapp_missing)
            return
        }
        if (assisted && (!accessibilityEnabled || !WhatsAppTargetResolver(this).supported(target))) {
            model.showError(
                if (!accessibilityEnabled) R.string.accessibility_enable else R.string.selectors_unsupported,
            )
            return
        }
        model.prepare(true) { bytes ->
            try {
                if (assisted) {
                    app.clipboardDiagnostic.copyForSending(bytes, target.packageName)
                    clipboardCopied = false
                } else {
                    app.clipboardDiagnostic.copy(bytes, target.packageName)
                    clipboardCopied = true
                }
            } catch (_: RuntimeException) {
                model.showError(R.string.clipboard_error)
            }
        }
    }

    @Suppress("MagicNumber") // Compose spacing and UI refresh cadence.
    @Composable
    private fun NoteScreen() {
        var sessionActive by androidx.compose.runtime.remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            while (true) {
                sessionActive = app.sessions.active() != null
                delay(250)
            }
        }
        val target = targets.firstOrNull { it.packageName == model.settings.targetPackage } ?: targets.firstOrNull()
        val supported = target?.let { WhatsAppTargetResolver(this).supported(it) } == true
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing,
                ).imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(
                    R.string.app_name,
                ),
                style = MaterialTheme.typography.headlineLarge,
                modifier =
                    Modifier.semantics {
                        heading()
                    },
            )
            Text(stringResource(R.string.note_subtitle), style = MaterialTheme.typography.bodyLarge)
            NoteEditor(model)
            NotePreview(model)
            NoteError(model.error)
            PalettePicker(model)
            NoteActions(model, supported && accessibilityEnabled, sessionActive, app.sessions) { send(false) }
            SetupSection(target, supported)
            AssistedClipboardTest(supported && accessibilityEnabled && !model.preparing) { copySample(true) }
            ClipboardTestSection(
                target != null && !model.preparing,
                clipboardCopied,
                { copySample(false) },
            ) {
                app.clipboardDiagnostic.cancel()
                clipboardCopied = false
            }
            NoteSettingsUi(model, targets, target)
            Text(stringResource(R.string.privacy_note), style = MaterialTheme.typography.bodySmall)
        }
    }

    @Composable
    private fun SetupSection(
        target: WhatsAppTargetResolver.Target?,
        supported: Boolean,
    ) {
        Text(
            stringResource(
                R.string.setup_label,
            ),
            style = MaterialTheme.typography.titleMedium,
            modifier =
                Modifier.semantics {
                    heading()
                },
        )
        Text(stringResource(if (target == null) R.string.whatsapp_missing else R.string.check_installed))
        if (accessibilityEnabled) {
            Text(stringResource(R.string.accessibility_active))
        } else {
            OutlinedButton(onClick = {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }) { Text(stringResource(R.string.accessibility_enable)) }
        }
        if (notificationsAllowed) {
            Text(stringResource(R.string.notifications_active))
        } else {
            OutlinedButton(onClick = {
                if (Build.VERSION.SDK_INT >=
                    33
                ) {
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }) { Text(stringResource(R.string.notifications_enable)) }
        }
        if (target != null) Text(stringResource(R.string.whatsapp_version, target.version))
        Text(stringResource(R.string.selectors_version, WhatsAppSelectors.current.version))
        if (target != null && !supported) Text(stringResource(R.string.selectors_unsupported))
        if (target != null &&
            model.settings.successfulVersion != null &&
            model.settings.successfulVersion != target.version
        ) {
            Text(stringResource(R.string.whatsapp_updated_warning))
        }
        OutlinedButton(onClick = {
            send(true)
        }, enabled = supported && accessibilityEnabled && !model.preparing) {
            Text(
                stringResource(R.string.test_image_button),
            )
        }
    }
}

@Composable
private fun NoteError(error: Int?) {
    if (error != null) {
        Text(
            stringResource(error),
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

@Composable
private fun AssistedClipboardTest(
    enabled: Boolean,
    copy: () -> Unit,
) {
    Text(stringResource(R.string.clipboard_assisted_label), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(R.string.clipboard_assisted_help))
    OutlinedButton(onClick = copy, enabled = enabled) {
        Text(stringResource(R.string.clipboard_assisted_button))
    }
}

@Composable
private fun ClipboardTestSection(
    enabled: Boolean,
    copied: Boolean,
    copy: () -> Unit,
    cancel: () -> Unit,
) {
    Text(stringResource(R.string.clipboard_test_label), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(R.string.clipboard_test_help))
    OutlinedButton(onClick = copy, enabled = enabled) {
        Text(stringResource(R.string.clipboard_test_button))
    }
    if (copied) {
        Text(
            stringResource(R.string.clipboard_test_ready),
            Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        TextButton(onClick = cancel) { Text(stringResource(R.string.clipboard_test_cancel)) }
    }
}

private val NIGHT_PRIMARY = Color(android.graphics.Color.parseColor("#C4D7AD"))
private val DAY_PRIMARY = Color(android.graphics.Color.parseColor("#435C35"))

@Suppress("MagicNumber") // Declarative spacing and sizing of Compose controls.
@Composable
private fun NoteEditor(model: NoteViewModel) {
    OutlinedTextField(
        value = model.text,
        onValueChange = model::updateText,
        label = { Text(stringResource(R.string.note_label)) },
        placeholder = { Text(stringResource(R.string.note_hint)) },
        supportingText = {
            Text(
                stringResource(R.string.character_count, model.text.codePointCount(0, model.text.length)),
            )
        },
        minLines = 5,
        maxLines = 8,
        modifier = Modifier.fillMaxWidth(),
        isError = model.error == R.string.text_too_long,
    )
}

@Suppress("MagicNumber") // Declarative spacing and sizing of Compose controls.
@Composable
private fun NotePreview(model: NoteViewModel) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            val preview = model.preview
            if (preview != null && !preview.isRecycled) {
                Image(preview.asImageBitmap(), stringResource(R.string.preview_label), Modifier.width(144.dp))
            } else {
                Text(stringResource(R.string.preview_empty), modifier = Modifier.padding(24.dp))
            }
        }
    }
}

@Suppress("MagicNumber") // Declarative spacing and sizing of Compose controls.
@Composable
private fun PalettePicker(model: NoteViewModel) {
    Text(stringResource(R.string.theme_label), style = MaterialTheme.typography.titleSmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NotePalette.entries.forEach { palette ->
            val label =
                when (palette) {
                    NotePalette.PAPER -> R.string.paper_theme
                    NotePalette.NIGHT -> R.string.night_theme
                    NotePalette.SAGE -> R.string.sage_theme
                }
            FilterChip(selected = model.settings.palette == palette, onClick = {
                model.chooseSettings(model.settings.copy(palette = palette))
            }, label = { Text(stringResource(label)) })
        }
    }
}

@Suppress("MagicNumber") // Declarative spacing and sizing of Compose controls.
@Composable
private fun NoteActions(
    model: NoteViewModel,
    ready: Boolean,
    sessionActive: Boolean,
    sessions: SessionStore,
    onSend: () -> Unit,
) {
    Button(
        onClick = onSend,
        enabled =
            ready &&
                model.text.isNotBlank() &&
                model.error != R.string.text_too_long &&
                !model.preparing &&
                !sessionActive,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
    ) { Text(stringResource(if (model.preparing) R.string.preparing else R.string.send_once)) }
    if (sessionActive) {
        Text(stringResource(R.string.session_active))
        TextButton(onClick = {
            sessions.active()?.let {
                sessions.release(it.token, SessionState.ABORTED)
            }
        }) { Text(stringResource(R.string.cancel_session)) }
    }
}

@Composable
private fun NoteSettingsUi(
    model: NoteViewModel,
    targets: List<WhatsAppTargetResolver.Target>,
    target: WhatsAppTargetResolver.Target?,
) {
    Text(
        stringResource(
            R.string.settings_label,
        ),
        style = MaterialTheme.typography.titleMedium,
        modifier =
            Modifier.semantics {
                heading()
            },
    )
    listOf(
        NoteDimensions.STANDARD_HEIGHT to R.string.size_standard,
        NoteDimensions.TALL_HEIGHT to R.string.size_tall,
    ).forEach { (height, label) ->
        FilterChip(selected = model.settings.height == height, onClick = {
            model.chooseSettings(model.settings.copy(height = height))
        }, label = { Text(stringResource(label)) })
    }
    if (targets.size > 1) {
        Text(stringResource(R.string.account_label))
        targets.forEach { installed ->
            FilterChip(selected = target == installed, onClick = {
                model.chooseSettings(model.settings.copy(targetPackage = installed.packageName))
            }, label = {
                Text(
                    stringResource(
                        if (installed.packageName ==
                            "com.whatsapp"
                        ) {
                            R.string.whatsapp_account
                        } else {
                            R.string.business_account
                        },
                    ),
                )
            })
        }
    }
}
