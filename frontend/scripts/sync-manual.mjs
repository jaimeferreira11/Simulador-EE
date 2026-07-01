// Sincroniza el manual del jugador hacia el asset que sirve la sección de Ayuda.
// Se ejecuta automáticamente en `npm start` y `npm run build` (hooks prestart/prebuild).
//
// El asset `public/ayuda/guia-jugador.md` ya está versionado en el repo, así que el
// build funciona aunque la fuente no esté presente. Si existe una fuente externa
// (variable SYNC_MANUAL_SRC), se usa para refrescar el asset.
import { copyFileSync, existsSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';

const here = dirname(fileURLToPath(import.meta.url));
const dest = resolve(here, '../public/ayuda/guia-jugador.md');
const src = process.env.SYNC_MANUAL_SRC
  ? resolve(process.env.SYNC_MANUAL_SRC)
  : null;

if (src && existsSync(src)) {
  copyFileSync(src, dest);
  console.log(`[sync-manual] ${src} -> public/ayuda/guia-jugador.md`);
} else if (existsSync(dest)) {
  console.log('[sync-manual] usando public/ayuda/guia-jugador.md ya versionado');
} else {
  console.warn('[sync-manual] AVISO: no se encontró guia-jugador.md (la sección de Ayuda quedará vacía)');
}
