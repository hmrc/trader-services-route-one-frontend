/**
 * TAKEN FROM ASSETS_FRONTEND as currentlly not supported in GDS Design System
 * https://github.com/hmrc/assets-frontend/tree/master/assets/patterns/help-users-when-we-time-them-out-of-a-service
 * */

function displayDialog(elementToDisplay) {
  var $dialog = $('<div id="timeout-dialog" tabindex="-1" role="dialog" class="timeout-dialog">')
      .append(elementToDisplay)
  var $overlay = $('<div id="timeout-overlay" class="timeout-overlay">')
  var resetElementsFunctionList = []
  var closeCallbacks = []

  if (!$('html').hasClass('noScroll')) {
    $('html').addClass('noScroll')
    resetElementsFunctionList.push(function () {
      $('html').removeClass('noScroll')
    })
  }
  $('body').append($dialog).append($overlay)

  resetElementsFunctionList.push(function () {
    $dialog.remove()
    $overlay.remove()
  })

  // disable the non-dialog page to prevent confusion for VoiceOver users
  $('#skiplink-container, body>header, #global-cookie-message, body>main, body>footer').each(function () {
    var value = $(this).attr('aria-hidden')
    var $elem = $(this)
    resetElementsFunctionList.push(function () {
      if (value) {
        $elem.attr('aria-hidden', value)
      } else {
        $elem.removeAttr('aria-hidden')
      }
    })
  }).attr('aria-hidden', 'true')

  setupFocusHandlerAndFocusDialog()
  setupKeydownHandler()
  preventMobileScrollWhileAllowingPinchZoom()

  function close() {
    while (resetElementsFunctionList.length > 0) {
      var fn = resetElementsFunctionList.shift()
      fn()
    }
  }

  function closeAndInform() {
    $.each(closeCallbacks, function () {
      var fn = this
      fn()
    })
    close()
  }

  function setupFocusHandlerAndFocusDialog() {
    function keepFocus(event) {
      var modalFocus = document.getElementById('timeout-dialog')
      if (modalFocus) {
        if (event.target !== modalFocus && !modalFocus.contains(event.target)) {
          event.stopPropagation()
          modalFocus.focus()
        }
      }
    }

    var elemToFocusOnReset = document.activeElement
    $dialog.focus()

    $(document).on('focus', '*', keepFocus)

    resetElementsFunctionList.push(function () {
      $(document).off('focus', '*', keepFocus)
      $(elemToFocusOnReset).focus()
    })
  }

  function setupKeydownHandler() {
    function keydownListener(e) {
      if (e.keyCode === 27) {
        closeAndInform()
      }
    }

    $(document).on('keydown', keydownListener)

    resetElementsFunctionList.push(function () {
      $(document).off('keydown', keydownListener)
    })
  }

  function preventMobileScrollWhileAllowingPinchZoom() {
    function handleTouch(e) {
      var touches = e.originalEvent.touches || e.originalEvent.changedTouches || []

      if (touches.length === 1) {
        e.preventDefault()
      }
    }

    $(document).on('touchmove', handleTouch)

    resetElementsFunctionList.push(function () {
      $(document).off('touchmove', handleTouch)
    })
  }

  function createSetterFunctionForAttributeOfDialog(attributeName) {
    return function (value) {
      if (value) {
        $dialog.attr(attributeName, value)
      } else {
        $dialog.removeAttr(attributeName)
      }
    }
  }

  return {
    closeDialog: function () {
      close()
    },
    setAriaLive: createSetterFunctionForAttributeOfDialog('aria-live'),
    setAriaLabelledBy: createSetterFunctionForAttributeOfDialog('aria-labelledby'),
    addCloseHandler: function (closeHandler) {
      closeCallbacks.push(closeHandler)
    }
  }
}

function timeoutDialog(options) {

  validateInput(options)

  var cleanupFunctions = []
  var localisedDefaults = readCookie('PLAY_LANG') && readCookie('PLAY_LANG') === 'cy' && {
    title: undefined,
    message: 'Er eich diogelwch, byddwn yn eich allgofnodi cyn pen',
    keepAliveButtonText: 'Parhau i fod wedi’ch mewngofnodi',
    signOutButtonText: 'Allgofnodi',
    properties: {
      minutes: 'funud',
      minute: 'funud',
      seconds: 'eiliad',
      second: 'eiliad'
    }
  } || {
    title: undefined,
    message: 'For your security, we will sign you out in',
    keepAliveButtonText: 'Stay signed in',
    signOutButtonText: 'Sign out',
    properties: {
      minutes: 'minutes',
      minute: 'minute',
      seconds: 'seconds',
      second: 'second'
    }
  }

  var settings = mergeOptionsWithDefaults(options, localisedDefaults)

  setupDialogTimer()

  function validateInput(config) {
    var requiredConfig = ['timeout', 'countdown', 'keepAliveUrl', 'signOutUrl']
    var missingRequiredConfig = []

    $.each(requiredConfig, function () {
      if (!config.hasOwnProperty(this)) {
        missingRequiredConfig.push(this)
      }
    })

    if (missingRequiredConfig.length > 0) {
      throw new Error('Missing config item(s): [' + missingRequiredConfig.join(', ') + ']')
    }
  }

  function mergeOptionsWithDefaults(options, localisedDefaults) {
    return $.extend({}, localisedDefaults, options)
  }

  function setupDialogTimer() {
    settings.signout_time = getDateNow() + settings.timeout * 1000

    var timeout = window.setTimeout(function () {
      setupDialog()
    }, ((settings.timeout) - (settings.countdown)) * 1000)

    cleanupFunctions.push(function () {
      window.clearTimeout(timeout)
    })
  }

  function setupDialog() {
    var $countdownElement = $('<span id="timeout-countdown" class="countdown">');
    var $element = $('<div>')
      .append(settings.title ? $('<h1 class="govuk-heading-m push--top">').text(settings.title) : '')
      .append($('<p class="govuk-body" id="timeout-message" role="text">').text(settings.message + ' ')
        .append($countdownElement)
        .append('.'))
      .append($('<button id="timeout-keep-signin-btn" class="govuk-button govuk-!-margin-right-1">').text(settings.keepAliveButtonText))
      .append($('<button id="timeout-sign-out-btn" class="govuk-button govuk-button--secondary">').text(settings.signOutButtonText))

    $element.find('#timeout-keep-signin-btn').on('click', keepAliveAndClose)
    $element.find('#timeout-sign-out-btn').on('click', signOut)

    var dialogControl = displayDialog($element)

    cleanupFunctions.push(function () {
      dialogControl.closeDialog()
    })

    dialogControl.addCloseHandler(keepAliveAndClose)

    dialogControl.setAriaLabelledBy('timeout-message')
    if (getSecondsRemaining() > 60) {
      dialogControl.setAriaLive('polite')
    }

    startCountdown($countdownElement, dialogControl)
  }

  function getSecondsRemaining() {
    return Math.floor((settings.signout_time - getDateNow()) / 1000)
  }

  function startCountdown($countdownElement, dialogControl) {
    function updateCountdown(counter, $countdownElement) {
      var message
      if (counter === 60) {
        dialogControl.setAriaLive()
      }
      if (counter < 60) {
        message = counter + ' ' + settings.properties[counter === 1 ? 'second' : 'seconds']
      } else {
        var minutes = Math.ceil(counter / 60)
        message = minutes + ' ' + settings.properties[minutes === 1 ? 'minute' : 'minutes']
      }
      $countdownElement.text(message)
    }

    function runUpdate() {
      var counter = getSecondsRemaining()
      updateCountdown(counter, $countdownElement)
      if (counter <= 0) {
        signOut()
      }
    }

    var countdown = window.setInterval(runUpdate, 1000)
    cleanupFunctions.push(function () {
      window.clearInterval(countdown)
    })
    runUpdate()
  }

  function keepAliveAndClose() {
    cleanup()
    setupDialogTimer()
    $.get(settings.keepAliveUrl, function () {
    })
  }

  function getDateNow() {
    return Date.now() || +new Date()
  }

  function signOut() {
    window.location.href = settings.signOutUrl
  }

  function cleanup() {
    while (cleanupFunctions.length > 0) {
      var fn = cleanupFunctions.shift()
      fn()
    }
  }

  function readCookie(cookieName) { // From http://www.javascripter.net/faq/readingacookie.htm
    var re = new RegExp('[; ]'+cookieName+'=([^\\s;]*)');
    var sMatch = (' '+document.cookie).match(re);
    if (cookieName && sMatch) return unescape(sMatch[1]);
    return '';
  }

  return {cleanup: cleanup}
}