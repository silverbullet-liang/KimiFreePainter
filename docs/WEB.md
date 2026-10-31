
# Web 端使用说明

## 在线地址

https://silverbullet-liang.github.io/KimiFreePainter/

## 与 Android 版的差异

| 功能 | Android | Web |
|---|---|---|
| 获取 token | WebView 自动 | 手动粘贴（Bookmarklet 辅助） |
| 后台运行 | ✅ | ❌ 需保持页面打开 |
| 自动删帖 | ✅ 可配置 | ❌ 手动 |
| 相册保存 | ✅ | 浏览器下载 |
| 系统通知 | ✅ | ❌ |

## 快速开始

### 1. 获取 Token（Bookmarklet）

1. 浏览器打开 https://www.kimi.com 并登录
2. 新建书签，名称「Kimi Token」，URL 填入下面这段：

```
javascript:(function(){
  const at = localStorage.getItem('access_token');
  const rt = localStorage.getItem('refresh_token');
  if (!at || !rt) { alert('未找到 token，请先在 kimi.com 登录'); return; }
  const payload = JSON.stringify({ accessToken: at, refreshToken: rt });
  navigator.clipboard.writeText(payload).then(() => {
    alert('✅ Token 已复制到剪贴板\n\n回到 KimiFreePainter，粘贴到设置页');
  }).catch(() => {
    prompt('复制以下内容到 KimiFreePainter:', payload);
  });
})();
```

3. 在 kimi.com 页面点击该书签，token 会复制到剪贴板
4. 回到 KimiFreePainter → 设置页 → 粘贴 → 保存

### 2. 生图

1. 生图页输入提示词
2. 点「开始创作」
3. **保持页面打开**，等待结果（通常 1–3 分钟）
4. 完成后可下载图片

### 3. 安全提示

- 默认「临时会话」模式，刷新页面即清 token
- 若开启「记住 token」，token 以**明文**存 localStorage，**请勿在公共电脑开启**

## 常见问题

**Q: 刷新页面后任务丢了怎么办？**
A: Web 端会记住未完成任务，重新打开页面会提示恢复。但超过 10 分钟的任务不再恢复。

**Q: 为什么必须保持页面打开？**
A: Web 端没有后台 Service，关闭页面轮询就停了。

**Q: 为什么 Web 端不能自动删帖？**
A: 删帖是敏感操作，Web 端默认不自动执行，需手动确认。

**Q: CORS 错误怎么办？**
A: 若浏览器控制台报 CORS 错误，说明 Kimi 服务端未开放跨域。此时 Web 端会切换到「快捷指令模式」，生成一段控制台快捷操作供你手动执行。

**Q: Token 会过期吗？**
A: accessToken 15 分钟过期，refreshToken 90 天。Web 端遇到 401 会自动尝试刷新，失败则提示重新获取 token。
