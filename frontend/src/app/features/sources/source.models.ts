export interface JobSource {
  readonly id: string;
  readonly key: string;
  readonly displayName: string;
  readonly type: 'GREENHOUSE' | 'LEVER' | 'ADZUNA' | 'JOBICY' | 'REMOTIVE' | 'ARBEITNOW' | 'REMOTEOK' | 'MANUAL';
  readonly termsReviewStatus: 'APPROVED' | 'REVIEW_REQUIRED' | 'DISABLED';
  readonly enabled: boolean;
  readonly parserVersion: string;
  readonly consecutiveFailures: number;
  readonly lastAttemptedAt: string | null;
  readonly lastSuccessfulAt: string | null;
  readonly lastRunStatus: string | null;
  readonly totalJobs: number;
}

export interface FetchRun {
  readonly id: string;
  readonly status: string;
  readonly triggerType: string;
  readonly startedAt: string | null;
  readonly received: number;
  readonly inserted: number;
  readonly updated: number;
  readonly deduplicated: number;
  readonly ignored: number;
  readonly errorCategory: string | null;
  readonly safeError: string | null;
}

export interface ManualImportResult { readonly fetchRunId: string; readonly jobId: string; readonly created: boolean; }
