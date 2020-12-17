declare const GOVUKFrontend: any;
declare const HMRCFrontend: any;

export default function init() {
  GOVUKFrontend.initAll();
  HMRCFrontend.initAll();

  document.cookie = 'jsenabled=true; path=/';
}
