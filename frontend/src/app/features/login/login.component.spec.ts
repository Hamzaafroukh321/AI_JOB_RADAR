import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { LoginComponent } from './login.component';

describe('LoginComponent', () => {
  beforeEach(async () => TestBed.configureTestingModule({ imports: [LoginComponent], providers: [provideHttpClient(), provideRouter([])] }).compileComponents());
  it('requires a valid email and password', () => {
    const component = TestBed.createComponent(LoginComponent).componentInstance;
    component.form.setValue({ email: 'invalid', password: '' });
    expect(component.form.invalid).toBe(true);
    component.form.setValue({ email: 'person@example.test', password: 'local-only' });
    expect(component.form.valid).toBe(true);
  });
});

