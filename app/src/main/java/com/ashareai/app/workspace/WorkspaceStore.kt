package com.ashareai.app.workspace

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.ashareai.app.data.SettingsStore as FusionSettingsStore
import com.ashareai.app.standalone.data.settings.SettingsStore as LocalSettingsStore

private val Context.workspaceDataStore by preferencesDataStore(name = "workspace_settings")

/**
 * 工作区存储：管理当前工作区、各工作区上次路由和 Fusion 会话有效性。
 * 独立于本地和 Fusion 的各自 DataStore，只保存工作区切换状态。
 */
class WorkspaceStore(context: Context) {
    private val context = context.applicationContext
    private val sharedDataStore = SharedDataStore(context)
    private val switchMutex = Mutex()
    private val _syncRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** Emitted after an explicit workspace switch; sync services may collect this signal. */
    val syncRequests: SharedFlow<Unit> = _syncRequests

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
        switchMutex.withLock {
            context.workspaceDataStore.edit { preferences ->
                preferences[KEY_CURRENT_WORKSPACE] = workspace.name
            }
            // Sync while holding the same lock so rapid taps cannot apply preferences
            // to the workspace that won the next write.
            syncToWorkspace()
        }
    }

    /**
     * 同步共享数据到当前工作区。
     * 在工作区切换时自动调用，将 SharedDataStore 中的共享设置应用到当前工作区。
     */
    suspend fun syncToWorkspace() {
        val settings = sharedDataStore.sharedSettings.first()
        val values = sharedDataStore.sharedValues.first()
        when (currentWorkspaceValue()) {
            Workspace.LOCAL -> LocalSettingsStore(context).also { target ->
                if (settings.shareTheme) target.setDarkMode(values.themeMode)
                if (settings.shareGlassEffect) target.setGlassEnabled(values.glassEffectEnabled)
                if (settings.shareFullAnimations) target.setFullAnimationsEnabled(values.fullAnimationsEnabled)
                if (settings.shareIsland) target.setIslandEnabled(values.islandEnabled)
            }
            Workspace.FUSION -> FusionSettingsStore(context).also { target ->
                if (settings.shareTheme) target.setDarkMode(values.themeMode)
                if (settings.shareGlassEffect) target.setGlassEnabled(values.glassEffectEnabled)
                if (settings.shareFullAnimations) target.setFullAnimationsEnabled(values.fullAnimationsEnabled)
                if (settings.shareIsland) target.setIslandEnabled(values.islandEnabled)
            }
        }
        _syncRequests.tryEmit(Unit)
    }

    /**
     * 获取共享数据存储实例
     */
    fun getSharedDataStore(): SharedDataStore = sharedDataStore

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
