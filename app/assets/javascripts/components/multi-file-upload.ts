import { Component } from './component';
import { KeyValue } from '../interfaces/key-value.interface';
import { UploadState } from '../enums/upload-state.enum';
import parseTemplate from '../utils/parse-template.util';
import parseHtml from '../utils/parse-html.util';
import toggleElement from '../utils/toggle-element.util';
import ErrorManager from '../tools/error-manager.tool';

export class MultiFileUpload extends Component {
  private config;
  private uploadData = {};
  private messages: KeyValue;
  private classes: KeyValue;
  private formStatus: HTMLElement;
  private submitBtn: HTMLInputElement;
  private addAnotherBtn: HTMLButtonElement;
  private uploadMoreMessage: HTMLElement;
  private notifications: HTMLElement;
  private itemTpl: string;
  private itemList: HTMLUListElement;
  private lastFileIndex = 0;
  private readonly errorManager;

  constructor(form: HTMLFormElement) {
    super(form);

    this.config = {
      startRows: parseInt(form.dataset.multiFileUploadStartRows) || 1,
      minFiles: parseInt(form.dataset.multiFileUploadMinFiles) || 1,
      maxFiles: parseInt(form.dataset.multiFileUploadMaxFiles) || 10,
      uploadedFiles: form.dataset.multiFileUploadUploadedFiles ? JSON.parse(form.dataset.multiFileUploadUploadedFiles) : [],
      retryDelayMs: parseInt(form.dataset.multiFileUploadRetryDelayMs, 10) || 1000,
      maxRetries: parseInt(form.dataset.multiFileUploadMaxRetries) || 30,
      actionUrl: form.action,
      sendUrlTpl: decodeURIComponent(form.dataset.multiFileUploadSendUrlTpl),
      statusUrlTpl: decodeURIComponent(form.dataset.multiFileUploadStatusUrlTpl),
      removeUrlTpl: decodeURIComponent(form.dataset.multiFileUploadRemoveUrlTpl)
    };

    this.messages = {
      noFilesUploadedError: form.dataset.multiFileUploadErrorSelectFile,
      genericError: form.dataset.multiFileUploadErrorGeneric,
      couldNotRemoveFile: form.dataset.multiFileUploadErrorRemoveFile,
      stillTransferring: form.dataset.multiFileUploadStillTransferring,
      documentUploaded: form.dataset.multiFileUploadDocumentUploaded,
      documentDeleted: form.dataset.multiFileUploadDocumentDeleted,
    };

    this.classes = {
      itemList: 'multi-file-upload__item-list',
      item: 'multi-file-upload__item',
      uploading: 'multi-file-upload__item--uploading',
      verifying: 'multi-file-upload__item--verifying',
      uploaded: 'multi-file-upload__item--uploaded',
      removing: 'multi-file-upload__item--removing',
      file: 'multi-file-upload__file',
      fileName: 'multi-file-upload__file-name',
      filePreview: 'multi-file-upload__file-preview',
      remove: 'multi-file-upload__remove-item',
      addAnother: 'multi-file-upload__add-another',
      formStatus: 'multi-file-upload__form-status',
      submit: 'multi-file-upload__submit',
      fileNumber: 'multi-file-upload__number',
      progressBar: 'multi-file-upload__progress-bar',
      uploadMore: 'multi-file-upload__upload-more-message',
      notifications: 'multi-file-upload__notifications'
    };

    this.errorManager = new ErrorManager();

    this.cacheElements();
    this.cacheTemplates();
    this.bindEvents();
  }

  private cacheElements(): void {
    this.itemList = this.container.querySelector(`.${this.classes.itemList}`);
    this.addAnotherBtn = this.container.querySelector(`.${this.classes.addAnother}`);
    this.uploadMoreMessage = this.container.querySelector(`.${this.classes.uploadMore}`);
    this.formStatus = this.container.querySelector(`.${this.classes.formStatus}`);
    this.submitBtn = this.container.querySelector(`.${this.classes.submitBtn}`);
    this.notifications = this.container.querySelector(`.${this.classes.notifications}`);
  }

  private cacheTemplates(): void {
    this.itemTpl = document.getElementById('multi-file-upload-item-tpl').textContent;
  }

  private bindEvents(): void {
    this.addAnotherBtn.addEventListener('click', this.handleAddItem.bind(this));
    this.container.addEventListener('submit', this.handleSubmit.bind(this));
  }

  private bindItemEvents(item: HTMLElement): void {
    this.getFileFromItem(item).addEventListener('change', this.handleFileChange.bind(this));
    this.getRemoveButtonFromItem(item).addEventListener('click', this.handleRemoveItem.bind(this));
  }

  public init(): void {
    this.updateButtonVisibility();
    this.removeAllItems();
    this.createInitialRows();
  }

  private createInitialRows(): void {
    let rowCount = 0;

    this.config.uploadedFiles.filter(file => file['fileStatus'] === 'ACCEPTED').forEach(fileData => {
      this.createUploadedItem(fileData);

      rowCount++;
    });

    if (rowCount < this.config.startRows) {
      for (let a = rowCount; a < this.config.startRows; a++) {
        this.addItemWithProvisioning();
      }
    }
    else if (rowCount < this.config.maxFiles) {
      this.addItemWithProvisioning();
    }
  }

  private createUploadedItem(fileData: unknown): HTMLElement {
    const item = this.addItem();
    const file = this.getFileFromItem(item);
    const fileName = this.extractFileName(fileData['fileName']);
    const filePreview = this.getFilePreviewElement(item);

    this.setItemState(item, UploadState.Uploaded);
    this.getFileNameElement(item).textContent = fileName;

    filePreview.textContent = fileName;
    filePreview.href = fileData['previewUrl'];

    file.dataset.multiFileUploadFileRef = fileData['reference'];

    return item;
  }

  private handleSubmit(e: Event): void {
    e.preventDefault();

    this.updateFormStatusVisibility();

    if (this.errorManager.hasErrors()) {
      return;
    }

    if (this.isBusy()) {
      this.addNotification(this.messages.stillTransferring);

      return;
    }

    if (this.container.querySelector(`.${this.classes.uploaded}`)) {
      window.location.href = this.config.actionUrl;
    }
    else {
      const firstFileInput = this.itemList.querySelector(`.${this.classes.file}`);
      this.errorManager.addError(firstFileInput.id, this.messages.noFilesUploadedError);
    }
  }

  private handleAddItem(): void {
    const item = this.addItemWithProvisioning();
    const file = this.getFileFromItem(item);

    file.focus();
  }

  private addItem(): HTMLElement {
    const item = parseHtml(this.itemTpl, {
      fileNumber: (this.getItems().length + 1).toString(),
      fileIndex: (++this.lastFileIndex).toString()
    }) as HTMLElement;

    this.bindItemEvents(item);
    this.itemList.append(item);

    this.updateButtonVisibility();

    return item;
  }

  private addItemWithProvisioning(): HTMLElement {
    const item = this.addItem();
    const file = this.getFileFromItem(item);

    this.provisionUpload(file);

    return item;
  }

  private handleRemoveItem(e: Event): void {
    const target = e.target as HTMLElement;
    const item = target.closest(`.${this.classes.item}`) as HTMLElement;
    const file = this.getFileFromItem(item);

    if (this.isUploaded(item) || this.isVerifying(item)) {
      this.setItemState(item, UploadState.Removing);
      this.requestRemoveFile(file);
    }
    else if (this.isUploading(item)) {
      if (this.uploadData[file.id].uploadHandle) {
        this.uploadData[file.id].uploadHandle.abort();
      }

      this.removeItem(item);
    }
    else {
      this.removeItem(item);
    }
  }

  private requestRemoveFile(file: HTMLInputElement) {
    const item = file.closest(`.${this.classes.item}`) as HTMLElement;

    fetch(this.getRemoveUrl(file.dataset.multiFileUploadFileRef), {
      method: 'PUT'
    })
      .then(this.requestRemoveFileCompleted.bind(this, file))
      .catch(() => {
        this.setItemState(item, UploadState.Uploaded);
        this.errorManager.addError(file.id, this.messages.couldNotRemoveFile);
      });
  }

  private requestRemoveFileCompleted(file: HTMLInputElement) {
    const item = file.closest(`.${this.classes.item}`) as HTMLElement;
    const message = parseTemplate(this.messages.documentDeleted, {
      fileName: this.getFileName(file)
    });

    this.addNotification(message);

    this.removeItem(item);
  }

  private removeItem(item: HTMLElement): void {
    const file = this.getFileFromItem(item);

    this.errorManager.removeError(file.id);
    item.remove();
    this.updateFileNumbers();
    this.updateButtonVisibility();
    this.updateFormStatusVisibility();

    if (this.getItems().length === 0) {
      this.addItemWithProvisioning();
    }

    delete this.uploadData[file.id];
  }

  private provisionUpload(file: HTMLInputElement): void {
    this.uploadData[file.id] = {};
    this.uploadData[file.id].provisionPromise = this.requestProvisionUpload(file);
  }

  private requestProvisionUpload(file: HTMLInputElement) {
    return fetch(this.getSendUrl(file.id), {
      method: 'PUT'
    })
      .then(response => response.json())
      .then(this.handleProvisionUploadCompleted.bind(this, file))
      .catch(this.delayedProvisionUpload.bind(this, file));
  }

  private delayedProvisionUpload(file: string): void {
    window.setTimeout(this.provisionUpload.bind(this, file), this.config.retryDelayMs);
  }

  private handleProvisionUploadCompleted(file: HTMLInputElement, response: unknown): void {
    const fileRef = response['upscanReference'];

    file.dataset.multiFileUploadFileRef = fileRef;

    this.uploadData[file.id].reference = fileRef;
    this.uploadData[file.id].fields = response['uploadRequest']['fields'];
    this.uploadData[file.id].url = response['uploadRequest']['href'];
    this.uploadData[file.id].retries = 0;
  }

  private handleFileChange(e: Event): void {
    const file = e.target as HTMLInputElement;
    const item = this.getItemFromFile(file);

    if (!file.files.length) {
      this.errorManager.removeError(file.id);

      return;
    }

    this.getFileNameElement(item).textContent = '';
    this.setItemState(item, UploadState.Uploading);

    this.uploadData[file.id].provisionPromise.then(() => {
      this.prepareFileUpload(file);
    });
  }

  private prepareFileUpload(file: HTMLInputElement): void {
    const item = this.getItemFromFile(file);
    const fileName = this.getFileName(file);

    this.updateButtonVisibility();
    this.errorManager.removeError(file.id);

    this.getFileNameElement(item).textContent = fileName;
    this.getFilePreviewElement(item).textContent = fileName;

    this.uploadData[file.id].uploadHandle = this.uploadFile(file);
  }

  private prepareFormData(file: HTMLInputElement, data): FormData {
    const formData = new FormData();

    for (const [key, value] of Object.entries(data.fields)) {
      formData.append(key, value as string);
    }

    formData.append('file', file.files[0]);

    return formData;
  }

  private uploadFile(file: HTMLInputElement): XMLHttpRequest {
    const xhr = new XMLHttpRequest();
    const fileRef = file.dataset.multiFileUploadFileRef;
    const data = this.uploadData[file.id];
    const formData = this.prepareFormData(file, data);
    const item = this.getItemFromFile(file);

    xhr.upload.addEventListener('progress', this.handleUploadFileProgress.bind(this, item));
    xhr.addEventListener('load', this.handleUploadFileCompleted.bind(this, fileRef));
    xhr.addEventListener('error', this.handleUploadFileError.bind(this, fileRef));
    xhr.open('POST', data.url);
    xhr.send(formData);

    return xhr;
  }

  private handleUploadFileProgress(item: HTMLElement, e: ProgressEvent): void {
    if (e.lengthComputable) {
      this.updateUploadProgress(item, e.loaded / e.total * 95);
    }
  }

  private handleUploadFileCompleted(fileRef: string): void {
    const file = this.getFileByReference(fileRef);
    const item = this.getItemFromFile(file);

    this.setItemState(item, UploadState.Verifying);
    this.delayedRequestUploadStatus(fileRef);
  }

  private handleUploadFileError(fileRef: string): void {
    const file = this.getFileByReference(fileRef);
    const item = this.getItemFromFile(file);

    this.setItemState(item, UploadState.Default);
    this.errorManager.addError(file.id, this.messages.genericError);
  }

  private requestUploadStatus(fileRef: string): void {
    const file = this.getFileByReference(fileRef);

    if (!Object.prototype.hasOwnProperty.call(this.uploadData, file.id)) {
      return;
    }

    fetch(this.getStatusUrl(fileRef), {
      method: 'GET'
    })
      .then(response => response.json())
      .then(this.handleRequestUploadStatusCompleted.bind(this, fileRef))
      .catch(this.delayedRequestUploadStatus.bind(this, fileRef));
  }

  private delayedRequestUploadStatus(fileRef: string): void {
    window.setTimeout(this.requestUploadStatus.bind(this, fileRef), this.config.retryDelayMs);
  }

  private handleRequestUploadStatusCompleted(fileRef: string, response: unknown): void {
    const file = this.getFileByReference(fileRef);
    const data = this.uploadData[file.id];
    const error = response['errorMessage'] || this.messages.genericError;

    switch (response['fileStatus']) {
      case 'ACCEPTED':
        this.handleFileStatusSuccessful(file, response['previewUrl']);
        break;

      case 'FAILED':
      case 'REJECTED':
      case 'DUPLICATE':
        this.handleFileStatusFailed(file, error);
        break;

      case 'NOT_UPLOADED':
      case 'WAITING':
      default:
        data.retries++;

        if (data.retries > this.config.maxRetries) {
          this.uploadData[file.id].retries = 0;

          this.handleFileStatusFailed(file, this.messages.genericError);
        }
        else {
          this.delayedRequestUploadStatus(fileRef);
        }

        break;
    }
  }

  private handleFileStatusSuccessful(file: HTMLInputElement, previewUrl: string) {
    const item = this.getItemFromFile(file);

    this.addNotification(parseTemplate(this.messages.documentUploaded, {
      fileName: this.getFileName(file)
    }));

    this.getFilePreviewElement(item).href = previewUrl;
    this.setItemState(item, UploadState.Uploaded);
    this.updateButtonVisibility();
    this.updateFormStatusVisibility();
  }

  private handleFileStatusFailed(file: HTMLInputElement, errorMessage: string) {
    const item = this.getItemFromFile(file);

    this.setItemState(item, UploadState.Default);
    this.updateFormStatusVisibility();
    this.errorManager.addError(file.id, errorMessage);
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

  private updateFormStatusVisibility() {
    toggleElement(this.formStatus, this.isBusy());
  }

  private updateUploadProgress(item, value): void {
    item.querySelector(`.${this.classes.progressBar}`).style.width = `${value}%`;
  }

  private toggleRemoveButtons(state: boolean): void {
    this.getItems().forEach(item => {
      const button = this.getRemoveButtonFromItem(item);

      if (this.isUploading(item) || this.isVerifying(item) || this.isUploaded(item)) {
        state = true;
      }

      toggleElement(button, state);
    });
  }

  private addNotification(message: string): void {
    const element = document.createElement('p');
    element.textContent = message;

    this.notifications.append(element);
  }

  private toggleAddButton(state: boolean): void {
    toggleElement(this.addAnotherBtn, state);
  }

  private toggleUploadMoreMessage(state: boolean): void {
    toggleElement(this.uploadMoreMessage, state);
  }

  private getItems(): HTMLElement[] {
    return Array.from(this.itemList.querySelectorAll(`.${this.classes.item}`));
  }

  private removeAllItems(): void {
    this.getItems().forEach(item => item.remove());
  }

  private getSendUrl(fileId: string): string {
    return parseTemplate(this.config.sendUrlTpl, { fileId: fileId });
  }

  private getStatusUrl(fileRef: string): string {
    return parseTemplate(this.config.statusUrlTpl, { fileRef: fileRef });
  }

  private getRemoveUrl(fileRef: string): string {
    return parseTemplate(this.config.removeUrlTpl, { fileRef: fileRef });
  }

  private getFileByReference(fileRef: string): HTMLInputElement {
    return this.itemList.querySelector(`[data-multi-file-upload-file-ref="${fileRef}"]`);
  }

  private getFileFromItem(item: HTMLElement): HTMLInputElement {
    return item.querySelector(`.${this.classes.file}`) as HTMLInputElement;
  }

  private getItemFromFile(file: HTMLInputElement): HTMLElement {
    return file.closest(`.${this.classes.item}`) as HTMLElement;
  }

  private getRemoveButtonFromItem(item: HTMLElement): HTMLButtonElement {
    return item.querySelector(`.${this.classes.remove}`) as HTMLButtonElement;
  }

  private getFileName(file: HTMLInputElement): string {
    const item = this.getItemFromFile(file);
    const fileName = this.getFileNameElement(item).textContent.trim();

    if (fileName.length) {
      return this.extractFileName(fileName);
    }

    if (file.value.length) {
      return this.extractFileName(file.value);
    }

    return null;
  }

  private getFileNameElement(item: HTMLElement): HTMLElement {
    return item.querySelector(`.${this.classes.fileName}`);
  }

  private getFilePreviewElement(item: HTMLElement): HTMLLinkElement {
    return item.querySelector(`.${this.classes.filePreview}`);
  }

  private extractFileName(fileName: string): string {
    return fileName.split(/([\\/])/g).pop();
  }

  private isBusy(): boolean {
    const stillUploading = this.container.querySelector(`.${this.classes.uploading}`);
    const stillRemoving = this.container.querySelector(`.${this.classes.removing}`);

    return stillUploading !== null || stillRemoving !== null;
  }

  private isUploading(item: HTMLElement): boolean {
    return item.classList.contains(this.classes.uploading);
  }

  private isVerifying(item: HTMLElement): boolean {
    return item.classList.contains(this.classes.verifying);
  }

  private isUploaded(item: HTMLElement): boolean {
    return item.classList.contains(this.classes.uploaded);
  }

  private setItemState(item: HTMLElement, uploadState: UploadState): void {
    const file = this.getFileFromItem(item);
    item.classList.remove(this.classes.uploading, this.classes.verifying, this.classes.uploaded, this.classes.removing);

    file.disabled = uploadState !== UploadState.Default;

    switch (uploadState) {
      case UploadState.Uploading:
        item.classList.add(this.classes.uploading);
        break;
      case UploadState.Verifying:
        item.classList.add(this.classes.verifying);
        break;
      case UploadState.Uploaded:
        item.classList.add(this.classes.uploaded);
        break;
      case UploadState.Removing:
        item.classList.add(this.classes.removing);
        break;
    }
  }
}
