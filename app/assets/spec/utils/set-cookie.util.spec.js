import setCookie from '../../javascripts/utils/set-cookie.util';

describe('setCookie utility', () => {
  afterEach(() => {
    document.cookie = 'test-cookie=;expires=Thu, 01 Jan 1970 00:00:01 GMT;';
  });

  describe('When setCookie is called with name=test-cookie', () => {
    it('Then should create a cookie called test-cookie ', () => {
      setCookie('test-cookie', '');

      expect(document.cookie).toEqual('test-cookie=');
    });
  });

  describe('When setCookie is called with name=test-cookie and value=value', () => {
    it('Then should create a cookie called test-cookie with value=value', () => {
      setCookie('test-cookie', 'value');

      expect(document.cookie).toEqual('test-cookie=value');
    });
  });
});
