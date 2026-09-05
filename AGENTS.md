# AGENTS.md — nanmu 项目永久规则

本文档为本项目的永久性工程规则，任何开发、重构、评审行为都必须遵守。

## 1. 技术栈

- 语言：Kotlin
- UI：Jetpack Compose
- 架构：MVVM
- 依赖注入：Hilt

## 2. 音频处理

- 音频处理统一集成 **FFmpeg-Kit**。
- 转换命令必须为：`-ac 1 -c:a libvorbis -q:a 4`
  - 含义：单声道（mono）+ libvorbis 编码 + 质量档 4，输出 **单声道 OGG** 格式。

## 3. Android 约束

- `minSdk = 23`
- 存储权限方案仅允许以下两种：
  - `MANAGE_EXTERNAL_STORAGE`（全盘管理，需在 `AndroidManifest.xml` 声明并在设置中引导用户授权），或
  - SAF 框架（`ACTION_OPEN_DOCUMENT_TREE` / `ACTION_CREATE_DOCUMENT` 等 Storage Access Framework 方式）。
  - 禁止使用旧的 `READ_EXTERNAL_STORAGE` / `WRITE_EXTERNAL_STORAGE` 运行时权限方案。

## 4. JSON 生成

- JSON 序列化统一使用 **Moshi** 库。
- 生成模板必须符合 **MTR 模组规范**：

```json
{"id":{"sounds":[{"name":"mtr:文件名"}]}}
```

其中 `id` 为 sound 事件标识，`name` 必须带 `mtr:` 命名空间前缀。

## 5. 代码规范

- 所有耗时操作（文件 IO、音频转码、网络请求等）必须使用 **Kotlin 协程** 执行。
- 禁止在主线程执行任何耗时 / 阻塞操作（包括但不限于：阻塞式 IO、Thread.sleep、锁等待、同步网络调用）。
