import { useEffect, useMemo, useRef, useState } from 'react'
import {
  AI_SOURCE,
  ApiError,
  clearTokens,
  createMoment,
  deleteMoment,
  extractIntro,
  extractMomentIds,
  getMe,
  getFeedBundle,
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
  uploadImage,
  type Moment,
  type UploadedImage
} from './api/kimi'
import QrLogin from './QrLogin'
import { refreshTokens } from './api/auth'

declare const __APP_VERSION__: string

type Status = 'idle' | 'posting' | 'waiting' | 'done' | 'failed'
type Tab = 'paint' | 'history' | 'settings'
type Record = {
  id: string
  prompt: string
  title: string
  caption: string
  raw?: string
  excerpt?: string
  images: string[]
  at: number
  triggerId: string
  cleaned?: boolean
}

const HIST = 'kfp_history'

/** 算法更新后旧记录也能重新解析：有原文就现算，没有才用当时存的。标题已在正文里时去重。 */
function captionOf(r: Record): string {
  const c = r.raw ? extractIntro(r.raw, r.prompt) || r.excerpt || '' : r.caption
  if (r.title && c.startsWith(r.title)) return c.slice(r.title.length).replace(/^[\s:：]+/, '')
  return c
}

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
  const [warn, setWarn] = useState('')
  const [upd, setUpd] = useState('')
  const [updLinks, setUpdLinks] = useState('')
  const [result, setResult] = useState<Record | null>(null)
  const [history, setHistory] = useState<Record[]>(loadHistory)
  const [tokens, setTokens] = useState(() => loadTokens())
  const [clean, setClean] = useState(() => localStorage.getItem('kfp_clean') || 'trigger')
  const [poll, setPoll] = useState(() => Number(localStorage.getItem('kfp_poll') || 10))
  const [openId, setOpenId] = useState<string | null>(null)
  const [nick, setNick] = useState(() => localStorage.getItem('kfp_nick') || '')
  const [pick, setPick] = useState<File | null>(null)
  const [pickUrl, setPickUrl] = useState('')
  const timer = useRef<number | null>(null)
  const stopRef = useRef(false)

  // 登录态实时维护：access 到期自动用刷新态换新，换不动就判定掉线
  useEffect(() => {
    if (!tokens.refresh) return
    let alive = true
    const check = async () => {
      const left = jwtLeftDays(tokens.access)
      if (left >= 0) return
      try {
        const next = await refreshTokens(tokens.refresh)
        if (!alive || !next.access) return
        setTokens(next)
        saveTokens(next)
      } catch {
        /* 刷新失败不动它：可能是临时网络问题，下一轮再试 */
      }
    }
    void check()
    const t = window.setInterval(() => void check(), 60_000)
    return () => {
      alive = false
      window.clearInterval(t)
    }
  }, [tokens.access, tokens.refresh])

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

  /** 双击版本号：比对 GitHub 最新 Release。网页版刷新即更新，APK 另给下载地址。 */
  async function checkUpdate() {
    setUpd('检查中…')
    setUpdLinks('')
    try {
      const r = await fetch(
        'https://api.github.com/repos/silverbullet-liang/KimiFreePainter/releases/latest',
        { headers: { Accept: 'application/vnd.github+json' } }
      )
      const j = (await r.json()) as { tag_name?: string; html_url?: string }
      const remote = String(j.tag_name || '').replace(/^v/, '')
      if (!remote) throw new Error('no tag')
      const newer = remote.localeCompare(__APP_VERSION__, undefined, { numeric: true }) > 0
      if (newer) {
        setUpd(`发现 ${remote}，正在刷新`)
        setUpdLinks(
          `https://github.com/silverbullet-liang/KimiFreePainter/releases/download/v${remote}/KimiFreePainter-v${remote}.apk`
        )
        window.setTimeout(() => window.location.reload(), 1200)
      } else {
        setUpd('已是最新版本')
        window.setTimeout(() => setUpd(''), 2500)
      }
    } catch {
      setUpd('检查失败，请稍后再试')
      window.setTimeout(() => setUpd(''), 2500)
    }
  }

  async function start() {
    const text = prompt.trim()
    if (!text || status === 'posting' || status === 'waiting') return
    if (!logged) {
      setError('请先在设置页扫码登录')
      setTab('settings')
      return
    }
    setError('')
    setWarn('')
    setResult(null)
    stopRef.current = false
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

      // 带图即改图：一条动态只吃一张，平台侧限制。
      // 上传失败不阻塞：降级为纯文字发出，把原因写在界面上，而不是让按钮点了没反应。
      let image: UploadedImage | null = null
      if (pick) {
        try {
          image = await uploadImage(pick)
        } catch (e) {
          setWarn(`图片没能上传：${(e as Error).message}。本次按纯文字发出。`)
        }
      }

      let triggerId = ''
      try {
        triggerId = await createMoment(text, botId, AI_BOT, image)
      } catch (e) {
        if (!image) throw e
        setWarn(`带图发帖被拒（${(e as Error).message}），已按纯文字重试。`)
        image = null
        triggerId = await createMoment(text, botId, AI_BOT, null)
      }
      if (!triggerId) throw new Error('发帖没有返回动态 ID')

      // 回查确认：接口给了 id 不代表动态真的存在（可能被风控吞掉）。
      // 查不到就当场失败，不能让界面一直停在「画师正在创作」。
      let self = await getMoment(triggerId).catch(() => null)
      for (let i = 0; i < 2 && (!self || !self.id); i++) {
        await sleep(1500)
        self = await getMoment(triggerId).catch(() => null)
      }
      if (!self || !self.id) {
        throw new Error('动态没有发布成功：平台查不到这条动态，可能被拦截或账号受限')
      }
      if (self.text && !self.text.includes('@')) {
        throw new Error('动态发出去了，但正文里的 @ 没生效，画师收不到，请重试')
      }
      if (image && self.images.length === 0) {
        setWarn('图片没有随动态发出，本次按纯文字生成。')
      }

      knownIds.add(triggerId)
      setStatus('waiting')

      const deadline = Date.now() + 300_000
      let hit: Moment | null = null
      let rounds = 0
      let diag = ''
      while (Date.now() < deadline) {
        await sleep(Math.max(8, poll) * 1000)
        if (stopRef.current) throw new Error('已停止等待（触发帖不会被删除）')
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
        diag = `抓到 ${feeds.length} 条 / 带图 ${feeds.filter((x) => x.images.length > 0).length} 条 / AI来源 ${
          feeds.filter((x) => x.source === AI_SOURCE).length
        } 条 / 含引用 ${feeds.filter((x) => x.refMomentId).length} 条`

        // 判据一：引用关系，成品帖引用触发帖，或触发帖反过来引用成品帖
        hit = feeds.find((x) => x.refMomentId && x.refMomentId === triggerId) ?? null
        if (!hit) {
          const selfPost = feeds.find((x) => x.id === triggerId)
          const back = selfPost?.refMomentId
          if (back && back !== triggerId) {
            hit = await getMoment(back).catch(() => null)
          }
        }
        // 判据二：新出现且有图的帖（图在 media 或 cover 都算）
        if (!hit) {
          hit =
            pickNewArtifact(feeds, triggerId, knownIds, text) ??
            pickResult(feeds, watermark, knownIds)
        }
        if (hit) break

        // 判据三：对新出现的帖逐个回查详情。有图、来源是 AI、或正文引用了本次提示词，都算成品帖
        const freshAny = feeds.filter((x) => x.id !== triggerId && !knownIds.has(x.id))
        const head = text.slice(0, 12)
        for (const cand of freshAny) {
          const detail = await getMoment(cand.id).catch(() => null)
          if (!detail) continue
          if (
            detail.images.length > 0 ||
            detail.cover !== '' ||
            detail.source === AI_SOURCE ||
            (head.length >= 4 && (detail.text ?? '').includes(head))
          ) {
            hit = detail
            break
          }
        }
        if (hit) break

        // 判据四：触发帖的评论。链接是富文本，URL 在 links 里而不是正文
        const cs = await listComments(triggerId).catch(() => [])
        const reply = cs.find((c) => c.type === 'COMMENT_TYPE_BOT' || c.aiGenerated)
        if (reply) {
          console.debug('[probe] bot reply', reply.links, reply.text.slice(0, 80))
          for (const u of [...reply.links, reply.text]) {
            for (const id of extractMomentIds(u)) {
              const m = await getMoment(id).catch(() => null)
              if (m && (m.images.length > 0 || m.source === AI_SOURCE)) {
                hit = m
                break
              }
            }
            if (hit) break
          }
        }
        if (hit) break

        // 判据五：GetFeed 的 similarMoments，成品帖有时只出现在这里
        const bundle = await getFeedBundle(triggerId).catch(() => null)
        if (bundle) {
          const pool = [...bundle.similar, ...(bundle.moment ? [bundle.moment] : [])]
          hit =
            pool.find(
              (x) =>
                x.id !== triggerId &&
                (x.images.length > 0 || x.cover !== '' || x.source === AI_SOURCE)
            ) ?? null
        }
        if (hit) break

        if (hit) break
      }
      if (!hit) throw new Error(`等待超时。诊断：${diag}`)

      const record: Record = {
        id: hit.id,
        prompt: text,
        title: hit.title,
        caption: extractIntro(hit.text, text) || hit.excerpt || '',
        raw: hit.text,
        excerpt: hit.excerpt,
        images: hit.images.map((i) => i.originUrl || i.downloadUrl),
        at: Date.now(),
        triggerId
      }
      setResult(record)
      setHistory((h) => [record, ...h.filter((x) => x.id !== record.id)].slice(0, 40))
      setStatus('done')
      setPrompt('')
      setPick(null)
      setPickUrl('')

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

  return (
    <div className="app">
      <header>
        <div className="brand">
          <Brush />
          <span>免费画师</span>
        </div>
        <span className={`who ${logged ? 'on' : ''}`}>
          <span className={`dot ${logged ? 'on' : ''}`} />
          {logged ? nick || '已登录' : '未登录'}
        </span>
      </header>

      <main>
        {tab === 'paint' && (
          <>
            <div className="field">
              <textarea
                value={prompt}
                onChange={(e) => {
                  setPrompt(e.target.value)
                  // 长提示词自动撑高，超出视口后再内部滚动
                  const el = e.currentTarget
                  el.style.height = 'auto'
                  el.style.height = `${el.scrollHeight}px`
                }}
                placeholder="描述你想要的画面"
                rows={5}
              />
              <div className="counter">{prompt.length} 字</div>
            </div>

            <div className="picker">
              <label className="pick-btn">
                <input
                  type="file"
                  accept="image/*"
                  onChange={(e) => {
                    const f = e.target.files?.[0] ?? null
                    e.target.value = ''
                    if (!f) return
                    if (pickUrl) URL.revokeObjectURL(pickUrl)
                    setPick(f)
                    setPickUrl(URL.createObjectURL(f))
                  }}
                />
                <ImageIcon />
                添加图片（改图）
              </label>
              {pickUrl && (
                <div className="pick-prev">
                  <img src={pickUrl} alt="" draggable={false} />
                  <button
                    className="icon"
                    aria-label="移除图片"
                    onClick={() => {
                      URL.revokeObjectURL(pickUrl)
                      setPick(null)
                      setPickUrl('')
                    }}
                  >
                    <Trash />
                  </button>
                </div>
              )}
            </div>

            <button className="primary" onClick={start} disabled={!prompt.trim() || status === 'posting' || status === 'waiting'}>
              {status === 'posting' ? '正在提交' : status === 'waiting' ? '画师正在创作' : '开始创作'}
            </button>

            {(status === 'posting' || status === 'waiting') && (
              <div className="card running">
                <span className="pulse" />
                <span>{status === 'posting' ? '正在提交…' : `画师正在创作… 已等待 ${waited} 秒`}</span>
                <button className="ghost small" onClick={() => { stopRef.current = true }}>停止</button>
              </div>
            )}

            {warn && (
              <div className="card warn">
                <strong>提示</strong>
                <span>{warn}</span>
              </div>
            )}

            {error && (
              <div className="card err">
                <strong>没能完成</strong>
                <span>{error}</span>
              </div>
            )}

            {result && (
              <div className="card result">
                {result.images.map((src) => (
                  <img
                    key={src}
                    src={src}
                    alt={result.title || captionOf(result)}
                    referrerPolicy="no-referrer"
                  />
                ))}
                {result.title && <p className="title">{result.title}</p>}
                {captionOf(result) && <p className="caption">{captionOf(result)}</p>}
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
              <h2>扫码登录</h2>
              <p className="hint">用 Kimi App 扫码，令牌自动写入本机，不经过任何服务器。</p>
              <QrLogin
                onDone={(access, refresh) => {
                  const next = { access, refresh }
                  setTokens(next)
                  saveTokens(next)
                  void getMe()
                    .then((u) => {
                      setNick(u.name)
                      localStorage.setItem('kfp_nick', u.name)
                    })
                    .catch(() => undefined)
                }}
              />
            </section>

            <section>
              <h2>账号</h2>
              {logged ? (
                <>
                  <p className="hint">
                    {nick || '已登录'} · 刷新态剩余 {refreshDays >= 0 ? refreshDays + ' 天' : '未知'}
                  </p>
                  <button
                    className="ghost wide"
                    onClick={() => {
                      clearTokens()
                      setTokens({ access: '', refresh: '' })
                      setNick('')
                      localStorage.removeItem('kfp_nick')
                    }}
                  >
                    退出登录
                  </button>
                </>
              ) : (
                <p className="hint">还未登录，扫上面的二维码即可，不需要填写任何令牌。</p>
              )}
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
              <p className="hint ver" onDoubleClick={checkUpdate} title="双击检查更新">
                版本 {__APP_VERSION__}
                {upd ? ` · ${upd}` : ''} · GPL-3.0
              </p>
              {updLinks && (
                <p className="hint">
                  新版 APK：
                  <a href={updLinks} target="_blank" rel="noreferrer">{updLinks.split('/').pop()}</a>
                </p>
              )}
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
                {h.images[0] && <img src={h.images[0]} alt={h.title || captionOf(h)} referrerPolicy="no-referrer" />}
                {h.title && <p className="title">{h.title}</p>}
                {captionOf(h) && <p className="caption">{captionOf(h)}</p>}
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
function ImageIcon() {
  return (
    <svg viewBox="0 0 24 24" width="16" height="16" aria-hidden="true">
      <rect x="3.5" y="5" width="17" height="14" rx="3" fill="none" stroke="currentColor" strokeWidth="1.6" />
      <circle cx="9" cy="10" r="1.6" fill="currentColor" />
      <path d="M5 17l4.5-4.5 3.5 3 3-2.5L20 17" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinejoin="round" />
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
