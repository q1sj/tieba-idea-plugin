package com.example.tieba.ui

import com.example.tieba.data.TiebaBridge
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.openapi.project.Project
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
}
