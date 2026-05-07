import { Injectable } from '@angular/core';

export type ClientStatus = 'verde' | 'amarillo' | 'naranja' | 'rojo';

@Injectable({ providedIn: 'root' })
export class SeguimientoService {
  private storageKey = 'autodiagnostico.client.statuses';

  private load(): Record<number, ClientStatus> {
    try {
      const raw = localStorage.getItem(this.storageKey);
      if (!raw) return {};
      return JSON.parse(raw) as Record<number, ClientStatus>;
    } catch {
      return {};
    }
  }

  private save(map: Record<number, ClientStatus>): void {
    try {
      localStorage.setItem(this.storageKey, JSON.stringify(map));
    } catch {}
  }

  getStatus(clientId: number, fallback: ClientStatus = 'verde'): ClientStatus {
    const map = this.load();
    return (map[clientId] as ClientStatus) ?? fallback;
  }

  setStatus(clientId: number, status: ClientStatus): void {
    const map = this.load();
    map[clientId] = status;
    this.save(map);
  }
}
