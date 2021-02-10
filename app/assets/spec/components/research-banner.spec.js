import {ResearchBanner} from '../../javascripts/components/research-banner';

describe('Research Banner component', () => {
  let instance;
  let container;

  describe('Given the research-banner component is present in DOM', () => {
    beforeEach(() => {
      document.body.insertAdjacentHTML('afterbegin', `
        <div class="ssp-research-banner">
          <button class="ssp-research-banner__button">Close</button>
        </div>
      `);

      container = document.querySelector('.ssp-research-banner');
    });

    afterEach(() => {
      document.cookie = 'research-banner=;expires=Thu, 01 Jan 1970 00:00:01 GMT;';

      container.remove();
    });

    describe('And research-banner cookie is not set', () => {
      describe('When the component is initialised', () => {
        beforeEach(() => {
          instance = new ResearchBanner(container);
          instance.init();
        });

        it('Then should not hide the component', () => {
          expect(container.classList.contains('hidden')).toEqual(false);
        });

        describe('And close button is clicked', () => {
          beforeEach(() => {
            container.querySelector('.ssp-research-banner__button').click();
          });

          it('Then should hide the component', () => {
            expect(container.classList.contains('hidden')).toEqual(true);
          });

          it('Then should set the research-banner cookie', () => {
            expect(document.cookie).toEqual('research-banner=dismissed');
          });
        });
      });
    });

    describe('And research-banner cookie is set', () => {
      beforeEach(() => {
        document.cookie = 'research-banner=dismissed';
      });

      describe('When the component is initialised', () => {
        beforeEach(() => {
          instance = new ResearchBanner(container);
          instance.init();
        });

        it('Then should hide the component', () => {
          expect(container.classList.contains('hidden')).toEqual(true);
        });
      });
    });
  });
});
