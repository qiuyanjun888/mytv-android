# 直播换台起播缓冲优化实现计划

> **给执行型代理的说明：** 实施本计划时需要配合 `superpowers:subagent-driven-development`（推荐）或 `superpowers:executing-plans`。步骤使用复选框 `- [ ]` 追踪。

**目标：** 统一降低播放器起播缓冲门槛，缩短频道切换等待时间，让直播与后续接入同一播放器的回看都具备更快起播能力。

**架构：** 本次改动新增一个独立的 `Media3LoadControlPolicy`，集中维护快速换台的起播缓冲参数，并在构建 `ExoPlayer` 时统一挂载。播放器主体逻辑、源解析、EPG 和回看业务入口都不改，只调整 `LoadControl`。

**技术栈：** Kotlin、AndroidX Media3、JUnit4、ADB

---

### 任务 1：为快速起播缓冲策略写失败测试

**文件：**
- 新建：`app/src/test/java/top/yogiczy/mytv/ui/screens/leanback/video/player/Media3LoadControlPolicyTest.kt`
- 新建：`app/src/main/java/top/yogiczy/mytv/ui/screens/leanback/video/player/Media3LoadControlPolicy.kt`

- [ ] **步骤 1：写失败测试**

覆盖两类断言：
- 快速起播 profile 会降低 `bufferForPlaybackMs` 与 `bufferForPlaybackAfterRebufferMs`。
- `minBufferMs`、`maxBufferMs` 等主体缓冲参数保持 Media3 默认值。

- [ ] **步骤 2：运行测试确认失败**

运行：`bash ./gradlew testDebugUnitTest --tests top.yogiczy.mytv.ui.screens.leanback.video.player.Media3LoadControlPolicyTest`
预期：因策略类或行为未实现而失败。

### 任务 2：实现最小策略并接入播放器

**文件：**
- 修改：`app/src/main/java/top/yogiczy/mytv/ui/screens/leanback/video/player/Media3VideoPlayer.kt`
- 新建：`app/src/main/java/top/yogiczy/mytv/ui/screens/leanback/video/player/Media3LoadControlPolicy.kt`

- [ ] **步骤 1：实现缓冲配置策略**

新增独立策略对象，提供一组“快速换台”缓冲参数。

- [ ] **步骤 2：在播放器构建时挂载新的 `LoadControl`**

保持现有渲染器、音频与重试逻辑不变，只把 `LoadControl` 接进去。

### 任务 3：运行验证

**文件：**
- 测试：`app/src/test/java/top/yogiczy/mytv/ui/screens/leanback/video/player/Media3LoadControlPolicyTest.kt`

- [ ] **步骤 1：运行目标单测**

运行：`bash ./gradlew testDebugUnitTest --tests top.yogiczy.mytv.ui.screens.leanback.video.player.Media3LoadControlPolicyTest`
预期：通过

- [ ] **步骤 2：运行相关播放器单测**

运行：`bash ./gradlew testDebugUnitTest --tests top.yogiczy.mytv.ui.screens.leanback.video.player.*`
预期：通过

- [ ] **步骤 3：构建 debug 包**

运行：`bash ./gradlew assembleDebug`
预期：构建成功

- [ ] **步骤 4：真机换台回归**

重新安装并启动应用，抓取一次换台日志，确认 `READY` 不再被约 2500ms 缓冲门槛固定卡住。
