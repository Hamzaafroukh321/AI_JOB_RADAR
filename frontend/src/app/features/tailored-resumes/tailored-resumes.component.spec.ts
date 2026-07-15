import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TailoredResumeService } from './tailored-resume.service';
import { TailoredResumesComponent } from './tailored-resumes.component';

describe('TailoredResumesComponent', () => {
  let fixture: ComponentFixture<TailoredResumesComponent>;
  const service = {
    variants: signal(['AI_ENGINEER', 'GENAI_RAG', 'JAVA_ANGULAR', 'AI_TRAINING', 'REMOTE_CONTRACT']),
    resumes: signal([{
      id: 'resume-1', jobId: 'job-1', variant: 'GENAI_RAG', title: 'AI Engineer', version: 1,
      versionId: 'version-1', status: 'DRAFT', previewHtml: '', contentSha256: 'abc', createdAt: '2026-07-14T00:00:00Z',
      content: {
        headline: { text: 'Generative AI Engineer', verifiedFactIds: ['fact-1'] },
        summary: { text: 'Built verified RAG systems', verifiedFactIds: ['fact-1'] },
        highlights: [{ text: 'Built verified RAG systems', verifiedFactIds: ['fact-1'] }],
        missingRequirements: ['Kubernetes'],
      },
    }]),
    loading: signal(false), load: vi.fn().mockResolvedValue(undefined), generate: vi.fn(),
    approve: vi.fn(), downloadUrl: vi.fn().mockReturnValue('/download'),
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TailoredResumesComponent],
      providers: [{ provide: TailoredResumeService, useValue: service }],
    }).compileComponents();
    fixture = TestBed.createComponent(TailoredResumesComponent);
    fixture.detectChanges();
    await fixture.whenStable();
  });

  it('shows evidence IDs and unsupported requirements separately', () => {
    const text = fixture.nativeElement.textContent;
    expect(text).toContain('Evidence: fact-1');
    expect(text).toContain('Missing / not claimed');
    expect(text).toContain('Kubernetes');
  });
});
