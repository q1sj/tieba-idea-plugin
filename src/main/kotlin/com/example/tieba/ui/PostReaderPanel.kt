package com.example.tieba.ui

import com.example.tieba.data.TiebaPost
import com.intellij.util.ui.JBUI
import javax.swing.*
import java.awt.*

class PostReaderPanel : JPanel(BorderLayout(0, 5)) {

    var onThreadTitle: String = ""
        private set
    var onThreadTid: Long = 0
        private set
    val isOnlyOp: Boolean
        get() = onlyOpButton.isSelected

    private val titleLabel = JLabel("点击帖子查看内容", SwingConstants.CENTER).apply {
        font = font.deriveFont(Font.BOLD, 14f)
        foreground = Color.DARK_GRAY
    }

    private val editorPane = JEditorPane("text/html", "").apply {
        isEditable = false
        border = JBUI.Borders.empty(8)
        background = UIManager.getColor("Component.background")
    }

    private val loadingLabel = JLabel("加载中...", SwingConstants.CENTER).apply {
        foreground = Color.GRAY
    }

    private val prevPageButton = JButton("< 上一页").apply { isEnabled = false }
    private val nextPageButton = JButton("下一页 >").apply { isEnabled = false }
    private val onlyOpButton = JCheckBox("只看楼主").apply { isSelected = false }
    private val pageInfoLabel = JLabel("1")
    private val backToListButton = JButton("返回列表").apply {
        isFocusable = false
        font = font.deriveFont(11f)
    }

    private val cardPanel = JPanel(CardLayout())
    private val cardLayout: CardLayout = cardPanel.layout as CardLayout

    var onOnlyOpChange: ((Int) -> Unit)? = null
    var onPageChange: ((Int) -> Unit)? = null
    var onBack: (() -> Unit)? = null

    init {
        val topPanel = JPanel(BorderLayout(5, 0))
        topPanel.add(backToListButton, BorderLayout.WEST)
        topPanel.add(titleLabel, BorderLayout.CENTER)
        add(topPanel, BorderLayout.NORTH)

        val scrollPane = JScrollPane(editorPane)
        scrollPane.viewport.view = editorPane
        cardPanel.add(scrollPane, "CONTENT")
        cardPanel.add(loadingLabel, "LOADING")
        add(cardPanel, BorderLayout.CENTER)

        val bottomPanel = JPanel(BorderLayout(5, 0))
        bottomPanel.add(prevPageButton, BorderLayout.WEST)
        bottomPanel.add(pageInfoLabel, BorderLayout.CENTER)
        bottomPanel.add(nextPageButton, BorderLayout.EAST)

        val controlsPanel = JPanel(BorderLayout())
        controlsPanel.add(bottomPanel, BorderLayout.CENTER)
        controlsPanel.add(onlyOpButton, BorderLayout.EAST)

        add(controlsPanel, BorderLayout.SOUTH)

        onlyOpButton.addActionListener {
            val page = pageInfoLabel.text.split("/").first().toIntOrNull() ?: 1
            onOnlyOpChange?.invoke(page)
        }

        prevPageButton.addActionListener {
            val current = pageInfoLabel.text.split("/").first().toIntOrNull() ?: 1
            if (current > 1) onPageChange?.invoke(current - 1)
        }
        nextPageButton.addActionListener {
            val current = pageInfoLabel.text.split("/").first().toIntOrNull() ?: 1
            onPageChange?.invoke(current + 1)
        }

        backToListButton.addActionListener {
            onBack?.invoke()
        }
    }

    fun setPosts(
        posts: List<TiebaPost>,
        threadTitle: String,
        tid: Long,
        totalPage: Int,
        currentPage: Int,
        hasMore: Boolean,
        onlyOp: Boolean
    ) {
        onThreadTitle = threadTitle
        onThreadTid = tid
        titleLabel.text = threadTitle
        pageInfoLabel.text = "$currentPage/$totalPage"
        prevPageButton.isEnabled = currentPage > 1
        nextPageButton.isEnabled = hasMore
        onlyOpButton.isSelected = onlyOp

        val sb = StringBuilder()
        sb.append("<html><body style=\"font-family: sans-serif; font-size: 13px; line-height: 1.6; padding: 8px; background: transparent; color: inherit;\">")

        for (post in posts) {
            val borderColor = if (post.isOp) "#4285f4" else "#666"
            sb.append("<div style=\"border-left: 3px solid ${borderColor}; padding: 10px 12px; margin-bottom: 8px; border-radius: 0 4px 4px 0; background: transparent;\">")

            val opTag = if (post.isOp) " <span style=\"color:#e67e22; font-weight:bold;\">[楼主]</span>" else ""
            sb.append("<div style=\"margin-bottom: 6px; color: #888; font-size: 12px;\">")
            sb.append("<strong style=\"color: inherit;\">${escapeHtml(post.author)}</strong>$opTag")
            sb.append(" · 第${post.floor}楼")
            if (post.timestamp.isNotEmpty()) sb.append(" · ${post.timestamp}")
            sb.append("</div>")

            if (post.text.isNotEmpty()) {
                sb.append("<div style=\"margin-bottom: 6px; white-space: pre-wrap; color: inherit;\">")
                sb.append(escapeHtml(post.text))
                sb.append("</div>")
            }

            for (imgUrl in post.imgs) {
                sb.append("<div style=\"margin: 6px 0;\"><img src=\"$imgUrl\" style=\"max-width: 240px; max-height: 180px; cursor: pointer; border-radius: 4px;\" /></div>")
            }

            if (post.replyNum > 0) {
                sb.append("<div style=\"color: #999; font-size: 11px; margin-top: 4px;\">${post.replyNum} 条回复</div>")
            }

            sb.append("</div>")
        }

        sb.append("</body></html>")
        editorPane.text = sb.toString()
    }

    fun showError(message: String) {
        titleLabel.text = "错误"
        editorPane.text = "<html><body style=\"padding: 16px; color: #e74c3c; background: transparent;\"><strong>$message</strong></body></html>"
        pageInfoLabel.text = "1"
        prevPageButton.isEnabled = false
        nextPageButton.isEnabled = false
    }

    fun showLoading() {
        cardLayout.show(cardPanel, "LOADING")
    }

    fun hideLoading() {
        cardLayout.show(cardPanel, "CONTENT")
    }

    fun clear() {
        titleLabel.text = "点击帖子查看内容"
        editorPane.text = ""
        pageInfoLabel.text = "1"
        onThreadTid = 0
    }

    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }
}
