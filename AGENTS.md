# Android 协作说明

这是单模块 Kotlin + Jetpack Compose 客户端，代码位于 `app/src/main/java/com/ashareai/app/`；API/DTO 在 `data/`，Compose 页面在 `ui/`，通知行为在 `island/`。

## API 与研究契约

- `/api/v1` 与 Web 共用；新增字段必须可选并保持旧客户端解码，DTO 使用 snake_case。
- 大盘指数实时展示使用 `/api/v1/market/indices`；研究评分必须读取后端冻结的 `market_index_snapshot`，不能用报告打开时的实时价格重算。
- 报告详情展示指数收益、市场状态、评分调整和风险乘数；缺失快照按中性显示。
- AI API Key 只进入后端模型设置接口，不写入 `SettingsStore`、日志、截图、测试或普通持久化。

## 低内存与生命周期

只在前台登录状态运行股票报价轮询，后台立即取消。大盘指数在行情页进入和显式刷新时加载；不要在 `Application` 初始化、后台 Worker 或全局 ViewModel 中新增无条件网络轮询、图表预取或大缓存。通知、推送和监控仍遵循用户显式开启后才启动。

## UI

沿用 Material 3 和现有 `AShareTheme`。Liquid Glass 只用于导航、工具栏、弹层等功能性 chrome；报告、表格、图表和财务数据保持清晰实体表面。保持 48dp 触控目标、可见焦点、暗色模式、长中文和无障碍缩放。

## 验证

JDK 17 下代码改动必须运行：

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleRelease
```

涉及 Compose、导航、权限或生命周期时，在有设备时补跑 `connectedDebugAndroidTest`。检查 `git diff --check`；不要提交 `local.properties`、keystore、签名凭据、Token、服务器私有地址、APK 或 `build/`。
