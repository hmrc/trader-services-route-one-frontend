declare const GOVUKFrontend: { initAll };
declare const HMRCFrontend: { initAll };

export default function init(): void {
  GOVUKFrontend.initAll();
  HMRCFrontend.initAll();

  document.cookie = 'jsenabled=true; path=/';
}
