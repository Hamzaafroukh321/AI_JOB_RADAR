import { requireExternalHttpUrl } from './external-url.policy';

describe('requireExternalHttpUrl', () => {
  it('allows http and https application destinations', () => {
    expect(requireExternalHttpUrl('https://jobs.example.test/apply', 'https://radar.example.test')).toBe('https://jobs.example.test/apply');
    expect(requireExternalHttpUrl('/apply', 'http://localhost:4200')).toBe('http://localhost:4200/apply');
  });
  it('blocks script-bearing and malformed destinations', () => {
    expect(() => requireExternalHttpUrl('javascript:alert(1)', 'https://radar.example.test')).toThrow();
    expect(() => requireExternalHttpUrl('data:text/html,bad', 'https://radar.example.test')).toThrow();
  });
});
