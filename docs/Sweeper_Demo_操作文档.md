# Sweeper_Demo · 操作文档

> 与当前仓库代码同步；集成细节见 [Sweeper_SDK_集成文档.md](./Sweeper_SDK_集成文档.md)

---

## 1. 运行和准备

1. Android Studio 打开工程目录 `SweeperDemo`。
2. `app/libs/` 放入平台下发的 **安全算法 AAR**（当前已放 `security-algorithm-1.0.0-beta.aar`；业务 AAR 不随公开仓库分发，集成方自行从 IoT 平台获取，见 `app/libs/README.md`）。
3. IoT 平台 App 包名设为 `com.sweeper.demo.set`，并绑定当前调试证书 **SHA256**；AppKey/AppSecret 写入 `local.properties`（`app/build.gradle.kts` 会读取并注入 manifestPlaceholders）。
4. 配 `local.properties`（勿提交）：
   ```bash
   cp local.properties.example local.properties
   ```
   ```properties
   sdk.dir=/path/to/android/sdk
   appKey=你的_AppKey
   appSecret=你的_AppSecret

   # 可选：自定义调试签名（不配则用 build.gradle.kts 里写死的 debug 签名）
   KEYSTORE_FILE=/path/to/keystore
   KEYSTORE_PASSWORD=你的_密码
   KEY_ALIAS=key0
   KEY_PASSWORD=你的_密码
   ```
5. 真机调试（ABI：`armeabi-v7a` / `arm64-v8a`）；模拟器难以完整验证 **P2P 实时地图** 连接。
6. 运行 Debug，启动页为 **登录页**（来自 `:base` 模块 `com.ipc.demo:base`）。
   登录 → 设备列表 → 点击扫地机设备进入 **Sweeper 面板**（导航由 `SweeperDeviceNavigator` 按设备品类 `sd` 自动识别，非扫地机品类显示兜底标签）。

---

## 2. 整体流程

```text
SweeperDemoApplication
  ├─ BaseSdk.init(this, true)          # :base 提供登录 / 家庭 / 设备列表
  └─ ThingOptimusSdk.init(this)        # Sweeper SDK 容器
          │
LoginActivity（:base，LAUNCHER 入口）
  └─ 设备列表（:base）
       └─ SweeperDeviceNavigator.openDevice()   # 品类含 sd / sweep / vacuum / robot
            └─ SweeperPanelActivity             # 面板 Hub：设备信息 + 两个地图入口
                 ├─ SweeperP2pMapActivity       # P2P 实时地图
                 └─ SweeperLaserMapActivity     # 激光云地图
```

> 登录 / 控制顶栏包名 `:base`，导航路径来自 IPC Demo 风格的基础模块，功能以本工程按钮为准。

---

## 3. 功能说明

### 3.1 面板 Hub（SweeperPanelActivity）

- 顶部显示设备名 / 在线状态（在线绿、离线红）/ `devId` 与 `category` 元信息。
- 设备不存在或离线时给出降级提示，不崩溃。
- 两张入口卡片：**P2P 地图**（`cardP2p`）与 **激光云地图**（`cardLaser`），点击均携带 `deviceId` 跳转。

### 3.2 P2P 实时地图（SweeperP2pMapActivity）

- 进入页面**自动连接**并开始拉流（`connectAndDownload`），无需手动点开始。
- `btnStart` / `btnStop` 可手动开始/停止下载。
- 通过 `IThingSweeperP2P` 回调接收地图 / 路径字节流（`SweeperP2PDataCallback`，`DownloadType`），交给 `SweeperMapBinder` 实时渲染。
- `onDestroy` 里停止下载并 `onDestroyP2P()` 释放，避免 P2P 通道泄漏。
- 缺 `devId` 或 SDK 未初始化时显示对应错误文案；`btnRefresh` / `btnHistory` 在此页隐藏（P2P 无历史）。

### 3.3 激光云地图（SweeperLaserMapActivity）

- 进入页面自动执行 `initCloudAndListen`：初始化 **OSS 云配置**（`IThingCloudConfigCallback`，拿 bucket）→ 拉取**当前地图/路径文件** → 启动**路径更新监听**（`IThingSweeperDataListener`）。
- `btnRefresh` 手动重拉当前路径（`loadCurrentPath`）。
- `btnHistory` 拉取**历史记录**（`SweeperHistoryBean` / `SweeperHistory`），弹窗选择后展示对应轨迹。

### 3.4 地图渲染层（`map/` 包）

| 类 | 职责 |
| --- | --- |
| `SweeperMapParser` | 解析扫地机地图字节流（地图 / 路径 / 房间等帧数据） |
| `SweeperMapModel` | 解析结果的内存模型 |
| `SweeperMapView` | 自绘 View，按模型绘制地图与轨迹 |
| `SweeperMapBinder` | 把 Activity 的回调接到 `SweeperMapView` + 空态/信息文案管理 |

---

## 4. 常见问题

| 现象 | 排查 |
| --- | --- |
| 地图空白 | 确认设备在线、品类正确（`sd`）；P2P 需真机且与设备同一网络环境更易通 |
| 提示 SDK 未就绪（`sweeper_sdk_missing`） | 看 `ThingOptimusSdk init failed` 日志；检查 AppKey/Secret 是否与包名 `com.sweeper.demo.set` 匹配 |
| 登录失败 | IoT 平台包名 / 证书 SHA256 与实际运行签名不一致；`local.properties` 里 appKey/appSecret 未配 |
| 编译期 so / META-INF 冲突 | `app/build.gradle.kts` 已配 `pickFirsts`（`libc++_shared.so` 等）与资源 exclude，勿随意删除 |
| 设备列表不显示扫地机入口 | `SweeperDeviceHelper.isSweeper` 按品类关键词匹配，确认产品品类 |

---

## 5. 参考

- 集成细节：[Sweeper_SDK_集成文档.md](./Sweeper_SDK_集成文档.md)
- 涂鸦官方文档：
  - [Smart Sweep SDK 集成](https://developer.tuya.com/cn/docs/app-development/Smart-Sweep-SDK-integrated?id=Kdjvz86ribf9l)
  - [扫地机 P2P](https://developer.tuya.com/cn/docs/app-development/android-sweeper-p2p?id=Kb2uowd98x17e)
  - [激光扫地机](https://developer.tuya.com/cn/docs/app-development/lasersweeper?id=Ka6o1iaxtu71o)
