# Ciallo～跳广（AdGuardSkipper）

当前版本：**1.6.0**

免 Root 的开屏广告跳过与异常强跳拦截 Android 应用。核心能力使用系统无障碍服务（AccessibilityService）读取当前窗口、执行点击手势和返回操作，不依赖 Xposed、Root 或 VPN；主动开启布局抓取时，会使用用户授权的悬浮窗提供调试入口。

- 语言：Kotlin
- UI：Jetpack Compose (Material 3)
- 架构：MVVM + Coroutines + StateFlow + DataStore
- minSdk 26 / targetSdk 34 / compileSdk 34
- 构建：Gradle 8.7 + AGP 8.5.2 + Kotlin 1.9.24 + JDK 17

## 功能

### 1. 严格开屏广告跳过

只有从桌面 Launcher 冷启动新应用后的 **4000ms** 内才进入开屏期，每 **250ms** 扫描一次。应用切换、通知启动、服务重连都不会启用开屏启发式规则。

开屏期只允许：

1. **严格文本**：清洗零宽字符和空白后必须完全匹配
   `跳过`、`跳过广告`、`跳过5`、`跳过5s`、`5s跳过`。
   单独“关闭”“X”“我知道了”和纯数字一律不匹配。
2. **严格广告 SDK ViewId**：必须同时包含广告前缀（`tt_`、`ksad_`、`gdt_`、`beizi_`、`ad_`、`splash_`）和跳过词（`skip`、`jump`）。泛化的 `close`、`cancel` 不再匹配。
3. **尺寸与位置强校验**：按钮宽 < 屏宽 30%、高 < 屏高 10%，且位于屏幕四角边缘区域；屏幕中央控件绝不点击。

用户手动添加的规则只在 4 秒后的日常使用期生效，不参与开屏启发式扫描。

已彻底删除：

- 右上角/底部居中/小尺寸 ImageView 的纯几何盲扫
- 1~6 位纯数字倒计时匹配
- 通用“关闭”“X”开屏匹配
- 内置 assets 文本与盲点坐标规则

点击策略：命中后执行真实手势、节点 `ACTION_CLICK` 和最近可点击父节点 `ACTION_CLICK`，并设置 800ms 全局冷却。

### 2. 开屏保护期强跳拉回

- 服务启动时通过 `PackageManager` 动态查询本机所有 HOME/Launcher 包名。
- 从桌面进入应用 A 时记录 `sourcePackage` 和起始时间。
- 3.5 秒内若前台突然变成黑名单应用（内置淘宝、京东、拼多多、美团、抖音、快手等，可在 UI 中增删），且期间未经过 Launcher，立即执行 `GLOBAL_ACTION_BACK` 返回。
- 每次拦截写入日志，并更新一条本地“今日拦截次数”通知（Android 13+ 需通知权限）。

强跳拦截有 1.5 秒冷却，避免返回后再次触发。

### 3. 日常使用期：只执行用户自定义规则

4 秒后不再运行任何通用文本、通用 ViewId、关闭词或几何扫描。默认完全静默，只有用户在悬浮球中亲手保存的规则才会参与匹配。

- 所有自定义 ViewId、文本、坐标规则都必须记录并匹配 `activityClassName`。
- 坐标规则只在真实 Dialog/Popup 窗口事件下触发，绝不在普通 Activity 主界面盲点。
- 同一规则在同一页面生命周期最多点击一次；跨页面 5 秒内也不会重复。
- 每次自动点击前再次检查 800ms 全局冷却、悬浮球审查层暂停状态和页面锁。
- 系统设置、权限安装器、SystemUI、输入法窗口和所有桌面 Launcher 绝对豁免。

### 自身安全隔离

- 无障碍事件、轮询任务、应用内弹窗任务、窗口根节点、节点扫描和点击入口均按服务自身包名过滤。
- 悬浮球点击抓取前会先设为 `INVISIBLE`，等待 80ms 后从全部非自身目标窗口中抓取。
- `dumpCurrentWindowNodes()` 在窗口和节点两层排除 AdGuardSkipper 的悬浮球与审查浮层。
- 节点审查浮层打开时设置 `isInspectorDialogShowing=true`，暂停所有自动点击与弹窗扫描；关闭后自动解除暂停。

### 4. 悬浮窗可视化瞄准器

主页“进阶工具”区域提供 **开启布局抓取悬浮球** 开关：

1. 首次开启会申请 `SYSTEM_ALERT_WINDOW`（显示在其他应用上层）权限。
2. 开启后，屏幕侧边出现可拖动的抓取悬浮球；松手后自动吸附到最近侧边。
3. 遇到无法跳过的顽固开屏广告或应用内弹窗时点击悬浮球。悬浮球先隐藏 80ms，再由无障碍服务从所有非自身目标窗口递归抓取可见节点的 `className`、`text`、`contentDescription`、`viewIdResourceName`、`clickable` 和 `boundsInScreen`。
4. 完整快照写入 App 私有目录：

```text
files/layout_captures/layout_<packageName>_<yyyyMMdd_HHmmss_SSS>.json
```

5. 不再直接弹出密密麻麻的节点列表，而是打开全屏半透明瞄准层：
   - 小尺寸可交互控件绘制绿色虚线框；
   - 疑似关闭按钮绘制红色实线框并标注“疑似跳过按钮”；
   - 自动过滤大于屏幕 40% 的容器、不可见节点和纯背景布局。
6. 直接点击屏幕上的跳过/关闭位置：
   - 自动选中包含该坐标且面积最小的控件；没有控件时按手指坐标兜底；
   - 底部只出现两个大按钮：**“⚡ 智能屏蔽此按钮（推荐）”** 和 **“🎯 强制锁定此位置点击”**；
   - 智能模式优先保存 ViewId，其次保存文本规则，最后才用坐标兜底。
7. 顶部 **“只看推荐按钮”** 开关可切换为 3~5 个智能候选的极简列表，显示“[右上角] 跳过”等大白话信息。
8. 保存后立即关闭瞄准层，恢复悬浮球和自动监听。

JSON 结构示例：

```json
{
  "schemaVersion": 2,
  "packageName": "com.example.app",
  "activityName": "com.example.app.MainActivity",
  "capturedAt": 1760000000000,
  "capturedAtIso": "2026-10-30T05:00:00Z",
  "screen": { "width": 1080, "height": 2400 },
  "nodeCount": 42,
  "truncated": false,
  "nodes": [
    {
      "depth": 3,
      "className": "android.widget.TextView",
      "text": "跳过广告",
      "contentDescription": "",
      "viewIdResourceName": "com.example.app:id/tt_splash_skip_btn",
      "clickable": true,
      "visible": true,
      "boundsInScreen": { "left": 900, "top": 120, "right": 1040, "bottom": 210 }
    }
  ]
}
```

### 5. 国产系统双服务保活

- 无障碍服务连接后立即调用 `startForeground`，绑定常驻前台通知：**“正在守护开屏净土 | 今日已拦截 X 次”**。通知随强跳计数实时更新。
- 新增系统通知监听服务 `AdGuardNotificationListenerService`。通知监听和无障碍服务都由系统绑定，形成双重进程绑定。
- 无障碍服务连接时会请求重绑通知监听；通知监听连接时会反过来刷新无障碍服务的前台通知，实现轻量级“互相唤醒”。
- 每 15 分钟重新确认前台通知并请求一次通知监听重绑（仅在已授权通知使用权时）。
- 用户需要在系统“通知使用权”设置中手动授权，主页提供状态卡片与一键跳转。

### 6. Compose 管理界面

- **看板**：无障碍状态、电池优化、通知权限、通知使用权、悬浮球开关、今日跳过/拦截次数、本地规则数量、名单概览和“🧹 清空所有自定义规则”紧急按钮。
- **名单**：应用白名单与强跳黑名单，支持搜索、显示系统应用、应用图标与开关；顶部“全部应用 / 仅已加入名单”下拉可切换展示范围，当前名单内的应用始终置顶，其余应用按名称排序。
  - 应用扫描采用 `queryIntentActivities(ACTION_MAIN + CATEGORY_LAUNCHER, 0)` 与 `getInstalledPackages` 双通道合并去重。
  - 预装后升级的应用（含 `FLAG_UPDATED_SYSTEM_APP`）按第三方应用保留，不再被系统标记误过滤。
  - 第三方应用少于 5 个时显示醒目提示卡片，点击直达本应用系统详情页，便于开启国产 ROM 的“读取应用列表”权限。
- **日志**：最近 100 条记录，包含时间、类型、包名、来源包、规则触发类型与详情，可一键清空。
- **关于**：品牌图标、动态版本号、用户协议与隐私声明、作者与 GitHub 入口，并支持一键导出“自定义规则 + 最近拦截记录 + 设备信息”的 JSON 调试包。

白名单、黑名单、每日计数、日志、本地 ViewId、文本与坐标规则均通过 Preferences DataStore 持久化；计数在跨天后自动归零。用户协议的同意状态同样持久化在 DataStore，未同意时无障碍服务不会执行任何扫描或点击。旧版本规则在首次启动 1.5.0 时会被安全清理一次。

### 7. Ciallo 品牌图标 / Showcase / UI

- 桌面显示名：**Ciallo～(∠・ω< )⌒☆跳广**，无障碍服务与通知名称同步使用该品牌。
- 桌面图标取自第一张 Ciallo 插画：自适应图标与圆形图标使用角色主体、天空蓝底、白色贴纸和钴蓝描边；角色位于系统圆形遮罩的安全区，避免人物被切边。方形 legacy 图标保留完整插画与 `Ciallo～` 字样。
- Android 12+ 系统启动画面使用独立的角色安全区图标与浅蓝背景；进入应用后展示第二张“跳广”海报：白色贴纸海报卡片 + 珊瑚/柠檬黄装饰 + 蓝色进度条，加载结束后淡入主页。
- 应用内统一为 Ciallo 海报风格：粉蓝天空渐变、星星/气泡装饰、钴蓝描边卡片、像素标签（`SKIP`、`BLOCK`、`CIALLO`、`TOOL`）、圆润底栏与贴纸感按钮；主页、名单、日志、空状态和清空对话框共用同一套组件。
- 主题固定为浅色，并显式把状态栏/导航栏图标设为深色，避免系统深色模式下浅色背景上出现白色图标。
- 本次只改 UI/UX、主题、图标和启动展示；跳过与强跳拦截的核心逻辑未改动。

### 8. 关于页与用户协议强准入

- 首次启动必须阅读并同意《用户协议与隐私声明》，弹窗不可通过返回键或点击外部关闭；拒绝会直接退出应用，同意状态写入 Preferences DataStore。
- 无障碍服务在 `onAccessibilityEvent`、开屏轮询、应用内弹窗扫描、节点抓取等所有入口检查协议状态；未同意时直接返回，不读取节点、不点击、不强跳，即使从系统设置强行开启服务也无效。
- “关于”页提供品牌图标、动态版本号（兼容 Android 9+ `longVersionCode`）、协议查阅入口与“撤回协议同意并停用服务”入口。
- 作者与联系方式卡片支持打开 `github.com/eternal12138`、一键复制链接，以及导出本地 JSON 调试信息（自定义规则、最近拦截记录、Android 版本与厂商型号）并调起系统分享面板。
- 协议内容包含软件性质、四项权限用途与免责声明，全部纯本地展示，不上传任何数据。

## 项目结构

```text
AdGuardSkipper/
├── app/src/main/
│   ├── AndroidManifest.xml
│   ├── assets/default_rules.json
│   ├── java/com/adguardskipper/
│   │   ├── MainActivity.kt
│   │   ├── AdGuardSkipperApp.kt
│   │   ├── data/
│   │   │   ├── AppPreferences.kt
│   │   │   ├── AppRepository.kt
│   │   │   ├── RuleRepository.kt
│   │   │   └── model/
│   │   │       ├── Models.kt
│   │   │       └── LayoutSnapshot.kt
│   │   ├── service/
│   │   │   ├── AdSkipAccessibilityService.kt
│   │   │   ├── SkipEngine.kt
│   │   │   ├── JumpInterceptor.kt
│   │   │   ├── KeepAliveHelper.kt
│   │   │   └── NotificationHelper.kt
│   │   ├── ui/
│   │   │   ├── AdGuardSkipperRoot.kt
│   │   │   ├── MainViewModel.kt
│   │   │   ├── LayoutInspectorFloatWindow.kt
│   │   │   ├── AppIcon.kt
│   │   │   ├── CialloSplashScreen.kt
│   │   │   ├── components/CialloUi.kt
│   │   │   ├── components/UserAgreementDialog.kt
│   │   │   ├── screens/AboutScreen.kt
│   │   │   ├── screens/DashboardScreen.kt
│   │   │   ├── screens/ListsScreen.kt
│   │   │   ├── screens/LogsScreen.kt
│   │   │   └── theme/Theme.kt
│   │   └── util/
│   │       ├── AccessibilityNodeUtils.kt
│   │       ├── PermissionUtils.kt
│   │       └── SystemIntents.kt
│   └── res/
│       ├── xml/accessibility_service_config.xml
│       ├── values/...
│       ├── drawable/...
│       └── mipmap-anydpi-v26/...
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradle/wrapper/...
├── design/                       # 原始插画、图标预览与界面截图
└── tools/generate_brand_assets.py # 从两张原图重新生成图标/启动图
```

## 构建

前置条件：JDK 17、Android SDK Platform 34、Build-Tools 34.0.0。

```bash
# Windows
gradlew.bat :app:assembleDebug

# macOS / Linux
./gradlew :app:assembleDebug
```

APK 输出：

```text
app/build/outputs/apk/debug/app-debug.apk
dist/CialloSkipper-1.6.0-debug.apk   # 已完成构建验证、可直接安装的调试签名包
```

安装：

```bash
adb install -r dist/CialloSkipper-1.6.0-debug.apk
```

也可以直接用 Android Studio 打开根目录运行。Android Studio 会自动生成本机的 `local.properties`；命令行构建时，其中的 `sdk.dir` 需指向本机 Android SDK。

本工程已使用 JDK 17 / Gradle 8.7 / Android SDK 34 实际执行 `:app:assembleDebug` 并完成安装启动验证。构建校验记录与 APK 哈希见 [`dist/VERIFICATION.md`](dist/VERIFICATION.md)。

## 首次使用

1. 打开应用，点击“无障碍服务 → 去开启”，在系统设置中启用 **Ciallo～跳广 开屏跳过**。
2. 建议在“电池优化”中允许忽略限制。
3. Android 13+ 建议允许通知，以便接收拦截计数和常驻守护通知。
4. 在“通知使用权”卡片中授予 Ciallo～跳广 通知访问权限，启用双服务保活。
5. 进入“名单”页。如果顶部出现“未能读取到完整应用列表”，点击提示卡片，在系统应用详情中开启“读取应用列表/应用列表”权限，然后返回刷新。
6. 如需抓取顽固广告或应用内弹窗，开启“布局抓取悬浮球”，首次会跳转悬浮窗权限设置。
7. 从桌面冷启动目标应用，等待开屏广告出现；正常使用过程中出现的插屏/弹窗由事件驱动引擎处理。

部分 OEM 系统（MIUI/HyperOS、ColorOS、OriginOS 等）会额外限制无障碍服务，需要将本应用加入电池优化白名单、允许自启动，并关闭“无障碍服务自动关闭”等选项。

## 自定义规则

1.5.0 不再读取 `assets/default_rules.json` 中的盲扫文本或坐标。所有日常规则必须通过悬浮球可视化瞄准器手动添加，并保存在 Preferences DataStore：

- **ViewId 规则**：记录规范化后的控件 ID、保存时的 Activity。
- **文本规则**：记录清洗后的按钮文字、保存时的 Activity。
- **坐标规则**：记录归一化 `(x, y)`、保存时的 Activity。

触发条件：

- 当前 Activity 必须与保存时一致（Dialog 弹窗使用宿主 Activity）。
- 同一规则在同一页面生命周期只允许点击一次；跨页面 5 秒内也不重复。
- 坐标规则只响应真实的 Dialog/Popup 窗口事件。
- 首次启动 1.5.0 会清空一次旧版本遗留的所有自定义规则；之后可在主页点击“🧹 清空所有自定义规则”随时清除。

## 保活与前台服务说明

- 常驻通知使用 `specialUse` 类型的 `FOREGROUND_SERVICE`，已声明 `FOREGROUND_SERVICE_SPECIAL_USE` 权限以及 `PROPERTY_SPECIAL_USE_FGS_SUBTYPE`。
- 通知监听服务只作为系统绑定入口，不读取、不保存、不转发任何通知内容。
- 双服务绑定能提高进程优先级，但无法对抗所有 OEM 的强制清理策略。仍需在 HyperOS / HarmonyOS / MagicOS 中允许自启动、后台运行，并关闭电池优化和“锁屏清理”。
- Android 不允许普通应用直接启用或启动无障碍服务，也无法绕过通知使用权授权。本实现通过“两个系统绑定 + 通知监听重绑 + 前台通知重断言”提高存活率，不涉及系统漏洞。
- 若用户拒绝 `POST_NOTIFICATIONS`，前台服务仍可运行，但通知可能不会显示在通知抽屉中。

## 重要说明

- 应用声明 `QUERY_ALL_PACKAGES` 和部分国产 ROM 识别的 `com.android.permission.GET_INSTALLED_APPS` 以枚举桌面 Launcher 和可启动应用。Google Play 对该权限有严格的政策限制，本工程适合侧载、企业内部分发或个人构建；如需上架 Play，应移除该权限并改用更窄的 `<queries>` 声明，同时调整应用列表功能。
- 名单页若显示“未能读取到完整应用列表”，请在系统应用详情的权限管理中开启“读取应用列表/应用列表”权限，然后返回刷新。
- 无障碍服务只能读取系统暴露的节点。使用 Surface/Flutter/Unity 自绘、或完全禁止无障碍访问的广告可能无法获取文本/ViewId，此时可通过悬浮球添加坐标规则。为避免误点，坐标规则只在绑定的 Activity 且出现真实 Dialog/Popup 窗口事件时触发。
- 3.5 秒保护期、黑名单和“未经过 Launcher”的判断都是启发式的。系统版本、OEM 定制和广告跳转方式会影响实际效果。
- 布局快照包含界面文本，可能涉及隐私。文件仅保存在 App 私有的 `files/layout_captures` 目录，不会自动上传；需要时可通过文件管理器或 `run-as` 导出。日志最多保留 100 条，可随时清空。
- 悬浮窗、通知使用权和通知权限都需要用户主动授权；应用不会绕过系统授权。
- Google Play 对 `QUERY_ALL_PACKAGES`、`SYSTEM_ALERT_WINDOW`、`FOREGROUND_SERVICE_SPECIAL_USE` 和通知监听均有严格政策，本工程面向侧载或个人使用。
- 所有数据仅保存在本机 DataStore 和私有文件目录，不包含网络权限，不上传任何界面内容。
- 请遵守目标应用的服务条款和当地法律，仅将本工具用于改善个人设备体验。

## 实现要点

- `AdSkipAccessibilityService`：会话管理、事件分流、节流、日志/通知写入、布局快照与前台通知。
- `SkipEngine`：严格的 4 秒开屏文本/SDK 规则、Activity 绑定的自定义 ViewId/文本/坐标规则、页面级单次点击锁、系统界面豁免、手势 + 节点/父节点点击。
- `JumpInterceptor`：Launcher 动态发现、3.5 秒状态机、BACK 拉回、第三方弹窗拒绝。
- `LayoutInspectorFloatWindow`：系统悬浮球、拖拽吸附、递归节点抓取、全屏可视化瞄准层、智能候选、底部确认卡片与本地规则写入。
- `KeepAliveHelper`：前台常驻通知、通知监听重绑、双服务互相刷新。
- `AppPreferences`：DataStore 原子写入、每日计数重置、JSON 日志、逐条本地规则（ViewId/文本/坐标、Activity）编解码、一次性旧规则清理与紧急清空。
- `AppRepository`：Launcher 查询 + 已安装包双通道合并、预装升级应用识别。
- `RuleRepository`：assets 规则容错解析（数组与对象两种格式）。
- UI：单 Activity、Compose Material 3、四页底部导航（主页 / 名单 / 日志 / 关于）、名单二级 Tab、StateFlow 驱动。
