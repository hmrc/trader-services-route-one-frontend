import parseHtml from '../utils/parse-html.util';
import {ErrorList} from '../interfaces/error-list.interface';
import {KeyValue} from '../interfaces/key-value.interface';

/*
TODO use server-side-generated templates
*/
export default class ErrorManager {
  private classes: KeyValue;
  private errorSummaryTpl: string;
  private errorSummaryItemTpl: string;
  private errorMessageTpl: string;
  private errorSummary: HTMLElement;
  private errorSummaryList: HTMLUListElement;
  private errors: ErrorList = {};

  constructor() {
    this.classes = {
      inputContainer: 'govuk-form-group',
      inputContainerError: 'govuk-form-group--error',
      errorSummaryList: 'govuk-error-summary__list',
      label: 'govuk-label'
    };

    this.cacheTemplates();
    this.cacheElements();
  }

  private static getH1() {
    return document.querySelector('h1');
  }

  private cacheTemplates(): void {
    this.errorSummaryTpl = `
    <div class="govuk-error-summary" aria-labelledby="error-summary-title" role="alert" tabindex="-1" data-module="govuk-error-summary">
      <h2 class="govuk-error-summary__title" id="error-summary-title">
        There is a problem
      </h2>
      <div class="govuk-error-summary__body">
        <ul class="govuk-list govuk-error-summary__list">

        </ul>
      </div>
    </div>
    `;

    this.errorSummaryItemTpl = `
      <li>
        <a href="#{inputId}">{errorMessage}</a>
      </li>
    `;

    this.errorMessageTpl = `
      <span id="contactEmail-error" class="govuk-error-message">
         <span class="govuk-visually-hidden">Error:</span>
         <span class="multi-file-upload__error-message">{errorMessage}</span>
      </span>
    `;
  }

  private cacheElements(): void {
    this.errorSummary = parseHtml(this.errorSummaryTpl, {});
    this.errorSummaryList = this.errorSummary.querySelector(`.${this.classes.errorSummaryList}`);
  }

  public addError(message: string, inputId: string): void {
    const errorMessage = this.addErrorToField(message, inputId);
    const errorSummaryRow = this.addErrorToSummary(message, inputId);

    this.removeError(inputId);

    this.errors[inputId] = {
      errorMessage: errorMessage,
      errorSummaryRow: errorSummaryRow
    };

    this.updateErrorSummaryVisibility();
  }

  public removeError(inputId: string): void {
    if(!Object.prototype.hasOwnProperty.call(this.errors, inputId)) {
      return;
    }

    const error = this.errors[inputId];
    const input = document.getElementById(inputId);
    const inputContainer = this.getContainer(input);

    error.errorMessage.remove();
    error.errorSummaryRow.remove();

    inputContainer.classList.remove(this.classes.inputContainerError);

    delete this.errors[inputId];

    this.updateErrorSummaryVisibility();
  }

  private addErrorToField(message: string, inputId: string): HTMLElement {
    const input = document.getElementById(inputId);
    const inputContainer = this.getContainer(input);
    const label = this.getLabel(inputContainer);

    const errorMessage = parseHtml(this.errorMessageTpl, {
      errorMessage: message
    });

    inputContainer.classList.add(this.classes.inputContainerError);

    label.after(errorMessage);

    return errorMessage;
  }

  private addErrorToSummary(message: string, inputId: string): HTMLElement {
    const summaryRow = parseHtml(this.errorSummaryItemTpl, {
      inputId: inputId,
      errorMessage: message
    });

    this.errorSummaryList.append(summaryRow);

    return summaryRow;
  }

  private updateErrorSummaryVisibility(): void {
    if (Object.entries(this.errors).length) {
      ErrorManager.getH1().before(this.errorSummary);
    }
    else {
      this.errorSummary.remove();
    }
  }

  private getContainer(input: HTMLElement): HTMLElement {
    return input.closest(`.${this.classes.inputContainer}`) as HTMLElement;
  }

  private getLabel(container: HTMLElement): HTMLLabelElement {
    return container.querySelector(`.${this.classes.label}`);
  }
}
