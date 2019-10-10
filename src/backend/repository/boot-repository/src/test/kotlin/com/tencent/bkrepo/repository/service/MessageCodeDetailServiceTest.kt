package com.tencent.bkrepo.repository.service

import com.tencent.bkrepo.common.api.enums.SystemModuleEnum
import com.tencent.bkrepo.repository.pojo.MessageCodeCreateRequest
import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.DisplayName
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

/**
 *
 * @author: carrypan
 * @date: 2019-10-09
 */
@DisplayName("节点服务测试")
@SpringBootTest
internal class MessageCodeDetailServiceTest @Autowired constructor(
        private val messageCodeDetailService: MessageCodeDetailService
) {

    @Test
    @DisplayName("创建消息码")
    @Disabled
    fun create() {
        messageCodeDetailService.create(MessageCodeCreateRequest( "2500001", SystemModuleEnum.COMMON, "系统内部繁忙，请稍后再试试", "系統內部繁忙，請稍後再試試", "System service busy, please try again later"))
    }
}