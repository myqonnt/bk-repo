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

package com.tencent.bkrepo.common.operate.service.aop

import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.tencent.bkrepo.common.operate.api.OperateLogService
import com.tencent.bkrepo.common.operate.api.annotation.LogOperate
import com.tencent.bkrepo.common.operate.api.pojo.OperateLog
import com.tencent.bkrepo.common.operate.service.util.DesensitizedUtils.convertMethodArgsToMap
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.reflect.MethodSignature
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * 记录被[LogOperate]注解的类或方法操作日志
 */
@Component
@Aspect
class LogOperateAspect(private val operateLogService: OperateLogService) {
    @Volatile
    private var operateLogBuffer: MutableSet<OperateLog> = ConcurrentHashMap.newKeySet(LOG_BUFFER_SIZE)

    @Around(
        "@within(com.tencent.bkrepo.common.operate.api.annotation.LogOperate) " +
            "|| @annotation(com.tencent.bkrepo.common.operate.api.annotation.LogOperate)"
    )
    fun around(joinPoint: ProceedingJoinPoint): Any? {
        val signature = joinPoint.signature as MethodSignature
        val method = signature.method
        val annotation = method.getAnnotation(LogOperate::class.java)

        val args = joinPoint.args
        val methodName = "${method.declaringClass.name}#${method.name}"

        val operateType = annotation.type.ifEmpty { methodName }
        val desensitize = annotation.desensitize
        val userId = SecurityUtils.getUserId()
        val clientAddr = HttpContextHolder.getClientAddress()
        try {
            executor.execute {
                val descriptions = convertMethodArgsToMap(method, args, desensitize)
                saveOperateLog(operateType, userId, clientAddr, descriptions)
            }
        } catch (e: RejectedExecutionException) {
            logger.warn("user[$userId] invoke method[$methodName] logging failed.", e)
        }
        return joinPoint.proceed()
    }

    /**
     * 保存操作日志到[operateLogBuffer]中，[operateLogBuffer]会定时或者在容量已满时保存到数据库
     */
    @Suppress("UNCHECKED_CAST")
    fun saveOperateLog(type: String, userId: String, clientAddr: String, descriptions: Map<String, Any?>) {
        val operateLog = OperateLog(
            type = type,
            projectId = "",
            repoName = "",
            resourceKey = "",
            userId = userId,
            clientAddress = clientAddr,
            description = descriptions.filterValues { it != null } as Map<String, Any>
        )
        if (operateLogBuffer.size >= LOG_BUFFER_SIZE) {
            flush(false)
        }
        // 不加锁控制元素的添加，并发量较大时，允许buffer size少量超过限制的大小
        operateLogBuffer.add(operateLog)

        if (logger.isDebugEnabled) {
            logger.debug("save operate log[$operateLog]")
        }
    }

    /**
     * 将[operateLogBuffer]的数据保存到数据库
     *
     * @param force 是否[operateLogBuffer]未满时也强制保存到数据库
     */
    @Synchronized
    @Scheduled(fixedRate = FLUSH_LOG_RATE)
    fun flush(force: Boolean = true) {
        if (operateLogBuffer.isEmpty() || !force && operateLogBuffer.size < LOG_BUFFER_SIZE) {
            return
        }
        val current = operateLogBuffer
        operateLogBuffer = ConcurrentHashMap.newKeySet(LOG_BUFFER_SIZE)
        operateLogService.saveAsync(current)
        if (logger.isDebugEnabled) {
            logger.debug("save ${current.size} operate logs success.")
        }
    }

    companion object {
        private const val FLUSH_LOG_RATE = 60L * 1000L
        private const val LOG_BUFFER_SIZE = 1000
        private val logger = LoggerFactory.getLogger(LogOperateAspect::class.java)
        private val executor = ThreadPoolExecutor(
            0,
            Runtime.getRuntime().availableProcessors(),
            60,
            TimeUnit.SECONDS,
            LinkedBlockingQueue(1000),
            ThreadFactoryBuilder().setNameFormat("log-operate-%d").build(),
            ThreadPoolExecutor.AbortPolicy()
        )
    }
}
