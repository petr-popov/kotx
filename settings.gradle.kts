pluginManagement {
    plugins {
        kotlin("jvm") version "2.3.0"
        kotlin("plugin.serialization") version "2.3.0"
    }
}

rootProject.name = "kotx"

include("core")
include("mybatis")
include("test")
include("dbq")
