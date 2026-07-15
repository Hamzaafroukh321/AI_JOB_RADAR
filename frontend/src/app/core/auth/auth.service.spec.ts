import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AuthenticatedUser } from './auth.models';
import { AuthService } from './auth.service';

describe('AuthService', () => {
  const user: AuthenticatedUser = {
    id: '00000000-0000-0000-0000-000000000001', email: 'person@example.test',
    displayName: 'Synthetic Person', timezone: 'Africa/Casablanca', locale: 'en',
  };
  let service: AuthService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(AuthService); http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());

  it('restores an authenticated session', async () => {
    const result = service.restoreSession();
    http.expectOne('/api/v1/auth/me').flush(user);
    await expect(result).resolves.toBe(true);
    expect(service.user()).toEqual(user);
  });

  it('treats an expired session as unauthenticated', async () => {
    const result = service.restoreSession();
    http.expectOne('/api/v1/auth/me').flush({}, { status: 401, statusText: 'Unauthorized' });
    await expect(result).resolves.toBe(false);
    expect(service.authenticated()).toBe(false);
  });

  it('obtains CSRF before successful login and clears state after logout', async () => {
    const login = service.login({ email: user.email, password: 'synthetic-only' });
    http.expectOne('/api/v1/auth/csrf').flush({ headerName: 'X-XSRF-TOKEN' });
    await Promise.resolve();
    http.expectOne('/api/v1/auth/login').flush(user);
    await login;
    expect(service.authenticated()).toBe(true);
    const logout = service.logout();
    await Promise.resolve();
    http.expectOne('/api/v1/auth/logout').flush(null);
    await logout;
    expect(service.authenticated()).toBe(false);
  });
});
