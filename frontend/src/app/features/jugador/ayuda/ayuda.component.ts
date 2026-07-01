import {
  Component,
  computed,
  inject,
  signal,
  ViewEncapsulation,
  OnInit,
  viewChild,
  ElementRef,
} from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import {
  LucideAngularModule,
  BookOpen,
  Library,
  Search,
  X,
  Bot,
  Send,
  ExternalLink,
} from 'lucide-angular';
import { marked } from 'marked';
import { JugadorStore } from '../../../core/stores/jugador.store';
import { AsistenteApiService } from '../../../core/services/asistente-api.service';
import { Fuente } from '../../../core/models/asistente.model';

interface TerminoGlosario {
  termino: string;
  definicion: string;
}

interface GrupoGlosario {
  titulo: string;
  terminos: TerminoGlosario[];
}

/**
 * Sección de Ayuda del jugador: Manual operativo (renderiza la guía oficial
 * docs/01_producto/Guia_Jugador.md, servida como asset en public/ayuda/) y un
 * Glosario buscable. El glosario se PARSEA de la sección "Glosario de términos"
 * de esa misma guía (fuente única): editar la guía actualiza ambos.
 */
@Component({
  selector: 'app-ayuda',
  standalone: true,
  imports: [LucideAngularModule, FormsModule],
  templateUrl: './ayuda.component.html',
  styleUrl: './ayuda.component.css',
  encapsulation: ViewEncapsulation.None,
})
export class AyudaComponent implements OnInit {
  private http = inject(HttpClient);
  private sanitizer = inject(DomSanitizer);
  private jugadorStore = inject(JugadorStore);
  private asistenteApi = inject(AsistenteApiService);
  private route = inject(ActivatedRoute);

  readonly icons = { BookOpen, Library, Search, X, Bot, Send, ExternalLink };

  tab = signal<'manual' | 'glosario' | 'asistente'>('manual');

  // Chat del asistente
  mensajes = signal<
    {
      rol: 'user' | 'bot';
      texto: string;
      fuentes?: Fuente[];
      relacionadas?: string[];
      origen?: string;
    }[]
  >([]);
  borrador = signal('');
  enviando = signal(false);
  query = signal('');

  // Referencia al contenedor scrolleable del chat (para auto-scroll al fondo).
  private chatScroll = viewChild<ElementRef<HTMLElement>>('chatScroll');

  // Código de la competencia activa; sin él, el asistente no puede consultar.
  codigoCompetencia = computed(() => this.jugadorStore.competencia()?.codigo ?? null);
  sinCompetencia = computed(() => !this.codigoCompetencia());

  manualHtml = signal<SafeHtml | null>(null);
  manualError = signal(false);
  private manualCargado = false;

  // Glosario parseado desde el manual (fuente única: docs/01_producto/Guia_Jugador.md).
  // Se llena al cargar el markdown; así un cambio en la guía se refleja sin tocar este componente.
  grupos = signal<GrupoGlosario[]>([]);

  // Glosario filtrado por la búsqueda (sobre término y definición).
  gruposFiltrados = computed<GrupoGlosario[]>(() => {
    const q = this.query().trim().toLowerCase();
    if (!q) return this.grupos();
    return this.grupos()
      .map((g) => ({
        titulo: g.titulo,
        terminos: g.terminos.filter(
          (t) => t.termino.toLowerCase().includes(q) || t.definicion.toLowerCase().includes(q),
        ),
      }))
      .filter((g) => g.terminos.length > 0);
  });

  totalResultados = computed(() =>
    this.gruposFiltrados().reduce((sum, g) => sum + g.terminos.length, 0),
  );

  ngOnInit(): void {
    const t = this.route.snapshot.queryParamMap.get('tab');
    if (t === 'asistente' || t === 'glosario' || t === 'manual') {
      this.seleccionar(t);
    }
    if (this.mensajes().length === 0) {
      this.mensajes.set([
        {
          rol: 'bot',
          texto:
            'Hola, soy tu asistente. Puedo responder dudas sobre las reglas y el uso del simulador. Probá preguntando, por ejemplo, "¿cómo se ordena el ranking?".',
          relacionadas: [
            '¿Cómo cargo mis decisiones?',
            '¿Cómo se ordena el ranking?',
            '¿Qué son los eventos?',
          ],
        },
      ]);
    }
  }

  seleccionar(tab: 'manual' | 'glosario' | 'asistente'): void {
    this.tab.set(tab);
    if (tab === 'manual') this.cargarManual();
  }

  constructor() {
    this.cargarManual();
  }

  onQuery(value: string): void {
    this.query.set(value);
  }

  limpiarBusqueda(): void {
    this.query.set('');
  }

  private cargarManual(): void {
    if (this.manualCargado) return;
    this.manualCargado = true;
    this.http.get('ayuda/guia-jugador.md', { responseType: 'text' }).subscribe({
      next: (md) => {
        const html = marked.parse(md, { async: false }) as string;
        this.manualHtml.set(this.sanitizer.bypassSecurityTrustHtml(html));
        this.grupos.set(this.parsearGlosario(md));
      },
      error: () => {
        this.manualCargado = false;
        this.manualError.set(true);
      },
    });
  }

  /** Extrae el glosario (sección "## Glosario de términos") del markdown del manual:
   *  cada "### grupo" con su tabla "| Término | Significado |". Mantiene la guía como
   *  fuente única del glosario. */
  private parsearGlosario(md: string): GrupoGlosario[] {
    const grupos: GrupoGlosario[] = [];
    let enGlosario = false;
    let grupoActual: GrupoGlosario | null = null;

    for (const linea of md.split('\n')) {
      if (linea.startsWith('## ')) {
        if (linea.includes('Glosario de términos')) {
          enGlosario = true;
          continue;
        }
        if (enGlosario) break; // siguiente sección de nivel 2 → fin del glosario
      }
      if (!enGlosario) continue;

      if (linea.startsWith('### ')) {
        grupoActual = { titulo: linea.slice(4).trim(), terminos: [] };
        grupos.push(grupoActual);
        continue;
      }

      if (grupoActual && linea.trimStart().startsWith('|')) {
        const celdas = linea.split('|').map((c) => c.trim());
        const termino = this.limpiarMd(celdas[1] ?? '');
        const definicion = this.limpiarMd(celdas[2] ?? '');
        if (!termino || termino.toLowerCase() === 'término' || /^[-\s]+$/.test(termino)) {
          continue; // encabezado o separador de la tabla
        }
        grupoActual.terminos.push({ termino, definicion });
      }
    }

    return grupos.filter((g) => g.terminos.length > 0);
  }

  /** Quita el énfasis markdown (**, *, `) para mostrar texto plano. */
  private limpiarMd(s: string): string {
    return s.replace(/\*\*/g, '').replace(/`/g, '').replace(/\*/g, '').trim();
  }

  enviar(texto?: string): void {
    const pregunta = (texto ?? this.borrador()).trim();
    if (!pregunta || this.enviando()) return;
    const codigo = this.codigoCompetencia();
    if (!codigo) return;

    this.mensajes.update((m) => [...m, { rol: 'user', texto: pregunta }]);
    this.borrador.set('');
    this.enviando.set(true);
    this.scrollAlFondo();

    this.asistenteApi.preguntar(codigo, pregunta).subscribe({
      next: (r) => {
        this.mensajes.update((m) => [
          ...m,
          {
            rol: 'bot',
            texto: r.texto,
            fuentes: r.fuentes,
            relacionadas: r.relacionadas,
            origen: r.origen,
          },
        ]);
        this.enviando.set(false);
        this.scrollAlFondo();
      },
      error: () => {
        this.mensajes.update((m) => [
          ...m,
          {
            rol: 'bot',
            texto: 'No pude responder en este momento. Revisá el Manual o intentá de nuevo.',
          },
        ]);
        this.enviando.set(false);
        this.scrollAlFondo();
      },
    });
  }

  preguntarSugerida(pregunta: string): void {
    this.enviar(pregunta);
  }

  /** Abre el Manual y hace scroll al encabezado de la sección, reintentando hasta que el
   *  markdown termine de cargar (evita el no-op en la primera carga fría del manual). */
  irAManual(ancla: string): void {
    this.seleccionar('manual');
    this.scrollAEncabezado(ancla, 20);
  }

  private scrollAEncabezado(ancla: string, intentos: number): void {
    const cont = document.querySelector('.manual-prose');
    const target = cont
      ? Array.from(cont.querySelectorAll('h1, h2, h3')).find((h) =>
          (h.textContent ?? '').toLowerCase().includes(ancla.toLowerCase()),
        )
      : null;
    if (target) {
      target.scrollIntoView({ behavior: 'smooth', block: 'start' });
      return;
    }
    if (intentos > 0) {
      setTimeout(() => this.scrollAEncabezado(ancla, intentos - 1), 100);
    }
  }

  /** Lleva el scroll del chat al último mensaje. */
  private scrollAlFondo(): void {
    setTimeout(() => {
      const el = this.chatScroll()?.nativeElement;
      if (el) el.scrollTop = el.scrollHeight;
    }, 0);
  }
}
