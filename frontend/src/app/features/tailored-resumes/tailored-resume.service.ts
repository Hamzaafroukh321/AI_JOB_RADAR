import { HttpClient } from '@angular/common/http';
import { inject, Injectable, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { TailoredResume } from './tailored-resume.models';

@Injectable({ providedIn: 'root' })
export class TailoredResumeService {
  private readonly http = inject(HttpClient);
  readonly variants = signal<string[]>([]);
  readonly resumes = signal<TailoredResume[]>([]);
  readonly loading = signal(false);

  async load(): Promise<void> {
    this.loading.set(true);
    try {
      const [variants, resumes] = await Promise.all([
        firstValueFrom(this.http.get<string[]>('/api/v1/tailored-resume-variants')),
        firstValueFrom(this.http.get<TailoredResume[]>('/api/v1/tailored-resumes')),
      ]);
      this.variants.set([...variants].sort());
      this.resumes.set(resumes);
    } finally { this.loading.set(false); }
  }

  async generate(jobId: string, variant: string): Promise<void> {
    await firstValueFrom(this.http.post('/api/v1/jobs/' + jobId + '/tailored-resumes', { variant }));
    await this.load();
  }

  async approve(id: string): Promise<void> {
    await firstValueFrom(this.http.post('/api/v1/tailored-resumes/' + id + '/approve', {}));
    await this.load();
  }

  downloadUrl(id: string, format: 'pdf' | 'docx'): string {
    return '/api/v1/tailored-resumes/' + id + '/download?format=' + format;
  }
}
