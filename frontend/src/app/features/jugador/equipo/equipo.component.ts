import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { FormsModule } from '@angular/forms';
import { HotToastService } from '@ngxpert/hot-toast';
import { LucideAngularModule, Check, Pencil, Trophy, X } from 'lucide-angular';
import { AreaDecision } from '../../../core/models/catalogo.model';
import { RankingItem } from '../../../core/models/resultado.model';
import { GuaraniCortoPipe } from '../../../core/pipes/guarani-corto.pipe';
import { CatalogoApiService } from '../../../core/services/catalogo-api.service';
import { EquipoApiService } from '../../../core/services/equipo-api.service';
import { ResultadoApiService } from '../../../core/services/resultado-api.service';
import { JugadorStore } from '../../../core/stores/jugador.store';
import { EstadoChipComponent } from '../../../shared/components/estado-chip/estado-chip.component';

@Component({
  selector: 'app-equipo',
  standalone: true,
  imports: [EstadoChipComponent, LucideAngularModule, GuaraniCortoPipe, FormsModule],
  templateUrl: './equipo.component.html',
})
export class EquipoComponent implements OnInit {
  readonly icons = { Trophy, Pencil, Check, X };

  private jugadorStore = inject(JugadorStore);
  private resultadoApi = inject(ResultadoApiService);
  private equipoApi = inject(EquipoApiService);
  private catalogoApi = inject(CatalogoApiService);
  private toast = inject(HotToastService);

  loading = this.jugadorStore.loading;
  equipo = this.jugadorStore.equipo;
  competencia = this.jugadorStore.competencia;
  trimestreActual = this.jugadorStore.trimestreActual;
  esCapitan = this.jugadorStore.esCapitan;

  miRanking = signal<RankingItem | null>(null);
  areas = signal<AreaDecision[]>([]);
  savingArea = signal<number | null>(null);
  editingArea = signal<number | null>(null); // miembro id being edited
  editingAreaValue = signal<string>('');      // selected value while editing

  iniciales = computed(() => {
    const nombre = this.equipo()?.nombre_empresa ?? '';
    return nombre
      .split(' ')
      .filter(Boolean)
      .map((p) => p[0])
      .join('')
      .substring(0, 2)
      .toUpperCase();
  });

  miembrosDisplay = computed(() => {
    const eq = this.equipo();
    if (!eq) return [];
    const areasLoaded = this.areas();
    return eq.miembros
      .map((m) => ({
        id: m.id,
        nombre: m.usuario.nombre_completo,
        email: m.usuario.email,
        iniciales: m.usuario.nombre_completo
          .split(' ')
          .filter(Boolean)
          .map((p) => p[0])
          .join('')
          .substring(0, 2)
          .toUpperCase(),
        rol: m.es_capitan ? 'Capitán (CEO)' : 'Miembro',
        esCapitan: m.es_capitan,
        areaId: m.area_id,
        areaNombre: areasLoaded.find(a => a.id === m.area_id)?.nombre ?? null,
      }))
      .sort((a, b) => {
        // 1. Captain first
        if (a.esCapitan !== b.esCapitan) return a.esCapitan ? -1 : 1;
        // 2. Members with area before members without
        const aHasArea = a.areaId != null ? 0 : 1;
        const bHasArea = b.areaId != null ? 0 : 1;
        if (aHasArea !== bHasArea) return aHasArea - bHasArea;
        // 3. Alphabetical by name
        return a.nombre.localeCompare(b.nombre, 'es');
      });
  });

  totalEquipos = computed(() => this.competencia()?.equipos.length ?? 0);

  ngOnInit(): void {
    this.initData();
  }

  private async initData(): Promise<void> {
    const areas = await firstValueFrom(this.catalogoApi.getAreas());
    this.areas.set(areas);

    await this.jugadorStore.init();

    const comp = this.competencia();
    const eq = this.equipo();
    if (!comp || !eq) return;

    this.resultadoApi.getRanking(comp.id).subscribe({
      next: (items) => {
        const mine = items.find((r) => r.equipo_id === eq.id);
        if (mine) this.miRanking.set(mine);
      },
    });
  }

  startEditArea(miembroId: number, currentAreaId: number | null): void {
    this.editingArea.set(miembroId);
    this.editingAreaValue.set(currentAreaId != null ? String(currentAreaId) : '');
  }

  cancelEditArea(): void {
    this.editingArea.set(null);
    this.editingAreaValue.set('');
  }

  onEditAreaChange(value: string): void {
    this.editingAreaValue.set(value);
  }

  confirmArea(miembroId: number): void {
    const value = this.editingAreaValue();
    const areaId = value === '' ? null : Number(value);
    const eq = this.equipo();
    if (!eq) return;

    this.savingArea.set(miembroId);
    this.equipoApi.updateMiembroArea(eq.id, miembroId, areaId).subscribe({
      next: () => {
        this.savingArea.set(null);
        this.editingArea.set(null);
        this.editingAreaValue.set('');
        this.toast.success('Area actualizada', {
          style: { background: '#065f46', color: '#fff' },
          iconTheme: { primary: '#34d399', secondary: '#fff' },
        });
        const updated = {
          ...eq,
          miembros: eq.miembros.map(m =>
            m.id === miembroId ? { ...m, area_id: areaId } : m
          ),
        };
        this.jugadorStore.equipo.set(updated);
      },
      error: (err) => {
        this.savingArea.set(null);
        this.toast.error(err.error?.detail ?? 'Error al actualizar area');
      },
    });
  }
}
