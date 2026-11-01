const BASE = 'https://www.kimi.com/apiv2'
const AUTH_BASE = 'https://auth.kimi.com/api'
const KEY = 'kfp_session'

export type Tokens = { access: string; refresh: string }

export class ApiError extends Error {
  code: number
  constructor(code: number, message: string) {
    super(message)
    this.code = code
  }
}

let tokens: Tokens = { access: '', refresh: '' }

export function loadTokens(): Tokens {
  try {
    const raw = localStorage.getItem(KEY)
    if (raw) tokens = JSON.parse(raw) as Tokens
  } catch {
    tokens = { access: '', refresh: '' }
  }
  return tokens
}

export function saveTokens(t: Tokens) {
  tokens = t
  localStorage.setItem(KEY, JSON.stringify(t))
}

export function clearTokens() {
  tokens = { access: '', refresh: '' }
  localStorage.removeItem(KEY)
}

export function hasTokens(): boolean {
  return !!(tokens.access || tokens.refresh)
}

/** 从 JWT 里读 exp，只用于展示剩余天数，不验签。 */
export function jwtLeftDays(token: string): number {
  try {
    const payload = token.split('.')[1]
    const json = JSON.parse(atob(payload.replace(/-/g, '+').replace(/_/g, '/')))
    if (!json.exp) return -1
    const left = json.exp - Math.floor(Date.now() / 1000)
    return left <= 0 ? 0 : Math.floor(left / 86400)
  } catch {
    return -1
  }
}

function headers(auth: boolean): Record<string, string> {
  const h: Record<string, string> = {
    'Content-Type': 'application/json',
    'R-Timezone': 'Asia/Shanghai',
    'x-msh-platform': 'android',
    'x-msh-os-version': '34',
    'x-msh-device-model': 'web',
    'x-msh-device-id': deviceId(),
    'x-msh-session-id': sessionId,
    'x-msh-version': '3.1.0',
    'x-msh-app-channel': 'official',
    'x-msh-android-version': '14',
    'X-Language': 'zh-CN'
  }
  if (auth && tokens.access) h['Authorization'] = `Bearer ${tokens.access}`
  return h
}

function deviceId(): string {
  const k = 'kfp_device'
  let v = localStorage.getItem(k)
  if (!v) {
    v = crypto.randomUUID()
    localStorage.setItem(k, v)
  }
  return v
}

const sessionId = crypto.randomUUID()

let refreshing: Promise<boolean> | null = null

async function refresh(): Promise<boolean> {
  if (refreshing) return refreshing
  refreshing = (async () => {
    if (!tokens.refresh) return false
    try {
      const res = await fetch(`${AUTH_BASE}/account.gateway.v1.AuthService/RefreshToken`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'x-msh-device-id': deviceId(),
          'x-msh-platform': 'android',
          'x-msh-session-id': sessionId,
          'x-msh-version': '3.1.0',
          'R-Timezone': 'Asia/Shanghai',
          'connect-protocol-version': '1'
        },
        body: JSON.stringify({ refresh_token: tokens.refresh })
      })
      if (!res.ok) return false
      const j = (await res.json()) as { accessToken?: string; refreshToken?: string }
      if (!j.accessToken) return false
      saveTokens({ access: j.accessToken, refresh: j.refreshToken || tokens.refresh })
      return true
    } catch {
      return false
    } finally {
      refreshing = null
    }
  })()
  return refreshing
}

async function call<T>(path: string, body: unknown, auth = true): Promise<T> {
  const send = async () => {
    const res = await fetch(`${BASE}${path}`, {
      method: 'POST',
      headers: headers(auth),
      body: JSON.stringify(body)
    })
    const text = await res.text()
    if (!text) {
      if (res.ok) return {} as T
      throw new ApiError(res.status, 'empty body')
    }
    let json: Record<string, unknown>
    try {
      json = JSON.parse(text)
    } catch {
      throw new ApiError(-1, '返回数据格式异常')
    }
    const code = typeof json.code === 'number' ? json.code : 200
    if (code !== 0 && code !== 200) {
      throw new ApiError(code, String(json.message || ''))
    }
    return json as T
  }

  try {
    return await send()
  } catch (e) {
    if (e instanceof ApiError && (e.code === 401 || e.code === 403)) {
      if (await refresh()) return await send()
      throw new ApiError(401, '登录态已失效，请重新填写')
    }
    throw e
  }
}

export type Bot = { userId: string; name: string; description?: string }

export type Image = {
  originUrl: string
  downloadUrl: string
  extra?: { width?: number; height?: number }
}

export type Moment = {
  id: string
  title: string
  text: string
  source: string
  createTime: number
  authorId: string
  refMomentId: string
  images: Image[]
}

function parseTime(v: unknown): number {
  if (typeof v === 'number') return v
  if (typeof v !== 'string') return 0
  const n = Number(v)
  if (!Number.isNaN(n) && v.trim() !== '') return n
  const d = Date.parse(v)
  return Number.isNaN(d) ? 0 : Math.floor(d / 1000)
}

function normImg(i: any): Image | null {
  if (!i) return null
  if (typeof i === 'string') {
    return { originUrl: i, downloadUrl: i }
  }
  const origin = i.originUrl || i.origin_url || i.url || i.imageUrl || i.image_url || ''
  const download = i.downloadUrl || i.download_url || i.url || origin
  if (!origin && !download) return null
  return { originUrl: origin, downloadUrl: download, extra: i.extra }
}

function parseMoment(m: any): Moment {
  const content = m?.content ?? {}
  const raw: any[] = content?.media?.images ?? []
  const images: Image[] = raw.map(normImg).filter((x): x is Image => !!x)
  const src = String(content?.source ?? '').trim().toUpperCase()
  return {
    id: m?.id ?? '',
    title: content?.title ?? '',
    text: content?.text ?? '',
    source: src,
    createTime: parseTime(m?.createTime),
    authorId: m?.author?.userId ?? '',
    refMomentId: m?.refMomentId ?? m?.content?.refMomentId ?? '',
    images
  }
}

export async function listBots(): Promise<Bot[]> {
  const r = await call<{ bots?: any[] }>('/moment.v1.UserService/ListMentionableBots', {
    sceneType: 'MENTIONABLE_SCENE_TYPE_AGENT'
  })
  return (r.bots ?? []).map((b) => ({
    userId: b.userId ?? '',
    name: b.name ?? '',
    description: b.description
  }))
}

export async function createMoment(
  prompt: string,
  botId: string,
  botName: string
): Promise<string> {
  const mention = `@${botName}`
  // 平台靠正文里的 @ 文本定位目标：只发 mentions 字段不会在动态里出现 @，
  // 机器人也就收不到。两者同时给，正文里的 @ 才是真正生效的那个。
  const text = prompt.includes(mention) ? prompt : `${mention} ${prompt}`
  const body: { [k: string]: unknown } = {
    text,
    visibility: 'MOMENT_VISIBILITY_PUBLIC'
  }
  // 拿不到机器人 userId 时只发正文 @：多一个空 id 反而可能被判参数非法
  if (botId) body.mentions = [{ userId: botId, name: botName }]
  const r = await call<{ moment?: { id?: string } }>('/moment.v1.MomentService/CreateMoment', body)
  return r.moment?.id ?? ''
}

export async function deleteMoment(momentId: string): Promise<void> {
  await call('/moment.v1.MomentService/DeleteMoment', { momentId })
}

export async function listMyFeeds(pageSize = 20): Promise<Moment[]> {
  const r = await call<{ feeds?: any[] }>('/moment.v1.FeedService/ListFeeds', {
    category: 'FEED_CATEGORY_USER_PUBLISH',
    pageSize
  })
  return (r.feeds ?? []).map((f) => parseMoment(f?.moment)).filter((m) => m.id)
}

export async function getMoment(id: string): Promise<Moment | null> {
  const r = await call<{ feed?: { moment?: any } }>('/moment.v1.FeedService/GetFeed', {
    feedType: 'FEED_TYPE_MOMENT',
    feedId: id
  })
  const m = r.feed?.moment
  return m ? parseMoment(m) : null
}

export type Comment = {
  id: string
  type: string
  aiGenerated: boolean
  text: string
  /** 富文本链接：机器人回复里的「查看作品」是超链接，URL 只在 links 里，正文拿不到 */
  links: string[]
}

export async function listComments(momentId: string): Promise<Comment[]> {
  const r = await call<{ comments?: any[] }>('/moment.v1.CommentService/ListComments', {
    momentId,
    pageSize: 20
  })
  return (r.comments ?? []).map((c) => ({
    id: c.id ?? '',
    type: c.type ?? '',
    aiGenerated: !!c.aiGenerated,
    text: c?.content?.text ?? '',
    links: (c?.links ?? c?.content?.links ?? [])
      .map((l: any) =>
        typeof l === 'string' ? l : l?.url || l?.linkUrl || l?.href || l?.link || ''
      )
      .filter((u: string) => !!u)
  }))
}

export const AI_SOURCE = 'MOMENT_SOURCE_AI'

/** 成品帖的唯一判据是 content.source === MOMENT_SOURCE_AI。 */
export function pickResult(
  feeds: Moment[],
  watermark: number,
  seen: Set<string>
): Moment | null {
  return (
    feeds.find(
      (m) => m.source === AI_SOURCE && m.createTime >= watermark && m.images.length > 0 && !seen.has(m.id)
    ) ?? null
  )
}

/**
 * 按「新增 id」识别成品帖——不依赖 createTime 的时钟与单位，比水位线稳。
 * 判据分三档：AI 来源 > 正文不是自己的提示词 > 只要是新出现且带图的帖。
 * 成品帖的 author 是发起人自己，所以 author 不能用来区分。
 */
export function pickNewArtifact(
  feeds: Moment[],
  triggerId: string,
  knownIds: Set<string>,
  prompt: string
): Moment | null {
  const fresh = feeds.filter(
    (m) => m.id && m.id !== triggerId && !knownIds.has(m.id) && m.images.length > 0
  )
  if (fresh.length === 0) return null
  const bySource = fresh.find((m) => m.source === AI_SOURCE)
  if (bySource) return bySource
  const p = prompt.trim()
  const byText = fresh.find((m) => (m.text ?? '').trim() !== p)
  return byText ?? fresh[0]
}

export function extractMomentIds(text: string): string[] {
  const out = new Set<string>()
  const re = /https?:\/\/[^\s，。、）)】\]"']+/g
  for (const hit of text.match(re) ?? []) {
    const path = hit.split('://')[1]?.split('/').slice(1).join('/') ?? ''
    const last = path.split('?')[0].split('#')[0].replace(/\/+$/, '').split('/').pop() ?? ''
    if (last.length >= 8 && /^[A-Za-z0-9_-]+$/.test(last)) out.add(last)
  }
  return [...out]
}

/**
 * 从成品帖正文里剥出「简介」。
 *
 * 成品帖正文的结构是：简介 + 「@画师 提示词」引用块 + 「—— 昵称」署名。
 * 这里不认画师名、也不认用户昵称，只按三件事剥离，换名字也不会失效：
 *   1. 以 @ 开头的提及行
 *   2. 纯破折号署名行
 *   3. 正文里原样引用的本次提示词（以 prompt 为准逐行比对）
 * 剥完为空时退回原文，宁可多显示也不空白。
 */
export function extractIntro(raw: string, prompt: string): string {
  const text = (raw || '').replace(/\r\n?/g, '\n')
  if (!text.trim()) return ''

  const norm = (s: string) => s.replace(/[\s@]/g, '').toLowerCase()
  const pn = norm(prompt || '')

  const junk = (line: string): boolean => {
    const s = line.trim()
    if (!s) return false
    if (/^@\S/.test(s)) return true // @画师 提及行
    if (/^[—–―]{2,}\s*\S{0,30}$/.test(s)) return true // —— 昵称
    if (pn) {
      const n = norm(s)
      if (n.length >= 6 && pn.includes(n)) return true // 提示词的一行
    }
    return false
  }

  const kept = text.split('\n').filter((l) => !junk(l))

  // 再按段清一次：提示词被平台合并/改写后，整段仍可能是它的片段
  const blocks = kept.join('\n').split(/\n{2,}/).filter((b) => {
    const n = norm(b)
    if (!n) return false
    if (!pn || n.length < 10) return true
    return !(pn.includes(n) || n.includes(pn))
  })

  const out = blocks.join('\n\n').replace(/\n{3,}/g, '\n\n').trim()
  if (out) return out

  return text
    .replace(/^@\S.*$/gm, '')
    .replace(/^[—–―]{2,}\s*\S{0,30}$/gm, '')
    .replace(/\n{3,}/g, '\n\n')
    .trim()
}
