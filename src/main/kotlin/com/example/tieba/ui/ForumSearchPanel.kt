package com.example.tieba.ui

import com.intellij.ui.SearchTextField
import com.intellij.util.ui.JBUI
import javax.swing.*
import java.awt.*
import java.awt.event.*

class ForumSearchPanel : JPanel(BorderLayout()) {

    var forum: String = ""
    var currentPage: Int = 1
    var isGoodMode: Boolean = false

    var onSearch: ((String) -> Unit)? = null
    var onPageChange: ((Int) -> Unit)? = null
    var onModeChange: ((Boolean) -> Unit)? = null
    var onForumSearch: ((String, Boolean) -> Unit)? = null

    private val searchField = SearchTextField()
    private val goodCheckbox = JCheckBox("精品")
    private val searchButton = JButton("浏览")
    private val prevPageButton = JButton("<")
    private val nextPageButton = JButton(">")
    private val pageInfoLabel = JLabel("1")
    private val keywordField = SearchTextField()
    private val onlyThreadCheckbox = JCheckBox("只看主题")
    private val forumSearchButton = JButton("吧内搜索")

    init {
        searchField.preferredSize = Dimension(150, searchField.preferredSize.height)
        keywordField.preferredSize = Dimension(150, keywordField.preferredSize.height)

        val topPanel = JPanel(BorderLayout(5, 0))
        topPanel.add(searchField, BorderLayout.CENTER)
        val rightPanel = JPanel(BorderLayout(5, 0))
        rightPanel.add(goodCheckbox, BorderLayout.WEST)
        rightPanel.add(searchButton, BorderLayout.EAST)
        topPanel.add(rightPanel, BorderLayout.EAST)

        val searchRowPanel = JPanel(BorderLayout(5, 0))
        searchRowPanel.add(keywordField, BorderLayout.CENTER)
        val keywordRightPanel = JPanel(BorderLayout(5, 0))
        keywordRightPanel.add(onlyThreadCheckbox, BorderLayout.WEST)
        keywordRightPanel.add(forumSearchButton, BorderLayout.EAST)
        searchRowPanel.add(keywordRightPanel, BorderLayout.EAST)

        val northPanel = JPanel(BorderLayout(0, 5))
        northPanel.add(topPanel, BorderLayout.NORTH)
        northPanel.add(searchRowPanel, BorderLayout.CENTER)
        add(northPanel, BorderLayout.NORTH)

        val bottomPanel = JPanel(BorderLayout(5, 0))
        prevPageButton.isEnabled = false
        prevPageButton.preferredSize = Dimension(30, 24)
        nextPageButton.preferredSize = Dimension(30, 24)
        bottomPanel.add(prevPageButton, BorderLayout.WEST)
        bottomPanel.add(pageInfoLabel, BorderLayout.CENTER)
        bottomPanel.add(nextPageButton, BorderLayout.EAST)
        add(bottomPanel, BorderLayout.SOUTH)

        searchField.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ENTER) {
                    doSearch()
                }
            }
        })
        keywordField.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ENTER) {
                    doForumSearch()
                }
            }
        })

        searchButton.addActionListener { doSearch() }
        forumSearchButton.addActionListener { doForumSearch() }
        goodCheckbox.addActionListener {
            isGoodMode = goodCheckbox.isSelected
            if (forum.isNotEmpty()) onModeChange?.invoke(isGoodMode)
        }
        prevPageButton.addActionListener {
            if (currentPage > 1) onPageChange?.invoke(currentPage - 1)
        }
        nextPageButton.addActionListener {
            onPageChange?.invoke(currentPage + 1)
        }
    }

    private fun doSearch() {
        val text = searchField.text.trim()
        if (text.isEmpty()) return
        forum = text
        currentPage = 1
        pageInfoLabel.text = "1"
        prevPageButton.isEnabled = false
        nextPageButton.isEnabled = false
        onSearch?.invoke(text)
    }

    private fun doForumSearch() {
        val keyword = keywordField.text.trim()
        if (keyword.isEmpty()) return
        if (forum.isEmpty()) return
        currentPage = 1
        pageInfoLabel.text = "1"
        prevPageButton.isEnabled = false
        nextPageButton.isEnabled = false
        onForumSearch?.invoke(keyword, onlyThreadCheckbox.isSelected)
    }

    fun updatePageInfo(current: Int, total: Int, hasMore: Boolean) {
        currentPage = current
        pageInfoLabel.text = "$current/$total"
        prevPageButton.isEnabled = current > 1
        nextPageButton.isEnabled = hasMore
    }
}
