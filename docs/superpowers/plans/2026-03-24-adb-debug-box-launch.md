# 电视盒子 ADB 唤醒与调试启动 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将默认 IPTV 源改为指定的 `test.m3u`，并把最新调试包安装到 `192.168.8.150` 电视盒子，在必要时唤醒设备后启动应用。

**Architecture:** 这次工作不新增模块，只修改默认源常量并复用现有 Android TV 启动入口。设备侧通过 ADB 完成连接、唤醒、安装、清数据和启动，以保证盒子使用新的默认直播源而不是旧的本地配置。

**Tech Stack:** Kotlin、Android Gradle、ADB、Android TV Leanback

---

### Task 1: 修改默认 IPTV 源常量

**Files:**
- Modify: `app/src/main/java/top/yogiczy/mytv/data/utils/Constants.kt`

- [ ] **Step 1: 确认默认源常量入口**

Run: `sed -n '1,80p' app/src/main/java/top/yogiczy/mytv/data/utils/Constants.kt`
Expected: 能看到 `IPTV_SOURCE_URL` 常量定义。

- [ ] **Step 2: 写入最小实现**

将 `Constants.IPTV_SOURCE_URL` 改为 `http://192.168.8.8:9000/iptv/sh/test.m3u`。

- [ ] **Step 3: 检查变更**

Run: `git diff -- app/src/main/java/top/yogiczy/mytv/data/utils/Constants.kt`
Expected: 只看到目标常量变更为 `test.m3u`。

### Task 2: 构建调试包

**Files:**
- Build artifact: `app/build/outputs/apk/debug/app-debug.apk`

- [ ] **Step 1: 构建 debug APK**

Run: `bash ./gradlew assembleDebug`
Expected: 构建成功并产出 debug APK。

### Task 3: 连接设备并尝试唤醒

**Files:**
- No file changes

- [ ] **Step 1: 连接电视盒子**

Run: `/Users/qiuyanjun/Library/Android/sdk/platform-tools/adb connect 192.168.8.150`
Expected: 输出 `connected` 或 `already connected`。

- [ ] **Step 2: 检查设备列表**

Run: `/Users/qiuyanjun/Library/Android/sdk/platform-tools/adb devices`
Expected: 出现 `192.168.8.150:5555` 或等价在线设备序列号。

- [ ] **Step 3: 发送唤醒按键**

Run: `/Users/qiuyanjun/Library/Android/sdk/platform-tools/adb -s 192.168.8.150:5555 shell input keyevent 224`
Expected: 命令返回成功。

- [ ] **Step 4: 退出屏保或广告页并回桌面**

Run: `/Users/qiuyanjun/Library/Android/sdk/platform-tools/adb -s 192.168.8.150:5555 shell input keyevent 82`
Run: `/Users/qiuyanjun/Library/Android/sdk/platform-tools/adb -s 192.168.8.150:5555 shell input keyevent 3`
Expected: 设备进入可交互状态或桌面。

### Task 4: 安装、清数据并启动应用

**Files:**
- Install artifact: `app/build/outputs/apk/debug/app-debug.apk`

- [ ] **Step 1: 安装应用**

Run: `/Users/qiuyanjun/Library/Android/sdk/platform-tools/adb -s 192.168.8.150:5555 install -r app/build/outputs/apk/debug/app-debug.apk`
Expected: 输出 `Success`。

- [ ] **Step 2: 清除应用数据**

Run: `/Users/qiuyanjun/Library/Android/sdk/platform-tools/adb -s 192.168.8.150:5555 shell pm clear top.yogiczy.mytv`
Expected: 输出 `Success`。

- [ ] **Step 3: 启动 LeanbackActivity**

Run: `/Users/qiuyanjun/Library/Android/sdk/platform-tools/adb -s 192.168.8.150:5555 shell am start -n top.yogiczy.mytv/top.yogiczy.mytv.activities.LeanbackActivity`
Expected: 返回 `Starting: Intent`，无启动错误。

- [ ] **Step 4: 验证前台启动状态**

Run: `/Users/qiuyanjun/Library/Android/sdk/platform-tools/adb -s 192.168.8.150:5555 shell dumpsys activity activities`
Expected: 输出中能看到 `top.yogiczy.mytv` 前台 Activity 记录。
