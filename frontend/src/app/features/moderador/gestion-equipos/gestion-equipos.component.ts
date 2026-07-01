import { Component, inject, OnInit, signal, computed } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { forkJoin, Subject } from 'rxjs';
import { debounceTime, distinctUntilChanged, switchMap } from 'rxjs/operators';
import { FormsModule } from '@angular/forms';
import { HotToastService } from '@ngxpert/hot-toast';
import {
  LucideAngularModule,
  Plus,
  X,
  Send,
  UserPlus,
  Star,
  Search,
  Rocket,
  Upload,
  Users,
  Download,
  CheckCircle2,
  AlertCircle,
} from 'lucide-angular';
import { CompetenciaApiService } from '../../../core/services/competencia-api.service';
import { CompetenciaStore } from '../../../core/stores/competencia.store';
import { EquipoApiService } from '../../../core/services/equipo-api.service';
import {
  InvitacionApiService,
  Invitacion,
  ImportResultEquipo,
} from '../../../core/services/invitacion-api.service';
import { UsuarioApiService } from '../../../core/services/usuario-api.service';
import { CatalogoApiService } from '../../../core/services/catalogo-api.service';
import { TrimestreApiService } from '../../../core/services/trimestre-api.service';
import { EquipoDetalle, EquipoMiembro } from '../../../core/models/equipo.model';
import { CompetenciaDetalle } from '../../../core/models/competencia.model';
import { Usuario } from '../../../core/models/usuario.model';
import { AreaDecision } from '../../../core/models/catalogo.model';
import {
  BotDificultad,
  BotPersonalidad,
  BotTipo,
  BOT_DIFICULTAD_DESCRIPCION,
  BOT_DIFICULTAD_LABEL,
} from '../../../core/models/bot.model';
import { BotBadgeComponent } from '../../../shared/components/bot-badge/bot-badge.component';
import Swal from 'sweetalert2';

const MAX_MIEMBROS_POR_EQUIPO = 10;

interface EquipoCard {
  id: number;
  nombre: string;
  color: string;
  miembros: MiembroRow[];
  maxMiembros: number;
  tipo?: BotTipo;
  dificultad?: BotDificultad | null;
  personalidad?: BotPersonalidad | null;
}

interface MiembroRow {
  id: number;
  nombre: string;
  iniciales: string;
  color: string;
  activo: boolean;
  esCapitan: boolean;
}

@Component({
  selector: 'app-gestion-equipos',
  standalone: true,
  imports: [FormsModule, RouterLink, LucideAngularModule, BotBadgeComponent],
  templateUrl: './gestion-equipos.component.html',
})
export class GestionEquiposComponent implements OnInit {
  readonly icons = {
    Plus,
    X,
    Send,
    UserPlus,
    Star,
    Search,
    Rocket,
    Upload,
    Users,
    Download,
    CheckCircle2,
    AlertCircle,
  };

  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private competenciaApi = inject(CompetenciaApiService);
  private competenciaStore = inject(CompetenciaStore);
  private equipoApi = inject(EquipoApiService);
  private invitacionApi = inject(InvitacionApiService);
  private usuarioApi = inject(UsuarioApiService);
  private catalogoApi = inject(CatalogoApiService);
  private trimestreApi = inject(TrimestreApiService);
  private toast = inject(HotToastService);

  loading = signal(true);
  competencia = signal<CompetenciaDetalle | null>(null);
  equipos = signal<EquipoCard[]>([]);
  invitacionesPendientes = signal<(Invitacion & { equipoNombre: string })[]>([]);
  competenciaId = 0;

  // Areas catalogo
  areas = signal<AreaDecision[]>([]);

  // Competencia ya inicio: no se puede crear equipos ni agregar/eliminar miembros, pero si cambiar capitan
  readonly enCurso = computed(() => {
    const estado = this.competencia()?.estado;
    return estado === 'EN_CURSO' || estado === 'PAUSADA';
  });

  // Competencia finalizada: no se puede hacer nada
  readonly soloLectura = computed(() => {
    const estado = this.competencia()?.estado;
    return estado === 'FINALIZADA' || estado === 'ARCHIVADA';
  });

  // No se puede modificar estructura (crear equipos, agregar/eliminar miembros)
  readonly noEditarEstructura = computed(() => this.enCurso() || this.soloLectura());

  readonly puedeCrearEquipo = computed(() => {
    const comp = this.competencia();
    if (!comp) return false;
    if (this.noEditarEstructura()) return false;
    return this.equipos().length < comp.num_equipos_max;
  });

  readonly canIniciar = computed(() => {
    const estado = this.competencia()?.estado;
    return estado === 'BORRADOR' || estado === 'ABIERTA_INSCRIPCION';
  });

  iniciandoCompetencia = signal(false);

  // --- Modal crear equipo ---
  showCrear = signal(false);
  crearNombre = '';
  crearColor = '#006B3F';
  crearSaving = signal(false);
  tipoEquipo = signal<BotTipo>('HUMANO');
  dificultadBot = signal<BotDificultad | null>(null);

  readonly DIFICULTAD_LABEL = BOT_DIFICULTAD_LABEL;
  readonly DIFICULTAD_DESC = BOT_DIFICULTAD_DESCRIPCION;
  readonly DIFICULTADES: BotDificultad[] = ['FACIL', 'MEDIO', 'DIFICIL', 'EXPERTO'];

  readonly coloresDisponibles = [
    '#006B3F',
    '#1E40AF',
    '#7C3AED',
    '#DC2626',
    '#F47920',
    '#D97706',
    '#059669',
    '#0891B2',
    '#BE185D',
    '#4F46E5',
    '#65A30D',
    '#475569',
  ];

  // --- Modal agregar miembro ---
  showAgregar = signal(false);
  agregarEquipoId = 0;
  agregarEquipoNombre = '';
  agregarEmail = '';
  agregarNombre = '';
  agregarAreaId: number | null = null;
  agregarEsCapitan = false;
  agregarSaving = signal(false);

  // Email search
  emailSearch$ = new Subject<string>();
  buscandoEmail = signal(false);
  usuarioEncontrado = signal<Usuario | null>(null);
  usuarioNoExiste = signal(false);

  // --- Modal carga masiva (CSV import) ---
  showCargaMasiva = signal(false);
  cargaEquipoId = 0;
  cargaEquipoNombre = '';
  cargaFile = signal<File | null>(null);
  cargaSubiendo = signal(false);
  cargaError = signal<string | null>(null);
  cargaResultado = signal<ImportResultEquipo | null>(null);

  ngOnInit(): void {
    this.competenciaId =
      Number(this.route.snapshot.queryParamMap.get('competencia')) ||
      this.competenciaStore.competenciaActiva()?.id ||
      0;
    if (!this.competenciaId) {
      this.loading.set(false);
      return;
    }
    this.loadData();
    this.loadAreas();
    this.setupEmailSearch();
  }

  private loadAreas(): void {
    this.catalogoApi.getAreas().subscribe({
      next: (areas) => this.areas.set(areas),
    });
  }

  private setupEmailSearch(): void {
    this.emailSearch$
      .pipe(
        debounceTime(400),
        distinctUntilChanged(),
        switchMap((email) => {
          if (!email || !email.includes('@')) {
            this.usuarioEncontrado.set(null);
            this.usuarioNoExiste.set(false);
            this.buscandoEmail.set(false);
            return [];
          }
          this.buscandoEmail.set(true);
          return this.usuarioApi.search(email);
        }),
      )
      .subscribe({
        next: (result) => {
          this.buscandoEmail.set(false);
          const exactMatch = result.content.find(
            (u) => u.email.toLowerCase() === this.agregarEmail.trim().toLowerCase(),
          );
          if (exactMatch) {
            this.usuarioEncontrado.set(exactMatch);
            this.agregarNombre = exactMatch.nombre_completo;
            this.usuarioNoExiste.set(false);
          } else {
            this.usuarioEncontrado.set(null);
            this.usuarioNoExiste.set(true);
          }
        },
      });
  }

  onEmailInput(email: string): void {
    this.agregarEmail = email;
    this.usuarioEncontrado.set(null);
    this.usuarioNoExiste.set(false);
    this.agregarNombre = '';
    this.emailSearch$.next(email.trim().toLowerCase());
  }

  private loadData(): void {
    this.loading.set(true);
    this.competenciaApi.getById(this.competenciaId).subscribe({
      next: (comp) => {
        this.competencia.set(comp);
        this.competenciaStore.competenciaActiva.set(comp);

        if (!comp.equipos.length) {
          this.equipos.set([]);
          this.invitacionesPendientes.set([]);
          this.loading.set(false);
          return;
        }

        // Load details for each equipo + their invitations
        const equipoDetails$ = comp.equipos.map((e) => this.equipoApi.getById(e.id));
        const invitaciones$ = comp.equipos.map((e) => this.invitacionApi.listByEquipo(e.id));

        forkJoin([forkJoin(equipoDetails$), forkJoin(invitaciones$)]).subscribe({
          next: ([detalles, invsPorEquipo]) => {
            this.equipos.set(detalles.map((d) => this.toCard(d)));

            const pendientes: (Invitacion & { equipoNombre: string })[] = [];
            invsPorEquipo.forEach((invs, idx) => {
              const eqNombre = detalles[idx].nombre_empresa;
              invs
                .filter((inv) => inv.estado === 'PENDIENTE')
                .forEach((inv) => pendientes.push({ ...inv, equipoNombre: eqNombre }));
            });
            this.invitacionesPendientes.set(pendientes);
            this.loading.set(false);
          },
          error: () => this.loading.set(false),
        });
      },
      error: () => this.loading.set(false),
    });
  }

  private toCard(d: EquipoDetalle): EquipoCard {
    return {
      id: d.id,
      nombre: d.nombre_empresa,
      color: d.codigo_color || '#006B3F',
      maxMiembros: MAX_MIEMBROS_POR_EQUIPO,
      tipo: d.tipo,
      dificultad: d.dificultad,
      personalidad: d.personalidad,
      miembros: d.miembros
        .map((m) => this.toMiembroRow(m, d.codigo_color))
        .sort((a, b) => (a.esCapitan === b.esCapitan ? 0 : a.esCapitan ? -1 : 1)),
    };
  }

  private toMiembroRow(m: EquipoMiembro, color: string): MiembroRow {
    const nombre = m.usuario.nombre_completo;
    const partes = nombre.split(' ');
    const iniciales =
      partes.length >= 2
        ? (partes[0][0] + partes[partes.length - 1][0]).toUpperCase()
        : nombre.substring(0, 2).toUpperCase();
    return {
      id: m.id,
      nombre,
      iniciales,
      color: color || '#006B3F',
      activo: m.usuario.activo,
      esCapitan: m.es_capitan,
    };
  }

  // --- Crear equipo ---
  abrirCrear(): void {
    this.crearNombre = '';
    this.crearColor = '#006B3F';
    this.tipoEquipo.set('HUMANO');
    this.dificultadBot.set(null);
    this.showCrear.set(true);
  }

  formCrearValido(): boolean {
    if (!this.crearNombre.trim()) return false;
    if (this.tipoEquipo() === 'BOT' && !this.dificultadBot()) return false;
    return true;
  }

  crearEquipo(): void {
    if (!this.formCrearValido()) return;
    this.crearSaving.set(true);
    const tipo = this.tipoEquipo();
    this.equipoApi
      .create(this.competenciaId, {
        nombre_empresa: this.crearNombre.trim(),
        codigo_color: this.crearColor,
        tipo,
        dificultad: tipo === 'BOT' ? this.dificultadBot() : null,
      })
      .subscribe({
        next: () => {
          this.crearSaving.set(false);
          this.showCrear.set(false);
          this.loadData();
        },
        error: (err) => {
          this.crearSaving.set(false);
          Swal.fire({
            title: 'Error al crear equipo',
            text: err.error?.detail ?? 'Ocurrio un error inesperado',
            icon: 'error',
            confirmButtonText: 'Entendido',
            confirmButtonColor: '#006B3F',
          });
        },
      });
  }

  // --- Agregar miembro ---
  abrirAgregar(equipo: EquipoCard): void {
    this.agregarEquipoId = equipo.id;
    this.agregarEquipoNombre = equipo.nombre;
    this.agregarEmail = '';
    this.agregarNombre = '';
    this.agregarAreaId = null;
    this.agregarEsCapitan = false;
    this.usuarioEncontrado.set(null);
    this.usuarioNoExiste.set(false);
    this.buscandoEmail.set(false);
    this.showAgregar.set(true);
  }

  formAgregarValido(): boolean {
    return !!this.agregarEmail.trim() && !!this.agregarNombre.trim();
  }

  agregarMiembro(): void {
    if (!this.formAgregarValido()) return;
    this.agregarSaving.set(true);

    const usuario = this.usuarioEncontrado();

    if (usuario) {
      // User exists — add directly as member
      this.equipoApi
        .addMiembro(this.agregarEquipoId, {
          usuario_id: usuario.id,
          es_capitan: this.agregarEsCapitan,
          area_id: this.agregarAreaId,
        })
        .subscribe({
          next: () => {
            this.agregarSaving.set(false);
            this.showAgregar.set(false);
            this.loadData();
          },
          error: (err) => {
            this.agregarSaving.set(false);
            Swal.fire({
              title: 'Error al agregar',
              text: err.error?.detail ?? 'Ocurrio un error inesperado',
              icon: 'error',
              confirmButtonText: 'Entendido',
              confirmButtonColor: '#006B3F',
            });
          },
        });
    } else {
      // User doesn't exist — send invitation
      this.invitacionApi
        .invitar(this.agregarEquipoId, {
          email: this.agregarEmail.trim().toLowerCase(),
          nombreCompleto: this.agregarNombre.trim(),
          areaId: this.agregarAreaId,
          esCapitan: this.agregarEsCapitan,
        })
        .subscribe({
          next: () => {
            this.agregarSaving.set(false);
            this.showAgregar.set(false);
            this.loadData();
          },
          error: (err) => {
            this.agregarSaving.set(false);
            Swal.fire({
              title: 'Error al invitar',
              text: err.error?.detail ?? 'Ocurrio un error inesperado',
              icon: 'error',
              confirmButtonText: 'Entendido',
              confirmButtonColor: '#006B3F',
            });
          },
        });
    }
  }

  // --- Carga masiva (CSV import) ---
  abrirCargaMasiva(equipo: EquipoCard): void {
    this.cargaEquipoId = equipo.id;
    this.cargaEquipoNombre = equipo.nombre;
    this.cargaFile.set(null);
    this.cargaError.set(null);
    this.cargaResultado.set(null);
    this.cargaSubiendo.set(false);
    this.showCargaMasiva.set(true);
  }

  onCargaFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;
    this.cargaError.set(null);
    this.cargaResultado.set(null);
    this.cargaFile.set(file);
  }

  descargarPlantillaCsv(): void {
    const contenido = 'email,nombre_completo\n';
    const blob = new Blob([contenido], { type: 'text/csv;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'plantilla_integrantes.csv';
    a.click();
    URL.revokeObjectURL(url);
  }

  cargarCsv(): void {
    const file = this.cargaFile();
    if (!file || this.cargaSubiendo()) return;
    this.cargaSubiendo.set(true);
    this.cargaError.set(null);
    this.invitacionApi.importMiembrosCsv(this.cargaEquipoId, file).subscribe({
      next: (resultado) => {
        this.cargaSubiendo.set(false);
        this.cargaResultado.set(resultado);
        if (resultado.invitados.length > 0) {
          this.toast.success(
            `${resultado.invitados.length} invitado(s) a ${this.cargaEquipoNombre}`,
            {
              style: { background: '#065f46', color: '#fff' },
              iconTheme: { primary: '#34d399', secondary: '#fff' },
            },
          );
        }
      },
      error: (err) => {
        this.cargaSubiendo.set(false);
        this.cargaError.set(err.error?.detail ?? 'No se pudo procesar el archivo. Intenta nuevamente.');
      },
    });
  }

  cerrarCargaMasiva(): void {
    const huboResultado = !!this.cargaResultado();
    this.showCargaMasiva.set(false);
    this.cargaFile.set(null);
    this.cargaError.set(null);
    this.cargaResultado.set(null);
    // Si hubo respuesta del backend, refrescar para reflejar las invitaciones pendientes nuevas.
    if (huboResultado) {
      this.loadData();
    }
  }

  // --- Acciones invitaciones ---
  reenviarInvitacion(inv: Invitacion & { equipoNombre: string }): void {
    this.invitacionApi
      .reenviar(inv.equipoId, {
        email: inv.email,
        nombreCompleto: inv.nombreCompleto,
      })
      .subscribe({
        next: () => this.loadData(),
      });
  }

  cancelarInvitacion(inv: Invitacion & { equipoNombre: string }): void {
    Swal.fire({
      title: 'Cancelar invitacion',
      text: `Cancelar la invitacion de ${inv.nombreCompleto} (${inv.email})?`,
      icon: 'warning',
      showCancelButton: true,
      confirmButtonText: 'Si, cancelar',
      cancelButtonText: 'No',
      confirmButtonColor: '#DC2626',
    }).then((result) => {
      if (result.isConfirmed) {
        this.invitacionApi.cancelar(inv.id).subscribe({
          next: () => this.loadData(),
        });
      }
    });
  }

  // --- Eliminar miembro ---
  eliminarMiembro(equipo: EquipoCard, miembro: MiembroRow): void {
    Swal.fire({
      title: 'Eliminar miembro',
      text: `Eliminar a ${miembro.nombre} del equipo ${equipo.nombre}?`,
      icon: 'warning',
      showCancelButton: true,
      confirmButtonText: 'Si, eliminar',
      cancelButtonText: 'No',
      confirmButtonColor: '#DC2626',
    }).then((result) => {
      if (result.isConfirmed) {
        this.equipoApi.removeMiembro(equipo.id, miembro.id).subscribe({
          next: () => this.loadData(),
        });
      }
    });
  }

  // --- Asignar capitan ---
  asignarCapitan(equipo: EquipoCard, miembro: MiembroRow): void {
    this.equipoApi.setCapitan(equipo.id, miembro.id).subscribe({
      next: () => {
        this.toast.success(`${miembro.nombre} es el nuevo capitan de ${equipo.nombre}`, {
          style: { background: '#065f46', color: '#fff' },
          iconTheme: { primary: '#34d399', secondary: '#fff' },
        });
        this.loadData();
      },
    });
  }

  // --- Iniciar competencia ---
  iniciarCompetencia(): void {
    const comp = this.competencia();
    if (!comp) return;

    const equipos = this.equipos();
    if (equipos.length < 2) {
      Swal.fire({
        title: 'No se puede iniciar',
        text: `Se necesitan al menos 2 equipos. Actualmente hay ${equipos.length}.`,
        icon: 'warning',
        confirmButtonText: 'Entendido',
        confirmButtonColor: '#006B3F',
      });
      return;
    }

    const sinMiembros = equipos.filter((e) => !e.miembros.length);
    if (sinMiembros.length) {
      const nombres = sinMiembros.map((e) => e.nombre).join(', ');
      Swal.fire({
        title: 'Equipos sin integrantes',
        text: `Los siguientes equipos no tienen miembros: ${nombres}`,
        icon: 'warning',
        confirmButtonText: 'Entendido',
        confirmButtonColor: '#006B3F',
      });
      return;
    }

    Swal.fire({
      title: 'Iniciar Competencia',
      html: `<p style="font-size:14px">Se crearan los ${comp.num_trimestres} trimestres y la competencia pasara a <strong>EN CURSO</strong>.</p>
             <p style="font-size:13px;color:#666;margin-top:8px">Los equipos sin capitan tendran uno asignado automaticamente.</p>`,
      icon: 'question',
      showCancelButton: true,
      confirmButtonText: 'Iniciar',
      cancelButtonText: 'Cancelar',
      confirmButtonColor: '#006B3F',
    }).then((result) => {
      if (result.isConfirmed) {
        this.iniciandoCompetencia.set(true);
        this.competenciaApi.iniciar(comp.id).subscribe({
          next: () => {
            this.iniciandoCompetencia.set(false);
            Swal.fire({
              title: 'Competencia iniciada',
              html: `<p style="font-size:14px">La competencia ya se encuentra <strong>EN CURSO</strong>.</p>
                     <p style="font-size:14px;margin-top:12px">¿Deseas abrir el <strong>Trimestre Q1</strong> ahora?</p>
                     <p style="font-size:13px;color:#666;margin-top:8px">Los equipos podran ingresar y enviar sus decisiones inmediatamente.</p>`,
              icon: 'success',
              showCancelButton: true,
              confirmButtonText: 'Abrir Q1',
              cancelButtonText: 'Mas tarde',
              confirmButtonColor: '#006B3F',
            }).then((abrirResult) => {
              if (abrirResult.isConfirmed) {
                this.competenciaApi.getById(comp.id).subscribe({
                  next: (updated) => {
                    const q1 = updated.trimestres.find(t => t.numero === 1 && t.estado === 'PENDIENTE');
                    if (q1) {
                      this.iniciandoCompetencia.set(true);
                      this.trimestreApi.abrir(q1.id).subscribe({
                        next: () => {
                          this.iniciandoCompetencia.set(false);
                          this.toast.success('Trimestre Q1 abierto para decisiones', {
                            style: { background: '#065f46', color: '#fff' },
                            iconTheme: { primary: '#34d399', secondary: '#fff' },
                          });
                          this.router.navigate(['/moderador/competencia'], {
                            queryParams: { competencia: comp.id },
                          });
                        },
                        error: () => {
                          this.iniciandoCompetencia.set(false);
                          this.router.navigate(['/moderador/competencia'], {
                            queryParams: { competencia: comp.id },
                          });
                        },
                      });
                    } else {
                      this.router.navigate(['/moderador/competencia'], {
                        queryParams: { competencia: comp.id },
                      });
                    }
                  },
                  error: () => {
                    this.router.navigate(['/moderador/competencia'], {
                      queryParams: { competencia: comp.id },
                    });
                  },
                });
              } else {
                this.router.navigate(['/moderador/competencia'], {
                  queryParams: { competencia: comp.id },
                });
              }
            });
          },
          error: (err) => {
            this.iniciandoCompetencia.set(false);
            Swal.fire({
              title: 'Error al iniciar',
              text: err.error?.detail ?? 'Ocurrio un error inesperado',
              icon: 'error',
              confirmButtonText: 'Entendido',
              confirmButtonColor: '#006B3F',
            });
          },
        });
      }
    });
  }
}
