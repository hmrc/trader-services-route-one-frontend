import {Component} from './components/component';
import {FileUpload} from './components/file-upload';
import {MultiFileUpload} from './components/multi-file-upload';
import {ResearchBanner} from './components/research-banner';

export default function loadComponents(): void {
  loadComponent(FileUpload, '.file-upload');
  loadComponent(MultiFileUpload, '.multi-file-upload');
  loadComponent(ResearchBanner, '.ssp-research-banner');
}

function loadComponent(component: new(container: HTMLElement) => Component, selector: string): void {
  const containers: HTMLElement[] = Array.from(document.querySelectorAll(selector));

  containers.forEach(container => {
    new component(container);
  });
}
