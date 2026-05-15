import { CommonModule } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Workshop } from '../../services/api.models';
import { AuthStateService } from '../../services/auth-state.service';
import { WorkshopService } from '../../services/workshop.service';

@Component({
  selector: 'app-taller-page',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './taller.html',
  styleUrls: ['./taller.css']
})
export class TallerComponent implements OnInit {
  private readonly workshopService = inject(WorkshopService);
  private readonly authState = inject(AuthStateService);
  private readonly router = inject(Router);

  readonly workshops = signal<Workshop[]>([]);
  readonly selectedWorkshop = signal<Workshop | null>(null);
  readonly loading = signal(true);
  readonly selecting = signal(false);
  readonly error = signal('');
  readonly userId = computed(() => this.authState.userId());

  ngOnInit(): void {
    this.loadWorkshops();
  }

  loadWorkshops(): void {
    this.loading.set(true);
    this.error.set('');

    this.workshopService.listWorkshops(this.userId()).subscribe({
      next: (workshops) => {
        this.workshops.set(workshops);
        this.selectedWorkshop.set(workshops.find((workshop) => workshop.selectedByClient) ?? workshops[0] ?? null);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('No se han podido cargar los talleres. Intentalo de nuevo en unos segundos.');
        this.loading.set(false);
      }
    });
  }

  selectPreview(workshop: Workshop): void {
    this.selectedWorkshop.set(workshop);
  }

  chooseWorkshop(workshop: Workshop): void {
    const clientId = this.userId();
    if (!clientId) {
      this.error.set('Inicia sesion como cliente para elegir un taller.');
      return;
    }

    if (workshop.selectedByClient && workshop.sessionUuid) {
      this.goToTracking(workshop.sessionUuid);
      return;
    }

    this.selecting.set(true);
    this.error.set('');

    this.workshopService.selectWorkshop(workshop.id, clientId).subscribe({
      next: (response) => {
        localStorage.setItem('trackingSessionUuid', response.tracking.sessionUuid);
        this.selecting.set(false);
        this.loadWorkshops();
        this.router.navigate(['/usuario/seguimiento/chat']);
      },
      error: (err) => {
        this.error.set(err.status === 409
          ? 'Este taller esta completo ahora mismo. Elige otro taller disponible.'
          : 'No se ha podido crear la sesion con el mecanico.');
        this.selecting.set(false);
      }
    });
  }

  goToTracking(sessionUuid: string | null): void {
    if (sessionUuid) {
      localStorage.setItem('trackingSessionUuid', sessionUuid);
    }
    this.router.navigate(['/usuario/seguimiento/chat']);
  }

  occupancyPercent(workshop: Workshop): number {
    if (workshop.vehicleLimit <= 0) {
      return 100;
    }

    return Math.min(100, Math.round((workshop.activeVehicles / workshop.vehicleLimit) * 100));
  }

  occupancyLabel(workshop: Workshop): string {
    return this.isFull(workshop) ? 'Completo' : `${this.occupancyPercent(workshop)}% ocupado`;
  }

  isFull(workshop: Workshop): boolean {
    return workshop.activeVehicles >= workshop.vehicleLimit && !workshop.selectedByClient;
  }
}
