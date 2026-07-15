import { Injectable, signal } from '@angular/core';

export interface ApiErrorNotice { readonly title: string; readonly detail: string; }

@Injectable({ providedIn: 'root' })
export class ApiErrorService {
  private readonly noticeSignal = signal<ApiErrorNotice | null>(null);
  readonly notice = this.noticeSignal.asReadonly();
  show(notice: ApiErrorNotice): void { this.noticeSignal.set(notice); }
  clear(): void { this.noticeSignal.set(null); }
}

