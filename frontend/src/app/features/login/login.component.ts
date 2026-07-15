import { Component, inject } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { PasswordModule } from 'primeng/password';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, ButtonModule, InputTextModule, PasswordModule, MessageModule],
  template: `
    <main class="login-page">
      <section aria-labelledby="login-title">
        <div class="brand">AJ</div><p class="eyebrow">PRIVATE JOB INTELLIGENCE</p>
        <h1 id="login-title">Welcome to AI Job Radar</h1>
        <p>Sign in to your private workspace. Applications are always completed manually on the employer's website.</p>
        @if (errorMessage) { <p-message severity="error" role="alert">{{ errorMessage }}</p-message> }
        <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
          <label for="email">Email</label>
          <input pInputText id="email" type="email" autocomplete="username" formControlName="email" />
          @if (form.controls.email.touched && form.controls.email.invalid) { <small class="field-error">Enter a valid email address.</small> }
          <label for="password">Password</label>
          <p-password inputId="password" autocomplete="current-password" [feedback]="false" [toggleMask]="true" formControlName="password" />
          @if (form.controls.password.touched && form.controls.password.invalid) { <small class="field-error">Password is required.</small> }
          <button pButton type="submit" [loading]="submitting" [disabled]="submitting">Sign in</button>
        </form>
        <small>Development access is configured locally; no default password is published.</small>
      </section>
    </main>
  `,
  styleUrl: './login.component.scss',
})
export class LoginComponent {
  private readonly auth = inject(AuthService); private readonly router = inject(Router); private readonly route = inject(ActivatedRoute);
  submitting = false; errorMessage = '';
  readonly form = new FormGroup({
    email: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.email] }),
    password: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
  });
  async submit(): Promise<void> {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    this.submitting = true; this.errorMessage = '';
    try { await this.auth.login(this.form.getRawValue()); await this.router.navigateByUrl(this.route.snapshot.queryParamMap.get('returnUrl') ?? '/dashboard'); }
    catch { this.errorMessage = 'Sign-in failed. Check your credentials or try again later.'; }
    finally { this.submitting = false; }
  }
}

