# AGENTS.md

## Project

IntelliJ IDEA plugin (Kotlin + Swing) for browsing Baidu Tieba. Communicates with a Python `aiotieba` subprocess via JSON line-delimited stdio with `req_id` correlation.

## Build & Deploy

```powershell
$env:JAVA_HOME="C:\Users\Q1sj\.jdks\graalvm-ce-21.0.2"
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat buildPlugin
```

Output: `build\distributions\tieba-idea-plugin-1.0.zip`

**Workflow rule: after every code change, run `.\gradlew.bat buildPlugin` to verify the build.** It must complete successfully before the change is considered done.

Install: extract ZIP to `%APPDATA%\JetBrains\IntelliJIdea2024.1\plugins\`, then reload plugin in IDE.

**No test framework** is configured. Verify via `.\gradlew.bat runIde` or manual install.

## Architecture

| Layer | Location | Notes |
|-------|----------|-------|
| Python bridge | `python-bridge/aiotieba_bridge.py` | Source of truth. Also copied to `src/main/resources/python-bridge/` for JAR packaging. **Keep both in sync.** |
| Bridge manager | `data/TiebaBridge.kt` | Spawns Python process, extracts script to `%TEMP%\tieba-plugin\`, manages JSON request/response with `ConcurrentHashMap<req_id, CompletableFuture>` |
| Data models | `data/TiebaModels.kt` | `TiebaThread`, `TiebaPost` |
| UI panels | `ui/*.kt` | Swing: `TiebaPanel` (main), `ForumSearchPanel`, `ThreadListPanel`, `PostReaderPanel`, `TiebaToolWindowFactory` |

## Critical Gotchas

1. **BorderLayout.CENTER only lays out the LAST component added.** Never add two components to CENTER (e.g. scrollPane + loadingLabel) — the first gets size 0. Use `CardLayout` to switch between loading and content states.

2. **Python 3.13 on Windows** requires `asyncio.WindowsSelectorEventLoopPolicy()` and `async with aiotieba.Client()` (context manager, not bare `Client()`).

3. **UTF-8 on Windows**: Python must call `sys.stdout/stderr/stdin.reconfigure(encoding='utf-8', errors='replace')`.

4. **Java 8 target, JDK 21 build**: `jvmToolchain(8)`, `jvmTarget = "1.8"`, `targetCompatibility = "1.8"`. Build with JDK 21.

5. **`intellij.localPath`** in `build.gradle.kts` is a hardcoded path to a temp IDEA installation (`D:/Program Files/java/repository1/AppData/Local/Temp/opencode/idea/idea-IC-201.8743.12`). Change it if the path differs.

6. **`ContentFactory.getInstance()`** is not available in the IDE API version used. `TiebaToolWindowFactory` uses reflection to call it.

7. **`instrumentCode` and `buildSearchableOptions` are disabled** in `build.gradle.kts` due to network/JBR issues. Re-enable only if those issues are resolved.

8. **IDEA logs**: `%LOCALAPPDATA%\JetBrains\IntelliJIdea2024.1\log\idea.log`. Use IntelliJ `Logger`, not `System.out`.

9. **Python dependency**: `aiotieba` must be installed via `pip install aiotieba`. The bridge script checks for it at startup and returns a JSON error if missing.
