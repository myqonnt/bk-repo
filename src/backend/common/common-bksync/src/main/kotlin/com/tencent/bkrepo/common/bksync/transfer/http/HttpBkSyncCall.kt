package com.tencent.bkrepo.common.bksync.transfer.http

import com.tencent.bkrepo.common.bksync.BkSync
import com.tencent.bkrepo.common.bksync.transfer.exception.PatchRequestException
import com.tencent.bkrepo.common.bksync.transfer.exception.SignRequestException
import com.tencent.bkrepo.common.api.util.HumanReadable
import com.tencent.bkrepo.common.api.util.executeAndMeasureNanoTime
import com.tencent.bkrepo.common.bksync.DiffResult
import kotlin.system.measureNanoTime
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import org.slf4j.LoggerFactory
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * bksync http实现
 * */
class HttpBkSyncCall(
    // http客户端
    private val client: OkHttpClient,
    // 重复率阈值，只有大于该阈值时才会使用增量上传
    private val reuseThreshold: Float = DEFAULT_THRESHOLD
) {
    private val logger = LoggerFactory.getLogger(HttpBkSyncCall::class.java)

    /**
     * 增量上传
     * 在重复率较低或者发生异常时，转为普通上传
     * */
    fun upload(request: UploadRequest) {
        try {
            val nanos = measureNanoTime { doUpload(request) }
            logger.info("Upload[${request.deltaUrl}] success,elapsed ${HumanReadable.time(nanos)}.")
        } catch (e: Exception) {
            logger.error(e.message, e)
            request.genericUrl?.let { commonUpload(request) }
        }
    }

    /**
     * 上传具体实现
     * 1. 请求sign
     * 2. 计算diff并且patch
     * */
    private fun doUpload(request: UploadRequest) {
        with(request) {
            // 请求sign
            logger.info("Request sign")
            val signStream = sign()
            signStream.buffered().use { patch(it) }
        }
    }

    /**
     * 获取sign数据流
     * */
    private fun UploadRequest.sign(): InputStream {
        val signRequest = Request.Builder()
            .url(signUrl)
            .headers(headers)
            .build()
        val response = client.newCall(signRequest).execute()
        if (!response.isSuccessful) {
            throw SignRequestException("Request sign error: ${response.message()}.")
        }
        return response.body()?.byteStream() ?: let {
            throw SignRequestException("Sign stream broken: ${response.message()}.")
        }
    }

    /**
     * 根据传人的sign数据流，进行diff计算，并且发起patch请求
     * */
    private fun UploadRequest.patch(signStream: InputStream) {
        val deltaFile = createTempFile()
        try {
            deltaFile.outputStream().buffered().use {
                logger.info("Detecting diff")
                val result = detecting(file, signStream, it)
                if (result.hitRate < reuseThreshold) {
                    logger.info(
                        "Current reuse hit rate[${result.hitRate}]" +
                            " less than threshold[$reuseThreshold],use common upload."
                    )
                    commonUpload(this)
                    return
                }
            }
            logger.info("Start upload delta file.")
            val body = RequestBody.create(MediaType.get(APPLICATION_OCTET_STREAM), deltaFile)
            val patchRequest = Request.Builder()
                .url(deltaUrl)
                .headers(headers)
                .header(X_BKREPO_OLD_FILE_PATH, oldFilePath)
                .patch(body)
                .build()
            val nanos = measureNanoTime {
                val patchResponse = client.newCall(patchRequest).execute()
                if (!patchResponse.isSuccessful) {
                    throw PatchRequestException("delta[$deltaUrl] upload failed: ${patchResponse.message()}.")
                }
                patchResponse.print()
            }
            logger.info("Delta data upload success,elapsed ${HumanReadable.time(nanos)}.")
        } finally {
            deltaFile.delete()
            logger.info("Delete temp deltaFile ${deltaFile.absolutePath} success.")
        }
    }

    /**
     * 检测文件增量
     * */
    private fun detecting(
        file: File,
        signInputStream: InputStream,
        deltaOutputStream: OutputStream
    ): DiffResult {
        with(HumanReadable) {
            val (result, nanos) = executeAndMeasureNanoTime {
                BkSync(BLOCK_SIZE).diff(file, signInputStream, deltaOutputStream)
            }
            val bytes = file.length()
            logger.info(
                "Detecting file[$file] success, " +
                    "size: ${size(bytes)}, elapse: ${time(nanos)}, average: ${throughput(bytes, nanos)}."
            )
            return result
        }
    }

    /**
     * 普通上传
     * */
    private fun commonUpload(request: UploadRequest) {
        with(request) {
            genericUrl ?: throw IllegalArgumentException("No genericUrl.")
            val body = RequestBody.create(MediaType.get(APPLICATION_OCTET_STREAM), file)
            val commonRequest = Request.Builder()
                .url(genericUrl!!)
                .put(body)
                .headers(headers)
                .build()
            val nanos = measureNanoTime {
                val response = client.newCall(commonRequest).execute()
                if (!response.isSuccessful) {
                    logger.info("Generic upload[$genericUrl] failed.")
                }
            }
            logger.info("Generic upload[$genericUrl] success, elapsed ${HumanReadable.time(nanos)}.")
        }
    }

    /**
     * 添加header
     * */
    private fun Request.Builder.headers(headers: Map<String, String>): Request.Builder {
        headers.forEach { (name, value) -> header(name, value) }
        return this
    }

    private fun Response.print() {
        this.body()?.byteStream()?.use {
            it.readBytes().let { res -> logger.info("response: ${String(res)}") }
        }
    }

    companion object {
        private const val APPLICATION_OCTET_STREAM = "application/octet-stream"
        private const val X_BKREPO_OLD_FILE_PATH = "X-BKREPO-OLD-FILE-PATH"
        private const val DEFAULT_THRESHOLD = 0.2f
        private const val BLOCK_SIZE = 2048
    }
}
