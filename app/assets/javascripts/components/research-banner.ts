import {Component} from './component';
import getCookie from '../utils/get-cookie.util';
import setCookie from '../utils/set-cookie.util';

export class ResearchBanner extends Component {
  private config;
  private link: HTMLAnchorElement;

  constructor(container: HTMLElement) {
    super(container);

    this.config = {
      linkSelector: '.ssp-research-banner__button',
      cookieName: 'research-banner',
      cookieMaxAgeInDays: 28
    };

    this.cacheElements();
    this.bindEvents();
    this.init();
  }

  init(): void {
    this.toggle(getCookie(this.config.cookieName) == null);
  }

  cacheElements(): void {
    this.link = this.container.querySelector(this.config.linkSelector);
  }

  bindEvents(): void {
    this.link.addEventListener('click', this.handleLinkClick.bind(this));
  }

  handleLinkClick(): void {
    this.toggle(false);

    if (getCookie(this.config.cookieName) == null) {
      setCookie(this.config.cookieName, 'dismissed', this.config.cookieMaxAgeInDays);
    }
  }

  toggle(state: boolean): void {
    this.container.classList.toggle('hidden', !state);
  }
}
