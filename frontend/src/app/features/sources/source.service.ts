import { HttpClient } from '@angular/common/http';
import { inject, Injectable, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { FetchRun, JobSource, ManualImportResult } from './source.models';

@Injectable({ providedIn: 'root' })
export class SourceService {
  private readonly http = inject(HttpClient);
  readonly sources = signal<JobSource[]>([]);
  readonly runs = signal<FetchRun[]>([]);
  readonly loading = signal(false);

  async load(): Promise<void> {
    this.loading.set(true);
    try { this.sources.set(await firstValueFrom(this.http.get<JobSource[]>('/api/v1/sources'))); }
    finally { this.loading.set(false); }
  }

  async create(value: object): Promise<void> {
    await firstValueFrom(this.http.post<JobSource>('/api/v1/sources', value));
    await this.load();
  }

  async setEnabled(source: JobSource, enabled: boolean): Promise<void> {
    await firstValueFrom(this.http.post(`/api/v1/sources/${source.id}/${enabled ? 'enable' : 'disable'}`, {}));
    await this.load();
  }

  async fetch(source: JobSource): Promise<void> {
    await firstValueFrom(this.http.post(`/api/v1/sources/${source.id}/fetch`, {}));
    await Promise.all([this.load(), this.loadRuns(source.id)]);
  }

  async loadRuns(sourceId: string): Promise<void> {
    this.runs.set(await firstValueFrom(this.http.get<FetchRun[]>(`/api/v1/sources/${sourceId}/runs`)));
  }

  importManual(value: object): Promise<ManualImportResult> {
    return firstValueFrom(this.http.post<ManualImportResult>('/api/v1/sources/manual-import', value));
  }
}
