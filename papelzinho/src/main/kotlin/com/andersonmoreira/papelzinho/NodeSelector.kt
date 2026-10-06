package com.andersonmoreira.papelzinho

data class UiNode(
    val resourceId: String = "",
    val description: String = "",
    val stateDescription: String = "",
    val text: String = "",
    val className: String = "",
    val checkable: Boolean = false,
    val checked: Boolean = false,
    val enabled: Boolean = true,
)

data class NodeMatch(
    val node: UiNode,
    val strategy: String,
)

data class NodeSelector(
    val resourceIds: List<String> = emptyList(),
    val contentDescriptions: List<Regex> = emptyList(),
    val texts: List<Regex> = emptyList(),
    val className: String? = null,
) {
    fun find(nodes: List<UiNode>): NodeMatch? {
        val eligible = nodes.filter { it.enabled && (className == null || it.className == className) }
        val strategies =
            listOf<Pair<String, (UiNode) -> Boolean>>(
                "resourceId" to { it.resourceId in resourceIds },
                "contentDescription" to
                    { node ->
                        contentDescriptions.any { it.matches(node.description) || it.matches(node.stateDescription) }
                    },
                "text" to { node -> texts.any { it.matches(node.text) } },
            )
        for ((strategy, matches) in strategies) {
            val found = eligible.filter(matches)
            if (found.isNotEmpty()) return found.singleOrNull()?.let { NodeMatch(it, strategy) }
        }
        return null
    }
}

data class SelectorSet(
    val version: String,
    val supportedPackage: String,
    val supportedVersion: String,
    val supportedLocale: String,
    val previewScreen: NodeSelector,
    val viewOnceToggle: NodeSelector,
    val viewOnceToggleActive: NodeSelector,
    val firstTimeDialogConfirm: NodeSelector,
    val sendButton: NodeSelector,
) {
    fun snapshot(nodes: List<UiNode>): PreviewSnapshot {
        val toggle = viewOnceToggle.find(nodes)?.node
        val active =
            toggle != null &&
                (
                    if (toggle.checkable) toggle.checked else viewOnceToggleActive.find(listOf(toggle)) != null
                )
        val known = firstTimeDialogConfirm.find(nodes) != null
        val dialog =
            nodes.any {
                it.className.contains("Dialog") ||
                    it.resourceId in setOf("android:id/button1", "android:id/alertTitle")
            }
        return PreviewSnapshot(
            preview = previewScreen.find(nodes) != null,
            toggle = toggle != null,
            active = active,
            send = sendButton.find(nodes) != null,
            knownDialog = known,
            unknownDialog = dialog && !known,
        )
    }
}
