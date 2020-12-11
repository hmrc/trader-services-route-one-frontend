const path = require("path");

module.exports = {
  watch: true,
  entry: "./javascripts/index.js",
  resolve: {
    extensions: ['*', '.js']
  },
  module: {
    rules: [
      {
        test: /\.js$/,
        use: {
          loader: 'babel-loader',
          options: {
            presets: ['@babel/preset-env']
          }
        }
      }
    ]
  },
  output: {
    path: path.resolve(__dirname, "build"),
    filename: "application.min.js"
  }
};
