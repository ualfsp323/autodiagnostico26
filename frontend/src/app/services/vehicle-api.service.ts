import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE_URL } from './api.config';
import { VehicleModelSummary, VehicleVariant } from './api.models';

@Injectable({ providedIn: 'root' })
export class VehicleApiService {
  private readonly base = API_BASE_URL;

  constructor(private http: HttpClient) {}

  getBrands(): Observable<string[]> {
    return this.http.get<string[]>(`${this.base}/vehicles/brands`);
  }

  getModels(brand: string): Observable<VehicleModelSummary[]> {
    return this.http.get<VehicleModelSummary[]>(
      `${this.base}/vehicles/brands/${encodeURIComponent(brand)}/models`,
    );
  }

  getVariants(vehicleId: number): Observable<VehicleVariant[]> {
    return this.http.get<VehicleVariant[]>(`${this.base}/vehicles/${vehicleId}/variants`);
  }
}
