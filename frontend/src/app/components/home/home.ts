import { Component } from '@angular/core';
import { IntroducirVehiculo } from '../introducir-vehiculo/introducir-vehiculo';
import { SeleccionaProblema, ProblemaSeleccion } from '../selecciona-problema/selecciona-problema';
import { VehicleSearchContext } from '../../services/api.models';

@Component({
  selector: 'app-home-page',
  standalone: true,
  imports: [IntroducirVehiculo, SeleccionaProblema],
  templateUrl: './home.html',
  styleUrl: './home.css',
})
export class HomeComponent {
  vehicleContext: VehicleSearchContext | null = null;
  seleccion: ProblemaSeleccion = { problemas: [], descripcionLibre: '' };

  get tieneProblema(): boolean {
    return this.seleccion.problemas.length > 0 || !!this.seleccion.descripcionLibre.trim();
  }

  onVehicleContextChange(ctx: VehicleSearchContext): void {
    this.vehicleContext = ctx;
  }

  onProblemaChange(seleccion: ProblemaSeleccion): void {
    this.seleccion = seleccion;
  }

  onEnviar(): void {
    // TODO: navegar a la pantalla de diagnóstico
    console.log('Enviar', this.seleccion, this.vehicleContext);
  }
}
