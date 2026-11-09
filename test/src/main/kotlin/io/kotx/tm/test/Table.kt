package io.kotx.tm.test

data class Table(
    val name: String,
    val columns: List<String>,
    val rows: ArrayList<List<Any?>>
)
