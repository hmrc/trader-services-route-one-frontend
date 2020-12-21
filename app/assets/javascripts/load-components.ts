import {Component} from './components/component';
import {FileUpload} from './components/file-upload';

export default function loadComponents(): void {
  loadComponent(FileUpload, '.file-upload');

  loadLegacyComponents();
}

function loadComponent(component: new(container: HTMLElement) => Component, selector: string): void {
  const containers: HTMLElement[] = Array.from(document.querySelectorAll(selector));

  containers.forEach(container => {
    new component(container);
  });
}

function loadLegacyComponents(): void {
  require('./legacy/research-banner.js');
}
