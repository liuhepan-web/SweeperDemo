# Sweeper Demo

涂鸦 Smart Life App SDK + Sweep SDK 演示工程（包名 `com.sweeper.demo.set`）。

面向开发者的统一入口：克隆本仓库 → 按下面步骤跑通 Demo → 对照文档集成到自有 App。

## 文档入口

| 文档 | 说明 |
| --- | --- |
| [操作文档](docs/Sweeper_Demo_操作文档.md) | 运行准备、整体流程图、面板 Hub / P2P 实时地图 / 激光云地图功能说明、渲染层职责、常见问题 |
| [集成文档](docs/Sweeper_SDK_集成文档.md) | 依赖（thingsmart 7.8.0 / sweeper 7.8.0 / thingsmart-ipcsdk 7.8.1）、仓库配置、AppKey 注入、SDK 初始化、P2P 与激光云地图 API 用法、地图数据管线 |

## 环境要求

- Android Studio（AGP 8.7.3）
- JDK 11
- 真机（ARM：`armeabi-v7a` / `arm64-v8a`）
- 涂鸦 IoT 平台账号与已创建 App（包名 `com.sweeper.demo.set`）

## 快速开始

1. **配置密钥**：复制 `local.properties.example` 为 `local.properties`，填入 `sdk.dir`、`appKey`、`appSecret`。
2. **放入安全算法包**：把涂鸦平台下发的 `security-algorithm-*.aar` 放进 `app/libs/`（业务 AAR 不随本仓库分发）。
3. **运行**：用 Android Studio 打开工程，连接真机直接 Run。

## 工程结构

```
app/src/main/java/com/example/sweeperdemo/
├── SweeperDemoApplication.java      # 应用入口，SDK 初始化
├── SweeperConstants.java            # 常量
├── SweeperDeviceNavigator.java      # 设备列表 → 面板跳转（品类 sd）
├── SweeperDeviceHelper.java         # 设备与配网辅助
├── ui/
│   ├── SweeperPanelActivity.java    # 面板 Hub（功能入口聚合）
│   ├── SweeperP2pMapActivity.java   # P2P 实时地图
│   └── SweeperLaserMapActivity.java # 激光云地图
└── map/                             # 地图解析 / 数据绑定 / 渲染（自建实现）
```

## 参考

- [Sweep SDK 集成文档（涂鸦开发者）](https://developer.tuya.com/cn/docs/app-development/Smart-Sweep-SDK-integrated?id=Kdjvz86ribf9l)
- [Sweeper P2P 文档（涂鸦开发者）](https://developer.tuya.com/cn/docs/app-development/android-sweeper-p2p?id=Kb2uowd98x17e)
