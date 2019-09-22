package com.tencent.bkrepo.common.mongo

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.mongodb.MongoDbFactory
import org.springframework.data.mongodb.MongoTransactionManager
import org.springframework.data.mongodb.config.EnableMongoAuditing

/**
 * mongodb 4.0+开始支持事物，但springboot data mongo为了兼容老版本不出错，默认不开启事物
 * 同时，mongodb 事物控制只能用与多副本集的情况，否则会报错，因此添加了enabled开关
 */
@Configuration
@EnableMongoAuditing
class MongoAutoConfiguration {

    @Bean
    @ConditionalOnProperty(value = ["spring.data.mongodb.transaction.enabled"], matchIfMissing = true)
    fun transactionManager(dbFactory: MongoDbFactory): MongoTransactionManager {
        return MongoTransactionManager(dbFactory)
    }
}
