import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { AuthService } from './auth.service';
import { authGuard } from './auth.guard';

describe('authGuard', () => {
  it('redirects unauthenticated users to login', async () => {
    const auth = { restoreSession: async () => false };
    TestBed.configureTestingModule({ providers: [provideRouter([]), { provide: AuthService, useValue: auth }] });
    const result = await TestBed.runInInjectionContext(() => authGuard({} as never, { url: '/dashboard' } as never));
    expect(TestBed.inject(Router).serializeUrl(result as never)).toContain('/login');
  });
});
