import { Component, inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef, MatDialogModule } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { FormsModule } from '@angular/forms';

export interface StrongConfirmDialogData {
  title: string;
  description: string;
  bulletPoints?: string[];
  confirmWord: string;
  confirmLabel: string;
  motivoLabel?: string;
  motivoRequired?: boolean;
}

@Component({
  selector: 'app-strong-confirm-dialog',
  standalone: true,
  imports: [MatDialogModule, MatButtonModule, FormsModule],
  template: `
    <div class="bg-red-600 text-white px-6 py-4">
      <h2 class="font-display text-xl">{{ data.title }}</h2>
    </div>
    <div class="px-6 py-5">
      <p class="font-body font-semibold mb-2 text-text-primary">{{ data.description }}</p>
      @if (data.bulletPoints?.length) {
        <ul class="list-disc pl-5 mb-4 text-sm text-text-secondary font-body">
          @for (point of data.bulletPoints; track point) {
            <li>{{ point }}</li>
          }
        </ul>
      }

      <p class="mb-2 text-sm font-body text-text-secondary">
        Para confirmar, escribí la palabra
        <strong class="text-text-primary">{{ data.confirmWord }}</strong>
        en el campo de abajo:
      </p>
      <input
        [(ngModel)]="typedWord"
        [placeholder]="data.confirmWord"
        class="w-full h-10 px-3 rounded-md border border-border bg-white font-body text-sm
               focus:outline-none focus:border-primary focus:ring-1 focus:ring-primary"
      />

      @if (data.motivoLabel) {
        <label class="block mt-4 mb-1 text-sm font-body font-medium text-text-icon">{{ data.motivoLabel }}</label>
        <textarea
          [(ngModel)]="motivo"
          rows="3"
          class="w-full px-3 py-2 rounded-md border border-border bg-white font-body text-sm resize-none
                 focus:outline-none focus:border-primary focus:ring-1 focus:ring-primary"
        ></textarea>
      }
    </div>
    <div class="flex justify-end gap-3 px-6 pb-5">
      <button mat-button mat-dialog-close class="font-body">Cancelar</button>
      <button
        mat-raised-button
        color="warn"
        [disabled]="!canConfirm"
        (click)="confirm()"
        class="font-body"
      >
        {{ data.confirmLabel }}
      </button>
    </div>
  `,
})
export class StrongConfirmDialogComponent {
  data = inject<StrongConfirmDialogData>(MAT_DIALOG_DATA);
  private dialogRef = inject(MatDialogRef<StrongConfirmDialogComponent>);

  typedWord = '';
  motivo = '';

  get canConfirm(): boolean {
    const wordMatch = this.typedWord.trim().toUpperCase() === this.data.confirmWord.toUpperCase();
    if (this.data.motivoRequired) return wordMatch && this.motivo.trim().length >= 20;
    return wordMatch;
  }

  confirm(): void {
    this.dialogRef.close({ confirmed: true, motivo: this.motivo });
  }
}
