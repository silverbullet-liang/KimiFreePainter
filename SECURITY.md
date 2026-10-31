
# 安全策略

## 报告漏洞

请**不要**通过公开 Issue 报告安全漏洞。

请通过以下方式之一报告：

- GitHub 的 [Private vulnerability reporting](https://github.com/silverbullet-liang/KimiFreePainter/security/advisories/new)
- 邮件至 `<your-email>`

我们会在 72 小时内响应，并在修复后公开致谢（如您同意）。

## 支持范围

仅最新 Release 版本接受安全报告。旧版本请先升级。

## 已知边界

- 本项目依赖 Kimi 服务端接口，服务端变更不视为安全漏洞
- 本项目不处理任何用户上传数据，不存在服务端数据泄露风险
- 本项目不收集任何用户数据，不存在隐私泄露风险

## 安全设计

### Token 存储

- Android：EncryptedSharedPreferences（Android Keystore 加密）
- Web：默认临时会话（内存），可选 localStorage（显著警告）

### 网络

- 全部请求走 HTTPS
- 不向任何第三方域名发起请求

### WebView

- 只允许 kimi.com 域名
- 不注入任何第三方脚本

### 权限

- 只申请必要权限
- 不申请位置、通讯录、相机、麦克风等敏感权限
