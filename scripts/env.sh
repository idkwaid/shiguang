#!/usr/bin/env bash
# 拾光构建环境：项目内自举，不依赖系统 JDK / Android SDK。
# 用法：source scripts/env.sh
set -a

SHIGUANG_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
export SHIGUANG_ROOT

# 固定版本，随 bootstrap-android.sh 安装
JDK_DIR="${SHIGUANG_ROOT}/.toolchain/jdk-21.0.13.1+2"
GRADLE_BIN="${SHIGUANG_ROOT}/.toolchain/gradle-9.6.0/bin"

export JAVA_HOME="${JAVA_HOME_OVERRIDE:-$JDK_DIR}"
export ANDROID_HOME="${ANDROID_HOME_OVERRIDE:-${SHIGUANG_ROOT}/.toolchain/sdk}"
export ANDROID_SDK_ROOT="${ANDROID_HOME}"
export GRADLE_USER_HOME="${SHIGUANG_ROOT}/.gradle-home"
export PATH="${JAVA_HOME}/bin:${GRADLE_BIN}:${PATH}"

set +a
