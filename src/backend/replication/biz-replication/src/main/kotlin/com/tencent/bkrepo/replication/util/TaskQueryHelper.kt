package com.tencent.bkrepo.replication.util

import com.tencent.bkrepo.replication.model.TReplicaObject
import com.tencent.bkrepo.replication.model.TReplicaTask
import com.tencent.bkrepo.replication.pojo.record.ExecutionStatus
import com.tencent.bkrepo.replication.pojo.request.ReplicaType
import com.tencent.bkrepo.replication.pojo.task.ReplicaStatus
import com.tencent.bkrepo.replication.pojo.task.TaskSortType
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.and
import org.springframework.data.mongodb.core.query.inValues
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.where

/**
 * 任务查询条件构造工具
 */
object TaskQueryHelper {

    /**
     * 构造list查询条件
     */
    fun buildListQuery(
        projectId: String,
        name: String? = null,
        lastExecutionStatus: ExecutionStatus? = null,
        enabled: Boolean? = null,
        sortType: TaskSortType?
    ): Query {
        val criteria = where(TReplicaTask::projectId).isEqualTo(projectId)
        name?.takeIf { it.isNotBlank() }?.apply { criteria.and(TReplicaTask::name).regex("^$this") }
        lastExecutionStatus?.apply { criteria.and(TReplicaTask::lastExecutionStatus).isEqualTo(this) }
        enabled?.apply { criteria.and(TReplicaTask::enabled).isEqualTo(this) }
        return Query(criteria).with(Sort.by(Sort.Direction.DESC, sortType?.key))
    }

    fun undoScheduledTaskQuery(): Query {
        val criteria = Criteria.where(TReplicaTask::replicaType.name).`is`(ReplicaType.SCHEDULED)
            .and(TReplicaTask::status.name).`in`(ReplicaStatus.UNDO_STATUS)
            .and(TReplicaTask::enabled.name).`is`(true)
        return Query(criteria)
    }

    fun realTimeTaskQuery(taskKeyList: List<String>): Query {
        val criteria = where(TReplicaTask::replicaType).isEqualTo(ReplicaType.REAL_TIME)
            .and(TReplicaTask::key).inValues(taskKeyList)
            .and(TReplicaTask::enabled).isEqualTo(true)
        return Query(criteria)
    }

    fun taskObjectQuery(projectId: String, repoName: String): Query {
        val criteria = where(TReplicaObject::localProjectId).isEqualTo(projectId)
            .and(TReplicaObject::localRepoName).isEqualTo(repoName)
        return Query(criteria)
    }
}
