package com.example.tieba.ui

import com.example.tieba.data.TiebaBridge
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory

class TiebaToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val bridge = TiebaBridge()
        bridge.start(project)

        val panel = TiebaPanel(project, bridge)
        val contentFactory = getContentFactory()
        val content = contentFactory.createContent(panel, "Tieba", false)
        content.setDisplayName("Tieba")
        content.setCloseable(true)
        toolWindow.contentManager.addContent(content)
    }

    private fun getContentFactory(): ContentFactory {
        try {
            val clazz = Class.forName("com.intellij.ui.content.ContentFactory")
            val method = clazz.getMethod("getInstance")
            return method.invoke(null) as ContentFactory
        } catch (e: Exception) {
            throw RuntimeException("Cannot create ContentFactory", e)
        }
    }
}
