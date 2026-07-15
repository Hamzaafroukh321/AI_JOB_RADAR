async page => {
  let state = 'OPENED';
  let resumeVersionId = null;
  await page.route('**/api/v1/**', async route => {
    const request = route.request();
    const path = request.url().replace(/^https?:\/\/[^/]+/, '').split('?')[0];
    const json = value => route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(value),
    });
    if (path === '/api/v1/auth/me') {
      return json({
        id: 'user-1',
        email: 'user@example.test',
        displayName: 'Test User',
        timezone: 'Africa/Casablanca',
        locale: 'en',
      });
    }
    if (path === '/api/v1/applications' && request.method() === 'GET') {
      return json([{
        id: 'app-1',
        jobId: 'job-1',
        title: 'Generative AI Engineer',
        company: 'Acme AI',
        state,
        resumeVersionId,
        appliedAt: state === 'APPLIED' ? '2026-07-14T20:00:00Z' : null,
        updatedAt: '2026-07-14T20:00:00Z',
      }]);
    }
    if (path === '/api/v1/tailored-resumes') {
      return json([{
        id: 'resume-1',
        jobId: 'job-1',
        variant: 'GENAI_RAG',
        title: 'Verified GenAI Resume',
        version: 2,
        versionId: 'version-2',
        status: 'APPROVED',
        content: {},
        previewHtml: '',
        contentSha256: 'abc',
        createdAt: '2026-07-14T19:00:00Z',
      }]);
    }
    if (path === '/api/v1/jobs/job-1/application/applied') {
      state = 'APPLIED';
      resumeVersionId = 'version-2';
      return json({});
    }
    if (path === '/api/v1/applications/app-1/not-applied') {
      state = 'OPENED';
      return json({});
    }
    if (path === '/api/v1/applications/app-1/events') return json([]);
    return route.fulfill({ status: 404, contentType: 'application/json', body: '{}' });
  });
  await page.goto('http://127.0.0.1:4300');
}
