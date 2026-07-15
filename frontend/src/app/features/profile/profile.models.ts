export interface CandidateProfile {
  readonly id: string;
  readonly headline: string | null;
  readonly homeCountryCode: string | null;
  readonly homeRegion: string | null;
  readonly currentCity: string | null;
  readonly relocationPreference: string;
  readonly sponsorshipRequired: boolean | null;
  readonly activeMasterResumeId: string | null;
  readonly profileVersion: number;
}

export interface CandidatePreferences {
  readonly targetRoleFamilies: string[];
  readonly targetSeniority: string[];
  readonly preferredRegions: string[];
  readonly preferredCountries: string[];
  readonly excludedCountries: string[];
  readonly employmentTypes: string[];
  readonly workplaceModes: string[];
  readonly minimumSalary: { readonly amount: number; readonly currency: string } | null;
  readonly contractAllowed: boolean | null;
  readonly freelanceAllowed: boolean | null;
  readonly annotationWorkAllowed: boolean | null;
  readonly temporaryWorkAllowed: boolean | null;
  readonly dailyDigestEnabled: boolean;
  readonly dailyDigestTime: string | null;
  readonly minimumMatchScore: number;
  readonly freshnessDays: number;
  readonly excludedCompanies: string[];
  readonly excludedKeywords: string[];
  readonly includedKeywords: string[];
  readonly workingHours: { readonly timezone: string; readonly preferredOverlap: string[] } | null;
  readonly preferredCompanySizes: string[];
  readonly preferredIndustries: string[];
}

export interface ResumeTextBlock {
  readonly page: number | null;
  readonly paragraph: number;
  readonly section: string | null;
  readonly text: string;
  readonly startOffset: number;
  readonly endOffset: number;
}

export interface MasterResume {
  readonly id: string;
  readonly name: string;
  readonly languageCode: string;
  readonly originalFilename: string;
  readonly mimeType: string;
  readonly sizeBytes: number;
  readonly sha256: string;
  readonly extractionStatus: string;
  readonly active: boolean;
  readonly createdAt: string;
  readonly extractionPreview: string | null;
  readonly blocks: ResumeTextBlock[];
}

export interface CandidateFact {
  readonly id: string;
  readonly masterResumeId: string | null;
  readonly factType: string;
  readonly organization: string | null;
  readonly roleTitle: string | null;
  readonly statement: string;
  readonly startDate: string | null;
  readonly endDate: string | null;
  readonly skills: string[];
  readonly sourcePage: number | null;
  readonly sourceStartOffset: number | null;
  readonly sourceEndOffset: number | null;
  readonly verificationStatus: string;
  readonly userEdited: boolean;
  readonly supersedesFactId: string | null;
  readonly createdAt: string;
}

export interface CandidateLanguage {
  readonly id: string;
  readonly languageCode: string;
  readonly spokenLevel: string;
  readonly writtenLevel: string;
  readonly professionalUse: boolean | null;
  readonly verifiedByUser: boolean;
}

export interface CandidateAuthorization {
  readonly id: string;
  readonly countryCode: string;
  readonly authorizationStatus: string;
  readonly sponsorshipNeeded: boolean | null;
  readonly expiresAt: string | null;
  readonly verifiedByUser: boolean;
  readonly notes: string | null;
}
