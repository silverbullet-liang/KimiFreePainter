import { useEffect, useMemo, useRef, useState } from 'react'
import {
  AI_SOURCE,
  ApiError,
  clearTokens,
  createMoment,
  deleteMoment,
  extractMomentIds,
  getMoment,
  hasTokens,
  jwtLeftDays,
  listBots,
  listComments,
  listMyFeeds,
  loadTokens,
  pickNewArtifact,
  pickResult,
  saveTokens,
  type Moment
} from './api/kimi'

type Status = 'idle' | 'posting' | 'waiting' | 'done' | 'failed'
type Tab = 'paint' | 'history' | 'settings'
type Record = {
  id: string
  prompt: string
  title: string
  caption: string
  images: string[]
  at: number
  triggerId: string
  cleaned?: boolean
}

const HIST = 'kfp_history'
const MAX_PROMPT = 500

function loadHistory(): Record[] {
  try {
    return JSON.parse(localStorage.getItem(HIST) || '[]') as Record[]
  } catch {
    return []
  }
}

export default function App() {
  const [tab, setTab] = useState<Tab>('paint')
  const [prompt, setPrompt] = useState('')
  const [status, setStatus] = useState<Status>('idle')
  const [waited, setWaited] = useState(0)
  const [error, setError] = useState('')
  const [result, setResult] = useState<Record | null>(null)
  const [history, setHistory] = useState<Record[]>(loadHistory)
  const [tokens, setTokens] = useState(() => loadTokens())
  const [clean, setClean] = useState(() => localStorage.getItem('kfp_clean') || 'trigger')
  const [poll, setPoll] = useState(() => Number(localStorage.getItem('kfp_poll') || 10))
  const [openId, setOpenId] = useState<string | null>(null)
  const timer = useRef<number | null>(null)

  const logged = hasTokens()
  const refreshDays = useMemo(() => jwtLeftDays(tokens.refresh), [tokens])

  useEffect(() => {
    if (status !== 'waiting' && status !== 'posting') {
      if (timer.current) window.clearInterval(timer.current)
      return
    }
    timer.current = window.setInterval(() => setWaited((w) => w + 1), 1000)
    return () => {
      if (timer.current) window.clearInterval(timer.current)
    }
  }, [status])

  useEffect(() => {
    localStorage.setItem(HIST, JSON.stringify(history.slice(0, 40)))
  }, [history])

  async function start() {
    const text = prompt.trim()
    if (!text || status === 'posting' || status === 'waiting') return
    if (!logged) {
      setError('请先在设置里填写登录态')
      setTab('settings')
      return
    }
    setError('')
    setResult(null)
    setWaited(0)
    setStatus('posting')
    try {
      // 机器人列表拿不到也不阻断：正文里的 @ 才是真正触发的那一环
      const bots = await listBots().catch(() => [])
      const AI_BOT = 'AI画师'
      const bot =
        bots.find((b) => b.name.replace(/\s+/g, '') === AI_BOT) ??
        bots.find((b) => b.name.includes('画师'))
      const botId = bot?.userId ?? ''

      // 发帖前先记下当前已存在的全部动态 id，之后只认「新出现的」
      const before = await listMyFeeds(20)
      const knownIds = new Set(before.map((m) => m.id))
      history.forEach((h) => knownIds.add(h.id))
      const watermark = before.reduce((m, x) => Math.max(m, x.createTime), 0)

      const triggerId = await createMoment(text, botId, AI_BOT)
      if (!triggerId) throw new Error('发帖未返回动态 ID，请稍后重试')
      knownIds.add(triggerId)
      setStatus('waiting')

      const deadline = Date.now() + 300_000
      let hit: Moment | null = null
      let rounds = 0
      while (Date.now() < deadline) {
        await sleep(Math.max(8, poll) * 1000)
        rounds++
        const feeds = await listMyFeeds(20)
        // 诊断日志：出问题时在控制台能看到每轮抓到了什么
        console.debug(
          `[probe ${rounds}]`,
          feeds.map((f) => ({
            id: f.id.slice(-6),
            src: f.source,
            img: f.images.length,
            t: f.createTime,
            known: knownIds.has(f.id)
          }))
        )
        if (rounds <= 3 && !feeds.some((f) => f.id === triggerId)) {
          console.warn(`[probe] 第 ${rounds} 轮仍看不到刚发布的触发帖，列表可能取不到最新数据`)
        }
        const self = feeds.find((f) => f.id === triggerId)
        if (self && rounds === 1) {
          console.debug('[probe] 触发帖正文:', self.text)
        }
        hit =
          pickNewArtifact(feeds, triggerId, knownIds, text) ??
          pickResult(feeds, watermark, knownIds)
        if (hit) break

        // 列表接口可能不返回图片字段：对新出现的帖再拉一次详情确认
        const freshAny = feeds.filter((f) => f.id !== triggerId && !knownIds.has(f.id))
        for (const cand of freshAny) {
          const detail = await getMoment(cand.id).catch(() => null)
          if (detail && detail.images.length > 0) {
            hit = detail
            break
          }
        }
        if (hit) break

        // 每轮都做一次评论兜底：触发帖下的机器人回复里有成品帖链接
        const cs = await listComments(triggerId).catch(() => [])
        const reply = cs.find((c) => c.type === 'COMMENT_TYPE_BOT' || c.aiGenerated)
        if (reply) {
          console.debug('[probe] bot reply', reply.text.slice(0, 120))
          for (const id of extractMomentIds(reply.text)) {
            const m = await getMoment(id).catch(() => null)
            if (m && m.images.length && (m.source === AI_SOURCE || !knownIds.has(m.id))) {
              hit = m
              break
            }
          }
        }
        if (hit) break
      }
      if (!hit) throw new Error('等待超时，可稍后在历史里查看')

      const record: Record = {
        id: hit.id,
        prompt: text,
        title: hit.title,
        caption: cleanCaption(hit.text),
        images: hit.images.map((i) => i.originUrl || i.downloadUrl),
        at: Date.now(),
        triggerId
      }
      setResult(record)
      setHistory((h) => [record, ...h.filter((x) => x.id !== record.id)].slice(0, 40))
      setStatus('done')
      setPrompt('')

      // 清理：立即执行，结果写回本条记录，界面能看到成功与否
      if (clean !== 'none') {
        const ok = await cleanPosts(triggerId, clean === 'all' ? hit.id : '')
        setResult((r) => (r ? { ...r, cleaned: ok } : r))
        setHistory((list) =>
          list.map((x) => (x.id === record.id ? { ...x, cleaned: ok } : x))
        )
      }
    } catch (e) {
      setStatus('failed')
      const err = e as ApiError
      setError(
        err.code === 401
          ? '登录态已失效，请重新填写'
          : err.code === 429
            ? '请求太频繁，请稍后再试'
            : err.message || '出现问题，请稍后重试'
      )
    }
  }

  function persistTokens() {
    saveTokens({ access: tokens.access.trim(), refresh: tokens.refresh.trim() })
    setTokens({ access: tokens.access.trim(), refresh: tokens.refresh.trim() })
  }

  return (
    <div className="app">
      <header>
        <div className="brand">
          <Brush />
          <span>免费画师</span>
        </div>
        <span className={`dot ${logged ? 'on' : ''}`} title={logged ? '已填写登录态' : '未填写登录态'} />
      </header>

      <main>
        {tab === 'paint' && (
          <>
            <div className="field">
              <textarea
                value={prompt}
                onChange={(e) => setPrompt(e.target.value.slice(0, MAX_PROMPT))}
                placeholder="描述你想要的画面"
                rows={5}
              />
              <div className="counter">{prompt.length} / {MAX_PROMPT}</div>
            </div>

            <button className="primary" onClick={start} disabled={!prompt.trim() || status === 'posting' || status === 'waiting'}>
              {status === 'posting' ? '正在提交' : status === 'waiting' ? '画师正在创作' : '开始创作'}
            </button>

            {(status === 'posting' || status === 'waiting') && (
              <div className="card running">
                <span className="pulse" />
                <span>{status === 'posting' ? '正在提交…' : `画师正在创作… 已等待 ${waited} 秒`}</span>
              </div>
            )}

            {error && <p className="error">{error}</p>}

            {result && (
              <div className="card result">
                {result.images.map((src) => (
                  <img
                    key={src}
                    src={src}
                    alt={result.title || result.caption}
                    referrerPolicy="no-referrer"
                  />
                ))}
                {result.title && <p className="title">{result.title}</p>}
                {result.caption && <p className="caption">{result.caption}</p>}
                {result.cleaned !== undefined && (
                  <p className="hint">
                    {result.cleaned ? '社区帖子已清理' : '帖子清理失败，可在历史里重试'}
                  </p>
                )}
                <div className="row">
                  <a
                    className="primary ghost"
                    href={result.images[0]}
                    download
                    target="_blank"
                    rel="noreferrer"
                  >
                    保存图片
                  </a>
                  <button
                    className="ghost"
                    onClick={async () => {
                      if (!window.confirm('删除后社区里的图片将无法访问。是否同时删除触发帖与作品帖？')) return
                      const ok = await cleanPosts(result.triggerId, result.id)
                      setResult({ ...result, cleaned: ok })
                      setHistory((l) =>
                        l.map((x) => (x.id === result.id ? { ...x, cleaned: ok } : x))
                      )
                    }}
                  >
                    删除社区帖
                  </button>
                </div>
              </div>
            )}

            {!result && status === 'idle' && (
              <div className="empty">
                <Brush />
                <p>输入提示词，画师会为你开始创作</p>
                <small>生图由平台服务端完成，本页只负责触发与取回</small>
              </div>
            )}
          </>
        )}

        {tab === 'history' && (
          <>
            {history.length === 0 ? (
              <div className="empty">
                <Clock />
                <p>还没有作品</p>
                <small>完成一次创作后会出现在这里</small>
              </div>
            ) : (
              <ul className="list">
                {history.map((h) => (
                  <li key={h.id} onClick={() => setOpenId(h.id)}>
                    {h.images[0] ? <img src={h.images[0]} alt="" referrerPolicy="no-referrer" loading="lazy" /> : <div className="ph" />}
                    <div className="meta">
                      <span className="line">{h.prompt}</span>
                      <span className="time">{new Date(h.at).toLocaleString()}</span>
                    </div>
                    <button
                      className="icon"
                      aria-label="删除"
                      onClick={(e) => {
                        e.stopPropagation()
                        setHistory((list) => list.filter((x) => x.id !== h.id))
                      }}
                    >
                      <Trash />
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </>
        )}

        {tab === 'settings' && (
          <>
            <section>
              <h2>登录态</h2>
              <p className="hint">在平台网页登录后，从浏览器开发者工具复制两个令牌粘贴到此处。数据仅存本机。</p>
              <label>access_token（可直接粘贴整段 JSON / 两个 JWT，自动拆分）</label>
              <textarea
                rows={3}
                value={tokens.access}
                onChange={(e) => setTokens({ ...tokens, access: e.target.value })}
                onPaste={(e) => {
                  const blob = parseTokenBlob(e.clipboardData.getData('text'))
                  if (blob) {
                    e.preventDefault()
                    setTokens(blob)
                  }
                }}
              />
              <label>refresh_token</label>
              <textarea rows={3} value={tokens.refresh} onChange={(e) => setTokens({ ...tokens, refresh: e.target.value })} />
              <div className="row">
                <button className="primary" onClick={persistTokens}>保存</button>
                <button
                  className="ghost"
                  onClick={() => {
                    clearTokens()
                    setTokens({ access: '', refresh: '' })
                  }}
                >
                  清除
                </button>
              </div>
              <p className="hint">
                {logged ? `刷新态剩余 ${refreshDays >= 0 ? refreshDays + ' 天' : '未知'}` : '尚未填写'}
              </p>
            </section>

            <section>
              <h2>创作</h2>
              <label>清理方式</label>
              <select value={clean} onChange={(e) => { setClean(e.target.value); localStorage.setItem('kfp_clean', e.target.value) }}>
                <option value="none">不清理</option>
                <option value="trigger">只删触发帖</option>
                <option value="all">触发帖与作品帖都删</option>
              </select>
              <label>轮询间隔（秒）</label>
              <input
                type="number"
                min={8}
                max={30}
                value={poll}
                onChange={(e) => {
                  const v = Math.min(30, Math.max(8, Number(e.target.value) || 10))
                  setPoll(v)
                  localStorage.setItem('kfp_poll', String(v))
                }}
              />
            </section>

            <section>
              <h2>关于</h2>
              <p className="hint">版本 1.0.0 · GPL-3.0</p>
              <p className="hint">本页不含任何第三方分析或追踪脚本；图片与历史仅保存在浏览器本地。</p>
              <p className="hint">使用平台非公开接口，非官方产品，与平台方无关联。接口变更可能导致不可用。</p>
            </section>
          </>
        )}
      </main>

      <nav>
        <button className={tab === 'paint' ? 'on' : ''} onClick={() => setTab('paint')}><Brush /><span>创作</span></button>
        <button className={tab === 'history' ? 'on' : ''} onClick={() => setTab('history')}><Clock /><span>历史</span></button>
        <button className={tab === 'settings' ? 'on' : ''} onClick={() => setTab('settings')}><Gear /><span>设置</span></button>
      </nav>

      {openId && (
        <div className="sheet" onClick={() => setOpenId(null)}>
          {history
            .filter((h) => h.id === openId)
            .map((h) => (
              <div className="sheet-inner" key={h.id} onClick={(e) => e.stopPropagation()}>
                {h.images[0] && <img src={h.images[0]} alt={h.title || h.caption} referrerPolicy="no-referrer" />}
                {h.title && <p className="title">{h.title}</p>}
                {h.caption && <p className="caption">{h.caption}</p>}
                <p className="caption sub">提示词：{h.prompt}</p>
                <div className="row">
                  <a className="primary" href={h.images[0]} download target="_blank" rel="noreferrer">
                    保存图片
                  </a>
                  <button
                    className="ghost"
                    onClick={async () => {
                      if (!window.confirm('删除后社区里的图片将无法访问，确认删除这两个帖子？')) return
                      const ok = await cleanPosts(h.triggerId, h.id)
                      setHistory((l) => l.map((x) => (x.id === h.id ? { ...x, cleaned: ok } : x)))
                      setOpenId(null)
                    }}
                  >
                    删除社区帖
                  </button>
                </div>
              </div>
            ))}
        </div>
      )}
    </div>
  )
}

function sleep(ms: number) {
  return new Promise((r) => setTimeout(r, ms))
}

/** 成品帖正文尾部会带「—— AI画师」署名与「@AI画师 提示词 ——昵称」的引用块，展示时截掉。 */
function cleanCaption(raw: string): string {
  return (raw || '')
    .replace(/\n\s*@AI画师[\s\S]*$/, '')
    .replace(/\n\s*[—–-]{1,3}\s*AI画师\s*$/g, '')
    .trim()
}

/** 删帖：返回是否全部成功。结果页与历史详情都复用这一处。 */
async function cleanPosts(triggerId: string, resultId?: string): Promise<boolean> {
  let ok = true
  if (triggerId) {
    try {
      await deleteMoment(triggerId)
    } catch {
      ok = false
    }
  }
  if (resultId) {
    try {
      await deleteMoment(resultId)
    } catch {
      ok = false
    }
  }
  return ok
}

/**
 * 兼容三种剪贴板内容：JSON（accessToken/access_token/a 任意键名）、
 * 「access + refresh」两行、以及只含一个 JWT 的纯文本。
 */
function parseTokenBlob(raw: string): { access: string; refresh: string } | null {
  const text = raw.trim()
  if (!text) return null
  try {
    const j = JSON.parse(text) as { [k: string]: string }
    const access = j.accessToken || j.access_token || j.access || j.a || ''
    const refresh = j.refreshToken || j.refresh_token || j.refresh || j.r || ''
    if (access || refresh) return { access, refresh }
  } catch {
    /* 不是 JSON，继续按文本处理 */
  }
  const jwts = text.match(/eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+/g)
  if (jwts && jwts.length >= 2) return { access: jwts[0], refresh: jwts[1] }
  if (jwts && jwts.length === 1) return { access: jwts[0], refresh: '' }
  return null
}

function Brush() {
  return (
    <svg viewBox="0 0 24 24" width="20" height="20" aria-hidden="true">
      <path d="M16.5 2.5l5 5c.6.6.6 1.5 0 2.1L10.5 20.6 3.5 22.5l1.9-7L16.5 2.5z" fill="currentColor" />
    </svg>
  )
}
function Clock() {
  return (
    <svg viewBox="0 0 24 24" width="20" height="20" aria-hidden="true">
      <circle cx="12" cy="12" r="9" fill="none" stroke="currentColor" strokeWidth="1.8" />
      <path d="M12 7v5l3.2 2" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
    </svg>
  )
}
function Gear() {
  return (
    <svg viewBox="0 0 24 24" width="20" height="20" aria-hidden="true">
      <circle cx="12" cy="12" r="3.2" fill="none" stroke="currentColor" strokeWidth="1.8" />
      <path d="M12 3v2.2M12 18.8V21M3 12h2.2M18.8 12H21M5.6 5.6l1.6 1.6M16.8 16.8l1.6 1.6M18.4 5.6l-1.6 1.6M7.2 16.8l-1.6 1.6" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" />
    </svg>
  )
}
function Trash() {
  return (
    <svg viewBox="0 0 24 24" width="18" height="18" aria-hidden="true">
      <path d="M5 7h14M10 7V5h4v2M7 7l1 12h8l1-12" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" />
    </svg>
  )
}
