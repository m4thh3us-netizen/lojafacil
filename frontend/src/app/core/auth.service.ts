import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, catchError, firstValueFrom, of, tap } from 'rxjs';

import { Me, Role } from './models';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private http = inject(HttpClient);
  private router = inject(Router);

  readonly me = signal<Me | null>(null);
  readonly loaded = signal(false);
  readonly role = computed(() => this.me()?.user.role ?? null);
  readonly isManagement = computed(() => this.role() === 'OWNER' || this.role() === 'MANAGER');
  readonly isOwner = computed(() => this.role() === 'OWNER');

  load(): Promise<unknown> {
    return firstValueFrom(this.http.get<Me>('/api/auth/me').pipe(
      tap(me => this.me.set(me)),
      catchError(() => { this.me.set(null); return of(null); }),
      tap(() => this.loaded.set(true)),
    ));
  }

  login(email: string, password: string): Observable<Me> {
    return this.http.post<Me>('/api/auth/login', { email, password }).pipe(tap(me => this.me.set(me)));
  }

  register(body: unknown): Observable<Me> {
    return this.http.post<Me>('/api/auth/register', body).pipe(tap(me => this.me.set(me)));
  }

  acceptInvitation(body: unknown): Observable<Me> {
    return this.http.post<Me>('/api/auth/invitations/accept', body).pipe(tap(me => this.me.set(me)));
  }

  logout(): void {
    this.http.post('/api/auth/logout', {}).pipe(catchError(() => of(null))).subscribe(() => {
      this.me.set(null);
      this.router.navigateByUrl('/login');
    });
  }

  sessionExpired(): void {
    if (this.me()) {
      this.me.set(null);
      this.router.navigate(['/login'], { queryParams: { expirada: 1 } });
    }
  }

  hasRole(...roles: Role[]): boolean {
    const r = this.role();
    return r !== null && roles.includes(r);
  }
}
