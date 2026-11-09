package io.kotx.tm.test

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import io.kotx.tm.core.DatasourceFactory
import java.util.Properties
import javax.sql.DataSource

class EmbeddedPostgresDatasourceFactory : DatasourceFactory {

    val logger = LoggerFactory.getLogger(EmbeddedPostgresDatasourceFactory::class.java)

    override fun rwDatasource(name: String): DataSource {
        return getEmbeddedPostgres(name)
    }

    override fun roDatasource(name: String): DataSource {
        return getEmbeddedPostgres(name)
    }

    companion object {
        val dataSourcesByName: MutableMap<String, DataSource> = mutableMapOf()
        var needLiquibaseUpdate: Boolean = true
        
        // Создаем HTTP клиент для проверки эндпоинтов
        private val httpClient = HttpClient(CIO) {
            install(HttpTimeout) {
                connectTimeoutMillis = 500
                socketTimeoutMillis = 500
            }
        }
    }

    private fun getEmbeddedPostgres(name: String): DataSource {
        if (!dataSourcesByName.containsKey(name)) {
            val url = getPostgresUrl(name)
            val props = Properties()
            props.setProperty("jdbcUrl", url)
            props.setProperty("username", "test")
            props.setProperty("password", "test")
            props.setProperty("connectionInitSql", "SET default_text_search_config = 'russian';")
            dataSourcesByName.put(name, HikariDataSource(HikariConfig(props)))
            logger.info("JDBC_URL for '$name': $url")
        }
        return dataSourcesByName.get(name)!!
    }
    
    private fun getPostgresUrl(name: String): String {
        return runBlocking {
            try {
                // Сначала пытаемся получить URL из HTTP эндпоинта
                val response = httpClient.get("http://localhost:8013/pg/$name")
                if (response.status == HttpStatusCode.OK) {
                    val urlFromHttp = response.bodyAsText()
                    logger.info("Got PostgreSQL URL from HTTP endpoint for '$name': $urlFromHttp")
                    needLiquibaseUpdate = false
                    urlFromHttp
                } else {
                    logger.info("HTTP endpoint returned status ${response.status} for '$name', falling back to postgresStarter")
                    PostgresStarter.getPgUrl(name)
                }
            } catch (e: Exception) {
                logger.info("HTTP endpoint not accessible for '$name' (${e.message}), falling back to postgresStarter")
                PostgresStarter.getPgUrl(name)
            }
        }
    }
}