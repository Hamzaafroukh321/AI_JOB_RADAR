import { HttpClient } from '@angular/common/http';
import { inject, Injectable, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { TailoredResume } from '../tailored-resumes/tailored-resume.models';
import { ApplicationEvent, ApplicationRecord, ApplicationState } from './application.models';

@Injectable({ providedIn: 'root' })
export class ApplicationService {
  private readonly http = inject(HttpClient);
  readonly applications = signal<ApplicationRecord[]>([]);
  readonly resumes = signal<TailoredResume[]>([]);
  readonly events = signal<Record<string, ApplicationEvent[]>>({});
  readonly loading = signal(false);
  async load(): Promise<void> {
    this.loading.set(true);
    try {
      const [applications, resumes] = await Promise.all([
        firstValueFrom(this.http.get<ApplicationRecord[]>('/api/v1/applications')),
        firstValueFrom(this.http.get<TailoredResume[]>('/api/v1/tailored-resumes')),
      ]);
      this.applications.set(applications);
      this.resumes.set(resumes.filter(item => item.status === 'APPROVED' || item.status === 'LOCKED'));
    } finally { this.loading.set(false); }
  }
  async applied(jobId: string, resumeId: string): Promise<void> {
    await firstValueFrom(this.http.post('/api/v1/jobs/' + jobId + '/application/applied', { resumeId, confirmed: true }));
    await this.load();
  }
  async notApplied(id: string): Promise<void> {
    await firstValueFrom(this.http.post('/api/v1/applications/' + id + '/not-applied', { confirmed: true }));
    await this.load();
  }
  async transition(id: string, state: ApplicationState): Promise<void> {
    await firstValueFrom(this.http.post('/api/v1/applications/' + id + '/state', { state }));
    await this.load();
  }
  async addNote(id: string, note: string): Promise<void> {
    await firstValueFrom(this.http.post('/api/v1/applications/' + id + '/notes', { note }));
    await this.loadEvents(id);
  }
  async addReminder(id: string, remindAt: string, message: string): Promise<void> {
    await firstValueFrom(this.http.post('/api/v1/applications/' + id + '/reminders', { remindAt, message }));
  }
  async loadEvents(id: string): Promise<void> {
    const events = await firstValueFrom(this.http.get<ApplicationEvent[]>('/api/v1/applications/' + id + '/events'));
    this.events.update(current => ({ ...current, [id]: events }));
  }
}
