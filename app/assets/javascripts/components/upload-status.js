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
    switch (response['fileStatus']) {
      case 'ACCEPTED':
        window.location.href = this.config.successUrl;
        break;

      case 'REJECTED':
        window.location.href = this.config.failureUrl;
        break;

      case 'WAITING':
      default:
        this.requestUploadStatus();
        break;
    }
  };

  new Upload();
});
