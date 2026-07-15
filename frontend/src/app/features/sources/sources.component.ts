import { DatePipe } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { JobSource } from './source.models';
import { SourceService } from './source.service';

@Component({
  selector: 'app-sources',
  imports: [ReactiveFormsModule, ButtonModule, DatePipe],
  template: `
    <header class="page-heading"><div><p class="eyebrow">Ingestion control</p><h1>Sources & fetch health</h1><p>Only approved public APIs and feeds can be enabled. A failed source never expires jobs from another source.</p></div></header>
    @if (message()) { <p class="message" role="status">{{ message() }}</p> }

    <section class="panel">
      <h2>Registered sources</h2>
      <div class="source-grid">
        @for (source of data.sources(); track source.id) {
          <article class="source-card">
            <div><span class="type">{{ source.type }}</span><h3>{{ source.displayName }}</h3></div>
            <dl><div><dt>Status</dt><dd>{{ source.enabled ? 'Enabled' : 'Disabled' }} · {{ source.termsReviewStatus }}</dd></div><div><dt>Latest run</dt><dd>{{ source.lastRunStatus ?? 'Never' }}</dd></div><div><dt>Freshness</dt><dd>{{ source.lastSuccessfulAt ? (source.lastSuccessfulAt | date:'medium') : 'No successful fetch' }}</dd></div><div><dt>Canonical links</dt><dd>{{ source.totalJobs }}</dd></div></dl>
            <div class="actions">
              @if (source.type !== 'MANUAL') {
                <button pButton type="button" severity="secondary" (click)="toggle(source)">{{ source.enabled ? 'Disable' : 'Enable' }}</button>
                <button pButton type="button" [disabled]="!source.enabled" (click)="fetch(source)">Fetch now</button>
              }
              <button pButton type="button" severity="secondary" (click)="data.loadRuns(source.id)">View runs</button>
            </div>
          </article>
        }
      </div>
    </section>

    <section class="columns">
      <form class="panel" [formGroup]="sourceForm" (ngSubmit)="createSource()">
        <h2>Add approved source</h2>
        <label>Key<input formControlName="key" /></label><label>Display name / company<input formControlName="displayName" /></label>
        <label>Connector<select formControlName="type"><option>GREENHOUSE</option><option>LEVER</option><option>ADZUNA</option><option>JOBICY</option><option>REMOTIVE</option><option>ARBEITNOW</option></select></label>
        <label>Board token, site, or search tag<input formControlName="identifier" /><small>No passwords or API keys. Jobicy uses its approved public remote-jobs API; Adzuna credentials must be configured as environment-variable references by an operator.</small></label>
        <label>Terms URL<input formControlName="termsUrl" type="url" /></label>
        <label>Terms review<select formControlName="termsReviewStatus"><option>REVIEW_REQUIRED</option><option>APPROVED</option><option>DISABLED</option></select></label>
        <button pButton type="submit" [disabled]="sourceForm.invalid">Register disabled source</button>
      </form>

      <form class="panel" [formGroup]="manualForm" (ngSubmit)="importManual()">
        <h2>Manual job import</h2><p>Paste the description. URL retrieval is never required and no login is attempted.</p>
        <label>Title<input formControlName="title" /></label><label>Company<input formControlName="company" /></label><label>Location<input formControlName="location" /></label>
        <label>Source URL<input formControlName="sourceUrl" type="url" /></label><label>Application URL<input formControlName="applicationUrl" type="url" /></label>
        <label>Description<textarea formControlName="description" rows="9"></textarea></label>
        <button pButton type="submit" [disabled]="manualForm.invalid">Import privately</button>
      </form>
    </section>

    @if (data.runs().length) { <section class="panel"><h2>Recent runs</h2><div class="table-wrap"><table><thead><tr><th>Started</th><th>Trigger</th><th>Status</th><th>Received</th><th>New</th><th>Updated</th><th>Deduped</th><th>Safe error</th></tr></thead><tbody>@for (run of data.runs(); track run.id) {<tr><td>{{ run.startedAt | date:'short' }}</td><td>{{ run.triggerType }}</td><td>{{ run.status }}</td><td>{{ run.received }}</td><td>{{ run.inserted }}</td><td>{{ run.updated }}</td><td>{{ run.deduplicated }}</td><td>{{ run.safeError ?? '—' }}</td></tr>}</tbody></table></div></section> }
  `,
  styleUrl: './sources.component.scss',
})
export class SourcesComponent implements OnInit {
  readonly data = inject(SourceService);
  private readonly forms = inject(FormBuilder);
  readonly message = signal('');
  readonly sourceForm = this.forms.nonNullable.group({
    key: ['', Validators.required], displayName: ['', Validators.required], type: ['GREENHOUSE', Validators.required],
    identifier: ['', Validators.required], termsUrl: ['', Validators.required], termsReviewStatus: ['REVIEW_REQUIRED', Validators.required],
  });
  readonly manualForm = this.forms.nonNullable.group({
    title: ['', Validators.required], company: ['', Validators.required], location: [''], sourceUrl: [''], applicationUrl: [''],
    description: ['', Validators.required], employmentType: [''], workplaceMode: [''],
  });

  ngOnInit(): Promise<void> { return this.data.load(); }
  async toggle(source: JobSource): Promise<void> { await this.data.setEnabled(source, !source.enabled); }
  async fetch(source: JobSource): Promise<void> { await this.data.fetch(source); this.message.set('Fetch completed. Review its run status below.'); }
  async createSource(): Promise<void> {
    if (this.sourceForm.invalid) return;
    const value = this.sourceForm.getRawValue();
    const configuration = value.type === 'GREENHOUSE' ? { boardToken: value.identifier } : value.type === 'LEVER' ? { site: value.identifier } : value.type === 'JOBICY' ? { tag: value.identifier, count: '100' } : value.type === 'REMOTIVE' ? { category: value.identifier, limit: '100' } : value.type === 'ARBEITNOW' ? { pages: value.identifier || '5' } : { query: value.identifier, countryCode: 'gb' };
    await this.data.create({ key: value.key, displayName: value.displayName, type: value.type, termsUrl: value.termsUrl, termsReviewStatus: value.termsReviewStatus, credentialsRequired: value.type === 'ADZUNA', timezone: 'Africa/Casablanca', priority: 100, parserVersion: '1.0.0', configuration });
    this.sourceForm.reset({ key: '', displayName: '', type: 'GREENHOUSE', identifier: '', termsUrl: '', termsReviewStatus: 'REVIEW_REQUIRED' });
    this.message.set('Source registered disabled. Approve terms before enabling it.');
  }
  async importManual(): Promise<void> {
    if (this.manualForm.invalid) return;
    const result = await this.data.importManual(this.manualForm.getRawValue());
    this.manualForm.reset({ title: '', company: '', location: '', sourceUrl: '', applicationUrl: '', description: '', employmentType: '', workplaceMode: '' });
    this.message.set(result.created ? 'Private job imported.' : 'Matching private job already existed; its source occurrence was updated.');
    await this.data.load();
  }
}
