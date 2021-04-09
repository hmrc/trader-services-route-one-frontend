declare const GOVUKFrontend: { initAll };
declare const HMRCFrontend: { initAll };

export default function init(): void {
  GOVUKFrontend.initAll();
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
