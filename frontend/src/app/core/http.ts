import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';

import { AuthService } from './auth.service';
import { Role } from './models';

export const sessionInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  return next(req).pipe(catchError((err: unknown) => {
    if (err instanceof HttpErrorResponse && err.status === 401 && req.url.startsWith('/api/admin')) {
      auth.sessionExpired();
    }
    return throwError(() => err);
  }));
};

export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  return auth.me() ? true : inject(Router).createUrlTree(['/login'], { queryParams: { voltar: state.url } });
};

export const guestGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  return auth.me() ? inject(Router).createUrlTree(['/painel']) : true;
};

export function roleGuard(...roles: Role[]): CanActivateFn {
  return () => {
    const auth = inject(AuthService);
    return auth.hasRole(...roles) ? true : inject(Router).createUrlTree(['/painel']);
  };
}
