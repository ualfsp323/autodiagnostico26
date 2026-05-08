import { Component, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';

export interface ProblemaItem {
  label: string;
  categoria: string;
}

interface CategoriaConfig {
  nombre: string;
  icono: string;
}

export interface ProblemaSeleccion {
  problemas: string[];
  descripcionLibre: string;
}

@Component({
  selector: 'app-selecciona-problema',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './selecciona-problema.html',
  styleUrl: './selecciona-problema.css',
})
export class SeleccionaProblema {
  @Output() problemaChange = new EventEmitter<ProblemaSeleccion>();

  readonly problemas: ProblemaItem[] = [
    // Motor
    { label: 'Motor no arranca', categoria: 'Motor' },
    { label: 'Arranque lento o dificultoso', categoria: 'Motor' },
    { label: 'Ruido extraño en el motor', categoria: 'Motor' },
    { label: 'Motor vibra o tiembla en ralentí', categoria: 'Motor' },
    { label: 'Sobrecalentamiento del motor', categoria: 'Motor' },
    { label: 'Pérdida de potencia', categoria: 'Motor' },
    { label: 'Consumo excesivo de combustible', categoria: 'Motor' },
    { label: 'Pérdida de aceite', categoria: 'Motor' },
    { label: 'Pérdida de líquido refrigerante', categoria: 'Motor' },

    // Frenos
    { label: 'Frenos defectuosos o poco eficaces', categoria: 'Frenos' },
    { label: 'Ruido al frenar (chirrido o crujido)', categoria: 'Frenos' },
    { label: 'Pedal de freno blando o se hunde', categoria: 'Frenos' },
    { label: 'Vibración al frenar', categoria: 'Frenos' },
    { label: 'Coche se desvía al frenar', categoria: 'Frenos' },
    { label: 'Freno de mano no funciona bien', categoria: 'Frenos' },
    { label: 'Luz de frenos encendida en el cuadro', categoria: 'Frenos' },

    // Electricidad
    { label: 'Batería descargada', categoria: 'Electricidad' },
    { label: 'Luz de batería encendida', categoria: 'Electricidad' },
    { label: 'Check Engine encendido', categoria: 'Electricidad' },
    { label: 'Luces exteriores no funcionan', categoria: 'Electricidad' },
    { label: 'Intermitentes no funcionan', categoria: 'Electricidad' },
    { label: 'Problemas con el arranque eléctrico', categoria: 'Electricidad' },
    { label: 'Fusibles que se queman', categoria: 'Electricidad' },
    { label: 'Alternador con problemas', categoria: 'Electricidad' },
    { label: 'Pantalla o mandos sin respuesta', categoria: 'Electricidad' },

    // Transmisión
    { label: 'Transmisión con problemas generales', categoria: 'Transmisión' },
    { label: 'Ruido al cambiar de marcha', categoria: 'Transmisión' },
    { label: 'Cambio de marchas duro o agarrotado', categoria: 'Transmisión' },
    { label: 'Embrague patina o no agarra', categoria: 'Transmisión' },
    { label: 'Caja automática no cambia bien', categoria: 'Transmisión' },
    { label: 'Pérdida de fluido de transmisión', categoria: 'Transmisión' },

    // Dirección / Suspensión
    { label: 'Dirección dura o con vibraciones', categoria: 'Dirección' },
    { label: 'Dirección asistida no funciona', categoria: 'Dirección' },
    { label: 'Coche tira hacia un lado', categoria: 'Dirección' },
    { label: 'Ruido en la suspensión al pasar baches', categoria: 'Dirección' },
    { label: 'Amortiguadores deteriorados', categoria: 'Dirección' },
    { label: 'Golpeteo por debajo del coche', categoria: 'Dirección' },

    // Neumáticos
    { label: 'Neumático pinchado', categoria: 'Neumáticos' },
    { label: 'Neumático pierde aire lentamente', categoria: 'Neumáticos' },
    { label: 'Desgaste irregular de neumáticos', categoria: 'Neumáticos' },
    { label: 'Vibración en el volante a alta velocidad', categoria: 'Neumáticos' },
    { label: 'Presión de neumáticos baja', categoria: 'Neumáticos' },

    // Climatización
    { label: 'Aire acondicionado no enfría', categoria: 'Climatización' },
    { label: 'Calefacción no calienta', categoria: 'Climatización' },
    { label: 'Olor a humedad al poner el ventilador', categoria: 'Climatización' },
    { label: 'Ventilador interior no funciona', categoria: 'Climatización' },
    { label: 'Cristales se empañan en exceso', categoria: 'Climatización' },

    // Escape / Emisiones
    { label: 'Humo blanco por el escape', categoria: 'Escape' },
    { label: 'Humo negro o azul por el escape', categoria: 'Escape' },
    { label: 'Ruido excesivo en el escape', categoria: 'Escape' },
    { label: 'Olor a gases dentro del habitáculo', categoria: 'Escape' },

    // Carrocería
    { label: 'Óxido o corrosión en la carrocería', categoria: 'Carrocería' },
    { label: 'Entrada de agua o lluvia al interior', categoria: 'Carrocería' },
    { label: 'Ruidos o crujidos en el habitáculo', categoria: 'Carrocería' },
    { label: 'Problemas con puertas o ventanillas', categoria: 'Carrocería' },

    // Otro
    { label: 'Otro problema no listado', categoria: 'Otro' },
  ];

  readonly categoriasConfig: CategoriaConfig[] = [
    { nombre: 'Motor', icono: '⚙️' },
    { nombre: 'Frenos', icono: '🛑' },
    { nombre: 'Electricidad', icono: '⚡' },
    { nombre: 'Transmisión', icono: '🔄' },
    { nombre: 'Dirección', icono: '🔧' },
    { nombre: 'Neumáticos', icono: '⭕' },
    { nombre: 'Climatización', icono: '❄️' },
    { nombre: 'Escape', icono: '💨' },
    { nombre: 'Carrocería', icono: '🚗' },
    { nombre: 'Otro', icono: '✏️' },
  ];

  categoriaActiva: string | null = null;
  problemasSeleccionados: string[] = [];
  descripcionLibre = '';

  readonly esOtro = (cat: string) => cat === 'Otro';

  get tieneSeleccion(): boolean {
    return this.problemasSeleccionados.length > 0 || !!this.descripcionLibre.trim();
  }

  isSelected(label: string): boolean {
    return this.problemasSeleccionados.includes(label);
  }

  problemasPorCategoria(cat: string): ProblemaItem[] {
    return this.problemas.filter(p => p.categoria === cat);
  }

  categoriaConProblema(cat: string): boolean {
    if (cat === 'Otro') return !!this.descripcionLibre.trim();
    return this.problemas.some(p => p.categoria === cat && this.isSelected(p.label));
  }

  toggleCategoria(nombre: string): void {
    this.categoriaActiva = this.categoriaActiva === nombre ? null : nombre;
  }

  toggleProblema(label: string): void {
    if (this.isSelected(label)) {
      this.problemasSeleccionados = this.problemasSeleccionados.filter(p => p !== label);
    } else {
      this.problemasSeleccionados = [...this.problemasSeleccionados, label];
    }
    this.emitChange();
  }

  onDescripcionLibre(valor: string): void {
    this.descripcionLibre = valor;
    this.emitChange();
  }

  private emitChange(): void {
    this.problemaChange.emit({
      problemas: this.problemasSeleccionados,
      descripcionLibre: this.descripcionLibre,
    });
  }
}
