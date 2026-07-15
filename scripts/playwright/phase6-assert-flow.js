async page => {
  await page.getByRole('link', { name: 'Applications' }).click();
  await page.getByRole('heading', { name: 'Applications' }).waitFor();
  await page.getByRole('button', { name: 'Mark Applied' }).click();
  await page.getByRole('heading', { name: 'Confirm you applied manually' }).waitFor();
  await page.getByLabel('Approved resume').selectOption('resume-1');
  await page.getByRole('button', { name: 'Confirm Applied' }).click();
  await page.getByText('Locked resume version: version-2').waitFor();
  await page.getByRole('button', { name: 'Remove Applied' }).click();
  await page.getByRole('heading', { name: 'Remove Applied status?' }).waitFor();
  const appliedBeforeConfirmation = await page.getByText('Applied', { exact: true }).isVisible();
  await page.getByRole('button', { name: 'Confirm removal' }).click();
  await page.getByRole('button', { name: 'Mark Applied' }).waitFor();
  return {
    appliedBeforeConfirmation,
    finalState: await page.locator('article.card strong').first().innerText(),
    noAutomaticApplicationCopy: await page.getByText(
      'AI Job Radar never fills or submits an application.',
    ).isVisible(),
  };
}
