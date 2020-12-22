import {Component} from './component';

export class FileUpload extends Component {
  private config;
  private ariaLiveMessageTpl: string;
  private loadingContainer: HTMLDivElement;
  private spinner: HTMLDivElement;
  private submit: HTMLInputElement;
  private fileInput: HTMLInputElement;

  constructor(form: HTMLFormElement) {
    super(form);

    this.config = {
      uploadUrl: form.action,
      retryDelayMs: parseInt(form.dataset.fileUploadRetryDelayMs, 10) || 1000,
      successUrl: form.dataset.fileUploadRedirectSuccessUrl,
      failureUrl: form.dataset.fileUploadRedirectFailureUrl,
      checkStatusUrl: form.dataset.fileUploadCheckStatusUrl,
      ariaLiveMessage: form.dataset.fileUploadAriaLiveMessage,
      fileInputName: 'file'
    };

    this.cacheTemplates();
    this.cacheElements();
    this.bindEvents();
  }

  private cacheTemplates(): void {
    this.ariaLiveMessageTpl = `<p class="govuk-body">${this.config.ariaLiveMessage}</p>`;
  }

  private cacheElements(): void {
    this.loadingContainer = this.container.querySelector('.file-upload__loading-container');
    this.spinner = this.container.querySelector('.file-upload__spinner');
    this.submit = this.container.querySelector('.file-upload__submit');
    this.fileInput = this.container.querySelector(`[name="${this.config.fileInputName}"]`);
  }

  private bindEvents(): void {
    this.container.addEventListener('submit', this.handleSubmit.bind(this));
  }

  private handleSubmit(e: Event): void {
    e.preventDefault();

    this.showLoadingMessage();
    this.submitForm();
  }

  private showLoadingMessage(): void {
    this.submit.disabled = true;
    this.spinner.classList.remove('hidden');
    this.loadingContainer.insertAdjacentHTML('afterbegin', this.ariaLiveMessageTpl);
  }

  private submitForm(): void {
    const formData: FormData = new FormData(this.container as HTMLFormElement);

    if (!this.fileInput.value) {
      formData.delete(this.config.fileInputName);
    }

    fetch(this.config.uploadUrl, {
      method: 'POST',
      body: formData
    })
      .then(this.handleUploadFormCompleted.bind(this))
      .catch(this.handleUploadFormError.bind(this));
  }

  private handleUploadFormCompleted(): void {
    this.requestUploadStatus();
  }

  private handleUploadFormError(): void {
    window.location.href = this.config.failureUrl;
  }

  private requestUploadStatus(): void {
    fetch(this.config.checkStatusUrl, {
      method: 'GET'
    })
      .then(response => response.json())
      .then(this.handleRequestUploadStatusCompleted.bind(this))
      .catch(this.delayedRequestUploadStatus.bind(this));
  }

  private delayedRequestUploadStatus(): void {
    window.setTimeout(this.requestUploadStatus.bind(this), this.config.retryDelayMs);
  }

  private handleRequestUploadStatusCompleted(response: unknown): void {
    switch (response['fileStatus']) {
      case 'ACCEPTED':
        window.location.href = this.config.successUrl;
        break;

      case 'FAILED':
      case 'REJECTED':
        window.location.href = this.config.failureUrl;
        break;

      case 'WAITING':
      default:
        this.delayedRequestUploadStatus();
        break;
    }
  }
}
