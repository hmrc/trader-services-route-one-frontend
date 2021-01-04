import {Component} from './components/component';
import {FileUpload} from './components/file-upload';

import './legacy/research-banner';

export default function loadComponents(): void {
  loadComponent(FileUpload, '.file-upload');
}

function loadComponent(component: new(container: HTMLElement) => Component, selector: string): void {
  const containers: HTMLElement[] = Array.from(document.querySelectorAll(selector));

  containers.forEach(container => {
    new component(container);
  });
}
