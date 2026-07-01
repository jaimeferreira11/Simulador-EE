import { Component, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { LucideAngularModule, FileText, Table, FileSpreadsheet, Download, LucideIconData } from 'lucide-angular';
import { CompetenciaApiService } from '../../../core/services/competencia-api.service';
import { CompetenciaStore } from '../../../core/stores/competencia.store';
import { ResultadoApiService } from '../../../core/services/resultado-api.service';
import { Observable } from 'rxjs';

interface FormatOption {
  key: string;
  icono: LucideIconData;
  titulo: string;
  descripcion: string;
  extension: string;
}

interface ContentOption {
  key: string;
  label: string;
  checked: boolean;
}

@Component({
  selector: 'app-exportar',
  standalone: true,
  imports: [LucideAngularModule],
  templateUrl: './exportar.component.html',
})
export class ExportarComponent implements OnInit {
  readonly icons = { Download };

  private route = inject(ActivatedRoute);
  private competenciaApi = inject(CompetenciaApiService);
  private competenciaStore = inject(CompetenciaStore);
  private resultadoApi = inject(ResultadoApiService);

  competenciaNombre = signal('');
  competenciaId = 0;
  downloading = signal(false);

  selectedFormat = 'pdf';

  formatos: FormatOption[] = [
    { key: 'pdf', icono: FileText, titulo: 'Reporte PDF', descripcion: 'Documento formateado con tablas y portada', extension: '.pdf' },
    { key: 'excel', icono: Table, titulo: 'Excel Data', descripcion: 'Hojas de calculo con todos los datos (.xlsx)', extension: '.xlsx' },
    { key: 'csv', icono: FileSpreadsheet, titulo: 'CSV Raw', descripcion: 'Datos crudos separados por comas', extension: '.csv' },
  ];

  contenido: ContentOption[] = [
    { key: 'ranking', label: 'Ranking general', checked: true },
    { key: 'resultados', label: 'Resultados por equipo', checked: true },
    { key: 'eventos', label: 'Historial de eventos', checked: true },
    { key: 'decisiones', label: 'Historial decisiones', checked: false },
    { key: 'bitacora', label: 'Bitacora auditoria', checked: false },
  ];

  ngOnInit(): void {
    this.competenciaId = Number(this.route.snapshot.queryParamMap.get('competencia'))
      || this.competenciaStore.competenciaActiva()?.id
      || 0;
    if (this.competenciaId) {
      this.competenciaApi.getById(this.competenciaId).subscribe({
        next: (c) => {
          this.competenciaNombre.set(c.nombre);
          this.competenciaStore.competenciaActiva.set(c);
        },
      });
    }
  }

  selectFormat(key: string): void {
    this.selectedFormat = key;
  }

  toggleContenido(item: ContentOption): void {
    item.checked = !item.checked;
  }

  get selectedSections(): string[] {
    return this.contenido.filter(c => c.checked).map(c => c.key);
  }

  get buttonLabel(): string {
    const fmt = this.formatos.find(f => f.key === this.selectedFormat);
    if (!fmt) return 'Descargar';
    if (this.selectedFormat === 'pdf') return 'Generar Reporte PDF';
    if (this.selectedFormat === 'excel') return 'Descargar Excel';
    return 'Descargar CSV';
  }

  descargar(): void {
    if (!this.competenciaId || this.downloading()) return;
    this.downloading.set(true);

    const sections = this.selectedSections;
    let obs: Observable<Blob>;
    let filename: string;

    switch (this.selectedFormat) {
      case 'pdf':
        obs = this.resultadoApi.exportPdf(this.competenciaId, sections);
        filename = `competencia_${this.competenciaId}.pdf`;
        break;
      case 'excel':
        obs = this.resultadoApi.exportExcel(this.competenciaId);
        filename = `competencia_${this.competenciaId}.xlsx`;
        break;
      case 'csv':
        obs = this.resultadoApi.exportCsv(this.competenciaId, sections);
        filename = `competencia_${this.competenciaId}.csv`;
        break;
      default:
        this.downloading.set(false);
        return;
    }

    obs.subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = filename;
        a.click();
        URL.revokeObjectURL(url);
        this.downloading.set(false);
      },
      error: () => this.downloading.set(false),
    });
  }
}
