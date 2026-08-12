# 霁衡智研 Android

霁衡智研 Android 是 A 股研究、行情观察、模拟组合和 AI 量化辅助的原生客户端。它使用 Kotlin 与 Jetpack Compose 构建，并通过 AShare AI 后端的 `/api/v1` 接口取得行情、研究任务、报告、AI 对话和通知数据。

本应用只提供研究、回测和模拟组合能力，不连接真实券商账户，也不构成投资建议。

## 功能

- 行情与个股：自选、报价、日线及分钟 K 线、成交量、MACD、KDJ、均线、缩放和拖动浏览。
- 资产与交易观察：模拟持仓、自选股、买入区间、卖出建议、止损和交易建议监控。
- 研究工作流：每日研究、候选池、模拟组合、研究报告、运行审计、回测和金融数据搜索。
- AI 股票问答：流式对话、图片附件、股票提及、模型与推理强度选择、联网检索开关、上下文与成本统计。
- 通知：应用内通知、后台持仓/研究监控和兼容系统的焦点通知协议；仅在用户主动启用后运行。
- 个人数据：加密导出、导入和合并个人资产、对话与偏好数据。
- 管理员控制台：AI 模型配置、系统资源与低驻留策略、运行身份、版本化系统参数和 Edge Gateway 配置。

账号创建、禁用、删除和重置密码仍由 Web 管理端处理，Android 客户端不提供账户管理入口。

## AI 与安全

管理员可以在“我的 -> 管理员控制台 -> AI 模型配置”中配置 OpenAI-compatible API：

- Base URL、搜索模型、研究模型、推理强度和请求超时。
- 模型档案中的缓存协议、上下文窗口、Token 预留和成本参数。
- 可用模型读取、连通性探测和诊断日志。

API Key 不会保存到 Android DataStore。手机只在当前编辑期间持有输入值，提交后由后端的加密版本化模型配置服务保存；留空不会覆盖已保存的密钥。

系统设置和 Edge Gateway 写操作要求管理员使用当前密码获取短期解锁令牌。FRP 内容只会在解锁后的请求中读取或保存。

## 低驻留运行

客户端默认只保留登录、前台页面和必要的前台行情轮询：

- 前台行情刷新间隔保存在本机；应用退到后台后轮询立即停止。
- 厂商推送 SDK 不在 Application 启动时初始化。
- 通知权限、推送注册和前台监控服务仅在登录用户主动启用“行情与研究通知”后启动。
- 关闭通知时会停止前台监控、解绑设备并停止推送事件收集。
- 管理员可在系统设置中选择 `LIGHTWEIGHT` 运行模式、自动节能和后端缓存/并发上限。

## 架构

```text
app/src/main/java/com/ashareai/app/
├── data/       # Retrofit、SSE、DTO、DataStore 与安全存储
├── island/     # 推送、前台监控与焦点通知
├── ui/
│   ├── components/  # 图表、通用控件与状态组件
│   ├── navigation/  # Compose Navigation 路由和底部导航
│   ├── screens/     # 业务、研究和管理员页面
│   └── theme/       # 颜色、排版、Shape 与 Liquid Glass 控制层
└── MainActivity.kt
```

客户端 DTO 位于 `data/model/Dtos.kt`，字段直接保持后端 snake_case 契约。修改后端接口时，应同步修改 DTO、`ApiService` 和对应的序列化单元测试。

## 开发环境

- Android Studio，JDK 17。
- Android SDK，`compileSdk 36`。
- Android 10 / API 29 或更高版本的设备或模拟器。

在根目录创建不提交的 `local.properties`：

```properties
sdk.dir=C\:\\Users\\<用户名>\\AppData\\Local\\Android\\Sdk
```

默认后端地址是 `http://127.0.0.1:8000`。真机上的 `127.0.0.1` 指向手机自身，请在应用“设置”中改为局域网可访问的 IP/域名，或生产 HTTPS 地址。

## 构建与验证

PowerShell：

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebug assembleRelease
```

界面、导航、权限或生命周期改动还应在连接设备后执行：

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

Debug APK：`app/build/outputs/apk/debug/app-debug.apk`

Release APK：`app/build/outputs/apk/release/app-release.apk`

Release 需要签名。将本机的 keystore 信息写入已忽略的 `keystore.properties`：

```properties
storeFile=release.keystore
storePassword=<secret>
keyAlias=<alias>
keyPassword=<secret>
```

不要提交 keystore、`keystore.properties`、后端 URL、API Key、推送密钥或生成的 APK/AAB。发布前可执行：

```powershell
& "<Android SDK>\build-tools\<version>\apksigner.bat" verify --verbose app\build\outputs\apk\release\app-release.apk
```

## 贡献约定

- Kotlin 使用四个空格缩进和多行尾随逗号。
- 保持 Compose 页面、数据访问和业务逻辑隔离。
- 复用已有 Material 3 与 Apple-inspired Liquid Glass 控制层；玻璃效果仅用于导航、工具栏、底部控制层和弹层，密集数据区域保持清晰稳定。
- 新业务逻辑必须提供聚焦测试；修改 API 契约必须提供序列化测试。
- 提交信息使用 Conventional Commits，例如 `feat:`、`fix:`、`test:` 或 `docs:`。
