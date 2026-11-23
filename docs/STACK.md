# 技术栈与实际版本（M0 实测锁定）

> 本文件记录真正解析到、构建通过的版本组合，而不是计划版本。

## 工具链（项目内自举）

| 组件 | 版本 | 位置 |
|---|---|---|
| JDK | Temurin 21.0.13.1+2 | `.toolchain/jdk-21.0.13.1+2` |
| Gradle | 9.6.0（wrapper 同版本） | `.toolchain/gradle-9.6.0` + `gradlew` |
| Android SDK Platform | 37.1（`platforms;android-37.1`） | `.toolchain/sdk/platforms/android-37.1` |
| Build Tools | 37.0.0（另有 35/36 由 AGP 自动装过） | `.toolchain/sdk/build-tools` |
| 签名 | 仓库内 `keystore/debug.jks`（androiddebugkey / android） | 不依赖 `~/.android` |

## 构建插件

| 插件 | 版本 | 备注 |
|---|---|---|
| AGP | 9.5.2 | 启用内置 Kotlin（AGP 9 默认） |
| Gradle | 9.6.0 | AGP 9.5 要求 ≥ 9.6.0 |
| Kotlin Gradle Plugin | 2.4.0 | **通过根 buildscript classpath 覆盖** AGP 内置的 2.2.10，才能读 Compose 1.13 / Coil 3.6 的 Kotlin 2.4 元数据 |
| Compose Compiler 插件 | 2.4.0 | 与 Kotlin 同版本 |
| KSP | 2.3.14 | 与 Kotlin 2.4.0 兼容，用于 Room |
| Java / Kotlin target | 17 | `compileOptions` + 默认 jvmTarget |

## Android 配置

| 项 | 值 |
|---|---|
| `applicationId` / `namespace` | `dev.lumo.gallery` |
| `minSdk` | 26（Android 8.0） |
| `targetSdk` | 36 |
| `compileSdk` | 37 + `compileSdkMinor = 1`（Compose 1.13 要求 37.1+） |
| 构建产物 | `app/build/outputs/apk/debug/app-debug.apk`（v0.2.0 / versionCode 2） |

## 依赖库

| 库 | 版本 |
|---|---|
| Compose BOM | 2026.11.01 |
| Material3 | 1.5.0（BOM 管理） |
| Compose UI / Foundation / Animation | 1.13.0（BOM 管理） |
| Activity Compose | 1.12.4 |
| Lifecycle | 2.11.1 |
| Navigation Compose | 2.10.2 |
| Room | 2.8.5 |
| DataStore Preferences | 1.3.0 |
| WorkManager | 2.12.0 |
| DocumentFile | 1.1.0 |
| ExifInterface | 1.5.0-beta02 |
| Palette | 1.1.0 |
| Biometric | 1.1.0 |
| Coil 3 | 3.6.3（coil / coil-compose / coil-gif） |
| Telephoto（预留） | 0.19.0（当前自研缩放，未使用） |
| Haze（预留） | 2.1.0（当前底部栏为实色） |
| commons-compress | 1.28.0（ZIP / 7Z，含 AES 7z） |
| junrar | 8.1.2（RAR 4–7，含加密头） |
| zip4j | 2.11.6（加密 ZIP） |
| Kotlin Coroutines | 1.11.0 |
| Robolectric | 4.18.1 |
| JUnit | 4.13.2 |

## 与计划的主要偏差（已解决）

1. **原计划 AGP 8.13 + compileSdk 36 不可行**：2026 年的 AndroidX（core 1.19、Compose 1.13、Coil 3.6 等）要求 API 37.1 + AGP 9.1+，因此升级到 AGP 9.5.2 / Gradle 9.6.0 / compileSdk 37.1。
2. **AGP 9 内置 Kotlin 2.2.10 读不了 Kotlin 2.4 元数据**：在根 `build.gradle.kts` 用 `buildscript` classpath 覆盖 KGP 到 2.4.0。
3. **KSP 从 Kotlin 版本号解耦**：使用独立版本 2.3.14，已确认兼容 AGP 9 内置 Kotlin。
4. **原计划的 libarchive 原生库未采用**：ZIP/7Z 用 commons-compress、RAR 用 junrar、加密 ZIP 用 zip4j，全部纯 JVM，可在单元测试里覆盖，也避免 16 KB page-size 风险。
5. **内存约束**：本机 3.6 GB RAM / 2 核，构建改为 `-Xmx1400m`、`org.gradle.workers.max=1`、`kotlin.compiler.execution.strategy=in-process`，构建时间约 1.5–3 分钟。
6. **`kotlin.sourceSets` 兼容**：加了 `android.disallowKotlinSourceSets=false`（AGP 9 内置 Kotlin 默认拒绝该 DSL）。
7. **v0.2.0 数据迁移**：Room 升到 v2，`libraries` 增加 `treeRootRelPath`，带 `MIGRATION_1_2`（ALTER TABLE），升级不清库。
