import { Component, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { DatePipe } from '@angular/common';
import { LucideAngularModule, Calendar, Check, Lock, Zap, Users, Rocket, PenLine, Plus, LucideIconData, Activity } from 'lucide-angular';
import { CompetenciaApiService } from '../../../core/services/competencia-api.service';
import { CompetenciaStore } from '../../../core/stores/competencia.store';
import { AuditoriaApiService } from '../../../core/services/auditoria-api.service';
import { AuditoriaEvento } from '../../../core/models/auditoria.model';

interface BitacoraRow {
  accion: string;
  fecha: string;
  icono: LucideIconData;
}

const ICON_MAP: Record<string, LucideIconData> = {
  COMPETENCIA_CREADA: Plus,
  INSCRIPCION_ABIERTA: PenLine,
  COMPETENCIA_INICIADA: Rocket,
  TRIMESTRE_ABIERTO: Calendar,
  TRIMESTRE_CERRADO: Lock,
  TRIMESTRE_PROCESADO: Check,
  EVENTO_DISPARADO: Zap,
  EQUIPO_CREADO: Users,
  MIEMBRO_AGREGADO: Users,
  DECISION_ENVIADA: Check,
};

@Component({
  selector: 'app-bitacora',
  standalone: true,
  imports: [DatePipe, LucideAngularModule],
  templateUrl: './bitacora.component.html',
})
export class BitacoraComponent implements OnInit {
  private defaultIcon = Activity;

  private route = inject(ActivatedRoute);
  private competenciaApi = inject(CompetenciaApiService);
  private competenciaStore = inject(CompetenciaStore);
  private auditoriaApi = inject(AuditoriaApiService);

  loading = signal(true);
  competenciaNombre = signal('');
  entradas = signal<BitacoraRow[]>([]);

  ngOnInit(): void {
    const competenciaId = Number(this.route.snapshot.queryParamMap.get('competencia'))
      || this.competenciaStore.competenciaActiva()?.id;
    if (!competenciaId) {
      this.loading.set(false);
      return;
    }
    this.competenciaApi.getById(competenciaId).subscribe({
      next: (c) => {
        this.competenciaNombre.set(c.nombre);
        this.competenciaStore.competenciaActiva.set(c);
      },
    });
    this.auditoriaApi.list(competenciaId).subscribe({
      next: (page) => {
        this.entradas.set(page.content.map(e => this.toRow(e)));
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  private toRow(e: AuditoriaEvento): BitacoraRow {
    return {
      accion: e.descripcion,
      fecha: e.ocurrido_at,
      icono: ICON_MAP[e.tipo_accion] ?? this.defaultIcon,
    };
  }
}
