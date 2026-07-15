import { AuthenticatedUser } from '../../core/auth/auth.models';
export interface DashboardSummary { readonly phase: string; readonly status: string; readonly user: AuthenticatedUser; readonly generatedAt: string; }

