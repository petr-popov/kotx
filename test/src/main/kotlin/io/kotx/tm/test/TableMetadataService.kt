package io.kotx.tm.test

import kotlinx.coroutines.runBlocking
import io.kotx.tm.core.TransactionManager

class TableMetadataService(
    transactionSupport: TransactionManager,
    dbSchemaName: String
) {
    private val tableToMap: MutableMap<String, MutableMap<String, DataType>> = HashMap()

    init {
        runBlocking {
            transactionSupport.roTransaction {
                val result = transactionSupport.currentConnection().createStatement().executeQuery(
                    "select table_name, column_name, data_type " +
                        "from information_schema.columns " +
                        "where table_schema = '$dbSchemaName'"
                )
                while (result.next()) {
                    val table = result.getString("table_name")
                    val column = result.getString("column_name")
                    val type = result.getString("data_type")
                    tableToMap.computeIfAbsent(table) { HashMap() }[column] =
                        DataTypeMapper.stringToDataType(type)
                }
            }
        }
    }

    fun getDataType(tableName: String, column: String): DataType {
        val table = tableToMap[tableName] ?: throw RuntimeException("missing table '$tableName' in db")
        return table[column] ?: throw RuntimeException("missing column '$column' in '$tableName'")
    }
}
