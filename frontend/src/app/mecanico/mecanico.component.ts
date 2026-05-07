import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { RouterModule } from '@angular/router';

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

  clients: MechanicClient[] = [
    { id: 1, userName: 'Ana López', car: 'Toyota Corolla', problem: 'Cambio de aceite', status: 'verde' },
    { id: 2, userName: 'Luis Pérez', car: 'Honda Civic', problem: 'Frenos desgastados', status: 'amarillo' },
    { id: 3, userName: 'Ana López', car: 'Hyundai i30', problem: 'Revisión de batería', status: 'naranja' },
    { id: 4, userName: 'María Gómez', car: 'Ford Focus', problem: 'Ruido en suspensión', status: 'rojo' }
  ];

  getStatusLabel(status: ClientStatus): string {
    return {
      verde: 'En buen estado',
      amarillo: 'Pendiente',
      naranja: 'En revisión',
      rojo: 'Urgente'
    }[status];
  }
}