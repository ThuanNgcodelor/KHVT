import { chromium } from '../../source/frontend/node_modules/playwright/index.mjs'
import { fileURLToPath, pathToFileURL } from 'node:url'
// Reuse the Chromium already installed for the application's Playwright tests.
// MCP and @playwright/test can require different bundled browser revisions.
const cli = fileURLToPath(new URL('./node_modules/@playwright/mcp/cli.js',import.meta.url))
const config = fileURLToPath(new URL('./browser.config.json',import.meta.url))
process.argv = [process.execPath,cli,'--config',config,'--executable-path',chromium.executablePath(),...process.argv.slice(2)]
await import(pathToFileURL(cli).href)
