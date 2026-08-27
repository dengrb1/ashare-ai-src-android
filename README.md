# AShare AI Android

此仓库维护单一混合 APK，同时支持本地工作区和 Fusion 工作区：

- **本地工作区**：无需登录，使用 Room + EastMoney + 本地研究引擎 + AI Provider + 本地任务
- **Fusion 工作区**：连接 FastAPI 服务器，使用 `/api/v1` 全部用户侧能力（研究、AI、报告、行情、自选、持仓、提醒、模拟组合、回测）

两套数据、密钥、任务和通知完全隔离，用户可显式切换工作区。均不执行自动实盘交易，也不构成投资建议。

## 工作区隔离

- **本地工作区数据**：`ashare_standalone.db`（Room）、本地设置 DataStore、API Key Keystore、研究快照、通知记录
- **Fusion 工作区数据**：会话 token、服务器地址、用户名、工作区偏好、脱敏 UI 状态
- **不共享**：自选、持仓、提醒、研究运行、报告、候选池、模拟组合、AI 对话
- **共享**：主题偏好、超级岛开关（由 `WorkspaceStore` 根据当前工作区分发）

## 大盘指数

两种工作区都覆盖沪深 300（`000300`）、中证 500（`000905`）和中证 1000（`000852`）。

- Fusion 工作区行情页按需请求 `/api/v1/market/indices`；实时指数只用于展示。报告页读取后端在研究决策日冻结的指数快照，展示 1/5/20 日收益、市场状态、评分调整和风险乘数。
- 本地工作区在行情页按需加载三指数；每次研究开始时，仅获取一次指数 K 线并生成不可变 `MarketIndexContext`。个股总分、候选排序、模拟组合、AI 解释和报告都使用同一快照，历史报告不会被当前行情重算。
- 数据不足时按 `UNKNOWN`、中性调整和风险乘数 `1` 降级，绝不猜测市场状态。

## 低驻留与 AI

启动只恢复当前状态和必要调度。Fusion 工作区仅在前台登录状态按用户间隔轮询股票；本地工作区只在可见页面或研究任务中请求指数、K 线与目录。后台停止无关轮询，通知和 WorkManager 由用户显式设置控制。

Fusion 工作区的 AI Provider 配置由后端保存并向客户端脱敏；本地工作区支持 OpenAI-compatible Base URL、模型、Organization/Project 和 Android Keystore 加密的 API Key。AI 只能解释确定性研究和冻结市场环境，不能修改分数或风险阈值。

## 构建与验证

需要 Android Studio、JDK 17、Android SDK 36 和 minSdk 29。配置未提交的 `local.properties` 后，构建统一 APK：

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleRelease
```

**注意**：从 3.0.0 起，`-PappMode=connected|standalone` 参数已弃用。单一 APK 同时支持本地和 Fusion 工作区，构建命令无需再指定 `appMode`。

有设备时再运行 `connectedDebugAndroidTest`。Release 必须使用本地或 CI 签名配置签名；不要提交 keystore、密码、API Key、私有地址、APK 或 `build/`。

## 升级与迁移

- **连接版用户**（`com.ashareai.app` 1.x）：直接升级到 3.0.0，会话和服务器地址保留，自动进入 Fusion 工作区。
- **独立版用户**（`com.ashareai.app.standalone` 2.x）：
  1. 旧设备导出加密归档（`.ashare-local` 文件）
  2. 新设备安装 3.0.0，本地设置 → 导入归档
  3. 输入口令，导入自选、持仓、提醒、研究、报告、候选、模拟组合、对话
  4. API Key 不导入，需重新配置

## 工作区切换

- 本地设置页：显示"切换到 Fusion 工作区"按钮
- Fusion 个人页：显示"切换到本地工作区"按钮
- 切换时显示确认对话框，当前任务不会被打断
- 两套后台任务可同时运行，通知标注来源工作区

## 通知隔离

所有通知携带工作区标识：

- 本地研究完成 → `local_progress` 通道，深链 `local/reports?date=...`
- Fusion 卖出建议 → `fusion_alert` 通道，深链 `fusion/exit_advice`
- 点击通知自动切换到对应工作区并导航

## 版本历史

- **3.0.0** (versionCode 5)：单 APK 双工作区架构，合并 connected 和 standalone
- **2.1.0** (versionCode 4)：独立版最后版本（已弃用）
- **1.1.0** (versionCode 3)：连接版最后版本（已弃用）
