import {KeyValue} from '../interfaces/key-value.interface';

export default function parseTemplate(string: string, model: KeyValue): string {
  for (const [key, value] of Object.entries(model)) {
    string = string.replace(new RegExp('{' + key + '}', 'g'), value.toString());
  }

  return string;
}
