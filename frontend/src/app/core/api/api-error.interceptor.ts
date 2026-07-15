import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { ApiErrorService } from './api-error.service';
import { AuthService } from '../auth/auth.service';

interface ProblemDetails { readonly title?: string; readonly detail?: string; }

export const apiErrorInterceptor: HttpInterceptorFn = (request, next) => {
  const errors = inject(ApiErrorService);
  const auth = inject(AuthService);
  return next(request).pipe(catchError((error: HttpErrorResponse) => {
    if (error.status === 401 && !request.url.endsWith('/login')) auth.clearSession();
    const handledByAuthFlow = request.url.endsWith('/auth/me') || request.url.endsWith('/auth/login');
    if (handledByAuthFlow) return throwError(() => error);
    const problem = typeof error.error === 'object' && error.error ? error.error as ProblemDetails : null;
    errors.show({ title: problem?.title ?? 'Request failed', detail: problem?.detail ?? 'The service is unavailable. Please try again.' });
    return throwError(() => error);
  }));
};
