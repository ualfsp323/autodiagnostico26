import { CommonModule } from '@angular/common';
import { Component, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { SeguimientoChatComponent } from '../../components/seguimiento/chat/chat';
import { AuthStateService } from '../../services/auth-state.service';
import { SeguimientoService, ClientStatus } from '../../services/seguimiento.service';

@Component({
  selector: 'app-seguimiento',
  standalone: true,
  imports: [CommonModule, FormsModule, SeguimientoChatComponent],
  templateUrl: './seguimiento.component.html',
  styleUrl: './seguimiento.component.css'
})
export class SeguimientoComponent {
  private seguimiento = inject(SeguimientoService);
  private auth = inject(AuthStateService);
  private route = inject(ActivatedRoute);

  message = '';
  updates = [
    'Se ha recibido el coche en el taller.',
    'Se está revisando el sistema de frenos.'
  ];

  // current clientId is read from query param or default
  clientId = 101;

  constructor() {
    const param = this.route.snapshot.queryParamMap.get('clientId') ?? this.route.snapshot.queryParamMap.get('clientid') ?? this.route.snapshot.queryParamMap.get('client');
    const parsed = Number(param);
    if (!Number.isNaN(parsed) && parsed > 0) {
      this.clientId = parsed;
    }
  }

  get isMechanic(): boolean {
    const role = this.auth.role();
    return role === 'TALLER' || role === 'ADMIN';
  }

  get status(): ClientStatus {
    return this.seguimiento.getStatus(this.clientId);
  }

  setStatus(status: ClientStatus): void {
    this.seguimiento.setStatus(this.clientId, status);
  }

  addMessage(): void {
    const trimmed = this.message.trim();
    if (!trimmed) {
      return;
    }

    this.updates = [trimmed, ...this.updates];
    this.message = '';
  }
}