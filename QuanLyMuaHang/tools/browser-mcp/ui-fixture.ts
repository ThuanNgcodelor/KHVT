import { purchasing } from '../../source/frontend/tests/e2e/purchasingFixtures.ts'
// Test-only injection. The registered khvt-browser server does not load this.
export default async ({ page }: { page: import('playwright').Page }) => { await purchasing(page) }
