import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, catchError, of, throwError } from 'rxjs';
import { API_BASE_URL } from './api.config';
import { AuthLoginRequest, AuthRegisterRequest, AuthUserResponse } from './api.models';

/**
 * Auth API service with a lightweight fallback mock for local development.
 * If the real backend login call fails (network error), we try to authenticate
 * against a small in-memory list of workshop users (role TALLER).
 */
@Injectable({ providedIn: 'root' })
export class AuthApiService {
  private readonly baseUrl = `${API_BASE_URL}/auth`;

  // Minimal mock users matching the backend mechanics list for quick testing.
  // Passwords here are only for local mock use: 'taller123'.
  private readonly mockMechanics: AuthUserResponse[] = [
    { id: 1, fullName: 'Mecánico 1', email: 'mec1@taller.local', role: 'TALLER', avatarUrl: '', createdAt: new Date().toISOString() },
    { id: 2, fullName: 'Mecánico 2', email: 'mec2@taller.local', role: 'TALLER', avatarUrl: '', createdAt: new Date().toISOString() },
    { id: 3, fullName: 'Mecánico 3', email: 'mec3@taller.local', role: 'TALLER', avatarUrl: '', createdAt: new Date().toISOString() },
    { id: 4, fullName: 'Mecánico 4', email: 'mec4@taller.local', role: 'TALLER', avatarUrl: '', createdAt: new Date().toISOString() },
    { id: 5, fullName: 'Mecánico 5', email: 'mec5@taller.local', role: 'TALLER', avatarUrl: '', createdAt: new Date().toISOString() },
    { id: 6, fullName: 'Mecánico 6', email: 'mec6@taller.local', role: 'TALLER', avatarUrl: '', createdAt: new Date().toISOString() },
    { id: 7, fullName: 'Mecánico 7', email: 'mec7@taller.local', role: 'TALLER', avatarUrl: '', createdAt: new Date().toISOString() },
    { id: 8, fullName: 'Mecánico 8', email: 'mec8@taller.local', role: 'TALLER', avatarUrl: '', createdAt: new Date().toISOString() },
    { id: 9, fullName: 'Mecánico 9', email: 'mec9@taller.local', role: 'TALLER', avatarUrl: '', createdAt: new Date().toISOString() },
    { id: 10, fullName: 'Mecánico 10', email: 'mec10@taller.local', role: 'TALLER', avatarUrl: '', createdAt: new Date().toISOString() }
  ];

  constructor(private readonly http: HttpClient) {}

  login(payload: AuthLoginRequest): Observable<AuthUserResponse> {
    return this.http.post<AuthUserResponse>(`${this.baseUrl}/login`, payload).pipe(
      catchError((err) => {
        // If backend is unreachable, attempt local mock authentication.
        console.warn('Auth backend failed, falling back to local mock auth:', err?.message ?? err);

        const found = this.mockMechanics.find((m) => m.email === payload.email && payload.password === 'taller123');
        if (found) {
          return of(found);
        }

        return throwError(() => err);
      })
    );
  }

  register(payload: AuthRegisterRequest): Observable<AuthUserResponse> {
    return this.http.post<AuthUserResponse>(`${this.baseUrl}/register`, payload).pipe(
      catchError((err) => {
        // No mock register flow for now.
        return throwError(() => err);
      })
    );
  }
}
