# Android TV 播放器审查要点

## 优先检查这些区域

- `app/src/main/java/top/yogiczy/mytv/ui/screens/leanback/`：电视端 Compose 页面、焦点处理、按键事件、设置和浮层。
- `app/src/main/java/top/yogiczy/mytv/ui/screens/leanback/video/player/`：Media3 与自定义播放器集成。
- `app/src/main/java/top/yogiczy/mytv/data/repositories/`：IPTV、EPG、Git 发布信息和缓存相关行为。
- `app/src/main/java/top/yogiczy/mytv/activities/`：Leanback、手机端和平板端之间的显示类型路由。
- `app/src/main/java/top/yogiczy/mytv/BootReceiver.kt`：开机启动路径。
- `app/src/main/java/top/yogiczy/mytv/ui/utils/HttpServer.kt`：本地 Web 配置入口。
- `app/src/main/java/top/yogiczy/mytv/UnsafeTrustManager.kt`：与安全相关的证书绕过行为。

## 高风险审查清单

- 确认改动不会让电视界面的焦点卡死，也不会破坏方向键遍历、确认键、返回键、菜单键或长按流程。
- 确认播放相关改动仍然会正确释放播放器、取消任务，并避免重复监听器或陈旧的渲染 surface。
- 确认 Media3 的兜底逻辑覆盖 HLS、RTSP 和渐进式流媒体来源，不会出现无限重试或永远走不到的分支。
- 确认设置项变更能够正确持久化，并且不会回归那些需要重启的流程。
- 确认仓库和解析器的改动没有把阻塞式网络或文件操作移到主线程。
- 确认历史列表修改、缓存失效和兜底逻辑在失败后仍保持一致。
- 确认开机启动、更新和 HTTP 服务相关改动不会意外扩大暴露面，也不会破坏启动流程。

## 比样式问题更重要的发现

- 崩溃路径和未捕获异常。
- 只会在特定源类型或网络失败下出现的播放回归。
- 生命周期泄漏、重复协程、未释放的播放器，或销毁后仍继续更新状态。
- Leanback、手机端和平板端入口之间的行为分叉错误。
- 导出组件、本地 HTTP 访问、文件路径或信任管理器行为带来的安全回归。
- 当解析器、仓库或路由等逻辑密集型行为发生变化，却缺少其他验证手段时，对应缺失的测试。

## 实用审查命令

- `git diff --stat`
- `git diff --name-only`
- `git diff --unified=0`
- `rg -n "FocusRequester|handleLeanbackKeyEvents|HttpServer|UnsafeTrustManager|BootReceiver|ExoPlayer|RtspMediaSource|HlsMediaSource" app/src/main/java`
