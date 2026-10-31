
# 贡献指南

感谢您对 KimiFreePainter 的关注！欢迎通过以下方式贡献。

## 提交 Issue

### Bug 报告
- 使用 Bug Report 模板
- 附上 Android 版本、App 版本、复现步骤、日志
- 如果是 Web 端，附上浏览器版本

### 功能建议
- 使用 Feature Request 模板
- 说明场景与预期

## 提交 PR

1. Fork 本仓库
2. 从 `main` 切出特性分支：`feature/xxx` 或 `fix/xxx`
3. 遵循项目代码风格（Kotlin 官方风格 + 4 空格缩进）
4. 提交前跑通 `./gradlew lintDebug assembleDebug`（Android）或 `npm run build`（Web）
5. PR 描述清晰说明改动与动机
6. 等待 review

## 代码规范

### Kotlin
- 官方风格，无通配符 import
- 类 PascalCase，函数 camelCase，常量 UPPER_SNAKE
- 公开 API 必须有 KDoc

### Compose
- 状态提升，无副作用 composable
- 使用 `remember` 缓存昂贵计算
- 避免在 composable 内直接调 suspend 函数

### TypeScript / React
- 严格模式，无 `any`
- 函数组件 + Hooks
- 状态用 Zustand，不用 Context

### 文案
- 所有涉及 `@AI画师` 的地方保持中文，不翻译
- 避免「自动化」「脚本」「批量生成」等词汇
- 「免费」统一表述为「平台功能本身免费」

## 禁止事项

- 不得提交任何 API Key、Token、个人隐私数据
- 不得提交与项目无关的二进制文件
- 不得引入有法律风险的依赖
- 不得提交 `@AIArtist` / `@AI Painter` 等翻译形式（CI 会检查）

## 提交信息规范

```
<type>(<scope>): <subject>

<body>
```

type: `feat` / `fix` / `docs` / `style` / `refactor` / `test` / `chore`

示例：
```
feat(android): add history swipe-to-delete

- Implement SwipeToDismissBox in HistoryScreen
- Add delete confirmation dialog
```

## 开发环境

### Android
- Android Studio Hedgehog+
- JDK 17
- Android SDK 35

### Web
- Node.js 20+
- npm 10+

### 运行测试
```bash
# Android
./gradlew lintDebug assembleDebug

# Web
cd web && npm install && npm run build
```

## 行为准则

请遵守 [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md)。任何违反者可能被移除贡献权限。
