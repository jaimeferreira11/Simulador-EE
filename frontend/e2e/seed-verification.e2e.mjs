import puppeteer from 'puppeteer';

const BASE = 'http://localhost:4200';
const CREDS = { email: 'moderador@simulador.py', password: 'password123' };

const results = [];
function log(status, name, detail = '') {
  const icon = status === 'PASS' ? '✅' : '❌';
  console.log(`${icon} ${status} — ${name}${detail ? ': ' + detail : ''}`);
  results.push({ status, name, detail });
}

async function idle(page) {
  await page.waitForNetworkIdle({ idleTime: 500, timeout: 8000 }).catch(() => {});
}

async function shot(page, label) {
  await idle(page);
  await page.screenshot({ path: `e2e/screenshots/seed_${label}.png`, fullPage: true });
}

async function clickSidebar(page, label) {
  for (const link of await page.$$('aside a')) {
    const text = await link.evaluate(el => el.textContent.trim());
    if (text.includes(label)) {
      await link.click();
      await new Promise(r => setTimeout(r, 1500));
      await idle(page);
      return true;
    }
  }
  return false;
}

(async () => {
  console.log('\n🌱 Seed Verification — Moderador con datos\n');

  const { mkdirSync } = await import('fs');
  mkdirSync('e2e/screenshots', { recursive: true });

  const browser = await puppeteer.launch({
    headless: false,
    defaultViewport: { width: 1440, height: 900 },
    args: ['--window-size=1440,900'],
  });
  const page = await browser.newPage();

  // 1. Login
  await page.goto(`${BASE}/login`, { waitUntil: 'networkidle2', timeout: 15000 });
  await page.type('input[formcontrolname="email"]', CREDS.email, { delay: 20 });
  await page.type('input[formcontrolname="password"]', CREDS.password, { delay: 20 });
  await page.click('button[type="submit"]');
  await page.waitForFunction(() => window.location.pathname.includes('/moderador'), { timeout: 10000 });
  await idle(page);
  log('PASS', 'Login como moderador');

  // 2. Dashboard
  await shot(page, '01_dashboard');
  const compName = await page.$eval('.font-display.text-base', el => el.textContent.trim()).catch(() => '');
  log(compName.includes('Retail Championship') ? 'PASS' : 'FAIL', 'Dashboard: competencia visible', compName);

  // Check stat cards
  const statValues = await page.$$eval('.font-display.text-2xl', els => els.map(e => e.textContent.trim()));
  log(statValues.includes('1') ? 'PASS' : 'FAIL', 'Dashboard: stats cards', statValues.join(', '));

  // 3. Competencia detalle (click the card)
  const compCard = await page.$('.sim-card');
  if (compCard) await compCard.click();
  await new Promise(r => setTimeout(r, 2000));
  await idle(page);
  await shot(page, '02_competencia_detalle');
  log(page.url().includes('/moderador/competencias/') ? 'PASS' : 'FAIL', 'Competencia detalle', page.url());

  // 4. Equipos — sidebar
  await clickSidebar(page, 'Gestión de Equipos');
  await shot(page, '03_equipos_sidebar');

  // 5. Trimestres
  await clickSidebar(page, 'Trimestres');
  await shot(page, '04_trimestres');

  // 6. Eventos
  await clickSidebar(page, 'Eventos');
  await shot(page, '05_eventos');

  // 7. Rankings
  await clickSidebar(page, 'Rankings');
  await shot(page, '06_rankings');

  // 8. Resultados
  await clickSidebar(page, 'Resultados');
  await shot(page, '07_resultados');

  // 9. Bitácora
  await clickSidebar(page, 'Bitácora');
  await shot(page, '08_bitacora');

  // 10. Exportar
  await clickSidebar(page, 'Exportar');
  await shot(page, '09_exportar');

  // 11. Verify API has the data (direct API check via fetch)
  const apiCheck = await page.evaluate(async () => {
    const token = localStorage.getItem('access_token') || sessionStorage.getItem('access_token');
    if (!token) return { error: 'no token found in storage' };
    try {
      const res = await fetch('/v1/competencias', { headers: { Authorization: `Bearer ${token}` } });
      const data = await res.json();
      return { competencias: data.total_elements, status: res.status };
    } catch (e) {
      return { error: e.message };
    }
  });
  log(apiCheck.competencias >= 1 ? 'PASS' : 'FAIL', 'API: competencias accesibles', JSON.stringify(apiCheck));

  // Summary
  console.log('\n' + '─'.repeat(50));
  const passed = results.filter(r => r.status === 'PASS').length;
  const failed = results.filter(r => r.status === 'FAIL').length;
  console.log(`\n📊 Results: ${passed} passed, ${failed} failed, ${results.length} total`);
  if (failed > 0) {
    console.log('\nFailed:');
    results.filter(r => r.status === 'FAIL').forEach(r => console.log(`   ❌ ${r.name}: ${r.detail}`));
  }
  console.log('\n📸 Screenshots: e2e/screenshots/seed_*.png\n');

  await browser.close();
  process.exit(failed > 0 ? 1 : 0);
})();
