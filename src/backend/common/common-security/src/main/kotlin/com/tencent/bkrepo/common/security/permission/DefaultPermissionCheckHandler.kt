package com.tencent.bkrepo.common.security.permission

import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.security.exception.PermissionException
import com.tencent.bkrepo.common.security.manager.PermissionManager
import com.tencent.bkrepo.repository.pojo.repo.RepositoryInfo

open class DefaultPermissionCheckHandler(
    private val permissionManager: PermissionManager
) : PermissionCheckHandler {

    override fun onPermissionCheck(userId: String, permission: Permission, repositoryInfo: RepositoryInfo, artifactInfo: ArtifactInfo?) {
        permissionManager.checkPermission(userId, permission.type, permission.action, repositoryInfo)
    }

    override fun onPrincipalCheck(userId: String, principal: Principal) {
        permissionManager.checkPrincipal(userId, principal.type)
    }

    override fun onPermissionCheckFailed(exception: PermissionException) {
        // 默认向上抛异常
        throw exception
    }

    override fun onPermissionCheckSuccess() {
        // do nothing
    }
}