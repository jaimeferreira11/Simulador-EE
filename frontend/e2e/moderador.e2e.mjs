import puppeteer from 'puppeteer';

const BASE = 'http://localhost:4200';
const CREDS = { email: 'moderador@simulador.py', password: 'password123' };

let browser, page;
const results = [];

function log(status, name, detail = '') {
  const icon = status === 'PASS' ? '✅' : status === 'FAIL' ? '❌' : '⚠️';
  const msg = `${icon} ${status} — ${name}${detail ? ': ' + detail : ''}`;
  console.log(msg);
  results.push({ status, name, detail });
}

async function waitAndScreenshot(label) {
  await page.waitForNetworkIdle({ idleTime: 500, timeout: 8000 }).catch(() => {});
  await page.screenshot({ path: `e2e/screenshots/${label}.png`, fullPage: true });
}

async function testLogin() {
  await page.goto(`${BASE}/login`, { waitUntil: 'networkidle2', timeout: 15000 });

  // Verify login page loaded
  const title = await page.$eval('h2', el => el.textContent.trim());
  if (title !== 'Iniciar Sesión') {
    log('FAIL', 'Login page load', `Expected "Iniciar Sesión", got "${title}"`);
    return false;
  }
  log('PASS', 'Login page load');
  await waitAndScreenshot('01_login_page');

  // Fill credentials
  await page.type('input[formcontrolname="email"]', CREDS.email, { delay: 30 });
  await page.type('input[formcontrolname="password"]', CREDS.password, { delay: 30 });
  await waitAndScreenshot('02_login_filled');

  // Submit
  await page.click('button[type="submit"]');

  // Wait for navigation to moderador dashboard or error
  try {
    await page.waitForFunction(
      () => window.location.pathname.includes('/moderador') || document.querySelector('.text-red-600'),
      { timeout: 10000 }
    );
  } catch {
    log('FAIL', 'Login submit', 'Timeout waiting for redirect or error');
    await waitAndScreenshot('02b_login_timeout');
    return false;
  }

  // Check if we got an error
  const error = await page.$('.text-red-600');
  if (error) {
    const errorText = await page.$eval('.text-red-600', el => el.textContent.trim());
    log('FAIL', 'Login auth', errorText);
    await waitAndScreenshot('02c_login_error');
    return false;
  }

  // Verify we're on moderador dashboard
  const url = page.url();
  if (!url.includes('/moderador')) {
    log('FAIL', 'Login redirect', `URL is ${url}`);
    return false;
  }
  log('PASS', 'Login redirect to /moderador');
  return true;
}

async function testDashboard() {
  await page.waitForSelector('h1', { timeout: 5000 });
  const heading = await page.$eval('h1', el => el.textContent.trim());
  if (heading === 'Mis Competencias') {
    log('PASS', 'Dashboard heading');
  } else {
    log('FAIL', 'Dashboard heading', `Got "${heading}"`);
  }
  await waitAndScreenshot('03_dashboard');

  // Check sidebar has MODERADOR badge
  const badge = await page.$eval('.font-label.text-primary', el => el.textContent.trim()).catch(() => null);
  if (badge === 'MODERADOR') {
    log('PASS', 'Sidebar role badge');
  } else {
    log('FAIL', 'Sidebar role badge', `Got "${badge}"`);
  }

  // Check stats cards are present
  const statCards = await page.$$('.sim-stat-card');
  log(statCards.length >= 3 ? 'PASS' : 'FAIL', 'Dashboard stat cards', `Found ${statCards.length}`);
}

async function testSidebarNavigation() {
  const navItems = [
    { label: 'Crear Competencia', path: '/moderador/competencias/nueva', screenshotId: '04' },
    { label: 'Gestión de Equipos', path: '/moderador/equipos', screenshotId: '05' },
    { label: 'Trimestres', path: '/moderador/trimestres', screenshotId: '06' },
    { label: 'Eventos', path: '/moderador/eventos', screenshotId: '07' },
    { label: 'Rankings', path: '/moderador/rankings', screenshotId: '08' },
    { label: 'Resultados', path: '/moderador/resultados', screenshotId: '09' },
    { label: 'Bitácora', path: '/moderador/bitacora', screenshotId: '10' },
    { label: 'Exportar', path: '/moderador/reportes', screenshotId: '11' },
  ];

  for (const item of navItems) {
    try {
      // Click sidebar link
      const links = await page.$$('aside a');
      let clicked = false;
      for (const link of links) {
        const text = await link.evaluate(el => el.textContent.trim());
        if (text.includes(item.label)) {
          await link.click();
          clicked = true;
          break;
        }
      }
      if (!clicked) {
        log('FAIL', `Nav: ${item.label}`, 'Link not found in sidebar');
        continue;
      }

      await page.waitForFunction(
        (path) => window.location.pathname.includes(path),
        { timeout: 5000 },
        item.path
      );
      await waitAndScreenshot(`${item.screenshotId}_${item.label.toLowerCase().replace(/\s+/g, '_')}`);

      // Verify page has content (h1 or main content area)
      const hasContent = await page.$('main h1, main .sim-card, main form, main table, main div');
      log(hasContent ? 'PASS' : 'FAIL', `Nav: ${item.label}`, page.url());
    } catch (err) {
      log('FAIL', `Nav: ${item.label}`, err.message);
      await waitAndScreenshot(`${item.screenshotId}_${item.label.toLowerCase().replace(/\s+/g, '_')}_error`);
    }
  }
}

async function testBackToDashboard() {
  try {
    const links = await page.$$('aside a');
    for (const link of links) {
      const text = await link.evaluate(el => el.textContent.trim());
      if (text.includes('Mis Competencias')) {
        await link.click();
        break;
      }
    }
    await page.waitForFunction(
      () => window.location.pathname === '/moderador/dashboard',
      { timeout: 5000 }
    );
    log('PASS', 'Return to dashboard');
    await waitAndScreenshot('12_back_to_dashboard');
  } catch (err) {
    log('FAIL', 'Return to dashboard', err.message);
  }
}

async function testLogout() {
  try {
    await page.click('button[title="Cerrar sesión"]');
    await page.waitForFunction(
      () => window.location.pathname.includes('/login'),
      { timeout: 5000 }
    );
    log('PASS', 'Logout redirect to login');
    await waitAndScreenshot('13_after_logout');
  } catch (err) {
    log('FAIL', 'Logout', err.message);
  }
}

// Run
(async () => {
  console.log('\n🚀 Simulador E2E — Moderador flow\n');

  // Ensure screenshots dir exists
  const { mkdirSync } = await import('fs');
  mkdirSync('e2e/screenshots', { recursive: true });

  browser = await puppeteer.launch({
    headless: false,
    defaultViewport: { width: 1440, height: 900 },
    args: ['--window-size=1440,900'],
  });
  page = await browser.newPage();

  try {
    const loggedIn = await testLogin();
    if (loggedIn) {
      await testDashboard();
      await testSidebarNavigation();
      await testBackToDashboard();
      await testLogout();
    }
  } catch (err) {
    log('FAIL', 'Unexpected error', err.message);
  }

  // Summary
  console.log('\n' + '─'.repeat(50));
  const passed = results.filter(r => r.status === 'PASS').length;
  const failed = results.filter(r => r.status === 'FAIL').length;
  console.log(`\n📊 Results: ${passed} passed, ${failed} failed, ${results.length} total\n`);

  if (failed > 0) {
    console.log('Failed tests:');
    results.filter(r => r.status === 'FAIL').forEach(r => console.log(`   ❌ ${r.name}: ${r.detail}`));
    console.log('');
  }

  console.log('📸 Screenshots saved to e2e/screenshots/\n');

  await browser.close();
  process.exit(failed > 0 ? 1 : 0);
})();
