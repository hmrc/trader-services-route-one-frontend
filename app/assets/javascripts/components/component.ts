export abstract class Component {
  protected container: HTMLElement;

  protected constructor(container: HTMLElement) {
    this.container = container;
  }
}
