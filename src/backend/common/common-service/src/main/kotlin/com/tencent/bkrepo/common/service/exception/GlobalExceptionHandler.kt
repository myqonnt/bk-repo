package com.tencent.bkrepo.common.service.exception

import com.fasterxml.jackson.module.kotlin.MissingKotlinParameterException
import com.netflix.client.ClientException
import com.netflix.hystrix.exception.HystrixRuntimeException
import com.netflix.hystrix.exception.HystrixRuntimeException.FailureType
import com.tencent.bkrepo.common.api.constant.ANONYMOUS_USER
import com.tencent.bkrepo.common.api.constant.USER_KEY
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.exception.ExternalErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.message.MessageCode
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.common.service.util.LocaleMessageUtils
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * 统一异常处理
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(ExternalErrorCodeException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleException(exception: ExternalErrorCodeException): Response<Void> {
        logException(exception, "[${exception.methodKey}][${exception.errorCode}]${exception.errorMessage}")
        return Response.fail(exception.errorCode, exception.errorMessage ?: "")
    }

    @ExceptionHandler(ErrorCodeException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleException(exception: ErrorCodeException): Response<Void> {
        val errorMessage = LocaleMessageUtils.getLocalizedMessage(exception.messageCode, exception.params)
        logException(exception, "[${exception.messageCode.getCode()}]$errorMessage")
        return Response.fail(exception.messageCode.getCode(), errorMessage)
    }

    @ExceptionHandler(MissingKotlinParameterException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleException(exception: MissingKotlinParameterException): Response<Void> {
        val messageCode = CommonMessageCode.PARAMETER_MISSING
        val errorMessage = LocaleMessageUtils.getLocalizedMessage(messageCode, arrayOf(exception.parameter.name ?: ""))
        logException(exception, "[${messageCode.getCode()}]$errorMessage")
        return Response.fail(messageCode.getCode(), errorMessage)
    }

    @ExceptionHandler(HystrixRuntimeException::class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    fun handleException(exception: HystrixRuntimeException): Response<Void> {
        var causeMessage = exception.cause?.message
        var messageCode = CommonMessageCode.SERVICE_CALL_ERROR
        if (exception.failureType == FailureType.COMMAND_EXCEPTION) {
            if (exception.cause?.cause is ClientException) {
                causeMessage = (exception.cause?.cause as ClientException).errorMessage
            }
        } else if (exception.failureType == FailureType.SHORTCIRCUIT) {
            messageCode = CommonMessageCode.SERVICE_CIRCUIT_BREAKER
        }
        logException(exception, "[${exception.failureType}]${exception.message} Cause: $causeMessage", Level.ERROR)
        return response(messageCode)
    }

    @ExceptionHandler(Exception::class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    fun handleException(exception: Exception): Response<Void> {
        logger.error("${exception.javaClass.simpleName}: ${exception.message}", exception)
        logException(exception, exception.message, Level.ERROR)
        return response(CommonMessageCode.SYSTEM_ERROR)
    }

    private fun logException(exception: Exception, message: String? = null, level: Level = Level.WARN) {
        val userId = HttpContextHolder.getRequest().getAttribute(USER_KEY) ?: ANONYMOUS_USER
        val uri = HttpContextHolder.getRequest().requestURI
        val exceptionMessage = message ?: exception.message
        val fullMessage = "User[$userId] access [$uri] failed[${exception.javaClass.simpleName}]: $exceptionMessage"
        when(level) {
            Level.ERROR -> message?.run { logger.error(fullMessage) } ?: run {logger.error(fullMessage, exception)}
            else -> logger.warn(fullMessage)
        }
    }

    private fun response(messageCode: MessageCode): Response<Void> {
        val errorMessage = LocaleMessageUtils.getLocalizedMessage(messageCode, null)
        return Response.fail(messageCode.getCode(), errorMessage)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)
    }
}
