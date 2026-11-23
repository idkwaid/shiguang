# 架构说明（拾光 / Lumo）

## 分层

```
UI (Compose)
  feature/*  ── ViewModel ──► repo ──► Room DAO
                              │
                              ├─► Scanner（文件夹 / 压缩包）
                              ├─► MediaStreamProvider（SAF 流 / 压缩包条目流）
                              ├─► Thumbnailer / ImageEnricher / ExifReader / PHash
                              └─► ArchiveSessionStore（ZIP/RAR/7Z 读取器缓存）
```

- **单 Activity + Compose**，手写 `AppContainer` 注入依赖（无 Hilt）。
- **Navigation Compose** 字符串路由：`home / library/{id} / viewer/{lib}/{item} / search / favorites / explore / tags / tag/{id}/{name} / memory / random / cleanup / editor/{lib}/{item} / private / settings`。
- ViewModel 只依赖仓库，不碰 `ContentResolver`；SAF 细节留在 `core/data/source`。

## 数据模型（Room v1）

| 表 | 关键字段 | 说明 |
|---|---|---|
| `libraries` | id, type(FOLDER/ARCHIVE), name, treeUri/archiveUri, archiveFormat, isPrivate, isAvailable, sortMode, itemCount, coverItemId, scanStatus | 一个文件夹或压缩包 = 一个图库 |
| `media_items` | id(sha1(libraryId+locator)), libraryId, locator, docUri, displayName, mime, sizeBytes, lastModified, dateTaken/dateSource, width/height/orientation, note, isFavorite, **isPresent**, thumbReady, phash, sortKey | `isPresent` 软删除支撑“失效收藏灰占位” |
| `tags` / `media_tags` | id, name, colorArgb / (mediaId, tagId) | 星标之外的自定义标签，级联删除 |
| `reading_progress` | libraryId PK, mediaId, position | 每个图库的浏览进度 |

排序：扫描时写入 `sortKey = NaturalSortKey.of(displayName)`（数字补零到 12 位），SQL `ORDER BY sortKey`；压缩包条目用 `ofPath(locator)` 保留目录分组。`NaturalOrder` 比较器与 `sortKey` 的一致性由单测锁定。

## 扫描管线

1. `LibraryRepository.scan()` → `ScanCoordinator` 按类型派发 `FolderScanner` / `ArchiveScanner`。
2. **索引**：遍历得到 `ScannedEntry` 列表；`MediaIndexer` 与库内快照 diff：
   - 大小/修改时间没变 → 保留缩略图、EXIF、收藏、备注；
   - 新条目 → 插入，`thumbReady = false`；
   - 消失条目 → `isPresent = 0`（软删除）；
   - 事务内先 `markAllAbsent` 再 upsert 当前条目，天然幂等。
3. **富化**：`ImageEnricher` 每个图库一个串行协程，挑 `thumbReady = 0` 的条目：
   - `Thumbnailer`：bounds 采样 → 解码 → EXIF 方向校正 → 480px JPEG（`cache/thumbs`）；
   - 同时写入宽高/方向/拍摄时间（EXIF 优先，回退文件时间）；
   - `PHash.fromFile(thumb)` 写入 `phash`，供相似图清理。
4. **进度**：`ScanProgressBus`（`StateFlow<Map<libraryId, ScanProgress>>`），首页卡片与图库页共用。
5. **后台**：`LibraryScanWorker` 每 6 小时扫一次可用图库；前台靠 ContentObserver + resume 节流扫描。

## 压缩包管线

```
SAF Uri ──► SafFiles.openChannel (ParcelFileDescriptor → FileChannel)
             ├─ ZIP  : commons-compress ZipFile（随机读，不复制）
             ├─ 7Z   : commons-compress SevenZFile（随机读，密码）
             └─ RAR  : 先复制到 cache/archive-stage，junrar（密码/加密头）
ZIP 检测到加密条目 → 复制本地文件 → zip4j 解密读取
```

- `ArchiveSessionStore`：每图库缓存一个 `ArchiveReader`，密码只存内存；打开用 `Mutex` 串行化。
- `ArchiveStreamProvider`：条目先解到 `cache/archive-entries/<lib>/<sha1>_<mtime>.bin`，返回 `FileInputStream`，保证同一张图能被 EXIF/缩略图/大图多次读取；LRU 淘汰（默认 512 MB）。
- 图库删除时一并清理 staging、entry cache、缩略图与阅读进度。

## 全屏查看器

- `HorizontalPager` + `ZoomablePhoto`（自研手势）：捏合 1–6x、双击 2.5x、拖拽边界回弹、双指按住 350ms 临时 1x 对比；1x 时不消费横向手势，交给 Pager。
- 沉浸：`WindowInsetsController` 隐藏系统栏 + `FLAG_KEEP_SCREEN_ON`；离开恢复。
- 单击切换 chrome；左边缘右滑 / 下拉返回（`awaitEachGesture` 判断位移）。
- 缩略图面板、EXIF 信息面板、幻灯片（间隔可点选 2/3/5/10s，可随机）都在同一路由内的 `ModalBottomSheet`。
- 动态取色：`PaletteExtractor` 从 1600px 预览抽主色，用于页码胶囊与幻灯片控件。

## 安全

- PIN：PBKDF2-HMAC-SHA256（150k 迭代、16B 盐、256 位），DataStore 只存盐 + 哈希；5 次失败冷却 30s。
- 生物识别：`androidx.biometric`，`BIOMETRIC_WEAK | DEVICE_CREDENTIAL`；绑定 `FragmentActivity`（MainActivity 为 AppCompatActivity）。
- 会话：`PrivateAccess` 内存状态；`ProcessLifecycleOwner.onStop` 自动上锁。
- 防截屏：`SecureScreen(true)` → `FLAG_SECURE` + API 33 `setRecentsScreenshotEnabled(false)`，离开恢复。
- 私密图库过滤：`LibraryDao.observeVisible`（`isPrivate = 0`）与 `MediaDao` 搜索/收藏/随机的 JOIN 过滤。

## 关键取舍

- **不引入 Hilt**：手写容器，减少 KSP/编译时间与 AGP 兼容风险。
- **纯 JVM 归档库**：可在单测里覆盖 ZIP/7Z 读取，避免原生库 16 KB page-size 与 JVM 不可测问题。
- **软删除**：比“收藏表独立”更简单，也天然支持“源恢复后自动复活”。
- **压缩包条目落盘缓存**：牺牲一点磁盘换取可重复读、无并发句柄问题与翻页流畅。
- **无 paging 库**：个人图库规模下 `Flow<List<...>>` + `LazyGrid` 足够；如后续 5 万张以上再换 Paging3。
