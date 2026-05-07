import { Component } from '@angular/core';
import { BuscarVehiculo } from '../buscar-vehiculo/buscar-vehiculo';
import { SeleccionaProblema } from '../selecciona-problema/selecciona-problema';
import { VehicleSearchContext } from '../../services/api.models';

@Component({
  selector: 'app-home-page',
  standalone: true,
  imports: [BuscarVehiculo, SeleccionaProblema],
  templateUrl: './home.html',
  styleUrl: './home.css'
})
export class HomeComponent {
  vehicleContext: VehicleSearchContext | null = null;
  problemaSeleccionado: string | null = null;

  onVehicleContextChange(ctx: VehicleSearchContext): void {
    this.vehicleContext = ctx;
  }

  onProblemaChange(problema: string | null): void {
    this.problemaSeleccionado = problema;
  }
}
