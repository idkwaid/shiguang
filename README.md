# 拾光（Lumo）· 本地私人沉浸式相册管理器

一个**完全本地运行**的 Android 相册 App：把手机里的文件夹或 ZIP / RAR / 7Z 压缩包加进来，用瀑布流和沉浸式全屏浏览，支持收藏、标签、那年今日、随机回忆、EXIF、智能清理、轻量编辑、私密图库与防截屏。

不联网、不上传、不依赖云服务；图片索引和缩略图都在本机。

## 当前状态

| 里程碑 | 内容 | 状态 |
|---|---|---|
| M0 | 工具链自举、工程骨架、可安装 APK | ✅ |
| M1 | 文件夹导入、自然排序、瀑布流、搜索、收藏、阅读进度 | ✅ |
| M2 | ZIP / RAR / 7Z 虚拟图库、密码、损坏包、缩略图 | ✅ |
| M3 | 下拉刷新、ContentObserver、后台周期扫描、权限重授权 | ✅ |
| M4 | 沉浸全屏、捏合/双击缩放、双指长按对比、缩略图导航、幻灯片、动态取色 | ✅ |
| M5 | 收藏跨库、标签、那年今日、随机回忆、失效收藏清理 | ✅ |
| M6 | EXIF 面板、重复/相似清理、旋转/裁剪/水印/备注、分享 | ✅ |
| M7 | 私密图库、PIN + 生物识别、FLAG_SECURE、后台自动上锁 | ✅ |
| M8 | 文档、验收清单、最终构建 | 进行中 |

功能逐条对照见 [docs/FEATURES.md](docs/FEATURES.md)，真机验收见 [docs/QA-checklist.md](docs/QA-checklist.md)。

## 环境自举（本仓库自带工具链，不污染系统）

本项目在本会话环境里自举了 JDK 21 + Android SDK 37.1 + Gradle 9.6.0，全部放在 `.toolchain/`（已 gitignore）。

```bash
# 一键准备（幂等；已存在会跳过）
scripts/bootstrap-android.sh

# 之后每次构建
source scripts/env.sh
./gradlew :app:assembleDebug
```

产物：`app/build/outputs/apk/debug/app-debug.apk`（debug 签名，可直接侧载）。

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

如果用自己的 Android Studio：直接打开本目录即可（Gradle 9.6 / AGP 9.5.2 / JDK 21），`local.properties` 里的 `sdk.dir` 指向本机 SDK。

## 测试

```bash
source scripts/env.sh
./gradlew :app:testDebugUnitTest
```

覆盖：自然排序比较器与排序键一致性、MediaIds、EXIF 时间解析、LIKE 转义、Room DAO（收藏过滤/软删除/清理/搜索/自然排序）、pHash、编码边界等。
Robolectric 只跑 JVM 侧的 Android API；生物识别、防截屏、真实手势需要真机。

## 关键设计

- **图库 = 文件夹 / 压缩包**：压缩包内条目平铺展示，按“目录自然序 + 文件名自然序”排序；ZIP/7Z 直接用 SAF 文件描述符随机读取（不复制大文件），RAR 与加密 ZIP 复制到应用缓存后读取。
- **软删除**：源文件消失时条目标记 `isPresent = 0`，收藏页显示灰占位；“清理失效收藏”才真正删除。
- **扫描与富化分离**：扫描只建索引（名称/大小/时间），EXIF、尺寸、缩略图、pHash 由后台串行补齐，避免大图库卡顿。
- **防截屏**：只对私密图库与私密空间启用 `FLAG_SECURE`，普通图库不限制截屏。
- **密码**：PBKDF2-HMAC-SHA256（150k 迭代、16 字节随机盐），只存哈希；忘记密码只能重置。密码与压缩包密码都**只存在内存**，进程被杀即失效。

## 已知限制

- 不处理视频、RAW；HEIC 解码取决于设备系统是否支持。
- RAR / 加密 ZIP 需要先复制到应用缓存（同尺寸占用），普通 ZIP / 7Z 不复制。
- 压缩包内文件**不能单独删除**（不改写压缩包），清理时只能“移出索引”。
- 编辑只另存副本，永不覆盖原图；裁剪为预设比例，暂无自由拖拽裁剪框。
- 底部导航为实色（Haze 毛玻璃未启用）；缩略图→全屏为淡入过渡（未做共享元素）。
- 私密图库是应用内隐藏 + 门禁 + 防截屏，不做文件级加密，也不能阻止另一台手机拍屏。
- 单图库“打乱顺序”开关尚未实现（全局随机已可用）。
- 无云同步、无多用户。

## 目录

```
app/src/main/java/dev/lumo/gallery/
├─ core/model           领域模型
├─ core/data/db         Room 实体 / DAO / 数据库
├─ core/data/source     SAF 遍历、媒体流、压缩包流
├─ core/data/scan       文件夹/压缩包扫描、索引写入、后台 Worker
├─ core/data/repo       仓库层（图库/媒体/设置）
├─ core/archive         ZIP/RAR/7Z 读取器与缓存
├─ core/image           缩略图、EXIF、pHash、调色板
├─ core/security        PIN、生物识别、防截屏
├─ core/ui             主题、通用组件（瀑布流缩略图/图库卡片/可缩放全屏图）
├─ navigation           NavHost 与路由
└─ feature/*            首页/图库/查看器/搜索/收藏/标签/时光机/清理/编辑/私密/设置
```
