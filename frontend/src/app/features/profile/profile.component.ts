import { DatePipe } from '@angular/common';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { CandidateFact, CandidatePreferences } from './profile.models';
import { ProfileService } from './profile.service';

@Component({
  selector: 'app-profile',
  imports: [ReactiveFormsModule, ButtonModule, DatePipe],
  templateUrl: './profile.component.html',
  styleUrl: './profile.component.scss',
})
export class ProfileComponent implements OnInit {
  readonly data = inject(ProfileService);
  private readonly forms = inject(FormBuilder);
  readonly selectedFile = signal<File | null>(null);
  readonly uploadLanguage = signal<'en' | 'fr'>('en');
  readonly uploading = signal(false);
  readonly editingFact = signal<CandidateFact | null>(null);
  readonly editStatement = signal('');
  readonly message = signal('');
  readonly pendingFacts = computed(() => this.data.facts().filter((fact) => fact.verificationStatus === 'PROPOSED' || fact.verificationStatus === 'NEEDS_CLARIFICATION'));
  readonly verifiedFacts = computed(() => this.data.facts().filter((fact) => fact.verificationStatus === 'VERIFIED'));

  readonly profileForm = this.forms.nonNullable.group({
    headline: [''], homeCountryCode: [''], homeRegion: [''], currentCity: [''],
    relocationPreference: ['UNKNOWN'], sponsorshipRequired: this.forms.control<boolean | null>(null),
  });
  readonly preferenceForm = this.forms.nonNullable.group({
    targetRoleFamilies: [''], preferredRegions: [''], preferredCountries: [''],
    contractAllowed: this.forms.control<boolean | null>(null), freelanceAllowed: this.forms.control<boolean | null>(null),
    annotationWorkAllowed: this.forms.control<boolean | null>(null), temporaryWorkAllowed: this.forms.control<boolean | null>(null),
    minimumMatchScore: [60, [Validators.min(0), Validators.max(100)]], freshnessDays: [14, [Validators.min(1), Validators.max(365)]],
  });
  readonly languageForm = this.forms.nonNullable.group({
    languageCode: ['', Validators.required], spokenLevel: ['UNKNOWN'], writtenLevel: ['UNKNOWN'], professionalUse: false,
  });
  readonly authorizationForm = this.forms.nonNullable.group({
    countryCode: ['', [Validators.required, Validators.pattern(/[A-Za-z]{2}/)]], authorizationStatus: ['UNKNOWN'], sponsorshipNeeded: false, notes: [''],
  });

  async ngOnInit(): Promise<void> {
    await this.data.load();
    const profile = this.data.profile();
    if (profile) this.profileForm.patchValue({
      headline: profile.headline ?? '', homeCountryCode: profile.homeCountryCode ?? '', homeRegion: profile.homeRegion ?? '',
      currentCity: profile.currentCity ?? '', relocationPreference: profile.relocationPreference,
      sponsorshipRequired: profile.sponsorshipRequired,
    });
    const preferences = this.data.preferences();
    if (preferences) this.preferenceForm.patchValue({
      targetRoleFamilies: preferences.targetRoleFamilies.join(', '), preferredRegions: preferences.preferredRegions.join(', '),
      preferredCountries: preferences.preferredCountries.join(', '), contractAllowed: preferences.contractAllowed,
      freelanceAllowed: preferences.freelanceAllowed, annotationWorkAllowed: preferences.annotationWorkAllowed,
      temporaryWorkAllowed: preferences.temporaryWorkAllowed, minimumMatchScore: preferences.minimumMatchScore,
      freshnessDays: preferences.freshnessDays,
    });
  }

  chooseFile(event: Event): void {
    this.selectedFile.set((event.target as HTMLInputElement).files?.item(0) ?? null);
  }

  async upload(): Promise<void> {
    const file = this.selectedFile();
    if (!file) return;
    this.uploading.set(true);
    try {
      await this.data.upload(file, file.name.replace(/\.(pdf|docx)$/i, ''), this.uploadLanguage());
      this.selectedFile.set(null);
      this.message.set(`${this.uploadLanguage() === 'fr' ? 'French' : 'English'} resume stored privately and parsed. Review each proposed fact before it can influence matching or generated resumes.`);
    } catch {
      this.message.set('The resume was not stored. It may be a duplicate, invalid, or over the upload limit. Choose the other resume and try again.');
    } finally {
      this.uploading.set(false);
    }
  }

  async importSeed(): Promise<void> {
    const count = await this.data.importSeed();
    this.message.set(count ? `${count} seed facts added for review.` : 'Seed facts were already imported.');
  }

  async updateResumeLanguage(id: string, event: Event): Promise<void> {
    const languageCode = (event.target as HTMLSelectElement).value as 'en' | 'fr';
    await this.data.updateResumeLanguage(id, languageCode);
    this.message.set(`Resume language updated to ${languageCode === 'fr' ? 'French' : 'English'}.`);
  }

  startEdit(fact: CandidateFact): void { this.editingFact.set(fact); this.editStatement.set(fact.statement); }
  cancelEdit(): void { this.editingFact.set(null); this.editStatement.set(''); }
  async saveEdit(): Promise<void> {
    const fact = this.editingFact();
    if (!fact || !this.editStatement().trim()) return;
    await this.data.editFact(fact, this.editStatement().trim());
    this.cancelEdit();
  }

  async saveProfile(): Promise<void> {
    await this.data.updateProfile(this.profileForm.getRawValue());
    this.message.set('Profile saved and versioned.');
  }

  async savePreferences(): Promise<void> {
    const current = this.data.preferences();
    if (!current || this.preferenceForm.invalid) return;
    const value = this.preferenceForm.getRawValue();
    const updated: CandidatePreferences = {
      ...current,
      targetRoleFamilies: this.list(value.targetRoleFamilies), preferredRegions: this.list(value.preferredRegions),
      preferredCountries: this.list(value.preferredCountries).map((country) => country.toUpperCase()),
      contractAllowed: value.contractAllowed, freelanceAllowed: value.freelanceAllowed,
      annotationWorkAllowed: value.annotationWorkAllowed, temporaryWorkAllowed: value.temporaryWorkAllowed,
      minimumMatchScore: value.minimumMatchScore, freshnessDays: value.freshnessDays,
    };
    await this.data.updatePreferences(updated);
    this.message.set('Preferences saved and profile version advanced.');
  }

  async addLanguage(): Promise<void> {
    if (this.languageForm.invalid) return;
    await this.data.addLanguage(this.languageForm.getRawValue());
    this.languageForm.reset({ languageCode: '', spokenLevel: 'UNKNOWN', writtenLevel: 'UNKNOWN', professionalUse: false });
  }

  async addAuthorization(): Promise<void> {
    if (this.authorizationForm.invalid) return;
    await this.data.addAuthorization({ ...this.authorizationForm.getRawValue(), expiresAt: null });
    this.authorizationForm.reset({ countryCode: '', authorizationStatus: 'UNKNOWN', sponsorshipNeeded: false, notes: '' });
  }

  factSource(fact: CandidateFact): string {
    if (!fact.masterResumeId) return 'Development seed or manually entered fact';
    const resume = this.data.resumes().find((item) => item.id === fact.masterResumeId);
    return resume ? `${resume.name} (${resume.languageCode.toUpperCase()})` : 'Uploaded resume';
  }

  private list(value: string): string[] { return value.split(',').map((item) => item.trim()).filter(Boolean); }
}
