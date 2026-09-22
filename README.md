# Tieba Browser for IntelliJ IDEA

在 IntelliJ IDEA 中直接浏览百度贴吧的插件。基于 Kotlin + Swing 编写，通过 JSON 协议与 Python `aiotieba` 子进程通信，无需登录即可阅读公开的帖子内容。

## 功能特性

- **贴吧搜索**：输入吧名即可浏览该吧的帖子列表
- **帖子列表**：展示标题、作者、回复数、最后回复时间，自动标记 `[置顶]`、`[精华]`
- **楼内阅读**：楼层、作者、发布时间、回复数一目了然，楼主楼层高亮标识
- **图片查看**：点击 `[图片 N]` 链接在弹窗中加载并查看原图
- **楼中楼**：点击 `查看 N 条回复` 查看任意楼层的楼中楼评论，支持翻页
- **只看楼主**：一键过滤，仅显示楼主（楼主）发布的内容
- **分页导航**：帖子列表与楼内阅读均支持上下翻页
- **字体颜色**：通过取色器自定义阅读区文字颜色（设置持久化）
- **表情还原**：将百度贴吧表情还原为 `[泪]`、`[滑稽]` 等文字占位符

## 环境要求

| 依赖 | 说明 |
|------|------|
| IntelliJ IDEA | `since-build="201"`，即 2020.1 及以上版本 |
| JDK | 构建需要 JDK 21；插件运行目标为 Java 8 |
| Python | 需要 Python 3.10+，Windows 3.13 亦可（已兼容） |
| aiotieba | Python 库，`pip install aiotieba` |

## 安装方法

### 方式一：从源码构建

```powershell
.\gradlew.bat buildPlugin
```

构建产物位于 `build\distributions\tieba-idea-plugin-1.0.zip`。

将 ZIP 解压到 IDEA 插件目录后重启 IDE：

- Windows: `%APPDATA%\JetBrains\IntelliJIdea2024.1\plugins\`

### 方式二：开发模式运行

```powershell
.\gradlew.bat runIde
```

> **注意**：`build.gradle.kts` 中的 `intellij.localPath` 目前指向本机一个临时 IDEA 安装目录，如路径不同请自行修改。

### Python 环境

插件运行需要本机已安装 Python 及 `aiotieba`：

```bash
pip install aiotieba
```

插件启动时会自动检测 Python 与依赖，缺失时会在日志或界面中给出提示，不会崩溃。

## 使用方法

1. 打开 IDEA，点击右侧工具栏中的 **Tieba** 标签打开插件面板
2. 在顶部输入框输入吧名（如 `steam`），回车或点击「浏览」
3. 在帖子列表中点击任意帖子进入楼内阅读
4. 通过「只看楼主」、「返回列表」、翻页按钮等完成浏览操作

## 架构设计

```
┌─────────────────────────────┐      JSON line-delimited       ┌──────────────────────────────┐
│  IntelliJ IDEA Plugin       │                                │  Python 子进程                │
│  (Kotlin + Swing)           │  ──────── request ─────────▶   │  python-bridge/aiotieba_     │
│                             │    {"req_id":1,"action":...}   │  bridge.py                   │
│  ui/TiebaPanel  (主面板)     │                                │  └─ aiotieba 客户端           │
│  ui/ForumSearchPanel        │  ◀─────── response ─────────   │                              │
│  ui/ThreadListPanel         │     {"req_id":1,"threads":[]}  │                              │
│  ui/PostReaderPanel         │                                │                              │
│  data/TiebaBridge  (桥接)    │                                │                              │
└─────────────────────────────┘                                └──────────────────────────────┘
```

### 核心模块

| 模块 | 位置 | 说明 |
|------|------|------|
| Python 桥接脚本 | `python-bridge/aiotieba_bridge.py` | 负责调用 `aiotieba` 库获取贴吧数据，同时打包进 `src/main/resources/python-bridge/`（两处需保持同步） |
| 桥接管理器 | `data/TiebaBridge.kt` | 启动 Python 子进程，将脚本解压至 `%TEMP%\tieba-plugin\`，用 `req_id` + `CompletableFuture` 关联请求与响应 |
| 数据模型 | `data/TiebaModels.kt` | `TiebaThread`、`TiebaPost`、`TiebaComment` |
| UI 面板 | `ui/*.kt` | Swing 界面：主面板、吧搜索、帖子列表、楼内阅读、工具窗口工厂 |

### 通信协议

插件与 Python 子进程通过标准输入/输出以**逐行 JSON** 通信，每条请求携带自增的 `req_id`，响应中回带相同的 `req_id` 用于匹配。

支持的 `action`：

| action | 参数 | 说明 |
|--------|------|------|
| `get_threads` | `forum`, `page` | 获取帖子列表 |
| `get_posts` | `tid`, `page`, `only_op` | 获取楼层内容 |
| `get_comments` | `tid`, `pid`, `page` | 获取楼中楼回复 |
| `health` | - | 健康检查 |

## 开发注意事项

- **Python 3.13 on Windows**：脚本已通过 `asyncio.WindowsSelectorEventLoopPolicy()` 和 `async with aiotieba.Client()`（上下文管理器）适配
- **UTF-8 on Windows**：脚本会对 stdin/stdout/stderr 调用 `reconfigure(encoding='utf-8', errors='replace')`
- **Swing 布局陷阱**：`BorderLayout.CENTER` 只布局最后添加的组件，面板内一律使用 `CardLayout` 切换「加载中 / 内容」状态
- 日志请使用 IntelliJ 的 `Logger`，而非 `System.out`
- IDEA 日志位置：`%LOCALAPPDATA%\JetBrains\IntelliJIdea2024.1\log\idea.log`

## 已知限制

- 仅支持浏览公开内容，不包含登录、发帖等交互功能
- 基于 `aiotieba` 非官方接口实现，接口变动可能导致部分功能失效
- 插件声明了 `since-build="201"`，建议在较新的 IDEA 版本中使用

## 免责声明

本项目为个人学习与开源分享用途，与百度贴吧官方无任何关联。请合理使用，尊重原作者与社区规则，不要用于任何商业用途或违反平台规则的行为。本项目基于非官方接口实现，如造成任何问题，作者不承担相关责任。

## 致谢

- [aiotieba](https://github.com/lumina37/aiotieba/)：为插件提供底层贴吧数据获取能力
- JetBrains IntelliJ Platform SDK

## License

本项目使用 [MIT License](LICENSE)。
