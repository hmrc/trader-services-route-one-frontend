const path = require('path');
const ESLintPlugin = require('eslint-webpack-plugin');

module.exports = function (env) {
  return {
    mode: 'production',
    optimization: {
      minimize: true,
      concatenateModules: true
    },
    watch: false,
    devtool: 'source-map',
    entry: env.entry ? Object.values(env.entry) : [],
    resolve: {
      extensions: ['.js', '.ts'],
      alias: {
        'node_modules': path.join(__dirname, 'node_modules'),
        'webjars': env.webjars.path
      }
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
          exclude: /node_modules|legacy/,
          loader: ESLintPlugin.loader
        }
      ]
    },
    output: env.output,
    plugins: [new ESLintPlugin({ fix: false })],
    stats: {
      moduleAssets: true,
      relatedAssets: true,
      nestedModules: true,
      runtimeModules: true,
      dependentModules: true,
      groupAssetsByPath: true,
      groupModulesByPath: true,
      children: true,
      chunks: false,
      modules: true,
      outputPath: true,
      providedExports: false,
      reasons: false,
      source: true,
      chunkGroupChildren: true,
      chunkRelations: true,
      modulesSpace: 100,
      assetsSpace: 100,
      chunkModulesSpace: 100,
      nestedModulesSpace: 100,
    }
  }
};
