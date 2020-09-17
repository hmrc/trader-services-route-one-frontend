$(document).ready(function() {

    $("body").addClass( "js-enabled" );

    // =====================================================
    // Handle number inputs
    // =====================================================
    numberInputs();

    // =====================================================
    // Back link mimics browser back functionality
    // =====================================================

    // prevent resubmit warning
    if (window.history && window.history.replaceState && typeof window.history.replaceState === 'function') {
        window.history.replaceState(null, null, window.location.href);
    }
    // back click handle, dependent upon presence of referrer & no host change
    $('#back-link[href="#"]').on('click', function(e){
        e.preventDefault();
        window.history.back();
    });

});

function numberInputs() {
    // =====================================================
    // Set currency fields to number inputs on touch devices
    // this ensures on-screen keyboards display the correct style
    // don't do this for FF as it has issues with trailing zeroes
    // =====================================================
    if($('html.touchevents').length > 0 && window.navigator.userAgent.indexOf("Firefox") == -1){
        $('[data-type="currency"] > input[type="text"], [data-type="percentage"] > input[type="text"]').each(function(){
            $(this).attr('type', 'number');
            $(this).attr('step', 'any');
            $(this).attr('min', '0');
        });
    }

    // =====================================================
    // Disable mouse wheel and arrow keys (38,40) for number inputs to prevent mis-entry
    // also disable commas (188) as they will silently invalidate entry on Safari 10.0.3 and IE11
    // =====================================================
    $("form").on("focus", "input[type=number]", function(e) {
        $(this).on('wheel', function(e) {
            e.preventDefault();
        });
    });
    $("form").on("blur", "input[type=number]", function(e) {
        $(this).off('wheel');
    });
    $("form").on("keydown", "input[type=number]", function(e) {
        if ( e.which == 38 || e.which == 40 || e.which == 188 )
            e.preventDefault();
    });
}
