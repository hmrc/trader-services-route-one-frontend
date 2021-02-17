import {Component} from './component';

export class PrintPage extends Component {
  constructor(form: HTMLFormElement) {
    super(form);
  }

  public init(): void {
    this.bindEvents();
  }

  private bindEvents(): void {
    this.container.addEventListener('click', () => {
      window.print();
    });
  }
}
