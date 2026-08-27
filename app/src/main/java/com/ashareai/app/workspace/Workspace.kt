package com.ashareai.app.workspace

/**
 * 工作区枚举：本地工作区与 Fusion 工作区完全隔离，包括数据、密钥、任务和通知。
 */
enum class Workspace {
    /** 本地工作区：无需登录，使用 Room、EastMoney、本地研究引擎、AI Provider 和本地任务。*/
    LOCAL,

    /** Fusion 工作区：连接 FastAPI 服务，使用服务器 `/api/v1` 全部用户侧能力。*/
    FUSION
}

/**
 * 工作区状态：保存当前工作区、各工作区的上次路由和 Fusion 会话有效性。
 */
data class WorkspaceState(
    val current: Workspace,
    val lastLocalRoute: String?,
    val lastFusionRoute: String?,
    val fusionSessionValid: Boolean,
)
