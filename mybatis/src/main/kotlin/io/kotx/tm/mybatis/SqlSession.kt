package io.kotx.tm.mybatis

import kotlinx.coroutines.currentCoroutineContext
import org.apache.ibatis.session.SqlSession
import io.kotx.tm.core.CoroutineTransaction
import kotlin.reflect.KClass

object SqlSession {
    suspend fun get(): SqlSession {
        if (currentCoroutineContext().get(CoroutineTransaction.Key) == null) {
            throw RuntimeException("called outside of TransactionManager.r*Transaction")
        }
        return (currentCoroutineContext().get(CoroutineTransaction.Key)!!.transaction as MybatisSession).session
    }

    suspend fun <T : Any> mapper(clazz: KClass<T>): T {
        return get().getMapper(clazz.java)
    }
}