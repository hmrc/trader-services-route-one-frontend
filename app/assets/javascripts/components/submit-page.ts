import { Component } from './component';

export class SubmitPage extends Component {
    private spinner: HTMLDivElement;
    private submit: HTMLInputElement;

    constructor(form: HTMLFormElement) {
      super(form);   
    }
  
  public init(): void {
      this.cacheElements();
      this.bindEvents();
  }
  
  private bindEvents(): void {
      this.container.addEventListener('submit', this.handleSubmit.bind(this));
  }

  private cacheElements(): void {
        this.spinner = this.container.querySelector('.case-summary__submitting');
        this.submit = this.container.querySelector('.case-summary__submit');
    }
  
    private handleSubmit(e: Event): void {
      this.showLoadingMessage();
      
    }
  
    private showLoadingMessage(): void {
      this.submit.disabled = true;
      this.spinner.classList.remove('hidden');
  }  
    
}