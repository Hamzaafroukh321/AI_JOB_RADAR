export type ApplicationState = 'SAVED' | 'OPENED' | 'APPLIED' | 'INTERVIEW' | 'OFFER' | 'REJECTED' | 'WITHDRAWN';
export interface ApplicationRecord {
  readonly id: string; readonly jobId: string; readonly title: string; readonly company: string;
  readonly state: ApplicationState; readonly resumeVersionId: string | null; readonly appliedAt: string | null; readonly updatedAt: string;
}
export interface ApplicationEvent {
  readonly id: string; readonly type: string; readonly fromState: string | null; readonly toState: string | null;
  readonly note: string | null; readonly createdAt: string;
}
