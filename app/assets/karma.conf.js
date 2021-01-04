const webpackConfig = require('./webpack.dev.config.js');

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
    browsers: ['ChromeHeadless'],
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
