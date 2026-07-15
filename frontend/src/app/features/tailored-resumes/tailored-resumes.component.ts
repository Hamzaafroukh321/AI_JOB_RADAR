import { DatePipe, TitleCasePipe } from '@angular/common';
import { Component, inject, OnInit } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { TailoredResumeService } from './tailored-resume.service';

@Component({
  selector: 'app-tailored-resumes',
  imports: [ReactiveFormsModule, ButtonModule, DatePipe, TitleCasePipe],
  template: `
    <header class="page-heading">
      <div><p class="eyebrow">Truth-grounded documents</p><h1>Tailored resumes</h1>
        <p>Every displayed claim cites verified profile facts. Missing requirements remain visible and are never added as skills.</p></div>
    </header>
    <section class="panel">
      <h2>Generate a version</h2>
      <form [formGroup]="form" (ngSubmit)="generate()">
        <label for="job-id">Job ID</label><input id="job-id" formControlName="jobId" autocomplete="off">
        <label for="variant">Candidate variant</label>
        <select id="variant" formControlName="variant">
          @for (variant of data.variants(); track variant) { <option [value]="variant">{{ variant | titlecase }}</option> }
        </select>
        <button pButton type="submit" [disabled]="form.invalid || data.loading()">Generate fact-grounded draft</button>
      </form>
    </section>
    <section class="cards" aria-live="polite">
      @for (resume of data.resumes(); track resume.id) {
        <article class="panel">
          <div class="heading"><div><h2>{{ resume.title }}</h2><p>{{ resume.variant | titlecase }} &middot; version {{ resume.version }} &middot; {{ resume.createdAt | date:'medium' }}</p></div><span class="status">{{ resume.status }}</span></div>
          <h3>{{ resume.content.headline.text }}</h3>
          <p>{{ resume.content.summary.text }}</p>
          <h4>Verified highlights</h4>
          @for (claim of resume.content.highlights; track claim.text) {
            <div class="claim"><span>{{ claim.text }}</span><small>Evidence: {{ claim.verifiedFactIds.join(', ') }}</small></div>
          }
          @if (resume.content.missingRequirements.length) {
            <div class="missing"><h4>Missing / not claimed</h4><ul>@for (item of resume.content.missingRequirements; track item) { <li>{{ item }}</li> }</ul></div>
          }
          <div class="actions">
            @if (resume.status === 'DRAFT') { <button pButton type="button" (click)="data.approve(resume.id)">Approve version</button> }
            <a pButton [href]="data.downloadUrl(resume.id, 'pdf')">Download PDF</a>
            <a pButton [href]="data.downloadUrl(resume.id, 'docx')">Download DOCX</a>
          </div>
          <details><summary>Version evidence</summary><code>{{ resume.contentSha256 }}</code><p>Changes create a new version; approved and applied artifacts remain immutable.</p></details>
        </article>
      } @empty { <section class="panel"><h2>No tailored resumes yet</h2><p>Open a job, verify profile facts, then generate one of the five candidate variants.</p></section> }
    </section>
  `,
  styleUrl: './tailored-resumes.component.scss',
})
export class TailoredResumesComponent implements OnInit {
  readonly data = inject(TailoredResumeService);
  readonly form = new FormGroup({
    jobId: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    variant: new FormControl('AI_ENGINEER', { nonNullable: true, validators: [Validators.required] }),
  });
  ngOnInit(): Promise<void> { return this.data.load(); }
  generate(): Promise<void> { return this.data.generate(this.form.controls.jobId.value, this.form.controls.variant.value); }
}
