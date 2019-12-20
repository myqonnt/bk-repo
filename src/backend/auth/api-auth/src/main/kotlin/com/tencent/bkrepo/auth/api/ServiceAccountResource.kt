package com.tencent.bkrepo.auth.api

import com.tencent.bkrepo.auth.constant.SERVICE_NAME
import com.tencent.bkrepo.auth.pojo.Account
import com.tencent.bkrepo.auth.pojo.CreateAccountRequest
import com.tencent.bkrepo.auth.pojo.CredentialSet
import com.tencent.bkrepo.auth.pojo.enums.CredentialStatus
import com.tencent.bkrepo.common.api.pojo.Response
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.*


@Api(tags = ["SERVICE_ACCOUNT"], description = "服务-账号接口")
@FeignClient(SERVICE_NAME, contextId = "ServiceAccountResource")
@RequestMapping("/account")
interface ServiceAccountResource {

    @ApiOperation("查询所有账号账号")
    @PostMapping("/list")
    fun listAccount(
    ): Response<List<Account>>

    @ApiOperation("创建账号")
    @PostMapping("/create")
    fun createAccount(
        @RequestBody request: CreateAccountRequest
    ): Response<Boolean>

    @ApiOperation("更新账号状态账号")
    @PutMapping("/{account}/{locked}")
    fun updateAccount(
        @ApiParam(value = "账户id")
        @PathVariable account: String,
        @ApiParam(value = "账户id")
        @PathVariable locked: Boolean
    ): Response<Boolean>

    @ApiOperation("删除账号")
    @DeleteMapping("/{account}")
    fun deleteAccount(
        @ApiParam(value = "账户id")
        @PathVariable account: String
    ): Response<Boolean>

    @ApiOperation("获取账户下的ak/sk对")
    @GetMapping("/credential/list/{account}")
    fun getCredential(
        @ApiParam(value = "账户id")
        @PathVariable account: String
    ): Response<List<CredentialSet>>

    @ApiOperation("创建ak/sk对")
    @PostMapping("/credential/{account}")
    fun createCredential(
        @ApiParam(value = "账户id")
        @PathVariable account: String
    ): Response<Boolean>


    @ApiOperation("删除ak/sk对")
    @DeleteMapping("/credential/{account}/{accesskey}")
    fun deleteCredential(
        @ApiParam(value = "账户id")
        @PathVariable account: String,
        @ApiParam(value = "账户id")
        @PathVariable accesskey: String
    ): Response<Boolean>

    @ApiOperation("更新ak/sk对状态")
    @PutMapping("/credential/{account}/{accesskey}/{status}")
    fun updateCredential(
        @ApiParam(value = "账户id")
        @PathVariable account: String,
        @ApiParam(value = "accesskey")
        @PathVariable accesskey: String,
        @ApiParam(value = "status")
        @PathVariable status: CredentialStatus
    ): Response<Boolean>

    @ApiOperation("校验ak/sk")
    @GetMapping("/credential/{accesskey}/{secretkey}")
    fun checkCredential(
        @ApiParam(value = "accesskey")
        @PathVariable accesskey: String,
        @ApiParam(value = "secretkey")
        @PathVariable secretkey: String
    ): Response<Boolean>
}