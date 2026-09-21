package com.example.tieba.ui

import com.example.tieba.data.TiebaPost
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.ui.JBUI
import javax.imageio.ImageIO
import javax.swing.*
import javax.swing.event.HyperlinkEvent
import java.awt.*
import java.awt.image.BufferedImage
import java.net.HttpURLConnection
import java.net.URL
import java.util.LinkedHashMap

class PostReaderPanel : JPanel(BorderLayout(0, 5)) {

    companion object {
        private val LOG = Logger.getInstance(PostReaderPanel::class.java)
        private const val PROP_FONT_COLOR = "tieba.postFontColor"
        private const val IMAGE_LINK_PREFIX = "https://tieba.local/img/"
    }

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
        addHyperlinkListener { e ->
            if (e.eventType == HyperlinkEvent.EventType.ACTIVATED) {
                val url = e.url?.toString() ?: e.description?.takeIf { it.startsWith(IMAGE_LINK_PREFIX) } ?: return@addHyperlinkListener
                if (url.startsWith(IMAGE_LINK_PREFIX)) {
                    val idx = url.removePrefix(IMAGE_LINK_PREFIX).toIntOrNull()
                    if (idx != null && idx < currentImageUrls.size) {
                        showImageDialog(currentImageUrls[idx])
                    }
                }
            }
        }
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

    private var fgHex: String = colorToHex(loadColor())

    private val colorPickerButton = JButton().apply {
        preferredSize = Dimension(20, 20)
        isContentAreaFilled = true
        isFocusable = false
        border = BorderFactory.createLineBorder(Color.GRAY)
        background = loadColor()
        toolTipText = "选择字体颜色"
    }

    private val cardPanel = JPanel(CardLayout())
    private val cardLayout: CardLayout = cardPanel.layout as CardLayout

    var onOnlyOpChange: ((Int) -> Unit)? = null
    var onPageChange: ((Int) -> Unit)? = null
    var onBack: (() -> Unit)? = null

    private val imageCache = LinkedHashMap<String, BufferedImage>()
    private var currentImageUrls = emptyList<String>()

    private var lastPosts: List<TiebaPost>? = null
    private var lastThreadTitle: String = ""
    private var lastTotalPage: Int = 0
    private var lastCurrentPage: Int = 1
    private var lastHasMore: Boolean = false
    private var lastOnlyOp: Boolean = false

    init {
        val topPanel = JPanel(BorderLayout(5, 0))
        topPanel.add(backToListButton, BorderLayout.WEST)
        topPanel.add(titleLabel, BorderLayout.CENTER)
        topPanel.add(colorPickerButton, BorderLayout.EAST)
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

        colorPickerButton.addActionListener {
            val newColor = JColorChooser.showDialog(this, "选择字体颜色", colorPickerButton.background)
            if (newColor != null) {
                saveColor(newColor)
                fgHex = colorToHex(newColor)
                colorPickerButton.background = newColor
                val cached = lastPosts
                if (cached != null) {
                    setPosts(cached, lastThreadTitle, onThreadTid, lastTotalPage, lastCurrentPage, lastHasMore, lastOnlyOp)
                }
            }
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
        lastPosts = posts
        lastThreadTitle = threadTitle
        lastTotalPage = totalPage
        lastCurrentPage = currentPage
        lastHasMore = hasMore
        lastOnlyOp = onlyOp
        titleLabel.text = threadTitle
        pageInfoLabel.text = "$currentPage/$totalPage"
        prevPageButton.isEnabled = currentPage > 1
        nextPageButton.isEnabled = hasMore
        onlyOpButton.isSelected = onlyOp

        val allImageUrls = mutableListOf<String>()
        for (post in posts) {
            for (img in post.imgs) {
                allImageUrls.add(makeAbsolute(img))
            }
        }
        currentImageUrls = allImageUrls

        val sb = StringBuilder()
        sb.append("<html><body style=\"font-family: sans-serif; font-size: 13px; line-height: 1.6; padding: 8px; background: transparent; color: $fgHex;\">")

        var imgIdx = 0
        for (post in posts) {
            val borderColor = if (post.isOp) "#4285f4" else "#666"
            sb.append("<div style=\"border-left: 3px solid ${borderColor}; padding: 10px 12px; margin-bottom: 8px; border-radius: 0 4px 4px 0; background: transparent;\">")

            val opTag = if (post.isOp) " <span style=\"color:#e67e22; font-weight:bold;\">[楼主]</span>" else ""
            sb.append("<div style=\"margin-bottom: 6px; color: #aaa; font-size: 12px;\">")
            sb.append("<strong style=\"color: $fgHex;\">${escapeHtml(post.author)}</strong>$opTag")
            sb.append(" · 第${post.floor}楼")
            if (post.timestamp.isNotEmpty()) sb.append(" · ${post.timestamp}")
            sb.append("</div>")

            if (post.text.isNotEmpty()) {
                sb.append("<div style=\"margin-bottom: 6px; white-space: pre-wrap; color: $fgHex;\">")
                sb.append(escapeHtml(post.text))
                sb.append("</div>")
            }

            if (post.imgs.isNotEmpty()) {
                sb.append("<div style=\"margin: 6px 0;\">")
                for (i in post.imgs.indices) {
                    if (i > 0) sb.append(" ")
                    sb.append("<a href=\"${IMAGE_LINK_PREFIX}${imgIdx}\" style=\"color:#69b; text-decoration:none; cursor:pointer;\">[图片 ${i + 1}]</a>")
                    imgIdx++
                }
                sb.append("</div>")
            }

            if (post.replyNum > 0) {
                sb.append("<div style=\"color: #bbb; font-size: 11px; margin-top: 4px;\">${post.replyNum} 条回复</div>")
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
        lastPosts = null
    }

    private fun makeAbsolute(url: String): String {
        if (url.startsWith("http://") || url.startsWith("https://")) return url
        return "https://img.baidu.com$url"
    }

    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }

    private fun showImageDialog(imageUrl: String) {
        val dialog = JDialog(null as Frame?, "图片查看", true).apply {
            size = Dimension(600, 600)
            setLocationRelativeTo(null)
        }

        val loadingLabel = JLabel("加载中...", SwingConstants.CENTER).apply {
            foreground = Color.GRAY
            font = font.deriveFont(14f)
        }
        val contentPanel = JPanel(GridLayout())
        contentPanel.add(loadingLabel)

        val scrollPane = JScrollPane(contentPanel).apply {
            border = null
            viewport.background = dialog.background
        }
        dialog.layout = BorderLayout()
        dialog.add(scrollPane, BorderLayout.CENTER)

        if (imageCache.containsKey(imageUrl)) {
            showImageInDialog(dialog, contentPanel, imageCache[imageUrl]!!)
            dialog.setVisible(true)
            return
        }

        object : SwingWorker<BufferedImage?, Exception>() {
            override fun doInBackground(): BufferedImage? {
                return try {
                    val url = URL(imageUrl)
                    val conn = url.openConnection() as HttpURLConnection
                    conn.connectTimeout = 10000
                    conn.readTimeout = 15000
                    conn.requestMethod = "GET"
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    conn.setRequestProperty("Referer", "https://tieba.baidu.com/")
                    conn.instanceFollowRedirects = true
                    LOG.info("Loading image: $imageUrl, response code: ${conn.responseCode}")
                    conn.inputStream.use { stream ->
                        val image = ImageIO.read(stream)
                        LOG.info("Image loaded: ${image?.width}x${image?.height}")
                        image
                    }
                } catch (e: Exception) {
                    LOG.error("Failed to load image: $imageUrl", e)
                    throw e
                }
            }

            override fun done() {
                try {
                    val image = get()
                    if (image != null) {
                        imageCache[imageUrl] = image
                        if (imageCache.size > 50) {
                            imageCache.remove(imageCache.keys.first())
                        }
                        showImageInDialog(dialog, contentPanel, image)
                    } else {
                        showErrorInDialog(contentPanel, "图片加载失败")
                    }
                } catch (e: Exception) {
                    showErrorInDialog(contentPanel, "加载失败: ${e.message}")
                }
            }
        }.execute()

        dialog.setVisible(true)
    }

    private fun showErrorInDialog(panel: JPanel, message: String) {
        panel.removeAll()
        val label = JLabel("<html><div style=\"color:#e74c3c; padding:16px;\"><strong>$message</strong></div></html>")
        label.horizontalAlignment = SwingConstants.CENTER
        label.verticalAlignment = SwingConstants.CENTER
        panel.add(label)
        panel.revalidate()
        panel.repaint()
    }

    private fun showImageInDialog(dialog: JDialog, panel: JPanel, image: BufferedImage) {
        panel.removeAll()
        val imageLabel = JLabel().apply {
            icon = ImageIcon(image)
            horizontalAlignment = SwingConstants.CENTER
            verticalAlignment = SwingConstants.CENTER
        }
        panel.add(imageLabel)
        panel.revalidate()
        panel.repaint()
        dialog.pack()
        if (dialog.width > 1000) dialog.setSize(1000, dialog.height)
        if (dialog.height > 800) dialog.setSize(dialog.width, 800)
        dialog.setLocationRelativeTo(null)
    }

    private fun loadColor(): Color {
        val hex = PropertiesComponent.getInstance().getValue(PROP_FONT_COLOR)
        return if (hex != null) {
            try { Color.decode(hex) } catch (_: Exception) { defaultColor() }
        } else {
            defaultColor()
        }
    }

    private fun saveColor(color: Color) {
        PropertiesComponent.getInstance().setValue(PROP_FONT_COLOR, colorToHex(color))
    }

    private fun defaultColor(): Color {
        return UIManager.getColor("Component.foreground") ?: Color.WHITE
    }

    private fun colorToHex(color: Color): String {
        return String.format("#%02x%02x%02x", color.red, color.green, color.blue)
    }
}
