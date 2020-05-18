package com.tencent.bkrepo.common.artifact.webhook

import com.tencent.bkrepo.common.artifact.event.ArtifactEventType

data class ArtifactWebHookData(
    val projectId: String,
    val repoName: String,
    val artifactName: String,
    val eventType: ArtifactEventType
)