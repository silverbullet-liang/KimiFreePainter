import { defineConfig } from 'vite'
import { readFileSync } from 'node:fs'
import react from '@vitejs/plugin-react'

const pkg = JSON.parse(readFileSync(new URL('./package.json', import.meta.url), 'utf-8'))

export default defineConfig({
  // 版本号来自 package.json，构建时注入，界面上显示的永远是产物真实版本
  define: { __APP_VERSION__: JSON.stringify(pkg.version) },
  plugins: [react()],
  base: process.env.VITE_BASE || '/',
  build: { outDir: 'dist', assetsInlineLimit: 4096 }
})
