export interface AuthenticatedUser {
  readonly id: string;
  readonly email: string;
  readonly displayName: string;
  readonly timezone: string;
  readonly locale: string;
}

export interface LoginRequest {
  readonly email: string;
  readonly password: string;
}

