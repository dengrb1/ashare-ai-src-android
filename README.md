# 超级岛 A股（独立版）

这是一个不依赖登录、后端服务、MiPush 或服务器任务队列的 Android 独立版。应用包名为
com.ashareai.app.standalone，可与原版同时安装。

> 本应用仅提供本地行情展示和研究辅助，不构成投资建议，也不会自动交易。

## 本地优先设计

- Room 保存持仓、自选、报价/K 线缓存、提醒、通知中心、研究运行/报告/候选、模拟组合和 AI 会话。
- DataStore 仅保存轻量设置与调度状态。
- 默认行情 Provider 直连公开 Eastmoney 上游；请求有限速和超时，失败时使用带“陈旧”标记的缓存。
- MarketDataProvider 可替换；预留了不嵌入 Python、pandas 或 Chaquopy 的 AKShare 兼容网关接口。
- 持仓监控只在交易时段读取实际持仓。无持仓、非交易时段和无任务时不会轮询行情。
- 提醒、系统通知和 HyperOS Focus API v3 超级岛载荷均先写入本地通知中心。
- AI Provider 可配置名称、Base URL、模型、API Key 及可选 Organization/Project；API Key 在 Android Keystore 加密后才写入数据库。
- AI 优先调用 /v1/responses 流式接口；只有 404、405、501 这类明确不支持端点的响应才回退到 /v1/chat/completions。
- .ashare-local 档案使用 PBKDF2 + AES-GCM，仅含用户本地数据；不包含 API Key、行情/K 线缓存或临时文件。

## 功能

- 行情、日 K 线、持仓与自选
- ATR20×2 止损、浮盈退出、手动价位和有效期买入区间提醒
- 普通、预警、研究进度通知与 HyperOS 超级岛测试
- 手动/每日研究、报告、候选池、模拟组合和卖出研究
- 可选 AI 报告解释和应用内问答；持仓成本/数量需全局和本次双重授权
- 上海时区 15:05 交易日研究调度、研究独立 :research 进程、前台服务超时后的 WorkManager 降级

## 开发环境

- Android Studio + JDK 17
- Android SDK compile/target SDK 36
- minSdk 29（Android 10）

在根目录配置未提交的 local.properties：

    sdk.dir=D:\\ASDK

## 构建与验证

    .\gradlew.bat testDebugUnitTest
    .\gradlew.bat lintDebug
    .\gradlew.bat connectedDebugAndroidTest
    .\gradlew.bat assembleDebug assembleRelease

connectedDebugAndroidTest 需要已连接的 Android 设备或模拟器。Release 必须签名；构建支持以下任一未提交的本地配置：

    # keystore.properties
    storeFile=standalone-release.jks
    storePassword=...
    keyAlias=...
    keyPassword=...

或环境变量：

    ASHARE_STANDALONE_STORE_FILE
    ASHARE_STANDALONE_STORE_PASSWORD
    ASHARE_STANDALONE_KEY_ALIAS
    ASHARE_STANDALONE_KEY_PASSWORD

不要提交 keystore、签名口令、API Key、私有地址或 APK。

## 项目结构

    app/src/main/kotlin/com/ashareai/app/standalone/
    ├── data/          Room、DataStore、行情、AI 与加密档案
    ├── alerts/        本地提醒阈值与去重
    ├── monitor/       持仓前台监控与 WorkManager 降级
    ├── research/      确定性评分、研究服务与卖出研究
    ├── island/        HyperOS Focus API v3 载荷
    ├── notifications/
    ├── work/          上海时区任务与恢复
    └── ui/            Compose 页面和本地 ViewModel

JVM 测试位于 app/src/test/kotlin/；Room 迁移与 Compose 启动测试位于
app/src/androidTest/kotlin/。
