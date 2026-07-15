import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SourceService } from './source.service';
import { SourcesComponent } from './sources.component';

describe('SourcesComponent', () => {
  let fixture: ComponentFixture<SourcesComponent>;
  const service = {
    sources: signal([{ id: 'manual', key: 'manual', displayName: 'Manual import', type: 'MANUAL', termsReviewStatus: 'APPROVED', enabled: true, parserVersion: '1.0.0', consecutiveFailures: 0, lastAttemptedAt: null, lastSuccessfulAt: null, lastRunStatus: null, totalJobs: 0 }]),
    runs: signal([]), loading: signal(false), load: vi.fn().mockResolvedValue(undefined), create: vi.fn(),
    setEnabled: vi.fn(), fetch: vi.fn(), loadRuns: vi.fn(), importManual: vi.fn(),
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [SourcesComponent], providers: [{ provide: SourceService, useValue: service }] }).compileComponents();
    fixture = TestBed.createComponent(SourcesComponent);
    fixture.detectChanges();
    await fixture.whenStable();
  });

  it('makes compliance and manual fallback visible', () => {
    const text = fixture.nativeElement.textContent;
    expect(text).toContain('Only approved public APIs and feeds');
    expect(text).toContain('Paste the description');
    expect(text).toContain('no login is attempted');
    expect(text).not.toContain('Fetch now');
  });
});
