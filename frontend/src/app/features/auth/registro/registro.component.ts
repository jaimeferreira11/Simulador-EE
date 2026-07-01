import { Component, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { LucideAngularModule, Check } from 'lucide-angular';
import {
  InvitacionApiService,
  InvitacionDetalle,
} from '../../../core/services/invitacion-api.service';
import Swal from 'sweetalert2';

@Component({
  selector: 'app-registro',
  standalone: true,
  imports: [FormsModule, MatIconModule, LucideAngularModule, RouterLink],
  templateUrl: './registro.component.html',
})
export class RegistroComponent implements OnInit {
  readonly icons = { Check };
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private invitacionApi = inject(InvitacionApiService);

  hidePassword = true;
  hideConfirm = true;
  accepted = false;
  password = '';
  confirmPassword = '';

  token = '';
  loadingInvitacion = signal(true);
  loading = signal(false);
  error = signal<string | null>(null);
  invitacionError = signal<string | null>(null);

  invitacion = signal<InvitacionDetalle | null>(null);

  ngOnInit(): void {
    this.token = this.route.snapshot.queryParamMap.get('token') ?? '';
    if (!this.token) {
      this.loadingInvitacion.set(false);
      this.invitacionError.set('No se encontro un token de invitacion valido.');
      return;
    }
    this.invitacionApi.getByToken(this.token).subscribe({
      next: (inv) => {
        this.loadingInvitacion.set(false);
        if (inv.estado !== 'PENDIENTE') {
          this.invitacionError.set(
            inv.estado === 'ACEPTADA'
              ? 'Esta invitacion ya fue aceptada. Podes iniciar sesion.'
              : 'Esta invitacion ya no es valida (' + inv.estado.toLowerCase() + ').',
          );
          return;
        }
        this.invitacion.set(inv);
      },
      error: () => {
        this.loadingInvitacion.set(false);
        this.invitacionError.set(
          'No se pudo cargar la invitacion. El link puede ser invalido o haber expirado.',
        );
      },
    });
  }

  get passwordsMatch(): boolean {
    return this.password === this.confirmPassword && this.password.length >= 8;
  }

  activar(): void {
    if (!this.passwordsMatch || !this.accepted || !this.token) return;
    this.loading.set(true);
    this.error.set(null);

    this.invitacionApi.aceptar(this.token, this.password).subscribe({
      next: () => {
        this.loading.set(false);
        Swal.fire({
          title: 'Cuenta activada',
          text: 'Ya podes iniciar sesion con tu correo y contraseña.',
          icon: 'success',
          confirmButtonText: 'Ir a Login',
          confirmButtonColor: '#006B3F',
        }).then(() => {
          this.router.navigate(['/login']);
        });
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(err.error?.detail ?? 'Error al activar la cuenta');
      },
    });
  }
}
