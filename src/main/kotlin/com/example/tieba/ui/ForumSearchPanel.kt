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

    private val searchField = SearchTextField()
    private val goodCheckbox = JCheckBox("精品")
    private val searchButton = JButton("浏览")
    private val prevPageButton = JButton("<")
    private val nextPageButton = JButton(">")
    private val pageInfoLabel = JLabel("1")

    init {
        searchField.preferredSize = Dimension(150, searchField.preferredSize.height)

        val topPanel = JPanel(BorderLayout(5, 0))
        topPanel.add(searchField, BorderLayout.CENTER)
        val rightPanel = JPanel(BorderLayout(5, 0))
        rightPanel.add(goodCheckbox, BorderLayout.WEST)
        rightPanel.add(searchButton, BorderLayout.EAST)
        topPanel.add(rightPanel, BorderLayout.EAST)
        add(topPanel, BorderLayout.NORTH)

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

        searchButton.addActionListener { doSearch() }
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

    fun updatePageInfo(current: Int, total: Int, hasMore: Boolean) {
        currentPage = current
        pageInfoLabel.text = "$current/$total"
        prevPageButton.isEnabled = current > 1
        nextPageButton.isEnabled = hasMore
    }
}
