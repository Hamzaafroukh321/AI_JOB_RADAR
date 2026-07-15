import { DatePipe, TitleCasePipe } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { ApplicationRecord } from './application.models';
import { ApplicationService } from './application.service';

@Component({
  selector: 'app-applications',
  imports: [DatePipe, TitleCasePipe, ReactiveFormsModule, ButtonModule],
  template: `
    <header class="page-heading"><div><p class="eyebrow">Manual workflow</p><h1>Applications</h1>
      <p>Track actions you take on employer sites. AI Job Radar never fills or submits an application.</p></div>
      <a pButton href="/api/v1/applications/export.csv">Export CSV</a>
    </header>
    <section class="board" aria-live="polite">
      @for (application of data.applications(); track application.id) {
        <article class="card">
          <div class="heading"><div><h2>{{ application.title }}</h2><p>{{ application.company }}</p></div><strong>{{ application.state | titlecase }}</strong></div>
          <p>Updated {{ application.updatedAt | date:'medium' }}</p>
          @if (application.resumeVersionId) { <p><small>Locked resume version: {{ application.resumeVersionId }}</small></p> }
          <div class="actions">
            @if (application.state === 'APPLIED') {
              <button pButton type="button" severity="secondary" (click)="requestNotApplied(application)">Remove Applied</button>
              <button pButton type="button" (click)="data.transition(application.id, 'INTERVIEW')">Interview</button>
            } @else if (application.state === 'OPENED' || application.state === 'SAVED') {
              <button pButton type="button" (click)="requestApplied(application)">Mark Applied</button>
            }
            <button pButton type="button" severity="secondary" (click)="showHistory(application.id)">History</button>
          </div>
          @if (data.events()[application.id]; as events) {
            <ol>@for (event of events; track event.id) { <li>{{ event.createdAt | date:'short' }} — {{ event.type }} {{ event.note ?? '' }}</li> }</ol>
          }
        </article>
      } @empty { <section class="card"><h2>No tracked applications</h2><p>Open an employer application link from a job detail page to start tracking.</p></section> }
    </section>
    @if (pending(); as application) {
      <section class="dialog-backdrop" role="presentation">
        <div class="dialog" role="dialog" aria-modal="true" aria-labelledby="confirmation-title">
          @if (pendingAction() === 'apply') {
            <h2 id="confirmation-title">Confirm you applied manually</h2>
            <p>Only confirm after submitting on the employer site. Select the exact approved resume version used.</p>
            <label for="resume-version">Approved resume</label>
            <select id="resume-version" [formControl]="resumeId">
              <option value="">Select a resume</option>
              @for (resume of data.resumes(); track resume.id) { <option [value]="resume.id">{{ resume.title }} — v{{ resume.version }}</option> }
            </select>
            <div class="actions"><button pButton type="button" [disabled]="!resumeId.value" (click)="confirmApplied(application)">Confirm Applied</button><button pButton type="button" severity="secondary" (click)="cancel()">Cancel</button></div>
          } @else {
            <h2 id="confirmation-title">Remove Applied status?</h2>
            <p>This adds a reversal event. It does not delete the original audit history.</p>
            <div class="actions"><button pButton type="button" (click)="confirmNotApplied(application)">Confirm removal</button><button pButton type="button" severity="secondary" (click)="cancel()">Cancel</button></div>
          }
        </div>
      </section>
    }
  `,
  styleUrl: './applications.component.scss',
})
export class ApplicationsComponent implements OnInit {
  readonly data = inject(ApplicationService);
  readonly pending = signal<ApplicationRecord | null>(null);
  readonly pendingAction = signal<'apply' | 'remove'>('apply');
  readonly resumeId = new FormControl('', { nonNullable: true });
  ngOnInit(): Promise<void> { return this.data.load(); }
  requestApplied(application: ApplicationRecord): void { this.pendingAction.set('apply'); this.pending.set(application); }
  requestNotApplied(application: ApplicationRecord): void { this.pendingAction.set('remove'); this.pending.set(application); }
  async confirmApplied(application: ApplicationRecord): Promise<void> { await this.data.applied(application.jobId, this.resumeId.value); this.cancel(); }
  async confirmNotApplied(application: ApplicationRecord): Promise<void> { await this.data.notApplied(application.id); this.cancel(); }
  cancel(): void { this.pending.set(null); this.resumeId.setValue(''); }
  showHistory(id: string): Promise<void> { return this.data.loadEvents(id); }
}
