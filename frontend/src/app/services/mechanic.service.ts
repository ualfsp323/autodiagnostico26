import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, BehaviorSubject, map } from 'rxjs';
import { API_BASE_URL } from './api.config';
import { AuthStateService } from './auth-state.service';

export interface MechanicClient {
  clientId: number;
  clientName: string;
  clientEmail: string;
  clientAvatar: string;
  carInfo: string;
  problemDescription: string;
  status: 'verde' | 'amarillo' | 'naranja' | 'rojo';
  latestUpdate?: string;
  sessionUuid: string;
  tallerAssignmentId: number;
}

@Injectable({ providedIn: 'root' })
export class MechanicService {
  private readonly auth = inject(AuthStateService);
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${API_BASE_URL}/mechanic`;
  private readonly clientsSubject = new BehaviorSubject<MechanicClient[]>([]);

  readonly clients$ = this.clientsSubject.asObservable();

  getClientsForMechanic(mechanicId: number): Observable<MechanicClient[]> {
    return this.http.get<MechanicClient[]>(`${this.baseUrl}/${mechanicId}/clients`);
  }

  updateClientStatus(mechanicId: number, clientId: number, status: string): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/${mechanicId}/clients/${clientId}/status`, { status });
  }

  updateTrackingMessage(mechanicId: number, clientId: number, message: string): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/${mechanicId}/clients/${clientId}/tracking-update`, { message });
  }

  loadClientsForMechanic(mechanicId: number): void {
    this.getClientsForMechanic(mechanicId).subscribe({
      next: (clients) => this.clientsSubject.next(clients),
      error: (err) => console.error('Error loading clients:', err)
    });
  }

  getTrackingForClient(clientId: number) {

  return this.http.get<MechanicClient>(
    `${this.baseUrl}/client/${clientId}/tracking`
  );
}

getTracking(clientId: number): Observable<MechanicClient> {

  const mechanicId = this.auth.userId();

  if (!mechanicId) {
    throw new Error('Mechanic ID no encontrado');
  }

  return this.http
    .get<MechanicClient[]>(
      `${this.baseUrl}/${mechanicId}/clients`
    )
    .pipe(
      map(clients => {

        console.log('CLIENTS', clients);

        const tracking = clients.find(
          c => c.clientId === clientId
        );

        if (!tracking) {
          throw new Error('Cliente no encontrado');
        }

        return tracking;
      })
    );
}

}
