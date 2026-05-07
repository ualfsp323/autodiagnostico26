import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { RouterModule } from '@angular/router';
import { inject } from '@angular/core';
import { SeguimientoService } from '../services/seguimiento.service';
import { MechanicService, MechanicClient } from '../services/mechanic.service';
import { AuthStateService } from '../services/auth-state.service';

@Component({
  selector: 'app-mecanico',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './mecanico.component.html',
  styleUrl: './mecanico.component.css'
})
export class MecanicoComponent implements OnInit {
  mechanicName = 'Mecánico';
  workshopName = 'Taller';
  private seguimiento = inject(SeguimientoService);
  private mechanicService = inject(MechanicService);
  private authState = inject(AuthStateService);

  clients: MechanicClient[] = [];
  loading = true;
  error: string | null = null;

  ngOnInit(): void {
    const mechanicId = this.authState.userId();
    if (mechanicId) {
      this.mechanicName = this.authState.userName();
      this.mechanicService.getClientsForMechanic(mechanicId).subscribe({
        next: (clients) => {
          this.clients = clients;
          this.loading = false;
        },
        error: (err) => {
          this.error = 'Error loading clients';
          this.loading = false;
          console.error('Error loading clients:', err);
        }
      });
    }
  }

  getStatusLabel(status: string): string {
    return {
      verde: 'En buen estado',
      amarillo: 'Pendiente',
      naranja: 'En revisión',
      rojo: 'Urgente'
    }[status] || status;
  }
}