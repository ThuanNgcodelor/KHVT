import assert from 'node:assert/strict'
import { connectBrowserMcp } from './client.mjs'
const mcp = await connectBrowserMcp()
try {
  const list = await mcp.request('tools/list')
  assert(list.tools.some((tool) => tool.name === 'browser_navigate'))
  await mcp.call('browser_navigate', { url: process.env.QMH_BROWSER_URL ?? 'http://127.0.0.1:5173/login' })
  const snapshot = await mcp.call('browser_snapshot')
  assert(snapshot.content.some((item) => item.type === 'text' && item.text.includes('Đăng nhập')))
  console.log(`PASS MCP handshake, ${list.tools.length} tools, browser navigation and login screen`)
} finally { await mcp.close() }
