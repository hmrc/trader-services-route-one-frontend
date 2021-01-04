const webpackConfig = require('./webpack.dev.config.js');
process.env.CHROME_BIN = require('puppeteer').executablePath();

module.exports = function (config) {
  config.set({
    frameworks: ['jasmine'],
    exclude: [],
    basePath: '',
    files: [
      './spec/**/*.spec.js'
    ],
    reporters: ['spec'],
    plugins: [
      'karma-jasmine',
      'karma-webpack',
      'karma-spec-reporter',
      'karma-chrome-launcher'
    ],
    port: 9876,
    colors: true,
    logLevel: config.LOG_INFO,
    autoWatch: true,
    browsers: ['MyChromeHeadless'],
    customLaunchers: {
      MyChromeHeadless: {
        base: 'ChromeHeadless',
        flags: [
          '--no-sandbox'
        ]
      }
    },
    singleRun: false,
    concurrency: Infinity,
    preprocessors: {
      './spec/**/*.spec.js': ['webpack']
    },
    webpack: webpackConfig,
    webpackMiddleware: {
    },
    failOnEmptyTestSuite: false
  });
};
