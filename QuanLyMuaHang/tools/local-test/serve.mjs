import { createServer, request } from 'node:http'
import { createReadStream } from 'node:fs'
import { stat } from 'node:fs/promises'
import { resolve, join, extname } from 'node:path'
import { fileURLToPath } from 'node:url'
const dist = fileURLToPath(new URL('../../source/frontend/dist/', import.meta.url))
const types = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.css': 'text/css; charset=utf-8', '.svg': 'image/svg+xml' }
createServer(async (req, res) => {
  if (/^\/(api|actuator)(\/|$)/.test(req.url)) {
    const upstream = request({ hostname:'127.0.0.1',port:Number(process.env.QMH_PROXY_PORT ?? 8081),path:req.url,method:req.method,headers:req.headers }, (reply) => { res.writeHead(reply.statusCode, reply.headers); reply.pipe(res) })
    upstream.on('error', () => { if (!res.headersSent) res.writeHead(502); res.end() }); req.pipe(upstream); return
  }
  const path = resolve(dist, '.' + decodeURIComponent(new URL(req.url,'http://localhost').pathname))
  if (path !== dist && !path.startsWith(dist)) { res.writeHead(403); res.end(); return }
  let target = path
  try { if (!(await stat(target)).isFile()) target = join(dist,'index.html') } catch { target = join(dist,'index.html') }
  res.setHeader('Content-Type',types[extname(target)] ?? 'application/octet-stream'); createReadStream(target).on('error',()=>res.end()).pipe(res)
}).listen(Number(process.env.QMH_WEB_PORT ?? 5174),'127.0.0.1',()=>console.log('Local static frontend and API proxy ready'))
