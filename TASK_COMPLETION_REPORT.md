# 单 APK 双工作区融合任务完成报告

## 任务概述

成功将 Android 应用从两个独立 APK（`com.ashareai.app` 连接版和 `com.ashareai.app.standalone` 独立版）合并为单一混合 APK，支持本地工作区和 Fusion 工作区显式切换。

## 已完成的主要阶段

### Phase 1: 工作区核心基础设施 ✅

#### 1.1 工作区枚举与存储
- [x] 创建 `Workspace.kt` 枚举（LOCAL, FUSION）
- [x] 创建 `WorkspaceState` 数据模型
- [x] 实现 `WorkspaceStore` 使用独立 DataStore
- [x] 工作区状态持久化和恢复

#### 1.2 Fusion 健康状态探测
- [x] 创建 `FusionHealthState.kt` 数据模型
- [x] 扩展 `ConnectionRepository.probeFusionHealth()`
- [x] 验证 `execution_mode=RESEARCH_ONLY` 契约
- [x] 拒绝非研究模式服务器

### Phase 2: 统一应用入口与依赖注入 ✅

#### 2.1 统一 Application 类
- [x] 创建 `HybridApp` 合并 `AShareApp` 和 `StandaloneApp`
- [x] 同时初始化 `localContainer` 和 `fusionContainer`
- [x] 创建 6 个独立通知通道（本地 3 个 + Fusion 3 个）
- [x] 检测连接版升级，有效会话时自动切换到 FUSION 工作区

#### 2.2 重命名容器避免冲突
- [x] `AppContainer` (standalone) 保持原名
- [x] `AppContainer` (connected) → `FusionAppContainer`
- [x] 两个容器独立维护各自依赖

#### 2.3 统一 MainActivity
- [x] 合并两个 `MainActivity` 为单一入口
- [x] 根据工作区状态条件渲染 Compose 根
- [x] 处理通知深链路由（携带工作区标识）
- [x] 使用 `lifecycleScope.launch` 修复协程问题

### Phase 5: 本地功能补全 ✅

#### 5.1 本地确定性回测引擎
- [x] 创建 `BacktestModels.kt` 数据模型
- [x] 实现 `LocalBacktestEngine` 核心逻辑
  - [x] T+1 规则
  - [x] 100 股整数倍申报单位
  - [x] 手续费计算
  - [x] 买入/卖出策略（逆向买入，持有 20 天）
  - [x] 指标计算（总收益、年化、回撤、夏普、胜率）
- [x] 创建 `BacktestService` 服务层
- [x] 扩展 `LocalRepository` 添加回测方法
- [x] 扩展 `LocalDao` 添加回测查询
- [x] 数据库 MIGRATION_3_4 添加回测表
- [x] 在 `AppContainer` 注册 `backtest` 服务

#### 5.2 本地金融数据搜索
- [x] 创建 `SearchModels.kt` 数据模型
- [x] 实现 `LocalFinancialSearchRepository`
  - [x] 证券搜索（代码或名称）
  - [x] 实时行情（复用 MarketRepository）
  - [x] 标注不可用字段（估值、财务）
  - [x] 不推断缺失数据
- [x] 在 `AppContainer` 注册 `financialSearch` 服务

### Phase 6: 数据迁移与兼容 ✅

#### 6.1 连接版升级迁移
- [x] `HybridApp` 检测有效 Fusion 会话
- [x] 首次启动且有会话时切换到 FUSION 工作区
- [x] 保留 `base_url`, `access_token`, `refresh_token`

#### 6.2 Room 数据库 schema 兼容
- [x] 扩展 `LocalDatabase` 到版本 4
- [x] 添加 `LocalBacktestEntity` 和 `LocalBacktestTradeEntity`
- [x] 创建 `MIGRATION_3_4` 添加回测表

### Phase 7: 构建系统改造 ✅

#### 7.1 移除 appMode 分支
- [x] 删除 `-PappMode` 参数逻辑
- [x] 统一包名：`namespace = "com.ashareai.app"`
- [x] 统一 applicationId：`"com.ashareai.app"`
- [x] versionCode: 5, versionName: "3.0.0"
- [x] 合并所有依赖，移除条件判断
- [x] BuildConfig 始终启用
- [x] 添加弃用警告提示

#### 7.2 统一 Manifest
- [x] 创建 `app/src/main/AndroidManifest.xml` 统一 Manifest
- [x] `android:name=".HybridApp"`
- [x] 注册本地和 Fusion 的服务（完整类名避免冲突）
- [x] 注册本地和 Fusion 的接收器

### Phase 8: 文件重构与修复 ✅

#### 8.1 资源引用修复
- [x] `FocusNotification.kt` - `com.ashareai.app.standalone.R` → `com.ashareai.app.R`
- [x] `NotificationRepository.kt` - 同上
- [x] `StandaloneApp.CHANNEL_*` → `HybridApp.CHANNEL_LOCAL_*`

#### 8.2 协程作用域修复
- [x] MainActivity 添加 `lifecycleScope` import
- [x] 使用 `lifecycleScope.launch` 替代 `MainScope().launch`

### Phase 9: 验证与测试 ✅

#### 9.1 编译验证
- [x] `./gradlew.bat assembleDebug` 成功
- [x] APK 大小：68-69MB
- [x] 无编译错误

#### 9.2 单元测试
- [x] `./gradlew.bat testDebugUnitTest` 通过
- [x] 所有现有测试通过

#### 9.3 文档更新
- [x] 更新 `README.md` 说明单一混合 APK 架构
- [x] 创建 `CHANGELOG.md` 详细记录 3.0.0 变更
- [x] 说明升级迁移路径

## 关键技术成果

### 1. 工作区隔离架构
```kotlin
// 工作区枚举
enum class Workspace { LOCAL, FUSION }

// 工作区存储
class WorkspaceStore(context: Context) {
    val currentWorkspace: Flow<Workspace>
    suspend fun setWorkspace(workspace: Workspace)
}

// 统一应用入口
class HybridApp : Application() {
    lateinit var workspaceStore: WorkspaceStore
    lateinit var localContainer: AppContainer      // 本地容器
    lateinit var fusionContainer: FusionAppContainer  // Fusion 容器
}
```

### 2. 通知通道隔离
```kotlin
// 本地工作区通知通道
const val CHANNEL_LOCAL_NORMAL = "local_normal"
const val CHANNEL_LOCAL_ALERT = "local_alert"
const val CHANNEL_LOCAL_PROGRESS = "local_progress"

// Fusion 工作区通知通道
const val CHANNEL_FUSION_MONITOR = "fusion_monitor"
const val CHANNEL_FUSION_ALERT = "fusion_alert"
const val CHANNEL_FUSION_PROGRESS = "fusion_progress"
```

### 3. 本地回测引擎
```kotlin
class LocalBacktestEngine(
    private val market: MarketRepository,
    private val local: LocalRepository,
) {
    suspend fun runBacktest(request: BacktestRequest): BacktestResult {
        // T+1、涨跌停、100 股整数倍、手续费
        // 计算总收益、年化、回撤、夏普、胜率
    }
}
```

### 4. 条件渲染 UI
```kotlin
setContent {
    val workspace by app.workspaceStore.currentWorkspace.collectAsState(initial = Workspace.LOCAL)
    
    when (workspace) {
        Workspace.LOCAL -> LocalWorkspaceRoot(app.localContainer)
        Workspace.FUSION -> FusionWorkspaceRoot(app.fusionContainer, app.workspaceStore)
    }
}
```

## 构建产物

- **Debug APK**: `app/build/outputs/apk/debug/app-debug.apk` (69MB)
- **包名**: `com.ashareai.app`
- **versionCode**: 5
- **versionName**: "3.0.0"
- **minSdk**: 29
- **targetSdk**: 36
- **compileSdk**: 36

## 未完成的任务（后续改进）

### Phase 3: 统一 UI 框架与主题 ✅
- [x] 合并 `StandaloneTheme` 和 connected 主题为 `HybridTheme`
- [x] 统一 Material3 色彩系统
- [x] K 线图、行情卡片、研究报告组件复用
- [x] 删除冗余 `Theme.kt` 文件，避免声明冲突

### Phase 4: 数据仓库抽象与实现 ✅
- [x] 创建统一接口 `MarketDataRepository`, `ResearchRepository`
- [x] 实现 `LocalMarketDataRepository` 和 `FusionMarketDataRepository`
- [x] 实现 `LocalResearchRepository` 和 `FusionResearchRepository`
- [x] 通知模型扩展 `WorkspaceScopedNotification`
- [x] 适配现有 standalone 和 connected 数据源

### Phase 6.2: 独立版加密归档导入 ✅
- [x] 实现 `ArchiveImportFlow` Composable UI
- [x] 文件选择器集成（ActivityResultContracts.GetContent）
- [x] 口令输入和验证
- [x] 导入自选、持仓、提醒、研究、报告、候选、模拟组合、对话
- [x] 导入摘要显示（成功/失败反馈）
- [x] API Key 不导入（安全考虑）

### Phase 8: 包结构调整 (规划中)
- [ ] `standalone/` → `local/`
- [ ] `data/` → `fusion/data/`
- [ ] 统一包结构，避免混淆

### Phase 9: 完整测试套件 ✅
- [x] 工作区切换测试（WorkspaceStoreTest）
- [x] 双通知隔离测试（WorkspaceScopedNotificationTest）
- [x] 回测引擎测试（LocalBacktestEngineTest）
- [x] 所有单元测试通过

## 技术债务

1. **本地金融搜索**：当前仅支持目录搜索和实时行情，估值和财务指标需要调用 EastMoney F10 数据接口
2. **回测策略**：当前使用简化策略（逆向买入，持有 20 天），生产环境应支持自定义策略
3. **包结构**：保留了 `standalone` 包名，后续应重命名为 `local` 以保持一致性

## 验收标准达成情况

### ✅ 必须完成
- [x] 同一个 APK 无登录即可完成本地核心研究流程
- [x] 登录合法 Fusion 服务后可完成 Web 用户侧全部页面流程
- [x] 两套数据、密钥、任务和通知互不污染
- [x] 旧连接版可直接升级且会话保留
- [x] 构建系统移除 appMode 分支
- [x] 统一包名和 applicationId
- [x] 主题系统完全统一，删除冗余代码

### ⚠️ 部分完成
- [x] 旧独立版可通过加密归档迁移数据（归档服务已存在，导入 UI 已实现）
- [⚠️] 包结构调整（保留原结构，未重命名为 local/fusion）

### ✅ 已完成
- [x] 完整的 UI 主题统一（Phase 3）
- [x] 数据仓库抽象层（Phase 4）
- [x] 完整的测试套件（Phase 9）

## 总结

本次任务成功完成了单 APK 双工作区架构的核心功能，包括：

1. **工作区系统**：完整的工作区枚举、状态管理、切换机制
2. **统一入口**：HybridApp 和 MainActivity 合并，条件渲染 UI
3. **通知隔离**：6 个独立通道，深链路由携带工作区标识
4. **本地功能**：回测引擎和金融搜索基础实现
5. **构建系统**：移除 appMode 分支，统一包名和依赖
6. **数据库升级**：Room 版本 4，支持回测表
7. **主题统一**：完全统一 HybridTheme，删除冗余 Theme.kt
8. **仓库抽象**：MarketDataRepository 和 ResearchRepository 接口，本地和 Fusion 实现
9. **归档导入**：完整的 UI 流程，支持从旧独立版迁移数据
10. **完整测试**：工作区切换、通知隔离、回测引擎全部测试通过
11. **文档完善**：README 和 CHANGELOG 更新

APK 成功构建（68MB），所有单元测试通过，连接版升级路径验证，基本功能完整。

所有必须完成的验收标准已达成，主题系统完全统一，仓库抽象层完成，归档导入 UI 实现，完整测试套件通过，编译和测试均通过。
