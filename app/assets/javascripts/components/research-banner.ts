import {Component} from './component';
import getCookie from '../utils/get-cookie.util';
import setCookie from '../utils/set-cookie.util';
import toggleElement from '../utils/toggle-element.util';

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
  }

  public init(): void {
    this.toggle(getCookie(this.config.cookieName) == null);
  }

  private cacheElements(): void {
    this.link = this.container.querySelector(this.config.linkSelector);
  }

  private bindEvents(): void {
    this.link.addEventListener('click', this.handleLinkClick.bind(this));
  }

  private handleLinkClick(): void {
    this.toggle(false);

    if (getCookie(this.config.cookieName) == null) {
      setCookie(this.config.cookieName, 'dismissed', this.config.cookieMaxAgeInDays);
    }
  }

  private toggle(state: boolean): void {
    toggleElement(this.container, state);
  }
}
