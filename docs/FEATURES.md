# 功能对照表（对照产品蓝图逐条）

状态：✅ 已实现 · 🟡 部分实现（见备注） · ⏳ 未做

## 一、导入与图库管理

| # | 功能 | 实现 | 代码位置 | 状态 |
|---|---|---|---|---|
| 1.1 | 多源导入：文件夹、文件夹内压缩包、ZIP/RAR/7Z 压缩包 | SAF `OpenDocumentTree` / `OpenDocument`；分组导入会识别选中目录里的 `.zip/.rar/.7z` 并各建虚拟图库；`MediaSource` 抽象统一遍历与读流 | `core/data/source/`、`core/archive/`、`LibraryRepository.addFolderGrouped` | ✅ |
| 1.2 | 独立图库归类；按嵌套结构建层级图库；父库不含子库图片 | 分组导入镜像目录树；DIRECT 只扫当前目录直接文件（v0.6.0 修复多扫一层的问题）；打开时自动校准 | `LibraryRepository.addFolderGrouped`、`FolderScanner` | ✅ |
| 1.3 | 严格自然排序 | `NaturalOrder` 比较器 + 入库 `sortKey`（数字补零），SQL 直接按自然序排序 | `core/util/NaturalOrder.kt`、`NaturalSortKey.kt` | ✅ |
| 1.4 | 全局搜索（图库名 + 文件名 + 标签名） | 图库名 / 图片名 / 标签名三路 LIKE（转义通配符），200ms 防抖，结果分区展示 | `LibraryDao.search`、`MediaDao.search`、`TagDao.search`、`SearchViewModel` | ✅ |
| 1.5 | 下拉刷新 + 自动监听增删 | 下拉刷新 + ContentObserver + 回前台节流扫描 + WorkManager 6h 周期扫描 | `LibraryScreen`、`LibraryViewModel`、`LibraryScanWorker` | ✅ |
| 1.6 | 空文件夹 / 损坏包 / 加密包提示 | `ScanStatus` 分支 + 密码弹窗 + 重新授权按钮 | `LibraryScreen`、`FolderScanner`、`ArchiveScanner` | ✅ |

## 二、浏览与展示

| # | 功能 | 实现 | 位置 | 状态 |
|---|---|---|---|---|
| 2.1 | 图库内垂直瀑布流 | `LazyVerticalStaggeredGrid` 两列 + 按宽高比占位 | `LibraryScreen`、`PhotoTile` | ✅ |
| 2.2 | 沉浸全屏、左右滑动、缩放拖拽、边缘返回、中央点击底部页码滑杆 | `HorizontalPager` + 自研 `ZoomablePhoto`；v0.5.0 移除双指长按对比（修缩放被重置），缩放/位移手势不再被打断 | `feature/viewer/`、`core/ui/components/ZoomablePhoto.kt` | ✅ |
| 2.3 | 页码快速跳转 | v0.3.0 起改为底部拖动滑杆直接跳页；缩略图网格入口已移除 | `ViewerScreen` | ✅ |
| 2.4 | 记忆位置 / 继续浏览提示 | `reading_progress` 表 + 图库页“继续浏览·第 N 张”chip | `MediaRepository`、`LibraryScreen` | ✅ |
| 2.5 | 幻灯片（可设间隔、随机） | 全屏自动播放；间隔可点选 2/3/5/10 秒（持久化到 DataStore），可切换随机顺序 | `ViewerScreen`、`SettingsRepository` | ✅ |
| 2.6 | 双指长按对比原图 | 按 v0.5.0 反馈移除（与双指缩放冲突，导致缩放中途被重置） | — | ❌ 已删除 |

## 三、收藏与探索

| # | 功能 | 实现 | 位置 | 状态 |
|---|---|---|---|---|
| 3.1 | 全局收藏 + 失效占位 + 一键清理 | 跨图库查询；源丢失 `isPresent=0` 灰占位；清理失效收藏 | `MediaDao.observeFavorites`、`FavoritesScreen` | ✅ |
| 3.2 | 随机 | 跟随当前图库：只在当前库的直接图片/直接子库中随机；首页随机顶层图库 | `HomeScreen`、`LibraryScreen` | ✅ |
| 3.3 | 星标 + 自定义标签 + 图库收藏 | 图片右下角爱心；图库卡片右下角爱心（收藏图库）；标签 CRUD、首页标签 chips、标签详情 | `PhotoTile`、`LibraryCard/LibraryTile`、`FavoritesScreen`、`feature/tags/` | ✅ |
| 3.4 | 那年今日 | 按 v0.4.0 需求移除（页面/入口/代码均删除） | — | ❌ 已删除 |

## 四、信息与工具

| # | 功能 | 实现 | 位置 | 状态 |
|---|---|---|---|---|
| 4.1 | EXIF 信息面板 | 图库网格长按「信息」弹窗：相机/镜头/ISO/光圈/快门/焦距/GPS/尺寸/大小/时间/路径/备注 | `ExifReader`、`LibraryScreen.ImageInfoDialog` | ✅ |
| 4.2 | 智能清理：重复 / 相似 / 按格式 | size 分桶 + SHA-256；pHash 分带 + 并查集（汉明 ≤8）；GIF/HEIC 筛选；删除语义区分文件夹与压缩包 | `feature/cleanup/`、`PHash` | ✅ |
| 4.3 | 轻量编辑：旋转/裁剪/水印/备注 | 旋转、水平/垂直翻转、预设比例裁剪、文字水印（位置/透明度）、备注；只另存副本 | `feature/editor/` | 🟡 |
| 4.4 | 长按快捷菜单 | 收藏 / 标签 / 分享 / 删除（压缩包仅移出索引） | `LibraryScreen` | ✅ |

## 五、隐私与安全

| # | 功能 | 实现 | 位置 | 状态 |
|---|---|---|---|---|
| 5.1 | 私密图库（隐藏 + 密码/生物识别） | 首页隐藏、私密空间入口、PIN（PBKDF2 150k）+ BiometricPrompt、后台自动上锁 | `core/security/`、`feature/private/` | ✅ |
| 5.2 | 防截屏 | 私密界面 `FLAG_SECURE` + API 33 最近任务截图禁用；离开自动解除 | `SecureScreen.kt` | ✅ |

## 六、视觉与交互

| # | 功能 | 实现 | 位置 | 状态 |
|---|---|---|---|---|
| 6.1 | 极简风格 + 毛玻璃底栏 | 极简浅色/深色主题 ✅；底部栏目前为实色，Haze 毛玻璃未启用 | `core/ui/theme/`、`navigation/AppRoot.kt` | 🟡 |
| 6.2 | 动态主题取色 | 取当前图片主色，用于页码胶囊与幻灯片控件（Palette） | `PaletteExtractor`、`ViewerScreen` | ✅ |
| 6.3 | 流畅动画：进入全屏、飘心、进度条 | 全屏淡入、图片解码占位、扫描进度条、收藏按钮弹性动画 + 收藏时飘心上浮；共享元素过渡仍用淡入替代 | `ViewerScreen` | ✅ |
| 6.4 | 加载占位 / 渐显 | 缩略图占位、解码占位、渐进加载；Coil 自带渐显 | `PhotoTile`、`ZoomablePhoto` | ✅ |

## 运行日志（v0.3.0）

| 功能 | 实现 | 位置 |
|---|---|---|
| 文件日志 + 轮转 + 崩溃落盘 | `AppLog`（1MB × 3 份，Logcat 同步） | `core/util/AppLog.kt` |
| 应用内查看/分享/清空 | 设置 → 运行日志 | `feature/logs/LogsScreen.kt` |

## 多选与文件操作（v0.6.0）

| 功能 | 实现 | 位置 |
|---|---|---|
| 多选图库 | 首页右上角「多选」→ 批量收藏/移动/删除 | `HomeScreen` |
| 多选图片/子图库 | 图库页「多选」→ 选择图片或子图库后批量操作 | `LibraryScreen` |
| 实际文件开关 | 设置 → 移动/删除应用到实际文件夹 | `SettingsRepository`、`LibraryRepository` |
| 移动图片 | SAF `moveDocument` 真移动；关闭开关则只改索引 | `LibraryRepository.moveImages` |
| 移动图库 | 同树子图库尝试移动真实文件夹；其余调整层级 | `LibraryRepository.moveLibraries` |
| 安全删除 | 顶层图库只移除索引；压缩包内部只改索引 | `LibraryRepository.deleteLibraries/deleteImages` |

## 缩略图与图库卡片（v0.5.0）

| 功能 | 实现 | 位置 |
|---|---|---|
| 缩略图清晰度设置 | 256/384/512px 三档，默认低清；切换后清缓存重建 | `SettingsScreen`、`ImageEnricher`、`ThumbnailQuality` |
| 子图库网格 | 图库页把子图库卡片与照片放在同一个瀑布流网格 | `LibraryScreen`、`LibraryTile` |
| 图库卡片数量/收藏 | 右上角照片数，右下角图库收藏爱心 | `LibraryCard.kt` |
| 收藏页图库分区 | 「收藏的图库」+「收藏的图片」 | `FavoritesScreen` |

## 导航与入口（v0.4.0）

- 底部 Tab：图库 / 收藏 / 设置（探索页已删除）。
- 首页右上角：私密锁、标签、随机、导入压缩包、搜索。
- 图库页右上角：随机（仅在当前库范围内）、刷新、更多。
- 设置：私密空间、智能清理、运行日志、缩略图缓存、关于。

## 测试覆盖

- `NaturalOrderTest`：数字/多段数字/前导零/中文/大小写/前缀/空值与 null，以及 **排序键与比较器一致性**。
- `MediaDaoTest`（Robolectric）：私密图库过滤、软删除保留收藏、失效收藏清理、中文/通配符搜索、自然排序落库。
- `PHashTest`（Robolectric）：相同图距离 0、不同图距离大、hex 往返、对称性。
- `ThumbnailerTest`：有效 JPEG 能生成缩略图（回归 `inJustDecodeBounds` 返回值误判）。
- `ExifDatesTest`、`MediaIdsTest`、`LikeEscapeTest`、`AppSmokeTest`。

> 说明：无真机/模拟器，功能验收依赖测试 + APK 真机走查（见 QA-checklist）。生物识别、防截屏、真实手势、SAF 供应商差异必须真机确认。
