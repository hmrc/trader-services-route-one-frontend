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
        exclude: /node_modules/,
        use: {
          loader: 'babel-loader',
          options: {
            presets: ['@babel/preset-env']
          }
        }
      },
      // {
      //   test: /\.js$/,
      //   exclude: /node_modules|vendor/,
      //   loader: 'eslint-loader'
      // }
    ]
  },
  output: {
    path: path.resolve(__dirname, "build"),
    filename: "application.min.js"
  }
};
