<div align="center">

# ⚡ Ciallo～(∠・ω< )⌒☆跳广
### (AdGuardSkipper)

**广告秒跳过 · 纯本地零联网守护开屏净土**  
*A Rootless, 100% Offline Android Splash Ad Skipper & "Shake-to-Jump" Redirect Interceptor*

[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026%2B)-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![Language](https://img.shields.io/badge/Language-Kotlin-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![UI](https://img.shields.io/badge/UI-Jetpack%20Compose%20(M3)-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Privacy](https://img.shields.io/badge/Privacy-100%25%20Offline-success)](#️-隐私与安全承诺-privacy--security)
[![Release](https://img.shields.io/badge/Release-v1.6.0-orange)](../../releases)

<!-- 如果你把生成的封面图放在了项目根目录并命名为 cover.jpg，取消下方注释即可直接在主页居中展示封面 -->
<img src="cover.jpg" width="320" alt="Ciallo～(∠・ω< )⌒☆跳广 Cover" style="border-radius: 16px;" /> 

</div>

---

## 📖 项目简介 (Introduction)

**Ciallo～(∠・ω< )⌒☆跳广** 是一款基于 Android 官方无障碍服务（`AccessibilityService`）开发的**免 Root、纯本地离线**开屏广告跳过与自动跳转拦截工具。

针对国内主流广告 SDK（穿山甲、优量汇、快手联盟等）日益严苛的反无障碍检测、控件 ID 动态混淆、无文本图片跳过按钮以及臭名昭著的“摇一摇/扭一扭”强跳第三方应用，本项目采用 **多窗口视图穿透 + 坐标手势模拟 + 开屏状态机回退 + 屏幕可视化瞄准镜抓包** 四核架构，在确保日常使用零误触的前提下，毫秒级清扫广告干扰。

---

## ✨ 核心特性 (Key Features)

### 🚀 1. 强力免 Root 开屏跳过引擎 (`SkipEngine`)
- **主动轮询与多窗口穿透**：进入应用前 4~6 秒自动激活高频协程轮询，并通过 `AccessibilityWindowInfo` 穿透扫描所有浮层与 Dialog 窗口，彻底解决自绘 Canvas 延迟加载广告不触发系统事件的问题。
- **反混淆与零宽字符清洗**：自动剥离广告文本中的零宽空格（`\u200b` 等）与特殊干扰符，结合语义正则与主流广告联盟专属 ViewId 特征库进行多维打击。
- **真实手势坐标模拟 (`dispatchGesture`)**：当检测到广告将跳过按钮设为不可点击（`clickable="false"`）时，自动计算控件屏幕物理边框（`Rect`）中心点，模拟 40ms 真实手指按压。

### 🛡️ 2. “摇一摇”与恶意强跳双重防线 (`JumpInterceptor`)
- **第一道防线（系统确认框拦截）**：自动识别系统级“是否允许打开第三方应用 / 离开当前应用”弹窗，毫秒级自动点击“取消/拒绝”。
- **第二道防线（开屏强跳状态机拉回）**：动态识别本机桌面 Launcher，在应用冷启动的“开屏高危保护期”内，一旦监测到前台包名非正常突变为黑名单电商/短视频应用（如淘宝、京东、拼多多、抖音、美团等），立即触发全局返回（`GLOBAL_ACTION_BACK`）将其无缝拉回原应用。

### 🎯 3. 悬浮球“瞄准镜模式”——指哪打哪 (`LayoutInspectorFloatWindow`)
- **告别繁琐的代码节点树**：遇到美团骑行弹窗、哈啰单车等使用途中的顽固插屏广告时，点击屏幕边缘的悬浮球即可定格画面，开启**全屏可视化高亮框选层**。
- **智能红框标注与一键屏蔽**：系统自动用**红色实线框**标出疑似关闭按钮。用户只需用手指直接点选屏幕上的广告跳过/叉号（`×`）位置，即可一键生成 **精准控件规则** 或 **屏幕相对坐标盲点规则**，实时生效无需重启。

### 🔒 4. 严密防误触与安全边界架构
- **开屏期与日常期严格隔离**：高频启发式扫描严格限制在进入应用的前 4 秒内；4 秒后的日常使用期默认进入“静默模式”，仅响应用户通过悬浮球手动指定的精准规则，绝不乱点正常界面图标或数字红点。
- **三重防连点安全锁**：自定义规则强绑定 `Activity` 页面类名 + 单页面生命周期单次触发锁 + 系统核心界面（设置、安装器、输入法、桌面、软件自身）绝对豁免白名单。

### 🔋 5. 国产定制系统强力保活 (`KeepAliveHelper`)
- **Android 14+ 常驻前台通知**：实时响应式更新展示 *“正在守护开屏净土 | 今日已拦截 X 次”*。
- **双服务协同守护**：结合系统通知监听服务（`NotificationListenerService`）建立高优先级进程共生机制，并内置小米 HyperOS、华为 HarmonyOS、荣耀 MagicOS、vivo OriginOS、OPPO ColorOS 等专属自启动设置一键直达入口。

---

## 📲 下载与必看配置指南 (Installation & Setup)

前往 [**Releases 页面**](../../releases) 下载最新版本的 `.apk` 安装包并安装到手机。

### ⚠️ 第一步：解除 Android 13 / 14 / 15 “受限设置”拦截（极其关键）
由于本应用为个人开源侧载应用（非应用商店下载），在 Android 13 及以上系统中，首次开启无障碍服务时系统会置灰并提示 **“受限设置（出于安全考虑，此设置目前不可用）”**。请按以下三步解锁：
1. 先在系统【无障碍】设置中点击一下 `Ciallo～(∠・ω< )⌒☆跳广`，触发一次受限提示弹窗后关闭。
2. 打开手机系统的 **【设置】 -> 【应用管理 / 应用设置】 -> 搜索并点开【Ciallo～(∠・ω< )⌒☆跳广】**。
3. 点击应用详情页**右上角的三个点 `⋮`**（部分系统位于页面最底部），点击 **“允许受限设置”** 并验证锁屏密码/指纹。
4. 返回 App 首页，即可正常开启 **【无障碍服务】** 与 **【通知监听保活】**。

### 📋 第二步：开启国产系统“读取应用列表”权限
在小米、vivo、OPPO 等国产 ROM 上，若“白名单/防强跳黑名单”列表显示应用不全：
- 请点击页面顶部的引导卡片（或前往系统【应用权限管理】），将本应用的 **“获取应用列表 / 读取已安装应用”** 权限修改为 **始终允许**。

### 🔒 第三步：配置后台保活三件套
1. 在 App 首页看板点亮 **“忽略电池优化”**（设为无限制）。
2. 点击首页 **“自启动与后台保护”** 按钮，开启系统的自启动权限。
3. 呼出手机多任务后台卡片界面，将本应用卡片**下拉加锁**。

---

## 🏗️ 项目结构与技术栈 (Architecture & Tech Stack)

- **开发语言**：100% Kotlin (Coroutines + StateFlow)
- **UI 框架**：Jetpack Compose + Material Design 3
- **架构设计**：MVVM + 单例服务通信 + 本地 JSON/Preferences 持久化
- **编译环境**：Gradle Kotlin DSL (`minSdk 26`, `targetSdk 34`, JDK 17)

```text
app/src/main/
├── AndroidManifest.xml                         # 权限声明、无障碍服务、通知监听与 <queries> 配置
├── assets/
│   └── default_rules.json                      # 内置主流应用与四大广告联盟开屏跳过特征库
└── java/com/adguardskipper/
    ├── AdGuardSkipperApp.kt                    # Application 入口与常驻前台通知渠道初始化
    ├── data/
    │   └── preferences/AppPreferences.kt       # 规则库、黑白名单、拦截计数与用户协议状态持久化
    ├── engine/
    │   ├── SkipEngine.kt                       # 核心开屏识别算法、防误触隔离与手势坐标点击引擎
    │   └── JumpInterceptor.kt                  # 开屏强跳状态机拉回与系统打开外部应用弹窗拦截器
    ├── inspector/
    │   ├── NodeDumpItem.kt                     # 窗口视图节点模型与 JSON 序列化结构
    │   └── LayoutInspectorFloatWindow.kt       # 悬浮球、全屏可视化瞄准镜框选层与自定义规则生成器
    ├── keepalive/
    │   └── KeepAliveHelper.kt                  # 常驻通知动态刷新与国产 ROM 自启动 Intent 路由
    ├── service/
    │   ├── AdSkipAccessibilityService.kt       # 无障碍主服务、多窗口穿透扫描与协程主动轮询调度
    │   └── AdGuardNotificationListenerService.kt # 通知监听协同保活守护服务
    └── ui/
        ├── MainActivity.kt                     # Compose 主界面（看板、黑白名单、拦截日志、关于与协议弹窗）
        └── MainViewModel.kt                    # UI 状态流驱动、双通道应用列表扫描与调试日志导出
```

---

## 🛠️ 本地构建指南 (Build from Source)

你无需安装笨重的 Android Studio，只需配置 **JDK 17** 与 **Android SDK Command-line Tools** 即可通过命令行构建：

```bash
# 1. 克隆仓库
git clone [https://github.com/eternal12138/AdGuardSkipper.git](https://github.com/eternal12138/AdGuardSkipper.git)
cd AdGuardSkipper

# 2. 编译 Debug APK (Windows)
.\gradlew.bat assembleDebug

# 3. 编译 Debug APK (macOS / Linux)
./gradlew assembleDebug
```

编译产物将输出至：`app/build/outputs/apk/debug/app-debug.apk`。

---

## 🛡️ 隐私与安全承诺 (Privacy & Security)

无障碍服务（`AccessibilityService`）涉及系统较高权限，本项目做出如下最高级别隐私承诺：
1. **100% 纯本地离线运行**：本软件核心功能**不申请联网权限**，无任何远程服务器、无统计 SDK、无遥测上报。
2. **零敏感数据采集**：无障碍服务仅用于比对屏幕控件的跳过特征与坐标，绝不读取、记录或存储任何密码、聊天内容或个人隐私信息。
3. **代码完全开源透明**：所有逻辑均在本仓库公开可查，用户可随时自行审查代码并离线编译安装。

---

## ⚠️ 免责声明 (Disclaimer)

1. 本软件系独立开发者个人学习与 Android 无障碍技术交流作品，完全免费且不含任何商业盈利性质。
2. 因第三方应用界面频繁更新或用户自行添加“自定义坐标/控件规则”可能带来的操作误触风险由用户自行承担，开发者不承担任何直接或间接责任。
3. 严禁将本软件用于任何违反法律法规、破坏第三方计算机信息系统或侵害他人合法权益的用途。

---

## 👤 作者与致谢 (Author & Acknowledgments)

- **Developer**: **Lir Moriarty** ([@eternal12138](https://github.com/eternal12138))
- 如果这个小工具帮你的手机找回了清爽的开屏体验，欢迎点亮右上角的 **⭐ Star** 支持本项目！
- 遇到任何顽固广告或 Bug，可在 App 内通过 **【关于 -> 导出调试日志与自定义规则】** 复制诊断信息并提交 [Issue](../../issues)。
