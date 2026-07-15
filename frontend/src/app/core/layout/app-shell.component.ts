import { Component, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { ApiErrorService } from '../api/api-error.service';
import { AuthService } from '../auth/auth.service';

interface NavigationItem { readonly label: string; readonly route: string; }

@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, ButtonModule],
  template: `
    <a class="skip-link" href="#main-content">Skip to content</a>
    <div class="shell">
      <header>
        <div><span class="mark">AJ</span><strong>AI Job Radar</strong><small>Candidate truth & source ingestion</small></div>
        <div class="account"><span>{{ auth.user()?.displayName }}</span><button pButton type="button" severity="secondary" (click)="logout()">Log out</button></div>
      </header>
      <aside aria-label="Primary navigation">
        <nav>
          @for (item of navigation; track item.label) {
            <a [routerLink]="item.route" routerLinkActive="active">{{ item.label }}</a>
          }
        </nav>
      </aside>
      <main id="main-content" tabindex="-1">
        @if (errors.notice(); as notice) {
          <section class="error" role="alert"><strong>{{ notice.title }}</strong><span>{{ notice.detail }}</span><button type="button" (click)="errors.clear()" aria-label="Dismiss error">×</button></section>
        }
        <router-outlet />
      </main>
    </div>
  `,
  styleUrl: './app-shell.component.scss',
})
export class AppShellComponent {
  readonly auth = inject(AuthService);
  readonly errors = inject(ApiErrorService);
  private readonly router = inject(Router);
  readonly navigation: readonly NavigationItem[] = [
    { label: 'Dashboard', route: '/dashboard' },
    { label: 'My Profile & Resumes', route: '/profile' },
    { label: 'Junior AI Job Search', route: '/jobs' },
    { label: 'Tailored Resumes', route: '/tailored-resumes' },
    { label: 'Applications', route: '/applications' }, { label: 'Sources & Fetch Health', route: '/sources' },
  ];
  async logout(): Promise<void> { await this.auth.logout(); await this.router.navigate(['/login']); }
}
