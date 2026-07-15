import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { JobService } from './job.service';
import { JobsComponent } from './jobs.component';

describe('JobsComponent', () => {
  let fixture: ComponentFixture<JobsComponent>;
  const service = {
    page: signal({ content: [], page: 0, size: 25, totalElements: 0, totalPages: 0 }),
    detail: signal(null), loading: signal(false), search: vi.fn().mockResolvedValue(undefined),
    action: vi.fn(), load: vi.fn(), reanalyze: vi.fn(),
  };

  beforeEach(async () => {
    service.search.mockClear();
    await TestBed.configureTestingModule({
      imports: [JobsComponent],
      providers: [{ provide: JobService, useValue: service }, provideRouter([])],
    }).compileComponents();
    fixture = TestBed.createComponent(JobsComponent);
    fixture.detectChanges();
    await fixture.whenStable();
  });

  it('defaults to the personal junior AI and software search', () => {
    const text = fixture.nativeElement.textContent;
    expect(text).toContain('Junior AI & software roles');
    expect(text).toContain('Senior and unrelated business roles are excluded by default');
    expect(service.search).toHaveBeenCalledWith(expect.objectContaining({
      focus: 'PERSONAL_AI_SOFTWARE',
      experienceLevel: 'JUNIOR_ENTRY',
      section: 'ALL_AI',
    }));
  });
});
