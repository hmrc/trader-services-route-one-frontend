import init from './init';
import { FileUpload } from './components/file-upload';

init();

const forms: HTMLFormElement[] = Array.from(document.querySelectorAll('.file-upload'));

forms.forEach(form => {
  new FileUpload(form);
});

require('./legacy/researchBanner.js');
