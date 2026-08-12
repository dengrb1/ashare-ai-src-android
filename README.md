# AShare AI Android 独立版

独立版是不依赖登录、后端服务、MiPush 或服务器任务队列的本地 Android 投研应用，可与服务端客户端同时安装。它只做行情、研究、提醒、报告、模拟组合和可选 AI 解释，不做自动实盘交易，也不构成投资建议。

## 大盘指数与研究评分

行情页按需加载沪深 300（000300）、中证 500（000905）和中证 1000（000852）实时行情，页面离开或应用进入后台不继续请求。

每次研究任务开始时，应用只获取一次三指数最多 100 个交易日 K 线，生成不可变 `MarketIndexContext`，然后传给该任务的每支股票。它计算 1/5/20 日收益、加权市场状态、评分调整和风险乘数；个股总分、候选排序、模拟组合、AI 研究解释和本地报告正文全部使用这个冻结上下文。历史报告打开时不会被当前行情重算；指数数据不足时以 `UNKNOWN` 和中性调整降级。

## 本地与 AI

- Room 保存持仓、自选、报价/K 线缓存、提醒、报告、候选、模拟组合和 AI 会话。
- DataStore 只保存轻量设置和调度状态；行情请求有限速、超时，失败使用标记为陈旧的缓存。
- AI Provider 支持 OpenAI-compatible Base URL、模型、Organization/Project 和 API Key；API Key 用 Android Keystore 加密后保存，AI 只能解释确定性研究数据。
- 监控、通知、每日研究和 WorkManager 降级均按用户开关或任务状态启动，不在应用启动时预热无关功能。

## 构建与验证

需要 Android Studio、JDK 17、Android SDK 36、minSdk 29。配置未提交的 `local.properties` 后运行：

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleRelease
```

有设备时再运行 `connectedDebugAndroidTest`。Release 需要本地或 CI 签名配置；不要提交 keystore、密码、API Key、私有地址、APK 或 `build/`。
