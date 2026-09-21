package com.example.tieba.ui

import com.example.tieba.data.TiebaThread
import com.intellij.openapi.diagnostic.Logger
import javax.swing.*
import java.awt.*

class ThreadListPanel : JPanel(BorderLayout()) {

    companion object {
        private val LOG = Logger.getInstance(ThreadListPanel::class.java)
    }

    var onThreadSelect: ((TiebaThread) -> Unit)? = null

    private val listModel = DefaultListModel<TiebaThread>()
    private val threadList = JList<TiebaThread>(listModel)
    private val loadingLabel = JLabel("加载中...", SwingConstants.CENTER).apply {
        foreground = Color.GRAY
    }
    private val scrollPane = JScrollPane(threadList)
    private val cardPanel = JPanel(CardLayout())
    private val cardLayout = cardPanel.layout as CardLayout

    init {
        threadList.cellRenderer = ThreadCellRenderer()
        threadList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        threadList.addListSelectionListener { e ->
            if (!e.valueIsAdjusting) {
                val selected = threadList.selectedValue
                if (selected != null) {
                    onThreadSelect?.invoke(selected)
                }
            }
        }

        cardPanel.add(scrollPane, "LIST")
        cardPanel.add(loadingLabel, "LOADING")
        add(cardPanel, BorderLayout.CENTER)
    }

    fun setThreads(threads: List<TiebaThread>) {
        LOG.info("setThreads called with ${threads.size} threads, this.size=${this.size}, cardPanel.size=${cardPanel.size}, scrollPane.size=${scrollPane.size}, threadList.size=${threadList.size}")
        listModel.clear()
        threads.forEach { listModel.addElement(it) }
        LOG.info("setThreads done, listModel.size()=${listModel.size()}")
    }

    fun showLoading() {
        cardLayout.show(cardPanel, "LOADING")
    }

    fun hideLoading() {
        cardLayout.show(cardPanel, "LIST")
    }

    private inner class ThreadCellRenderer : JPanel(BorderLayout(0, 2)), ListCellRenderer<TiebaThread> {
        private val titleLabel = JLabel()
        private val infoLabel = JLabel()

        init {
            isOpaque = true
            border = BorderFactory.createEmptyBorder(3, 6, 3, 6)
            titleLabel.font = titleLabel.font.deriveFont(12f)
            infoLabel.foreground = Color.GRAY
            infoLabel.font = infoLabel.font.deriveFont(10f)
            add(titleLabel, BorderLayout.NORTH)
            add(infoLabel, BorderLayout.SOUTH)
        }

        override fun getListCellRendererComponent(
            list: JList<out TiebaThread>, value: TiebaThread?, index: Int, isSelected: Boolean, cellHasFocus: Boolean
        ): Component {
            if (value == null) {
                titleLabel.text = ""
                infoLabel.text = ""
                return this
            }

            val prefix = buildString {
                if (value.isTop) append("[置顶] ")
                if (value.isGood) append("[精华] ")
            }
            titleLabel.text = prefix + value.title
            titleLabel.toolTipText = value.text
            infoLabel.text = "${value.author} | ${value.replyNum}回复 | ${value.lastTime}"

            if (isSelected) {
                background = list.selectionBackground
                titleLabel.foreground = list.selectionForeground
                infoLabel.foreground = list.selectionForeground
            } else {
                background = list.background
                titleLabel.foreground = list.foreground
                infoLabel.foreground = Color.GRAY
            }

            return this
        }
    }
}
