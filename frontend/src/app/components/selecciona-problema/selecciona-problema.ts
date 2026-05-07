import { Component, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';

export interface ProblemaItem {
  label: string;
  categoria: string;
}

@Component({
  selector: 'app-selecciona-problema',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './selecciona-problema.html',
  styleUrl: './selecciona-problema.css',
})
export class SeleccionaProblema {
  @Output() problemaChange = new EventEmitter<string | null>();

  readonly problemas: ProblemaItem[] = [
    // Motor
    { label: 'Motor no arranca',                    categoria: 'Motor' },
    { label: 'Ruido extraño en el motor',            categoria: 'Motor' },
    { label: 'Sobrecalentamiento del motor',         categoria: 'Motor' },
    { label: 'Pérdida de potencia',                  categoria: 'Motor' },
    { label: 'Consumo excesivo de combustible',      categoria: 'Motor' },
    // Frenos y dirección
    { label: 'Frenos defectuosos',                   categoria: 'Frenos y dirección' },
    { label: 'Ruido al frenar',                      categoria: 'Frenos y dirección' },
    { label: 'Dirección dura o con vibraciones',     categoria: 'Frenos y dirección' },
    // Electricidad
    { label: 'Luces no funcionan',                   categoria: 'Electricidad' },
    { label: 'Batería descargada',                   categoria: 'Electricidad' },
    { label: 'Check Engine encendido',               categoria: 'Electricidad' },
    // Transmisión
    { label: 'Transmisión con problemas',            categoria: 'Transmisión' },
    { label: 'Ruido al cambiar de marcha',           categoria: 'Transmisión' },
    // Confort y carrocería
    { label: 'Aire acondicionado no funciona',       categoria: 'Confort' },
    { label: 'Pérdida de aceite u otros líquidos',   categoria: 'Confort' },
    { label: 'Humo por el escape',                   categoria: 'Confort' },
    { label: 'Suspensión o neumáticos',              categoria: 'Confort' },
    // General
    { label: 'Otro',                                 categoria: 'General' },
  ];

  readonly categorias: string[] = [
    ...new Set(this.problemas.map(p => p.categoria)),
  ];

  dropdownAbierto = false;
  problemaSeleccionado: string | null = null;

  problemasPorCategoria(cat: string): ProblemaItem[] {
    return this.problemas.filter(p => p.categoria === cat);
  }

  toggleDropdown(): void {
    this.dropdownAbierto = !this.dropdownAbierto;
  }

  seleccionarProblema(label: string): void {
    this.problemaSeleccionado = label;
    this.dropdownAbierto = false;
    this.problemaChange.emit(label);
  }
}
