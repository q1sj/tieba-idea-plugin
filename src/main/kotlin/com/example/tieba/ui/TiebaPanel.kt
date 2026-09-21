package com.example.tieba.ui

import com.example.tieba.data.TiebaBridge
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.openapi.project.Project
import com.intellij.util.ui.JBUI
import javax.swing.*
import java.awt.*

class TiebaPanel(private val project: Project, private val bridge: TiebaBridge) : JPanel(BorderLayout()) {

    private val searchPanel = ForumSearchPanel()
    private val threadListPanel = ThreadListPanel()
    private val postReaderPanel = PostReaderPanel()
    private val statusLabel = JLabel("输入吧名开始浏览").apply {
        foreground = Color.GRAY
        font = font.deriveFont(11f)
    }

    private val cardPanel = JPanel(CardLayout())
    private val cardLayout = cardPanel.layout as CardLayout
    private val listCard = JPanel(BorderLayout(0, 5))

    init {
        listCard.add(searchPanel, BorderLayout.NORTH)
        listCard.add(threadListPanel, BorderLayout.CENTER)
        listCard.add(statusLabel, BorderLayout.SOUTH)

        cardPanel.add(listCard, "LIST")
        cardPanel.add(postReaderPanel, "POST")
        add(cardPanel, BorderLayout.CENTER)

        searchPanel.onSearch = { forum ->
            loadThreads(forum)
        }
        searchPanel.onPageChange = { page ->
            val forum = searchPanel.forum
            if (forum.isNotEmpty()) {
                loadThreads(forum, page)
            }
        }

        threadListPanel.onThreadSelect = { thread ->
            loadPosts(thread.tid, thread.title)
        }

        postReaderPanel.onOnlyOpChange = { page ->
            val title = postReaderPanel.onThreadTitle
            val tid = postReaderPanel.onThreadTid
            if (tid != 0L) {
                loadPosts(tid, title, page, onlyOp = true)
            }
        }

        postReaderPanel.onPageChange = { page ->
            val title = postReaderPanel.onThreadTitle
            val tid = postReaderPanel.onThreadTid
            val onlyOp = postReaderPanel.isOnlyOp
            if (tid != 0L) {
                loadPosts(tid, title, page, onlyOp = onlyOp)
            }
        }

        postReaderPanel.onBack = {
            cardLayout.show(cardPanel, "LIST")
        }

        postReaderPanel.onShowComments = { post ->
            val tid = postReaderPanel.onThreadTid
            if (tid != 0L && post.pid != 0L) {
                showCommentsDialog(tid, post)
            }
        }
    }

    private fun loadThreads(forum: String, page: Int = 1) {
        statusLabel.text = "加载中..."
        threadListPanel.showLoading()

        bridge.sendRequest(
            mapOf("action" to "get_threads", "forum" to forum, "page" to page)
        ).thenAccept { json ->
            SwingUtilities.invokeLater {
                threadListPanel.hideLoading()
                try {
                    val obj: JsonObject = JsonParser.parseString(json).asJsonObject
                    if (obj.has("error")) {
                        statusLabel.text = "错误: ${obj.get("error").asString}"
                        return@invokeLater
                    }

                    val threads = mutableListOf<com.example.tieba.data.TiebaThread>()
                    val totalPage = obj.get("totalPage")?.asInt ?: 0
                    val hasMore = obj.get("hasMore")?.asBoolean ?: false

                    obj.get("threads")?.asJsonArray?.forEach { tj ->
                        val t = tj.asJsonObject
                        threads.add(
                            com.example.tieba.data.TiebaThread(
                                tid = t.get("tid")?.asLong ?: 0,
                                title = t.get("title")?.asString ?: "",
                                text = t.get("text")?.asString ?: "",
                                author = t.get("author")?.asString ?: "",
                                replyNum = t.get("replyNum")?.asInt ?: 0,
                                viewNum = t.get("viewNum")?.asInt ?: 0,
                                lastTime = t.get("lastTime")?.asString ?: "",
                                isGood = t.get("isGood")?.asBoolean ?: false,
                                isTop = t.get("isTop")?.asBoolean ?: false
                            )
                        )
                    }

                    threadListPanel.setThreads(threads)
                    searchPanel.updatePageInfo(page, totalPage, hasMore)
                    statusLabel.text = "$forum - 第${page}页"
                } catch (e: Exception) {
                    statusLabel.text = "解析错误: ${e.message}"
                }
            }
        }
    }

    private fun loadPosts(tid: Long, title: String, page: Int = 1, onlyOp: Boolean = false) {
        cardLayout.show(cardPanel, "POST")
        postReaderPanel.showLoading()

        bridge.sendRequest(
            mapOf("action" to "get_posts", "tid" to tid, "page" to page, "only_op" to onlyOp)
        ).thenAccept { json ->
            SwingUtilities.invokeLater {
                postReaderPanel.hideLoading()
                try {
                    val obj: JsonObject = JsonParser.parseString(json).asJsonObject
                    if (obj.has("error")) {
                        postReaderPanel.showError(obj.get("error").asString)
                        return@invokeLater
                    }

                    val posts = mutableListOf<com.example.tieba.data.TiebaPost>()
                    val totalPage = obj.get("totalPage")?.asInt ?: 0
                    val hasMore = obj.get("hasMore")?.asBoolean ?: false
                    val threadTitle = obj.get("threadTitle")?.asString ?: title

                    obj.get("posts")?.asJsonArray?.forEach { pj ->
                        val p = pj.asJsonObject
                        val imgs = mutableListOf<String>()
                        p.get("imgs")?.asJsonArray?.forEach { imgs.add(it.asString) }
                        posts.add(
                            com.example.tieba.data.TiebaPost(
                                floor = p.get("floor")?.asInt ?: 0,
                                pid = p.get("pid")?.asLong ?: 0,
                                author = p.get("author")?.asString ?: "",
                                text = p.get("text")?.asString ?: "",
                                imgs = imgs,
                                timestamp = p.get("timestamp")?.asString ?: "",
                                isOp = p.get("isOp")?.asBoolean ?: false,
                                replyNum = p.get("replyNum")?.asInt ?: 0
                            )
                        )
                    }

                    postReaderPanel.setPosts(posts, threadTitle, tid, totalPage, page, hasMore, onlyOp)
                } catch (e: Exception) {
                    postReaderPanel.showError("解析错误: ${e.message}")
                }
            }
        }
    }

    private fun showCommentsDialog(tid: Long, post: com.example.tieba.data.TiebaPost) {
        val dialog = JDialog(null as Frame?, "第${post.floor}楼回复", true).apply {
            size = Dimension(520, 480)
            setLocationRelativeTo(null)
        }

        val editorPane = JEditorPane("text/html", "").apply {
            isEditable = false
            border = JBUI.Borders.empty(8)
        }
        val scrollPane = JScrollPane(editorPane)
        val loadingLabel = JLabel("加载中...", SwingConstants.CENTER).apply {
            foreground = Color.GRAY
        }
        val cardPanel = JPanel(CardLayout())
        val cardLayout = cardPanel.layout as CardLayout
        cardPanel.add(scrollPane, "CONTENT")
        cardPanel.add(loadingLabel, "LOADING")

        val prevButton = JButton("< 上一页").apply { isEnabled = false }
        val nextButton = JButton("下一页 >").apply { isEnabled = false }
        val pageLabel = JLabel("1")
        val bottomPanel = JPanel(BorderLayout(5, 0))
        bottomPanel.add(prevButton, BorderLayout.WEST)
        bottomPanel.add(pageLabel, BorderLayout.CENTER)
        bottomPanel.add(nextButton, BorderLayout.EAST)

        dialog.layout = BorderLayout(0, 5)
        dialog.add(cardPanel, BorderLayout.CENTER)
        dialog.add(bottomPanel, BorderLayout.SOUTH)

        var currentPage = 1
        var totalPage = 1
        var hasMore = false

        fun render(comments: List<com.example.tieba.data.TiebaComment>, page: Int) {
            val sb = StringBuilder()
            sb.append("<html><body style=\"font-family: sans-serif; font-size: 13px; line-height: 1.6; padding: 8px;\">")
            if (comments.isEmpty()) {
                sb.append("<div style=\"color: #999; text-align: center; padding: 12px;\">暂无回复</div>")
            }
            for (c in comments) {
                val borderColor = if (c.isOp) "#4285f4" else "#666"
                sb.append("<div style=\"border-left: 3px solid $borderColor; padding: 8px 10px; margin-bottom: 6px;\">")
                sb.append("<div style=\"margin-bottom: 4px; color: #aaa; font-size: 12px;\">")
                sb.append("<strong>${escapeHtml(c.author)}</strong>")
                if (c.isOp) sb.append(" <span style=\"color:#e67e22; font-weight:bold;\">[楼主]</span>")
                if (c.timestamp.isNotEmpty()) sb.append(" · ${c.timestamp}")
                if (c.agree > 0) sb.append(" · 赞${c.agree}")
                sb.append("</div>")
                if (c.text.isNotEmpty()) {
                    sb.append("<div style=\"white-space: pre-wrap;\">${escapeHtml(c.text)}</div>")
                }
                sb.append("</div>")
            }
            sb.append("</body></html>")
            editorPane.text = sb.toString()
            pageLabel.text = "$page/$totalPage"
            prevButton.isEnabled = page > 1
            nextButton.isEnabled = hasMore
        }

        fun loadComments(page: Int) {
            cardLayout.show(cardPanel, "LOADING")
            bridge.sendRequest(
                mapOf("action" to "get_comments", "tid" to tid, "pid" to post.pid, "page" to page)
            ).thenAccept { json ->
                SwingUtilities.invokeLater {
                    try {
                        val obj: JsonObject = JsonParser.parseString(json).asJsonObject
                        if (obj.has("error")) {
                            cardLayout.show(cardPanel, "CONTENT")
                            editorPane.text = "<html><body style=\"padding: 16px; color: #e74c3c;\"><strong>${obj.get("error").asString}</strong></body></html>"
                            return@invokeLater
                        }
                        val comments = mutableListOf<com.example.tieba.data.TiebaComment>()
                        totalPage = obj.get("totalPage")?.asInt ?: 0
                        hasMore = obj.get("hasMore")?.asBoolean ?: false
                        currentPage = page
                        obj.get("comments")?.asJsonArray?.forEach { cj ->
                            val c = cj.asJsonObject
                            comments.add(
                                com.example.tieba.data.TiebaComment(
                                    author = c.get("author")?.asString ?: "",
                                    text = c.get("text")?.asString ?: "",
                                    timestamp = c.get("timestamp")?.asString ?: "",
                                    isOp = c.get("isOp")?.asBoolean ?: false,
                                    agree = c.get("agree")?.asInt ?: 0
                                )
                            )
                        }
                        cardLayout.show(cardPanel, "CONTENT")
                        render(comments, page)
                    } catch (e: Exception) {
                        cardLayout.show(cardPanel, "CONTENT")
                        editorPane.text = "<html><body style=\"padding: 16px; color: #e74c3c;\"><strong>解析错误: ${e.message}</strong></body></html>"
                    }
                }
            }
        }

        prevButton.addActionListener { if (currentPage > 1) loadComments(currentPage - 1) }
        nextButton.addActionListener { loadComments(currentPage + 1) }

        loadComments(1)
        dialog.setVisible(true)
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
