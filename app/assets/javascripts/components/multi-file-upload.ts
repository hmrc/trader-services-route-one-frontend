import {Component} from './component';
import {KeyValue} from '../interfaces/key-value.interface';
import parseTemplate from '../utils/parse-template.util';
import parseHtml from '../utils/parse-html.util';
import toggleElement from '../utils/toggle-element.util';
import ErrorManager from '../tools/error-manager.tool';

/*
TODO when removing a row, abort the XHR in progress, if there is one
TODO prevent submitting the form when uploads / removals are still in progress
TODO add error handling for all async calls
TODO provision new upload on error
TODO i18n
TODO make sure the form is fully responsive
TODO notify screen reader users that file has been uploaded / removed
TODO improve overall accessibility
TODO clean up code
 */
export class MultiFileUpload extends Component {
  private config;
  private uploadData = {};
  private classes: KeyValue;
  private submitBtn: HTMLInputElement;
  private addAnotherBtn: HTMLButtonElement;
  private uploadMoreMessage: HTMLElement;
  private itemTpl: string;
  private itemList: HTMLUListElement;
  private lastFileIndex = 0;
  private readonly errorManager;

  constructor(form: HTMLFormElement) {
    super(form);

    this.config = {
      minFiles: parseInt(form.dataset.multiFileUploadMinFiles) || 1,
      maxFiles: parseInt(form.dataset.multiFileUploadMaxFiles) || 10,
      acceptedFileTypes: form.dataset.multiFileUploadAcceptedFileTypes,
      uploadedFiles: JSON.parse(form.dataset.multiFileUploadUploadedFiles) || [],
      retryDelayMs: parseInt(form.dataset.fileUploadRetryDelayMs, 10) || 1000,
      actionUrl: form.action,
      sendUrlTpl: decodeURIComponent(form.dataset.multiFileUploadSendUrlTpl),
      statusUrlTpl: decodeURIComponent(form.dataset.multiFileUploadStatusUrlTpl),
      removeUrlTpl: decodeURIComponent(form.dataset.multiFileUploadRemoveUrlTpl),
      noFilesUploadedErrorMessage: 'You need to upload at least one file',
      genericErrorMessage: 'The file could not be uploaded'
    };

    this.classes = {
      itemList: 'multi-file-upload__item-list',
      item: 'multi-file-upload__item',
      uploading: 'multi-file-upload__item--uploading',
      uploaded: 'multi-file-upload__item--uploaded',
      removing: 'multi-file-upload__item--removing',
      file: 'multi-file-upload__file',
      fileName: 'multi-file-upload__file-name',
      remove: 'multi-file-upload__remove-item',
      addAnother: 'multi-file-upload__add-another',
      submit: 'multi-file-upload__submit',
      fileNumber: 'multi-file-upload__number',
      progressBar: 'multi-file-upload__progress-bar',
      uploadMore: 'multi-file-upload__upload-more-message'
    };

    this.errorManager = new ErrorManager();

    this.cacheElements();
    this.cacheTemplates();
    this.bindEvents();
    this.init();
  }

  private cacheElements(): void {
    this.itemList = this.container.querySelector(`.${this.classes.itemList}`);
    this.addAnotherBtn = this.container.querySelector(`.${this.classes.addAnother}`);
    this.uploadMoreMessage = this.container.querySelector(`.${this.classes.uploadMore}`);
    this.submitBtn = this.container.querySelector(`.${this.classes.submitBtn}`);
  }

  private cacheTemplates(): void {
    this.itemTpl = `      
      <li class="multi-file-upload__item">
        <div class="govuk-form-group">
          <label class="govuk-label" for="file-{fileIndex}">Document <span class="multi-file-upload__number">{fileNumber}</span></label>
          <div class="multi-file-upload__item-content">
            <div class="multi-file-upload__file-container">
              <input class="multi-file-upload__file govuk-file-upload" type="file" id="file-{fileIndex}" accept="${this.config.acceptedFileTypes}">
              <span class="multi-file-upload__file-name"></span>
            </div>

            <div class="multi-file-upload__meta-container">
              <div class="multi-file-upload__status">
                <span class="multi-file-upload__progress">
                  <span class="multi-file-upload__progress-bar"></span>
                </span>
                <span class="multi-file-upload__tag govuk-tag">Uploaded</span>
              </div>

              <button type="button" class="multi-file-upload__remove-item govuk-link">
                Remove
                <span class="govuk-visually-hidden">document <span class="multi-file-upload__number">{fileNumber}</span></span>
              </button>
              <span class="multi-file-upload__removing">Removing...</span>
            </div>
          </div>
        </div>
      </li>`;
  }

  private bindEvents(): void {
    this.getItems().forEach(this.bindItemEvents.bind(this));

    this.addAnotherBtn.addEventListener('click', this.handleAddItem.bind(this));
    this.container.addEventListener('submit', this.handleSubmit.bind(this));
  }

  private bindItemEvents(item: HTMLElement): void {
    item.querySelector(`.${this.classes.file}`).addEventListener('change', this.handleFileChange.bind(this));
    item.querySelector(`.${this.classes.remove}`).addEventListener('click', this.handleRemoveItem.bind(this));
  }

  private init(): void {
    this.updateButtonVisibility();
    this.removeAllItems();
    this.createInitialRows();
  }

  private createInitialRows(): void {
    let rowCount = 0;

    this.config.uploadedFiles.filter(file => file['fileStatus'] === 'ACCEPTED').forEach(file => {
      const item = this.addItem();
      const fileInput = item.querySelector(`.${this.classes.file}`) as HTMLInputElement;

      this.setItemStateClass(item, this.classes.uploaded);

      item.querySelector(`.${this.classes.fileName}`).textContent = file.fileName;
      fileInput.dataset.multiFileUploadFileRef = file.reference;

      rowCount++;
    });

    if (rowCount === 0) {
      for (let a = rowCount; a < this.config.minFiles; a++) {
        this.addItemWithProvisioning();
      }
    }
    else if (rowCount < this.config.maxFiles) {
      this.addItemWithProvisioning();
    }
  }

  private handleSubmit(e: Event): void {
    e.preventDefault();

    if (this.container.querySelector(`.${this.classes.uploaded}`)) {
      window.location.href = this.config.actionUrl;
    }
    else {
      const firstFileInput = this.itemList.querySelector(`.${this.classes.file}`);
      this.errorManager.addError(firstFileInput.id, this.config.noFilesUploadedErrorMessage);
    }
  }

  private handleAddItem(): void {
    const item = this.addItemWithProvisioning();
    const file = item.querySelector(`.${this.classes.file}`) as HTMLInputElement;

    file.focus();
  }

  private addItem(): HTMLLIElement {
    const time = new Date().getTime();
    const item = parseHtml(this.itemTpl, {
      fileNumber: (this.getItems().length + 1).toString(),
      fileIndex: time.toString() + ++this.lastFileIndex
    }) as HTMLLIElement;

    this.bindItemEvents(item);
    this.itemList.append(item);

    this.updateButtonVisibility();

    return item;
  }

  private addItemWithProvisioning(): HTMLLIElement {
    const item = this.addItem();
    const file = item.querySelector(`.${this.classes.file}`) as HTMLInputElement;

    this.provisionUpload(file);

    return item;
  }

  private handleRemoveItem(e: Event): void {
    const target = e.target as HTMLElement;
    const item = target.closest(`.${this.classes.item}`) as HTMLLIElement;
    const file = item.querySelector(`.${this.classes.file}`) as HTMLInputElement;

    if (!this.isUploaded(item)) {
      this.removeItem(item);

      return;
    }

    this.setItemStateClass(item, this.classes.removing);

    fetch(this.getRemoveUrl(file.dataset.multiFileUploadFileRef), {
      method: 'PUT'
    })
      .then(this.removeItem.bind(this, item));
  }

  private removeItem(item: HTMLElement): void {
    item.remove();
    this.updateFileNumbers();
    this.updateButtonVisibility();

    if (this.getItems().length === 0) {
      this.addItem();
    }
  }

  private provisionUpload(file: HTMLInputElement): void {
    this.uploadData[file.id] = {};

    this.uploadData[file.id].provisionPromise = fetch(this.getSendUrl(file.id), {
      method: 'PUT'
    })
      .then(response => response.json())
      .then(this.handleProvisionUploadCompleted.bind(this, file))
      .catch((e) => {
        // TODO implement error handling
        console.log(e);
      });
  }

  private handleProvisionUploadCompleted(file: HTMLInputElement, response: unknown): void {
    console.log('handleProvisionUploadCompleted', response);
    const fields = response['uploadRequest']['fields'];
    const url = response['uploadRequest']['href'];
    const fileRef = response['upscanReference'];

    file.dataset.multiFileUploadFileRef = fileRef;

    this.uploadData[file.id].reference = fileRef;
    this.uploadData[file.id].fields = fields;
    this.uploadData[file.id].url = url;

    console.log(this.uploadData);
  }

  private handleFileChange(e: Event): void {
    const file = e.target as HTMLInputElement;

    if (!file.files.length) {
      return;
    }

    this.uploadData[file.id].provisionPromise.then(() => {
      this.uploadFile(file);
    });
  }

  private uploadFile(file: HTMLInputElement): void {
    const formData = new FormData();
    const fileRef = file.dataset.multiFileUploadFileRef;
    const item = file.closest(`.${this.classes.item}`) as HTMLLIElement;
    const data = this.uploadData[file.id];

    this.setItemStateClass(item, this.classes.uploading);
    this.errorManager.removeError(file.id);

    item.querySelector(`.${this.classes.fileName}`).textContent = file.value.split(/([\\/])/g).pop();

    console.log('uploadFile', file);

    for (const [key, value] of Object.entries(data.fields)) {
      formData.append(key, value as string);
    }

    formData.append('file', file.files[0]);

    const xhr = new XMLHttpRequest();
    xhr.upload.addEventListener('progress', this.handleUploadFileProgress.bind(this, item));
    xhr.addEventListener('loadend', this.handleUploadFileCompleted.bind(this, fileRef));
    xhr.addEventListener('error', this.handleUploadFileError.bind(this, fileRef));
    xhr.open('POST', data.url);
    xhr.send(formData);
  }

  private handleUploadFileProgress(item: HTMLLIElement, e: ProgressEvent): void {
    console.log('PROGRESS', e, item);
    if (e.lengthComputable) {
      this.updateUploadProgress(item, e.loaded / e.total * 95);
    }
  }

  private handleUploadFileCompleted(fileRef: string): void {
    this.requestUploadStatus(fileRef);
  }

  private handleUploadFileError(fileRef: string): void {
    console.log('handleUploadFileError', fileRef);
  }

  private requestUploadStatus(fileRef: string): void {
    console.log('requestUploadStatus', fileRef);

    fetch(this.getStatusUrl(fileRef), {
      method: 'GET'
    })
      .then(response => response.json())
      .then(this.handleRequestUploadStatusCompleted.bind(this, fileRef))
      .catch((response) => { console.log('error', response); });
  }

  private delayedRequestUploadStatus(fileRef: string): void {
    console.log('delayedRequestUploadStatus', fileRef);

    window.setTimeout(this.requestUploadStatus.bind(this, fileRef), this.config.retryDelayMs);
  }

  private handleRequestUploadStatusCompleted(fileRef: string, response: unknown): void {
    console.log('handleRequestUploadStatusCompleted', fileRef, response);
    const file = this.getFileByReference(fileRef);
    const item = file.closest(`.${this.classes.item}`) as HTMLLIElement;
    let error: string;

    switch (response['fileStatus']) {
      case 'ACCEPTED':
        this.setItemStateClass(item, this.classes.uploaded);
        this.updateUploadProgress(item, 100);
        this.updateButtonVisibility();
        this.errorManager.removeError(file.id);
        break;

      case 'FAILED':
      case 'REJECTED':
      case 'DUPLICATE':
      case 'NOT_UPLOADED':
        this.setItemStateClass(item, '');
        console.log('Error', response, file);
        error = response['errorMessage'] || this.config.genericErrorMessage;
        this.errorManager.addError(file.id, error);
        break;

      case 'WAITING':
      default:
        this.errorManager.removeError(file.id);
        this.delayedRequestUploadStatus(fileRef);
        break;
    }
  }

  private updateFileNumbers(): void {
    let fileNumber = 1;

    this.getItems().forEach(item => {
      Array.from(item.querySelectorAll(`.${this.classes.fileNumber}`)).forEach(span => {
        span.textContent = fileNumber.toString();
      });

      fileNumber++;
    });
  }

  private updateButtonVisibility(): void {
    const itemCount = this.getItems().length;

    this.toggleRemoveButtons(itemCount > this.config.minFiles);
    this.toggleAddButton(itemCount < this.config.maxFiles);
    this.toggleUploadMoreMessage(itemCount === this.config.maxFiles);
  }

  private updateUploadProgress(item, value): void {
    item.querySelector(`.${this.classes.progressBar}`).style.width = `${value}%`;
  }

  private toggleRemoveButtons(state: boolean): void {
    this.getItems().forEach(item => {
      const button = item.querySelector(`.${this.classes.remove}`) as HTMLElement;

      if (this.isUploaded(item)) {
        state = true;
      }

      toggleElement(button, state);
    });
  }

  private toggleAddButton(state: boolean): void {
    toggleElement(this.addAnotherBtn, state);
  }

  private toggleUploadMoreMessage(state: boolean): void {
    toggleElement(this.uploadMoreMessage, state);
  }

  private getItems(): HTMLLIElement[] {
    return Array.from(this.itemList.querySelectorAll(`.${this.classes.item}`));
  }

  private removeAllItems(): void {
    this.getItems().forEach(item => item.remove());
  }

  private getSendUrl(fileId: string): string {
    return parseTemplate(this.config.sendUrlTpl, {fileId: fileId});
  }

  private getStatusUrl(fileRef: string): string {
    return parseTemplate(this.config.statusUrlTpl, {fileRef: fileRef});
  }

  private getRemoveUrl(fileRef: string): string {
    return parseTemplate(this.config.removeUrlTpl, {fileRef: fileRef});
  }

  private getFileByReference(fileRef: string): HTMLInputElement {
    return this.itemList.querySelector(`[data-multi-file-upload-file-ref="${fileRef}"]`);
  }

  private isUploaded(item: HTMLLIElement): boolean {
    return item.classList.contains(this.classes.uploaded);
  }

  private setItemStateClass(item: HTMLLIElement, className: string): void {
    item.classList.remove(this.classes.uploading, this.classes.uploaded, this.classes.removing);

    if (className) {
      item.classList.add(className);
    }
  }
}
