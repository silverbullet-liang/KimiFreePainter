// 三种模式的提示词组装与漫画长图拼接。生图本身始终走 @AI画师 发帖链路。

export type Mode = 'normal' | 'comic' | 'clean'

export const MODE_LABEL: Record<Mode, string> = {
  normal: '生图',
  comic: '漫画',
  clean: '去水印'
}

/** 去水印：位置选项。custom 时由用户输入具体位置描述。 */
export const WM_POSITIONS = [
  { value: '左上角', label: '左上角' },
  { value: '右上角', label: '右上角' },
  { value: '左下角', label: '左下角' },
  { value: '右下角', label: '右下角' },
  { value: '中央', label: '中央' },
  { value: '四周', label: '四周' },
  { value: 'custom', label: '自定义…' }
]

/**
 * 去水印不调用任何官方去水印接口：把位置与内容写进提示词，让画师重绘一张无水印图。
 */
export function cleanPrompt(position: string, content: string): string {
  const where = position === 'custom' ? '指定位置' : position
  const what = content.trim()
  return [
    `请去除图片${where}的水印${what ? `「${what}」` : ''}`,
    '只移除水印本身，完整保留原图的构图、人物、色彩、光影和清晰度，其余部分不要改动',
    '输出一张不带任何水印的完整图片'
  ].join('。')
}

/** 统一背景与风格自动附加到每一格，不做一次性生成。 */
export function cellPrompt(background: string, style: string, cell: string, index: number, total: number): string {
  const parts = [
    background.trim(),
    style.trim(),
    cell.trim() || `第 ${index + 1} 格画面`
  ].filter(Boolean)
  return `${parts.join('，')}。这是 ${total} 格漫画中的第 ${index + 1} 格，只画这一格，输出一张单幅图片。`
}

/** 把远端图片读成本地位图；跨域失败由调用方降级处理。 */
async function loadBitmap(url: string): Promise<ImageBitmap> {
  const res = await fetch(url, { mode: 'cors', cache: 'force-cache' })
  if (!res.ok) throw new Error(`图片读取失败 ${res.status}`)
  const blob = await res.blob()
  return createImageBitmap(blob)
}

/** 四边是否已有明显白边：任一边的近白像素占比 ≥ 80% 即认为有。 */
function hasWhiteBorder(bitmap: ImageBitmap): boolean {
  const w = bitmap.width
  const h = bitmap.height
  if (!w || !h) return false
  const c = document.createElement('canvas')
  c.width = w
  c.height = h
  const ctx = c.getContext('2d', { willReadFrequently: true })!
  ctx.drawImage(bitmap, 0, 0)
  const d = ctx.getImageData(0, 0, w, h).data
  const near = (i: number) => d[i] > 235 && d[i + 1] > 235 && d[i + 2] > 235
  const ratio = (pick: (k: number) => number, count: number) => {
    let n = 0
    for (let k = 0; k < count; k++) if (pick(k) * 4 + 3 < d.length && near(pick(k) * 4)) n++
    return n / count
  }
  const row = (y: number) => (x: number) => y * w + x
  const col = (x: number) => (y: number) => y * w + x
  const edge = Math.max(
    ratio(row(0), w),
    ratio(row(h - 1), w),
    ratio(col(0), h),
    ratio(col(w - 1), h)
  )
  return edge >= 0.8
}

/**
 * 纵向拼接多格为长图：已有白边直接拼，没有就先统一加白边。
 * 返回 blob URL；读图失败时抛错，由调用方降级为逐格展示。
 */
export async function stitchComic(urls: string[]): Promise<string> {
  if (urls.length === 0) throw new Error('没有可拼接的图片')
  const bits: ImageBitmap[] = []
  for (const u of urls) bits.push(await loadBitmap(u))
  try {
    const pad = 24
    const needPad = bits.some((b) => !hasWhiteBorder(b))
    const cellW = Math.max(...bits.map((b) => b.width)) + (needPad ? pad * 2 : 0)
    const cellH = Math.max(...bits.map((b) => b.height)) + (needPad ? pad * 2 : 0)
    const c = document.createElement('canvas')
    c.width = cellW
    c.height = cellH * bits.length
    const ctx = c.getContext('2d')!
    ctx.fillStyle = '#ffffff'
    ctx.fillRect(0, 0, c.width, c.height)
    bits.forEach((b, i) => {
      const x = needPad ? pad + Math.round((cellW - pad * 2 - b.width) / 2) : Math.round((cellW - b.width) / 2)
      const y = i * cellH + (needPad ? pad + Math.round((cellH - pad * 2 - b.height) / 2) : Math.round((cellH - b.height) / 2))
      ctx.drawImage(b, x, y)
    })
    return c.toDataURL('image/png')
  } finally {
    bits.forEach((b) => b.close())
  }
}
