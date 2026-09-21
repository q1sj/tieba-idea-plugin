package com.example.tieba.data

data class TiebaThread(
    val tid: Long,
    val title: String,
    val text: String = "",
    val author: String = "",
    val replyNum: Int = 0,
    val viewNum: Int = 0,
    val lastTime: String = "",
    val isGood: Boolean = false,
    val isTop: Boolean = false
) {
    override fun toString(): String = title
}

data class TiebaPost(
    val floor: Int,
    val pid: Long = 0,
    val author: String = "",
    val text: String = "",
    val imgs: List<String> = emptyList(),
    val timestamp: String = "",
    val isOp: Boolean = false,
    val replyNum: Int = 0
)

data class TiebaComment(
    val author: String = "",
    val text: String = "",
    val timestamp: String = "",
    val isOp: Boolean = false,
    val agree: Int = 0
)
