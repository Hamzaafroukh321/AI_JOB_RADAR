import { Routes } from '@angular/router';
import { authGuard } from './core/auth/auth.guard';

export const routes: Routes = [
  { path: 'login', loadComponent: () => import('./features/login/login.component').then((m) => m.LoginComponent) },
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./core/layout/app-shell.component').then((m) => m.AppShellComponent),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      { path: 'dashboard', loadComponent: () => import('./features/dashboard/dashboard.component').then((m) => m.DashboardComponent) },
      { path: 'profile', loadComponent: () => import('./features/profile/profile.component').then((m) => m.ProfileComponent) },
      { path: 'sources', loadComponent: () => import('./features/sources/sources.component').then((m) => m.SourcesComponent) },
      { path: 'jobs', loadComponent: () => import('./features/jobs/jobs.component').then((m) => m.JobsComponent) },
      { path: 'jobs/:id', loadComponent: () => import('./features/jobs/job-detail.component').then((m) => m.JobDetailComponent) },
      { path: 'tailored-resumes', loadComponent: () => import('./features/tailored-resumes/tailored-resumes.component').then((m) => m.TailoredResumesComponent) },
      { path: 'applications', loadComponent: () => import('./features/applications/applications.component').then((m) => m.ApplicationsComponent) },
    ],
  },
  { path: '**', redirectTo: '' },
];
