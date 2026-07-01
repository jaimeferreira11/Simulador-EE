import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { LucideAngularModule, Check } from 'lucide-angular';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-recuperar-password',
  standalone: true,
  imports: [RouterLink, FormsModule, LucideAngularModule],
  templateUrl: './recuperar-password.component.html',
})
export class RecuperarPasswordComponent {
  readonly icons = { Check };
  private authService = inject(AuthService);

  email = '';
  enviado = signal(false);
  loading = signal(false);
  error = signal<string | null>(null);

  enviar(): void {
    if (!this.email.trim()) return;
    this.loading.set(true);
    this.error.set(null);
    this.authService.requestPasswordReset(this.email.trim()).subscribe({
      next: () => {
        this.loading.set(false);
        this.enviado.set(true);
      },
      error: () => {
        // API returns 204 even if email doesn't exist (security), so this is a real error
        this.loading.set(false);
        this.enviado.set(true); // Show success anyway to not leak email existence
      },
    });
  }
}
