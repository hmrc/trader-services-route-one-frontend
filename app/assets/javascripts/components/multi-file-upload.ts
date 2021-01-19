import {Component} from './component';
import {KeyValue} from '../interfaces/key-value.interface';
import parseTemplate from '../utils/parse-template.util';
import parseHtml from '../utils/parse-html.util';
import toggleElement from '../utils/toggle-element.util';

/*
TODO provision upload when row is created, not when upload is initiated
TODO add "Removing..." state (replace Remove button? - to be discussed with design)
TODO on page load, always show one extra empty row after the list of already uploaded files, unless no more files can be uploaded (to be discussed with design)
TODO on page load, any already-uploaded files need a remove button, even if there's only one item
TODO when removing a row, abort the XHR in progress, if there is one
 */
export class MultiFileUpload extends Component {
  private config;
  private classes: KeyValue;
  private submitBtn: HTMLInputElement;
  private addAnotherBtn: HTMLButtonElement;
  private itemTpl: string;
  private itemList: HTMLUListElement;
  private lastFileIndex = 0;

  constructor(form: HTMLFormElement) {
    super(form);

    this.config = {
      minFiles: parseInt(form.dataset.multiFileUploadMinFiles) || 1,
      maxFiles: parseInt(form.dataset.multiFileUploadMaxFiles) || 10,
      uploadedFiles: JSON.parse(form.dataset.multiFileUploadUploadedFiles) || [],
      retryDelayMs: parseInt(form.dataset.fileUploadRetryDelayMs, 10) || 1000,
      sendUrlTpl: decodeURIComponent(form.dataset.multiFileUploadSendUrlTpl),
      statusUrlTpl: decodeURIComponent(form.dataset.multiFileUploadStatusUrlTpl),
      removeUrlTpl: decodeURIComponent(form.dataset.multiFileUploadRemoveUrlTpl)
    };

    this.classes = {
      itemList: 'multi-file-upload__item-list',
      item: 'multi-file-upload__item',
      uploading: 'multi-file-upload__item--uploading',
      uploaded: 'multi-file-upload__item--uploaded',
      file: 'multi-file-upload__file',
      fileName: 'multi-file-upload__file-name',
      remove: 'multi-file-upload__remove-item',
      addAnother: 'multi-file-upload__add-another',
      submit: 'multi-file-upload__submit',
      fileNumber: 'multi-file-upload__number',
      progressBar: 'multi-file-upload__progress-bar'
    };

    this.cacheElements();
    this.cacheTemplates();
    this.bindEvents();
    this.init();
  }

  private cacheElements(): void {
    this.itemList = this.container.querySelector(`.${this.classes.itemList}`);
    this.addAnotherBtn = this.container.querySelector(`.${this.classes.addAnother}`);
    this.submitBtn = this.container.querySelector(`.${this.classes.submitBtn}`);
  }

  private cacheTemplates(): void {
    this.itemTpl = `      
      <li class="multi-file-upload__item">
        <div class="multi-file-upload__item-inner">
          <label class="govuk-label" for="file-{fileIndex}">Document <span class="multi-file-upload__number">{fileNumber}</span></label>
          <span id="contactEmail-error" class="govuk-error-message">
             <span class="govuk-visually-hidden">Error:</span>
             <span class="multi-file-upload__error-message"></span>
          </span>
          <div class="multi-file-upload__item-content">
            <div class="multi-file-upload__file-container">
              <input class="multi-file-upload__file govuk-file-upload" type="file" id="file-{fileIndex}">
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
            </div>
          </div>
        </div>
      </li>`;
  }

  private bindEvents(): void {
    this.getItems().forEach(this.bindItemEvents.bind(this));

    this.addAnotherBtn.addEventListener('click', this.handleAddItem.bind(this));
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

    for (let a = rowCount; a < this.config.minFiles; a++) {
      this.addItem();
    }
  }

  private handleAddItem(): void {
    const item = this.addItem();
    const file = item.querySelector(`.${this.classes.file}`) as HTMLInputElement;

    file.focus();
  }

  private handleRemoveItem(e: Event): void {
    const target = e.target as HTMLElement;
    const item = target.closest(`.${this.classes.item}`) as HTMLLIElement;
    const file = item.querySelector(`.${this.classes.file}`) as HTMLInputElement;

    if (!this.isUploaded(item)) {
      item.remove();

      this.updateFileNumbers();
      this.updateButtonVisibility();

      return;
    }

    fetch(this.getRemoveUrl(file.dataset.multiFileUploadFileRef), {
      method: 'PUT'
    })
      .then(response => {
        console.log(response);

        item.remove();

        this.updateFileNumbers();
        this.updateButtonVisibility();
      });
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

  private handleFileChange(e: Event): void {
    this.provisionUpload(e.target as HTMLInputElement);
  }

  private provisionUpload(file: HTMLInputElement): void {
    fetch(this.getSendUrl(file.id), {
      method: 'PUT'
    })
      .then(response => response.json())
      .then(this.uploadFile.bind(this, file))
      .catch((e) => {
        // TODO implement error handling
        console.log(e);
      });
  }

  private uploadFile(file: HTMLInputElement, response: unknown): void {
    const formData = new FormData();
    const fields = response['uploadRequest']['fields'];
    const url = response['uploadRequest']['href'];
    const fileRef = response['upscanReference'];
    const item = file.closest(`.${this.classes.item}`) as HTMLLIElement;

    this.setItemStateClass(item, this.classes.uploading);
    item.querySelector(`.${this.classes.fileName}`).textContent = file.value.split(/([\\/])/g).pop();

    console.log('uploadFile', fileRef, response);

    file.dataset.multiFileUploadFileRef = fileRef;

    for (const [key, value] of Object.entries(fields)) {
      formData.append(key, value as string);
    }

    formData.append('file', file.files[0]);

    const xhr = new XMLHttpRequest();
    xhr.upload.addEventListener('progress', this.handleUploadFileProgress.bind(this, item));
    xhr.addEventListener('loadend', this.handleUploadFileCompleted.bind(this, fileRef));
    xhr.addEventListener('error', this.handleUploadFileError.bind(this, fileRef));
    xhr.open('POST', url);
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
    // window.location.href = this.config.failureUrl;
  }

  private requestUploadStatus(fileRef: string): void {
    console.log('requestUploadStatus', fileRef);

    fetch(this.getStatusUrl(fileRef), {
      method: 'GET'
    })
      .then(response => response.json())
      .then(this.handleRequestUploadStatusCompleted.bind(this, fileRef))
      .catch(this.delayedRequestUploadStatus.bind(this, fileRef));
  }

  private delayedRequestUploadStatus(fileRef: string): void {
    console.log('delayedRequestUploadStatus', fileRef);

    window.setTimeout(this.requestUploadStatus.bind(this, fileRef), this.config.retryDelayMs);
  }

  private handleRequestUploadStatusCompleted(fileRef: string, response: unknown): void {
    console.log('handleRequestUploadStatusCompleted', fileRef, response);
    const file = this.getFileByReference(fileRef);
    const item = file.closest(`.${this.classes.item}`) as HTMLLIElement;

    switch (response['fileStatus']) {
      case 'ACCEPTED':
        this.setItemStateClass(item, this.classes.uploaded);
        this.updateUploadProgress(item, 100);
        break;

      case 'FAILED':
      case 'REJECTED':
      case 'DUPLICATE':
        this.setItemStateClass(item, '');
        console.log('Error', response);
        // display error message
        break;

      case 'WAITING':
      default:
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

    this.toggleRemoveButtons(itemCount <= this.config.minFiles);
    this.toggleAddButton(itemCount >= this.config.maxFiles);
  }

  private updateUploadProgress(item, value): void {
    item.querySelector(`.${this.classes.progressBar}`).style.width = `${value}%`;
  }

  private toggleRemoveButtons(state: boolean): void {
    Array.from(this.itemList.querySelectorAll(`.${this.classes.remove}`)).forEach(button => {
      button.classList.toggle(this.classes.hidden, state);
      toggleElement(button as HTMLElement, state);
    });
  }

  private toggleAddButton(state: boolean): void {
    toggleElement(this.addAnotherBtn, state);
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
    item.classList.remove(this.classes.uploading, this.classes.uploaded);

    if (className) {
      item.classList.add(className);
    }
  }
}
