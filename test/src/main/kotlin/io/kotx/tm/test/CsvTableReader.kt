package io.kotx.tm.test

import com.opencsv.CSVParser
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class CsvTableReader(
    private val tablesMetadata: TableMetadataService
) {
    enum class State { TABLE_NAME, HEADER, DATA }

    fun read(path: String): List<Table> {
        val stream = CsvTableReader::class.java.getResourceAsStream(path)
            ?: throw RuntimeException("Missing file '$path'")
        val lines = stream.bufferedReader().readLines()

        val result = ArrayList<Table>()
        var state = State.TABLE_NAME
        var tableName: String? = null
        var columns: List<String>? = null
        var rows: ArrayList<List<Any?>> = ArrayList()

        for (line in lines) {
            if (line.isBlank()) {
                state = State.TABLE_NAME
                continue
            }
            when (state) {
                State.TABLE_NAME -> {
                    if (tableName != null) {
                        result.add(Table(tableName, columns ?: emptyList(), rows))
                        columns = null
                        rows = ArrayList()
                    }
                    tableName = line.trim()
                }
                State.HEADER -> columns = line.trim().split(',').map { it.trim() }
                State.DATA -> rows.add(parseRow(tableName!!, columns!!, line.trim()))
            }
            if (state == State.TABLE_NAME) state = State.HEADER
            else if (state == State.HEADER) state = State.DATA
        }

        if (tableName != null) {
            result.add(Table(tableName, columns ?: emptyList(), rows))
        }
        return result
    }

    private fun parseRow(tableName: String, columns: List<String>, line: String): List<Any?> {
        val values = CSVParser().parseLine(line).toList()
        if (values.size != columns.size) {
            throw RuntimeException("columns and values size mismatch for table '$tableName', line: '$line'")
        }
        return values.mapIndexed { i, value ->
            if (value == "null") null
            else when (tablesMetadata.getDataType(tableName, columns[i])) {
                DataType.INT -> value.toLong()
                DataType.DOUBLE -> value.toDouble()
                DataType.BOOLEAN -> value.toBoolean()
                DataType.TEXT, DataType.TIMESTAMP, DataType.TS_VECTOR -> value
                DataType.DATE -> parseLocalDate(value)
            }
        }
    }

    private fun parseLocalDate(value: String): LocalDate =
        try {
            LocalDate.parse(value)
        } catch (e: Exception) {
            LocalDate.parse(value, DateTimeFormatter.ofPattern("dd-MM-uuuu"))
        }
}
