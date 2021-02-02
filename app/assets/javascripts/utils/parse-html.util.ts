import {KeyValue} from '../interfaces/key-value.interface';
import parseTemplate from './parse-template.util';

export default function parseHtml(string: string, model: KeyValue): HTMLElement {
  string = parseTemplate(string, model);

  const dp = new DOMParser();
  const doc = dp.parseFromString(string, 'text/html');

  return doc.body.firstChild as HTMLElement;
}
