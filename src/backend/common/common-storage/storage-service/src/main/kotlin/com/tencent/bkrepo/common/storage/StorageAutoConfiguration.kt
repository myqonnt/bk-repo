package com.tencent.bkrepo.common.storage

import com.tencent.bkrepo.common.storage.core.FileStorage
import com.tencent.bkrepo.common.storage.core.StorageProperties
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.common.storage.core.cache.CacheStorageService
import com.tencent.bkrepo.common.storage.core.simple.SimpleStorageService
import com.tencent.bkrepo.common.storage.credentials.StorageType
import com.tencent.bkrepo.common.storage.filesystem.FileSystemStorage
import com.tencent.bkrepo.common.storage.innercos.InnerCosFileStorage
import com.tencent.bkrepo.common.storage.locator.FileLocator
import com.tencent.bkrepo.common.storage.locator.HashFileLocator
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync

/**
 * 存储自动配置
 *
 * @author: carrypan
 * @date: 2019-09-17
 */
@Configuration
@EnableAsync
@EnableConfigurationProperties(StorageProperties::class)
class StorageAutoConfiguration {

    @Bean
    fun fileStorage(properties: StorageProperties): FileStorage {
        val fileStorage = when (properties.type) {
            StorageType.FILESYSTEM -> FileSystemStorage()
            StorageType.INNERCOS -> InnerCosFileStorage()
            else -> FileSystemStorage()
        }
        logger.info("Initializing FileStorage[${fileStorage::class.simpleName}]")
        return fileStorage
    }

    @Bean
    fun storageService(properties: StorageProperties): StorageService {
        val storageService = if (properties.cache.enabled) CacheStorageService() else SimpleStorageService()
        logger.info("Initializing StorageService[${storageService::class.simpleName}].")
        return storageService
    }

    @Bean
    @ConditionalOnMissingBean(FileLocator::class)
    fun fileLocator() = HashFileLocator()

    companion object {
        private val logger = LoggerFactory.getLogger(StorageAutoConfiguration::class.java)
    }
}
