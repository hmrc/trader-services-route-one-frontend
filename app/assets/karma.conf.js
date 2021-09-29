const path = require('path');
const webpackConfig = require('./webpack.config.js');
process.env.CHROME_BIN = require('puppeteer').executablePath();

module.exports = function (config) {
  config.set({
    client: {
      jasmine: {
        random: false
      }
    },
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
    specReporter: {
      suppressSkipped: true
    },
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
    webpack: webpackConfig({
      entry: {
        0: path.join(__dirname, './javascripts/index.ts')
      },
      output: {
        path: path.join(__dirname, './build'),
        filename: 'application.min.js',
      },
      webjars: {
        path: path.join(__dirname, '../../target/web/web-modules/main/webjars')
      }
    }),
    webpackMiddleware: {
    },
    failOnEmptyTestSuite: false
  });
};
