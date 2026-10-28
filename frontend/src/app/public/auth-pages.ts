import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, OnInit, inject, input, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { AuthService } from '../core/auth.service';
import { LABELS } from '../core/format';
import { Role } from '../core/models';
import { ToastService, errorMessage, fieldErrors } from '../core/ui.services';
import { Icon } from '../shared/icon';
import { AuthFrame } from './auth-frame';

export const PASSWORD_PATTERN = /^(?=.*[A-Za-z])(?=.*\d).{8,72}$/;

@Component({
  selector: 'app-password-field',
  imports: [Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="input-group">
      <ng-content />
      <button type="button" class="btn btn-ghost btn-icon btn-sm" (click)="toggle()"
              [attr.aria-label]="shown() ? 'Ocultar senha' : 'Mostrar senha'" [attr.aria-pressed]="shown()">
        <app-icon [name]="shown() ? 'eyeOff' : 'eye'" />
      </button>
    </div>`,
})
export class PasswordField {
  readonly target = input.required<HTMLInputElement>();
  readonly shown = signal(false);
  toggle() {
    this.shown.update(v => !v);
    this.target().type = this.shown() ? 'text' : 'password';
  }
}

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, RouterLink, AuthFrame, PasswordField],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-auth-frame title="Entrar no painel" subtitle="Acesse a gestão da sua loja.">
      @if (expired) { <div class="alert warning" role="alert">Sua sessão expirou. Entre novamente.</div> }
      <form [formGroup]="form" (ngSubmit)="submit()" class="stack" style="margin-top:16px" novalidate>
        <div class="field">
          <label for="email">E-mail</label>
          <input id="email" class="input" type="email" formControlName="email" autocomplete="username" required>
        </div>
        <div class="field">
          <div class="row-between"><label for="pw">Senha</label><a routerLink="/recuperar-senha" class="hint">Esqueci minha senha</a></div>
          <app-password-field [target]="pw"><input #pw id="pw" class="input" type="password" formControlName="password" autocomplete="current-password" required></app-password-field>
        </div>
        @if (error()) { <div class="alert danger" role="alert">{{ error() }}</div> }
        <button class="btn btn-primary btn-block" type="submit" [disabled]="busy()">{{ busy() ? 'Entrando...' : 'Entrar' }}</button>
      </form>
      <hr class="divider">
      <p class="hint">Ambiente demonstrativo: <b>demo&#64;lojafacil.dev</b> / <b>Demo&#64;1234</b> (também gerente&#64; e operador&#64;lojafacil.dev).</p>
      <p class="hint">Ainda não tem conta? <a routerLink="/cadastro">Crie sua loja</a>.</p>
    </app-auth-frame>`,
})
export class LoginPage {
  private fb = inject(NonNullableFormBuilder);
  private auth = inject(AuthService);
  private router = inject(Router);
  readonly voltar = input<string>();
  readonly expirada = input<string>();
  readonly busy = signal(false);
  readonly error = signal('');
  readonly form = this.fb.group({ email: ['', [Validators.required, Validators.email]], password: ['', Validators.required] });
  get expired() { return !!this.expirada(); }

  submit() {
    if (this.form.invalid) { this.form.markAllAsTouched(); this.error.set('Informe e-mail e senha.'); return; }
    if (this.busy()) return;
    this.busy.set(true);
    this.error.set('');
    const { email, password } = this.form.getRawValue();
    this.auth.login(email, password).subscribe({
      next: () => {
        const back = this.voltar();
        this.router.navigateByUrl(back && back.startsWith('/painel') ? back : '/painel');
      },
      error: err => { this.busy.set(false); this.error.set(errorMessage(err)); },
    });
  }
}

@Component({
  selector: 'app-forgot',
  imports: [ReactiveFormsModule, RouterLink, AuthFrame],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-auth-frame title="Recuperar senha" subtitle="Enviaremos um link de uso único, válido por 30 minutos.">
      @if (sent()) {
        <div class="alert success" role="status" style="margin-top:12px">
          Se o e-mail estiver cadastrado, você receberá o link em instantes. Em desenvolvimento, veja a caixa do Mailpit (localhost:8025).
        </div>
      } @else {
        <form [formGroup]="form" (ngSubmit)="submit()" class="stack" style="margin-top:16px" novalidate>
          <div class="field"><label for="email">E-mail</label><input id="email" class="input" type="email" formControlName="email" required></div>
          @if (error()) { <div class="alert danger" role="alert">{{ error() }}</div> }
          <button class="btn btn-primary btn-block" [disabled]="busy()">{{ busy() ? 'Enviando...' : 'Enviar link' }}</button>
        </form>
      }
      <p class="hint" style="margin-top:14px"><a routerLink="/login">Voltar ao login</a></p>
    </app-auth-frame>`,
})
export class ForgotPage {
  private http = inject(HttpClient);
  readonly form = inject(NonNullableFormBuilder).group({ email: ['', [Validators.required, Validators.email]] });
  readonly busy = signal(false);
  readonly sent = signal(false);
  readonly error = signal('');

  submit() {
    if (this.form.invalid || this.busy()) { this.form.markAllAsTouched(); return; }
    this.busy.set(true);
    this.http.post('/api/auth/password/forgot', this.form.getRawValue()).subscribe({
      next: () => this.sent.set(true),
      error: err => { this.busy.set(false); this.error.set(errorMessage(err)); },
    });
  }
}

@Component({
  selector: 'app-reset',
  imports: [ReactiveFormsModule, RouterLink, AuthFrame, PasswordField],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-auth-frame title="Criar nova senha">
      @if (!token()) {
        <div class="alert danger" role="alert">Link inválido. Solicite uma nova recuperação.</div>
      } @else {
        <form [formGroup]="form" (ngSubmit)="submit()" class="stack" style="margin-top:12px" novalidate>
          <div class="field">
            <label for="pw">Nova senha</label>
            <app-password-field [target]="pw"><input #pw id="pw" class="input" type="password" formControlName="password" autocomplete="new-password"></app-password-field>
            <span class="hint">Mínimo de 8 caracteres, com letras e números.</span>
          </div>
          <div class="field"><label for="pw2">Confirme a senha</label><input id="pw2" class="input" type="password" formControlName="passwordConfirmation" autocomplete="new-password"></div>
          @if (error()) { <div class="alert danger" role="alert">{{ error() }}</div> }
          <button class="btn btn-primary btn-block" [disabled]="busy()">{{ busy() ? 'Salvando...' : 'Salvar nova senha' }}</button>
        </form>
      }
      <p class="hint" style="margin-top:14px"><a routerLink="/login">Voltar ao login</a></p>
    </app-auth-frame>`,
})
export class ResetPage {
  private http = inject(HttpClient);
  private router = inject(Router);
  private toast = inject(ToastService);
  readonly token = input<string>();
  readonly form = inject(NonNullableFormBuilder).group({
    password: ['', [Validators.required, Validators.pattern(PASSWORD_PATTERN)]],
    passwordConfirmation: ['', Validators.required],
  });
  readonly busy = signal(false);
  readonly error = signal('');

  submit() {
    const v = this.form.getRawValue();
    if (this.form.invalid) { this.error.set('A senha deve ter ao menos 8 caracteres, com letras e números.'); return; }
    if (v.password !== v.passwordConfirmation) { this.error.set('A confirmação não confere.'); return; }
    if (this.busy()) return;
    this.busy.set(true);
    this.http.post('/api/auth/password/reset', { ...v, token: this.token() }).subscribe({
      next: () => { this.toast.success('Senha alterada. Entre com a nova senha.'); this.router.navigateByUrl('/login'); },
      error: err => { this.busy.set(false); this.error.set(errorMessage(err)); },
    });
  }
}

@Component({
  selector: 'app-invite',
  imports: [ReactiveFormsModule, AuthFrame, PasswordField],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-auth-frame title="Aceitar convite">
      @if (loading()) {
        <span class="skeleton line"></span><span class="skeleton line" style="width:70%"></span>
      } @else if (!preview()) {
        <div class="alert danger" role="alert">{{ error() || 'Convite inválido ou expirado.' }}</div>
      } @else {
        <p class="text-2">Você foi convidado(a) para a equipe de <b>{{ preview()!.storeName }}</b> como <b>{{ roleLabel(preview()!.role) }}</b> ({{ preview()!.email }}).</p>
        <form [formGroup]="form" (ngSubmit)="submit()" class="stack" novalidate>
          <div class="field"><label for="n">Nome completo</label><input id="n" class="input" formControlName="fullName" autocomplete="name"></div>
          <div class="field"><label for="ph">Telefone (opcional)</label><input id="ph" class="input" formControlName="phone" autocomplete="tel"></div>
          <div class="field">
            <label for="pw">Senha</label>
            <app-password-field [target]="pw"><input #pw id="pw" class="input" type="password" formControlName="password" autocomplete="new-password"></app-password-field>
            <span class="hint">Mínimo de 8 caracteres, com letras e números.</span>
          </div>
          <div class="field"><label for="pw2">Confirme a senha</label><input id="pw2" class="input" type="password" formControlName="passwordConfirmation"></div>
          @if (error()) { <div class="alert danger" role="alert">{{ error() }}</div> }
          <button class="btn btn-primary btn-block" [disabled]="busy()">{{ busy() ? 'Criando acesso...' : 'Criar acesso' }}</button>
        </form>
      }
    </app-auth-frame>`,
})
export class InvitePage implements OnInit {
  private http = inject(HttpClient);
  private auth = inject(AuthService);
  private router = inject(Router);
  readonly token = input<string>();
  readonly loading = signal(true);
  readonly busy = signal(false);
  readonly error = signal('');
  readonly preview = signal<{ storeName: string; email: string; role: Role } | null>(null);
  readonly form = inject(NonNullableFormBuilder).group({
    fullName: ['', Validators.required], phone: [''],
    password: ['', [Validators.required, Validators.pattern(PASSWORD_PATTERN)]], passwordConfirmation: ['', Validators.required],
  });

  ngOnInit() {
    const t = this.token();
    if (!t) { this.loading.set(false); return; }
    this.http.get<{ storeName: string; email: string; role: Role }>(`/api/auth/invitations/${encodeURIComponent(t)}`).subscribe({
      next: p => { this.preview.set(p); this.loading.set(false); },
      error: err => { this.error.set(errorMessage(err)); this.loading.set(false); },
    });
  }

  roleLabel(r: Role) { return LABELS[r]; }

  submit() {
    const v = this.form.getRawValue();
    if (this.form.invalid) { this.error.set('Preencha nome e uma senha com ao menos 8 caracteres, letras e números.'); return; }
    if (v.password !== v.passwordConfirmation) { this.error.set('A confirmação não confere.'); return; }
    if (this.busy()) return;
    this.busy.set(true);
    this.auth.acceptInvitation({ ...v, token: this.token() }).subscribe({
      next: () => this.router.navigateByUrl('/painel'),
      error: err => { this.busy.set(false); const fe = fieldErrors(err); this.error.set(Object.values(fe)[0] ?? errorMessage(err)); },
    });
  }
}
