# 部署与自动构建（GitHub Actions）

仓库：<https://github.com/idkwaid/shiguang>

## 工作流

`.github/workflows/android.yml`：

| 触发 | 行为 |
|---|---|
| push 到 `main` | 装 JDK 21 / Android SDK 37.1 + build-tools 37.0.0 → `assembleDebug` + 单元测试 → 上传 `shiguang-debug-apk` artifact |
| Pull Request | 同上（构建 + 测试，不发布） |
| push `v*` tag | 构建成功后创建 GitHub Release，附加 `app-debug.apk` |
| 手动 | Actions 页面 Run workflow |

CI 使用仓库内的 `keystore/debug.jks` 做 debug 签名（该文件是公开的 Android debug 密钥，只用于调试包）。Release 产物目前也是 debug 签名，安装时若与旧包冲突请先卸载或用同签名覆盖安装。

## 本地构建

```bash
scripts/bootstrap-android.sh     # 首次：项目内安装 JDK21 / SDK37.1 / Gradle9.6
source scripts/env.sh
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

APK：`app/build/outputs/apk/debug/app-debug.apk`

## 发布新版本

```bash
# 修改 app/build.gradle.kts 的 versionCode / versionName 后
git add -A && git commit -m "release: vX.Y.Z"
git tag vX.Y.Z
git push origin main --tags
```

推送 tag 后 Actions 的 `release` job 会自动上传 APK 到 Releases。

## 安全提示

- 不要把 GitHub Personal Access Token 写进仓库、脚本或 Gradle 文件；本工作流不需要任何 token 以外的仓库密钥（由 GitHub 自动注入）。
- Token 只应保存在本机凭据管理器（credential helper）或临时使用；泄漏后立即 revoke 并重新生成。
- 这台电脑与他人共用：如果使用 `git config --global credential.helper`，会共享凭据。建议在本仓库使用局部配置：

```bash
git config --local credential.helper ''
# 或者只在单次命令里使用：git -c credential.helper= push ...
```
