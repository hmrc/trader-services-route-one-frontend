$(document).ready(function () {
  var Upload = function () {
    this.$form = $('.upload-status');

    if (!this.$form.length) {
      return;
    }

    this.config = {
      retryTimeoutMs: this.$form.data('upload-status-retry-timeout-ms') || 1000,
      successUrl: this.$form.data('upload-status-redirect-success-url'),
      failureUrl: this.$form.data('upload-status-redirect-failure-url'),
      checkStatusUrl: this.$form.data('upload-status-check-status-url')
    };

    this.requestUploadStatus();
  };

  Upload.prototype.requestUploadStatus = function () {
    var self = this;

    window.setTimeout(function() {
      $.ajax({
        url: self.config.checkStatusUrl,
        type: "GET",
        data: {},
        processData: false,
        contentType: false,
        crossDomain: true
      })
      .fail($.proxy(self.requestUploadStatus, self))
      .done($.proxy(self.handleRequestCompleted, self));

    }, this.config.retryTimeoutMs);
  };

  Upload.prototype.handleRequestCompleted = function(response) {
    //TODO get the correct response property and its values
    switch (response.status) {
      case 'success':
        window.location.href = this.config.successUrl;
        break;

      case 'failure':
        window.location.href = this.config.failureUrl;
        break;

      case 'pending':
      default:
        this.requestUploadStatus();
        break;
    }
  };

  new Upload();
});
