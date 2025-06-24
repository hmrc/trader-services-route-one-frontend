import { initAll } from 'webjars/lib/govuk-frontend/dist/govuk/govuk-frontend.min.js';
import HMRCFrontend from 'webjars/lib/hmrc-frontend/hmrc/all.js';

export default function

  init(): void {
  initAll();
  HMRCFrontend.initAll();

  document.cookie = 'jsenabled=true; path=/';

  Array
    .from(document.querySelectorAll('button[data-disable-after-click="true"]'))
    .forEach(element => {
      element.addEventListener('click', function (event: Event) {
        const target = event.target as HTMLInputElement;
        window.setTimeout((target: HTMLInputElement) => {
          target.setAttribute('disabled', '');
        }, 10, target);
        return true;
      });
    });
}
