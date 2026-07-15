import { HttpClient } from '@angular/common/http';
import { Component, inject, OnInit, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { DashboardSummary } from './dashboard.models';

@Component({
  selector: 'app-dashboard',
  template: `
    <header class="page-header"><p class="eyebrow">REPOSITORY & FOUNDATION</p><h1>Phase 0 control room</h1><p>The secure foundation is connected. Job discovery and resume features begin in later phases.</p></header>
    @if (loading()) { <section class="state" aria-live="polite">Loading protected foundation status…</section> }
    @else if (summary(); as data) {
      <section class="grid" aria-label="Foundation status">
        <article><span>Current phase</span><strong>{{ data.phase }}</strong><small>{{ data.status }}</small></article>
        <article><span>Signed in as</span><strong>{{ data.user.displayName }}</strong><small>{{ data.user.email }}</small></article>
        <article><span>Timezone</span><strong>{{ data.user.timezone }}</strong><small>UTC is used internally</small></article>
      </section>
      <section class="notice"><h2>Manual application by design</h2><p>AI Job Radar will help discover, assess, and prepare for roles. It will never submit a job application for you.</p></section>
    } @else { <section class="state" role="status">No foundation status is available. Check the backend readiness endpoint and retry.</section> }
  `,
  styleUrl: './dashboard.component.scss',
})
export class DashboardComponent implements OnInit {
  private readonly http = inject(HttpClient); readonly summary = signal<DashboardSummary | null>(null); readonly loading = signal(true);
  async ngOnInit(): Promise<void> { try { this.summary.set(await firstValueFrom(this.http.get<DashboardSummary>('/api/v1/dashboard/summary'))); } finally { this.loading.set(false); } }
}

