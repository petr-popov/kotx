package io.kotx.tm.core

import javax.sql.DataSource

interface DatasourceFactory {

    fun rwDatasource(name: String): DataSource

    fun roDatasource(name: String): DataSource

}