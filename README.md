# AShare AI Android

此仓库维护两种 A 股 AI 投研 Android 客户端：连接版使用 Web/FastAPI 后端的研究、AI 配置、报告、行情、自选、持仓、提醒和模拟组合能力；独立版不依赖登录、后端服务或服务器任务队列，在本地完成行情、研究、报告、提醒、模拟组合和可选 AI 解释。两者均不执行自动实盘交易，也不构成投资建议。

## 大盘指数

两种版本都覆盖沪深 300（`000300`）、中证 500（`000905`）和中证 1000（`000852`）。

- 连接版行情页按需请求 `/api/v1/market/indices`；实时指数只用于展示。报告页读取后端在研究决策日冻结的指数快照，展示 1/5/20 日收益、市场状态、评分调整和风险乘数。
- 独立版在行情页按需加载三指数；每次研究开始时，仅获取一次指数 K 线并生成不可变 `MarketIndexContext`。个股总分、候选排序、模拟组合、AI 解释和报告都使用同一快照，历史报告不会被当前行情重算。
- 数据不足时按 `UNKNOWN`、中性调整和风险乘数 `1` 降级，绝不猜测市场状态。

## 低驻留与 AI

启动只恢复当前状态和必要调度。连接版仅在前台登录状态按用户间隔轮询股票；独立版只在可见页面或研究任务中请求指数、K 线与目录。后台停止无关轮询，通知和 WorkManager 由用户显式设置控制。

连接版的 AI Provider 配置由后端保存并向客户端脱敏；独立版支持 OpenAI-compatible Base URL、模型、Organization/Project 和 Android Keystore 加密的 API Key。AI 只能解释确定性研究和冻结市场环境，不能修改分数或风险阈值。

## 构建与验证

需要 Android Studio、JDK 17、Android SDK 36 和 minSdk 29。配置未提交的 `local.properties` 后运行：

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleRelease
```

有设备时再运行 `connectedDebugAndroidTest`。Release 必须使用本地或 CI 签名配置签名；不要提交 keystore、密码、API Key、私有地址、APK 或 `build/`。
