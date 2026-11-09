package io.kotx.tm.test

object DataTypeMapper {

    private val mapping: Map<String, DataType> = mapOf(
        "timestamp without time zone" to DataType.TIMESTAMP,
        "timestamp with time zone" to DataType.TIMESTAMP,
        "boolean" to DataType.BOOLEAN,
        "character varying" to DataType.TEXT,
        "text" to DataType.TEXT,
        "integer" to DataType.INT,
        "smallint" to DataType.INT,
        "USER-DEFINED" to DataType.TEXT,
        "bigint" to DataType.INT,
        "uuid" to DataType.TEXT,
        "tsvector" to DataType.TS_VECTOR,
        "numeric" to DataType.TEXT,
        "double precision" to DataType.DOUBLE,
        "date" to DataType.DATE,
        "jsonb" to DataType.TEXT
    )

    fun stringToDataType(value: String): DataType =
        mapping.getOrElse(value) { throw RuntimeException("no data type for '$value'") }
}
