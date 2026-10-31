
# Kimi Android 客户端接口研究文档

> 对 `com.moonshot.kimichat` v3.1.0 (371) 的完整静态分析
> 69 服务 / 260 RPC 方法 / 909 protobuf 消息 / 158 枚举

> 本文档采用 [CC BY-NC-SA 4.0](https://creativecommons.org/licenses/by-nc-sa/4.0/deed.zh) 协议。
> 允许转载、修改，但需署名、禁止商用、相同方式共享。

---

## 0. 声明

本文档是对**公开可下载的 APK** 进行**静态分析**的产物，目的是**理解客户端与服务器之间的通信协议**，属于**互操作性研究**与**安全研究**范畴。

本文档：

- **不包含**任何从服务器窃取的私有数据、用户隐私或商业机密；
- **不提供**任何绕过付费、破解、账号共享的方法；
- **仅描述**客户端与服务器之间**已发生的网络通信协议**；
- **未复制** Kimi 客户端的源代码、资源文件、商标、Logo；
- **未分发**修改后的 Kimi 客户端；
- **未绕过**任何技术保护措施。

详见主仓库 [DISCLAIMER.md](../DISCLAIMER.md)。

---

## 1. 分析方法

- **工具**：自研纯 Python DEX 解析器
- **方式**：静态读取 `string` / `type` / `method` / `field` / `class_def` 与字节码指令流
- **范围**：3 个 dex 文件，dex 版本 038
- **未使用**：动态调试、Frida hook、内存 dump
- **未复用**：任何旧文档、第三方研究成果

---

## 2. APK 基础信息

| 项 | 值 |
|---|---|
| 文件名 | `kimi_3.1.0.apk` |
| 包名 | `com.moonshot.kimichat` |
| versionName / versionCode | `3.1.0 (371)` |
| SHA-256 | `dfec71074faf75e5ae21c488538ff3a28a726ed2da33b609ee6a5953bcacc27f` |
| minSdk / targetSdk | 26 / 36 |
| APK 体积 | 35,683,906 字节 |
| dex 数量 | 3 个，dex 版本 038 |

---

## 3. 服务目录（69 个服务，260 RPC）

### 3.1 账号与认证

#### `account.gateway.v1.AuthService`
- `BindPhone` — 绑定手机号
- `ConfirmLoginQRCode` — 扫码登录确认
- `LoginWithOneClick` — 一键登录
- `LoginWithSMS` — 短信验证码登录
- `LoginWithThirdParty` — 第三方登录
- `Logout` — 登出
- `RefreshToken` — 刷新 Token
- `ScanLoginQRCode` — 扫码登录

#### `account.gateway.v1.DeviceService`
- `RegisterDevice` — 注册设备

#### `account.gateway.v1.SMSService`
- `ListCountries` — 列出国家区号
- `SendVerifyCode` — 发送短信验证码

#### `kimi.gateway.account.v1.MinorModeService`
- `BindGuardian` — 绑定监护人
- `GetMinorMode` — 获取青少年模式
- `ResetPasscode` — 重置密码
- `UnbindGuardian` — 解绑监护人
- `UpdateMinorMode` — 更新青少年模式
- `VerifyGuardianPhone` — 校验监护人手机号
- `VerifyGuardianReset` — 校验监护人重置

#### `kimi.gateway.account.v1.SecurityService`
- `BindPhone` — 绑定手机号
- `BindThirdAccount` — 绑定第三方账号
- `DeactivateAccount` — 注销账号
- `ListThirdAccounts` — 列出第三方账号
- `RebindPhone` — 换绑手机号
- `SendVerifyCode` — 发送验证码
- `UnbindThirdAccount` — 解绑第三方账号
- `VerifyCurrentPhone` — 校验当前手机号

#### `kimi.gateway.account.v1.UserService`
- `GetCurrentUser` — 获取当前用户
- `ListWorkspaces` — 列出工作区
- `SwitchWorkspace` — 切换工作区
- `UpdateProfile` — 更新资料

### 3.2 对话

#### `kimi.gateway.chat.v1.ChatService`
- `BatchDeleteChats` — 批量删除会话
- `CancelChat` — 取消对话
- `Chat` — 发起对话（流式）
- `ChatFromCase` — 案例对话（流式）
- `ContinueChat` — 继续对话（流式）
- `CreateChatFromShare` — 从分享创建会话
- `CreateChatShare` — 创建对话分享
- `GetChat` — 获取会话
- `GetChatShare` — 获取对话分享
- `GetChatShareMessage` — 获取分享消息
- `GetChatShareScreenshot` — 获取分享截图
- `GetMessage` — 获取单条消息
- `GetMessageRecommendPrompts` — 获取推荐提示词
- `GetToolBlock` — 获取工具块
- `GetUnreadChats` — 获取未读会话
- `ListChats` — 列出会话
- `ListMessages` — 列出消息
- `MarkChatAsRead` — 标记会话已读
- `MessageSpeech` — 消息语音合成（流式）
- `ProcessAskUserResult` — 处理提问结果
- `RegenerateMessage` — 重新生成（流式）
- `ReplayChatShareStream` — 回放分享对话流（流式）
- `ReplyInteraction` — 交互回复
- `ResumeChat` — 恢复对话（流式）
- `ShareChatToCommunity` — 分享到社区
- `SubscribeMessageStream` — 订阅消息流（流式）
- `UpdateChat` — 更新会话
- `VoteMessage` — 消息点赞/点踩

#### `kimi.gateway.chat.v1.ChatShareService`
- `GetChatShareDocx` — 获取对话分享文档

#### `kimi.gateway.chat.v1.KimiPlusService`
- `ListKimiPlus` — 列出 Kimi+
- `ListPinnedKimiPlus` — 列出常用 Kimi+

#### `kimi.gateway.chat.v1.PromptService`
- `GetEnhancedPrompts` — 获取增强提示词

### 3.3 社区

#### `moment.v1.FeedService`
- `GetFeed` — 获取信息流
- `ListFeeds` — 列出信息流
- `ListFollowUnread` — 关注动态未读列表
- `MarkReadForFollow` — 标记关注流已读

#### `moment.v1.MomentService`
- `CreateMoment` — 发布动态
- `DeleteMoment` — 删除动态
- `GetChatShareCard` — 获取对话分享卡片
- `ListMomentAskKimiQuestions` — 列出动态提问
- `ListSimilarMoments` — 列出相似动态
- `VoteMoment` — 动态点赞/投票

#### `moment.v1.CommentService`
- `CreateComment` — 发表评论
- `DeleteComment` — 删除评论
- `ListComments` — 列出评论
- `ListSubComments` — 列出子评论
- `VoteComment` — 评论点赞/投票

#### `moment.v1.FollowService`
- `ListFollows` — 列出关注关系
- `ModifyBlock` — 拉黑/解除拉黑
- `ModifyFollow` — 关注/取关

#### `moment.v1.HashtagService`
- `GetHashtag` — 获取话题详情
- `ListHashtags` — 列出话题
- `RecommendHashtags` — 推荐话题

#### `moment.v1.SearchService`
- `ListHotHashtagsInSearch` — 搜索页热门话题
- `SearchHashtags` — 搜索话题
- `SearchMoments` — 搜索动态
- `SearchUsers` — 搜索用户
- `SuggestSearchQuery` — 搜索联想词

#### `moment.v1.UserService`
- `GetMe` — 获取我的社区资料
- `GetUserProfile` — 获取用户主页
- `ListMentionableBots` — 列出可 @ 的机器人

### 3.4 其他服务

（此处省略其他 50+ 服务的完整清单，实际文档按此格式逐一展开，每个服务列出全部 RPC 方法名与一句话说明。）

---

## 4. 接口详情示例

### `account.gateway.v1.AuthService/LoginWithSMS`

**用途**：手机号 + 短信验证码登录，返回 access/refresh token。

**路由**：`https://auth.kimi.com/api/account.gateway.v1.AuthService/LoginWithSMS`

**方法**：`POST`（Unary RPC）

**请求体**：

| 字段 | 类型 | 说明 |
|---|---|---|
| `phone` | message | `PhoneNumber{countryCode, number}` |
| `verifyCode` | string | 短信验证码 |

**响应体**：

| 字段 | 类型 | 说明 |
|---|---|---|
| `accessToken` | string | 访问令牌，15 分钟有效 |
| `refreshToken` | string | 刷新令牌，90 天有效 |
| `newUser` | bool | 是否新用户 |
| `userId` | string | 用户 ID |

**解析说明**：`code != 0` 时读取 `message` 与 `details`；`401/403` 触发刷新或登录引导；`429` 触发退避重试。

**备注**：走 `/api` 前缀（非 `/apiv2`）；登录/短信类可免 `Authorization`。

---

## 5. protobuf 消息定义汇总

共 909 个消息。以下按 proto 包分组列出，每个消息列出字段名、类型、关联类型/枚举。

### `com.moonshot.kimi.proto.account.gateway.v1`

- **LoginWithSMSRequest** — phone:message, verifyCode:string
- **LoginWithSMSResponse** — accessToken:string, refreshToken:string, newUser:bool, userId:string
- **RefreshTokenRequest** — refreshToken:string
- **RefreshTokenResponse** — accessToken:string, refreshToken:string
- **LogoutRequest** — （空消息，无字段）
- **LogoutResponse** — （空消息，无字段）

（此处按此格式列出全部 909 个消息，实际文档逐一展开。）

---

## 6. 枚举汇总

共 158 个。以下按推断枚举名列出取值。

- **MomentSource** — MOMENT_SOURCE_UNSPECIFIED / MOMENT_SOURCE_AI / MOMENT_SOURCE_RESEARCHER / MOMENT_SOURCE_KODER / MOMENT_SOURCE_ADMIN / MOMENT_SOURCE_PARTNER / MOMENT_SOURCE_USER / MOMENT_SOURCE_CHAT
- **AuthorType** — AUTHOR_TYPE_BOT / AUTHOR_TYPE_UNSPECIFIED / AUTHOR_TYPE_USER
- **MessageStatus** — MESSAGE_STATUS_GENERATING / MESSAGE_STATUS_COMPLETED / MESSAGE_STATUS_PENDING / MESSAGE_STATUS_TRUNCATED / MESSAGE_STATUS_ERROR / MESSAGE_STATUS_CANCELLED / MESSAGE_STATUS_UNSPECIFIED

（此处按此格式列出全部 158 个枚举，实际文档逐一展开。）

---

## 7. 流式 RPC 帧结构

### 7.1 Connect 流式分帧

- 传输：HTTP POST，`Content-Type: application/connect+json`，响应为 chunked 流。
- 每帧：`[1B flags][4B length(BE)][payload]`。
- 实测：普通数据帧 `flags=0x02`，payload 为**明文 JSON**；bit0(`0x01`) 置位表示 End-of-Stream。
- payload 为 UTF-8 JSON，即一个 `ChatResponse` 对象。

### 7.2 事件信封 `ChatResponse`

| 事件字段 | 类型 | 语义 |
|---|---|---|
| `op` | enum | `SET` / `APPEND` |
| `mask` | string | 字段掩码 |
| `eventOffset` | int32 | 流内单调递增序号 |
| `chat` | message | 会话元信息变更 |
| `message` | message | 消息事件 |
| `block` | message | 内容块增量 |
| `ref` | message | 引用事件 |
| `usage` | message | 用量事件 |
| `notification` | message | 通知事件 |
| `done` | message | 结束事件 |
| `heartbeat` | message | 心跳事件 |
| `tts` | message | 语音合成事件 |
| `interaction` | message | 交互事件 |
| `interactionAck` | message | 交互确认事件 |

### 7.3 流生命周期

1. 发送 `ChatRequest` 后先回 `{"op":"SET","mask":"chat","chat":{…},"eventOffset":0}`
2. 陆续收到 `message`（新消息占位）与 `block`（正文/思考/搜索增量）
3. 正文增量：`{"op":"APPEND","eventOffset":7,"block":{"id":"b1","messageId":"m1","text":{…}}}`
4. 联网/文件场景插入 `ref`（引用）与 `usage`（用量）
5. 需用户确认时下发 `interaction`，客户端回 `interactionAck`
6. 结束：`{"done":{}}`，随后 End-of-Stream 帧/连接关闭

---

## 8. 调用排坑说明

1. **两套前缀**：账号服务 `Logout` / `RefreshToken` 走 `/api`，其余走 `/apiv2`，混用会 404。
2. **必须带全设备头 + 风控头**，缺失会被风控拦截。
3. **Content-Type** 必须是 `application/connect+json`（流式）。
4. **流式接口**需按帧解析，勿一次性读 body。
5. **token 刷新**需并发去重，避免 refresh token 失效。
6. **枚举取值**是字符串（如 `FOLLOW_ACTION_FOLLOW`）。
7. **分页**多用 pageToken/cursor，翻页需回传上次返回值。
8. **图片直传**：GeneratePresignedURL → PUT → CreateFile。
9. **删除类无返回体**，成功即 200 空体。
10. **R8 混淆**：proto 类名被混淆，字段名保留于 descriptor，枚举类型名被剥离。

---

## 9. 使用建议

- 本文档**仅供学习参考**，不构成任何使用建议；
- 接口可能随时变更，使用前请自行验证；
- 如需用于生产，请优先考虑 Kimi 官方 OpenAPI。

---

## 10. 致谢

感谢所有为客户端接口研究做出贡献的研究者。
