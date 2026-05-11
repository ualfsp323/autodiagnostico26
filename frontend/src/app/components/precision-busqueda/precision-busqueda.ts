import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-precision-busqueda',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './precision-busqueda.html',
  styleUrl: './precision-busqueda.css',
})
export class PrecisionBusqueda {
  @Input() level: number = 0;
  @Input() label: string = 'Sin información del vehículo';

  get dots(): boolean[] {
    return [0, 1, 2, 3].map(i => i < this.level);
  }
}
