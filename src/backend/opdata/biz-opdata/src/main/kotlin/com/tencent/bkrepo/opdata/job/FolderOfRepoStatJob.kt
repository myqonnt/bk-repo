/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bkrepo.opdata.job

import com.tencent.bkrepo.common.artifact.path.PathUtils.UNIX_SEPARATOR
import com.tencent.bkrepo.common.artifact.path.PathUtils.isRoot
import com.tencent.bkrepo.common.artifact.path.PathUtils.normalizeFullPath
import com.tencent.bkrepo.common.artifact.path.PathUtils.resolveFirstLevelFolder
import com.tencent.bkrepo.common.service.log.LoggerHolder
import com.tencent.bkrepo.opdata.config.OpFolderStatJobProperties
import com.tencent.bkrepo.opdata.job.pojo.FolderMetric
import com.tencent.bkrepo.opdata.model.RepoModel
import com.tencent.bkrepo.opdata.model.TFolderMetrics
import com.tencent.bkrepo.opdata.repository.FolderMetricsRepository
import com.tencent.bkrepo.repository.constant.SHARDING_COUNT
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.bson.types.ObjectId
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Future
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 *统计仓库下一级目录数据
 */
@Component
class FolderOfRepoStatJob(
    private val repoModel: RepoModel,
    private val folderMetricsRepository: FolderMetricsRepository,
    private val mongoTemplate: MongoTemplate,
    private val opJobProperties: OpFolderStatJobProperties
) : BaseJob(mongoTemplate) {

    private var executor: ThreadPoolExecutor? = null

    @Scheduled(cron = "00 00 15 * * ?")
    @SchedulerLock(name = "FolderStatJob", lockAtMostFor = "PT10H")
    fun statFolderSize() {
        if (!opJobProperties.enabled) {
            logger.info("The job of folder stat is disabled.")
            return
        }
        logger.info("start to stat folder metrics")
        val folderMetricsList = mutableListOf<TFolderMetrics>()

        for (i in 0 until SHARDING_COUNT) {
            folderMetricsList.addAll(stat(i))
        }

        // 数据写入mongodb统计表
        folderMetricsRepository.deleteAll()
        logger.info("start to insert folder's metrics ")
        folderMetricsRepository.insert(folderMetricsList)
        logger.info("stat folder metrics done")
    }

    private fun stat(shardingIndex: Int): List<TFolderMetrics> {
        val lastId = AtomicReference<String>()
        val startIds = LinkedBlockingQueue<String>(DEFAULT_ID_QUEUE_SIZE)
        val collectionName = collectionName(shardingIndex)

        val folderMetricsList = if (submitId(lastId, startIds, collectionName, opJobProperties.batchSize)) {
            doStat(lastId, startIds, collectionName)
        } else {
            emptyList()
        }.ifEmpty { return emptyList() }
        return folderMetricsList.map { folderMetrics ->
            val credentialsKey = repoModel.getRepoInfo(folderMetrics.projectId, folderMetrics.repoName)
                ?.credentialsKey ?: "default"
            TFolderMetrics(
                folderMetrics.projectId, folderMetrics.repoName, credentialsKey,
                folderMetrics.path, folderMetrics.nodeNum.toLong(), folderMetrics.capSize.toLong()
            )
        }
    }

    @Suppress("LoopWithTooManyJumpStatements")
    private fun doStat(
        lastId: AtomicReference<String>,
        startIds: LinkedBlockingQueue<String>,
        nodeCollectionName: String
    ): List<FolderMetric> {
        refreshExecutor()
        // 用于日志输出
        val totalCount = AtomicLong()
        val livedNodeCount = AtomicLong()
        val start = System.currentTimeMillis()

        // 统计数据
        val folderMetrics = ConcurrentHashMap<String, FolderMetric>()
        val futures = ArrayList<Future<*>>()

        while (true) {
            val startId = startIds.poll(1, TimeUnit.SECONDS)
                ?: // lastId为null表示id遍历提交未结束，等待新id入队
                if (lastId.get() == null) {
                    continue
                } else {
                    break
                }

            val future = executor!!.submit {
                val query = Query(Criteria.where(FIELD_NAME_ID).gte(ObjectId(startId)))
                    .with(Sort.by(FIELD_NAME_ID))
                    .limit(opJobProperties.batchSize)
                query.fields().include(
                    NodeDetail::projectId.name, NodeDetail::repoName.name, NodeDetail::size.name,
                    NodeDetail::folder.name, NodeDetail::path.name, NodeDetail::fullPath.name, FIELD_NAME_DELETED,
                )
                val nodes = mongoTemplate.find(query, Map::class.java, nodeCollectionName)
                nodes.forEach {
                    val deleted = it[FIELD_NAME_DELETED]
                    totalCount.incrementAndGet()
                    if (deleted == null) {
                        val projectId = it[NodeDetail::projectId.name].toString()
                        val repoName = it[NodeDetail::repoName.name].toString()
                        val path = it[NodeDetail::path.name].toString()
                        val isFolder = it[NodeDetail::folder.name].toString().toBoolean()
                        val size = try {
                            it[NodeDetail::size.name].toString().toLong()
                        } catch (e: NumberFormatException) {
                            0
                        }
                        val fullPath = it[NodeDetail::fullPath.name].toString()
                        livedNodeCount.incrementAndGet()
                        val key: String = if (isRoot(path)) {
                            if (isFolder) {
                                fullPath
                            } else {
                                UNIX_SEPARATOR.toString()
                            }
                        } else {
                            resolveFirstLevelFolder(normalizeFullPath(path))
                        }
                        folderMetrics.getOrPut(FOLDER_KEY_FORMAT.format(projectId, repoName, key)) {
                            FolderMetric(projectId, repoName, key)
                        }.apply {
                            nodeNum.increment()
                            capSize.add(size)
                        }
                    }
                }
            }
            futures.add(future)
        }

        // 等待所有任务结束
        futures.forEach { it.get() }

        // 输出结果
        val elapsed = System.currentTimeMillis() - start
        logger.info(
            "process $nodeCollectionName finished, elapsed[$elapsed ms]" +
                "totalNodeCount[${totalCount.toLong()}, lived[${livedNodeCount.get()}]]"
        )
        return folderMetrics.values.toList()
    }

    private fun collectionName(shardingIndex: Int): String = "${TABLE_PREFIX}$shardingIndex"

    @Synchronized
    private fun refreshExecutor() {
        if (executor == null) {
            executor = ThreadPoolExecutor(
                opJobProperties.threadPoolSize,
                opJobProperties.threadPoolSize,
                DEFAULT_THREAD_KEEP_ALIVE_SECONDS, TimeUnit.SECONDS,
                LinkedBlockingQueue(DEFAULT_THREAD_POOL_QUEUE_CAPACITY),
                ThreadPoolExecutor.CallerRunsPolicy()
            )
            executor!!.allowCoreThreadTimeOut(true)
        } else if (executor!!.maximumPoolSize != opJobProperties.threadPoolSize) {
            executor!!.corePoolSize = opJobProperties.threadPoolSize
            executor!!.maximumPoolSize = opJobProperties.threadPoolSize
        }
    }

    companion object {
        private val logger = LoggerHolder.jobLogger
        private const val TABLE_PREFIX = "node_"
        private const val FIELD_NAME_DELETED = "deleted"
        private const val DEFAULT_ID_QUEUE_SIZE = 10000
        private const val DEFAULT_THREAD_KEEP_ALIVE_SECONDS = 60L
        private const val DEFAULT_THREAD_POOL_QUEUE_CAPACITY = 1000
        private const val FOLDER_KEY_FORMAT = "%s|%s|%s"
    }
}
