package io.kotx.tm.mybatis

import io.kotx.tm.core.Transaction

class MybatisSession(val session: org.apache.ibatis.session.SqlSession) : Transaction {

    override fun commit(force: Boolean) {
        session.commit()
    }

    override fun rollback(force: Boolean) {
        session.rollback()
    }

    override fun close(force: Boolean) {
        session.close()
    }

    override fun connection(): java.sql.Connection = session.connection
}