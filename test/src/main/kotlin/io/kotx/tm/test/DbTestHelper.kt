package io.kotx.tm.test

import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import io.kotx.tm.core.TransactionManager
import java.sql.Statement

class DbTestHelper(
    private val transactionManager: TransactionManager
) {
    private val logger = LoggerFactory.getLogger(DbTestHelper::class.java)

    private val tableMetadataService = TableMetadataService(transactionManager, "public")
    private val csvTableReader = CsvTableReader(tableMetadataService)

    fun execSql(sql: String) {
        runBlocking {
            transactionManager.rwTransaction {
                executeSql(transactionManager.currentConnection().createStatement(), sql)
            }
        }
    }

    fun fillDatabase(pathToCsv: String, restartId: Long? = null) {
        logger.debug("filling DB from $pathToCsv")
        val tables = csvTableReader.read(pathToCsv)
        runBlocking {
            transactionManager.rwTransaction {
                val conn = transactionManager.currentConnection()
                tables.forEach { t ->
                    val stmt = conn.createStatement()
                    executeSql(stmt, "TRUNCATE TABLE ${t.name} CASCADE")
                    if (restartId != null) {
                        try {
                            executeSql(stmt, "ALTER SEQUENCE IF EXISTS ${t.name}_id_seq RESTART WITH $restartId")
                        } catch (ex: Exception) {
                            logger.error("Silently ignore: {}", ex.message)
                        }
                    }
                    if (t.rows.isNotEmpty()) {
                        val qry = StringBuilder(
                            "INSERT INTO ${t.name} (${t.columns.joinToString(",")}) VALUES\n"
                        )
                        t.rows.forEachIndexed { rowIndex, row ->
                            val values = row.mapIndexed { i, value ->
                                if (value == null) "null"
                                else when (tableMetadataService.getDataType(t.name, t.columns[i])) {
                                    DataType.TEXT, DataType.DATE, DataType.TIMESTAMP ->
                                        "'${value.toString().replace("'", "")}'"
                                    DataType.INT, DataType.DOUBLE, DataType.BOOLEAN -> value
                                    DataType.TS_VECTOR ->
                                        "to_tsvector('rus_fts_config', '${value.toString().replace("'", "")}')"
                                }.toString()
                            }.joinToString(", ")
                            qry.append("($values)")
                            if (rowIndex < t.rows.size - 1) qry.append(",\n")
                        }
                        executeSql(stmt, qry.toString())
                    }
                }
            }
        }
    }

    fun assert(pathToCsv: String) {
        logger.debug("asserting $pathToCsv")
        runBlocking {
            transactionManager.rwConnection {
                val conn = transactionManager.currentConnection()
                val stmt = conn.createStatement()
                val tables = csvTableReader.read(pathToCsv)
                tables.forEach { csvTable ->
                    val csvRows = csvTable.rows.map { it.joinToString(",") }
                    val dbRows = getDbRows(stmt, csvTable)
                    withClue("Table '${csvTable.name}' does not match csv '$pathToCsv'") {
                        dbRows shouldContainExactlyInAnyOrder csvRows
                    }
                }
            }
        }
    }

    fun getDbRows(pathToCsv: String): List<String> {
        val result = mutableListOf<String>()
        runBlocking {
            transactionManager.rwConnection {
                val tables = csvTableReader.read(pathToCsv)
                val stmt = transactionManager.currentConnection().createStatement()
                result.addAll(getDbRows(stmt, tables[0]))
            }
        }
        return result
    }

    private fun getDbRows(stmt: Statement, t: Table): List<String> {
        val resultSet = stmt.executeQuery(
            "SELECT ${t.columns.joinToString(", ")} FROM ${t.name}"
        )
        val rows = mutableListOf<String>()
        while (resultSet.next()) {
            rows.add(t.columns.map { col ->
                try {
                    resultSet.getObject(col)
                } catch (e: Exception) {
                    throw RuntimeException("unable to get ${t.name}.$col", e)
                }
            }.joinToString(","))
        }
        return rows
    }

    private fun executeSql(stmt: Statement, sql: String) {
        logger.debug("execute: $sql")
        stmt.executeUpdate(sql)
    }
}
