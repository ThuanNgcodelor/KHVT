import { spawn } from 'node:child_process'
import { fileURLToPath } from 'node:url'
import assert from 'node:assert/strict'
import { connectBrowserMcp } from './client.mjs'
const root = fileURLToPath(new URL('../../',import.meta.url))
const server = spawn(process.execPath,[fileURLToPath(new URL('../local-test/serve.mjs',import.meta.url))], {cwd:root,env:{...process.env,QMH_WEB_PORT:'5190',NODE_OPTIONS:'--max-old-space-size=48'},stdio:'ignore',windowsHide:true})
let mcp
try {
  const deadline=Date.now()+20_000
  while(Date.now()<deadline) { try { if((await fetch('http://127.0.0.1:5190')).ok) break } catch {} await new Promise((wait)=>setTimeout(wait,200)) }
  mcp=await connectBrowserMcp({initPage:fileURLToPath(new URL('./ui-fixture.ts',import.meta.url)),diagnostics:true})
  const list=await mcp.request('tools/list'); assert(list.tools.some((tool)=>tool.name==='browser_run_code_unsafe'))
  await mcp.call('browser_navigate',{url:'http://127.0.0.1:5190/modules'})
  await mcp.call('browser_run_code_unsafe',{code:`async (page) => { await page.getByRole('heading',{name:'Ứng dụng của bạn'}).waitFor(); await page.getByRole('link',{name:/Mua hàng Đơn mua/}).click(); await page.getByRole('heading',{name:'Tổng quan mua hàng'}).waitFor(); return 'Portal and dashboard ready'; }`})
  await mcp.call('browser_run_code_unsafe',{code:`async (page) => { await page.goto('http://127.0.0.1:5190/purchase-orders/new'); await page.getByLabel('NCC mặc định',{exact:true}).selectOption('1'); await page.getByLabel('VAT (%)',{exact:true}).selectOption('8'); await page.getByLabel('Tên vật tư dòng 1',{exact:true}).fill('Vật tư MCP mẫu'); await page.getByLabel('Số lượng dòng 1',{exact:true}).fill('2'); await page.getByLabel('Đơn giá dòng 1',{exact:true}).fill('100'); await page.getByRole('button',{name:'Kiểm tra và lưu bản nháp'}).click(); await page.getByRole('button',{name:'Lưu bản nháp nhóm này'}).click(); await page.getByRole('link',{name:/Đã lưu PO-TEST-1/}).click(); await page.getByRole('heading',{name:'PO-TEST-1',exact:true}).waitFor(); return 'PO fixture draft created and detail verified'; }`})
  for(const [path,heading] of [['catalog/materials','Vật tư'],['catalog/suppliers','Nhà cung cấp'],['price-search','Tra cứu giá'],['imports','Nhập dữ liệu']]) {
    await mcp.call('browser_run_code_unsafe',{code:`async (page) => { await page.goto('http://127.0.0.1:5190/${path}'); await page.getByRole('heading',{name:${JSON.stringify(heading)},exact:true}).waitFor(); await page.waitForLoadState('networkidle'); if(await page.getByRole('alert').count()) throw new Error('Unexpected alert'); return 'Page ready'; }`})
  }
  await mcp.call('browser_run_code_unsafe',{code:`async (page) => { await page.setViewportSize({width:390,height:844}); await page.goto('http://127.0.0.1:5190/purchase-orders/new'); await page.getByRole('heading',{name:'Lập đơn mua'}).waitFor(); if(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth+1)) throw new Error('Page overflow'); await page.screenshot({path:'source/target/runtime/mcp-po-mobile.png',fullPage:true}); return 'Mobile checked'; }`})
  console.log('PASS real MCP initialize, tools/list, browser navigation, portal, purchasing workflow and mobile. API data is synthetic.')
} finally { if(mcp) await mcp.close(); server.kill() }
