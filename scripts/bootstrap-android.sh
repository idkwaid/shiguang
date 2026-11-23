#!/usr/bin/env bash
# 工具链自举：项目内安装 JDK 21 + Android SDK 36 + Gradle 8.14.3 + debug 签名。
# 幂等：已存在的组件会跳过。
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TC="${ROOT}/.toolchain"
mkdir -p "${TC}" "${ROOT}/keystore"

JDK_VER="jdk-21.0.13.1+2"
GRADLE_VER="gradle-9.6.0"

download() { # url dest
  if [ -s "$2" ]; then echo "[skip] $2"; else
    echo "[get ] $1"
    curl -fL --retry 3 --progress-bar -o "$2" "$1"
  fi
}

# 1) JDK
if [ ! -d "${TC}/${JDK_VER}" ]; then
  download "https://api.adoptium.net/v3/binary/latest/21/ga/linux/x64/jdk/hotspot/normal/eclipse" "${TC}/jdk21.tar.gz"
  tar -xzf "${TC}/jdk21.tar.gz" -C "${TC}"
fi
export JAVA_HOME="${TC}/${JDK_VER}"
export PATH="${JAVA_HOME}/bin:${PATH}"

# 2) Android SDK command line tools
export ANDROID_HOME="${TC}/sdk"
export ANDROID_SDK_ROOT="${ANDROID_HOME}"
if [ ! -x "${ANDROID_HOME}/cmdline-tools/latest/bin/sdkmanager" ]; then
  download "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip" "${TC}/cmdline-tools.zip"
  unzip -q "${TC}/cmdline-tools.zip" -d "${TC}"
  mkdir -p "${ANDROID_HOME}/cmdline-tools"
  rm -rf "${ANDROID_HOME}/cmdline-tools/latest"
  mv "${TC}/cmdline-tools" "${ANDROID_HOME}/cmdline-tools/latest"
fi

yes | "${ANDROID_HOME}/cmdline-tools/latest/bin/sdkmanager" --sdk_root="${ANDROID_HOME}" --licenses >/dev/null 2>&1 || true
"${ANDROID_HOME}/cmdline-tools/latest/bin/sdkmanager" --sdk_root="${ANDROID_HOME}" \
  "platform-tools" "platforms;android-36" "build-tools;36.0.0"

# 3) Gradle 发行包（同时生成 wrapper）
if [ ! -x "${TC}/${GRADLE_VER}/bin/gradle" ]; then
  download "https://services.gradle.org/distributions/${GRADLE_VER}-bin.zip" "${TC}/gradle.zip"
  unzip -q "${TC}/gradle.zip" -d "${TC}"
fi

# 4) 项目内 debug 签名（避免依赖 ~/.android）
if [ ! -s "${ROOT}/keystore/debug.jks" ]; then
  "${JAVA_HOME}/bin/keytool" -genkeypair -v \
    -keystore "${ROOT}/keystore/debug.jks" -storetype PKCS12 \
    -alias androiddebugkey -storepass android -keypass android \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Android Debug,O=Android,C=US"
fi

# 5) local.properties
echo "sdk.dir=${ANDROID_HOME}" > "${ROOT}/local.properties"

echo "✅ 工具链就绪：JDK ${JDK_VER} / Android SDK 36 / ${GRADLE_VER}"
