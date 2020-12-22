'use strict';

(function() {
  var BANNER_COOKIE = "mtdpurr";
  var BANNER_QUERY = "div.ssp-research-banner";
  var LINK_QUERY = ".ssp-research-banner--close-link a";
  var SECONDS_IN_DAY = 86400;

  var banner = document.querySelector(BANNER_QUERY);
  var closeLink = document.querySelector(LINK_QUERY);

  function getCookie(name) {
    var key = name + "=";
    var cookies = decodeURIComponent(document.cookie).split(';');

    for (var i = 0; i < cookies.length; i++) {
      var cookie = cookies[i];
      if (cookie.includes(key)) {
        var startChar = cookie.indexOf(key) + key.length;
        return cookie.substring(startChar, cookie.length);
      }
    }

    return null;
  }

  function setCookie(name, value, maxAgeInDays) {
    var maxAge = (maxAgeInDays === 'undefined') ? "" : ";max-age=" + (SECONDS_IN_DAY * maxAgeInDays);
    if (getCookie(name) === null) {
      document.cookie = name + "=" + value + maxAge;
    }
  }

  if (getCookie(BANNER_COOKIE) == null) {
    banner.style.display = "block";
  }

  closeLink.addEventListener("click", function(event) {
    banner.style.display = "none";
    setCookie(BANNER_COOKIE, "suppress_for_all_services", 28)
  });

})();
