export default function getCookie(name: string): string {
  const cookies = decodeURIComponent(document.cookie).split(';');

  for (let a = 0; a < cookies.length; a++) {
    const parts = cookies[a].split('=');

    if (parts[0].trim() === name) {
      parts.shift();

      return parts.join('=');
    }
  }

  return null;
}
