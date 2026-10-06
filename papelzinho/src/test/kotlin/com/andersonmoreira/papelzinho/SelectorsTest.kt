package com.andersonmoreira.papelzinho

import com.andersonmoreira.papelzinho.selectors.WhatsAppSelectors
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class SelectorsTest {
    @Test
    fun `clipboard profile confirms active state only in observed on fixture`() {
        val selectors = WhatsAppSelectors.clipboardPreview
        val off = selectors.snapshot(nodes("clipboard_preview_default"))
        val on = selectors.snapshot(nodes("clipboard_preview_viewonce_on"))
        assertTrue(off.preview)
        assertTrue(off.toggle)
        assertTrue(off.send)
        assertFalse(off.active)
        assertFalse(off.canSend)
        assertTrue(on.preview)
        assertTrue(on.active)
        assertTrue(on.canSend)
        assertFalse(selectors.snapshot(nodes("share_preview_observed")).canSend)
        assertFalse(selectors.snapshot(nodes("chat_after_send")).preview)
        assertFalse(selectors.snapshot(nodes("chat_list")).preview)
    }

    @Test
    fun `clipboard editor needs a distinct preview and send profile`() {
        val snapshot = WhatsAppSelectors.current.snapshot(nodes("clipboard_preview_viewonce_on"))
        assertFalse(snapshot.preview)
        assertTrue(snapshot.toggle)
        assertFalse(snapshot.send)
        assertTrue(snapshot.active)
        assertFalse(snapshot.canSend)
    }

    private fun nodes(name: String): List<UiNode> {
        val factory = DocumentBuilderFactory.newInstance()
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        val doc = factory.newDocumentBuilder().parse(File("../fixtures/whatsapp/pt-BR/$name.xml"))
        val nodes = doc.getElementsByTagName("node")
        return (0 until nodes.length).map { index ->
            val attrs = nodes.item(index).attributes

            fun attr(key: String) = attrs.getNamedItem(key)?.nodeValue.orEmpty()
            UiNode(
                resourceId = attr("resource-id"),
                description = attr("content-desc"),
                text = attr("text"),
                className = attr("class"),
                checkable = attr("checkable") == "true",
                checked = attr("checked") == "true",
                enabled = attr("enabled") == "true",
            )
        }
    }

    @Test
    fun `observed selectors distinguish off on and chat`() {
        val selectors = WhatsAppSelectors.current
        val off = nodes("preview_default")
        val on = nodes("preview_viewonce_on")
        val chat = nodes("chat_after_send")
        assertTrue(selectors.snapshot(off).preview)
        assertFalse(selectors.snapshot(off).active)
        assertTrue(selectors.snapshot(on).active)
        assertFalse(selectors.snapshot(chat).preview)
        assertFalse(selectors.snapshot(nodes("chat_list")).preview)
        assertNull(selectors.viewOnceToggleActive.find(off))
        assertNotNull(selectors.viewOnceToggleActive.find(on))
    }

    @Test
    fun `preview signature remains recognizable when toggle disappears`() {
        val selectors = WhatsAppSelectors.current
        val missing = nodes("preview_default").filter { it.resourceId !in selectors.viewOnceToggle.resourceIds }
        assertTrue(selectors.snapshot(missing).preview)
        assertFalse(selectors.snapshot(missing).toggle)
        assertFalse(selectors.snapshot(missing).canSend)
    }

    @Test
    fun `ambiguous button and unknown active labels do not authorize send`() {
        val selectors = WhatsAppSelectors.current
        val off = nodes("preview_default")
        val toggle = selectors.viewOnceToggle.find(off)!!.node
        assertFalse(selectors.snapshot(off.map { if (it === toggle) it.copy(description = "unknown") else it }).active)
        val send = selectors.sendButton.find(off)!!.node
        assertNull(selectors.sendButton.find(off + send.copy()))
    }

    @Test
    fun `matching prioritizes ids over descriptions and requires class`() {
        val byDescription = UiNode(description = "fallback", className = "Button")
        val byId = UiNode(resourceId = "known", className = "Button")
        val selector =
            NodeSelector(
                resourceIds = listOf("known"),
                contentDescriptions = listOf(Regex("fallback")),
                className = "Button",
            )
        assertEquals("resourceId", selector.find(listOf(byDescription, byId))!!.strategy)
        assertNull(selector.find(listOf(byId.copy(className = "Image"))))
    }
}
