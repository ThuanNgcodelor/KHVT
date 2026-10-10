import { spawn } from 'node:child_process'
import { createInterface } from 'node:readline'
import { fileURLToPath } from 'node:url'

// Exercise the real MCP stdio protocol; do not record tool arguments or replies
// because authenticated pages can contain business data and credentials.
export async function connectBrowserMcp() {
  const cli = fileURLToPath(new URL('./node_modules/@playwright/mcp/cli.js', import.meta.url))
  const config = fileURLToPath(new URL('./browser.config.json', import.meta.url))
  const child = spawn(process.execPath, [cli, '--config', config], { stdio: ['pipe', 'pipe', 'pipe'], windowsHide: true })
  const pending = new Map()
  let sequence = 0
  child.stderr.resume()
  createInterface({ input: child.stdout }).on('line', (line) => {
    let message
    try { message = JSON.parse(line) } catch { return }
    if (message.method === 'ping' && message.id !== undefined) {
      child.stdin.write(JSON.stringify({ jsonrpc: '2.0', id: message.id, result: {} }) + '\n')
    }
    const waiter = pending.get(message.id)
    if (waiter) {
      pending.delete(message.id); clearTimeout(waiter.timer)
      message.error ? waiter.reject(new Error(`MCP error ${message.error.code}`)) : waiter.resolve(message.result)
    }
  })
  const request = (method, params = {}) => new Promise((resolve, reject) => {
    const id = ++sequence
    const timer = setTimeout(() => { pending.delete(id); reject(new Error(`MCP timeout: ${method}`)) }, 60_000)
    pending.set(id, { resolve, reject, timer })
    child.stdin.write(JSON.stringify({ jsonrpc: '2.0', id, method, params }) + '\n')
  })
  child.on('exit', () => {
    for (const waiter of pending.values()) { clearTimeout(waiter.timer); waiter.reject(new Error('MCP server exited')) }
    pending.clear()
  })
  const info = await request('initialize', { protocolVersion: '2024-11-05', capabilities: {}, clientInfo: { name: 'khvt-local-check', version: '1.0.0' } })
  child.stdin.write(JSON.stringify({ jsonrpc: '2.0', method: 'notifications/initialized' }) + '\n')
  return {
    info, request,
    call: async (name, args = {}) => {
      const result = await request('tools/call', { name, arguments: args })
      if (result.isError) throw new Error(`MCP tool failed: ${name}`)
      return result
    },
    close: async () => { try { await request('tools/call', { name: 'browser_close', arguments: {} }) } finally { child.stdin.end(); child.kill() } },
  }
}
