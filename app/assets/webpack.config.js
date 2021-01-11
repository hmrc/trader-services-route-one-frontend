const path = require('path');

module.exports = {
  watch: true,
  devtool: 'source-map',
  entry: './javascripts/index.ts',
  resolve: {
    extensions: ['.js', '.ts']
  },
  module: {
    rules: [
      {
        test: /\.ts$/,
        exclude: /node_modules/,
        use: {
          loader: 'babel-loader',
          options: {
            presets: [
              '@babel/typescript',
              '@babel/preset-env'
            ],
            plugins: [
              '@babel/plugin-proposal-class-properties'
            ]
          }
        }
      },
      {
        test: /\.ts$/,
        exclude: /node_modules/,
        loader: 'eslint-loader'
      }
    ]
  },
  output: {
    path: path.resolve(__dirname, 'build'),
    filename: 'application.min.js'
  }
};
