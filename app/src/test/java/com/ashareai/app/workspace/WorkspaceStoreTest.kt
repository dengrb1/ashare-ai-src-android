package com.ashareai.app.workspace

import org.junit.Assert.*
import org.junit.Test

/**
 * 工作区模型测试。
 *
 * 验证工作区枚举和状态模型。
 */
class WorkspaceStoreTest {

    @Test
    fun workspace_hasLocalAndFusion() {
        val workspaces = Workspace.values()
        assertTrue(workspaces.contains(Workspace.LOCAL))
        assertTrue(workspaces.contains(Workspace.FUSION))
        assertEquals(2, workspaces.size)
    }

    @Test
    fun workspaceState_defaultValues() {
        val state = WorkspaceState(
            current = Workspace.LOCAL,
            lastLocalRoute = null,
            lastFusionRoute = null,
            fusionSessionValid = false,
        )

        assertEquals(Workspace.LOCAL, state.current)
        assertNull(state.lastLocalRoute)
        assertNull(state.lastFusionRoute)
        assertFalse(state.fusionSessionValid)
    }

    @Test
    fun workspaceState_withLocalRoute() {
        val state = WorkspaceState(
            current = Workspace.LOCAL,
            lastLocalRoute = "local/reports",
            lastFusionRoute = null,
            fusionSessionValid = false,
        )

        assertEquals("local/reports", state.lastLocalRoute)
    }

    @Test
    fun workspaceState_withFusionRoute() {
        val state = WorkspaceState(
            current = Workspace.FUSION,
            lastLocalRoute = null,
            lastFusionRoute = "fusion/home",
            fusionSessionValid = true,
        )

        assertEquals("fusion/home", state.lastFusionRoute)
        assertTrue(state.fusionSessionValid)
    }

    @Test
    fun workspaceState_switchBetweenWorkspaces() {
        val localState = WorkspaceState(
            current = Workspace.LOCAL,
            lastLocalRoute = "local/overview",
            lastFusionRoute = null,
            fusionSessionValid = false,
        )

        val fusionState = localState.copy(
            current = Workspace.FUSION,
            lastFusionRoute = "fusion/home",
            fusionSessionValid = true,
        )

        assertEquals(Workspace.LOCAL, localState.current)
        assertEquals(Workspace.FUSION, fusionState.current)
        // 验证路由保留
        assertEquals("local/overview", fusionState.lastLocalRoute)
        assertEquals("fusion/home", fusionState.lastFusionRoute)
    }

    @Test
    fun workspaceState_preservesRoutesAfterSwitch() {
        val state = WorkspaceState(
            current = Workspace.FUSION,
            lastLocalRoute = "local/candidates",
            lastFusionRoute = "fusion/reports",
            fusionSessionValid = true,
        )

        // 切换回本地工作区
        val switchedState = state.copy(current = Workspace.LOCAL)

        // 验证路由都保留
        assertEquals(Workspace.LOCAL, switchedState.current)
        assertEquals("local/candidates", switchedState.lastLocalRoute)
        assertEquals("fusion/reports", switchedState.lastFusionRoute)
        assertTrue(switchedState.fusionSessionValid)
    }

    @Test
    fun workspace_enumEquality() {
        val local1 = Workspace.LOCAL
        val local2 = Workspace.LOCAL
        val fusion = Workspace.FUSION

        assertEquals(local1, local2)
        assertNotEquals(local1, fusion)
    }

    @Test
    fun workspaceState_sessionValidation() {
        val invalidSession = WorkspaceState(
            current = Workspace.FUSION,
            lastLocalRoute = null,
            lastFusionRoute = "fusion/home",
            fusionSessionValid = false,
        )

        val validSession = invalidSession.copy(fusionSessionValid = true)

        assertFalse(invalidSession.fusionSessionValid)
        assertTrue(validSession.fusionSessionValid)
    }
}
