# 🚇 MTR 音频包制作器 (Android)

一款专为《我的世界》MTR（Minecraft Transit Railway）模组设计的音频追加包制作工具。  
您可以直接在手机上将任意音频文件（MP3/WAV/FLAC）转换为 MTR 模组所需的 OGG 格式，并一键导出为完整的资源包（.zip），方便在游戏中使用。

---

## ✨ 功能特性

- ✅ 支持导入 MP3 / WAV / FLAC 等常见音频格式
- ✅ 一键转换为 MTR 模组兼容的 **单声道 OGG**（Vorbis 编码）
- ✅ 自定义每个音频的 **声音 ID**（如 `my_door`）
- ✅ 实时显示转换进度
- ✅ 导出完整的 Minecraft 资源包（包含 `pack.mcmeta`、`sounds.json` 和所有音频文件）
- ✅ 支持自定义资源包名称、版本号和 `pack_format`（兼容不同 MC 版本）
- ✅ 基于 Jetpack Compose 的现代 Material 3 界面
- ✅ 所有耗时操作使用 Kotlin 协程，不阻塞主线程

---

## 📱 使用方法

1. **添加音频**：点击“添加音频”，从文件管理器选择 MP3/WAV/FLAC 文件。
2. **查看列表**：每个音频会显示文件名和当前状态（未转换/转换中/已转换/失败）。
3. **编辑 ID**：点击列表项，在弹出的对话框中修改声音 ID（默认从文件名生成）。
4. **开始转换**：点击音频项右侧的“转换”按钮，应用会将音频转为 OGG 格式。
5. **导出资源包**：至少转换一个音频后，点击“导出资源包”按钮，设置资源包名称、版本号和 `pack_format`，即可生成 ZIP 文件并保存到手机的 Download 目录。

---

## 🛠 技术栈

| 组件 | 技术 |
|---|---|
| UI 框架 | Jetpack Compose + Material 3 |
| 架构模式 | MVVM + Repository |
| 依赖注入 | Dagger Hilt |
| 音频处理 | FFmpegKit (社区维护版 `dev.ffmpegkit-maintained`) |
| JSON 序列化 | Moshi |
| 异步编程 | Kotlin Coroutines + Flow |
| 构建工具 | Gradle (Kotlin DSL) |

---

## 🏗 如何构建（开发者）

### 前提条件

- Android Studio 或 Gradle 环境
- JDK 17
- Android SDK (API 34)

### 步骤

1. **克隆仓库**
   ```bash
   git clone https://github.com/ganganjue/mtr-audio-pack-maker.git
   cd mtr-audio-pack-maker