import { useCallback, useEffect, useRef, useState } from 'react'
import qrcode from 'qrcode-generator'
import { createQRCode, qrContent, qrStatus } from './api/auth'

type Tone = '' | 'wait' | 'ok' | 'bad'

/**
 * 后台标签页会节流甚至冻结主线程定时器，扫码时浏览器恰恰在后台。
 * 心跳放进 Worker（不受可见性节流影响），补拉一次 visibilitychange；
 * 环境禁用 Blob Worker 时退回 setTimeout。
 */
function heartbeat(ms: number, fn: () => void): () => void {
  let timer = 0
  let worker: Worker | null = null
  let url = ''
  const onVisible = () => {
    if (!document.hidden) fn()
  }
  document.addEventListener('visibilitychange', onVisible)
  try {
    url = URL.createObjectURL(
      new Blob(
        [
          `var t=null;self.onmessage=function(e){if(e.data==='start'){if(t)clearInterval(t);t=setInterval(function(){self.postMessage(1)},${ms})}else if(e.data==='stop'){if(t)clearInterval(t);t=null}}`
        ],
        { type: 'text/javascript' }
      )
    )
    worker = new Worker(url)
    worker.onmessage = () => fn()
    worker.postMessage('start')
  } catch {
    const loop = () => {
      fn()
      timer = window.setTimeout(loop, ms)
    }
    timer = window.setTimeout(loop, ms)
  }
  return () => {
    document.removeEventListener('visibilitychange', onVisible)
    if (timer) clearTimeout(timer)
    if (worker) worker.terminate()
    if (url) URL.revokeObjectURL(url)
  }
}

export default function QrLogin({
  onDone
}: {
  onDone: (access: string, refresh: string) => void
}) {
  const box = useRef<HTMLDivElement>(null)
  const stop = useRef<() => void>(() => undefined)
  const gen = useRef(0)
  const [open, setOpen] = useState(false)
  const [hint, setHint] = useState('')
  const [tone, setTone] = useState<Tone>('')

  const draw = (code: string) => {
    const el = box.current
    if (!el) return false
    try {
      const qr = qrcode(0, 'M')
      qr.addData(qrContent(code))
      qr.make()
      el.innerHTML = qr.createSvgTag({ cellSize: 4, margin: 2, scalable: true })
      const svg = el.querySelector('svg')
      if (svg) {
        svg.setAttribute('width', '100%')
        svg.setAttribute('height', '100%')
      }
      return true
    } catch {
      el.textContent = '二维码渲染失败'
      return false
    }
  }

  const start = useCallback(async () => {
    const mine = ++gen.current
    stop.current()
    setTone('')
    setHint('正在获取二维码…')
    if (box.current) box.current.innerHTML = ''

    let code = ''
    try {
      code = await createQRCode()
    } catch (e) {
      if (mine !== gen.current) return
      setTone('bad')
      setHint('获取二维码失败：' + (e as Error).message)
      return
    }
    if (mine !== gen.current) return
    if (!code) {
      setTone('bad')
      setHint('服务端未返回登录码')
      return
    }
    if (!draw(code)) {
      setTone('bad')
      setHint('二维码渲染失败')
      return
    }
    setHint('请用 Kimi App 扫描二维码')

    const deadline = Date.now() + 5 * 60 * 1000
    let busy = false
    let last = 0
    const finish = (t: Tone, text: string) => {
      setTone(t)
      setHint(text)
      stop.current()
    }
    const tick = async () => {
      if (mine !== gen.current) return
      const now = Date.now()
      if (busy || now - last < 900) return
      if (now > deadline) {
        finish('bad', '二维码已过期，点「刷新二维码」')
        return
      }
      last = now
      busy = true
      try {
        const s = await qrStatus(code)
        if (mine !== gen.current) return
        if (s.status.includes('SUCCESS')) {
          if (!s.accessToken) {
            finish('bad', '登录成功但未取到令牌')
            return
          }
          finish('ok', '登录成功')
          onDone(s.accessToken, s.refreshToken)
          return
        }
        if (s.status.includes('EXPIRED')) {
          finish('bad', '二维码已过期，点「刷新二维码」')
          return
        }
        if (s.status.includes('SCANNED')) {
          setTone('wait')
          setHint('已扫码，请在手机上确认')
        } else {
          setTone('')
          setHint('请用 Kimi App 扫描二维码')
        }
      } catch {
        last = 0 // 网络抖动：保留轮询，下一拍重试
      } finally {
        busy = false
      }
    }
    stop.current = heartbeat(1200, tick)
  }, [onDone])

  useEffect(() => () => stop.current(), [])

  if (!open) {
    return (
      <button
        className="ghost wide"
        onClick={() => {
          setOpen(true)
          void start()
        }}
      >
        扫码登录
      </button>
    )
  }

  return (
    <div className="qr">
      <div className="qr-box" ref={box} />
      <p className={`qr-hint ${tone}`}>{hint || '正在获取二维码…'}</p>
      <button className="ghost wide" onClick={() => void start()}>
        刷新二维码
      </button>
    </div>
  )
}
