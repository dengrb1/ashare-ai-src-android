# Android 协作说明

本仓库同时维护两套 Kotlin + Jetpack Compose 客户端：连接 Web/FastAPI 的客户端位于 `app/src/main/java/com/ashareai/app/`，本地优先独立版位于 `app/src/main/kotlin/com/ashareai/app/standalone/`。它们均用于研究、回测和模拟，不执行自动实盘交易。

## 大盘指数与评分

- 连接版通过 `/api/v1/market/indices` 只在行情页按需展示沪深 300 `000300`、中证 500 `000905`、中证 1000 `000852`；报告读取后端冻结的 `market_index_snapshot`，不得用打开报告时的实时行情重算。
- 独立版使用 Eastmoney `1.<symbol>` secid；`MarketIndexContextAnalyzer` 在每次研究运行开始时冻结 1/5/20 日收益、50/30/20 加权收益、市场状态、评分调整和风险乘数。
- 指数不足时只能降级为 `UNKNOWN`、调整 `0`、风险乘数 `1`。指数上下文必须传给确定性评分、排序、模拟组合、报告和 AI 提示词；AI 只能解释结果，不能修改评分或门槛。

## 内存、AI 与界面

- 不新增启动预热、全局轮询或无界缓存。连接版只在前台登录后轮询股票，独立版仅在需要时加载指数、K 线和目录，后台立即停止前台轮询或由用户启用的 WorkManager 接管。
- 连接版的 AI 密钥仅由后端模型设置管理；独立版经 `ApiKeyCipher` 和 Android Keystore 加密。两种版本都不得将密钥写入日志、普通偏好、报告、测试夹具或截图。
- 使用 Material 3。Liquid Glass 仅适用于导航、工具栏、分段控件与临时操作层；报告、图表、表格和数据卡片保持清晰实体表面。

## 验证与安全

JDK 17 下，独立版为默认构建模式，改动后运行：

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleRelease
```

连接版使用 `-PappMode=connected`，例如 ` .\gradlew.bat -PappMode=connected testDebugUnitTest lintDebug assembleDebug assembleRelease`。涉及 Compose、导航、权限、前台服务或 Room 迁移时，有设备则补跑对应模式的 `connectedDebugAndroidTest`。提交前执行 `git diff --check`；不得提交 `local.properties`、keystore、签名凭据、API Key、私有服务器地址、APK 或 `build/`。
