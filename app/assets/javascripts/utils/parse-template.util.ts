import {KeyValue} from '../interfaces/key-value.interface';

export default function parseTemplate(html: string, model: KeyValue): string {
  for (const [key, value] of Object.entries(model)) {
    html = html.replace(new RegExp('{' + key + '}', 'g'), value.toString());
  }

  return html;
}
