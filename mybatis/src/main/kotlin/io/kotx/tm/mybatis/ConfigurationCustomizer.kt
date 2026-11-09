package io.kotx.tm.mybatis

import org.apache.ibatis.session.Configuration

interface ConfigurationCustomizer {

    fun customize(config: Configuration)

}