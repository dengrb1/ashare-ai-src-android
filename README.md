# AShare AI Android

这是连接 A 股 AI 投研 Web/FastAPI 后端的 Android 客户端。它保留服务器端研究、AI 配置、报告、行情、自选、持仓、提醒和模拟组合能力；不执行自动实盘交易，也不构成投资建议。

## 大盘指数

行情页按需请求 `/api/v1/market/indices`，显示沪深 300（000300）、中证 500（000905）和中证 1000（000852）的实时行情。这个实时接口只用于展示，不会改变已发布报告。

报告页读取后端在研究决策日冻结的指数快照，并显示 1/5/20 日收益、`RISK_ON`/`NEUTRAL`/`RISK_OFF`/`UNKNOWN` 状态、评分调整和风险乘数。旧报告缺少快照时按中性兼容。

## 低驻留运行

会话恢复和当前页面是默认必需功能。行情股票报价只在前台、登录后按用户间隔轮询；大盘指数只在行情页首次进入或手动刷新时加载，通知和市场状态保留低频刷新。进入后台会取消轮询，不启动无关服务。

## AI

AI Provider、Base URL、模型、Organization/Project 和 API Key 由服务端模型设置管理。客户端只提交配置请求和显示脱敏状态，不把密钥写入普通偏好、日志或报告。AI 只能解释已有研究和冻结指数环境，不能修改分数或风险门槛。

## 开发与验证

环境要求 Android Studio、JDK 17、Android SDK 36、minSdk 29。配置未提交的 `local.properties` 后运行：

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleRelease
```

需要设备时再运行 `connectedDebugAndroidTest`。Release 必须通过本地或 CI 签名配置签名；不要提交 keystore、密码、API Key、`local.properties`、APK 或 `build/`。
