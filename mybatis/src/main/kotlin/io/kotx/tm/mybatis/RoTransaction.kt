package io.kotx.tm.mybatis

import org.apache.ibatis.transaction.Transaction
import java.sql.Connection

class RoTransaction(val conn: Connection) :Transaction {
    override fun getConnection(): Connection {
        return conn
    }

    override fun commit() {

    }

    override fun rollback() {

    }

    override fun close() {
        if (!conn.isClosed) {
            conn.close()
        }
    }

    override fun getTimeout(): Int? {
        return null
    }
}