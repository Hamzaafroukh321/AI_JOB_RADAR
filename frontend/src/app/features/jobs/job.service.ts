import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { requireExternalHttpUrl } from '../../core/security/external-url.policy';
import { JobDetail, JobMatch, JobPage } from './job.models';

@Injectable({ providedIn: 'root' })
export class JobService {
  private readonly http = inject(HttpClient);
  readonly page = signal<JobPage | null>(null);
  readonly detail = signal<JobDetail | null>(null);
  readonly match = signal<JobMatch | null>(null);
  readonly loading = signal(false);
  async search(filters: Record<string, string | boolean | number>): Promise<void> {
    this.loading.set(true);
    try {
      let params = new HttpParams();
      for (const [key, value] of Object.entries(filters)) if (value !== '' && value !== false) params = params.set(key, String(value));
      this.page.set(await firstValueFrom(this.http.get<JobPage>('/api/v1/jobs', { params })));
    } finally { this.loading.set(false); }
  }
  async load(id: string): Promise<void> { this.detail.set(await firstValueFrom(this.http.get<JobDetail>(`/api/v1/jobs/${id}`))); }
  async loadMatch(id: string, force = false): Promise<void> {
    const url = '/api/v1/jobs/' + id + '/match';
    const request = force ? this.http.post<JobMatch>(url + '/recompute', {}) : this.http.get<JobMatch>(url);
    this.match.set(await firstValueFrom(request));
  }
  async feedback(id: string, feedbackType: string): Promise<void> {
    await firstValueFrom(this.http.post('/api/v1/jobs/' + id + '/match/feedback', { feedbackType }));
  }
  async action(id: string, action: 'save' | 'unsave' | 'hide' | 'restore' | 'archive'): Promise<void> {
    if (action === 'unsave') await firstValueFrom(this.http.delete(`/api/v1/jobs/${id}/save`));
    else await firstValueFrom(this.http.post(`/api/v1/jobs/${id}/${action}`, {}));
  }
  async reanalyze(id: string): Promise<void> { await firstValueFrom(this.http.post(`/api/v1/jobs/${id}/reanalyze`, {})); await this.load(id); }
  async openApplication(id: string): Promise<string> {
    const result = await firstValueFrom(this.http.post<{ applicationUrl: string }>('/api/v1/jobs/' + id + '/application/open', {}));
    return requireExternalHttpUrl(result.applicationUrl);
  }
}
