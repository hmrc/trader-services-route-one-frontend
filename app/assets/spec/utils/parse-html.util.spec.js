import parseHtml from '../../javascripts/utils/parse-html.util';

describe('parseHtml utility', () => {
  let template;
  let html;

  describe('Given a template contains a div with a paragraph', () => {
    beforeEach(() => {
      template = '<div class="div-class"><p>Content</p></div>';
    });

    describe('When parseHtml is called', () => {
      beforeEach(() => {
        html = parseHtml(template, {});
      });

      it('Then should return a "div" element', () => {
        expect(html instanceof HTMLDivElement).toEqual(true);
      });

      it('Then returned element should have a class "div"', () => {
        expect(html.classList.contains('div-class')).toEqual(true);
      });

      it('Then returned element should contain a "p" element', () => {
        expect(html.firstChild instanceof HTMLParagraphElement).toEqual(true);
      });

      it('Then child element should contain text "Content"', () => {
        expect(html.firstChild.textContent).toEqual("Content");
      });
    });
  });

  describe('Given a template contains a placeholder', () => {
    beforeEach(() => {
      template = '<p id="item-{number}">Item {number}</p>';
    });

    describe('When parseHtml is called with {number: 1}', () => {
      beforeEach(() => {
        html = parseHtml(template, {
          number: '1'
        });
      });

      it('Then returned element should have an id "item-1"', () => {
        expect(html.id).toEqual('item-1');
      });

      it('Then returned element should contain text "Item 1"', () => {
        expect(html.textContent).toEqual('Item 1');
      });
    });
  });
});
