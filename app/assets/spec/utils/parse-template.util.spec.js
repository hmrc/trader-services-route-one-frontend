import parseTemplate from '../../javascripts/utils/parse-template.util';

describe('parseTemplate utility', () => {
  let template;

  describe('Given a template contains a placeholder', () => {
    beforeEach(() => {
      template = 'A quick brown {placeholder} jumps over the lazy dog';
    });

    describe('When parseTemplate is called with placeholder="fox"', () => {
      beforeEach(() => {
        template = parseTemplate(template, {
          placeholder: 'fox'
        });
      });

      it('Then template should equal to "A quick brown fox jumps over the lazy dog"', () => {
        expect(template).toEqual('A quick brown fox jumps over the lazy dog');
      });
    });

    describe('When parseTemplate is called without providing a substitute for the placeholder', () => {
      beforeEach(() => {
        template = parseTemplate(template, {});
      });

      it('Then template should equal to "A quick brown {placeholder} jumps over the lazy dog"', () => {
        expect(template).toEqual('A quick brown {placeholder} jumps over the lazy dog');
      });
    });
  });

  describe('Given a template contains multiple placeholders', () => {
    beforeEach(() => {
      template = 'A quick brown {placeholder} jumps over the lazy {placeholder2}';
    });

    describe('When parseTemplate is called with placeholder="fox" and placeholder2="dog"', () => {
      beforeEach(() => {
        template = parseTemplate(template, {
          placeholder: 'fox',
          placeholder2: 'dog'
        });
      });

      it('Then template should equal to "A quick brown fox jumps over the lazy dog"', () => {
        expect(template).toEqual('A quick brown fox jumps over the lazy dog');
      });
    });
  });

  describe('Given a template contains multiple instances of a placeholder', () => {
    beforeEach(() => {
      template = '{placeholder}, {placeholder}, {placeholder} your boat';
    });

    describe('When parseTemplate is called with placeholder="row"', () => {
      beforeEach(() => {
        template = parseTemplate(template, {
          placeholder: 'row'
        });
      });

      it('Then template should equal to "row, row, row your boat"', () => {
        expect(template).toEqual('row, row, row your boat');
      });
    });
  });
});
