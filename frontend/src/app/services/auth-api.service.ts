import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, catchError, of, throwError } from 'rxjs';
import { API_BASE_URL } from './api.config';
import { AuthLoginRequest, AuthRegisterRequest, AuthUserResponse } from './api.models';

/**
 * Auth API service that delegates authentication to the backend API.
 * No local fallback mocks are used; backend errors are propagated to callers.
 */
@Injectable({ providedIn: 'root' })
export class AuthApiService {
  private readonly baseUrl = `${API_BASE_URL}/auth`;


  constructor(private readonly http: HttpClient) {}

  login(payload: AuthLoginRequest): Observable<AuthUserResponse> {
    return this.http.post<AuthUserResponse>(`${this.baseUrl}/login`, payload).pipe(
      catchError((err) => {
        // Let callers handle backend errors; do not fallback to local mocks.
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
