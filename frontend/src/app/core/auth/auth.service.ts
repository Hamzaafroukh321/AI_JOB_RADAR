import { HttpClient } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { AuthenticatedUser, LoginRequest } from './auth.models';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly currentUser = signal<AuthenticatedUser | null>(null);
  private initialized = false;

  readonly user = this.currentUser.asReadonly();
  readonly authenticated = computed(() => this.currentUser() !== null);

  async restoreSession(): Promise<boolean> {
    if (this.initialized) return this.authenticated();
    this.initialized = true;
    try {
      this.currentUser.set(await firstValueFrom(this.http.get<AuthenticatedUser>('/api/v1/auth/me')));
      return true;
    } catch {
      this.currentUser.set(null);
      return false;
    }
  }

  async login(request: LoginRequest): Promise<void> {
    await firstValueFrom(this.http.get('/api/v1/auth/csrf'));
    const user = await firstValueFrom(this.http.post<AuthenticatedUser>('/api/v1/auth/login', request));
    this.initialized = true;
    this.currentUser.set(user);
  }

  async logout(): Promise<void> {
    try { await firstValueFrom(this.http.post<void>('/api/v1/auth/logout', {})); }
    finally { this.clearSession(); }
  }

  clearSession(): void { this.initialized = true; this.currentUser.set(null); }
}

