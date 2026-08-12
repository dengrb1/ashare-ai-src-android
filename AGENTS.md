# 独立版协作说明

这是单模块 Kotlin + Jetpack Compose 本地优先应用，生产代码位于 `app/src/main/kotlin/com/ashareai/app/standalone/`；行情与缓存在 `data/market`，研究在 `research`，Room 在 `data/local`，AI 在 `data/ai`，页面在 `ui`。

## 大盘指数契约

- 指数代码固定为沪深 300 `000300`、中证 500 `000905`、中证 1000 `000852`；上海指数必须使用 Eastmoney `1.<symbol>` secid。
- `MarketIndexContextAnalyzer` 只使用研究启动时取到的历史 K 线，输出 1/5/20 日收益、50/30/20 加权收益、市场状态、评分调整和风险乘数。
- `ResearchCoordinator` 每个 run 冻结一次 context，并将其传入 `DeterministicResearchEngine`；报告、候选、模拟组合和 AI prompt 都必须保持一致。
- 数据不足只能降级为 `UNKNOWN`、调整 0、风险乘数 1，不得猜测或用当前实时价格回写历史报告。

## 低内存与生命周期

应用启动只恢复本地状态和必要调度。行情页进入时才加载三指数；股票 K 线和目录按需加载。无持仓、非交易时段和无任务时不轮询；后台停止前台监控或由 WorkManager 按设置接管。不要引入全局网络循环、无界缓存或 Python/Chaquopy。

## AI 与安全

AI Provider 的 API Key 必须通过 `ApiKeyCipher` 使用 Android Keystore 加密，不能写日志、普通 DataStore、导出档案、测试夹具或截图。AI 只能解释确定性结果，不能修改评分、风险或交易门槛。独立版不保存账号管理信息，也不调用服务器登录接口。

## UI 与验证

沿用 Material 3 和现有独立版主题。Liquid Glass 只应用于导航、工具条、分段控制和临时操作层；图表、报告、表格和数据卡片保持清晰。代码改动后运行：

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleRelease
```

涉及 Compose、导航、权限、前台服务或 Room 迁移时，在有设备时补跑 `connectedDebugAndroidTest`。提交前执行 `git diff --check`，不得提交 `local.properties`、keystore、密钥、Token、APK 或 `build/`。
