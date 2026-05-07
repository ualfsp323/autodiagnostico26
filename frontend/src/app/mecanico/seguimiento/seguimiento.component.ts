import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-seguimiento',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './seguimiento.component.html',
  styleUrl: './seguimiento.component.css'
})
export class SeguimientoComponent {
  message = '';
  updates = [
    'Se ha recibido el coche en el taller.',
    'Se está revisando el sistema de frenos.'
  ];

  addMessage(): void {
    const trimmed = this.message.trim();
    if (!trimmed) {
      return;
    }

    this.updates = [trimmed, ...this.updates];
    this.message = '';
  }
}