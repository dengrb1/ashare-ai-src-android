package com.ashareai.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.ashareai.app.AShareApp
import com.ashareai.app.data.model.EdgeGatewayDraft
import com.ashareai.app.data.model.ModelSettingsDraft
import com.ashareai.app.data.model.RuntimeIdentityRequest
import com.ashareai.app.data.model.SystemSettingsUnlockRequest
import kotlinx.serialization.json.JsonObject

/** Permission-gated administrator API boundary. Secrets are sent only to the server. */
class AdminViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as AShareApp).container.administrationRepository

    suspend fun modelSettings() = repository.modelSettings()
    suspend fun saveModelSettings(request: ModelSettingsDraft) = repository.saveModelSettings(request)
    suspend fun testModelSettings(request: ModelSettingsDraft) = repository.testModelSettings(request)
    suspend fun listConfiguredModels(request: ModelSettingsDraft) = repository.listConfiguredModels(request)
    suspend fun modelProbeLogs(limit: Int = 50) = repository.modelProbeLogs(limit)
    suspend fun systemSettings() = repository.systemSettings()
    suspend fun systemResources() = repository.systemResources()
    suspend fun unlockSystemSettings(request: SystemSettingsUnlockRequest) = repository.unlockSystemSettings(request)
    suspend fun saveSystemSettings(token: String, body: JsonObject) = repository.saveSystemSettings(token, body)
    suspend fun restoreSystemSetting(field: String, token: String) = repository.restoreSystemSetting(field, token)
    suspend fun runtimeIdentity() = repository.runtimeIdentity()
    suspend fun saveRuntimeIdentity(token: String, request: RuntimeIdentityRequest) = repository.saveRuntimeIdentity(token, request)
    suspend fun energySaving() = repository.energySaving()
    suspend fun rearmEnergySaving() = repository.rearmEnergySaving()
    suspend fun wakeEnergySaving() = repository.wakeEnergySaving()
    suspend fun edgeGateway(token: String? = null) = repository.edgeGateway(token)
    suspend fun edgeGatewayLogs(limit: Int = 200) = repository.edgeGatewayLogs(limit)
    suspend fun validateEdgeGateway(request: EdgeGatewayDraft) = repository.validateEdgeGateway(request)
    suspend fun saveEdgeGateway(token: String, request: EdgeGatewayDraft) = repository.saveEdgeGateway(token, request)
    suspend fun rollbackEdgeGateway(token: String) = repository.rollbackEdgeGateway(token)
}
