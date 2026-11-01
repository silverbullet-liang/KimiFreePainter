/**
 * Kimi Web 端扫码登录。
 *
 * 网关在 auth.kimi.com，不在 www.kimi.com/apiv2——后者对 AuthService 全 404。
 * 免 Authorization，CORS 放开；两个方法都属于 account.gateway.v1.AuthService。
 * 二维码内容与官网 WechatQrPanel 一致：https://www.kimi.com/wechat/mp/auth?id=<code>&device_id=<webId>
 */
const AUTH = 'https://auth.kimi.com/api'

async function rpc<T>(path: string, body: object): Promise<T> {
  const res = await fetch(AUTH + path, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'application/json',
      'x-msh-platform': 'web',
      'x-msh-version': '3.1.0',
      'X-Language': 'zh-CN'
    },
    body: JSON.stringify(body)
  })
  const text = await res.text()
  let data: any = null
  try {
    data = text ? JSON.parse(text) : {}
  } catch {
    data = {}
  }
  if (!res.ok) {
    const det = data?.details?.[0]?.debug?.localizedMessage?.message
    throw new Error(det || data?.message || data?.code || `HTTP ${res.status}`)
  }
  return data as T
}

/** 申请登录码。返回空串表示服务端没给 code。 */
export async function createQRCode(): Promise<string> {
  const d = await rpc<{ code?: string }>(
    '/account.gateway.v1.AuthService/CreateLoginQRCode',
    {}
  )
  return d?.code ? String(d.code) : ''
}

export type QRState = {
  /** STATUS_PENDING | STATUS_SCANNED | STATUS_EXPIRED | STATUS_SUCCESS */
  status: string
  accessToken: string
  refreshToken: string
  userId: string
}

export async function qrStatus(code: string): Promise<QRState> {
  const d = await rpc<any>('/account.gateway.v1.AuthService/GetLoginQRCodeStatus', { code })
  return {
    status: String(d?.status ?? ''),
    accessToken: pick(d, ['accessToken', 'access_token', 'token']),
    refreshToken: pick(d, ['refreshToken', 'refresh_token']),
    userId: String(d?.userId ?? '')
  }
}

/** 令牌字段名不稳定，多路径兜底。 */
function pick(d: any, keys: string[]): string {
  if (!d || typeof d !== 'object') return ''
  for (const k of keys) if (typeof d[k] === 'string' && d[k]) return d[k]
  for (const k of Object.keys(d)) {
    const v = d[k]
    if (v && typeof v === 'object') {
      for (const kk of keys) if (typeof v[kk] === 'string' && v[kk]) return v[kk]
    }
  }
  return ''
}

/**
 * device_id 是火山引擎发的设备标识。浏览器里没有那个 SDK，
 * 按官网同款字段自造一个并持久化，保证同一台设备多次登录是同 id。
 */
export function webId(): string {
  try {
    const snap = JSON.parse(localStorage.getItem('volcano-token-info') || 'null')
    if (snap?.webId) return String(snap.webId)
  } catch {
    /* 快照不存在时继续自造 */
  }
  let id = ''
  try {
    id = localStorage.getItem('kfp_qr_wid') || ''
  } catch {
    id = ''
  }
  if (!id) {
    const buf = new Uint8Array(8)
    try {
      crypto.getRandomValues(buf)
    } catch {
      for (let i = 0; i < 8; i++) buf[i] = (Math.random() * 256) | 0
    }
    id =
      'w' +
      Date.now().toString(36) +
      Array.from(buf)
        .map((x) => x.toString(16).padStart(2, '0'))
        .join('')
    try {
      localStorage.setItem('kfp_qr_wid', id)
    } catch {
      /* 隐私模式下写不进去，本次会话内有效即可 */
    }
  }
  return id
}

export function qrContent(code: string): string {
  return `https://www.kimi.com/wechat/mp/auth?id=${encodeURIComponent(code)}&device_id=${encodeURIComponent(webId())}`
}
