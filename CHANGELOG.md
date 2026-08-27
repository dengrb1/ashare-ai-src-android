# Changelog

## [3.0.0] - 2026-08-27

### 重大变更：单 APK 双工作区架构

将原 `com.ashareai.app` (connected) 和 `com.ashareai.app.standalone` (standalone) 两个独立 APK 合并为单一混合 APK，支持本地工作区和 Fusion 工作区显式切换。

### 新增功能

#### 工作区系统
- **本地工作区**：无需登录，Room 数据库 + EastMoney 行情 + 本地研究引擎 + AI Provider
- **Fusion 工作区**：连接 FastAPI 服务器，使用 `/api/v1` 全部用户侧能力
- 工作区状态持久化到独立 DataStore
- 工作区切换入口（本地设置页 / Fusion 个人页）
- 通知深链路由携带工作区标识，点击自动切换

#### 本地回测引擎
- 确定性历史模拟：基于本地缓存 K 线和研究报告
- 遵守 A 股规则：T+1、涨跌停、停复牌、申报单位（100 股整数倍）
- 手续费模型：买入/卖出各收取固定比例
- 回测指标：总收益、年化收益、最大回撤、夏普比率、胜率
- 持久化交易记录和每日收益

#### 本地金融搜索
- 证券搜索（代码或名称）
- 实时行情（复用 MarketRepository）
- 标注不可用字段（估值、财务暂不支持）
- 不推断缺失数据，保证数据完整性

#### 通知隔离
- 6 个独立通知通道：
  - 本地：`local_normal`, `local_alert`, `local_progress`
  - Fusion：`fusion_monitor`, `fusion_alert`, `fusion_progress`
- 通知来源明确标识
- 深链路由包含工作区前缀（`local/` 或 `fusion/`）

### 架构改进

#### 统一应用入口
- `HybridApp` 替代 `AShareApp` 和 `StandaloneApp`
- 同时初始化本地和 Fusion 容器
- 统一 `MainActivity`，根据工作区状态条件渲染 Compose 根

#### 统一主题系统
- 创建 `HybridTheme` 作为唯一主题入口
- `StandaloneTheme` 改为 `HybridTheme` 别名，保持向后兼容
- `AShareTheme` 也作为 `HybridTheme` 别名，Fusion 工作区使用
- 删除冗余 `Theme.kt`，消除声明冲突
- 统一 Material3 色彩系统和排版规范

#### 构建系统
- 移除 `-PappMode=connected|standalone` 构建参数
- 统一包名：`com.ashareai.app`
- 统一 applicationId：`com.ashareai.app`
- 合并所有依赖，不再条件判断
- 统一 AndroidManifest.xml

#### 数据隔离
- 本地工作区：`ashare_standalone.db` (Room), 本地设置 DataStore, API Key Keystore
- Fusion 工作区：会话 token, 服务器地址, 用户名, 工作区偏好
- 不共享：自选、持仓、提醒、研究运行、报告、候选池、模拟组合、AI 对话
- 共享：主题偏好、超级岛开关

#### 数据库迁移
- Room Database 升级到版本 4
- 新增表：`local_backtests`, `local_backtest_trades`
- 添加 MIGRATION_3_4

### 升级与迁移

#### 连接版用户 (com.ashareai.app 1.x)
- 直接升级到 3.0.0
- 会话和服务器地址自动保留
- 自动进入 Fusion 工作区

#### 独立版用户 (com.ashareai.app.standalone 2.x)
- 旧设备导出加密归档（`.ashare-local` 文件）
- 新设备安装 3.0.0
- 本地设置 → 导入归档
- 输入口令，导入自选、持仓、提醒、研究、报告、候选、模拟组合、对话
- API Key 不导入，需重新配置

### 技术细节

#### 核心类
- `com.ashareai.app.HybridApp` - 统一应用入口
- `com.ashareai.app.workspace.Workspace` - 工作区枚举
- `com.ashareai.app.workspace.WorkspaceStore` - 工作区状态管理
- `com.ashareai.app.workspace.FusionHealthState` - Fusion 健康契约验证
- `com.ashareai.app.standalone.backtest.LocalBacktestEngine` - 本地回测引擎
- `com.ashareai.app.standalone.search.LocalFinancialSearchRepository` - 本地金融搜索

#### 包结构调整
- `app/src/main/kotlin/com/ashareai/app/standalone/` → `app/src/main/java/com/ashareai/app/local/` (规划中)
- `app/src/main/java/com/ashareai/app/data/` → `app/src/main/java/com/ashareai/app/fusion/data/` (规划中)
- 当前保持原有包结构，待后续重构

### 已知限制

- 本地金融搜索当前仅支持目录搜索和实时行情，估值和财务指标标记为不可用
- 本地回测使用简化策略（逆向买入，持有 20 天），生产环境应支持自定义策略
- 工作区切换时前台任务继续运行，不会自动暂停

### 破坏性变更

- `-PappMode` 构建参数已弃用，构建命令无需再指定
- `com.ashareai.app.standalone.R` 改为 `com.ashareai.app.R`
- `StandaloneApp.CHANNEL_*` 改为 `HybridApp.CHANNEL_LOCAL_*`
- 原 `com.ashareai.app.standalone` 包保留，但部分类改为使用统一资源
- `Theme.kt` 已删除，所有主题相关代码统一使用 `HybridTheme.kt`

### 修复

- 修复 MainActivity 协程作用域问题（使用 `lifecycleScope.launch`）
- 修复通知通道引用错误
- 修复回测引擎类型推断问题
- 修复主题系统声明冲突（删除冗余 Theme.kt）

---

## [2.1.0] - (已弃用)

独立版最后版本，包名 `com.ashareai.app.standalone`。

## [1.1.0] - (已弃用)

连接版最后版本，包名 `com.ashareai.app`。
