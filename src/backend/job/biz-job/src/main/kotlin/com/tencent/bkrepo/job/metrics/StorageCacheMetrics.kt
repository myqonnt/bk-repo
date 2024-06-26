/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2024 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.job.metrics

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.BaseUnits
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

@Component
class StorageCacheMetrics(
    private val registry: MeterRegistry,
) {

    private val cacheSizeMap = ConcurrentHashMap<String, Long>()
    private val cacheCountMap = ConcurrentHashMap<String, Long>()

    /**
     * 设置当前缓存总大小，由于目前只有Job服务在清理缓存时会统计，因此只有Job服务会调用该方法
     */
    fun setCacheSize(storageKey: String, size: Long) {
        cacheSizeMap[storageKey] = size
        Gauge.builder(CACHE_SIZE, cacheSizeMap) { cacheSizeMap.getOrDefault(storageKey, 0L).toDouble() }
            .baseUnit(BaseUnits.BYTES)
            .tag(TAG_STORAGE_KEY, storageKey)
            .description("storage cache total size")
            .register(registry)
    }

    /**
     * 设置当前缓存总数，由于目前只有Job服务在清理缓存时会统计，因此只有Job服务会调用该方法
     */
    fun setCacheCount(storageKey: String, count: Long) {
        cacheCountMap[storageKey] = count
        Gauge.builder(CACHE_COUNT, cacheCountMap) { cacheCountMap.getOrDefault(storageKey, 0L).toDouble() }
            .tag(TAG_STORAGE_KEY, storageKey)
            .description("storage cache total count")
            .register(registry)
    }

    companion object {
        const val CACHE_SIZE = "storage.cache.size"
        const val CACHE_COUNT = "storage.cache.count"
        const val TAG_STORAGE_KEY = "storageKey"
    }
}
