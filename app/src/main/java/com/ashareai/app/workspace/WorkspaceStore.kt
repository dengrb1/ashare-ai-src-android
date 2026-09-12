package com.ashareai.app.workspace

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.workspaceDataStore by preferencesDataStore(name = "workspace_settings")

/**
 * 工作区存储：管理当前工作区、各工作区上次路由和 Fusion 会话有效性。
 * 独立于本地和 Fusion 的各自 DataStore，只保存工作区切换状态。
 */
class WorkspaceStore(context: Context) {
    private val context = context.applicationContext

    companion object {
        private val KEY_CURRENT_WORKSPACE = stringPreferencesKey("current_workspace")
        private val KEY_LAST_LOCAL_ROUTE = stringPreferencesKey("last_local_route")
        private val KEY_LAST_FUSION_ROUTE = stringPreferencesKey("last_fusion_route")
        private val KEY_FUSION_SESSION_VALID = booleanPreferencesKey("fusion_session_valid")
    }

    val currentWorkspace: Flow<Workspace> = context.workspaceDataStore.data.map { preferences ->
        val stored = preferences[KEY_CURRENT_WORKSPACE]
        when (stored) {
            "FUSION" -> Workspace.FUSION
            else -> Workspace.LOCAL  // 默认本地工作区
        }
    }

    val workspaceState: Flow<WorkspaceState> = context.workspaceDataStore.data.map { preferences ->
        WorkspaceState(
            current = when (preferences[KEY_CURRENT_WORKSPACE]) {
                "FUSION" -> Workspace.FUSION
                else -> Workspace.LOCAL
            },
            lastLocalRoute = preferences[KEY_LAST_LOCAL_ROUTE],
            lastFusionRoute = preferences[KEY_LAST_FUSION_ROUTE],
            fusionSessionValid = preferences[KEY_FUSION_SESSION_VALID] ?: false,
        )
    }

    suspend fun setWorkspace(workspace: Workspace) {
        context.workspaceDataStore.edit { preferences ->
            preferences[KEY_CURRENT_WORKSPACE] = workspace.name
        }
    }

    suspend fun saveLastRoute(workspace: Workspace, route: String) {
        context.workspaceDataStore.edit { preferences ->
            when (workspace) {
                Workspace.LOCAL -> preferences[KEY_LAST_LOCAL_ROUTE] = route
                Workspace.FUSION -> preferences[KEY_LAST_FUSION_ROUTE] = route
            }
        }
    }

    suspend fun markFusionSessionValid(valid: Boolean) {
        context.workspaceDataStore.edit { preferences ->
            preferences[KEY_FUSION_SESSION_VALID] = valid
        }
    }

    suspend fun currentWorkspaceValue(): Workspace = currentWorkspace.first()
    suspend fun workspaceStateValue(): WorkspaceState = workspaceState.first()
}
