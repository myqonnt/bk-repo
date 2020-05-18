package com.tencent.bkrepo.common.job

import com.mongodb.MongoClient
import net.javacrumbs.shedlock.core.LockProvider
import net.javacrumbs.shedlock.provider.mongo.MongoLockProvider
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock
import org.springframework.boot.autoconfigure.mongo.MongoProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.SchedulingConfigurer
import org.springframework.scheduling.config.ScheduledTaskRegistrar
import java.util.concurrent.Executors

/**
 *
 * @author: carrypan
 * @date: 2019/12/23
 */
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "60s", defaultLockAtLeastFor = "1s")
class JobAutoConfiguration {

    @Bean
    fun lockProvider(mongoClient: MongoClient, mongoProperties: MongoProperties): LockProvider {
        return MongoLockProvider(mongoClient, mongoProperties.mongoClientDatabase)
    }

    @Configuration
    class ScheduleConfig : SchedulingConfigurer {
        override fun configureTasks(taskRegistrar: ScheduledTaskRegistrar) {
            taskRegistrar.setScheduler(Executors.newScheduledThreadPool(5))
        }
    }
}