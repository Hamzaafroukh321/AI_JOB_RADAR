import { HttpClient } from '@angular/common/http';
import { inject, Injectable, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import {
  CandidateAuthorization,
  CandidateFact,
  CandidateLanguage,
  CandidatePreferences,
  CandidateProfile,
  MasterResume,
} from './profile.models';

@Injectable({ providedIn: 'root' })
export class ProfileService {
  private readonly http = inject(HttpClient);
  readonly profile = signal<CandidateProfile | null>(null);
  readonly preferences = signal<CandidatePreferences | null>(null);
  readonly resumes = signal<MasterResume[]>([]);
  readonly facts = signal<CandidateFact[]>([]);
  readonly languages = signal<CandidateLanguage[]>([]);
  readonly authorizations = signal<CandidateAuthorization[]>([]);
  readonly loading = signal(false);

  async load(): Promise<void> {
    this.loading.set(true);
    try {
      const [profile, preferences, resumes, facts, languages, authorizations] = await Promise.all([
        firstValueFrom(this.http.get<CandidateProfile>('/api/v1/profile')),
        firstValueFrom(this.http.get<CandidatePreferences>('/api/v1/profile/preferences')),
        firstValueFrom(this.http.get<MasterResume[]>('/api/v1/master-resumes')),
        firstValueFrom(this.http.get<CandidateFact[]>('/api/v1/candidate-facts')),
        firstValueFrom(this.http.get<CandidateLanguage[]>('/api/v1/profile/languages')),
        firstValueFrom(this.http.get<CandidateAuthorization[]>('/api/v1/profile/authorizations')),
      ]);
      this.profile.set(profile);
      this.preferences.set(preferences);
      this.resumes.set(resumes);
      this.facts.set(facts);
      this.languages.set(languages);
      this.authorizations.set(authorizations);
    } finally {
      this.loading.set(false);
    }
  }

  async upload(file: File, name: string, languageCode: 'en' | 'fr'): Promise<void> {
    const form = new FormData();
    form.append('file', file);
    form.append('name', name || file.name);
    form.append('languageCode', languageCode);
    await firstValueFrom(this.http.post<MasterResume>('/api/v1/master-resumes', form));
    await this.load();
  }

  async activate(id: string): Promise<void> {
    await firstValueFrom(this.http.post(`/api/v1/master-resumes/${id}/activate`, {}));
    await this.load();
  }

  async updateResumeLanguage(id: string, languageCode: 'en' | 'fr'): Promise<void> {
    await firstValueFrom(this.http.put(`/api/v1/master-resumes/${id}/language`, { languageCode }));
    await this.load();
  }

  async importSeed(): Promise<number> {
    const result = await firstValueFrom(this.http.post<{ imported: number }>('/api/v1/profile/import-seed', {}));
    await this.load();
    return result.imported;
  }

  async transition(id: string, action: 'verify' | 'reject' | 'clarify'): Promise<void> {
    await firstValueFrom(this.http.post(`/api/v1/candidate-facts/${id}/${action}`, {}));
    await this.load();
  }

  async editFact(fact: CandidateFact, statement: string): Promise<void> {
    await firstValueFrom(this.http.put(`/api/v1/candidate-facts/${fact.id}`, {
      masterResumeId: fact.masterResumeId,
      factType: fact.factType,
      organization: fact.organization,
      roleTitle: fact.roleTitle,
      statement,
      startDate: fact.startDate,
      endDate: fact.endDate,
      skills: fact.skills,
      sourcePage: fact.sourcePage,
      sourceStartOffset: fact.sourceStartOffset,
      sourceEndOffset: fact.sourceEndOffset,
    }));
    await this.load();
  }

  async updateProfile(value: object): Promise<void> {
    this.profile.set(await firstValueFrom(this.http.put<CandidateProfile>('/api/v1/profile', value)));
  }

  async updatePreferences(value: CandidatePreferences): Promise<void> {
    this.preferences.set(await firstValueFrom(this.http.put<CandidatePreferences>('/api/v1/profile/preferences', value)));
    await this.load();
  }

  async addLanguage(value: object): Promise<void> {
    await firstValueFrom(this.http.post('/api/v1/profile/languages', value));
    await this.load();
  }

  async deleteLanguage(id: string): Promise<void> {
    await firstValueFrom(this.http.delete(`/api/v1/profile/languages/${id}`));
    await this.load();
  }

  async addAuthorization(value: object): Promise<void> {
    await firstValueFrom(this.http.post('/api/v1/profile/authorizations', value));
    await this.load();
  }

  async deleteAuthorization(id: string): Promise<void> {
    await firstValueFrom(this.http.delete(`/api/v1/profile/authorizations/${id}`));
    await this.load();
  }
}
