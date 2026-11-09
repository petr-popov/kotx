package io.kotx.tm.mybatis

import org.apache.ibatis.mapping.Environment
import org.apache.ibatis.session.Configuration
import org.apache.ibatis.transaction.TransactionFactory
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory
import io.kotx.tm.core.DatasourceFactory
import javax.sql.DataSource

class MybatisConfigurer(
    val datasourceFactory: DatasourceFactory,
    val dbName: String,
    val configCustomizers: List<ConfigurationCustomizer>
) {

    fun roConfiguration() = configure0(datasourceFactory.roDatasource(dbName), RoTransactionFactory())

    fun rwConfiguration() = configure0(datasourceFactory.rwDatasource(dbName), JdbcTransactionFactory())

    private fun configure0(ds: DataSource, transactionFactory: TransactionFactory): Configuration {

        val environment = Environment(
            "default",
            transactionFactory,
            ds
        )
        val config = Configuration(environment)
        config.logPrefix = "mybatis"
        configCustomizers.forEach {
            it.customize(config)
        }
        return config
    }

}