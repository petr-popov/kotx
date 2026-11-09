package io.kotx.tm.test

import org.slf4j.LoggerFactory
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

object PostgresStarter {

    private val logger = LoggerFactory.getLogger(EmbeddedPostgresDatasourceFactory::class.java)

    private val pgUrlsByDatasourceName: MutableMap<String, String> = mutableMapOf()

    fun getPgUrl(dsName: String): String {
        if (!pgUrlsByDatasourceName.containsKey(dsName)) {
            val postgres = PostgreSQLContainer(
                DockerImageName.parse("pirson.gitlab.yandexcloud.net:5050/sark/sark-back/pg-with-rum:amd64")
                    .asCompatibleSubstituteFor("postgres")
            )
            logger.info("starting posgres '$dsName' ...")
            postgres.start()
            logger.info("postgres '$dsName' is up, creating ds...")
            pgUrlsByDatasourceName.put(dsName, postgres.getJdbcUrl())
            logger.info("STARTED PG for '$dsName': ${postgres.getJdbcUrl()}")
        }
        return pgUrlsByDatasourceName.get(dsName)!!
    }

}