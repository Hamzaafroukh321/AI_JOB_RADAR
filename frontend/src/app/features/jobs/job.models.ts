export interface JobCard {
  readonly id: string; readonly originalTitle: string; readonly canonicalTitle: string; readonly company: string;
  readonly location: string | null; readonly source: string | null; readonly sourcePostedAt: string | null;
  readonly firstSeenAt: string; readonly lastVerifiedAt: string; readonly employmentType: string; readonly seniority: string;
  readonly workplaceMode: string; readonly remoteScope: string; readonly moroccoEligibility: string;
  readonly primaryRoleFamily: string | null; readonly aiRelevance: string; readonly annotationRelevance: string;
  readonly salaryMin: number | null; readonly salaryMax: number | null; readonly salaryCurrency: string | null;
  readonly saved: boolean; readonly hidden: boolean; readonly archived: boolean;
}
export interface JobPage { readonly content: JobCard[]; readonly page: number; readonly size: number; readonly totalElements: number; readonly totalPages: number; }
export interface EvidenceItem { readonly text: string; readonly evidence: string; }
export interface Requirement { readonly requirement: string; readonly type: string; readonly evidence: string; readonly confidence: number; }
export interface JobAnalysis { readonly jobSummary: string; readonly aiRelevance: string; readonly annotationRelevance: string; readonly workplaceMode: string; readonly remoteScope: string; readonly moroccoEligibility: string; readonly responsibilities: EvidenceItem[]; readonly mustHaveRequirements: Requirement[]; readonly niceToHaveRequirements: Requirement[]; readonly technologies: string[]; readonly warnings: string[]; readonly overallConfidence: number; }
export interface JobDetail { readonly job: JobCard; readonly description: string; readonly applicationUrl: string | null; readonly sourceLinks: string[]; readonly regions: string[]; readonly analysis: JobAnalysis | null; readonly analysisValidationStatus: string | null; readonly analysisModel: string | null; readonly analysisCreatedAt: string | null; }
export interface MatchEvidence { readonly label: string; readonly jobEvidence: string; readonly verifiedFactIds: string[]; }
export interface JobMatch {
  readonly id: string; readonly jobId: string; readonly eligibilityState: string; readonly overallScore: number;
  readonly confidence: number; readonly componentScores: Record<string, number>; readonly strongMatches: MatchEvidence[];
  readonly partialMatches: MatchEvidence[]; readonly missingRequirements: string[]; readonly unknowns: string[];
  readonly hardBlockers: string[]; readonly userQuestions: string[]; readonly recommendedAction: string; readonly rationale: string;
}
