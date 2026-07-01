import { Component, inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';

export interface AdvanceTrimesterDialogData {
  /** Número del próximo trimestre que se abrirá tras avanzar. */
  proximoTrimestre: number;
}

/**
 * Diálogo que aparece tras enviar las decisiones del CEO en modo demo.
 * El moderador decide si avanzar el trimestre ahora ({@code Sí, avanzar})
 * o postergar y avanzar después con el FAB ({@code No, después}).
 *
 * Sigue el estilo Material del resto de diálogos del proyecto
 * (ver {@link StrongConfirmDialogComponent}).
 */
@Component({
  selector: 'app-advance-trimester-dialog',
  standalone: true,
  imports: [MatDialogModule, MatButtonModule],
  template: `
    <div class="bg-primary text-white px-6 py-4">
      <h2 class="font-display text-xl">¿Avanzar al trimestre Q{{ data.proximoTrimestre }}?</h2>
    </div>
    <div class="px-6 py-5 font-body text-sm text-text-primary">
      <p>
        Los bots ya enviaron sus decisiones. Si avanzás ahora, el motor procesa
        Q{{ data.proximoTrimestre - 1 }} y abre Q{{ data.proximoTrimestre }} con un
        nuevo set de decisiones de bots.
      </p>
    </div>
    <div class="flex justify-end gap-3 px-6 pb-5">
      <button mat-button (click)="onCancel()" class="font-body">No, después</button>
      <button mat-raised-button color="primary" (click)="onConfirm()" class="font-body">
        Sí, avanzar
      </button>
    </div>
  `,
})
export class AdvanceTrimesterDialogComponent {
  data = inject<AdvanceTrimesterDialogData>(MAT_DIALOG_DATA);
  private dialogRef = inject(MatDialogRef<AdvanceTrimesterDialogComponent>);

  onConfirm(): void {
    this.dialogRef.close({ confirmed: true });
  }

  onCancel(): void {
    this.dialogRef.close({ confirmed: false });
  }
}
