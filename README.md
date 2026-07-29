# 霁衡智研 Android

霁衡智研是一款基于 Kotlin 与 Jetpack Compose 开发的 A 股行情与研究辅助 Android 客户端。应用通过可配置的后端服务获取账户、行情、K 线、研究结果和监控提醒数据。

> 本项目提供投资信息展示与研究辅助，不构成任何投资建议。

## 功能概览

- 行情与自选股：查看报价、涨跌、成交量及收藏状态。
- 个股详情：支持日线和多种分钟 K 线、成交量、MACD、KDJ、均线、缩放与拖动浏览。
- 资产与研究：查看持仓、研究任务和模拟组合等数据。
- 监控提醒：支持应用内行情监控和通知能力。
- 设置：可在应用内配置后端服务地址、登录状态和主题等偏好。

## 技术栈

- Kotlin、Jetpack Compose、Material 3
- Retrofit、OkHttp、kotlinx.serialization
- DataStore
- JUnit、Kotlin Coroutines Test
- GitHub Actions

## 开发环境

- Android Studio（使用 JDK 17）
- Android SDK（`compileSdk 36`）
- Android 10 / API 29 或更高版本的设备或模拟器

在项目根目录创建 `local.properties` 并配置本机 Android SDK，例如：

```properties
sdk.dir=C\:\\Users\\<用户名>\\AppData\\Local\\Android\\Sdk
```

`local.properties`、令牌、私有服务地址和签名文件均不应提交到仓库。

## 后端配置

应用默认请求地址为 `http://127.0.0.1:8000`。真机上的 `127.0.0.1` 指向手机自身，请在应用设置页改为设备可访问的后端地址，例如局域网 IP 或 HTTPS 域名。

## 构建与验证

Windows PowerShell：

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
.\gradlew.bat installDebug
```

生成的 Debug APK 位于：

```text
app/build/outputs/apk/debug/app-debug.apk
```

构建 Release 包：

```powershell
.\gradlew.bat assembleRelease
```

未配置发布签名时，Android Gradle Plugin 会输出未签名的 Release APK；该文件不能直接安装。正式发布前请在安全的 CI 密钥管理系统中配置签名证书和密码，切勿将 `.jks`、`.keystore` 或密码提交到仓库。

## 自动构建

推送到 `main` 时，GitHub Actions 会自动：

1. 执行单元测试；
2. 执行 Android Lint；
3. 构建 Release APK；
4. 将 APK 作为 workflow artifact 保存 30 天。

也可在 GitHub Actions 页面手动触发 **Build Android Release APK** 工作流。

## 项目结构

```text
app/src/main/java/com/ashareai/app/
├── data/       # API、数据模型与设置存储
├── island/     # 通知与行情监控
└── ui/         # Compose 页面、组件、导航与主题
```

单元测试位于 `app/src/test/`；设备与 Compose UI 测试（如有）位于 `app/src/androidTest/`。

## 贡献约定

- Kotlin 使用四个空格缩进，多行声明保留尾随逗号。
- 新增业务逻辑时补充针对性单元测试。
- 提交前至少运行 `testDebugUnitTest` 和 `lintDebug`。
- 提交信息使用 Conventional Commits 风格，例如 `feat:`、`fix:`、`test:`。
