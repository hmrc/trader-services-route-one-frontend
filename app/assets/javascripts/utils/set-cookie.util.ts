export default function setCookie(name: string, value: string, maxAgeInDays?: number): void {
  const SECONDS_IN_DAY = 86400;

  const parts = [
    `${name}=${value}`,
    'Path=/'
  ];

  if (maxAgeInDays) {
    parts.push(`max-age=${SECONDS_IN_DAY * maxAgeInDays}`);
  }

  document.cookie = parts.join(';');
}
