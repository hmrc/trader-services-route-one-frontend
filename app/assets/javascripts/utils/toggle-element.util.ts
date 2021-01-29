const hiddenClass = 'hidden';

export default function toggleElement(element: HTMLElement, state: boolean): void {
  element.classList.toggle(hiddenClass, !state);
}
