import {MultiFileUpload} from '../../javascripts/components/multi-file-upload';

function createFileList(files) {
  const dt = new DataTransfer();

  for (let i = 0, len = files.length; i < len; i++) {
    dt.items.add(files[i]);
  }

  return dt.files;
}

function getStatusResponse() {
  return {
    fileStatus: 'ACCEPTED'
  };
}

function getProvisionResponse() {
  return {
    upscanReference: '123',
    uploadRequest: {
      fields: {},
      href: 'uploadUrl'
    }
  };
}

describe('Multi File Upload component', () => {
  let instance;
  let container;
  let item;
  let item2;
  let input;

  describe('Given multi-file-upload component and its templates are present in DOM', () => {
    beforeEach(() => {
      document.body.insertAdjacentHTML('afterbegin', `
        <form class="multi-file-upload"
          data-multi-file-upload-document-uploaded="Document {fileName} has been uploaded"
          data-multi-file-upload-document-deleted="Document {fileName} has been deleted"
          >
          <ul class="multi-file-upload__item-list"></ul>
          
          <button type="button" class="multi-file-upload__add-another govuk-button govuk-button--secondary">Add another document</button>
          
          <p class="govuk-body multi-file-upload__upload-more-message hidden">To upload more...</p>
          
          <p class="govuk-body multi-file-upload__form-status hidden" aria-hidden="true">
            Still transferring...
            <span class="file-upload__spinner ccms-loader"></span>
          </p>
          
          <div class="multi-file-upload__notifications govuk-visually-hidden" aria-live="polite"></div>
          
          <script type="text/x-template" id="multi-file-upload-item-tpl"> 
            <li class="multi-file-upload__item">
              <div class="govuk-form-group">
                <label class="govuk-label" for="file-{fileIndex}">Document <span class="multi-file-upload__number">{fileNumber}</span></label>
                <div class="multi-file-upload__item-content">
                  <div class="multi-file-upload__file-container">
                    <input class="multi-file-upload__file govuk-file-upload" type="file" id="file-{fileIndex}">
                    <span class="multi-file-upload__file-name"></span>
                    <a class="multi-file-upload__file-preview"></a>
                  </div>
            
                  <div class="multi-file-upload__meta-container">
                    <div class="multi-file-upload__status">
                      <span class="multi-file-upload__progress">
                        <span class="multi-file-upload__progress-bar"></span>
                      </span>
                      <span class="multi-file-upload__tag govuk-tag">Uploaded</span>
                    </div>
            
                    <button type="button" class="multi-file-upload__remove-item govuk-link">
                      Remove
                      <span class="govuk-visually-hidden">document <span class="multi-file-upload__number">{fileNumber}</span></span>
                    </button>
                    <span class="multi-file-upload__removing">Removing...</span>
                  </div>
                </div>
              </div>
            </li>
          </script>
          
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
        </form>
      `);

      container = document.querySelector('.multi-file-upload');
    });

    afterEach(() => {
      container.remove();
    });

    describe('And data-multi-file-upload-min-files is set to 1', () => {
      beforeEach(() => {
        container.dataset.multiFileUploadMinFiles = '1';
      });

      describe('When component is initialised', () => {
        beforeEach(() => {
          instance = new MultiFileUpload(container);
          instance.init();
        });

        it('Then one row should be present', () => {
          expect(container.querySelectorAll('.multi-file-upload__item').length).toEqual(1);
        });
      });

      describe('And data-multi-file-upload-max-files is set to 2', () => {
        beforeEach(() => {
          container.dataset.multiFileUploadMaxFiles = '2';
        });

        describe('And component is initialised', () => {
          beforeEach(() => {
            instance = new MultiFileUpload(container);
            instance.init();
          });

          describe('When "Add another" button is clicked', () => {
            beforeEach(() => {
              container.querySelector('.multi-file-upload__add-another').click();
            });

            it('Then 2 rows should be present', () => {
              expect(container.querySelectorAll('.multi-file-upload__item').length).toEqual(2);
            });

            it('Then "Add another" button should be hidden', () => {
              const addAnotherBtn = container.querySelector('.multi-file-upload__add-another');

              expect(addAnotherBtn.classList.contains('hidden')).toEqual(true);
            });

            it('Then "How to add more" help text should be visible', () => {
              const helpText = container.querySelector('.multi-file-upload__upload-more-message');

              expect(helpText.classList.contains('hidden')).toEqual(false);
            });
          });
        });
      });
    });

    describe('And component is initialised', () => {
      beforeEach(() => {
        instance = new MultiFileUpload(container);

        instance.init();

        item = container.querySelector('.multi-file-upload__item');
        input = container.querySelector('.multi-file-upload__file');
      });

      describe('When user selects a file', () => {
        beforeEach((done) => {
          spyOn(instance, 'requestProvisionUpload').and.callFake((file) => {
            const response = getProvisionResponse();
            const promise = Promise.resolve(response);

            promise.then(() => {
              instance.handleProvisionUploadCompleted(file, response);
              done();
            });

            return promise;
          });

          spyOn(instance, 'uploadFile');

          input.files = createFileList([new File([''], '/path/to/test.txt')]);
          input.dispatchEvent(new Event('change'));
          done();
        });

        it('Then file upload should get provisioned', () => {
          expect(instance.requestProvisionUpload).toHaveBeenCalled();
        });

        it('Then item should be in "uploading" state', (done) => {
          expect(item.classList.contains('multi-file-upload__item--uploading')).toEqual(true);
          done();
        });

        it('Then input should have data prop multiFileUploadFileRef="123"', (done) => {
          expect(input.dataset.multiFileUploadFileRef).toEqual('123');
          done();
        });

        it('Then uploadFile should have been called', (done) => {
          expect(instance.uploadFile).toHaveBeenCalled();
          done();
        });

        it('Then fileName should contain "test.txt"', (done) => {
          const fileName = container.querySelector('.multi-file-upload__file-name');
          expect(fileName.textContent).toEqual('test.txt');
          done();
        });
      });
    });

    describe('And component is initialised', () => {
      beforeEach(() => {
        instance = new MultiFileUpload(container);

        spyOn(instance, 'uploadFile').and.callFake((file) => {
          instance.handleUploadFileCompleted(file.dataset.multiFileUploadFileRef);
        });
        spyOn(instance, 'requestUploadStatus').and.callFake((fileRef) => {
          instance.handleRequestUploadStatusCompleted(fileRef, getStatusResponse());
        });
        spyOn(instance, 'delayedRequestUploadStatus').and.callFake((fileRef) => {
          instance.requestUploadStatus(fileRef);
        });

        instance.init();

        item = container.querySelector('.multi-file-upload__item');
        input = container.querySelector('.multi-file-upload__file');
      });

      describe('When file is uploaded', () => {
        beforeEach((done) => {
          spyOn(instance, 'requestProvisionUpload').and.callFake((file) => {
            const response = getProvisionResponse();
            const promise = Promise.resolve(response);

            promise.then(() => {
              instance.handleProvisionUploadCompleted(file, response);
              done();
            });

            return promise;
          });

          input.files = createFileList([new File([''], '/path/to/test.txt')]);
          input.dispatchEvent(new Event('change'));
          done();
        });

        it('Then item should be in "uploaded" state', (done) => {
          expect(item.classList.contains('multi-file-upload__item--uploaded')).toEqual(true);
          done();
        });

        it('Then "file uploaded" message is placed in aria live region', (done) => {
          const notifications = container.querySelector('.multi-file-upload__notifications');
          expect(notifications.textContent.trim()).toEqual("Document test.txt has been uploaded");
          done();
        });
      });
    });

    describe('And there is one initially uploaded file', () => {
      beforeEach(() => {
        container.dataset.multiFileUploadUploadedFiles = JSON.stringify([{
          fileStatus: 'ACCEPTED',
          fileName: 'test.txt',
          reference: '123'
        }]);
      });

      describe('When component is initialised', () => {
        beforeEach(() => {
          instance = new MultiFileUpload(container);

          spyOn(instance, 'requestProvisionUpload').and.returnValue(Promise.resolve(getProvisionResponse()));

          instance.init();

          item = container.querySelector('.multi-file-upload__item');
          item2 = container.querySelector('.multi-file-upload__item:nth-of-type(2)');
          input = container.querySelector('.multi-file-upload__file');
        });

        it('Then first item should be in "uploaded" state', () => {
          expect(item.classList.contains('multi-file-upload__item--uploaded')).toEqual(true);
        });

        it('Then input should have data prop multiFileUploadFileRef="123"', () => {
          expect(input.dataset.multiFileUploadFileRef).toEqual('123');
        });

        it('Then fileName should contain "test.txt"', () => {
          const fileName = container.querySelector('.multi-file-upload__file-name');
          expect(fileName.textContent).toEqual('test.txt');
        });

        it('Then second item should exist', () => {
          expect(item2 instanceof HTMLLIElement).toEqual(true);
        });
      });

      describe('And component is initialised', () => {
        beforeEach(() => {
          instance = new MultiFileUpload(container);

          spyOn(instance, 'requestProvisionUpload').and.returnValue(Promise.resolve(getProvisionResponse()));
          spyOn(instance, 'requestRemoveFile');

          instance.init();

          item = container.querySelector('.multi-file-upload__item');
          input = container.querySelector('.multi-file-upload__file');
        });

        describe('When "Remove" is clicked', () => {
          beforeEach(() => {
            item.querySelector('.multi-file-upload__remove-item').click();
          });

          it('Then item should be in "removing" state', () => {
            expect(item.classList.contains('multi-file-upload__item--removing')).toEqual(true);
          });

          it('Then requestRemoveFile should have been called', () => {
            expect(instance.requestRemoveFile).toHaveBeenCalled();
          });
        });
      });

      describe('And component is initialised', () => {
        beforeEach(() => {
          instance = new MultiFileUpload(container);

          spyOn(instance, 'requestProvisionUpload').and.returnValue(Promise.resolve(getProvisionResponse()));
          spyOn(instance, 'requestRemoveFile').and.callFake((file) => {
            instance.requestRemoveFileCompleted(file);
          });

          instance.init();

          item = container.querySelector('.multi-file-upload__item');
          input = container.querySelector('.multi-file-upload__file');
        });

        describe('When file is removed', () => {
          beforeEach(() => {
            item.querySelector('.multi-file-upload__remove-item').click();
          });

          it('Then item should be removed', () => {
            expect(item.parentNode).toEqual(null);
          });

          it('Then "file deleted" message is placed in aria live region', () => {
            const notifications = container.querySelector('.multi-file-upload__notifications');
            expect(notifications.textContent.trim()).toEqual("Document test.txt has been deleted");
          });

          it('Then new item should be added', () => {
            expect(container.querySelectorAll('.multi-file-upload__item').length).toEqual(1);
          });
        });
      });
    });
  });
});
