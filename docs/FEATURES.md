# 功能对照表（对照产品蓝图逐条）

状态：✅ 已实现 · 🟡 部分实现（见备注） · ⏳ 未做

## 一、导入与图库管理

| # | 功能 | 实现 | 代码位置 | 状态 |
|---|---|---|---|---|
| 1.1 | 多源导入：文件夹、ZIP/RAR/7Z 压缩包 | SAF `OpenDocumentTree` / `OpenDocument`；`MediaSource` 抽象统一遍历与读流 | `core/data/source/`、`core/archive/` | ✅ |
| 1.2 | 独立图库归类；压缩包内子目录平铺 | 每个 folder/archive 一个 `libraries` 行；压缩包条目 `locator` 保留相对路径但只显示文件名 | `LibraryRepository.addFolder/addArchive`、`ArchiveScanner` | ✅ |
| 1.3 | 严格自然排序 | `NaturalOrder` 比较器 + 入库 `sortKey`（数字补零），SQL 直接按自然序排序 | `core/util/NaturalOrder.kt`、`NaturalSortKey.kt` | ✅ |
| 1.4 | 全局搜索（图库名 + 文件名 + 标签名） | 图库名 / 图片名 / 标签名三路 LIKE（转义通配符），200ms 防抖，结果分区展示 | `LibraryDao.search`、`MediaDao.search`、`TagDao.search`、`SearchViewModel` | ✅ |
| 1.5 | 下拉刷新 + 自动监听增删 | 下拉刷新 + ContentObserver + 回前台节流扫描 + WorkManager 6h 周期扫描 | `LibraryScreen`、`LibraryViewModel`、`LibraryScanWorker` | ✅ |
| 1.6 | 空文件夹 / 损坏包 / 加密包提示 | `ScanStatus` 分支 + 密码弹窗 + 重新授权按钮 | `LibraryScreen`、`FolderScanner`、`ArchiveScanner` | ✅ |

## 二、浏览与展示

| # | 功能 | 实现 | 位置 | 状态 |
|---|---|---|---|---|
| 2.1 | 图库内垂直瀑布流 | `LazyVerticalStaggeredGrid` 两列 + 按宽高比占位 | `LibraryScreen`、`PhotoTile` | ✅ |
| 2.2 | 沉浸全屏、左右滑动、缩放拖拽、单击隐藏、边缘返回、底部页码 | `HorizontalPager` + 自研 `ZoomablePhoto` + `ImmersiveViewer` + 边缘手势 | `feature/viewer/`、`core/ui/components/ZoomablePhoto.kt` | ✅ |
| 2.3 | 点页码呼出缩略图网格并跳转 | `ModalBottomSheet` + 5 列缩略图 + 自动滚到当前 | `ViewerScreen.ThumbnailSheet` | ✅ |
| 2.4 | 记忆位置 / 继续浏览提示 | `reading_progress` 表 + 图库页“继续浏览·第 N 张”chip | `MediaRepository`、`LibraryScreen` | ✅ |
| 2.5 | 幻灯片（可设间隔、随机） | 全屏自动播放；间隔可点选 2/3/5/10 秒（持久化到 DataStore），可切换随机顺序 | `ViewerScreen`、`SettingsRepository` | ✅ |
| 2.6 | 双指长按临时原图对比 | `ZoomablePhoto` 双指按住 350ms → 1x，松手恢复 | `ZoomablePhoto.kt` | ✅ |

## 三、收藏与探索

| # | 功能 | 实现 | 位置 | 状态 |
|---|---|---|---|---|
| 3.1 | 全局收藏 + 失效占位 + 一键清理 | 跨图库查询；源丢失 `isPresent=0` 灰占位；清理失效收藏 | `MediaDao.observeFavorites`、`FavoritesScreen` | ✅ |
| 3.2 | 单图库随机 / 全局随机回忆 | 全局随机 60 张 + 换一批 ✅；单图库“打乱顺序”开关未做 | `RandomScreen` | 🟡 |
| 3.3 | 星标 + 自定义标签 + 首页聚合 | 收藏 + 标签 CRUD + 标签详情/长按打标签 + 首页标签 chips + 探索页标签入口 | `feature/tags/`、`HomeScreen` | ✅ |
| 3.4 | 时光机（那年今日） | EXIF 拍摄时间优先，文件时间兜底；按月日匹配、按年份分组 | `MemoryViewModel`、`MemoryScreen` | ✅ |

## 四、信息与工具

| # | 功能 | 实现 | 位置 | 状态 |
|---|---|---|---|---|
| 4.1 | EXIF 信息面板 | 相机/镜头/ISO/光圈/快门/焦距/GPS/分辨率/大小/时间/路径 | `ExifReader`、`ViewerScreen.InfoSheet` | ✅ |
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

## 测试覆盖

- `NaturalOrderTest`：数字/多段数字/前导零/中文/大小写/前缀/空值与 null，以及 **排序键与比较器一致性**。
- `MediaDaoTest`（Robolectric）：私密图库过滤、软删除保留收藏、失效收藏清理、中文/通配符搜索、自然排序落库。
- `PHashTest`（Robolectric）：相同图距离 0、不同图距离大、hex 往返、对称性。
- `ExifDatesTest`、`MediaIdsTest`、`LikeEscapeTest`、`AppSmokeTest`。

> 说明：无真机/模拟器，功能验收依赖测试 + APK 真机走查（见 QA-checklist）。生物识别、防截屏、真实手势、SAF 供应商差异必须真机确认。
