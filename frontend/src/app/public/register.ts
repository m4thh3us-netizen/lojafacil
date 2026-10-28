import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { debounceTime, distinctUntilChanged, filter, switchMap } from 'rxjs';

import { AuthService } from '../core/auth.service';
import { errorMessage, fieldErrors } from '../core/ui.services';
import { Icon } from '../shared/icon';
import { AuthFrame } from './auth-frame';
import { PASSWORD_PATTERN, PasswordField } from './auth-pages';

function slugify(s: string): string {
  return s.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/(^-|-$)/g, '').slice(0, 60);
}

@Component({
  selector: 'app-register',
  imports: [ReactiveFormsModule, RouterLink, AuthFrame, PasswordField, Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-auth-frame title="Criar sua loja" [width]="500">
      <ol class="steps" aria-label="Etapas do cadastro">
        <li [class.on]="step() === 1" [attr.aria-current]="step() === 1 ? 'step' : null">1. Responsável</li>
        <li [class.on]="step() === 2" [attr.aria-current]="step() === 2 ? 'step' : null">2. Loja</li>
      </ol>

      @if (step() === 1) {
        <form [formGroup]="owner" (ngSubmit)="next()" class="stack fade-in" novalidate>
          <div class="field"><label for="fn">Nome completo</label><input id="fn" class="input" formControlName="fullName" autocomplete="name" required></div>
          <div class="field"><label for="em">E-mail</label><input id="em" class="input" type="email" formControlName="email" autocomplete="email" required>
            @if (errors()['owner.email']) { <span class="error-text">{{ errors()['owner.email'] }}</span> }</div>
          <div class="field"><label for="ph">Telefone <span class="muted">(opcional)</span></label><input id="ph" class="input" formControlName="phone" autocomplete="tel"></div>
          <div class="field">
            <label for="pw">Senha</label>
            <app-password-field [target]="pw"><input #pw id="pw" class="input" type="password" formControlName="password" autocomplete="new-password" required></app-password-field>
            <span class="hint">Mínimo de 8 caracteres, com letras e números.</span>
          </div>
          <div class="field"><label for="pw2">Confirmação da senha</label><input id="pw2" class="input" type="password" formControlName="passwordConfirmation" autocomplete="new-password" required></div>
          @if (error()) { <div class="alert danger" role="alert">{{ error() }}</div> }
          <button class="btn btn-primary btn-block" type="submit">Continuar <app-icon name="arrowRight" /></button>
        </form>
      } @else {
        <form [formGroup]="store" (ngSubmit)="submit()" class="stack fade-in" novalidate>
          <div class="field"><label for="sn">Nome da loja</label><input id="sn" class="input" formControlName="name" (input)="autoSlug()" required></div>
          <div class="field">
            <label for="sl">Endereço da sua loja virtual</label>
            <div class="slug"><span class="muted">/loja/</span><input id="sl" class="input" formControlName="slug" (input)="slugTouched = true" required aria-describedby="slug-help"></div>
            <span id="slug-help" class="hint" [class.error-text]="slugStatus()?.available === false" aria-live="polite">
              @if (slugStatus(); as s) { {{ s.available ? 'Disponível ✓' : s.reason }} } @else { Letras minúsculas, números e hífen. }
            </span>
          </div>
          <div class="field"><label for="ds">Descrição <span class="muted">(opcional)</span></label><textarea id="ds" class="input" formControlName="description" rows="3"></textarea></div>
          @if (error()) { <div class="alert danger" role="alert">{{ error() }}</div> }
          <div class="row">
            <button class="btn" type="button" (click)="step.set(1)" [disabled]="busy()"><app-icon name="arrowLeft" /> Voltar</button>
            <button class="btn btn-primary spacer" type="submit" [disabled]="busy()">{{ busy() ? 'Criando loja...' : 'Criar loja' }}</button>
          </div>
        </form>
      }
      <p class="hint" style="margin-top:14px">Já tem conta? <a routerLink="/login">Entrar</a></p>
    </app-auth-frame>`,
  styles: [`
    .steps { display: flex; gap: 8px; list-style: none; padding: 0; margin: 8px 0 18px; }
    .steps li { flex: 1; padding: 8px 10px; border-radius: 10px; background: var(--surface-3); color: var(--text-3); font-size: .85rem; font-weight: 600; text-align: center; transition: background .15s; }
    .steps li.on { background: var(--primary-soft); color: var(--primary); }
    .slug { display: flex; align-items: center; gap: 6px; }
  `],
})
export class RegisterPage {
  private fb = inject(NonNullableFormBuilder);
  private auth = inject(AuthService);
  private http = inject(HttpClient);
  private router = inject(Router);
  readonly step = signal<1 | 2>(1);
  readonly busy = signal(false);
  readonly error = signal('');
  readonly errors = signal<Record<string, string>>({});
  readonly slugStatus = signal<{ available: boolean; reason?: string } | null>(null);
  slugTouched = false;

  readonly owner = this.fb.group({
    fullName: ['', Validators.required], email: ['', [Validators.required, Validators.email]], phone: [''],
    password: ['', [Validators.required, Validators.pattern(PASSWORD_PATTERN)]], passwordConfirmation: ['', Validators.required],
  });
  readonly store = this.fb.group({ name: ['', Validators.required], slug: ['', [Validators.required, Validators.minLength(3)]], description: [''] });

  constructor() {
    this.store.controls.slug.valueChanges.pipe(
      debounceTime(350), distinctUntilChanged(), filter(v => v.length >= 3),
      switchMap(slug => this.http.get<{ available: boolean; reason?: string }>('/api/auth/slug-availability', { params: { slug } })),
      takeUntilDestroyed(inject(DestroyRef)),
    ).subscribe(s => this.slugStatus.set(s));
  }

  autoSlug() {
    if (!this.slugTouched) this.store.controls.slug.setValue(slugify(this.store.controls.name.value));
  }

  next() {
    const v = this.owner.getRawValue();
    this.error.set('');
    if (this.owner.controls.fullName.invalid || this.owner.controls.email.invalid) { this.owner.markAllAsTouched(); this.error.set('Informe nome e um e-mail válido.'); return; }
    if (this.owner.controls.password.invalid) { this.error.set('A senha deve ter ao menos 8 caracteres, com letras e números.'); return; }
    if (v.password !== v.passwordConfirmation) { this.error.set('A confirmação de senha não confere.'); return; }
    this.step.set(2);
  }

  submit() {
    if (this.store.invalid) { this.store.markAllAsTouched(); this.error.set('Informe o nome e o endereço da loja.'); return; }
    if (this.busy()) return;
    this.busy.set(true);
    this.error.set('');
    this.auth.register({ owner: this.owner.getRawValue(), store: this.store.getRawValue() }).subscribe({
      next: () => this.router.navigateByUrl('/painel'),
      error: err => {
        this.busy.set(false);
        const fe = fieldErrors(err);
        this.errors.set(fe);
        if (Object.keys(fe).some(k => k.startsWith('owner'))) this.step.set(1);
        this.error.set(Object.values(fe)[0] ?? errorMessage(err));
      },
    });
  }
}
