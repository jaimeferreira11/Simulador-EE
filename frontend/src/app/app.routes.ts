import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';
import { rolGuard } from './core/guards/rol.guard';
import { demoCeoModeGuard } from './core/guards/demo-ceo-mode.guard';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () =>
      import('./features/auth/login/login.component').then((m) => m.LoginComponent),
  },
  {
    path: 'registro',
    loadComponent: () =>
      import('./features/auth/registro/registro.component').then((m) => m.RegistroComponent),
  },
  {
    path: 'recuperar-password',
    loadComponent: () =>
      import('./features/auth/recuperar-password/recuperar-password.component').then(
        (m) => m.RecuperarPasswordComponent,
      ),
  },

  /* ── Admin Plataforma ── */
  {
    path: 'admin',
    loadComponent: () =>
      import('./layout/admin-layout/admin-layout.component').then((m) => m.AdminLayoutComponent),
    canActivate: [authGuard, rolGuard('ADMIN_PLATAFORMA')],
    children: [
      {
        path: 'usuarios',
        loadComponent: () =>
          import('./features/admin/usuarios/usuarios.component').then(
            (m) => m.UsuariosAdminComponent,
          ),
      },
      {
        path: 'rubros',
        loadComponent: () =>
          import('./features/admin/rubros/rubros.component').then((m) => m.RubrosAdminComponent),
      },
      {
        path: 'parametros-macro',
        loadComponent: () =>
          import('./features/admin/parametros-macro/parametros-macro.component').then(
            (m) => m.ParametrosMacroAdminComponent,
          ),
      },
      {
        path: 'parametros-rubro',
        loadComponent: () =>
          import('./features/admin/parametros-rubro/parametros-rubro.component').then(
            (m) => m.ParametrosRubroAdminComponent,
          ),
      },
      {
        path: 'eventos',
        loadComponent: () =>
          import('./features/admin/eventos/eventos.component').then((m) => m.EventosAdminComponent),
      },
      {
        path: 'entidades',
        loadComponent: () =>
          import('./features/admin/entidades/entidades.component').then(
            (m) => m.EntidadesAdminComponent,
          ),
      },
      { path: '', redirectTo: 'usuarios', pathMatch: 'full' },
    ],
  },

  /* ── Moderador ── */
  {
    path: 'moderador',
    loadComponent: () =>
      import('./layout/moderador-layout/moderador-layout.component').then(
        (m) => m.ModeradorLayoutComponent,
      ),
    canActivate: [authGuard, rolGuard('MODERADOR', 'ADMIN_PLATAFORMA')],
    children: [
      {
        path: 'dashboard',
        loadComponent: () =>
          import('./features/moderador/dashboard/dashboard.component').then(
            (m) => m.ModeradorDashboardComponent,
          ),
      },
      {
        path: 'competencia',
        loadComponent: () =>
          import('./features/moderador/dashboard-competencia/dashboard-competencia.component').then(
            (m) => m.DashboardCompetenciaModComponent,
          ),
      },
      {
        path: 'competencias/nueva',
        loadComponent: () =>
          import('./features/moderador/crear-competencia/crear-competencia.component').then(
            (m) => m.CrearCompetenciaComponent,
          ),
      },
      {
        path: 'competencias/:id',
        loadComponent: () =>
          import('./features/moderador/gestionar-competencia/gestionar-competencia.component').then(
            (m) => m.GestionarCompetenciaComponent,
          ),
      },
      {
        path: 'usuarios',
        loadComponent: () =>
          import('./features/moderador/usuarios/usuarios.component').then(
            (m) => m.UsuariosModComponent,
          ),
      },
      {
        path: 'equipos',
        loadComponent: () =>
          import('./features/moderador/gestion-equipos/gestion-equipos.component').then(
            (m) => m.GestionEquiposComponent,
          ),
      },
      {
        path: 'equipos/:id',
        loadComponent: () =>
          import('./features/moderador/detalle-equipo/detalle-equipo.component').then(
            (m) => m.DetalleEquipoModComponent,
          ),
      },
      {
        path: 'trimestres',
        loadComponent: () =>
          import('./features/moderador/control-trimestre/control-trimestre.component').then(
            (m) => m.ControlTrimestreComponent,
          ),
      },
      {
        path: 'trimestres/:id',
        loadComponent: () =>
          import('./features/moderador/detalle-trimestre/detalle-trimestre.component').then(
            (m) => m.DetalleTrimestreModComponent,
          ),
      },
      {
        path: 'decisiones-equipos',
        loadComponent: () =>
          import('./features/moderador/decisiones-equipos/decisiones-equipos.component').then(
            (m) => m.DecisionesEquiposComponent,
          ),
      },
      {
        path: 'decisiones-equipos/:equipoId',
        loadComponent: () =>
          import('./features/moderador/detalle-decisiones/detalle-decisiones.component').then(
            (m) => m.DetalleDecisionesComponent,
          ),
      },
      {
        path: 'eventos',
        loadComponent: () =>
          import('./features/moderador/eventos/eventos.component').then(
            (m) => m.EventosModComponent,
          ),
      },
      {
        path: 'rankings',
        loadComponent: () =>
          import('./features/moderador/ranking-evolucion/ranking-evolucion.component').then(
            (m) => m.RankingEvolucionComponent,
          ),
      },
      {
        path: 'resultados',
        loadComponent: () =>
          import('./features/moderador/resultados-equipo/resultados-equipo.component').then(
            (m) => m.ResultadosEquipoModComponent,
          ),
      },
      {
        path: 'bitacora',
        loadComponent: () =>
          import('./features/moderador/bitacora/bitacora.component').then(
            (m) => m.BitacoraComponent,
          ),
      },
      {
        path: 'reportes',
        loadComponent: () =>
          import('./features/moderador/exportar/exportar.component').then(
            (m) => m.ExportarComponent,
          ),
      },
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
    ],
  },

  /* ── Jugador: Selector (no layout) ── */
  {
    path: 'jugador/seleccionar-competencia',
    loadComponent: () =>
      import('./features/jugador/seleccionar-competencia/seleccionar-competencia.component').then(
        (m) => m.SeleccionarCompetenciaComponent,
      ),
    canActivate: [authGuard, rolGuard('JUGADOR')],
  },

  /* ── Jugador ── */
  {
    path: 'jugador',
    loadComponent: () =>
      import('./layout/jugador-layout/jugador-layout.component').then(
        (m) => m.JugadorLayoutComponent,
      ),
    canActivate: [authGuard, demoCeoModeGuard],
    children: [
      {
        path: 'perfil',
        loadComponent: () =>
          import('./features/jugador/perfil/perfil.component').then((m) => m.PerfilComponent),
      },
      {
        path: 'equipo',
        loadComponent: () =>
          import('./features/jugador/equipo/equipo.component').then((m) => m.EquipoComponent),
      },
      {
        path: 'competencia',
        loadComponent: () =>
          import('./features/jugador/competencia/competencia.component').then(
            (m) => m.CompetenciaJugadorComponent,
          ),
      },
      {
        path: 'decisiones',
        loadComponent: () =>
          import('./features/jugador/decisiones/decisiones.component').then(
            (m) => m.DecisionesComponent,
          ),
      },
      {
        path: 'resultados',
        loadComponent: () =>
          import('./features/jugador/resultados/resultados.component').then(
            (m) => m.ResultadosComponent,
          ),
      },
      {
        path: 'chat',
        loadComponent: () =>
          import('./features/jugador/chat/chat.component').then((m) => m.ChatComponent),
      },
      {
        path: 'rankings',
        loadComponent: () =>
          import('./features/jugador/rankings/rankings.component').then((m) => m.RankingsComponent),
      },
      {
        path: 'noticias',
        loadComponent: () =>
          import('./features/jugador/noticias/noticias.component').then((m) => m.NoticiasComponent),
      },
      {
        path: 'mis-competencias',
        loadComponent: () =>
          import('./features/jugador/mis-competencias/mis-competencias.component').then(
            (m) => m.MisCompetenciasComponent,
          ),
      },
      {
        path: 'historial-decisiones',
        loadComponent: () =>
          import('./features/jugador/historial-decisiones/historial-decisiones.component').then(
            (m) => m.HistorialDecisionesComponent,
          ),
      },
      {
        path: 'ayuda',
        loadComponent: () =>
          import('./features/jugador/ayuda/ayuda.component').then((m) => m.AyudaComponent),
      },
      { path: '', redirectTo: 'competencia', pathMatch: 'full' },
    ],
  },

  { path: '', redirectTo: 'login', pathMatch: 'full' },
  { path: '**', redirectTo: 'login' },
];
