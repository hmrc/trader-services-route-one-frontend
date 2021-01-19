import {KeyValue} from '../interfaces/key-value.interface';
import parseTemplate from './parse-template.util';

export default function parseHtml(html: string, model: KeyValue): HTMLElement {
  html = parseTemplate(html, model);

  const dp = new DOMParser();
  const doc = dp.parseFromString(html, 'text/html');

  return doc.body.firstChild as HTMLElement;
}
