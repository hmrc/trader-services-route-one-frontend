import getCookie from '../../javascripts/utils/get-cookie.util';

describe('getCookie utility', () => {
  afterEach(() => {
    document.cookie = 'test-cookie=;expires=Thu, 01 Jan 1970 00:00:01 GMT;';
    document.cookie = 'cookie1=;expires=Thu, 01 Jan 1970 00:00:01 GMT;';
    document.cookie = 'cookie2=;expires=Thu, 01 Jan 1970 00:00:01 GMT;';
    document.cookie = 'cookie3=;expires=Thu, 01 Jan 1970 00:00:01 GMT;';
  });

  describe('Given that test-cookie is set with value="value"', () => {
    beforeEach(() => {
      document.cookie = 'test-cookie=value';
    });

    describe('When getCookie is called with name="test-cookie"', () => {
      it('Then should return value=value', () => {
        expect(getCookie('test-cookie')).toEqual('value');
      });
    });
  });

  describe('Given that test-cookie is set with value="with=equals=sign"', () => {
    beforeEach(() => {
      document.cookie = 'test-cookie=with=equals=sign';
    });

    describe('When getCookie is called with name="test-cookie"', () => {
      it('Then should return "with=equals=sign"', () => {
        expect(getCookie('test-cookie')).toEqual('with=equals=sign');
      });
    });
  });

  describe('Given that three cookies are set', () => {
    beforeEach(() => {
      document.cookie = 'cookie1=1';
      document.cookie = 'cookie2=2';
      document.cookie = 'cookie3=3';
    });

    describe('When getCookie is called to retrieve the second cookie', () => {
      it('Then should return "2"', () => {
        expect(getCookie('cookie2')).toEqual('2');
      });
    });
  });
});
