import {ResearchBanner} from '../../javascripts/components/research-banner';

describe('Research banner component', () => {
  let componentInstance;
  let componentContainer;

  describe('Given the research-banner component is present in DOM', () => {
    beforeEach(() => {
      document.body.insertAdjacentHTML('afterbegin', `
        <div class="ssp-research-banner">
          <button class="ssp-research-banner__button">Close</button>
        </div>
      `);

      componentContainer = document.querySelector('.ssp-research-banner');
    });

    afterEach(() => {
      document.cookie = 'research-banner=;expires=Thu, 01 Jan 1970 00:00:01 GMT;';

      componentContainer.remove();
    });

    describe('And research-banner cookie is not set', () => {
      describe('When the component is initialised', () => {
        beforeEach(() => {
          componentInstance = new ResearchBanner(componentContainer);
        });

        it('Should not hide the component', () => {
          expect(componentContainer.classList.contains('hidden')).toEqual(false);
        });

        describe('And close button is clicked', () => {
          beforeEach(() => {
            componentContainer.querySelector('.ssp-research-banner__button').click();
          });

          it('Should hide the component', () => {
            expect(componentContainer.classList.contains('hidden')).toEqual(true);
          });

          it('Should set the research-banner cookie', () => {
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
          componentInstance = new ResearchBanner(componentContainer);
        });

        it('Should hide the component', () => {
          expect(componentContainer.classList.contains('hidden')).toEqual(true);
        });
      });
    });
  });
});
