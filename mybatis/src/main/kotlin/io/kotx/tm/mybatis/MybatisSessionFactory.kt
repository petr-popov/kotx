package io.kotx.tm.mybatis

import org.apache.ibatis.session.Configuration
import org.apache.ibatis.session.ExecutorType
import org.apache.ibatis.session.SqlSessionFactoryBuilder
import io.kotx.tm.core.Transaction
import io.kotx.tm.core.TransactionFactory

class MybatisSessionFactory(
    configurer: MybatisConfigurer,
) : TransactionFactory {

    val roConfiguration: Configuration
    val rwConfiguration: Configuration

    init {
        roConfiguration = configurer.roConfiguration()
        rwConfiguration = configurer.rwConfiguration()
    }

    override fun newRw(autoCommit: Boolean): Transaction {
        return MybatisSession(
            SqlSessionFactoryBuilder().build(rwConfiguration).openSession(autoCommit)
        )
    }

    override fun newRo(autoCommit: Boolean): Transaction {
        return MybatisSession(
            SqlSessionFactoryBuilder().build(roConfiguration).openSession(autoCommit)
        )
    }

    override fun newBatch(): Transaction {
        return MybatisSession(
            SqlSessionFactoryBuilder().build(rwConfiguration).openSession(ExecutorType.BATCH)
        )
    }
}