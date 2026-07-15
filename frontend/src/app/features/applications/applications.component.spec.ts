import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ApplicationService } from './application.service';
import { ApplicationRecord } from './application.models';
import { ApplicationsComponent } from './applications.component';

describe('ApplicationsComponent', () => {
  let fixture: ComponentFixture<ApplicationsComponent>;
  const opened: ApplicationRecord = {
    id: 'app-1', jobId: 'job-1', title: 'AI Engineer', company: 'Acme', state: 'OPENED',
    resumeVersionId: null, appliedAt: null, updatedAt: '2026-07-14T00:00:00Z',
  };
  const service = {
    applications: signal([opened]), resumes: signal([{
      id: 'resume-1', jobId: 'job-1', variant: 'AI_ENGINEER', title: 'AI Engineer', version: 2,
      versionId: 'version-2', status: 'APPROVED', content: {}, previewHtml: '', contentSha256: 'abc', createdAt: '',
    }]), events: signal({}), loading: signal(false),
    load: vi.fn().mockResolvedValue(undefined), applied: vi.fn().mockResolvedValue(undefined),
    notApplied: vi.fn().mockResolvedValue(undefined), transition: vi.fn(), loadEvents: vi.fn(),
  };
  beforeEach(async () => {
    service.applications.set([opened]);
    vi.clearAllMocks();
    await TestBed.configureTestingModule({
      imports: [ApplicationsComponent],
      providers: [{ provide: ApplicationService, useValue: service }],
    }).compileComponents();
    fixture = TestBed.createComponent(ApplicationsComponent);
    fixture.detectChanges();
    await fixture.whenStable();
  });

  it('requires Mark Applied then explicit resume confirmation', async () => {
    const buttons = [...fixture.nativeElement.querySelectorAll('button')] as HTMLButtonElement[];
    buttons.find(button => button.textContent?.includes('Mark Applied'))?.click();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Confirm you applied manually');
    fixture.componentInstance.resumeId.setValue('resume-1');
    fixture.detectChanges();
    const confirm = [...fixture.nativeElement.querySelectorAll('button')]
      .find((button: HTMLButtonElement) => button.textContent?.includes('Confirm Applied')) as HTMLButtonElement;
    confirm.click();
    await fixture.whenStable();
    expect(service.applied).toHaveBeenCalledWith('job-1', 'resume-1');
  });

  it('requires a separate confirmation before removing Applied', () => {
    service.applications.set([{ ...opened, state: 'APPLIED', resumeVersionId: 'version-2' }]);
    fixture.detectChanges();
    const remove = [...fixture.nativeElement.querySelectorAll('button')]
      .find((button: HTMLButtonElement) => button.textContent?.includes('Remove Applied')) as HTMLButtonElement;
    remove.click();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Remove Applied status?');
    expect(service.notApplied).not.toHaveBeenCalled();
  });
});
