export interface ResumeClaim { readonly text: string; readonly verifiedFactIds: string[]; }
export interface ResumeContent {
  readonly headline: ResumeClaim; readonly summary: ResumeClaim; readonly highlights: ResumeClaim[];
  readonly missingRequirements: string[];
}
export interface TailoredResume {
  readonly id: string; readonly jobId: string; readonly variant: string; readonly title: string;
  readonly version: number; readonly versionId: string; readonly status: 'DRAFT' | 'APPROVED' | 'LOCKED';
  readonly content: ResumeContent; readonly previewHtml: string; readonly contentSha256: string; readonly createdAt: string;
}
