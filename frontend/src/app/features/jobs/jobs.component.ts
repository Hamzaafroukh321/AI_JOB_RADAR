import { DatePipe } from '@angular/common';
import { Component, inject, OnInit } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { JobCard } from './job.models';
import { JobService } from './job.service';

@Component({
  selector: 'app-jobs',
  imports: [ReactiveFormsModule, RouterLink, ButtonModule, DatePipe],
  template: `
    <header class="hero">
      <div>
        <p class="eyebrow">Personal job radar</p>
        <h1>Junior AI & software roles</h1>
        <p>
          Focused on AI engineering, LLM/RAG, Java/Spring, Angular, Python, prompt evaluation,
          and technical AI training. Senior and unrelated business roles are excluded by default.
        </p>
      </div>
      <div class="result-count" aria-live="polite">
        <strong>{{ data.page()?.totalElements ?? 0 }}</strong>
        <span>matching roles</span>
      </div>
    </header>

    <form class="search-panel" [formGroup]="filters" (ngSubmit)="search()">
      <div class="search-row">
        <label class="query-field">
          <span>Search within results</span>
          <input formControlName="q" placeholder="Try Java, RAG, LLM, Angular…" />
        </label>
        <button pButton type="submit" [disabled]="data.loading()">Search</button>
        <button pButton type="button" severity="secondary" (click)="reset()">Reset</button>
      </div>

      <div class="filter-grid">
        <label><span>Role focus</span><select formControlName="focus" (change)="search()"><option value="PERSONAL_AI_SOFTWARE">My AI & software profile</option><option value="AI_ALL">All AI-related roles</option></select></label>
        <label><span>Experience</span><select formControlName="experienceLevel" (change)="search()"><option value="JUNIOR_ENTRY">Junior & entry level</option><option value="EXCLUDE_SENIOR">Any non-senior role</option><option value="JUNIOR">Explicitly junior only</option><option value="INTERN">Internships</option><option value="ALL">All levels</option></select></label>
        <label><span>Region</span><select formControlName="section" (change)="search()"><option value="ALL_AI">All regions</option><option value="EUROPE">Europe</option><option value="MIDDLE_EAST">Middle East</option><option value="WORLDWIDE_REMOTE">Worldwide from Morocco</option><option value="MOROCCO">Morocco</option><option value="AI_TRAINING_DATA">AI training & evaluation</option></select></label>
        <label><span>Workplace</span><select formControlName="workplaceMode" (change)="search()"><option value="">Any</option><option value="REMOTE">Remote</option><option value="HYBRID">Hybrid</option><option value="ONSITE">On-site</option></select></label>
        <label><span>Sort</span><select formControlName="sort" (change)="search()"><option value="NEWEST">Newest posted</option><option value="DISCOVERED">Recently discovered</option><option value="COMPANY">Company</option><option value="SALARY">Highest salary</option></select></label>
      </div>
      <p class="filter-note">“Remote” is not automatically worldwide. Location eligibility remains visible on every card.</p>
    </form>

    @if (data.loading()) {
      <section class="state" aria-live="polite">Searching verified sources…</section>
    } @else if (!data.page()?.content?.length) {
      <section class="state empty"><h2>No junior roles match these filters</h2><p>Try “Any non-senior role,” another region, or a broader role focus.</p><button pButton type="button" severity="secondary" (click)="reset()">Reset filters</button></section>
    } @else {
      <section class="cards" aria-label="Job search results">
        @for (job of data.page()?.content ?? []; track job.id) {
          <article class="job-card">
            <div class="job-main">
              <div class="badges" aria-label="Job classifications"><span class="level">{{ job.seniority }}</span><span [class.high-ai]="job.aiRelevance === 'HIGH'">{{ job.aiRelevance }} AI</span><span>{{ job.workplaceMode }}</span><span>{{ job.remoteScope }}</span></div>
              <h2><a [routerLink]="['/jobs', job.id]">{{ job.originalTitle }}</a></h2>
              <p class="company">{{ job.company }} <span>•</span> {{ job.location ?? 'Location not stated' }}</p>
              <dl class="facts"><div><dt>Morocco eligibility</dt><dd>{{ job.moroccoEligibility }}</dd></div><div><dt>Type</dt><dd>{{ job.employmentType }}</dd></div>@if (job.salaryMax) {<div><dt>Salary</dt><dd>{{ job.salaryMin ?? '—' }}–{{ job.salaryMax }} {{ job.salaryCurrency }}</dd></div>}</dl>
            </div>
            <footer><span>{{ job.source ?? 'Direct import' }} • checked {{ job.lastVerifiedAt | date: 'mediumDate' }}</span><div class="actions"><a pButton severity="secondary" [routerLink]="['/jobs', job.id]">View evidence</a><button pButton type="button" severity="secondary" (click)="save(job)">{{ job.saved ? 'Saved' : 'Save' }}</button><button pButton type="button" severity="secondary" (click)="hide(job)">Hide</button></div></footer>
          </article>
        }
      </section>

      @if ((data.page()?.totalPages ?? 0) > 1) {
        <nav class="pagination" aria-label="Job result pages"><button pButton type="button" severity="secondary" [disabled]="(data.page()?.page ?? 0) === 0" (click)="goToPage((data.page()?.page ?? 0) - 1)">Previous</button><span>Page {{ (data.page()?.page ?? 0) + 1 }} of {{ data.page()?.totalPages }}</span><button pButton type="button" severity="secondary" [disabled]="(data.page()?.page ?? 0) + 1 >= (data.page()?.totalPages ?? 0)" (click)="goToPage((data.page()?.page ?? 0) + 1)">Next</button></nav>
      }
    }
  `,
  styleUrl: './jobs.component.scss',
})
export class JobsComponent implements OnInit {
  readonly data = inject(JobService);
  private readonly forms = inject(FormBuilder);
  readonly filters = this.forms.nonNullable.group({ q: [''], focus: ['PERSONAL_AI_SOFTWARE'], experienceLevel: ['JUNIOR_ENTRY'], section: ['ALL_AI'], workplaceMode: [''], sort: ['NEWEST'] });

  ngOnInit(): Promise<void> { return this.search(); }
  search(page = 0): Promise<void> { return this.data.search({ ...this.filters.getRawValue(), page, size: 25 }); }
  reset(): Promise<void> { this.filters.reset({ q: '', focus: 'PERSONAL_AI_SOFTWARE', experienceLevel: 'JUNIOR_ENTRY', section: 'ALL_AI', workplaceMode: '', sort: 'NEWEST' }); return this.search(); }
  goToPage(page: number): Promise<void> { return this.search(Math.max(0, page)); }
  async save(job: JobCard): Promise<void> { await this.data.action(job.id, job.saved ? 'unsave' : 'save'); await this.search(this.data.page()?.page ?? 0); }
  async hide(job: JobCard): Promise<void> { await this.data.action(job.id, 'hide'); await this.search(this.data.page()?.page ?? 0); }
}
