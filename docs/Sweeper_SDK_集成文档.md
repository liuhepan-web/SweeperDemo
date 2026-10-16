# Sweeper_SDK · 集成文档

> 与当前仓库代码同步；运行步骤见 [Sweeper_Demo_操作文档.md](./Sweeper_Demo_操作文档.md)

---

## 1. 依赖概览

本工程在涂鸦 **Sweep SDK** 之上做 Demo 层封装，另依赖一个 `:base` 业务模块提供登录/设备列表。

| 依赖 | 坐标 / 版本 | 说明 |
| --- | --- | --- |
| Sweep SDK | `com.thingclips.smart:sweeper:7.8.0` | 扫地机业务 SDK（云地图、历史） |
| IPC SDK | `com.thingclips.smart:thingsmart-ipcsdk:7.8.1` | P2P 通道基于 IPC P2P |
| 涂鸦基础包 | `com.thingclips.smart:thingsmart:7.8.0` | Home SDK（设备、家庭） |
| LZ4 | `org.lz4:lz4-java:1.8.0` | 地图数据解压 |
| :base 模块 | `com.ipc.demo:base:1.0.0` | 登录页 / 设备列表 / 导航接口（`DeviceNavigator`） |
| 安全算法 AAR | `app/libs/security-algorithm-1.0.0-beta.aar` | 平台下发的本地 AAR，`fileTree(libs)` 引入 |

版本统一在 `gradle/libs.versions.toml` 管理（AGP 8.7.3，compile/targetSdk 35，minSdk 24，JDK 11）。

---

## 2. 仓库与依赖配置

`settings.gradle.kts` 需包含涂鸦 maven（工程已配好）：

```kotlin
maven { url = uri("https://maven-other.tuya.com/repository/maven-releases/") }
maven { url = uri("https://maven-other.tuya.com/repository/maven-commercial-releases/") }
maven { url = uri("https://maven.aliyun.com/repository/public") }
google()
mavenCentral()
```

`app/build.gradle.kts` 关键配置：

```kotlin
// 本地 AAR
implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))

// 涂鸦 Sweep / IPC
implementation(libs.thingsmart.sweeper)
implementation(libs.thingsmart.ipcsdk)
implementation(libs.lz4.java)

// :base 登录/设备列表
implementation("com.ipc.demo:base:1.0.0")

// 规避已知冲突
configurations.all {
    exclude(group = "com.thingclips.smart", module = "thingsmart-modularCampAnno")
}

defaultConfig {
    multiDexEnabled = true
    ndk { abiFilters += listOf("armeabi-v7a", "arm64-v8a") }
}

packaging {
    jniLibs {
        pickFirsts += listOf(
            "lib/*/libc++_shared.so", "lib/*/libyuv.so", "lib/*/libopenh264.so",
            "lib/*/liblog.so", "lib/*/libv8wrapper.so", "lib/*/libv8android.so"
        )
    }
}
```

---

## 3. AppKey / AppSecret 注入

`local.properties`（不提交）中配置，Gradle 读取后注入 manifestPlaceholders：

```kotlin
val properties = Properties()
val localFile = rootProject.file("local.properties")
if (localFile.exists()) properties.load(localFile.inputStream())
manifestPlaceholders["TUYA_SMART_APPKEY"] = properties.getProperty("appKey", "")
manifestPlaceholders["TUYA_SMART_SECRET"] = properties.getProperty("appSecret", "")
```

Manifest 侧对应：

```xml
<meta-data android:name="THING_SMART_APPKEY"  android:value="${TUYA_SMART_APPKEY}" />
<meta-data android:name="THING_SMART_SECRET" android:value="${TUYA_SMART_SECRET}" />
```

> 注意：IoT 平台的 App 包名必须与 `applicationId`（`com.sweeper.demo.set`）一致，且绑定调试证书 SHA256，否则登录/设备接口校验失败。
> AppKey/Secret 已移到 `local.properties`（git 忽略），manifest 仅保留 `${TUYA_SMART_APPKEY}` 占位符，不随仓库泄露；clone 后自行在 `local.properties` 填入自己的凭证。

---

## 4. 权限

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
<uses-permission android:name="android.permission.CHANGE_WIFI_STATE" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
```

---

## 5. SDK 初始化

`SweeperDemoApplication`：

```java
public class SweeperDemoApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // 1. 注册设备导航器：:base 设备列表点击时回调 openDevice
        BaseHub.setDeviceNavigator(new SweeperDeviceNavigator());
        // 2. 初始化 :base（登录 / 家庭 / 设备列表）
        BaseSdk.init(this, true);
        // 3. 初始化涂鸦业务容器（Sweeper Kit 挂在 OptimusSdk 下）
        try {
            ThingOptimusSdk.init(this);
        } catch (Throwable t) {
            Log.e(TAG, "ThingOptimusSdk init failed", t);
        }
    }
}
```

初始化顺序不能颠倒：`ThingOptimusSdk` 依赖 AppKey 元数据，`BaseSdk` 提供 `DeviceNavigator` 回调宿主。

---

## 6. 核心 API 用法

### 6.1 设备识别与跳转

```java
// 缓存设备 bean
DeviceBean bean = ThingHomeSdk.getDataInstance().getDeviceBean(devId);

// 品类判断：category/categoryCode 含 sd / sweep / vacuum / robot 视为扫地机
// SweeperDeviceHelper.isSweeper(devId)

// :base 设备列表点击回调
public void openDevice(@NonNull Activity activity, @NonNull String devId) {
    Intent intent = new Intent(activity, SweeperPanelActivity.class);
    intent.putExtra(SweeperConstants.EXTRA_DEV_ID, devId);
    activity.startActivity(intent);
}
```

### 6.2 P2P 实时地图

参考官方：[扫地机 P2P](https://developer.tuya.com/cn/docs/app-development/android-sweeper-p2p?id=Kb2uowd98x17e)

```java
IThingSweeperKitSdk kit = ThingOptimusSdk.getManager(IThingSweeperKitSdk.class);
IThingSweeperP2P sweeperP2P = kit.getSweeperP2PInstance(devId);

// 连接 + 订阅地图/路径字节流（DownloadType 控制下载类型）
sweeperP2P.connectSweeperP2P(new SweeperP2PCallback() { /* 连接状态 */ });
sweeperP2P.startDownloadSweeperP2P(DownloadType.MAP_AND_PATH, new SweeperP2PDataCallback() {
    @Override public void onSweeperP2PDataReceived(byte[] data, int type) {
        // 交给 SweeperMapParser 解析 → SweeperMapView 渲染
    }
});

// 停止并释放（onDestroy 必须调用）
sweeperP2P.stopDownloadSweeperP2P();
sweeperP2P.onDestroyP2P();
```

### 6.3 激光云地图

参考官方：[激光扫地机](https://developer.tuya.com/cn/docs/app-development/lasersweeper?id=Ka6o1iaxtu71o)

```java
IThingSweeperKitSdk kitSdk = ThingOptimusSdk.getManager(IThingSweeperKitSdk.class);
IThingSweeperKit sweeperKit = kitSdk.getSweeperInstance();

// 1) 初始化云配置（拿 OSS bucket，供文件下载）
sweeperKit.getSweeperCloudConfig(devId, new IThingCloudConfigCallback() { /* bucket */ });

// 2) 注册数据监听：当前地图文件、路径增量
sweeperKit.onSweeperDataListener(devId, new IThingSweeperDataListener() {
    @Override public void onSweeperData(SweeperDataBean bean) { /* 解析渲染 */ }
    @Override public void onSweeperPath(SweeperPathBean path) { /* 追加轨迹 */ }
});
// 字节级监听：IThingByteDataListener

// 3) 历史记录
sweeperKit.getSweeperHistory(devId, new IThingResultCallback<SweeperHistoryBean>() { /* SweeperHistory 列表 */ });
```

---

## 7. 地图数据管线（本工程实现）

```text
SDK 回调（字节流 / 文件 URL）
   │
SweeperMapParser          # 解帧：地图矩阵 / 路径点 / 房间信息（LZ4 解压）
   │  → SweeperMapModel   # 归一化模型
SweeperMapView            # 自绘渲染（Canvas）
   │
SweeperMapBinder          # Activity ↔ View 桥接：状态文案、空态、线程调度
```

集成到其他工程时，可直接复用 `map/` 包四个类（无 Activity 依赖），只需按你的 UI 宿主接 `SweeperMapBinder`。

---

## 8. 混淆

`proguard-rules.pro` 保持默认；release 若开启 minify，需按涂鸦官方文档追加 Sweep/IPCSdk 的 keep 规则（本 Demo release 未开 minify）。

---

## 9. 官方文档索引

- [Smart Sweep SDK 集成](https://developer.tuya.com/cn/docs/app-development/Smart-Sweep-SDK-integrated?id=Kdjvz86ribf9l)
- [扫地机 P2P](https://developer.tuya.com/cn/docs/app-development/android-sweeper-p2p?id=Kb2uowd98x17e)
- [激光扫地机](https://developer.tuya.com/cn/docs/app-development/lasersweeper?id=Ka6o1iaxtu71o)
