import { DatePipe, KeyValuePipe, PercentPipe, TitleCasePipe } from '@angular/common';
import { Component, inject, OnInit } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { JobService } from './job.service';

@Component({
  selector: 'app-job-detail',
  imports: [RouterLink, ButtonModule, DatePipe, PercentPipe, KeyValuePipe, TitleCasePipe],
  template: `
    <a routerLink="/jobs">&larr; Back to jobs</a>
    @if (data.detail(); as detail) {
      <header class="hero">
        <div>
          <p>{{ detail.job.company }} &middot; {{ detail.job.location ?? 'Location unknown' }}</p>
          <h1>{{ detail.job.originalTitle }}</h1>
          <div class="badges">
            <span>{{ detail.job.aiRelevance }} AI relevance</span>
            <span>{{ detail.job.annotationRelevance }} annotation relevance</span>
            <span>{{ detail.job.workplaceMode }} &middot; {{ detail.job.remoteScope }}</span>
            <span>Morocco: {{ detail.job.moroccoEligibility }}</span>
          </div>
        </div>
        <div class="actions">
          <button pButton type="button" (click)="save()">{{ detail.job.saved ? 'Unsave' : 'Save' }}</button>
          @if (detail.applicationUrl) {
            <button pButton type="button" (click)="openApplication()">Open original job</button>
          }
        </div>
      </header>

      @if (data.match(); as match) {
        <section class="panel match-panel" aria-labelledby="match-heading">
          <div class="match-summary">
            <div><p class="score">{{ match.overallScore }}</p><small>match score</small></div>
            <div>
              <h2 id="match-heading">{{ match.eligibilityState | titlecase }}</h2>
              <p>{{ match.rationale }}</p>
              <p>{{ match.recommendedAction }}</p>
            </div>
          </div>
          <h3>Score components</h3>
          <div class="components">
            @for (component of match.componentScores | keyvalue; track component.key) {
              <label><span>{{ component.key | titlecase }}</span><meter min="0" max="100" [value]="component.value">{{ component.value }}</meter><strong>{{ component.value }}</strong></label>
            }
          </div>
          @if (match.strongMatches.length) {
            <h3>Strong matches backed by verified facts</h3>
            @for (item of match.strongMatches; track item.label) {
              <article class="evidence"><strong>{{ item.label }}</strong><q>{{ item.jobEvidence }}</q><small>Verified fact references: {{ item.verifiedFactIds.join(', ') }}</small></article>
            }
          }
          @if (match.missingRequirements.length) {
            <h3>Missing evidence</h3>
            <ul>@for (item of match.missingRequirements; track item) { <li>{{ item }}</li> }</ul>
          }
          @if (match.hardBlockers.length) {
            <div class="blockers"><h3>Hard blockers</h3><ul>@for (item of match.hardBlockers; track item) { <li>{{ item }}</li> }</ul></div>
          }
          @if (match.userQuestions.length) {
            <h3>Questions to resolve</h3>
            <ul>@for (item of match.userQuestions; track item) { <li>{{ item }}</li> }</ul>
          }
          <div class="feedback">
            <button pButton type="button" severity="secondary" (click)="refreshMatch()">Recompute match</button>
            <button pButton type="button" severity="secondary" (click)="feedback('HELPFUL')">Helpful</button>
            <button pButton type="button" severity="secondary" (click)="feedback('NOT_HELPFUL')">Not helpful</button>
          </div>
        </section>
      } @else {
        <section class="panel"><h2>Match unavailable</h2><p>A structured analysis and verified profile facts are required.</p></section>
      }

      @if (detail.analysis; as analysis) {
        <section class="panel">
          <div class="analysis-head"><h2>Structured analysis</h2><span>{{ detail.analysisValidationStatus }} &middot; {{ analysis.overallConfidence | percent:'1.0-0' }} confidence</span></div>
          <p>{{ analysis.jobSummary }}</p>
          @if (analysis.warnings.length) {
            <div class="warning"><strong>Review needed</strong>@for (warning of analysis.warnings; track warning) { <p>{{ warning }}</p> }</div>
          }
          <h3>Technologies</h3><p>{{ analysis.technologies.join(' · ') || 'None extracted' }}</p>
          <h3>Evidence</h3>
          @for (item of analysis.responsibilities; track item.text) { <blockquote>{{ item.evidence }}</blockquote> }
          @for (item of analysis.mustHaveRequirements; track item.requirement) {
            <article class="requirement"><strong>{{ item.requirement }}</strong><span>{{ item.confidence | percent:'1.0-0' }}</span><q>{{ item.evidence }}</q></article>
          }
          <button pButton type="button" severity="secondary" (click)="reanalyze()">Reanalyze</button>
        </section>
      } @else {
        <section class="panel"><h2>Analysis unavailable</h2><p>The deterministic fields remain usable. Retry analysis safely.</p><button pButton type="button" (click)="reanalyze()">Analyze</button></section>
      }
      <section class="panel"><h2>Original description</h2><p class="description">{{ detail.description }}</p><p><small>Source verified {{ detail.job.lastVerifiedAt | date:'medium' }}. Job text is untrusted and never treated as system instructions.</small></p></section>
    }
  `,
  styleUrl: './job-detail.component.scss',
})
export class JobDetailComponent implements OnInit {
  readonly data = inject(JobService);
  private readonly route = inject(ActivatedRoute);
  private id = '';

  async ngOnInit(): Promise<void> {
    this.id = this.route.snapshot.paramMap.get('id') ?? '';
    await this.data.load(this.id);
    try { await this.data.loadMatch(this.id); } catch { this.data.match.set(null); }
  }

  async save(): Promise<void> {
    const job = this.data.detail()?.job;
    if (!job) return;
    await this.data.action(this.id, job.saved ? 'unsave' : 'save');
    await this.data.load(this.id);
  }

  reanalyze(): Promise<void> { return this.data.reanalyze(this.id); }
  refreshMatch(): Promise<void> { return this.data.loadMatch(this.id, true); }
  feedback(type: string): Promise<void> { return this.data.feedback(this.id, type); }
  async openApplication(): Promise<void> {
    const url = await this.data.openApplication(this.id);
    window.open(url, '_blank', 'noopener,noreferrer');
  }
}
