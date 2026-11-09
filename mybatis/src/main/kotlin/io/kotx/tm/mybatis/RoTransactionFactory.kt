package io.kotx.tm.mybatis

import org.apache.ibatis.session.TransactionIsolationLevel
import org.apache.ibatis.transaction.Transaction
import org.apache.ibatis.transaction.TransactionFactory
import java.sql.Connection
import javax.sql.DataSource

class RoTransactionFactory:TransactionFactory {
    override fun newTransaction(conn: Connection): Transaction {
        return RoTransaction(conn)
    }

    override fun newTransaction(
        dataSource: DataSource,
        level: TransactionIsolationLevel?,
        autoCommit: Boolean
    ): Transaction {
        return RoTransaction(dataSource.connection)
    }

}