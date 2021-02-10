import ErrorManager from '../../javascripts/tools/error-manager.tool';

describe('ErrorManager tool', () => {
  let container;
  let errorManager;

  afterEach(() => {
    container.remove();
  });

  describe('Given an h1 and errorManager templates are present in DOM', () => {
    beforeEach(() => {
      document.body.insertAdjacentHTML('afterbegin', `
        <div class="test-container">
          <h1></h1>
          
          <script type="text/x-template" id="error-manager-summary-tpl">
            <div class="govuk-error-summary">
              <ul class="govuk-list govuk-error-summary__list"></ul>
            </div>
          </script>

          <script type="text/x-template" id="error-manager-summary-item-tpl">
            <li>
              <a href="#{inputId}">{errorMessage}</a>
            </li>
          </script>

          <script type="text/x-template" id="error-manager-message-tpl">
            <span class="govuk-error-message">
              <span class="multi-file-upload__error-message">{errorMessage}</span>
            </span>
          </script>
        </div>
      `);

      container = document.querySelector('.test-container');
    });

    describe('And a form with one field is present in DOM', () => {
      beforeEach(() => {
        container.insertAdjacentHTML('beforeend', `
          <form>
            <div class="govuk-form-group" id="item">
              <label class="govuk-label"></label>
              <input id="input">
            </div>
          </form>
        `);
      });

      describe('And errorManager is instantiated', () => {
        beforeEach(() => {
          errorManager = new ErrorManager();
        });

        describe('When an error is added to the field', () => {
          beforeEach(() => {
            errorManager.addError('input', 'message');
          });

          it('Then the summary is attached before h1', () => {
            const h1 = document.querySelector('h1');
            const nodeBeforeH1 = h1.previousSibling;

            expect(nodeBeforeH1.classList.contains('govuk-error-summary')).toEqual(true);
          });

          it('Then an error message is added to the summary', () => {
            const summary = document.querySelector('.govuk-error-summary');

            expect(summary.querySelector('[href="#input"]').textContent.trim()).toEqual('message');
          });

          it('Then an error is added to the field', () => {
            const item = document.getElementById('item');

            expect(item.querySelector('.govuk-error-message').textContent.trim()).toEqual('message');
          });
        });

        describe('And an error is already added to the field', () => {
          beforeEach(() => {
            errorManager.addError('input', 'message');
          });

          describe('When a new error is added the the same field', () => {
            beforeEach(() => {
              errorManager.addError('input', 'message2');
            });

            it('Then the previous error gets removed from the summary', () => {
              const summary = document.querySelector('.govuk-error-summary');

              expect(summary.querySelector('[href="#input"]').textContent.trim()).not.toEqual('message');
            });

            it('Then the previous error gets removed from the field', () => {
              const item = document.getElementById('item');

              expect(item.querySelector('.govuk-error-message').textContent.trim()).not.toEqual('message');
            });
          });

          describe('When the error is removed from the field', () => {
            beforeEach(() => {
              errorManager.removeError('input');
            });

            it('Then the error gets removed from the summary', () => {
              expect(document.querySelector('.govuk-error-summary [href="#input"]')).toEqual(null);
            });

            it('Then the error gets removed from the field', () => {
              expect(document.querySelector('#item .govuk-error-message')).toEqual(null);
            });

            it('Then the summary gets detached from DOM', () => {
              expect(document.querySelector('govuk-error-summary')).toEqual(null);
            });
          });
        });
      });
    });

    describe('And a form with two fields is present in DOM', () => {
      beforeEach(() => {
        container.insertAdjacentHTML('beforeend', `
          <form>
            <div class="govuk-form-group" id="item1">
              <label class="govuk-label"></label>
              <input id="input1">
            </div>
            
            <div class="govuk-form-group" id="item2">
              <label class="govuk-label"></label>
              <input id="input2">
            </div>
          </form>
        `);
      });

      describe('And errorManager is instantiated', () => {
        beforeEach(() => {
          errorManager = new ErrorManager();
        });

        describe('And an error is already added to each field', () => {
          beforeEach(() => {
            errorManager.addError('input1', 'message1');
            errorManager.addError('input2', 'message2');
          });

          describe('When one error is removed and the other remains', () => {
            beforeEach(() => {
              errorManager.removeError('input1');
            });

            it('Then the summary is not detached from DOM', () => {
              expect(document.querySelectorAll('.govuk-error-summary').length).toEqual(1);
            });
          });
        });
      });
    });
  });
});
