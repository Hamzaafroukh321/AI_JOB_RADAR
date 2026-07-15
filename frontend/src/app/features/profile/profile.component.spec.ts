import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ProfileComponent } from './profile.component';
import { ProfileService } from './profile.service';

describe('ProfileComponent', () => {
  let fixture: ComponentFixture<ProfileComponent>;
  const service = {
    profile: signal({ id: 'p1', headline: null, homeCountryCode: null, homeRegion: null, currentCity: null, relocationPreference: 'UNKNOWN', sponsorshipRequired: null, activeMasterResumeId: null, profileVersion: 3 }),
    preferences: signal({ targetRoleFamilies: [], targetSeniority: [], preferredRegions: [], preferredCountries: [], excludedCountries: [], employmentTypes: [], workplaceModes: [], minimumSalary: null, contractAllowed: null, freelanceAllowed: null, annotationWorkAllowed: null, temporaryWorkAllowed: null, dailyDigestEnabled: false, dailyDigestTime: null, minimumMatchScore: 60, freshnessDays: 14, excludedCompanies: [], excludedKeywords: [], includedKeywords: [], workingHours: null, preferredCompanySizes: [], preferredIndustries: [] }),
    resumes: signal([]), facts: signal([]), languages: signal([]), authorizations: signal([]), loading: signal(false),
    load: vi.fn().mockResolvedValue(undefined), upload: vi.fn(), activate: vi.fn(), updateResumeLanguage: vi.fn(), importSeed: vi.fn(), transition: vi.fn(),
    editFact: vi.fn(), updateProfile: vi.fn(), updatePreferences: vi.fn(), addLanguage: vi.fn(), deleteLanguage: vi.fn(),
    addAuthorization: vi.fn(), deleteAuthorization: vi.fn(),
  };

  beforeEach(async () => {
    vi.clearAllMocks();
    await TestBed.configureTestingModule({ imports: [ProfileComponent], providers: [{ provide: ProfileService, useValue: service }] }).compileComponents();
    fixture = TestBed.createComponent(ProfileComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  });

  it('explains the verified-fact boundary and keeps unknown eligibility explicit', () => {
    expect(fixture.nativeElement.textContent).toContain('Only facts you explicitly verify');
    expect(fixture.nativeElement.textContent).toContain('Never inferred');
    expect(fixture.nativeElement.textContent).toContain('remains unknown until you confirm');
    expect(fixture.nativeElement.textContent).toContain('Profile version 3');
  });

  it('uploads a resume with the language selected by the user', async () => {
    const component = fixture.componentInstance;
    const file = new File(['resume'], 'resume-fr.pdf', { type: 'application/pdf' });
    component.selectedFile.set(file);
    component.uploadLanguage.set('fr');

    await component.upload();

    expect(service.upload).toHaveBeenCalledWith(file, 'resume-fr', 'fr');
    expect(component.message()).toContain('French resume');
  });
});
