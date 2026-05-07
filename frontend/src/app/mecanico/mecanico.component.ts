import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { RouterModule } from '@angular/router';
import { inject } from '@angular/core';
import { SeguimientoService } from '../services/seguimiento.service';

type ClientStatus = 'verde' | 'amarillo' | 'naranja' | 'rojo';

interface MechanicClient {
  id: number;
  userName: string;
  car: string;
  problem: string;
  status: ClientStatus;
}

@Component({
  selector: 'app-mecanico',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './mecanico.component.html',
  styleUrl: './mecanico.component.css'
})
export class MecanicoComponent {
  mechanicName = 'Mecánico 1';
  workshopName = 'Taller Aurora';
  private seguimiento = inject(SeguimientoService);

  clients: MechanicClient[] = [
    { id: 101, userName: 'Ana López', car: 'Toyota Corolla', problem: 'Cambio de aceite', status: this.seguimiento.getStatus(101) },
    { id: 102, userName: 'Luis Pérez', car: 'Honda Civic', problem: 'Frenos desgastados', status: this.seguimiento.getStatus(102) },
    { id: 103, userName: 'Ana López', car: 'Hyundai i30', problem: 'Revisión de batería', status: this.seguimiento.getStatus(103) },
    { id: 104, userName: 'María Gómez', car: 'Ford Focus', problem: 'Ruido en suspensión', status: this.seguimiento.getStatus(104) }
  ];

  getStatusLabel(status: ClientStatus): string {
    return {
      verde: 'En buen estado',
      amarillo: 'Pendiente',
      naranja: 'En revisión',
      rojo: 'Urgente'
    }[status];
  }

  getStatus(client: MechanicClient): ClientStatus {
    return this.seguimiento.getStatus(client.id, client.status);
  }
}