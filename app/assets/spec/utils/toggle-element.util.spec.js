import toggleElement from '../../javascripts/utils/toggle-element.util';

describe('toggleElement utility', () => {
  let element;

  afterEach(() => {
    element.remove();
  });

  describe('Given an element is visible', () => {
    beforeEach(() => {
      document.body.insertAdjacentHTML('afterbegin', `<div class="test-container"></div>`);

      element = document.querySelector('.test-container');
    });

    describe('When toggleElement is called with state="true"', () => {
      beforeEach(() => {
        toggleElement(element, true);
      });

      it('Then should not hide element', () => {
        expect(element.classList.contains('hidden')).toEqual(false);
      });
    });

    describe('When toggleElement is called with state="false"', () => {
      beforeEach(() => {
        toggleElement(element, false);
      });

      it('Then should hide element', () => {
        expect(element.classList.contains('hidden')).toEqual(true);
      });
    });
  });

  describe('Given an element is hidden', () => {
    beforeEach(() => {
      document.body.insertAdjacentHTML('afterbegin', `<div class="test-container hidden"></div>`);

      element = document.querySelector('.test-container');
    });

    describe('When toggleElement is called with state="true"', () => {
      beforeEach(() => {
        toggleElement(element, true);
      });

      it('Then should show element', () => {
        expect(element.classList.contains('hidden')).toEqual(false);
      });
    });

    describe('When toggleElement is called with state="false"', () => {
      beforeEach(() => {
        toggleElement(element, false);
      });

      it('Then should not show element', () => {
        expect(element.classList.contains('hidden')).toEqual(true);
      });
    });
  });
});
